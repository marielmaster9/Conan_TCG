package com.example.conantcg.ui.screens

import android.Manifest
import android.util.Log
import android.util.Size
import android.view.MotionEvent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Done
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size as CanvasSize
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.example.conantcg.ui.viewmodel.CardViewModel
import com.example.conantcg.utils.ConanTheme
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.delay
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@androidx.annotation.OptIn(androidx.camera.core.ExperimentalGetImage::class)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CardScannerScreen(viewModel: CardViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var hasPermission by remember { mutableStateOf(false) }
    var isCoolingDown by remember { mutableStateOf(false) }
    var lastAdded by remember { mutableStateOf<String?>(null) }
    var debugAiText by remember { mutableStateOf("จัดรูปการ์ดให้อยู่ในกรอบ") }

    var cameraControl by remember { mutableStateOf<CameraControl?>(null) }
    var zoomRatio by remember { mutableFloatStateOf(1f) }

    val textRecognizer = remember { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        hasPermission = isGranted
        if (!isGranted) {
            Toast.makeText(context, "ต้องอนุญาตให้ใช้กล้อง", Toast.LENGTH_SHORT).show()
            onBack()
        }
    }

    LaunchedEffect(Unit) { launcher.launch(Manifest.permission.CAMERA) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("สแกนการ์ดทั้งใบ", color = Color.White, fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = Color.White) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = ConanTheme.BlueSuit.copy(alpha = 0.9f))
            )
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding).background(Color.Black)) {
            if (hasPermission) {
                // 1. เลเยอร์กล้อง
                AndroidView(
                    factory = { ctx ->
                        val view = PreviewView(ctx)
                        val future = ProcessCameraProvider.getInstance(ctx)

                        view.setOnTouchListener { _, event ->
                            if (event.action == MotionEvent.ACTION_UP) {
                                val factory = view.meteringPointFactory
                                val point = factory.createPoint(event.x, event.y)
                                val action = FocusMeteringAction.Builder(point, FocusMeteringAction.FLAG_AF).setAutoCancelDuration(3, TimeUnit.SECONDS).build()
                                cameraControl?.startFocusAndMetering(action)
                            }
                            true
                        }

                        future.addListener({
                            val provider = future.get()
                            val preview = Preview.Builder().setTargetResolution(Size(1280, 720)).build().also { it.setSurfaceProvider(view.surfaceProvider) }
                            val analyzer = ImageAnalysis.Builder().setTargetResolution(Size(1280, 720)).setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build().also {
                                it.setAnalyzer(Executors.newSingleThreadExecutor()) { proxy ->
                                    val mediaImage = proxy.image
                                    if (mediaImage != null && !isCoolingDown) {
                                        val image = InputImage.fromMediaImage(mediaImage, proxy.imageInfo.rotationDegrees)
                                        textRecognizer.process(image)
                                            .addOnSuccessListener { visionText ->
                                                debugAiText = visionText.textBlocks.joinToString(" ") { b -> b.text }.take(30).replace("\n", " ")
                                                processVisionText(visionText, viewModel) { cardName ->
                                                    lastAdded = cardName
                                                    isCoolingDown = true
                                                }
                                            }
                                            .addOnCompleteListener { proxy.close() }
                                    } else {
                                        proxy.close()
                                    }
                                }
                            }

                            try {
                                provider.unbindAll()
                                val camera = provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analyzer)
                                cameraControl = camera.cameraControl
                                cameraControl?.setZoomRatio(zoomRatio)
                            } catch (e: Exception) { Log.e("Scanner", "Error", e) }
                        }, ContextCompat.getMainExecutor(ctx))
                        view
                    },
                    modifier = Modifier.fillMaxSize()
                )

                // 2. เลเยอร์ Overlay สแกนแบบเต็มใบ (แนวตั้ง)
                ScannerOverlayFullCard()

                // 3. เลเยอร์แสดงข้อมูล AI
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 24.dp)
                        .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(percent = 50))
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = "กำลังค้นหา: $debugAiText",
                        color = Color(0xFF00FFCC),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                // 4. เลเยอร์แถบควบคุมซูม
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 32.dp, start = 24.dp, end = 24.dp)
                        .background(Color(0xFF1E1E2E).copy(alpha = 0.85f), RoundedCornerShape(24.dp))
                        .padding(horizontal = 24.dp, vertical = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("เลื่อนเพื่อซูมให้การ์ดพอดีกรอบ", color = Color.White.copy(alpha = 0.8f), fontSize = 13.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    Slider(
                        value = zoomRatio,
                        onValueChange = { zoomRatio = it; cameraControl?.setZoomRatio(it) },
                        valueRange = 1f..4f,
                        colors = SliderDefaults.colors(
                            thumbColor = Color(0xFFE53935),
                            activeTrackColor = Color(0xFFE53935),
                            inactiveTrackColor = Color.White.copy(alpha = 0.2f)
                        )
                    )
                }

                // 5. Popup แจ้งเตือนสแกนติด
                if (isCoolingDown) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .background(Color(0xFF4CAF50).copy(alpha = 0.95f), RoundedCornerShape(16.dp))
                            .padding(24.dp, 16.dp)
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.Done, contentDescription = null, tint = Color.White, modifier = Modifier.size(48.dp))
                            Spacer(Modifier.height(8.dp))
                            Text("เพิ่มเข้าคลังแล้ว", color = Color.White, fontSize = 14.sp)
                            Text("$lastAdded", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        }
                    }
                    LaunchedEffect(Unit) { delay(1800); isCoolingDown = false }
                }
            }
        }
    }
}

// ---------------------------------------------------------
// ฟังก์ชันวาด UI ทับหน้ากล้อง (เจาะรูใสแบบแนวตั้ง + เลเซอร์วิ่ง)
// ---------------------------------------------------------
@Composable
fun ScannerOverlayFullCard() {
    val infiniteTransition = rememberInfiniteTransition(label = "Laser")
    val laserY by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2500, easing = LinearEasing), // ปรับให้วิ่งช้าลง เพราะกรอบใหญ่ขึ้น
            repeatMode = RepeatMode.Reverse
        ),
        label = "LaserY"
    )

    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = 0.99f }
    ) {
        val canvasWidth = size.width
        val canvasHeight = size.height

        // กำหนดขนาดให้เป็น "แนวตั้ง" (คล้ายสัดส่วนการ์ดจริง 63x88mm หรือสัดส่วน 1 : 1.4)
        val rectWidth = canvasWidth * 0.75f
        val rectHeight = rectWidth * 1.4f
        val left = (canvasWidth - rectWidth) / 2
        val top = (canvasHeight - rectHeight) / 2
        val cornerRadius = 16.dp.toPx()

        // วาดพื้นหลังดำโปร่งแสง
        drawRect(color = Color.Black.copy(alpha = 0.65f))

        // เจาะช่องใส (สัดส่วนการ์ด)
        drawRoundRect(
            color = Color.Transparent,
            topLeft = Offset(left, top),
            size = CanvasSize(rectWidth, rectHeight),
            cornerRadius = CornerRadius(cornerRadius, cornerRadius),
            blendMode = BlendMode.Clear
        )

        // เส้นเลเซอร์
        val laserPos = top + (rectHeight * laserY)
        drawLine(
            color = Color(0xFFE53935).copy(alpha = 0.7f),
            start = Offset(left + 8.dp.toPx(), laserPos),
            end = Offset(left + rectWidth - 8.dp.toPx(), laserPos),
            strokeWidth = 3.dp.toPx()
        )

        // ขอบมุมทั้ง 4 ด้าน (Brackets)
        val bracketLength = 32.dp.toPx() // ทำให้ยาวขึ้นให้สมส่วนกับกรอบใหญ่
        val bracketStroke = 4.dp.toPx()
        val bracketColor = Color.White

        drawLine(bracketColor, Offset(left, top), Offset(left + bracketLength, top), bracketStroke)
        drawLine(bracketColor, Offset(left, top), Offset(left, top + bracketLength), bracketStroke)

        drawLine(bracketColor, Offset(left + rectWidth, top), Offset(left + rectWidth - bracketLength, top), bracketStroke)
        drawLine(bracketColor, Offset(left + rectWidth, top), Offset(left + rectWidth, top + bracketLength), bracketStroke)

        drawLine(bracketColor, Offset(left, top + rectHeight), Offset(left + bracketLength, top + rectHeight), bracketStroke)
        drawLine(bracketColor, Offset(left, top + rectHeight), Offset(left, top + rectHeight - bracketLength), bracketStroke)

        drawLine(bracketColor, Offset(left + rectWidth, top + rectHeight), Offset(left + rectWidth - bracketLength, top + rectHeight), bracketStroke)
        drawLine(bracketColor, Offset(left + rectWidth, top + rectHeight), Offset(left + rectWidth, top + rectHeight - bracketLength), bracketStroke)
    }
}

// ---------------------------------------------------------
// ฟังก์ชันประมวลผลข้อความ
// ---------------------------------------------------------
private fun processVisionText(visionText: com.google.mlkit.vision.text.Text, viewModel: CardViewModel, onSuccess: (String) -> Unit) {
    for (block in visionText.textBlocks) {
        val rawText = block.text.replace("\\s".toRegex(), "").trim()
        val cleanText = rawText.replace(Regex("[^A-Za-z0-9\\[\\]\\-]"), "")
        if (cleanText.length < 3) continue
        val fixedText = cleanText.replace("O", "0").replace("I", "1").replace("l", "1")
        val id = Regex("\\[(.*?)]").find(fixedText)?.groupValues?.get(1) ?: fixedText

        val card = viewModel.cardList.find { it.cardNum.equals(id, true) || it.cardId.equals(id, true) }
        if (card != null) {
            viewModel.incrementCard(card.cardNum)
            onSuccess(card.name)
            return
        }
    }
}

package com.example.conantcg.ui.screens

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.RectF
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.util.Size
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect as ComposeRect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size as ComposeSize
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import coil.compose.AsyncImage
import com.example.conantcg.data.model.WebCard
import com.example.conantcg.ui.viewmodel.CardViewModel
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.nio.ByteBuffer
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sqrt
import kotlinx.coroutines.delay

private object ScanV3Style {
    val red = Color(0xFFE53935)
    val green = Color(0xFF4CAF50)
    val yellow = Color(0xFFFFB300)
    val popup = Color(0xFF161622)
}

// Allow slower phones time for 3 *consistent* reads of small edge text.
private const val FRAME_INTERVAL_MS = 170L
private const val SAME_CARD_WINDOW_MS = 5000L
private const val REARM_EMPTY_FRAMES = 3
private const val FULL_SCAN_GRACE_MS = 2500L
private const val ASSIST_ZOOM_MIN = 1.45f
private const val PARTIAL_TIMEOUT_MS = 30000L
private const val PARTIAL_WINDOW_MS = 7000L

private enum class CardPose { NORMAL, UPSIDE_DOWN }
private enum class ScanMode { FULL_CARD, ZOOM_ASSISTED }

private data class ScanWindow(
    val frame: RectF,
    val previewWidth: Int,
    val previewHeight: Int,
    val landscapeCard: Boolean
)

private data class ScanZones(
    val cardId: RectF,
    val cardNum: RectF,
    val numRotation: Float
)

private data class NumberMatch(
    val card: WebCard,
    val corrected: Boolean,
    // A longer printing (P/P2/SEC1/SEC2...) may have been cut by OCR.
    val suffixMayBeMissing: Boolean
)

private data class ScanObservation(
    val mode: ScanMode,
    val number: NumberMatch?,
    val readCardId: String?,
    val pose: CardPose,
    val numZoneOnScreen: RectF,
    val rawNumberOcr: String,
    val usedNumberRotation: Float,
    val numberBand: Int,
    val rawIdOcr: String = ""
)

private fun cleanScanCode(s: String): String = s.uppercase(Locale.ROOT)
    .filter { it in 'A'..'Z' || it in '0'..'9' }

/** Only cardNum identifies a physical printing / artwork. */
private class CardNumberIndex(cards: List<WebCard>) {
    private val byNumber = cards.filter { it.cardNum.isNotBlank() }
        .groupBy { cleanScanCode(it.cardNum) }
        .filterKeys { it.length >= 5 }
    private val lengths = byNumber.keys.map { it.length }.distinct().sortedDescending()
    private val versionSuffixes = listOf("SEC2", "SEC1", "P2", "P")

    // Build ambiguity map once: B06015 can be the cropped beginning
    // of B06015P/P2/SEC1/SEC2, and B06015P can precede B06015P2.
    private val incompleteVariants: Set<String> = buildSet {
        for (printed in byNumber.keys) {
            val suffix = versionSuffixes.firstOrNull { printed.endsWith(it) } ?: continue
            val base = printed.removeSuffix(suffix)
            if (base in byNumber) add(base)
            if (suffix == "P2" && "${base}P" in byNumber) add("${base}P")
        }
    }

    private fun possibleTruncatedSuffix(cardNum: String): Boolean =
        cleanScanCode(cardNum) in incompleteVariants

    /** Offered only in an explicit user-confirmation dialog, never auto-saved. */
    fun artworkChoices(card: WebCard): List<WebCard> {
        val code = cleanScanCode(card.cardNum)
        val base = versionSuffixes.firstOrNull { code.endsWith(it) }
            ?.let { code.removeSuffix(it) } ?: code
        val identity = dbCardId(card)
        return byNumber.entries
            .asSequence()
            .filter { (number, _) ->
                number == base || versionSuffixes.any { number == base + it }
            }
            .flatMap { (_, entries) -> entries.asSequence() }
            .filter { dbCardId(it) == identity }
            .distinctBy { cleanScanCode(it.cardNum) }
            .sortedBy { cleanScanCode(it.cardNum) }
            .toList()
    }

    fun containsNumber(number: String): Boolean =
        byNumber.containsKey(cleanScanCode(number))

    private fun findIn(raw: String, corrected: Boolean): List<NumberMatch> {
        val normalized = cleanScanCode(raw)
        if (normalized.length < 5) return emptyList()

        val found = linkedMapOf<String, NumberMatch>()
        for (len in lengths) {
            if (len > normalized.length) continue
            for (i in 0..(normalized.length - len)) {
                val slice = normalized.substring(i, i + len)
                val stored = byNumber[slice] ?: continue

                // Duplicate catalog entries for the very same printed number
                // are safe only if they agree on the card identity.
                val unique = stored.distinctBy { card ->
                    "${cleanScanCode(card.cardNum)}:${dbCardId(card) ?: cleanScanCode(card.cardId)}"
                }
                if (unique.size != 1) continue
                val card = unique.single()
                found[card.cardNum] = NumberMatch(
                    card = card,
                    corrected = corrected,
                    suffixMayBeMissing = possibleTruncatedSuffix(card.cardNum)
                )
            }
        }
        return found.values.toList()
    }

    private fun pickLongest(matches: Collection<NumberMatch>): NumberMatch? {
        if (matches.isEmpty()) return null
        // Key fix: B06015 is a substring of B06015P/P2/SEC1/SEC2. Never reject the
        // complete Parallel number just because the normal number also exists.
        val longest = matches.maxOf { cleanScanCode(it.card.cardNum).length }
        val leaders = matches
            .filter { cleanScanCode(it.card.cardNum).length == longest }
            .distinctBy { cleanScanCode(it.card.cardNum) }
        return leaders.singleOrNull()
    }

    fun detect(ocr: Text): NumberMatch? {
        val exact = linkedMapOf<String, NumberMatch>()
        val fuzzy = linkedMapOf<String, NumberMatch>()
        val lines = ocr.textBlocks.flatMap { it.lines }

        val samples = mutableListOf(ocr.text.replace('\n', ' '))
        for (line in lines) {
            samples.add(line.text)
            val words = line.elements.map { it.text }.filter { it.isNotBlank() }
            samples.addAll(words)
            for (start in words.indices) {
                var joined = ""
                for (end in start until minOf(start + 4, words.size)) {
                    joined += words[end]
                    samples.add(joined)
                }
            }
        }

        for (sample in samples.distinct()) {
            findIn(sample, false).forEach { exact[it.card.cardNum] = it }
            val normalized = cleanScanCode(sample)
            val fixed = normalized.replace('O', '0')
                .replace('I', '1').replace('L', '1')
            if (fixed != normalized) {
                findIn(fixed, true).forEach { fuzzy[it.card.cardNum] = it }
            }
        }

        // Exact matches always outrank corrected OCR. Resolve shared prefixes
        // by preferring the *longest* complete card number, not by first hit.
        if (exact.isNotEmpty()) return pickLongest(exact.values)
        return pickLongest(fuzzy.values)
    }
}

/** Capture both numeric IDs (1121) and partner IDs (P060). */
private fun normalizedCardIdentity(raw: String): String? {
    val cleaned = cleanScanCode(raw).removePrefix("ID")
    val parsed = Regex("^([A-Z]?)([0-9]{3,6})$")
        .matchEntire(cleaned) ?: return null
    val prefix = parsed.groupValues[1]
    val digits = parsed.groupValues[2]
    val number = if (prefix.isEmpty()) digits.trimStart('0').ifEmpty { "0" } else digits
    return prefix + number
}

/**
 * Find PRINTED ID such as ID[P060], ID[1121], or OCR's "ID P O6O".
 * Require the literal ID marker; never take a random number from card effects.
 * Extra OCR candidates are verified against the matched card's database identity
 * by the existing Zoom Assist state machine before saving anything.
 */
private fun parsePrintedId(raw: String): String? {
    val normalized = cleanScanCode(raw)
    val matches = Regex("ID([A-Z]?)([0-9OIL]{3,6})")
        .findAll(normalized)
        .mapNotNull { match ->
            val prefix = match.groupValues[1]
            val numbers = match.groupValues[2]
                .replace('O', '0')
                .replace('I', '1')
                .replace('L', '1')
            normalizedCardIdentity(prefix + numbers)
        }
        .distinct()
        .toList()
    return matches.singleOrNull()
}

private fun readPrintedCardId(text: Text): String? {
    val candidates = linkedSetOf<String>()
    parsePrintedId(text.text)?.let { candidates += it }
    for (block in text.textBlocks) {
        for (line in block.lines) {
            parsePrintedId(line.text)?.let { candidates += it }
        }
    }
    return candidates.singleOrNull()
}

private fun dbCardId(card: WebCard): String? =
    normalizedCardIdentity(card.cardId)

/** Used only for diagnostic status, never for directly saving a card. */
private fun obviousNumberInRawOcr(raw: String): String? =
    Regex("[A-Z][0-9]{5}[A-Z]?")
        .find(cleanScanCode(raw))?.value

/** Fractions are relative to the physical card frame, not the entire camera. */
private fun computeZones(
    landscape: Boolean,
    pose: CardPose,
    numberBand: Int
): ScanZones {
    // The printed card edge is NOT necessarily flush with the movable frame.
    // Use two overlapping narrow bands, scanning alternate bands until locked.
    if (pose == CardPose.NORMAL) {
        if (landscape) return ScanZones(
            RectF(0.01f, 0.73f, 0.55f, 0.99f),
            RectF(0.68f, 0.58f, 0.995f, 0.995f),
            0f
        )
        return ScanZones(
            RectF(0.00f, 0.73f, 0.64f, 0.999f),
            if (numberBand == 0) {
                // Slightly inboard edge: screenshot shows code near 77-80%.
                RectF(0.67f, 0.40f, 0.87f, 0.975f)
            } else {
                // Card aligned near the outer edge of the red frame.
                RectF(0.81f, 0.40f, 0.999f, 0.985f)
            },
            90f
        )
    }
    if (landscape) return ScanZones(
        RectF(0.45f, 0.01f, 0.99f, 0.27f),
        RectF(0.005f, 0.005f, 0.32f, 0.42f),
        180f
    )
    return ScanZones(
        RectF(0.36f, 0.001f, 0.999f, 0.27f),
        // 180° card inversion mirrors the physical right edge to the left.
        if (numberBand == 0) {
            RectF(0.13f, 0.025f, 0.33f, 0.60f)
        } else {
            RectF(0.001f, 0.015f, 0.19f, 0.60f)
        },
        270f
    )
}

private fun subRegion(frame: RectF, rel: RectF): RectF = RectF(
    frame.left + frame.width() * rel.left,
    frame.top + frame.height() * rel.top,
    frame.left + frame.width() * rel.right,
    frame.top + frame.height() * rel.bottom
)

/** FILL_CENTER approximation; use matching Preview/ImageAnalysis aspect ratios. */
private fun screenToImageRect(
    screen: RectF, previewW: Int, previewH: Int,
    imageW: Int, imageH: Int
): Rect? {
    if (previewW < 1 || previewH < 1 || imageW < 1 || imageH < 1) return null
    val scale = maxOf(previewW.toFloat() / imageW, previewH.toFloat() / imageH)
    val dx = (previewW - imageW * scale) / 2f
    val dy = (previewH - imageH * scale) / 2f
    val l = ((screen.left - dx) / scale).roundToInt().coerceIn(0, imageW)
    val t = ((screen.top - dy) / scale).roundToInt().coerceIn(0, imageH)
    val r = ((screen.right - dx) / scale).roundToInt().coerceIn(0, imageW)
    val b = ((screen.bottom - dy) / scale).roundToInt().coerceIn(0, imageH)
    if (r - l < 14 || b - t < 14) return null
    return Rect(l, t, r, b)
}

/**
 * Extract only a small region of the Y plane (grayscale), mapped into the
 * upright orientation, then rotate the printed code so ML Kit sees horizontal text.
 * The ImageProxy is closed immediately after copying these pixels.
 */
private fun captureRoi(
    proxy: ImageProxy, crop: Rect, rotation: Int,
    textRotation: Float, targetShortSide: Float = 150f
): Bitmap {
    val rawW = proxy.width
    val rawH = proxy.height
    val plane = proxy.planes[0]
    val data: ByteBuffer = plane.buffer
    val stride = plane.rowStride
    val pixelStride = plane.pixelStride
    val w = crop.width()
    val h = crop.height()
    val pixels = IntArray(w * h)
    var out = 0
    for (y in crop.top until crop.bottom) {
        for (x in crop.left until crop.right) {
            // Inverse mapping of CameraX clockwise rotation into raw Y plane.
            val rx: Int
            val ry: Int
            when (rotation) {
                90 -> { rx = y; ry = rawH - 1 - x }
                180 -> { rx = rawW - 1 - x; ry = rawH - 1 - y }
                270 -> { rx = rawW - 1 - y; ry = x }
                else -> { rx = x; ry = y }
            }
            val luminance = data.get(ry * stride + rx * pixelStride).toInt() and 0xFF
            pixels[out++] = AndroidColor.rgb(luminance, luminance, luminance)
        }
    }
    val source = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    source.setPixels(pixels, 0, w, 0, 0, w, h)
    val turned = if (textRotation == 0f) source else {
        val matrix = Matrix().apply { postRotate(textRotation) }
        Bitmap.createBitmap(source, 0, 0, w, h, matrix, true)
            .also { source.recycle() }
    }
    // Edge codes can be just 6-10 pixels high in the camera image.
    // Upscale the small ROI before OCR; this improves character segmentation,
    // although it cannot restore detail absent from the original image.
    val shorter = minOf(turned.width, turned.height)
    val factor = (targetShortSide / shorter.coerceAtLeast(1)).coerceIn(1f, 3.0f)
    return if (factor > 1.15f) {
        Bitmap.createScaledBitmap(
            turned, (turned.width * factor).roundToInt(),
            (turned.height * factor).roundToInt(), true
        ).also { turned.recycle() }
    } else turned
}

@Composable
@SuppressLint("UnsafeOptInUsageError")
@OptIn(ExperimentalGetImage::class)
fun CardScannerScreen(
    viewModel: CardViewModel,
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val density = LocalDensity.current
    val mainHandler = remember { Handler(Looper.getMainLooper()) }
    val executor = remember { Executors.newSingleThreadExecutor() }
    val numberRecognizer = remember {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }
    val idRecognizer = remember {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }
    val assistRecognizer = remember {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }
    val previewView = remember(context) {
        PreviewView(context).apply { scaleType = PreviewView.ScaleType.FILL_CENTER }
    }
    val busy = remember { AtomicBoolean(false) }
    val alive = remember { AtomicBoolean(true) }
    val lastFrameAt = remember { AtomicLong(0L) }
    val frames = remember { AtomicInteger(0) }
    val scanWindow = remember { AtomicReference<ScanWindow?>(null) }
    val preferredPose = remember { AtomicReference<CardPose?>(null) }
    val preferredNumberAngle = remember { AtomicReference<Float?>(null) }
    val preferredNumberBand = remember { AtomicReference<Int?>(null) }

    val index = remember(viewModel.cardList.size) {
        CardNumberIndex(viewModel.cardList.toList())
    }
    val latestIndex by rememberUpdatedState(index)

    var hasPermission by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(
            context, Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED)
    }
    val permissionRequest = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { hasPermission = it }
    LaunchedEffect(Unit) {
        if (!hasPermission) permissionRequest.launch(Manifest.permission.CAMERA)
    }

    var camera by remember { mutableStateOf<Camera?>(null) }
    var cameraError by remember { mutableStateOf<String?>(null) }
    var zoom by remember { mutableFloatStateOf(1f) }
    var landscapeFrame by remember { mutableStateOf(false) }
    var frameCenter by remember { mutableStateOf<Offset?>(null) }
    var focusPoint by remember { mutableStateOf<Offset?>(null) }
    var scanMessage by remember { mutableStateOf("Scanner Ready") }
    var currentTarget by remember { mutableStateOf<WebCard?>(null) }
    var currentZone by remember { mutableStateOf<RectF?>(null) }
    var matchCode by remember { mutableStateOf<String?>(null) }
    var matchCount by remember { mutableIntStateOf(0) }
    var lastMatchAt by remember { mutableLongStateOf(0L) }
    var wasCorrected by remember { mutableStateOf(false) }
    var scannedCardNum by remember { mutableStateOf<String?>(null) }
    var waitingForRemoval by remember { mutableStateOf(false) }
    var lastAccepted by remember { mutableStateOf<String?>(null) }
    var emptyFrames by remember { mutableIntStateOf(0) }
    var nextAllowedAt by remember { mutableLongStateOf(0L) }

    // FULL CARD is always the initial mode. Zoom assist only starts after
    // a failed full-card attempt AND user zooms >= 1.45x.
    var scanMode by remember { mutableStateOf(ScanMode.FULL_CARD) }
    val scanModeForAnalyzer by rememberUpdatedState(scanMode)
    var fullStartedAt by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }

    // Temporarily captured codes for assisted scanning. Neither code is
    // enough on its own: both are required before the collection is updated.
    var heldId by remember { mutableStateOf<String?>(null) }
    var heldNumber by remember { mutableStateOf<NumberMatch?>(null) }
    // Once Card Num is saved, keep the OCR image upright for the horizontal ID label.
    val searchingHorizontalId by rememberUpdatedState(heldNumber != null && heldId == null)
    var tentativeId by remember { mutableStateOf<String?>(null) }
    var tentativeIdCount by remember { mutableIntStateOf(0) }
    var tentativeNumber by remember { mutableStateOf<String?>(null) }
    var tentativeNumberCount by remember { mutableIntStateOf(0) }
    var tentativeIdAt by remember { mutableLongStateOf(0L) }
    var tentativeNumberAt by remember { mutableLongStateOf(0L) }
    var lastCapturedAt by remember { mutableLongStateOf(0L) }
    var assistHint by remember { mutableStateOf("") }
    // Manual artwork verification is only required when OCR might have
    // truncated a longer known number, e.g. B06015 -> B06015P / P2.
    var reviewChoices by remember { mutableStateOf<List<WebCard>>(emptyList()) }
    var reviewSelection by remember { mutableStateOf<String?>(null) }
    var reviewDetectedNum by remember { mutableStateOf("") }

    fun requestArtworkVerification(card: WebCard) {
        reviewChoices = index.artworkChoices(card).ifEmpty { listOf(card) }
        reviewSelection = null // User must actively inspect and choose the printing.
        reviewDetectedNum = card.cardNum
        scanMessage = "Verify suffix / artwork before adding"
    }

    fun commitScannedCard(card: WebCard) {
        viewModel.updateQuantity(card.cardNum, viewModel.getQuantity(card.cardNum) + 1)
        scannedCardNum = card.cardNum
        lastAccepted = card.cardNum
        waitingForRemoval = true
        emptyFrames = 0
        scanMessage = "Scan Successful!"
        reviewChoices = emptyList()
        reviewSelection = null
    }

    fun setZoom(value: Float) {
        val min = camera?.cameraInfo?.zoomState?.value?.minZoomRatio ?: 1f
        val max = camera?.cameraInfo?.zoomState?.value?.maxZoomRatio ?: 4f
        val actual = value.coerceIn(min, max)
        zoom = actual
        camera?.cameraControl?.setZoomRatio(actual)
    }

    val frameWidth by animateDpAsState(
        if (landscapeFrame) 360.dp else 280.dp,
        spring(stiffness = Spring.StiffnessMediumLow), label = "frameWidth"
    )
    val frameHeight by animateDpAsState(
        if (landscapeFrame) 250.dp else 400.dp,
        spring(stiffness = Spring.StiffnessMediumLow), label = "frameHeight"
    )

    val sensorManager = remember {
        context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    }
    val accelerometer = remember { sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) }
    var tiltX by remember { mutableFloatStateOf(0f) }
    var tiltY by remember { mutableFloatStateOf(0f) }
    var bubbleX by remember { mutableFloatStateOf(0f) }
    var bubbleY by remember { mutableFloatStateOf(0f) }
    var level by remember { mutableStateOf(true) }
    val gyroX by animateFloatAsState(tiltX, spring(stiffness = Spring.StiffnessLow), label = "gyroX")
    val gyroY by animateFloatAsState(tiltY, spring(stiffness = Spring.StiffnessLow), label = "gyroY")
    val spiritX by animateFloatAsState(bubbleX, label = "spiritX")
    val spiritY by animateFloatAsState(bubbleY, label = "spiritY")

    DisposableEffect(sensorManager, accelerometer) {
        var sx = 0f
        var sy = 0f
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                sx += .15f * (event.values[0] - sx)
                sy += .15f * (event.values[1] - sy)
                level = sqrt(sx * sx + sy * sy) < 2f
                tiltX = -sx * 6f
                tiltY = sy * 6f
                bubbleX = (-sx * 3.5f).coerceIn(-7f, 7f)
                bubbleY = (sy * 3.5f).coerceIn(-7f, 7f)
            }
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        if (accelerometer != null) sensorManager.registerListener(
            listener, accelerometer, SensorManager.SENSOR_DELAY_UI
        )
        onDispose { sensorManager.unregisterListener(listener) }
    }

    LaunchedEffect(focusPoint) {
        if (focusPoint != null) { delay(1000L); focusPoint = null }
    }

    fun resetLock() {
        currentTarget = null
        currentZone = null
        matchCode = null
        matchCount = 0
        wasCorrected = false
        preferredNumberAngle.set(null)
        preferredNumberBand.set(null)
    }

    fun clearPartial() {
        heldId = null
        heldNumber = null
        tentativeId = null
        tentativeIdCount = 0
        tentativeNumber = null
        tentativeNumberCount = 0
        tentativeIdAt = 0L
        tentativeNumberAt = 0L
        lastCapturedAt = 0L
    }

    fun restartFullCard() {
        clearPartial()
        resetLock()
        preferredPose.set(null)
        scanMode = ScanMode.FULL_CARD
        fullStartedAt = SystemClock.elapsedRealtime()
        frames.set(0)
        reviewChoices = emptyList()
        reviewSelection = null
        scanMessage = "Scanner Ready"
    }

    // Receives one OCR observation on the main thread.
    // No collection write is allowed unless we have a reliable cardNum.
    val consume by rememberUpdatedState<(ScanObservation) -> Unit> { observation ->
        if (!alive.get() || scannedCardNum != null || reviewChoices.isNotEmpty()) {
            // Screen was closed or success popup is active.
        } else {
            val now = SystemClock.elapsedRealtime()
            val found = observation.number
            val foundId = observation.readCardId

            if (waitingForRemoval) {
                if (found?.card?.cardNum != lastAccepted) emptyFrames++ else emptyFrames = 0
                if (emptyFrames >= REARM_EMPTY_FRAMES) {
                    waitingForRemoval = false
                    lastAccepted = null
                    emptyFrames = 0
                }
                scanMessage = "Move card away, then scan next"
            } else if (now >= nextAllowedAt) {
                if (scanMode == ScanMode.FULL_CARD &&
                    zoom >= ASSIST_ZOOM_MIN && now - fullStartedAt >= FULL_SCAN_GRACE_MS) {
                    // ONLY here do we enable memory scanning. Zoom alone at startup
                    // does not activate it until full scanning has been attempted.
                    clearPartial()
                    resetLock()
                    preferredPose.set(null)
                    scanMode = ScanMode.ZOOM_ASSISTED
                    scanMessage = "Zoom Assist: find Card ID or Card Num"
                } else if (observation.mode == scanMode) {
                    if (scanMode == ScanMode.FULL_CARD) {
                        // Fast default: verify an exact printing by Card Num.
                        // Card ID corroborates when present but never substitutes for Num.
                        val dbId = found?.card?.let(::dbCardId)
                        val mismatch = found != null && foundId != null &&
                                dbId != null && dbId != foundId

                        when {
                            mismatch -> {
                                resetLock()
                                preferredPose.set(null)
                                scanMessage = "ID / Num mismatch: adjust card"
                            }
                            found == null -> {
                                if (now - lastMatchAt > SAME_CARD_WINDOW_MS) {
                                    resetLock()
                                    preferredPose.set(null)
                                }
                                scanMessage = when {
                                    foundId != null -> "ID $foundId seen • searching Card Num"
                                    observation.rawNumberOcr.isNotBlank() ->
                                        "Edge OCR: ${observation.rawNumberOcr.take(20)}"
                                    else -> "Full scan: searching Card Num"
                                }
                            }
                            else -> {
                                val same = matchCode == found.card.cardNum &&
                                        now - lastMatchAt <= SAME_CARD_WINDOW_MS
                                matchCount = if (same) matchCount + 1 else 1
                                wasCorrected = (same && wasCorrected) || found.corrected
                                matchCode = found.card.cardNum
                                lastMatchAt = now
                                currentTarget = found.card
                                currentZone = RectF(observation.numZoneOnScreen)
                                preferredPose.set(observation.pose)
                                preferredNumberAngle.set(observation.usedNumberRotation)
                                preferredNumberBand.set(observation.numberBand)
                                val needed = if (wasCorrected) 4 else 3
                                scanMessage = "Card Num ${found.card.cardNum}: $matchCount/$needed"
                                if (matchCount >= needed) {
                                    val card = viewModel.cardList.firstOrNull {
                                        it.cardNum == found.card.cardNum
                                    }
                                    if (card != null) {
                                        if (found.suffixMayBeMissing) {
                                            requestArtworkVerification(card)
                                        } else {
                                            commitScannedCard(card)
                                        }
                                    }
                                    resetLock()
                                }
                            }
                        }
                    } else {
                        // ZOOM ASSIST: accept either identifier first, from ANYWHERE
                        // in the movable frame. Text orientations cycle between
                        // 0° / 90° / 270° / 180° while the user pans the camera.
                        if (lastCapturedAt != 0L &&
                            now - lastCapturedAt > PARTIAL_TIMEOUT_MS) {
                            clearPartial()
                            scanMessage = "Codes expired - please scan again"
                        }

                        // If a different code appears AFTER one is confirmed,
                        // clear both sides rather than silently mixing two cards.
                        val changedId = heldId != null && foundId != null && heldId != foundId
                        val changedNum = heldNumber != null && found != null &&
                                heldNumber?.card?.cardNum != found.card.cardNum
                        if (changedId || changedNum) {
                            clearPartial()
                            scanMessage = "Card changed - start again"
                        } else {
                            if (heldId == null && foundId != null) {
                                val same = tentativeId == foundId &&
                                        now - tentativeIdAt < PARTIAL_WINDOW_MS
                                tentativeIdCount = if (same) tentativeIdCount + 1 else 1
                                tentativeId = foundId
                                tentativeIdAt = now
                                if (tentativeIdCount >= 2) {
                                    heldId = foundId
                                    lastCapturedAt = now
                                    tentativeId = null
                                    tentativeIdCount = 0
                                }
                            }
                            if (heldNumber == null && found != null) {
                                val key = found.card.cardNum
                                val same = tentativeNumber == key &&
                                        now - tentativeNumberAt < PARTIAL_WINDOW_MS
                                tentativeNumberCount = if (same) tentativeNumberCount + 1 else 1
                                tentativeNumber = key
                                tentativeNumberAt = now
                                val required = if (found.corrected) 3 else 2
                                if (tentativeNumberCount >= required) {
                                    heldNumber = found
                                    lastCapturedAt = now
                                    tentativeNumber = null
                                    tentativeNumberCount = 0
                                }
                            }

                            if (heldId != null && heldNumber != null) {
                                val card = heldNumber!!.card
                                val expectedId = dbCardId(card)
                                if (expectedId != null && expectedId == heldId) {
                                    // Both captured codes match the same catalog identity.
                                    // But an incomplete variant suffix still needs a visual choice.
                                    if (heldNumber!!.suffixMayBeMissing) {
                                        requestArtworkVerification(card)
                                    } else {
                                        commitScannedCard(card)
                                    }
                                    clearPartial()
                                } else {
                                    // Never auto-select artwork from ID alone.
                                    clearPartial()
                                    scanMessage = "ID / Num mismatch - rescan same card"
                                }
                            } else {
                                currentTarget = heldNumber?.card
                                currentZone = if (heldNumber != null) {
                                    RectF(observation.numZoneOnScreen)
                                } else null
                                scanMessage = when {
                                    heldId != null -> "ID $heldId saved - scan Card Num"
                                    heldNumber != null && foundId != null ->
                                        "ID $foundId seen (${tentativeIdCount}/2) - confirming"
                                    heldNumber != null && observation.rawIdOcr.isNotBlank() ->
                                        "ID OCR: ${observation.rawIdOcr.take(28)}"
                                    heldNumber != null ->
                                        "Num ${heldNumber!!.card.cardNum} saved - scan Card ID"
                                    foundId != null -> "Reading Card ID..."
                                    found != null -> "Reading Card Num..."
                                    obviousNumberInRawOcr(observation.rawNumberOcr) != null -> {
                                        val candidate = obviousNumberInRawOcr(observation.rawNumberOcr)!!
                                        if (index.containsNumber(candidate)) {
                                            "OCR $candidate matched format • verifying"
                                        } else {
                                            "Num $candidate not in card catalog"
                                        }
                                    }
                                    observation.rawNumberOcr.isNotBlank() ->
                                        "Zoom OCR: ${observation.rawNumberOcr.take(19)}"
                                    else -> "Zoom Assist: move to ID or Num"
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (hasPermission) {
        DisposableEffect(context, lifecycleOwner, previewView) {
            val future = ProcessCameraProvider.getInstance(context)
            var boundProvider: ProcessCameraProvider? = null
            var disposed = false
            future.addListener({
                if (!disposed && alive.get() && !executor.isShutdown) {
                    try {
                        val provider = future.get()
                        boundProvider = provider
                        // Prefer 1080p analysis for tiny numbers printed on
                        // the card edge. CameraX may fall back if unsupported.
                        val selector = ResolutionSelector.Builder()
                            .setResolutionStrategy(ResolutionStrategy(
                                Size(1920, 1080),
                                ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER
                            )).build()
                        val preview = Preview.Builder().setResolutionSelector(selector)
                            .build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
                        val analysis = ImageAnalysis.Builder()
                            .setResolutionSelector(selector)
                            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                            .build()
                        analysis.setAnalyzer(executor) { proxy ->
                            val now = SystemClock.elapsedRealtime()
                            val window = scanWindow.get()
                            if (busy.get() || now - lastFrameAt.get() < FRAME_INTERVAL_MS ||
                                !alive.get() || window == null || proxy.planes.isEmpty()) {
                                proxy.close()
                            } else if (!busy.compareAndSet(false, true)) {
                                proxy.close()
                            } else {
                                lastFrameAt.set(now)
                                val attempt = frames.getAndIncrement()
                                val mode = scanModeForAnalyzer
                                val rotate = proxy.imageInfo.rotationDegrees
                                val imageW = if (rotate == 90 || rotate == 270) proxy.height else proxy.width
                                val imageH = if (rotate == 90 || rotate == 270) proxy.width else proxy.height

                                if (mode == ScanMode.ZOOM_ASSISTED) {
                                    // Keep original zoom-assisted full-frame scan when searching
                                    // either code. After NUM is saved, ID is horizontal, so
                                    // stop cycling 90/270/180 degree rotations.
                                    val seekId = searchingHorizontalId
                                    val angle = if (seekId) 0f else when (attempt % 8) {
                                        0, 1, 2 -> 0f
                                        3, 4 -> 90f
                                        5, 6 -> 270f
                                        else -> 180f
                                    }
                                    var fullBitmap: Bitmap? = null
                                    var idBitmap: Bitmap? = null
                                    try {
                                        val rect = screenToImageRect(
                                            window.frame, window.previewWidth, window.previewHeight,
                                            imageW, imageH
                                        )
                                        if (rect != null) {
                                            fullBitmap = captureRoi(
                                                proxy, rect, rotate, angle,
                                                targetShortSide = 180f
                                            )
                                        }
                                        if (seekId) {
                                            // The user has moved the camera to the left-lower
                                            // identifier, which can now be near the CENTER of
                                            // the screen, not necessarily at the frame's bottom.
                                            // Alternate overlapping regions while keeping text
                                            // upright; both include the location in the screenshot.
                                            val rel = if (attempt % 2 == 0) {
                                                RectF(0.02f, 0.28f, 0.98f, 0.80f)
                                            } else {
                                                RectF(0.02f, 0.48f, 0.98f, 0.98f)
                                            }
                                            val idRect = screenToImageRect(
                                                subRegion(window.frame, rel),
                                                window.previewWidth, window.previewHeight,
                                                imageW, imageH
                                            )
                                            if (idRect != null) {
                                                idBitmap = captureRoi(
                                                    proxy, idRect, rotate, 0f,
                                                    targetShortSide = 260f
                                                )
                                            }
                                        }
                                    } catch (e: Exception) {
                                        Log.w("ConanScannerV343", "Zoom ROI", e)
                                    } finally {
                                        proxy.close()
                                    }
                                    val bitmap = fullBitmap
                                    if (bitmap == null) {
                                        idBitmap?.recycle()
                                        busy.set(false)
                                    } else {
                                        val extraBitmap = idBitmap
                                        val completed = AtomicBoolean(false)
                                        fun finish(primary: Text?, focused: Text? = null) {
                                            if (!completed.compareAndSet(false, true)) return
                                            val number = primary?.let { latestIndex.detect(it) }
                                            val primaryId = primary?.let(::readPrintedCardId)
                                            val focusedId = focused?.let(::readPrintedCardId)
                                            // Conflicting IDs on the same frame are unsafe.
                                            val parsedIds = listOfNotNull(primaryId, focusedId)
                                                .distinct()
                                            val foundId = parsedIds.singleOrNull()
                                            val raw = primary?.text?.replace('\n', ' ')
                                                ?.take(60) ?: ""
                                            val idRaw = focused?.text?.replace('\n', ' ')
                                                ?.take(60) ?: ""
                                            mainHandler.post {
                                                if (alive.get()) consume(ScanObservation(
                                                    ScanMode.ZOOM_ASSISTED, number, foundId,
                                                    CardPose.NORMAL, RectF(window.frame),
                                                    raw, angle, 0, idRaw
                                                ))
                                            }
                                            bitmap.recycle()
                                            extraBitmap?.recycle()
                                            busy.set(false)
                                        }
                                        try {
                                            assistRecognizer.process(InputImage.fromBitmap(bitmap, 0))
                                                .addOnSuccessListener { primary ->
                                                    val primaryId = readPrintedCardId(primary)
                                                    if (extraBitmap == null || primaryId != null) {
                                                        finish(primary)
                                                    } else {
                                                        try {
                                                            idRecognizer.process(
                                                                InputImage.fromBitmap(extraBitmap, 0)
                                                            )
                                                                .addOnSuccessListener { focused ->
                                                                    finish(primary, focused)
                                                                }
                                                                .addOnFailureListener {
                                                                    finish(primary)
                                                                }
                                                        } catch (error: Exception) {
                                                            finish(primary)
                                                        }
                                                    }
                                                }
                                                .addOnFailureListener { finish(null) }
                                        } catch (e: Exception) { finish(null) }
                                    }
                                } else {
                                    // FULL CARD: two physical corners, normal first and
                                    // rotated orientation when unsuccessful. Preserve the
                                    // V3.3 dual-band right-edge number detection.
                                    val pose = preferredPose.get() ?: if ((attempt / 12) % 2 == 0)
                                        CardPose.NORMAL else CardPose.UPSIDE_DOWN
                                    val band = preferredNumberBand.get() ?: (attempt % 2)
                                    val zones = computeZones(window.landscapeCard, pose, band)
                                    val idZone = subRegion(window.frame, zones.cardId)
                                    val numZone = subRegion(window.frame, zones.cardNum)
                                    var idBitmap: Bitmap? = null
                                    var numBitmap: Bitmap? = null
                                    var angle = 0f
                                    try {
                                        val idRect = screenToImageRect(idZone, window.previewWidth,
                                            window.previewHeight, imageW, imageH)
                                        val numRect = screenToImageRect(numZone, window.previewWidth,
                                            window.previewHeight, imageW, imageH)
                                        if (idRect != null && numRect != null) {
                                            idBitmap = captureRoi(proxy, idRect, rotate,
                                                if (pose == CardPose.NORMAL) 0f else 180f)
                                            angle = preferredNumberAngle.get() ?: if ((attempt / 6) % 2 == 0) {
                                                zones.numRotation
                                            } else when (zones.numRotation) {
                                                90f -> 270f
                                                270f -> 90f
                                                0f -> 180f
                                                else -> 0f
                                            }
                                            numBitmap = captureRoi(proxy, numRect, rotate, angle,
                                                targetShortSide = 270f)
                                        }
                                    } catch (e: Exception) {
                                        Log.w("ConanScannerV34", "Full ROI", e)
                                    } finally {
                                        proxy.close()
                                    }
                                    val idImg = idBitmap
                                    val numImg = numBitmap
                                    if (idImg == null || numImg == null) {
                                        idImg?.recycle()
                                        numImg?.recycle()
                                        busy.set(false)
                                    } else {
                                        var raw = ""
                                        val completed = AtomicBoolean(false)
                                        fun finish(number: NumberMatch?, id: String?) {
                                            if (!completed.compareAndSet(false, true)) return
                                            mainHandler.post {
                                                if (alive.get()) consume(ScanObservation(
                                                    ScanMode.FULL_CARD, number, id, pose,
                                                    numZone, raw, angle, band
                                                ))
                                            }
                                            idImg.recycle()
                                            numImg.recycle()
                                            busy.set(false)
                                        }
                                        try {
                                            numberRecognizer.process(InputImage.fromBitmap(numImg, 0))
                                                .addOnSuccessListener { ocr ->
                                                    raw = ocr.text.replace('\n', ' ').trim().take(60)
                                                    val number = latestIndex.detect(ocr)
                                                    // Read left-side ID occasionally even when
                                                    // the right-side Num isn't detected.
                                                    if (number == null && attempt % 4 != 0) {
                                                        finish(null, null)
                                                    } else try {
                                                        idRecognizer.process(InputImage.fromBitmap(idImg, 0))
                                                            .addOnSuccessListener { idText ->
                                                                finish(number, readPrintedCardId(idText))
                                                            }
                                                            .addOnFailureListener { finish(number, null) }
                                                    } catch (e: Exception) { finish(number, null) }
                                                }
                                                .addOnFailureListener { finish(null, null) }
                                        } catch (e: Exception) { finish(null, null) }
                                    }
                                }
                            }
                        }
                        provider.unbindAll()
                        camera = provider.bindToLifecycle(
                            lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis
                        )
                        camera?.cameraControl?.setZoomRatio(zoom)
                        cameraError = null
                    } catch (e: Exception) {
                        cameraError = e.message ?: "Camera unavailable"
                        Log.e("ConanScannerV3", "Camera binding", e)
                    }
                }
            }, ContextCompat.getMainExecutor(context))
            onDispose {
                disposed = true
                boundProvider?.unbindAll()
                camera = null
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            alive.set(false)
            executor.shutdown()
            numberRecognizer.close()
            idRecognizer.close()
            assistRecognizer.close()
        }
    }

    // ========================================================
    // ORIGINAL SCREEN DESIGN, WITH V3 DETECTION
    // ========================================================
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        if (hasPermission) {
            AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
        } else {
            Column(Modifier.align(Alignment.Center).padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally) {
                Text("อนุญาตให้ใช้กล้องเพื่อสแกนการ์ด", color = Color.White)
                Spacer(Modifier.height(12.dp))
                Button(onClick = { permissionRequest.launch(Manifest.permission.CAMERA) }) {
                    Text("Allow Camera")
                }
            }
        }

        if (hasPermission) BoxWithConstraints(
            modifier = Modifier.fillMaxSize()
                .pointerInput(camera) {
                    detectTransformGestures { _, pan, zoomFactor, _ ->
                        if (abs(zoomFactor - 1f) > 0.002f) setZoom(zoom * zoomFactor)
                        if (pan != Offset.Zero) {
                            val current = frameCenter ?: Offset(
                                size.width / 2f, size.height / 2f
                            )
                            frameCenter = Offset(
                                (current.x + pan.x).coerceIn(0f, size.width.toFloat()),
                                (current.y + pan.y).coerceIn(0f, size.height.toFloat())
                            )
                        }
                    }
                }
                .pointerInput(camera) {
                    detectTapGestures { point ->
                        focusPoint = point
                        val metering = previewView.meteringPointFactory.createPoint(point.x, point.y)
                        camera?.cameraControl?.startFocusAndMetering(
                            FocusMeteringAction.Builder(metering).build()
                        )
                    }
                }
        ) {
            val screenW = with(density) { maxWidth.toPx() }
            val screenH = with(density) { maxHeight.toPx() }
            val center = frameCenter ?: Offset(screenW / 2f, screenH / 2f)
            val width = with(density) { minOf(frameWidth, maxWidth - 16.dp).toPx() }
            val height = with(density) { minOf(frameHeight, maxHeight - 32.dp).toPx() }
            val left = (center.x + gyroX - width / 2f).coerceIn(0f, (screenW - width).coerceAtLeast(0f))
            val top = (center.y + gyroY - height / 2f).coerceIn(0f, (screenH - height).coerceAtLeast(0f))
            val right = left + width
            val bottom = top + height
            val frame = RectF(left, top, right, bottom)
            SideEffect { scanWindow.set(ScanWindow(frame, screenW.roundToInt(), screenH.roundToInt(), landscapeFrame)) }

            Canvas(Modifier.fillMaxSize()) {
                val overlay = Path().apply {
                    addRect(ComposeRect(0f, 0f, size.width, size.height))
                    addRoundRect(RoundRect(left, top, right, bottom, CornerRadius(16.dp.toPx())))
                    fillType = PathFillType.EvenOdd
                }
                drawPath(overlay, Color.Black.copy(alpha = 0.55f))
                drawRoundRect(ScanV3Style.red.copy(alpha = .3f), Offset(left, top),
                    ComposeSize(width, height), CornerRadius(16.dp.toPx()), style = Stroke(1.dp.toPx()))
                val color = if (currentTarget != null) ScanV3Style.green else ScanV3Style.red
                val d = 28.dp.toPx()
                val stroke = 3.5.dp.toPx()
                drawLine(color, Offset(left, top + d), Offset(left, top), stroke, StrokeCap.Round)
                drawLine(color, Offset(left, top), Offset(left + d, top), stroke, StrokeCap.Round)
                drawLine(color, Offset(right - d, top), Offset(right, top), stroke, StrokeCap.Round)
                drawLine(color, Offset(right, top), Offset(right, top + d), stroke, StrokeCap.Round)
                drawLine(color, Offset(left, bottom - d), Offset(left, bottom), stroke, StrokeCap.Round)
                drawLine(color, Offset(left, bottom), Offset(left + d, bottom), stroke, StrokeCap.Round)
                drawLine(color, Offset(right - d, bottom), Offset(right, bottom), stroke, StrokeCap.Round)
                drawLine(color, Offset(right, bottom - d), Offset(right, bottom), stroke, StrokeCap.Round)
            }

            // Spirit level badge.
            Surface(
                modifier = Modifier.offset {
                    IntOffset((left + width / 2f - 55.dp.toPx()).roundToInt(),
                        (top - 38.dp.toPx()).roundToInt())
                },
                color = if (level) Color(0xFF1B382B) else Color(0xFF381B1B),
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(1.dp, if (level) ScanV3Style.green else ScanV3Style.red)
            ) {
                Row(Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(18.dp).background(Color.Black, CircleShape),
                        contentAlignment = Alignment.Center) {
                        Box(Modifier.offset(spiritX.dp, spiritY.dp).size(6.dp).background(
                            if (level) ScanV3Style.green else ScanV3Style.red, CircleShape))
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(if (level) "Level" else "Tilted", color = Color.White,
                        fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }

            Surface(modifier = Modifier.offset { IntOffset(left.roundToInt(), (bottom + 10.dp.toPx()).roundToInt()) },
                color = Color.Black.copy(alpha = .85f), shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, if (currentTarget == null) ScanV3Style.red else ScanV3Style.green)) {
                Text(if (landscapeFrame) "CASE / EVIDENCE" else "CARD ID  •  CARD NUM",
                    Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }

            // Highlight the *right-hand printed number region* when matched.
            if (currentTarget != null && currentZone != null) {
                val target = currentZone!!
                Canvas(Modifier.fillMaxSize()) {
                    drawRoundRect(ScanV3Style.green, Offset(target.left, target.top),
                        ComposeSize(target.width(), target.height()),
                        CornerRadius(6.dp.toPx()), style = Stroke(2.5.dp.toPx()))
                }
                Surface(modifier = Modifier.offset { IntOffset(target.left.roundToInt(),
                    (target.top - 48.dp.toPx()).roundToInt()) },
                    color = Color(0xFF1E1E1E), shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, ScanV3Style.green)) {
                    Column(Modifier.padding(horizontal = 8.dp, vertical = 5.dp)) {
                        Text(currentTarget!!.cardNum, color = ScanV3Style.green,
                            fontWeight = FontWeight.ExtraBold, fontSize = 13.sp)
                        Text("ID: ${currentTarget!!.cardId}", color = Color.LightGray, fontSize = 10.sp)
                    }
                }
            }
            focusPoint?.let { p ->
                Box(Modifier.offset { IntOffset((p.x - 26.dp.toPx()).roundToInt(),
                    (p.y - 26.dp.toPx()).roundToInt()) }.size(52.dp)
                    .border(1.5.dp, ScanV3Style.yellow, CircleShape),
                    contentAlignment = Alignment.Center) {
                    Box(Modifier.size(6.dp).background(ScanV3Style.yellow, CircleShape))
                }
            }
        }

        Surface(Modifier.align(Alignment.TopCenter).fillMaxWidth(),
            color = Color.Black.copy(alpha = .75f)) {
            Row(Modifier.statusBarsPadding().padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBackClick) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                }
                Text("Card Scanner V3.4.3", Modifier.weight(1f), color = Color.White,
                    fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Surface(shape = RoundedCornerShape(12.dp),
                    color = if (landscapeFrame) ScanV3Style.yellow else Color.White.copy(alpha = .15f),
                    modifier = Modifier.clickable { landscapeFrame = !landscapeFrame; restartFullCard() }) {
                    Row(Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.AspectRatio, contentDescription = "Orientation",
                            modifier = Modifier.size(16.dp),
                            tint = if (landscapeFrame) Color.Black else Color.White)
                        Spacer(Modifier.width(5.dp))
                        Text(if (landscapeFrame) "Landscape" else "Portrait",
                            color = if (landscapeFrame) Color.Black else Color.White,
                            fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        Column(Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally) {
            if (scanMode == ScanMode.ZOOM_ASSISTED && scannedCardNum == null) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = Color.Black.copy(alpha = .90f),
                    border = BorderStroke(1.dp, ScanV3Style.yellow.copy(alpha = .7f)),
                    modifier = Modifier.padding(bottom = 9.dp)
                ) {
                    Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("ZOOM ASSIST • capture either code first",
                            color = ScanV3Style.yellow, fontSize = 11.sp,
                            fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(5.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("ID: ${heldId ?: "WAIT"}",
                                color = if (heldId == null) Color.LightGray else ScanV3Style.green,
                                fontSize = 12.sp)
                            Spacer(Modifier.width(14.dp))
                            Text("NUM: ${heldNumber?.card?.cardNum ?: "WAIT"}",
                                color = if (heldNumber == null) Color.LightGray else ScanV3Style.green,
                                fontSize = 12.sp)
                            Spacer(Modifier.width(10.dp))
                            TextButton(onClick = { clearPartial(); resetLock();
                                scanMessage = "Zoom Assist: start again" },
                                contentPadding = PaddingValues(0.dp)) {
                                Text("Reset", fontSize = 11.sp, color = ScanV3Style.yellow)
                            }
                        }
                    }
                }
            }
            Surface(color = Color.Black.copy(alpha = .84f),
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(1.dp, Color.White.copy(alpha = .2f))) {
                Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(8.dp).background(
                        if (currentTarget == null) ScanV3Style.red else ScanV3Style.green, CircleShape))
                    Spacer(Modifier.width(8.dp))
                    Text(cameraError ?: scanMessage, color = Color.White, fontSize = 12.sp)
                }
            }
            Spacer(Modifier.height(14.dp))
            Surface(color = Color.Black.copy(alpha = .85f), shape = RoundedCornerShape(30.dp),
                border = BorderStroke(1.dp, Color.White.copy(alpha = .2f))) {
                Row(Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Surface(shape = CircleShape, color = ScanV3Style.red) {
                        Text(String.format(Locale.US, "%.1fx", zoom),
                            Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            fontWeight = FontWeight.Bold, color = Color.White, fontSize = 12.sp)
                    }
                    listOf(1f, 1.5f, 2f, 3f).forEach { ratio ->
                        val selected = abs(zoom - ratio) < .05f
                        Box(Modifier.background(if (selected) Color.White.copy(alpha = .2f)
                        else Color.Transparent, CircleShape)
                            .clickable { setZoom(ratio) }
                            .padding(horizontal = 9.dp, vertical = 8.dp)) {
                            Text("${ratio}x".replace(".0", ""), color = Color.LightGray, fontSize = 11.sp)
                        }
                    }
                    IconButton(onClick = { setZoom(1f); frameCenter = null; restartFullCard() },
                        modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Refresh, contentDescription = "Reset Zoom and Frame",
                            tint = Color.White, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }

        val card = viewModel.cardList.firstOrNull { it.cardNum == scannedCardNum }
        AnimatedVisibility(visible = card != null,
            enter = fadeIn(tween(200)) + scaleIn(tween(250)),
            exit = fadeOut(tween(150)) + scaleOut(tween(150)),
            modifier = Modifier.fillMaxSize()) {
            if (card != null) Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .92f)),
                contentAlignment = Alignment.Center) {
                Card(modifier = Modifier.fillMaxWidth(.85f),
                    shape = RoundedCornerShape(28.dp),
                    colors = CardDefaults.cardColors(containerColor = ScanV3Style.popup),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = .15f))) {
                    Column(Modifier.fillMaxWidth().padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally) {
                        Surface(color = Color(0xFF1B382B), shape = RoundedCornerShape(50),
                            border = BorderStroke(1.dp, ScanV3Style.green)) {
                            Row(Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.CheckCircle, contentDescription = null,
                                    tint = ScanV3Style.green, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Scan Successful!", color = Color(0xFFA5D6A7),
                                    fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                        Spacer(Modifier.height(20.dp))
                        AsyncImage(model = viewModel.getCardImageModel(card),
                            contentDescription = card.cardNum,
                            modifier = Modifier.height(if (landscapeFrame) 200.dp else 260.dp)
                                .fillMaxWidth().clip(RoundedCornerShape(16.dp))
                                .border(2.dp, ScanV3Style.red, RoundedCornerShape(16.dp)),
                            contentScale = ContentScale.Fit)
                        Spacer(Modifier.height(18.dp))
                        Text(card.cardNum, color = Color.White, fontSize = 24.sp,
                            fontWeight = FontWeight.ExtraBold)
                        Text("Card ID: ${card.cardId}", color = Color.Gray, fontSize = 13.sp)
                        Spacer(Modifier.height(14.dp))
                        Surface(color = ScanV3Style.red.copy(alpha = .15f),
                            shape = RoundedCornerShape(12.dp)) {
                            Text("In Collection: ${viewModel.getQuantity(card.cardNum)}",
                                Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                                color = Color(0xFFFF8A80), fontSize = 12.sp,
                                fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.height(22.dp))
                        Button(onClick = {
                            scannedCardNum = null
                            nextAllowedAt = SystemClock.elapsedRealtime() + 700L
                            restartFullCard()
                            setZoom(1f)
                        }, modifier = Modifier.fillMaxWidth().height(50.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = ScanV3Style.red),
                            shape = RoundedCornerShape(14.dp)) {
                            Text("Scan Next", fontWeight = FontWeight.Bold)
                        }
                        TextButton(onClick = {
                            scannedCardNum = null
                            waitingForRemoval = false
                            lastAccepted = null
                            nextAllowedAt = SystemClock.elapsedRealtime() + 1200L
                            restartFullCard()
                            setZoom(1f)
                        }) { Text("Scan same card again", color = Color.LightGray) }
                    }
                }
            }
        }


        // ====================================================
        // SUFFIX / ARTWORK CONFIRMATION
        // Only appears if OCR may have cut a suffix from the true number.
        // Never select Normal/P/P2/SEC automatically when ambiguous.
        // ====================================================
        if (reviewChoices.isNotEmpty() && scannedCardNum == null) {
            Dialog(onDismissRequest = {
                reviewChoices = emptyList()
                reviewSelection = null
                clearPartial()
                resetLock()
                scanMessage = "Verification cancelled - rescan Card Num"
            }) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    color = ScanV3Style.popup,
                    border = BorderStroke(1.dp, ScanV3Style.yellow)
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Verify artwork / suffix", color = Color.White,
                            fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Spacer(Modifier.height(6.dp))
                        Text("OCR saw $reviewDetectedNum. A longer variant may exist " +
                                "(P, P2, SEC1, SEC2). Confirm the exact card shown.",
                            color = Color.LightGray, fontSize = 12.sp)
                        Spacer(Modifier.height(12.dp))
                        Column(Modifier.heightIn(max = 380.dp).verticalScroll(rememberScrollState())) {
                            reviewChoices.forEach { choice ->
                                val isChosen = reviewSelection == choice.cardNum
                                Surface(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                                        .clickable { reviewSelection = choice.cardNum },
                                    color = if (isChosen) Color(0xFF234832) else Color(0xFF252B36),
                                    shape = RoundedCornerShape(12.dp),
                                    border = BorderStroke(1.dp,
                                        if (isChosen) ScanV3Style.green else Color.DarkGray)
                                ) {
                                    Row(Modifier.padding(8.dp),
                                        verticalAlignment = Alignment.CenterVertically) {
                                        AsyncImage(
                                            model = viewModel.getCardImageModel(choice),
                                            contentDescription = choice.cardNum,
                                            modifier = Modifier.width(54.dp).height(76.dp),
                                            contentScale = ContentScale.Fit
                                        )
                                        Spacer(Modifier.width(10.dp))
                                        Column(Modifier.weight(1f)) {
                                            Text(choice.cardNum, color = Color.White,
                                                fontWeight = FontWeight.Bold)
                                            Text(choice.name, color = Color.LightGray,
                                                fontSize = 11.sp, maxLines = 2)
                                            Text("ID: ${choice.cardId}", color = Color.LightGray,
                                                fontSize = 10.sp)
                                        }
                                        RadioButton(selected = isChosen,
                                            onClick = { reviewSelection = choice.cardNum })
                                    }
                                }
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically) {
                            TextButton(onClick = {
                                reviewChoices = emptyList()
                                reviewSelection = null
                                clearPartial()
                                resetLock()
                                scanMessage = "Rescan to read full suffix"
                            }) { Text("Rescan", color = Color.LightGray) }
                            Spacer(Modifier.width(8.dp))
                            Button(
                                enabled = reviewSelection != null,
                                onClick = {
                                    val selected = reviewChoices.firstOrNull {
                                        it.cardNum == reviewSelection
                                    }
                                    if (selected != null) {
                                        commitScannedCard(selected)
                                        clearPartial()
                                        resetLock()
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = ScanV3Style.red)
                            ) { Text("Add selected") }
                        }
                    }
                }
            }
        }
    }
}

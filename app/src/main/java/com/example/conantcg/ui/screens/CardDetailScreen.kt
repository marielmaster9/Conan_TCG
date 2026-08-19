package com.example.conantcg.ui.screens

import android.content.ContentValues
import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.SubcomposeAsyncImage
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.example.conantcg.R
import com.example.conantcg.data.model.WebCard
import com.example.conantcg.ui.viewmodel.CardViewModel
import com.example.conantcg.utils.ConanTheme
import com.example.conantcg.utils.GoogleTranslator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.absoluteValue
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.TextRange

// ฟังก์ชันสำหรับเซฟรูปลง Gallery มือถือ
fun saveBitmapToGallery(context: Context, bitmap: Bitmap, displayName: String) {
    val contentValues = ContentValues().apply {
        put(MediaStore.MediaColumns.DISPLAY_NAME, "$displayName.jpg")
        put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // สร้างโฟลเดอร์ ConanTCG ในแกลเลอรี
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/ConanTCG")
        }
    }

    val resolver = context.contentResolver
    val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)

    if (uri != null) {
        try {
            resolver.openOutputStream(uri)?.use { stream ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 100, stream)
            }
            Toast.makeText(context, "Saved to Gallery!", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(context, "Failed to save: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    } else {
        Toast.makeText(context, "Failed to save image", Toast.LENGTH_SHORT).show()
    }
}

// --- SwipeableCardDetailDialog ---
@Composable
fun SwipeableCardDetailDialog(
    initialCards: List<WebCard>,
    startIndex: Int,
    viewModel: CardViewModel,
    isCollectionMode: Boolean,
    showWishlistButton: Boolean = false,
    onDismiss: () -> Unit
) {
    val pagerState = rememberPagerState(initialPage = startIndex, pageCount = { initialCards.size })
    val scope = rememberCoroutineScope()

    var isEditingMode by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Box(modifier = Modifier.fillMaxSize().clickable(onClick = onDismiss))
            HorizontalPager(
                state = pagerState,
                contentPadding = PaddingValues(horizontal = 30.dp),
                pageSpacing = 1.dp,
                modifier = Modifier.fillMaxWidth().fillMaxHeight(0.90f)
            ) { page ->
                val pageOffset = (pagerState.currentPage - page) + pagerState.currentPageOffsetFraction
                val scale = lerp(1f, 0.85f, pageOffset.absoluteValue.coerceIn(0f, 1f))
                Card(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = 24.dp)
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                            alpha = lerp(1f, 0.6f, pageOffset.absoluteValue.coerceIn(0f, 1f))
                        },
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(8.dp)
                ) {
                    SingleCardDetailView(
                        card = initialCards[page],
                        viewModel = viewModel,
                        isCollectionMode = isCollectionMode,
                        showWishlistButton = showWishlistButton,
                        isCurrentPage = (page == pagerState.currentPage),
                        isEditingMode = isEditingMode,
                        onEditingModeChange = { isEditingMode = it }
                    )
                }
            }
            if (pagerState.currentPage > 0) {
                IconButton(onClick = { scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) } }, modifier = Modifier.align(Alignment.CenterStart).padding(start = 8.dp).background(Color.Black.copy(alpha = 0.3f), CircleShape)) {
                    Icon(imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Prev", tint = Color.White)
                }
            }
            if (pagerState.currentPage < initialCards.size - 1) {
                IconButton(onClick = { scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) } }, modifier = Modifier.align(Alignment.CenterEnd).padding(end = 8.dp).background(Color.Black.copy(alpha = 0.3f), CircleShape)) {
                    Icon(imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next", tint = Color.White)
                }
            }
        }
    }
}

// --- SingleCardDetailView ---
@Composable
fun SingleCardDetailView(
    card: WebCard,
    viewModel: CardViewModel,
    isCollectionMode: Boolean,
    showWishlistButton: Boolean = false,
    isCurrentPage: Boolean = false,
    isEditingMode: Boolean = false,
    onEditingModeChange: (Boolean) -> Unit = {}
) {
    val quantity = viewModel.getQuantity(card.cardNum)
    // ✅ 2.1 สร้าง State สำหรับ TextFieldValue เพื่อควบคุม Cursor
    var quantityTextValue by remember {
        mutableStateOf(
            TextFieldValue(
                text = quantity.toString(),
                selection = TextRange(quantity.toString().length)
            )
        )
    }
    // ✅ 2.2 ซิงค์ค่า quantity และดัน Cursor ไปไว้หลังสุดเมื่อเปลี่ยนการ์ด หรือกดปุ่ม +/-
    LaunchedEffect(card.cardNum, quantity) {
        val currentStr = quantity.toString()
        // อัปเดตก็ต่อเมื่อค่าไม่ตรงกัน เพื่อไม่ให้กวนจังหวะตอนผู้ใช้กำลังพิมพ์
        if (quantityTextValue.text != currentStr) {
            quantityTextValue = TextFieldValue(
                text = currentStr,
                selection = TextRange(currentStr.length) // บังคับ Cursor ไปหลังสุด
            )
        }
    }
    val imageModel = viewModel.getCardImageModel(card)
    var currentLanguage by remember { mutableStateOf("JP") }

    val context = LocalContext.current
    val focusRequester = remember { FocusRequester() }
    val scope = rememberCoroutineScope() // สำหรับแอนิเมชันและดาวน์โหลดรูป

    if (isCollectionMode) {
        LaunchedEffect(isCurrentPage, isEditingMode) {
            if (isCurrentPage && isEditingMode) {
                delay(150)
                try { focusRequester.requestFocus() } catch (e: Exception) {}
            }
        }
    }

    var nameTh by remember { mutableStateOf<String?>(null) }
    var typeTh by remember { mutableStateOf<String?>(null) }
    var colorTh by remember { mutableStateOf<String?>(null) }
    var featureTh by remember { mutableStateOf<String?>(null) }

    var nameEn by remember { mutableStateOf<String?>(null) }
    var typeEn by remember { mutableStateOf<String?>(null) }
    var colorEn by remember { mutableStateOf<String?>(null) }
    var featureEn by remember { mutableStateOf<String?>(null) }

    var isTranslating by remember { mutableStateOf(false) }

    val featureText = card.feature.replace(Regex("<.*?>"), "").trim()
    val cutInText = card.cut_in.replace(Regex("<.*?>"), "").trim()
    val hiramekiText = card.hirameki.replace(Regex("<.*?>"), "").trim()
    val hensoText = card.henso.replace(Regex("<.*?>"), "").trim()

    val originalText = remember(card) {
        buildString {
            if (featureText.isNotBlank() && featureText != "null") append("★[カードの能力]\n").append(featureText).append("\n\n")
            if (cutInText.isNotBlank() && cutInText != "null") append("♣[カットイン]\n").append(cutInText).append("\n\n")
            if (hiramekiText.isNotBlank() && hiramekiText != "null") append("♦[ヒラメキ]\n").append(hiramekiText).append("\n\n")
            if (hensoText.isNotBlank() && hensoText != "null") append("♠[変装]\n").append(hensoText)
        }.trim()
    }

    LaunchedEffect(currentLanguage, card.cardNum) {
        if (currentLanguage == "TH" && nameTh == null) {
            isTranslating = true
            val delimiter = " ||| "
            val metaQuery = "${card.name}$delimiter${card.type}$delimiter${card.color}"
            val metaResult = GoogleTranslator.translate(context, metaQuery, "th")
            if (metaResult != null) {
                val parts = metaResult.split("|||")
                if (parts.size >= 1) nameTh = parts[0].trim()
                if (parts.size >= 2) typeTh = parts[1].trim()
                if (parts.size >= 3) colorTh = parts[2].trim()
            }
            if (originalText.isNotBlank()) {
                val textResult = GoogleTranslator.translate(context, originalText, "th")
                if (textResult != null) featureTh = textResult.trim()
            }
            isTranslating = false
        } else if (currentLanguage == "EN" && nameEn == null) {
            isTranslating = true
            val delimiter = " ||| "
            val metaQuery = "${card.name}$delimiter${card.type}$delimiter${card.color}"
            val metaResult = GoogleTranslator.translate(context, metaQuery, "en")
            if (metaResult != null) {
                val parts = metaResult.split("|||")
                if (parts.size >= 1) nameEn = parts[0].trim()
                if (parts.size >= 2) typeEn = parts[1].trim()
                if (parts.size >= 3) colorEn = parts[2].trim()
            }
            if (originalText.isNotBlank()) {
                val textResult = GoogleTranslator.translate(context, originalText, "en")
                if (textResult != null) featureEn = textResult.trim()
            }
            isTranslating = false
        }
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(16.dp).verticalScroll(rememberScrollState())) {

        // กล่องครอบรูปภาพ
        Box(modifier = Modifier.fillMaxWidth()) {
            SubcomposeAsyncImage(
                model = ImageRequest.Builder(LocalContext.current).data(imageModel).build(),
                contentDescription = null,
                modifier = Modifier.height(300.dp).fillMaxWidth(),
                contentScale = ContentScale.Fit
            )

            // ✅ แถบปุ่มฝั่งขวาบน (เรียงลงมา)
            Column(
                modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {

                // 1. ปุ่มหัวใจ (โชว์เฉพาะเปิด showWishlistButton)
                if (showWishlistButton) {
                    val isWishlisted = viewModel.isWishlisted(card.cardNum)
                    var triggerPulse by remember { mutableStateOf(false) }
                    val heartScale by animateFloatAsState(
                        targetValue = if (triggerPulse) 1.3f else 1f,
                        animationSpec = spring(dampingRatio = 0.5f, stiffness = 500f),
                        finishedListener = { if (it == 1.3f) triggerPulse = false },
                        label = "heartPulseBounce"
                    )
                    val animatedBgColor by animateColorAsState(
                        targetValue = if (isWishlisted) Color.White.copy(alpha = 0.9f) else Color.Black.copy(alpha = 0.5f),
                        animationSpec = tween(300),
                        label = "heartBgColorFade"
                    )

                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(animatedBgColor)
                            .graphicsLayer {
                                scaleX = heartScale
                                scaleY = heartScale
                            }
                            .clickable {
                                viewModel.toggleWishlist(card.cardNum)
                                triggerPulse = true
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        val animatedIconColor by animateColorAsState(
                            targetValue = if (isWishlisted) Color(0xFFFF4081) else Color.White,
                            animationSpec = tween(300),
                            label = "heartIconColorFade"
                        )
                        Icon(
                            imageVector = if (isWishlisted) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            contentDescription = "Wishlist",
                            tint = animatedIconColor,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                // 2. ปุ่มเซฟรูปภาพ
                var isDownloading by remember { mutableStateOf(false) }

                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.5f))
                        .clickable(enabled = !isDownloading) {
                            isDownloading = true
                            scope.launch(Dispatchers.IO) {
                                try {
                                    val request = ImageRequest.Builder(context)
                                        .data(imageModel)
                                        .allowHardware(false) // สำคัญ: เพื่อให้ดึง Bitmap ออกมาได้
                                        .build()

                                    val result = context.imageLoader.execute(request)
                                    if (result is SuccessResult) {
                                        val bitmap = (result.drawable as? BitmapDrawable)?.bitmap
                                        if (bitmap != null) {
                                            // สร้างชื่อไฟล์ที่เซฟ เช่น 001_D01
                                            val fileName = "${card.cardId.replace("/", "-")}_${card.cardNum}"
                                            withContext(Dispatchers.Main) {
                                                saveBitmapToGallery(context, bitmap, fileName)
                                            }
                                        } else {
                                            withContext(Dispatchers.Main) {
                                                Toast.makeText(context, "Cannot read image data", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    } else {
                                        withContext(Dispatchers.Main) {
                                            Toast.makeText(context, "Failed to download image", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                } catch (e: Exception) {
                                    withContext(Dispatchers.Main) {
                                        Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                                    }
                                } finally {
                                    isDownloading = false
                                }
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    if (isDownloading) {
                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(
                            imageVector = Icons.Default.Download,
                            contentDescription = "Save Image",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        val displayName = when(currentLanguage) { "TH" -> nameTh ?: card.name; "EN" -> nameEn ?: card.name; else -> card.name }
        Text(text = displayName, fontWeight = FontWeight.Bold, fontSize = 20.sp, textAlign = TextAlign.Center)
        Text("${card.cardId} | ${card.cardNum}", color = Color.Gray, fontSize = 14.sp)

        Spacer(Modifier.height(24.dp))

        Column(Modifier.fillMaxWidth()) {
            val displayColor = when(currentLanguage) { "TH" -> colorTh ?: card.color; "EN" -> colorEn ?: card.color; else -> card.color }
            val displayType = when(currentLanguage) { "TH" -> typeTh ?: card.type; "EN" -> typeEn ?: card.type; else -> card.type }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                DetailStatItem("Color", displayColor, Modifier.weight(1f))
                DetailStatItem("Type", displayType, Modifier.weight(1f))
                DetailStatItem("Rarity", card.rarity, Modifier.weight(1f))
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                DetailStatItem("Level", card.cost, Modifier.weight(1f))
                DetailStatItem("LP", card.lp, Modifier.weight(1f))
                DetailStatItem("AP", card.ap, Modifier.weight(1f))
            }
        }

        Spacer(Modifier.height(24.dp))

        if (isCollectionMode) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth().padding(8.dp)
            ) {
                FilledIconButton(
                    onClick = { viewModel.decrementCard(card.cardNum) },
                    enabled = quantity > 0,
                    colors = IconButtonDefaults.filledIconButtonColors(containerColor = ConanTheme.BowTieRed, disabledContainerColor = Color.LightGray)
                ) { Icon(Icons.Default.Remove, null) }

                Spacer(Modifier.width(16.dp))

                Box(modifier = Modifier.width(60.dp), contentAlignment = Alignment.Center) {
                    BasicTextField(
                        // ✅ 3.1 เปลี่ยนมาใช้ตัวแปร TextFieldValue ที่เราสร้างไว้
                        value = quantityTextValue,
                        onValueChange = { newValue ->
                            val input = newValue.text
                            val filtered = input.filter { it.isDigit() }
                            val finalInput = if (quantity == 0 && filtered.length > 1) filtered.replace("0", "") else filtered
                            val newQty = if (finalInput.isEmpty()) 0 else finalInput.toInt()

                            if (newQty <= 999) {
                                // ✅ 3.2 ป้องกันบัค Cursor ทะลุ Index เวลาลบเลข 0 ออก
                                if (input != finalInput) {
                                    quantityTextValue = TextFieldValue(text = finalInput, selection = TextRange(finalInput.length))
                                } else {
                                    // ถ้าพิมพ์ตัวเลขปกติ ก็ใช้ newValue ที่ติด Cursor ตำแหน่งเดิมมาได้เลย
                                    quantityTextValue = newValue
                                }
                                viewModel.setCardQuantity(card.cardNum, newQty)
                            }
                        },
                        modifier = Modifier.focusRequester(focusRequester).onFocusChanged { if (it.isFocused) onEditingModeChange(true) },
                        textStyle = TextStyle(fontSize = 28.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, color = Color.Black, textDecoration = TextDecoration.Underline),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        cursorBrush = SolidColor(ConanTheme.BlueSuit)
                    )
                }

                Spacer(Modifier.width(16.dp))

                FilledIconButton(
                    onClick = { viewModel.incrementCard(card.cardNum) },
                    colors = IconButtonDefaults.filledIconButtonColors(containerColor = ConanTheme.BlueSuit)
                ) { Icon(Icons.Default.Add, null) }
            }
        }

        Spacer(Modifier.height(16.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Card Text", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (isTranslating) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                }
                Button(onClick = { currentLanguage = "JP" }, colors = ButtonDefaults.buttonColors(containerColor = if(currentLanguage == "JP") ConanTheme.BlueSuit else Color.LightGray), contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp), modifier = Modifier.height(32.dp).defaultMinSize(minWidth = 1.dp)) { Text("JP", fontSize = 12.sp) }
                Spacer(Modifier.width(8.dp))
                Button(onClick = { currentLanguage = "TH" }, colors = ButtonDefaults.buttonColors(containerColor = if(currentLanguage == "TH") ConanTheme.BowTieRed else Color.LightGray), contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp), modifier = Modifier.height(32.dp).defaultMinSize(minWidth = 1.dp)) { Text("TH", fontSize = 12.sp) }
                Spacer(Modifier.width(8.dp))
                Button(onClick = { currentLanguage = "EN" }, colors = ButtonDefaults.buttonColors(containerColor = if(currentLanguage == "EN") Color(0xFF4CAF50) else Color.LightGray), contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp), modifier = Modifier.height(32.dp).defaultMinSize(minWidth = 1.dp)) { Text("EN", fontSize = 12.sp) }
            }
        }
        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        val textToDisplay = when(currentLanguage) {
            "TH" -> featureTh?.takeIf { it.isNotBlank() } ?: originalText
            "EN" -> featureEn?.takeIf { it.isNotBlank() } ?: originalText
            else -> originalText
        }

        if (textToDisplay.isNotBlank()) {
            val annotatedText = buildAnnotatedString {
                val regex = Regex("([★♣♦♠])")
                val parts = textToDisplay.split(regex)
                val matches = regex.findAll(textToDisplay).map { it.value }.toList()

                parts.forEachIndexed { index, part ->
                    append(part)
                    if (index < matches.size) {
                        when (matches[index]) {
                            "★" -> { appendInlineContent("abilityIcon", "[icon]"); append(" ") }
                            "♣" -> { appendInlineContent("cutInIcon", "[icon]"); append(" ") }
                            "♦" -> { appendInlineContent("hiramekiIcon", "[icon]"); append(" ") }
                            "♠" -> { appendInlineContent("hensoIcon", "[icon]"); append(" ") }
                        }
                    }
                }
            }

            val inlineContent = mapOf(
                "abilityIcon" to InlineTextContent(
                    Placeholder(width = 18.sp, height = 18.sp, placeholderVerticalAlign = PlaceholderVerticalAlign.TextCenter)
                ) {
                    Image(painter = painterResource(id = R.drawable.icon_ability), contentDescription = "Ability Icon", modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                },
                "cutInIcon" to InlineTextContent(
                    Placeholder(width = 18.sp, height = 18.sp, placeholderVerticalAlign = PlaceholderVerticalAlign.TextCenter)
                ) {
                    Image(painter = painterResource(id = R.drawable.icon_cut_in), contentDescription = "Cut In Icon", modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                },
                "hiramekiIcon" to InlineTextContent(
                    Placeholder(width = 18.sp, height = 18.sp, placeholderVerticalAlign = PlaceholderVerticalAlign.TextCenter)
                ) {
                    Image(painter = painterResource(id = R.drawable.icon_hirameki), contentDescription = "Hirameki Icon", modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                },
                "hensoIcon" to InlineTextContent(
                    Placeholder(width = 18.sp, height = 18.sp, placeholderVerticalAlign = PlaceholderVerticalAlign.TextCenter)
                ) {
                    Image(painter = painterResource(id = R.drawable.icon_henso), contentDescription = "Henso Icon", modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                }
            )

            Text(
                text = annotatedText,
                inlineContent = inlineContent,
                fontSize = 14.sp,
                color = Color.DarkGray,
                textAlign = TextAlign.Start,
                lineHeight = 20.sp,
                modifier = Modifier.fillMaxWidth()
            )
        } else {
            Text(text = "-", fontSize = 14.sp, color = Color.Gray, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
fun DetailStatItem(label: String, value: String, modifier: Modifier = Modifier) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = modifier) {
        Text(label, fontSize = 12.sp, color = Color.Gray)
        Spacer(Modifier.height(4.dp))
        val displayValue = if(value.isBlank() || value.equals("null", ignoreCase = true)) "-" else value
        Text(text = displayValue, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = ConanTheme.BlueSuit, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
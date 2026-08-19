package com.example.conantcg.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.conantcg.R
import com.example.conantcg.data.model.WebCard
import com.example.conantcg.ui.viewmodel.CardViewModel
import com.example.conantcg.utils.ConanTheme

// 1. Dashboard Button
@Composable
fun DashboardButton(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    val src = remember { MutableInteractionSource() }
    val press by src.collectIsPressedAsState()
    val scale by animateFloatAsState(if (press) 0.92f else 1f, label = "")
    Button(onClick = onClick, modifier = Modifier.fillMaxWidth().height(72.dp).scale(scale).shadow(4.dp, RoundedCornerShape(12.dp)), shape = RoundedCornerShape(12.dp), colors = ButtonDefaults.buttonColors(containerColor = ConanTheme.BlueSuit), contentPadding = PaddingValues(0.dp), interactionSource = src) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxSize()) {
            Box(modifier = Modifier.width(72.dp).fillMaxHeight().background(ConanTheme.BowTieRed), contentAlignment = Alignment.Center) { Icon(icon, null, Modifier.size(32.dp), Color.White) }
            Box(Modifier.weight(1f).padding(horizontal = 16.dp)) { Text(title, fontSize = 20.sp, color = Color.White) }
            Icon(Icons.AutoMirrored.Filled.ArrowForwardIos, null, Modifier.padding(end = 16.dp), Color.White)
        }
    }
}

// --- 2. Premium Loading Screen (Background + การ์ดหมุน 3D ย่อขนาดแล้ว) ---
@Composable
fun LoadingScreen(status: String = "Loading...", prog: Float = 0f) {
    val infiniteTransition = rememberInfiniteTransition(label = "loading_anim")

    // แอนิเมชันการ์ดพลิก 3 มิติ (หมุนแกน Y แบบต่อเนื่องไม่ชะลอ)
    val cardRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ), label = "card_flip"
    )

    // แอนิเมชันให้หลอดโหลดวิ่งแบบนุ่มนวล
    val animatedProgress by animateFloatAsState(
        targetValue = prog,
        animationSpec = tween(durationMillis = 500, easing = FastOutSlowInEasing),
        label = "progress_bar"
    )

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        // --- 1. ใส่รูป Background กำแพงอิฐ (bg1.jpg) ---
        Image(
            painter = painterResource(id = R.drawable.bg1), // รูปพื้นหลัง
            contentDescription = "Background",
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )

        // ใส่ฟิลเตอร์สีดำจางๆ ทับ Background เพื่อให้ตัวหนังสือและส่วนโหลดเด่นขึ้น
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.5f))
        )

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 48.dp)
        ) {
            // --- 2. รูปการ์ดโคนันหมุน 3 มิติ (loading_card.jpg) ที่ย่อขนาดแล้ว ---
            Image(
                painter = painterResource(id = R.drawable.loading_card), // รูปการ์ด
                contentDescription = "Loading Animation",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .width(100.dp) // <-- ย่อขนาดลงมาให้กำลังพอดี (สามารถแก้ตัวเลขนี้ได้ตามต้องการ)
                    .aspectRatio(0.71f) // คุมสัดส่วนให้เป็นรูปทรงการ์ดเกม
                    .graphicsLayer {
                        rotationY = cardRotation // สั่งหมุนพลิกตามแกน Y
                        cameraDistance = 12f * density // สร้างมิติความลึก
                    }
            )

            Spacer(modifier = Modifier.height(40.dp))

            // ข้อความส่วนหัวแบบ Sci-fi
            Text(
                text = "Investigate",
                color = Color.White.copy(alpha = 0.8f),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 4.sp // เว้นช่องไฟกว้างๆ
            )

            Spacer(modifier = Modifier.height(16.dp))

            // หลอดโหลด (Custom Progress Bar)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color.Black.copy(alpha = 0.6f))
                    .border(0.5.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
            ) {
                // แถบสีที่วิ่งตาม Progress
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(animatedProgress) // ความยาวเปลี่ยนตาม % จริง
                        .clip(RoundedCornerShape(4.dp))
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(ConanTheme.BlueSuit, Color(0xFF4FC3F7)) // ไล่สีน้ำเงินไปสว่าง
                            )
                        )
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ข้อความสถานะด้านล่าง พร้อม %
            Text(
                text = status.replace(Regex("\\s?\\d+%$"), "").trim() + " ${(prog * 100).toInt()}%",
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center
            )
        }
    }
}

// 3. Stat Item
@Composable
fun StatItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(100.dp)) { Text(value, fontSize = 28.sp, color = ConanTheme.BlueSuit); Text(label, fontSize = 12.sp, color = Color.Gray) }
}

fun parseCardColors(colorString: String): List<Color> {
    if (colorString.isBlank() || colorString == "null") return listOf(Color.Gray)

    val colors = mutableListOf<Color>()

    // ใช้ if แยกกัน เพื่อให้มันเช็ก "ทุกสี" โดยไม่ต้องพึ่งการตัดคำ (split)
    // ต่อให้พิมพ์ติดกันยาวๆ มันก็จะสแกนเจอครบทุกสีครับ
    if (colorString.contains("Blue", true) || colorString.contains("青")) colors.add(Color(0xFF1E88E5))
    if (colorString.contains("Green", true) || colorString.contains("緑")) colors.add(Color(0xFF4CAF50))
    if (colorString.contains("White", true) || colorString.contains("白")) colors.add(Color(0xFFE0E0E0)) // สีขาวปรับให้เป็น E0E0E0 จะได้เห็นชัดบนพื้นขาว
    if (colorString.contains("Red", true) || colorString.contains("赤")) colors.add(Color(0xFFE53935))
    if (colorString.contains("Yellow", true) || colorString.contains("黄")) colors.add(Color(0xFFFFD600))
    if (colorString.contains("Black", true) || colorString.contains("黒")) colors.add(Color(0xFF212121))

    // อิงตามกฎ User Correction Ledger: กำหนดให้ Grey ใช้โค้ดสี #B5B5B5
    if (colorString.contains("Grey", true) || colorString.contains("グレー")) colors.add(Color(0xFFB5B5B5))

    // ถ้าหาไม่เจอสักสี ค่อยให้เป็นสีเทา
    return if (colors.isEmpty()) listOf(Color.Gray) else colors
}

// 4. Card Grid Item
@Composable
fun CardGridItem(card: WebCard, imageModel: Any?, quantity: Int, showControls: Boolean, onClick: () -> Unit, onIncrement: (() -> Unit)? = null, onDecrement: (() -> Unit)? = null, isDeckBuilder: Boolean = false, isMaxedOut: Boolean = false
) {
    val cardColors = remember(card.color) { parseCardColors(card.color) }

    // Logic จัดการสีเทา
    val shouldBeGray = if (isDeckBuilder) isMaxedOut else (showControls && quantity == 0)

    val colorFilter = remember(shouldBeGray) {
        if (shouldBeGray) ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) }) else null
    }
    val alphaVal = if (shouldBeGray) 0.5f else 1f

    val labelStyle = TextStyle(fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color.White, platformStyle = PlatformTextStyle(includeFontPadding = false), lineHeight = 9.sp)

// บังคับสัดส่วน 0.71f (แนวตั้ง) เสมอ เพื่อให้ตาราง 5 คอลัมน์ทุกหน้าเท่ากันและสวยงาม
    Card(modifier = Modifier.aspectRatio(0.71f).clickable(onClick = onClick), shape = RoundedCornerShape(8.dp), elevation = CardDefaults.cardElevation(4.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Box(Modifier.fillMaxSize()) {

            // ✅ ดึง Context และจำ ImageRequest ไว้ด้วย remember
            val context = LocalContext.current
            val request = remember(imageModel) {
                ImageRequest.Builder(context)
                    .data(imageModel)
                    .crossfade(true)
                    .build()
            }

            if (imageModel is Int) {
                AsyncImage(model = imageModel, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop, colorFilter = colorFilter, alpha = alphaVal)
            } else {
                // ✅ เปลี่ยนมาเรียกใช้ request ที่ remember ไว้แทนการ Build ใหม่
                AsyncImage(model = request, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop, colorFilter = colorFilter, alpha = alphaVal)
            }

            // Color Bar
            Row(modifier = Modifier.fillMaxWidth().height(4.dp).align(Alignment.BottomCenter)) { cardColors.forEach { color -> Box(modifier = Modifier.weight(1f).fillMaxHeight().background(color)) } }

            // Quantity Badge
            if (showControls && quantity > 0) {
                Box(modifier = Modifier.align(Alignment.TopEnd).padding(4.dp).background(ConanTheme.BlueSuit.copy(alpha = 0.9f), CircleShape).size(24.dp), contentAlignment = Alignment.Center) { Text(text = quantity.toString(), color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
            }

            // Bottom Labels
            Box(modifier = Modifier.align(Alignment.BottomStart).padding(bottom = 4.dp).background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(topEnd = 4.dp)).padding(horizontal = 3.dp, vertical = 2.dp)) { Text(text = "${card.rarity} | ${card.cardId}", style = labelStyle) }
            Box(modifier = Modifier.align(Alignment.BottomEnd).padding(bottom = 4.dp).background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(topStart = 4.dp)).padding(horizontal = 3.dp, vertical = 2.dp)) { Text(text = card.cardNum, style = labelStyle) }
        }
    }
}

// 5. Card List Item
@Composable
fun CardListItem(card: WebCard, imageModel: Any?, quantity: Int, showControls: Boolean, onIncrement: () -> Unit, onDecrement: () -> Unit, onQuantityChange: (Int) -> Unit, onClick: () -> Unit) {
    val cardColors = remember(card.color) { parseCardColors(card.color) }
    val isUnowned = showControls && quantity == 0
    val colorFilter = remember(isUnowned) { if (isUnowned) ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) }) else null }

    Card(modifier = Modifier.fillMaxWidth().height(100.dp).clickable(onClick = onClick), shape = RoundedCornerShape(8.dp), colors = CardDefaults.cardColors(containerColor = Color.White), elevation = CardDefaults.cardElevation(2.dp)) {
        Row(Modifier.fillMaxSize()) {
            Column(modifier = Modifier.width(6.dp).fillMaxHeight()) { cardColors.forEach { color -> Box(modifier = Modifier.weight(1f).fillMaxWidth().background(color)) } }

            // ✅ ดึง Context และจำ ImageRequest
            val context = LocalContext.current
            val request = remember(imageModel) {
                ImageRequest.Builder(context)
                    .data(imageModel)
                    .crossfade(true) // แนะนำให้ใส่ crossfade เพื่อให้รูปค่อยๆ เฟดมาดูสมูทขึ้น
                    .build()
            }

            Box(modifier = Modifier.width(70.dp).fillMaxHeight().background(Color.LightGray)) {
                // ✅ เปลี่ยนมาเรียกใช้ request ที่ remember ไว้
                AsyncImage(model = request, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop, colorFilter = colorFilter, alpha = if (isUnowned) 0.6f else 1f)
            }
            Column(modifier = Modifier.weight(1f).padding(8.dp).fillMaxHeight(), verticalArrangement = Arrangement.Center) {
                Text(text = card.name, fontWeight = FontWeight.Bold, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(color = Color.DarkGray, shape = RoundedCornerShape(4.dp), modifier = Modifier.padding(end = 8.dp)) { Text(text = card.rarity, color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)) }
                    Text(text = card.cardNum, fontSize = 12.sp, color = Color.Gray)
                }
            }

            if (showControls) {
                Column(modifier = Modifier.fillMaxHeight().padding(horizontal = 8.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        FilledIconButton(onClick = onDecrement, modifier = Modifier.size(32.dp), enabled = quantity > 0, colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color.LightGray)) { Icon(Icons.Default.Remove, null, modifier = Modifier.size(16.dp)) }
                        Box(modifier = Modifier.width(50.dp).padding(horizontal = 4.dp), contentAlignment = Alignment.Center) {

                            // --- [แก้ไข] TextField ในหน้า List View ---
                            BasicTextField(
                                value = if (quantity > 0) quantity.toString() else "0",
                                onValueChange = { input ->
                                    val filtered = input.filter { it.isDigit() }
                                    // โลจิกแก้ปัญหาพิมพ์ 9 ใส่ 0 แล้วเป็น 90
                                    val cleanInput = if (quantity == 0 && filtered.length == 2 && filtered.contains("0")) {
                                        filtered.replaceFirst("0", "")
                                    } else {
                                        filtered.trimStart('0')
                                    }
                                    val newQty = if (cleanInput.isEmpty()) 0 else cleanInput.toIntOrNull() ?: 0
                                    if (newQty <= 999) onQuantityChange(newQty)
                                },
                                textStyle = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, color = Color.Black),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                cursorBrush = SolidColor(ConanTheme.BlueSuit),
                                singleLine = true
                            )
                            // ------------------------------------------
                        }
                        FilledIconButton(onClick = onIncrement, modifier = Modifier.size(32.dp), colors = IconButtonDefaults.filledIconButtonColors(containerColor = ConanTheme.BlueSuit)) { Icon(Icons.Default.Add, null, modifier = Modifier.size(16.dp), tint = Color.White) }
                    }
                }
            } else {
                if(quantity > 0) { Box(modifier = Modifier.fillMaxHeight().padding(end = 16.dp), contentAlignment = Alignment.Center) { Text(text = "x$quantity", fontWeight = FontWeight.Bold, color = ConanTheme.BlueSuit) } }
            }
        }
    }
}

// 6. Swipeable Card Detail Dialog
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SwipeableCardDetailDialog(initialCards: List<WebCard>, startIndex: Int, viewModel: CardViewModel, isCollectionMode: Boolean, onDismiss: () -> Unit) {
    val pagerState = rememberPagerState(initialPage = startIndex, pageCount = { initialCards.size })
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(modifier = Modifier.fillMaxSize().background(Color.Transparent), contentAlignment = Alignment.Center) {
            Box(modifier = Modifier.fillMaxSize().clickable(onClick = onDismiss))
            HorizontalPager(state = pagerState, contentPadding = PaddingValues(horizontal = 32.dp), pageSpacing = 16.dp, modifier = Modifier.fillMaxWidth().wrapContentHeight()) { page ->
                val card = initialCards[page]
                val quantity = viewModel.getQuantity(card.cardNum)
                val fullText = remember(card) {
                    buildString {
                        val f = card.feature.replace(Regex("<.*?>"), "").trim()
                        val c = card.cut_in.replace(Regex("<.*?>"), "").trim()
                        val h = card.hirameki.replace(Regex("<.*?>"), "").trim()
                        val e = card.henso.replace(Regex("<.*?>"), "").trim()
                        if (f.isNotBlank() && f != "null") append("[Ability]\n$f\n\n")
                        if (c.isNotBlank() && c != "null") append("[Cut-In]\n$c\n\n")
                        if (h.isNotBlank() && h != "null") append("[Hirameki]\n$h\n\n")
                        if (e.isNotBlank() && e != "null") append("[Disguise]\n$e")
                    }.trim().ifBlank { "-" }
                }
                Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = Color.White), modifier = Modifier.fillMaxWidth().wrapContentHeight()) {
                    Column(modifier = Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        val imageModel = viewModel.getCardImageModel(card)
                        Box(modifier = Modifier.fillMaxWidth().heightIn(max = 400.dp).clip(RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
                            if (imageModel is Int) { Image(painter = painterResource(id = imageModel), contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize()) }
                            else { AsyncImage(model = ImageRequest.Builder(LocalContext.current).data(imageModel).crossfade(true).build(), contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize()) }
                        }
                        Spacer(Modifier.height(16.dp))
                        Text(text = card.name, fontSize = 22.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, color = Color.Black)
                        Text(text = "ID: ${card.cardNum}", fontSize = 14.sp, color = Color.Gray)
                        Spacer(Modifier.height(16.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) { StatColumn("Color", card.color); StatColumn("Type", card.type); StatColumn("Rarity", card.rarity) }
                        Spacer(Modifier.height(12.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) { StatColumn("Level", card.cost); StatColumn("LP", card.lp); StatColumn("AP", card.ap) }
                        Spacer(Modifier.height(24.dp))
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth()) {
                            FilledIconButton(onClick = { viewModel.decrementCard(card.cardNum) }, modifier = Modifier.size(48.dp), enabled = quantity > 0, colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color(0xFFEEEEEE))) { Icon(Icons.Default.Remove, null, tint = Color.Black) }
                            Spacer(Modifier.width(24.dp))
                            Box(modifier = Modifier.width(60.dp), contentAlignment = Alignment.Center) {
                                BasicTextField(value = quantity.toString(), onValueChange = { input -> val filtered = input.filter { it.isDigit() }; val newQty = if (filtered.isEmpty()) 0 else filtered.toInt(); if (newQty <= 999) viewModel.updateCollectionCount(card.cardNum, newQty) }, textStyle = TextStyle(fontSize = 32.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, color = Color.Black, textDecoration = TextDecoration.Underline), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, cursorBrush = SolidColor(ConanTheme.BlueSuit))
                            }
                            Spacer(Modifier.width(24.dp))
                            FilledIconButton(onClick = { viewModel.incrementCard(card.cardNum) }, modifier = Modifier.size(48.dp), colors = IconButtonDefaults.filledIconButtonColors(containerColor = ConanTheme.BowTieRed)) { Icon(Icons.Default.Add, null, tint = Color.White) }
                        }
                        Spacer(Modifier.height(24.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { LanguageChip("JP", true); Spacer(Modifier.width(4.dp)); LanguageChip("TH", false); Spacer(Modifier.width(4.dp)); LanguageChip("EN", false) }
                        Spacer(Modifier.height(8.dp))
                        Box(modifier = Modifier.fillMaxWidth().height(100.dp).verticalScroll(rememberScrollState())) { Text(text = fullText, fontSize = 14.sp, color = Color.DarkGray, lineHeight = 22.sp) }
                    }
                }
            }
        }
    }
}

// Helpers
@Composable
fun StatColumn(label: String, value: String) { Column(horizontalAlignment = Alignment.CenterHorizontally) { Text(label, fontSize = 12.sp, color = Color.Gray); Text(value, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = ConanTheme.BlueSuit) } }
@Composable
fun LanguageChip(lang: String, isSelected: Boolean) { Surface(color = if (isSelected) ConanTheme.BlueSuit else Color.LightGray, shape = CircleShape, modifier = Modifier.size(30.dp)) { Box(contentAlignment = Alignment.Center) { Text(lang, fontSize = 10.sp, color = Color.White, fontWeight = FontWeight.Bold) } } }
package com.example.conantcg.ui.screens

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.conantcg.R
import com.example.conantcg.data.model.WebCard
import com.example.conantcg.ui.components.CardGridItem
import com.example.conantcg.ui.components.FilterChipItem
import com.example.conantcg.ui.components.FilterTableRow
import com.example.conantcg.ui.components.SimpleDropdown
import com.example.conantcg.ui.components.getColor
import com.example.conantcg.ui.viewmodel.CardViewModel
import com.example.conantcg.utils.ConanTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private enum class BuilderSelectionMode { PARTNER, CASE, DECK }

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun DeckBuilderScreen(viewModel: CardViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var showSelector by remember { mutableStateOf(false) }
    var selectionMode by remember { mutableStateOf(BuilderSelectionMode.DECK) }
    var showCopyNotification by remember { mutableStateOf(false) }

    // ✅ 1. เพิ่มตัวแปรนี้สำหรับเก็บว่ากำลังซูมการ์ดใบไหนอยู่
    var zoomedCard by remember { mutableStateOf<WebCard?>(null) }

    val captureController = dev.shreyaspatil.capturable.controller.rememberCaptureController()

    val partnerId = viewModel.partnerCard.value
    val caseId = viewModel.caseCard.value
    val deckMap = viewModel.deckMap.toMap()
    var localDeckName by remember { mutableStateOf(viewModel.getActiveDeckName()) }

    val partnerCard = remember(partnerId, viewModel.cardList) { viewModel.cardList.find { it.cardNum == partnerId } }
    val caseCard = remember(caseId, viewModel.cardList) { viewModel.cardList.find { it.cardNum == caseId } }

    val deckCards = remember(deckMap, viewModel.cardList, partnerId, caseId) {
        deckMap.flatMap { (id, count) ->
            if (id == partnerId || id == caseId) return@flatMap emptyList()
            val card = viewModel.cardList.find { it.cardNum == id }
            if (card != null && !card.type.contains("Partner", true) && !card.type.contains("パートナー", true) && !card.type.contains("Case", true) && !card.type.contains("事件", true)) {
                List(count) { card }
            } else { emptyList() }
        }.sortedWith(compareByDescending<WebCard> { it.cost.toIntOrNull() ?: 0 }.thenBy { it.cardNum })
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(elevation = 8.dp)
                    .background(Brush.verticalGradient(colors = listOf(ConanTheme.BlueSuit, Color(0xFF151C5E))))
                    .statusBarsPadding()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 8.dp)) {
                    IconButton(onClick = onBack) {
                        Image(
                            painter = painterResource(id = R.drawable.arrowleft),
                            contentDescription = "Back",
                            modifier = Modifier.size(30.dp) // ปรับขนาดให้พอดี
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Deck Builder", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp, letterSpacing = 1.sp)
                        Text("Editing Mode", color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp)
                    }
                    Spacer(Modifier.weight(1f))
                  //  IconButton(onClick = { viewModel.clearDeck() }) { Icon(Icons.Default.Delete, contentDescription = "Clear Deck", tint = Color.White) }
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Spacer(Modifier.height(16.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(45.dp)
                        .shadow(2.dp, RoundedCornerShape(8.dp))
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.White),
                    contentAlignment = Alignment.CenterStart
                ) {
                    BasicTextField(
                        value = localDeckName,
                        onValueChange = {
                            localDeckName = it
                            viewModel.renameActiveDeck(it)
                        },
                        modifier = Modifier.fillMaxSize(),
                        textStyle = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.Black),
                        singleLine = true,
                        decorationBox = { inner ->
                            Box(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp), contentAlignment = Alignment.CenterStart) {
                                if (localDeckName.isEmpty()) { Text("Enter Deck Name...", color = Color.Gray, fontSize = 16.sp) }
                                inner()
                            }
                        }
                    )
                }

                Spacer(Modifier.height(16.dp))

                dev.shreyaspatil.capturable.Capturable(
                    controller = captureController,
                    onCaptured = { bitmap, error ->
                        if (bitmap != null) {
                            viewModel.saveBitmapToGallery(context, bitmap.asAndroidBitmap())
                        }
                        if (error != null) {
                            Toast.makeText(context, "Failed to capture image.", Toast.LENGTH_SHORT).show()
                        }
                    }
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Row(modifier = Modifier.fillMaxWidth().height(160.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                            Box(modifier = Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                                DBDeckSlot("PARTNER", partnerCard, viewModel, Modifier.fillMaxHeight().aspectRatio(0.71f)) { selectionMode = BuilderSelectionMode.PARTNER; showSelector = true }
                            }
                            Spacer(Modifier.width(16.dp))
                            Box(modifier = Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                                DBDeckSlot("CASE", caseCard, viewModel, Modifier.fillMaxWidth(0.95f).aspectRatio(1.41f)) { selectionMode = BuilderSelectionMode.CASE; showSelector = true }
                            }
                        }

                        Spacer(Modifier.height(16.dp))

                        Text("Main Deck (${deckCards.size}/40)", color = Color.White, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 8.dp))

                        val deckInteractionSrc = remember { MutableInteractionSource() }
                        val isDeckPressed by deckInteractionSrc.collectIsPressedAsState()
                        val deckScale by animateFloatAsState(targetValue = if (isDeckPressed) 0.98f else 1f, animationSpec = spring(dampingRatio = 0.5f, stiffness = 400f), label = "deckScale")

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(220.dp)
                                .scale(deckScale)
                                .shadow(4.dp, RoundedCornerShape(8.dp))
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFF2D2D3A).copy(alpha = 0.9f))
                                .border(1.dp, Color.Gray.copy(0.3f), RoundedCornerShape(8.dp))
                                .clickable(interactionSource = deckInteractionSrc, indication = null) { selectionMode = BuilderSelectionMode.DECK; showSelector = true }
                        ) {
                            if (deckCards.isEmpty()) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.align(Alignment.Center)) {
                                    Icon(Icons.Default.AddCircleOutline, contentDescription = "Add Cards", tint = Color.White.copy(0.5f), modifier = Modifier.size(32.dp))
                                    Spacer(Modifier.height(8.dp))
                                    Text("Tap here to add cards", color = Color.White.copy(0.5f))
                                }
                            } else {
                                LazyVerticalGrid(
                                    columns = GridCells.Fixed(10),
                                    modifier = Modifier.fillMaxSize().padding(4.dp),
                                    horizontalArrangement = Arrangement.spacedBy(1.dp),
                                    verticalArrangement = Arrangement.spacedBy(1.dp)
                                ) {
                                    items(deckCards.size, key = { index -> "${deckCards[index].cardNum}_$index" }) { index ->
                                        val card = deckCards[index]
                                        AsyncImage(
                                            model = viewModel.getCardImageModel(card),
                                            contentDescription = "Card Image",
                                            // ✅ 2. ใส่ .clickable เพื่อให้สั่งซูมรูปการ์ด
                                            modifier = Modifier.fillMaxWidth().aspectRatio(0.71f).clip(RoundedCornerShape(2.dp)).clickable { zoomedCard = card },
                                            contentScale = ContentScale.Crop
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(Modifier.height(16.dp))
                        DBStatsPanel(deckCards)
                    }
                }

                Spacer(Modifier.height(24.dp))
                Button(onClick = { onBack() }, modifier = Modifier.fillMaxWidth().height(56.dp).shadow(4.dp, RoundedCornerShape(28.dp)), colors = ButtonDefaults.buttonColors(containerColor = ConanTheme.BlueSuit), shape = RoundedCornerShape(28.dp)) { Text("Save & Close", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold) }
                Spacer(Modifier.height(24.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    DBCircleButton(Icons.Default.Delete) { viewModel.clearDeck() }

                    DBCircleButton(Icons.Default.CameraAlt) {
                        captureController.capture()
                    }

                    DBCircleButton(Icons.Default.ContentCopy) {
                        viewModel.duplicateCurrentDeck()
                        showCopyNotification = true
                        coroutineScope.launch {
                            delay(2500)
                            showCopyNotification = false
                        }
                    }
                }
                Spacer(Modifier.height(50.dp))
            }
        }

        AnimatedVisibility(
            visible = showCopyNotification,
            enter = slideInVertically(
                initialOffsetY = { -it * 2 },
                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow)
            ) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 72.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = Color(0xFF4CAF50),
                shadowElevation = 8.dp,
                modifier = Modifier.padding(horizontal = 16.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
                ) {
                    Icon(Icons.Default.CheckCircle, contentDescription = "Success", tint = Color.White, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Deck copied successfully!", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
            }
        }
    }

    if (showSelector) {
        AdvancedSelectorDialog(
            viewModel = viewModel, mode = selectionMode,
            onCardSelected = { card ->
                if (selectionMode == BuilderSelectionMode.PARTNER) viewModel.setPartner(card.cardNum)
                else if (selectionMode == BuilderSelectionMode.CASE) viewModel.setCase(card.cardNum)
                else { if (deckCards.size < 40) viewModel.addToDeck(card.cardNum) }
                if (selectionMode != BuilderSelectionMode.DECK) showSelector = false
            },
            onDismiss = { showSelector = false }
        )
    }

    // ✅ 3. โค้ดสำหรับแสดงหน้าต่างซูมภาพ
    if (zoomedCard != null) {
        Dialog(
            onDismissRequest = { zoomedCard = null }, // ปิดเมื่อกดออก
            properties = DialogProperties(usePlatformDefaultWidth = false) // ให้เต็มจอได้
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.8f)) // พื้นหลังสีดำโปร่งแสง
                    .clickable { zoomedCard = null }, // แตะที่ว่างเพื่อปิด
                contentAlignment = Alignment.Center
            ) {
                // รูปการ์ดขนาดใหญ่
                AsyncImage(
                    model = viewModel.getCardImageModel(zoomedCard!!),
                    contentDescription = "Zoomed Card",
                    modifier = Modifier
                        .fillMaxWidth(0.85f) // ขยายให้กว้าง 85% ของหน้าจอ
                        .clip(RoundedCornerShape(16.dp)),
                    contentScale = ContentScale.Fit
                )
            }
        }
    }
}

// --- Helpers ---

@Composable private fun DBStatsPanel(deckCards: List<WebCard>) {
    val charCount = deckCards.count { it.type.contains("Character", true) || it.type.contains("キャラ") }
    val eventCount = deckCards.count { it.type.contains("Event", true) || it.type.contains("イベント") }

    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF2D2D3A).copy(alpha = 0.9f)), shape = RoundedCornerShape(12.dp), elevation = CardDefaults.cardElevation(4.dp), modifier = Modifier.fillMaxWidth().height(160.dp)) {
        Row(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            Column(Modifier.weight(1.5f).fillMaxHeight()) {
                Text("Cost Curve", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                DBCostChart(deckCards)
            }
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(0.6f).fillMaxHeight(), verticalArrangement = Arrangement.SpaceBetween, horizontalAlignment = Alignment.CenterHorizontally) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) { Text("$charCount", color = Color(0xFF4FC3F7), fontSize = 26.sp, fontWeight = FontWeight.Bold); Text("Character", color = Color.Gray, fontSize = 10.sp) }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) { Text("$eventCount", color = Color(0xFFE57373), fontSize = 26.sp, fontWeight = FontWeight.Bold); Text("Event", color = Color.Gray, fontSize = 10.sp) }
                }
                HorizontalDivider(color = Color.Gray.copy(0.3f), modifier = Modifier.padding(vertical = 4.dp))
                Column(horizontalAlignment = Alignment.CenterHorizontally) { Text("${deckCards.size} / 40", color = if(deckCards.size == 40) Color(0xFF66BB6A) else Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold); Text("Total Cards", color = Color.Gray, fontSize = 10.sp) }
            }
        }
    }
}

@Composable private fun DBCostChart(cards: List<WebCard>) {
    val costCounts = remember(cards) { val c = IntArray(10); cards.forEach { val cost = it.cost.toIntOrNull()?:0; if(cost in 1..9) c[cost]++ }; c }
    val max = costCounts.maxOrNull()?.coerceAtLeast(1) ?: 1

    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.Bottom) {
        for(i in 1..9) {
            val count = costCounts[i]; val heightRatio = count.toFloat() / max
            Column(modifier = Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.Bottom, horizontalAlignment = Alignment.CenterHorizontally) {
                if (count > 0) Text("$count", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center) else Spacer(Modifier.height(14.dp))
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.BottomCenter) {
                    Box(Modifier.fillMaxWidth().fillMaxHeight(heightRatio.coerceAtLeast(0.02f)).background(if (count > 0) Color(0xFF29B6F6) else Color.Transparent, RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp)))
                }
                Spacer(Modifier.height(4.dp)); Text("$i", color = Color.Gray, fontSize = 11.sp, textAlign = TextAlign.Center)
            }
        }
    }
}

@Composable private fun DBDeckSlot(label: String, card: WebCard?, viewModel: CardViewModel, modifier: Modifier, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(targetValue = if (isPressed) 0.92f else 1f, animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = 500f), label = "scale")

    Box(
        modifier = modifier
            .scale(scale)
            .clickable(interactionSource = interactionSource, indication = null) { onClick() }
            .shadow(4.dp, RoundedCornerShape(8.dp))
    ) {
        Crossfade(targetState = card, animationSpec = tween(400), label = "card_fade") { currentCard ->
            if (currentCard != null) {
                AsyncImage(model = viewModel.getCardImageModel(currentCard), contentDescription = "Slot Card", modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(8.dp)).border(2.dp, Color.White, RoundedCornerShape(8.dp)), contentScale = ContentScale.Crop)
            } else {
                Box(modifier = Modifier.fillMaxSize().background(Color(0xFFE0E0E0).copy(alpha = 0.8f), RoundedCornerShape(8.dp)).border(1.dp, Color.LightGray, RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Add, contentDescription = "Add", tint = Color.Gray)
                        Spacer(Modifier.height(4.dp))
                        Text(label, color = Color.Gray, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        if (card != null) {
            Box(Modifier.align(Alignment.TopStart).background(ConanTheme.BowTieRed, RoundedCornerShape(topStart = 8.dp, bottomEnd = 8.dp)).padding(horizontal = 8.dp, vertical = 2.dp)) { Text(label, color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold) }
        }
    }
}

@Composable private fun DBCircleButton(icon: ImageVector, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(targetValue = if (isPressed) 0.85f else 1f, label = "btn_scale")

    Button(onClick = onClick, shape = CircleShape, colors = ButtonDefaults.buttonColors(containerColor = Color.White), modifier = Modifier.size(50.dp).scale(scale).shadow(4.dp, CircleShape), contentPadding = PaddingValues(0.dp), interactionSource = interactionSource) { Icon(icon, contentDescription = "Action", tint = ConanTheme.BlueSuit) }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun AdvancedSelectorDialog(viewModel: CardViewModel, mode: BuilderSelectionMode, onCardSelected: (WebCard) -> Unit, onDismiss: () -> Unit) {
    var searchQuery by remember { mutableStateOf("") }
    var isFilterExpanded by remember { mutableStateOf(false) }

    var selectedColors by remember { mutableStateOf(setOf<String>()) }
    var selectedTypes by remember { mutableStateOf(setOf<String>()) }
    var selectedRarities by remember { mutableStateOf(setOf<String>()) }
    var selectedAps by remember { mutableStateOf(setOf<String>()) }

    var selectedLevels by remember { mutableStateOf(setOf<String>()) }
    var selectedLps by remember { mutableStateOf(setOf<String>()) }

    var selectedPack by remember { mutableStateOf("All") }
    var selectedCardCategory by remember { mutableStateOf("All") }

    val deckMap = viewModel.deckMap.toMap()
    val partnerId = viewModel.partnerCard.value
    val caseId = viewModel.caseCard.value

    val currentPartner = remember(partnerId, viewModel.cardList) { viewModel.cardList.find { it.cardNum == partnerId } }
    val currentCase = remember(caseId, viewModel.cardList) { viewModel.cardList.find { it.cardNum == caseId } }

    var previewCard by remember {
        mutableStateOf<WebCard?>(
            if (mode == BuilderSelectionMode.PARTNER) currentPartner
            else if (mode == BuilderSelectionMode.CASE) currentCase
            else null
        )
    }

    val selectedDeckCards = remember(deckMap, viewModel.cardList) {
        deckMap.flatMap { (id, count) ->
            if (id != partnerId && id != caseId) {
                val c = viewModel.cardList.find { it.cardNum == id }
                if (c != null) List(count) { c } else emptyList()
            } else emptyList()
        }.sortedWith(
            compareByDescending<WebCard> { it.cost.toIntOrNull() ?: 0 }
                .thenBy { it.cardNum }
        )
    }

    val availablePacks = remember { listOf("All") + viewModel.cardList.map { it.pack }.distinct().sorted() }
    val availableCategories = remember(viewModel.cardList) {
        val allCats = mutableSetOf<String>()
        viewModel.cardList.forEach { card -> card.categories.forEach { raw -> raw.split(",", "、").forEach { if(it.trim().isNotBlank()) allCats.add(it.trim()) } } }
        allCats.toList().sorted()
    }

    val colorMap = remember { mapOf("Blue" to "青", "Green" to "緑", "White" to "白", "Red" to "赤", "Yellow" to "黄", "Black" to "黒") }

    val searchDictionary = remember {
        mapOf(
            "conan" to listOf("コナン"), "edogawa" to listOf("江戸川"), "shinichi" to listOf("新一"), "kudo" to listOf("工藤"),
            "ran" to listOf("蘭"), "mouri" to listOf("毛利"), "kogoro" to listOf("小五郎"), "heiji" to listOf("平次"),
            "hattori" to listOf("服部"), "kazuha" to listOf("和葉"), "toyama" to listOf("遠山"), "haibara" to listOf("灰原", "哀", "シェリー", "志保"),
            "ai" to listOf("哀", "灰原"), "ayumi" to listOf("歩美"), "mitsuhiko" to listOf("光彦"), "genta" to listOf("元太"),
            "agasa" to listOf("阿笠"), "sonoko" to listOf("園子"), "suzuki" to listOf("鈴木"), "makoto" to listOf("京極", "真"),
            "eri" to listOf("妃", "英理"), "kisaki" to listOf("妃"), "yukiko" to listOf("有希子"), "yusaku" to listOf("優作"),
            "takagi" to listOf("高木"), "sato" to listOf("佐藤", "美和子"), "megure" to listOf("目暮"), "shiratori" to listOf("白鳥"),
            "chiba" to listOf("千葉"), "yumi" to listOf("由美"), "naeko" to listOf("苗子"), "kuroda" to listOf("黒田"),
            "yamato" to listOf("大和", "敢助"), "kansuke" to listOf("敢助"), "uehara" to listOf("上原"), "yui" to listOf("由衣"),
            "komei" to listOf("高明"), "morofushi" to listOf("諸伏"), "matsuda" to listOf("松田", "陣平"), "hagiwara" to listOf("萩原", "研二"),
            "date" to listOf("伊達", "航"), "scotch" to listOf("スコッチ", "景光"), "hiromitsu" to listOf("景光"), "amuro" to listOf("安室", "透", "降谷", "バーボン"),
            "toru" to listOf("透"), "bourbon" to listOf("バーボン"), "rei" to listOf("零", "降谷"), "furuya" to listOf("降谷"),
            "kazami" to listOf("風見", "裕也"), "yuya" to listOf("裕也"), "akai" to listOf("赤井", "秀一", "ライ", "沖矢"),
            "shuichi" to listOf("秀一"), "rye" to listOf("ライ"), "okiya" to listOf("沖矢", "昴"), "subaru" to listOf("昴"),
            "jodie" to listOf("ジョディ"), "james" to listOf("ジェイムズ"), "camel" to listOf("キャメル"), "kir" to listOf("キール", "水無", "怜奈"),
            "rena" to listOf("怜奈"), "gin" to listOf("ジン"), "vodka" to listOf("ウォッカ"), "vermouth" to listOf("ベルモット"),
            "chianti" to listOf("キャンティ"), "korn" to listOf("コルン"), "rum" to listOf("ラム"), "pinga" to listOf("ピンガ"),
            "sherry" to listOf("シェリー"), "kid" to listOf("キッド", "怪盗"), "kaitou" to listOf("怪盗"), "kaito" to listOf("快斗"),
            "kuroba" to listOf("黒羽"), "aoko" to listOf("青子"), "nakamori" to listOf("中森"), "sera" to listOf("世良", "真純"),
            "masumi" to listOf("真純"), "shukichi" to listOf("秀吉"), "haneda" to listOf("羽田"), "mary" to listOf("メアリー"),
            "detective" to listOf("探偵"), "detective boys" to listOf("少年探偵団"), "police" to listOf("警察"), "fbi" to listOf("FBI"),
            "black organization" to listOf("黒ずくめの組織"), "partner" to listOf("パートナー"), "character" to listOf("キャラ"),
            "event" to listOf("イベント"), "case" to listOf("事件"), "boss" to listOf("ボス")
        )
    }

    val filteredCards = remember(viewModel.cardList, mode, searchQuery, selectedColors, selectedTypes, selectedRarities, selectedAps, selectedLevels, selectedLps, selectedPack, selectedCardCategory) {
        viewModel.cardList.filter { card ->
            val typeMatchMode = when (mode) {
                BuilderSelectionMode.PARTNER -> card.type.contains("Partner", true) || card.type.contains("パートナー", true)
                BuilderSelectionMode.CASE -> card.type.contains("Case", true) || card.type.contains("事件", true)
                BuilderSelectionMode.DECK -> !card.type.contains("Partner", true) && !card.type.contains("パートナー", true) && !card.type.contains("Case", true) && !card.type.contains("事件", true)
            }

            val query = searchQuery.trim()
            val lowerSearchQuery = query.lowercase()

            val jpSearchTerms = searchDictionary.entries
                .filter { it.key.contains(lowerSearchQuery) || lowerSearchQuery.contains(it.key) }
                .flatMap { it.value }

            val searchMatch = query.isEmpty() ||
                    card.cardNum.contains(query, true) ||
                    card.cardId.contains(query, true) ||
                    card.pack.contains(query, true) ||
                    card.name.contains(query, true) ||
                    jpSearchTerms.any { jpWord -> card.name.contains(jpWord, true) }

            val colorMatch = selectedColors.isEmpty() || selectedColors.any { colorKey -> val jpColor = colorMap[colorKey] ?: ""; card.color.contains(colorKey, true) || (jpColor.isNotEmpty() && card.color.contains(jpColor, true)) }

            val typeFilterMatch = selectedTypes.isEmpty() || selectedTypes.any { typeKey ->
                val jpType = when(typeKey) {
                    "Partner" -> "パートナー"
                    "Character" -> "キャラ"
                    "Event" -> "イベント"
                    "Case" -> "事件"
                    else -> ""
                }
                card.type.contains(typeKey, true) || (jpType.isNotEmpty() && card.type.contains(jpType, true))
            }

            val rarityMatch = selectedRarities.isEmpty() || selectedRarities.contains(card.rarity)
            val cardAp = card.ap.toString().replace(",", "").trim().toIntOrNull() ?: -1; val apMatch = selectedAps.isEmpty() || selectedAps.any { it.toIntOrNull() == cardAp }
            val packMatch = selectedPack == "All" || card.pack == selectedPack
            val catMatch = selectedCardCategory == "All" || card.categories.any { it.contains(selectedCardCategory, true) }

            val levelMatch = selectedLevels.isEmpty() || selectedLevels.contains(card.cost.trim())
            val lpMatch = selectedLps.isEmpty() || selectedLps.contains(card.lp.trim())

            typeMatchMode && searchMatch && colorMatch && typeFilterMatch && rarityMatch && levelMatch && apMatch && lpMatch && packMatch && catMatch
        }
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        var isVisible by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) { isVisible = true }
        AnimatedVisibility(visible = isVisible, enter = fadeIn() + slideInVertically(initialOffsetY = { it / 4 }, animationSpec = tween(300, easing = FastOutSlowInEasing))) {
            Scaffold(
                topBar = {
                    Column(Modifier.background(Color.White)) {
                        TopAppBar(title = { Text(if(mode == BuilderSelectionMode.DECK) "Select Cards (${deckMap.values.sum()}/40)" else "Select ${mode.name}", fontSize = 16.sp) }, navigationIcon = { IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, contentDescription = "Close") } })
                        Box(Modifier.fillMaxWidth().background(Color(0xFFF5F5F5)).padding(8.dp)) {
                            when (mode) {
                                BuilderSelectionMode.PARTNER, BuilderSelectionMode.CASE -> {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                                        val isPartner = mode == BuilderSelectionMode.PARTNER; val previewHeight = if (isPartner) 180.dp else 130.dp; val previewRatio = if (isPartner) 0.71f else 1.41f
                                        if (previewCard != null) {
                                            AsyncImage(model = viewModel.getCardImageModel(previewCard!!), contentDescription = "Preview Card", modifier = Modifier.height(previewHeight).aspectRatio(previewRatio).clip(RoundedCornerShape(8.dp)), contentScale = ContentScale.Crop)
                                            Spacer(Modifier.height(8.dp))
                                            Button(onClick = { onCardSelected(previewCard!!) }, modifier = Modifier.width(200.dp).height(36.dp), colors = ButtonDefaults.buttonColors(containerColor = ConanTheme.BowTieRed), shape = RoundedCornerShape(8.dp), contentPadding = PaddingValues(0.dp)) { Text("Select ${if(isPartner) "Partner" else "Case"}", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp) }
                                        } else {
                                            Box(modifier = Modifier.height(previewHeight).aspectRatio(previewRatio).background(Color.LightGray.copy(alpha=0.5f), RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) { Text("Tap a card below to preview", color = Color.Gray, fontSize = 12.sp) }
                                        }
                                    }
                                }
                                BuilderSelectionMode.DECK -> {
                                    Box(modifier = Modifier.fillMaxWidth().heightIn(min = 100.dp, max = 220.dp).clip(RoundedCornerShape(8.dp)).background(Color.White).border(1.dp, Color.LightGray.copy(alpha = 0.5f), RoundedCornerShape(8.dp)), contentAlignment = Alignment.TopStart) {
                                        if (selectedDeckCards.isEmpty()) {
                                            Column(modifier = Modifier.fillMaxWidth().align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) { Icon(Icons.Default.Style, contentDescription = "Style", tint = Color.LightGray, modifier = Modifier.size(28.dp)); Spacer(Modifier.height(4.dp)); Text("Tap cards below to add to your deck", color = Color.Gray, fontSize = 12.sp) }
                                        } else {
                                            BoxWithConstraints(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(8.dp)) {
                                                val spacing = 4.dp; val cardWidth = (maxWidth - (spacing * 7) - 2.dp) / 8
                                                FlowRow(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(spacing, Alignment.Start), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                                    selectedDeckCards.forEach { card ->
                                                        Box(modifier = Modifier.width(cardWidth).aspectRatio(0.71f)) {
                                                            AsyncImage(model = viewModel.getCardImageModel(card), contentDescription = "Deck Card", modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(4.dp)).border(0.5.dp, Color.LightGray, RoundedCornerShape(4.dp)), contentScale = ContentScale.Crop)
                                                            Box(modifier = Modifier.align(Alignment.TopEnd).offset(x = 4.dp, y = (-4).dp).size(14.dp).background(Color.Red, CircleShape).border(1.dp, Color.White, CircleShape).clickable { viewModel.removeFromDeck(card.cardNum) }, contentAlignment = Alignment.Center) { Icon(Icons.Default.Close, contentDescription = "Remove", tint = Color.White, modifier = Modifier.size(8.dp)) }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                                .height(40.dp)
                                .border(1.dp, Color.Gray.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                                .background(Color.White, RoundedCornerShape(8.dp))
                                .padding(horizontal = 12.dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxSize()) {
                                Icon(Icons.Default.Search, contentDescription = "Search", tint = Color.Gray, modifier = Modifier.size(20.dp))
                                Spacer(Modifier.width(8.dp))
                                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                                    if (searchQuery.isEmpty()) { Text("Search cards...", color = Color.Gray, fontSize = 14.sp) }
                                    BasicTextField(
                                        value = searchQuery,
                                        onValueChange = { searchQuery = it },
                                        textStyle = TextStyle(fontSize = 14.sp, color = Color.Black),
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }

                                if (searchQuery.isNotEmpty()) {
                                    IconButton(onClick = { searchQuery = "" }, modifier = Modifier.size(32.dp)) {
                                        Icon(Icons.Default.Close, contentDescription = "Clear", tint = Color.Gray, modifier = Modifier.size(20.dp))
                                    }
                                }

                                IconButton(onClick = { isFilterExpanded = !isFilterExpanded }, modifier = Modifier.size(32.dp)) {
                                    Icon(Icons.Default.Tune, contentDescription = "Filter", tint = if (isFilterExpanded) ConanTheme.BowTieRed else Color.Gray, modifier = Modifier.size(20.dp))
                                }
                            }
                        }

                        AnimatedVisibility(visible = isFilterExpanded) {
                            FilterTable(
                                onColorChange = { selectedColors = it }, onTypeChange = { selectedTypes = it }, onRarityChange = { selectedRarities = it },
                                onLevelChange = { selectedLevels = it }, onApChange = { selectedAps = it }, onLpChange = { selectedLps = it },
                                onPackChange = { selectedPack = it }, onCategoryChange = { selectedCardCategory = it },
                                currentColors = selectedColors, currentTypes = selectedTypes, currentRarities = selectedRarities,
                                currentAps = selectedAps, currentLevels = selectedLevels, currentLps = selectedLps,
                                currentPack = selectedPack, currentCategory = selectedCardCategory,
                                availablePacks = availablePacks, availableCategories = availableCategories
                            )
                        }
                        HorizontalDivider()
                    }
                }
            ) { padding ->
                Box(Modifier.padding(padding).fillMaxSize().background(Color.White)) {
                    LazyVerticalGrid(columns = GridCells.Fixed(5), contentPadding = PaddingValues(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        val cardUsageStats = mutableMapOf<String, Int>()
                        deckMap.forEach { (id, count) -> viewModel.cardList.find { it.cardNum == id }?.let { cardUsageStats[it.cardId.ifBlank { it.cardNum }] = (cardUsageStats[it.cardId.ifBlank { it.cardNum }] ?: 0) + count } }
                        val mainDeckTotal = selectedDeckCards.size
                        items(filteredCards.size) { index ->
                            val card = filteredCards[index]
                            val isMaxed = mode == BuilderSelectionMode.DECK && ((cardUsageStats[card.cardId.ifBlank { card.cardNum }] ?: 0) >= 3 || mainDeckTotal >= 40)
                            CardGridItem(card = card, imageModel = viewModel.getCardImageModel(card), quantity = deckMap[card.cardNum] ?: 0, showControls = mode == BuilderSelectionMode.DECK, onClick = { if (mode == BuilderSelectionMode.DECK) { if (!isMaxed) onCardSelected(card) } else { previewCard = card } }, isDeckBuilder = true, isMaxedOut = isMaxed)
                        }
                    }
                }
            }
        }
    }
}

@Composable fun FilterTable(
    onColorChange: (Set<String>) -> Unit, onTypeChange: (Set<String>) -> Unit, onRarityChange: (Set<String>) -> Unit,
    onLevelChange: (Set<String>) -> Unit, onApChange: (Set<String>) -> Unit, onLpChange: (Set<String>) -> Unit,
    onPackChange: (String) -> Unit, onCategoryChange: (String) -> Unit, currentColors: Set<String>,
    currentTypes: Set<String>, currentRarities: Set<String>, currentAps: Set<String>, currentLevels: Set<String>,
    currentLps: Set<String>, currentPack: String, currentCategory: String, availablePacks: List<String>, availableCategories: List<String>
) {
    val colorMap = listOf("Blue" to "青", "Green" to "緑", "White" to "白", "Red" to "赤", "Yellow" to "黄", "Black" to "黒")
    val types = listOf("Partner", "Character", "Event", "Case")
    val rarities = listOf("D", "C", "CP", "CP2", "R", "RP", "SR", "SRP", "SRCP", "MR", "MRP", "MRCP", "SEC", "PR", "SP")
    val aps = listOf("0", "1000", "2000", "3000", "4000", "5000", "6000", "7000", "8000", "9000")
    val levels = (1..9).map { it.toString() }
    val lps = listOf("0","1", "2", "3")

    Column(Modifier.background(Color(0xFFF9F9F9)).padding(8.dp)) {
        FilterTableRow("Package") { SimpleDropdown(availablePacks, currentPack) { onPackChange(it) } }
        HorizontalDivider(color = Color.LightGray.copy(0.5f))
        FilterTableRow("Category") { SimpleDropdown(listOf("All") + availableCategories, currentCategory) { onCategoryChange(it) } }
        HorizontalDivider(color = Color.LightGray.copy(0.5f))
        FilterTableRow("Color") { Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) { colorMap.forEach { (engKey, kanjiLabel) -> val isSel = currentColors.contains(engKey); Box(modifier = Modifier.size(30.dp).background(if (isSel) getColor(engKey) else Color.White, RoundedCornerShape(4.dp)).border(if (isSel) 2.dp else 1.dp, if (isSel) Color.Black else Color.LightGray, RoundedCornerShape(4.dp)).clickable { onColorChange(if (isSel) currentColors - engKey else currentColors + engKey) }, contentAlignment = Alignment.Center) { Text(text = kanjiLabel, color = if (isSel && (engKey == "White" || engKey == "Yellow")) Color.Black else if (isSel) Color.White else getColor(engKey), fontSize = 12.sp, fontWeight = FontWeight.Bold) } } } }
        HorizontalDivider(color = Color.LightGray.copy(0.5f))
        FilterTableRow("Type") { LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) { items(types) { type -> FilterChipItem(type, currentTypes.contains(type)) { onTypeChange(if (it) currentTypes + type else currentTypes - type) } } } }
        HorizontalDivider(color = Color.LightGray.copy(0.5f))
        FilterTableRow("Rarity") { LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) { items(rarities) { rarity -> FilterChipItem(rarity, currentRarities.contains(rarity)) { onRarityChange(if (it) currentRarities + rarity else currentRarities - rarity) } } } }
        HorizontalDivider(color = Color.LightGray.copy(0.5f))
        FilterTableRow("Level") { LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) { items(levels) { level -> FilterChipItem(level, currentLevels.contains(level)) { onLevelChange(if (it) currentLevels + level else currentLevels - level) } } } }
        HorizontalDivider(color = Color.LightGray.copy(0.5f))
        FilterTableRow("AP") { LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) { items(aps) { ap -> FilterChipItem(ap, currentAps.contains(ap)) { onApChange(if (it) currentAps + ap else currentAps - ap) } } } }
        HorizontalDivider(color = Color.LightGray.copy(0.5f))
        FilterTableRow("LP") { LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) { items(lps) { lp -> FilterChipItem(lp, currentLps.contains(lp)) { onLpChange(if (it) currentLps + lp else currentLps - lp) } } } }
        Spacer(Modifier.height(8.dp))
        Button(onClick = { onColorChange(emptySet()); onTypeChange(emptySet()); onRarityChange(emptySet()); onApChange(emptySet()); onLevelChange(emptySet()); onLpChange(emptySet()); onPackChange("All"); onCategoryChange("All") }, modifier = Modifier.fillMaxWidth().height(36.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDCDCDC)), shape = RoundedCornerShape(6.dp), contentPadding = PaddingValues(0.dp)) { Text("Reset Filters", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 12.sp) }
    }
}

@Composable fun DBFilterRowLabel(label: String, content: @Composable () -> Unit) { Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) { Text(label, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.Gray, modifier = Modifier.width(80.dp)); Box(modifier = Modifier.weight(1f)) { content() } } }
@Composable fun DBCustomDropdown(selectedValue: String, options: List<String>, onValueChange: (String) -> Unit, modifier: Modifier = Modifier) { var expanded by remember { mutableStateOf(false) }; Box(modifier = modifier.height(32.dp).background(Color.White, RoundedCornerShape(4.dp)).border(1.dp, Color.LightGray, RoundedCornerShape(4.dp)).clickable { expanded = true }.padding(horizontal = 8.dp), contentAlignment = Alignment.CenterStart) { Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) { Text(selectedValue, fontSize = 12.sp, color = Color.Black, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f)); Icon(Icons.Default.ArrowDropDown, contentDescription = "Dropdown", tint = Color.Gray, modifier = Modifier.size(20.dp)) }; DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }, modifier = Modifier.background(Color.White).heightIn(max = 200.dp)) { options.forEach { option -> DropdownMenuItem(text = { Text(option, fontSize = 12.sp) }, onClick = { onValueChange(option); expanded = false }, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp), modifier = Modifier.height(32.dp)) } } } }
@Composable fun DBFilterChipItem(label: String, isSelected: Boolean, textColor: Color = Color.Black, onClick: () -> Unit) { val borderColor = if (isSelected) ConanTheme.BlueSuit else Color.LightGray.copy(alpha = 0.6f); val displayTextColor = if (isSelected && textColor == Color.Black) ConanTheme.BlueSuit else textColor; val bgColor = if (isSelected) ConanTheme.BlueSuit.copy(alpha=0.1f) else Color.White; Surface(onClick = onClick, shape = RoundedCornerShape(6.dp), color = bgColor, border = BorderStroke(1.dp, borderColor), modifier = Modifier.height(32.dp)) { Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 8.dp)) { Text(text = label, fontSize = 12.sp, color = displayTextColor, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium) } } }
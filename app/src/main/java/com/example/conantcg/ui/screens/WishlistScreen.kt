package com.example.conantcg.ui.screens

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.example.conantcg.R
import com.example.conantcg.data.model.WebCard
import com.example.conantcg.ui.components.FilterTable
import com.example.conantcg.ui.components.SwipeableCardDetailDialog
import com.example.conantcg.ui.viewmodel.CardViewModel
import com.example.conantcg.utils.CategoryMap
import com.example.conantcg.utils.ConanTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.ceil

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WishlistScreen(viewModel: CardViewModel, onNavigate: (AppScreen) -> Unit, onBack: () -> Unit) {

    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // 1. ดึงรายการการ์ดทั้งหมดใน Wishlist
    val wishlistedCards = viewModel.cardList.filter { viewModel.isWishlisted(it.cardNum) }

    var viewingContext by remember { mutableStateOf<Pair<List<WebCard>, Int>?>(null) }
    var isSavingAll by remember { mutableStateOf(false) }
    var saveProgress by remember { mutableIntStateOf(0) }

    // ==========================================
    // ส่วนควบคุม Filter และ Search
    // ==========================================
    var viewMode by rememberSaveable { mutableIntStateOf(1) } // 1 = Grid, 0 = List
    var isFilterExpanded by rememberSaveable { mutableStateOf(false) }

    var searchQuery by rememberSaveable { mutableStateOf("") }
    var selectedColors by rememberSaveable { mutableStateOf(setOf<String>()) }
    var selectedTypes by rememberSaveable { mutableStateOf(setOf<String>()) }
    var selectedRarities by rememberSaveable { mutableStateOf(setOf<String>()) }
    var selectedAps by rememberSaveable { mutableStateOf(setOf<String>()) }
    var selectedLevels by rememberSaveable { mutableStateOf(setOf<String>()) }
    var selectedLps by rememberSaveable { mutableStateOf(setOf<String>()) }
    var selectedCardCategory by rememberSaveable { mutableStateOf("All") }
    var selectedPack by rememberSaveable { mutableStateOf<String?>("All") }

    val translatedCategories = remember(wishlistedCards) {
        val allCats = mutableSetOf<String>()
        wishlistedCards.forEach { card -> card.categories.forEach { if(it.isNotBlank()) allCats.add(it.trim()) } }
        allCats.associateWith { CategoryMap.translate(it) }
    }

    val availablePacks = remember(wishlistedCards) { wishlistedCards.map { it.pack }.distinct().sorted() }
    val availableCategories = remember(translatedCategories) {
        val allTranslated = translatedCategories.values.distinct()
        val (others, normals) = allTranslated.partition { it.equals("Other", true) || it.equals("Others", true) || it == "อื่นๆ" }
        normals.sorted() + others.sorted()
    }

    val colorMap = remember { mapOf("Blue" to "青", "Green" to "緑", "White" to "白", "Red" to "赤", "Yellow" to "黄", "Black" to "黒", "Grey" to "グレー") }

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
            "date" to listOf("伊達", "航"), "scotch" to listOf("スコッチ", "景光"), "hiromitsu" to listOf("景光"), "amuro" to listOf("安室", "透", "降谷"),
            "toru" to listOf("透"), "bourbon" to listOf("バーボン"), "rei" to listOf("零", "降谷"), "furuya" to listOf("降谷"),
            "kazami" to listOf("風見", "裕也"), "yuya" to listOf("裕也"), "akai" to listOf("赤井", "秀一", "ライ", "沖矢"),
            "shuichi" to listOf("秀一"), "rye" to listOf("ライ"), "okiya" to listOf("沖矢", "昴"), "subaru" to listOf("昴"),
            "jodie" to listOf("ジョディ"), "james" to listOf("ジェイムズ"), "camel" to listOf("キャメル"), "kir" to listOf("キール", "水無", "怜奈"),
            "rena" to listOf("怜奈"), "gin" to listOf("ジン"), "vodka" to listOf("ウォッカ"), "vermouth" to listOf("ベルモット"),
            "chianti" to listOf("キャンティ"), "korn" to listOf("コルン"), "rum" to listOf("ラム"), "pinga" to listOf("ピンガ"),
            "sherry" to listOf("シェリー"), "kid" to listOf("キッド", "怪盗"), "kaitou" to listOf("怪盗"), "kaito" to listOf("快斗"),
            "kuroba" to listOf("黒羽"), "aoko" to listOf("青子"), "nakamori" to listOf("中森"), "sera" to listOf("世良", "真純"),
            "masumi" to listOf("真純"), "shukichi" to listOf("秀吉"), "haneda" to listOf("羽田"), "mary" to listOf("メアリー")
        )
    }

    // 2. การ์ดที่ถูกกรองแล้วสำหรับแสดงผล
    val displayCards = remember(wishlistedCards, selectedPack, searchQuery, selectedColors, selectedTypes, selectedRarities, selectedAps, selectedLevels, selectedLps, selectedCardCategory) {
        val baseList = if (selectedPack != null && selectedPack != "All") wishlistedCards.filter { it.pack == selectedPack } else wishlistedCards
        baseList.filter { card ->
            val query = searchQuery.trim()
            val lowerSearchQuery = query.lowercase()

            val jpSearchTerms = searchDictionary.entries
                .filter { it.key.contains(lowerSearchQuery) || lowerSearchQuery.contains(it.key) }
                .flatMap { it.value }

            val matchSearch = query.isEmpty() ||
                    card.cardNum.contains(query, true) ||
                    card.cardId.contains(query, true) ||
                    card.pack.contains(query, true) ||
                    card.name.contains(query, true) ||
                    jpSearchTerms.any { jpWord -> card.name.contains(jpWord, true) }

            val matchColor = selectedColors.isEmpty() || selectedColors.any { card.color.contains(it, true) || card.color.contains(colorMap[it] ?: "", true) }

            val matchType = selectedTypes.isEmpty() || selectedTypes.any { typeKey ->
                val jpType = when(typeKey) { "Partner" -> "パートナー"; "Character" -> "キャラ"; "Event" -> "イベント"; "Case" -> "事件"; else -> "" }
                card.type.contains(typeKey, true) || (jpType.isNotEmpty() && card.type.contains(jpType, true))
            }

            val matchRarity = selectedRarities.isEmpty() || selectedRarities.contains(card.rarity)
            val matchAp = selectedAps.isEmpty() || selectedAps.contains(card.ap)
            val matchLevel = selectedLevels.isEmpty() || selectedLevels.contains(card.cost.trim())
            val matchLp = selectedLps.isEmpty() || selectedLps.contains(card.lp.trim())
            val matchCat = when(selectedCardCategory) { "All" -> true; else -> card.categories.any { translatedCategories[it.trim()] == selectedCardCategory } }

            matchSearch && matchColor && matchType && matchRarity && matchAp && matchLevel && matchLp && matchCat
        }
    }

    val isAnyFilterActive = searchQuery.isNotEmpty() || selectedColors.isNotEmpty() || selectedTypes.isNotEmpty() || selectedRarities.isNotEmpty() || selectedAps.isNotEmpty() || selectedLevels.isNotEmpty() || selectedLps.isNotEmpty() || selectedCardCategory != "All" || (selectedPack != null && selectedPack != "All")

    Scaffold(
        topBar = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(elevation = 8.dp)
                    .background(Brush.verticalGradient(colors = listOf(ConanTheme.BlueSuit, Color(0xFF151C5E))))
                    .statusBarsPadding()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 8.dp)
                ) {
                    IconButton(onClick = onBack) {
                        Image(painter = painterResource(id = R.drawable.arrowleft), contentDescription = "Back", modifier = Modifier.size(30.dp))
                    }
                    Spacer(Modifier.weight(1f))
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("My Wishlist", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp, letterSpacing = 1.sp)
                        Text("${displayCards.size} Cards", color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp)
                    }
                    Spacer(Modifier.weight(1f))

                    // ปุ่ม Save All (บันทึกเฉพาะ displayCards)
                    if (displayCards.isNotEmpty()) {
                        if (isSavingAll) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(end = 8.dp)) {
                                Text("$saveProgress/${displayCards.size}", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                Spacer(Modifier.width(8.dp))
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                            }
                        } else {
                            IconButton(onClick = {
                                isSavingAll = true
                                saveProgress = 0
                                scope.launch(Dispatchers.IO) {
                                    try {
                                        val downloadedBitmaps = mutableListOf<Bitmap>()
                                        for (card in displayCards) {
                                            val request = ImageRequest.Builder(context)
                                                .data(viewModel.getCardImageModel(card))
                                                .allowHardware(false)
                                                .build()

                                            val result = context.imageLoader.execute(request)
                                            if (result is SuccessResult) {
                                                val bitmap = (result.drawable as? BitmapDrawable)?.bitmap
                                                if (bitmap != null) {
                                                    downloadedBitmaps.add(bitmap)
                                                }
                                            }
                                            withContext(Dispatchers.Main) { saveProgress++ }
                                        }

                                        if (downloadedBitmaps.isNotEmpty()) {
                                            val columns = 4
                                            val cardWidth = 300
                                            val cardHeight = 422
                                            val padding = 20

                                            val rows = ceil(downloadedBitmaps.size / columns.toDouble()).toInt()
                                            val totalWidth = (columns * cardWidth) + ((columns + 1) * padding)
                                            val totalHeight = (rows * cardHeight) + ((rows + 1) * padding)

                                            val combinedBitmap = Bitmap.createBitmap(totalWidth, totalHeight, Bitmap.Config.ARGB_8888)
                                            val canvas = Canvas(combinedBitmap)

                                            canvas.drawColor(android.graphics.Color.TRANSPARENT)

                                            var currentX = padding
                                            var currentY = padding

                                            for ((index, bitmap) in downloadedBitmaps.withIndex()) {
                                                val scaledBitmap = Bitmap.createScaledBitmap(bitmap, cardWidth, cardHeight, true)
                                                canvas.drawBitmap(scaledBitmap, currentX.toFloat(), currentY.toFloat(), null)

                                                currentX += cardWidth + padding
                                                if ((index + 1) % columns == 0) {
                                                    currentX = padding
                                                    currentY += cardHeight + padding
                                                }
                                            }

                                            val timeStamp = System.currentTimeMillis()

                                            val contentValues = ContentValues().apply {
                                                put(MediaStore.MediaColumns.DISPLAY_NAME, "Wishlist_$timeStamp.jpg")
                                                put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
                                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/ConanTCG")
                                                }
                                            }
                                            val resolver = context.contentResolver
                                            val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
                                            if (uri != null) {
                                                resolver.openOutputStream(uri)?.use { stream ->
                                                    combinedBitmap.compress(Bitmap.CompressFormat.JPEG, 100, stream)
                                                }
                                            }

                                            withContext(Dispatchers.Main) {
                                                Toast.makeText(context, "Saved ${displayCards.size} cards to Gallery!", Toast.LENGTH_LONG).show()
                                            }
                                        }
                                    } catch (e: Exception) {
                                        e.printStackTrace()
                                        withContext(Dispatchers.Main) {
                                            Toast.makeText(context, "Failed to create image.", Toast.LENGTH_SHORT).show()
                                        }
                                    } finally {
                                        withContext(Dispatchers.Main) { isSavingAll = false }
                                    }
                                }
                            }) {
                                Icon(Icons.Default.Download, contentDescription = "Save Filtered", tint = Color.White)
                            }
                        }
                    } else {
                        Spacer(Modifier.width(48.dp))
                    }
                }
            }
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { onNavigate(AppScreen.WISHLIST_ADD) },
                containerColor = ConanTheme.BowTieRed,
                contentColor = Color.White,
                icon = { Icon(Icons.Default.Add, "Add Cards") },
                text = { Text("Add Cards", fontWeight = FontWeight.Bold) },
                shape = RoundedCornerShape(16.dp),
                elevation = FloatingActionButtonDefaults.elevation(8.dp)
            )
        },
        containerColor = Color.Transparent
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {

            // หน้าว่างตอนไม่มี Wishlist เลยสักใบ
            if (wishlistedCards.isEmpty()) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Box(
                        modifier = Modifier.size(100.dp).background(Color.White.copy(0.1f), CircleShape).border(1.dp, Color.White.copy(0.3f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.FavoriteBorder, null, modifier = Modifier.size(50.dp), tint = Color.White.copy(alpha = 0.8f))
                    }
                    Spacer(Modifier.height(16.dp))
                    Text("Your wishlist is empty", fontWeight = FontWeight.Bold, fontSize = 20.sp, color = Color.White)
                    Spacer(Modifier.height(8.dp))
                    Text("Tap '+ Add Cards' to browse the catalog", color = Color.LightGray, fontSize = 14.sp)
                }
            } else {

                // ==========================================
                // ส่วน Search & Filter UI
                // ==========================================
                Column(Modifier.fillMaxWidth().background(Color.White).padding(8.dp)) {
                    BasicTextField(
                        value = searchQuery, onValueChange = { searchQuery = it },
                        modifier = Modifier.fillMaxWidth().height(40.dp).background(Color.White, RoundedCornerShape(8.dp)).border(1.dp, Color.LightGray, RoundedCornerShape(8.dp)),
                        singleLine = true, textStyle = TextStyle(fontSize = 14.sp, color = Color.Black),
                        decorationBox = { innerTextField ->
                            Row(modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Search, contentDescription = "Search", tint = Color.Gray, modifier = Modifier.size(20.dp))
                                Spacer(Modifier.width(8.dp))
                                Box(modifier = Modifier.weight(1f)) {
                                    if (searchQuery.isEmpty()) Text("Search your wishlist...", color = Color.Gray, fontSize = 14.sp)
                                    innerTextField()
                                }
                                if (searchQuery.isNotEmpty()) {
                                    IconButton(onClick = { searchQuery = "" }, modifier = Modifier.size(32.dp)) {
                                        Icon(Icons.Default.Close, contentDescription = "Clear", tint = Color.Gray, modifier = Modifier.size(20.dp))
                                    }
                                }
                                IconButton(onClick = { isFilterExpanded = !isFilterExpanded }, modifier = Modifier.size(32.dp)) {
                                    Icon(Icons.Default.Tune, contentDescription = "Filter", tint = if (isFilterExpanded) ConanTheme.BowTieRed else Color.Gray, modifier = Modifier.size(20.dp))
                                }
                                IconButton(onClick = { viewMode = if(viewMode==0) 1 else 0 }, modifier = Modifier.size(32.dp)) {
                                    Icon(if (viewMode == 0) Icons.Default.GridView else Icons.AutoMirrored.Filled.List, contentDescription = "View Mode", tint = Color.Gray, modifier = Modifier.size(20.dp))
                                }
                            }
                        }
                    )

                    // ลบ WishlistSegmentedFilter ออกตามที่ต้องการ

                    if (isAnyFilterActive) {
                        Text(text = "Found: ${displayCards.size} cards", style = MaterialTheme.typography.bodySmall, color = Color.Gray, modifier = Modifier.padding(top = 8.dp))
                    }

                    AnimatedVisibility(visible = isFilterExpanded) {
                        Column(Modifier.padding(top = 8.dp)) {
                            FilterTable(
                                onColorChange = { selectedColors = it }, onTypeChange = { selectedTypes = it }, onRarityChange = { selectedRarities = it },
                                onLevelChange = { selectedLevels = it }, onApChange = { selectedAps = it }, onLpChange = { selectedLps = it },
                                onPackChange = { pack -> selectedPack = pack },
                                onCategoryChange = { selectedCardCategory = it }, currentColors = selectedColors, currentTypes = selectedTypes,
                                currentRarities = selectedRarities, currentAps = selectedAps, currentLevels = selectedLevels,
                                currentLps = selectedLps, currentPack = selectedPack ?: "All", currentCategory = selectedCardCategory,
                                availablePacks = listOf("All") + availablePacks,
                                availableCategories = listOf("All") + availableCategories
                            )
                        }
                    }
                }

                // ==========================================
                // ส่วนแสดงผล List หรือ Grid
                // ==========================================
                Box(Modifier.weight(1f)) {
                    if (displayCards.isEmpty()) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("No matching cards.", color = Color.White)
                        }
                    } else {
                        if (viewMode == 0) {
                            LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(displayCards, key = { it.cardNum }) { card ->
                                    WishlistScreenCardListItem(
                                        card = card,
                                        imageModel = viewModel.getCardImageModel(card),
                                        isWishlisted = viewModel.isWishlisted(card.cardNum),
                                        onWishlistClick = { viewModel.toggleWishlist(card.cardNum) },
                                        onClick = {
                                            val index = displayCards.indexOf(card)
                                            if(index >= 0) viewingContext = displayCards to index
                                        }
                                    )
                                }
                            }
                        } else {
                            LazyVerticalGrid(columns = GridCells.Fixed(3), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                itemsIndexed(items = displayCards, key = { _, card -> card.cardNum }) { index, card ->
                                    WishlistScreenCardGridItem(
                                        card = card,
                                        imageModel = viewModel.getCardImageModel(card),
                                        isWishlisted = viewModel.isWishlisted(card.cardNum),
                                        onWishlistClick = { viewModel.toggleWishlist(card.cardNum) },
                                        onClick = { viewingContext = displayCards to index }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (viewingContext != null) {
        val (cardList, startIndex) = viewingContext!!
        SwipeableCardDetailDialog(
            initialCards = cardList,
            startIndex = startIndex,
            viewModel = viewModel,
            isCollectionMode = false,
            showWishlistButton = true,
            onDismiss = { viewingContext = null }
        )
    }
}

// -------------------------------------------------------------------------
// Component แสดงรูปการ์ด (Grid) พร้อมปุ่มหัวใจสำหรับหน้า WishlistScreen
// -------------------------------------------------------------------------
@Composable
private fun WishlistScreenCardGridItem(
    card: WebCard,
    imageModel: Any,
    isWishlisted: Boolean,
    onWishlistClick: () -> Unit,
    onClick: () -> Unit
) {
    val stripeColor = remember(card.color) {
        when {
            card.color.contains("Blue") || card.color.contains("青") -> Color(0xFF1E88E5)
            card.color.contains("Green") || card.color.contains("緑") -> Color(0xFF4CAF50)
            card.color.contains("Red") || card.color.contains("赤") -> Color(0xFFE53935)
            card.color.contains("Yellow") || card.color.contains("黄") -> Color(0xFFFFD600)
            card.color.contains("Black") || card.color.contains("黒") -> Color(0xFF212121)
            card.color.contains("White") || card.color.contains("白") -> Color(0xFFE0E0E0)
            card.color.contains("Grey") || card.color.contains("グレー") -> Color(0xFFB5B5B5)
            else -> Color(0xFFB5B5B5)
        }
    }

    val labelStyle = TextStyle(fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color.White, platformStyle = PlatformTextStyle(includeFontPadding = false), lineHeight = 9.sp)

    var triggerPulse by remember { mutableStateOf(false) }
    val heartScale by animateFloatAsState(targetValue = if (triggerPulse) 1.3f else 1f, animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow), finishedListener = { if (it == 1.3f) triggerPulse = false }, label = "")
    val animatedBgColor by animateColorAsState(targetValue = if (isWishlisted) Color.White.copy(alpha = 0.9f) else Color.Black.copy(alpha = 0.5f), animationSpec = tween(300), label = "")
    val animatedIconColor by animateColorAsState(targetValue = if (isWishlisted) Color(0xFFFF4081) else Color.White, animationSpec = tween(300), label = "")

    Card(
        modifier = Modifier.fillMaxWidth().aspectRatio(0.71f).clickable { onClick() },
        shape = RoundedCornerShape(8.dp),
        elevation = CardDefaults.cardElevation(4.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            AsyncImage(model = imageModel, contentDescription = card.name, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())

            Box(modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(4.dp).background(stripeColor))
            Box(modifier = Modifier.align(Alignment.BottomStart).padding(bottom = 4.dp).background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(topEnd = 4.dp)).padding(horizontal = 3.dp, vertical = 2.dp)) { Text(text = "${card.rarity} | ${card.cardId}", style = labelStyle) }
            Box(modifier = Modifier.align(Alignment.BottomEnd).padding(bottom = 4.dp).background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(topStart = 4.dp)).padding(horizontal = 3.dp, vertical = 2.dp)) { Text(text = card.cardNum, style = labelStyle) }

            Box(
                modifier = Modifier.align(Alignment.TopEnd).padding(4.dp).size(28.dp).clip(CircleShape).background(animatedBgColor)
                    .graphicsLayer { scaleX = heartScale; scaleY = heartScale }
                    .clickable { onWishlistClick(); triggerPulse = true },
                contentAlignment = Alignment.Center
            ) {
                Icon(imageVector = if (isWishlisted) Icons.Default.Favorite else Icons.Default.FavoriteBorder, contentDescription = "Wishlist", tint = animatedIconColor, modifier = Modifier.size(16.dp))
            }
        }
    }
}

// -------------------------------------------------------------------------
// Component แสดงรูปการ์ด (List) พร้อมปุ่มหัวใจสำหรับหน้า WishlistScreen
// -------------------------------------------------------------------------
@Composable
fun WishlistScreenCardListItem(
    card: WebCard,
    imageModel: Any,
    isWishlisted: Boolean,
    onWishlistClick: () -> Unit,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Row(modifier = Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(model = imageModel, contentDescription = card.name, contentScale = ContentScale.Crop, modifier = Modifier.width(60.dp).aspectRatio(0.71f).clip(RoundedCornerShape(4.dp)))
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(text = card.cardNum, fontSize = 12.sp, color = Color.Gray)
                    Text(text = "[${card.cardId}]", fontSize = 12.sp, color = Color.Gray)
                }
                Text(text = card.name, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(text = card.type, fontSize = 12.sp, color = ConanTheme.TextSecondary)
            }
            IconButton(onClick = onWishlistClick) {
                Icon(
                    imageVector = if (isWishlisted) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    contentDescription = "Wishlist",
                    tint = if (isWishlisted) Color(0xFFFF4081) else Color.Gray,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}
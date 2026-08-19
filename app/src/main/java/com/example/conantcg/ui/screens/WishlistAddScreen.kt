package com.example.conantcg.ui.screens

import android.content.res.Configuration
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.conantcg.R
import com.example.conantcg.data.model.PackCategory
import com.example.conantcg.data.model.WebCard
import com.example.conantcg.ui.components.*
import com.example.conantcg.ui.viewmodel.CardViewModel
import com.example.conantcg.utils.CategoryMap
import com.example.conantcg.utils.ConanTheme
import kotlinx.coroutines.launch

// ใช้ Enum ตัวเดียวกันสำหรับระบบ Filter
enum class WishlistFilterStatus { ALL, OWNED, UNOWNED }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WishlistAddScreen(viewModel: CardViewModel, onBack: () -> Unit) {
    val pagerState = rememberPagerState(pageCount = { PackCategory.entries.size })
    val scope = rememberCoroutineScope()
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    var viewingContext by remember { mutableStateOf<Pair<List<WebCard>, Int>?>(null) }

    val mapSaver = listSaver<MutableMap<PackCategory, String?>, String>(
        save = { map -> map.entries.map { "${it.key.name}|${it.value ?: ""}" } },
        restore = { list ->
            val map = mutableStateMapOf<PackCategory, String?>()
            list.forEach { item ->
                val parts = item.split("|")
                if (parts.size == 2) {
                    val key = PackCategory.valueOf(parts[0])
                    val value = parts[1].ifEmpty { null }
                    map[key] = value
                }
            }
            map
        }
    )
    val packSelectionMap = rememberSaveable(saver = mapSaver) { mutableStateMapOf() }
    val currentCategory = PackCategory.entries[pagerState.currentPage]
    val currentSelectedPack = packSelectionMap[currentCategory]

    var searchQuery by rememberSaveable { mutableStateOf("") }
    var selectedColors by rememberSaveable { mutableStateOf(setOf<String>()) }
    var selectedTypes by rememberSaveable { mutableStateOf(setOf<String>()) }
    var selectedRarities by rememberSaveable { mutableStateOf(setOf<String>()) }
    var selectedAps by rememberSaveable { mutableStateOf(setOf<String>()) }
    var selectedLevels by rememberSaveable { mutableStateOf(setOf<String>()) }
    var selectedLps by rememberSaveable { mutableStateOf(setOf<String>()) }
    var selectedCardCategory by rememberSaveable { mutableStateOf("All") }
    var statusFilter by rememberSaveable { mutableStateOf(WishlistFilterStatus.ALL) }

    val isAnyFilterActive = searchQuery.isNotEmpty() || selectedColors.isNotEmpty() || selectedTypes.isNotEmpty() || selectedRarities.isNotEmpty() || selectedAps.isNotEmpty() || selectedLevels.isNotEmpty() || selectedLps.isNotEmpty() || selectedCardCategory != "All" || statusFilter != WishlistFilterStatus.ALL

    fun resetFilters() {
        searchQuery = ""; selectedColors = emptySet(); selectedTypes = emptySet();
        selectedRarities = emptySet(); selectedAps = emptySet();
        selectedLevels = emptySet(); selectedLps = emptySet();
        selectedCardCategory = "All"; statusFilter = WishlistFilterStatus.ALL
    }

    BackHandler {
        when {
            viewingContext != null -> viewingContext = null
            currentSelectedPack != null -> {
                resetFilters()
                packSelectionMap[currentCategory] = null
            }
            isAnyFilterActive -> resetFilters()
            else -> onBack()
        }
    }

    LaunchedEffect(pagerState.currentPage) { resetFilters() }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            Column(Modifier.background(Brush.verticalGradient(colors = listOf(ConanTheme.BlueSuit, Color(0xFF151C5E)))).statusBarsPadding()) {
                if (isLandscape) {
                    Row(modifier = Modifier.fillMaxWidth().height(40.dp).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = {
                            if (currentSelectedPack != null) {
                                if (isAnyFilterActive) resetFilters()
                                else packSelectionMap[currentCategory] = null
                            } else onBack()
                        }) {
                            Image(painter = painterResource(id = R.drawable.arrowleft), contentDescription = "Back", modifier = Modifier.size(30.dp))
                        }
                        Spacer(Modifier.width(8.dp))
                        Text(text = "Add to Wishlist", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        if (currentSelectedPack != null) { Text(" > ", color = Color.White.copy(alpha = 0.7f)); Text(text = currentSelectedPack, color = Color.White.copy(alpha = 0.9f), fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f)) }
                    }
                } else {
                    Box(modifier = Modifier.fillMaxWidth().height(48.dp)) {
                        IconButton(onClick = {
                            if (currentSelectedPack != null) {
                                if (isAnyFilterActive) resetFilters()
                                else packSelectionMap[currentCategory] = null
                            } else onBack()
                        }, modifier = Modifier.align(Alignment.CenterStart)) {
                            Image(painter = painterResource(id = R.drawable.arrowleft), contentDescription = "Back", modifier = Modifier.size(30.dp))
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.align(Alignment.Center).padding(horizontal = 48.dp)) {
                            Text("Add to Wishlist", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                            if (currentSelectedPack != null) { Text(currentSelectedPack, color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                        }
                    }
                }
                AnimatedVisibility(visible = currentSelectedPack == null) {
                    AnimatedCategoryTabs(categories = PackCategory.entries, selectedIndex = pagerState.currentPage, onTabSelected = { index -> scope.launch { pagerState.animateScrollToPage(index) } })
                }
            }
        }
    ) { padding ->
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.padding(padding).fillMaxSize(),
            userScrollEnabled = currentSelectedPack == null
        ) { pageIndex ->
            WishlistCategoryBrowserPage(
                viewModel = viewModel,
                category = PackCategory.entries[pageIndex],
                selectedPack = packSelectionMap[PackCategory.entries[pageIndex]],
                onPackSelect = { pack -> packSelectionMap[PackCategory.entries[pageIndex]] = pack },
                onCardClick = { list, index -> viewingContext = list to index },
                searchQuery = searchQuery,
                onSearchQueryChange = { searchQuery = it },
                selectedColors = selectedColors,
                onColorsChange = { selectedColors = it },
                selectedTypes = selectedTypes,
                onTypesChange = { selectedTypes = it },
                selectedRarities = selectedRarities,
                onRaritiesChange = { selectedRarities = it },
                selectedAps = selectedAps,
                onApsChange = { selectedAps = it },
                selectedLevels = selectedLevels,
                onLevelsChange = { selectedLevels = it },
                selectedLps = selectedLps,
                onLpsChange = { selectedLps = it },
                selectedCardCategory = selectedCardCategory,
                onCategoryChange = { selectedCardCategory = it },
                statusFilter = statusFilter,
                onStatusChange = { statusFilter = it },
                isAnyFilterActive = isAnyFilterActive
            )
        }
    }

    if (viewingContext != null) {
        val (cardList, startIndex) = viewingContext!!
        SwipeableCardDetailDialog(
            initialCards = cardList,
            startIndex = startIndex,
            viewModel = viewModel,
            isCollectionMode = false,
            showWishlistButton = true, // ✅ สั่งเปิดปุ่มหัวใจ
            onDismiss = { viewingContext = null }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WishlistCategoryBrowserPage(
    viewModel: CardViewModel,
    category: PackCategory,
    selectedPack: String?,
    onPackSelect: (String?) -> Unit,
    onCardClick: (List<WebCard>, Int) -> Unit,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    selectedColors: Set<String>,
    onColorsChange: (Set<String>) -> Unit,
    selectedTypes: Set<String>,
    onTypesChange: (Set<String>) -> Unit,
    selectedRarities: Set<String>,
    onRaritiesChange: (Set<String>) -> Unit,
    selectedAps: Set<String>,
    onApsChange: (Set<String>) -> Unit,
    selectedLevels: Set<String>,
    onLevelsChange: (Set<String>) -> Unit,
    selectedLps: Set<String>,
    onLpsChange: (Set<String>) -> Unit,
    selectedCardCategory: String,
    onCategoryChange: (String) -> Unit,
    statusFilter: WishlistFilterStatus,
    onStatusChange: (WishlistFilterStatus) -> Unit,
    isAnyFilterActive: Boolean
) {
    var viewMode by rememberSaveable { mutableIntStateOf(1) }
    var isFilterExpanded by rememberSaveable { mutableStateOf(false) }

    val categoryCards = remember(viewModel.cardList.size, category) { viewModel.cardList.filter { it.getPackCategory() == category } }

    val translatedCategories = remember(categoryCards) {
        val allCats = mutableSetOf<String>()
        categoryCards.forEach { card -> card.categories.forEach { if(it.isNotBlank()) allCats.add(it.trim()) } }
        allCats.associateWith { CategoryMap.translate(it) }
    }

    val availablePacks = remember(categoryCards) { categoryCards.map { it.pack }.distinct().sorted() }
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
            "masumi" to listOf("真純"), "shukichi" to listOf("秀吉"), "haneda" to listOf("羽田"), "mary" to listOf("メアリー"),
            "detective" to listOf("探偵"), "detective boys" to listOf("少年探偵団"), "police" to listOf("警察"), "fbi" to listOf("FBI"),
            "black organization" to listOf("黒ずくめの組織")
        )
    }

    val displayCards = remember(categoryCards, selectedPack, searchQuery, selectedColors, selectedTypes, selectedRarities, selectedAps, selectedLevels, selectedLps, selectedCardCategory, statusFilter) {
        val baseList = if (selectedPack != null) categoryCards.filter { it.pack == selectedPack } else categoryCards
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
                val jpType = when(typeKey) {
                    "Partner" -> "パートナー"
                    "Character" -> "キャラ"
                    "Event" -> "イベント"
                    "Case" -> "事件"
                    else -> ""
                }
                card.type.contains(typeKey, true) || (jpType.isNotEmpty() && card.type.contains(jpType, true))
            }

            val matchRarity = selectedRarities.isEmpty() || selectedRarities.contains(card.rarity)
            val matchAp = selectedAps.isEmpty() || selectedAps.contains(card.ap)
            val matchLevel = selectedLevels.isEmpty() || selectedLevels.contains(card.cost.trim())
            val matchLp = selectedLps.isEmpty() || selectedLps.contains(card.lp.trim())

            val matchCat = when(selectedCardCategory) { "All" -> true; else -> card.categories.any { translatedCategories[it.trim()] == selectedCardCategory } }

            val isOwned = viewModel.getQuantity(card.cardNum) > 0
            val matchStatus = when(statusFilter) {
                WishlistFilterStatus.OWNED -> isOwned
                WishlistFilterStatus.UNOWNED -> !isOwned
                WishlistFilterStatus.ALL -> true
            }

            matchSearch && matchColor && matchType && matchRarity && matchAp && matchLevel && matchLp && matchCat && matchStatus
        }
    }

    Column(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxWidth().background(Color.White).padding(8.dp)) {
            BasicTextField(
                value = searchQuery, onValueChange = onSearchQueryChange,
                modifier = Modifier.fillMaxWidth().height(40.dp).background(Color.White, RoundedCornerShape(8.dp)).border(1.dp, Color.LightGray, RoundedCornerShape(8.dp)),
                singleLine = true, textStyle = TextStyle(fontSize = 14.sp, color = Color.Black),
                decorationBox = { innerTextField ->
                    Row(modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Search, contentDescription = "Search", tint = Color.Gray, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Box(modifier = Modifier.weight(1f)) {
                            if (searchQuery.isEmpty()) Text("Search in ${category.title}...", color = Color.Gray, fontSize = 14.sp)
                            innerTextField()
                        }
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { onSearchQueryChange("") }, modifier = Modifier.size(32.dp)) {
                                Icon(Icons.Default.Close, contentDescription = "Clear", tint = Color.Gray, modifier = Modifier.size(20.dp))
                            }
                        }
                        IconButton(onClick = { isFilterExpanded = !isFilterExpanded }, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Default.Tune, contentDescription = "Filter", tint = if (isFilterExpanded) ConanTheme.BowTieRed else Color.Gray, modifier = Modifier.size(20.dp))
                        }
                        if (selectedPack != null || isAnyFilterActive) {
                            IconButton(onClick = { viewMode = if(viewMode==0) 1 else 0 }, modifier = Modifier.size(32.dp)) {
                                Icon(if (viewMode == 0) Icons.Default.GridView else Icons.AutoMirrored.Filled.List, contentDescription = "View Mode", tint = Color.Gray, modifier = Modifier.size(20.dp))
                            }
                        }
                    }
                }
            )

            Spacer(Modifier.height(12.dp))
            WishlistSegmentedFilter(
                currentFilter = statusFilter,
                onFilterSelected = onStatusChange
            )

            if (isAnyFilterActive || selectedPack != null) Text(text = "Found: ${displayCards.size} cards", style = MaterialTheme.typography.bodySmall, color = Color.Gray, modifier = Modifier.padding(top = 8.dp))
            AnimatedVisibility(visible = isFilterExpanded) {
                Column(Modifier.padding(top = 8.dp)) {
                    FilterTable(
                        onColorChange=onColorsChange, onTypeChange=onTypesChange, onRarityChange=onRaritiesChange,
                        onLevelChange=onLevelsChange, onApChange=onApsChange, onLpChange=onLpsChange,
                        onPackChange={ pack -> onPackSelect(if(pack=="All") null else pack) },
                        onCategoryChange=onCategoryChange, currentColors=selectedColors, currentTypes=selectedTypes,
                        currentRarities=selectedRarities, currentAps=selectedAps, currentLevels=selectedLevels,
                        currentLps=selectedLps, currentPack=selectedPack?:"All", currentCategory=selectedCardCategory,
                        availablePacks = listOf("All") + availablePacks,
                        availableCategories = listOf("All") + availableCategories
                    )
                }
            }
        }

        Box(Modifier.weight(1f)) {
            AnimatedContent(
                targetState = selectedPack == null && !isAnyFilterActive,
                transitionSpec = {
                    if (targetState) {
                        (fadeIn(tween(300)) + scaleIn(initialScale = 0.95f, animationSpec = tween(300)))
                            .togetherWith(fadeOut(tween(200)))
                    } else {
                        (fadeIn(tween(300)) + slideInVertically(animationSpec = tween(300)) { it / 6 })
                            .togetherWith(fadeOut(tween(200)) + scaleOut(targetScale = 0.95f, animationSpec = tween(200)))
                    }
                },
                label = "PackToCardTransition"
            ) { isShowingPacks ->
                if (isShowingPacks) {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        items(availablePacks, key = { it }) { packName ->
                            val stats = viewModel.getPackStats(packName)
                            PackSelectionItem(
                                packName = packName,
                                owned = stats.owned,
                                total = stats.total,
                                showCollectionStats = false,
                                imageModel = viewModel.getPackCoverImage(packName),
                                isDownloaded = viewModel.isPackDownloaded(packName),
                                downloadProgress = viewModel.downloadingPacks[packName],
                                onDownloadClick = { viewModel.downloadPack(packName) },
                                onClick = { onPackSelect(packName) }
                            )
                        }
                    }
                } else {
                    Column {
                        if (displayCards.isEmpty()) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("No matching cards.", color = Color.White) }
                        else {
                            if (viewMode == 0) {
                                LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    items(displayCards, key = { it.cardNum }) { card ->
                                        WishlistLocalCardListItem(
                                            card = card,
                                            imageModel = viewModel.getCardImageModel(card),
                                            isWishlisted = viewModel.isWishlisted(card.cardNum),
                                            onWishlistClick = { viewModel.toggleWishlist(card.cardNum) },
                                            onClick = {
                                                val index = displayCards.indexOf(card)
                                                if(index >= 0) onCardClick(displayCards, index)
                                            }
                                        )
                                    }
                                }
                            } else {
                                LazyVerticalGrid(columns = GridCells.Fixed(3), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    items(displayCards, key = { it.cardNum }) { card ->
                                        WishlistLocalCardGridItem(
                                            card = card,
                                            imageModel = viewModel.getCardImageModel(card),
                                            isWishlisted = viewModel.isWishlisted(card.cardNum),
                                            onWishlistClick = { viewModel.toggleWishlist(card.cardNum) },
                                            onClick = {
                                                val index = displayCards.indexOf(card)
                                                if(index >= 0) onCardClick(displayCards, index)
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// แถบกรอง All / Owned / Unowned แบบแนวยาว
@Composable
fun WishlistSegmentedFilter(currentFilter: WishlistFilterStatus, onFilterSelected: (WishlistFilterStatus) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(32.dp)
            .border(1.dp, Color.LightGray, RoundedCornerShape(8.dp))
            .clip(RoundedCornerShape(8.dp))
            .background(Color.White),
        verticalAlignment = Alignment.CenterVertically
    ) {
        WishlistFilterSegment(
            text = "All",
            isSelected = currentFilter == WishlistFilterStatus.ALL,
            onClick = { onFilterSelected(WishlistFilterStatus.ALL) },
            modifier = Modifier.weight(1f)
        )
        VerticalDivider(modifier = Modifier.height(16.dp), color = Color.LightGray)
        WishlistFilterSegment(
            text = "Owned",
            isSelected = currentFilter == WishlistFilterStatus.OWNED,
            onClick = { onFilterSelected(WishlistFilterStatus.OWNED) },
            modifier = Modifier.weight(1f)
        )
        VerticalDivider(modifier = Modifier.height(16.dp), color = Color.LightGray)
        WishlistFilterSegment(
            text = "Unowned",
            isSelected = currentFilter == WishlistFilterStatus.UNOWNED,
            onClick = { onFilterSelected(WishlistFilterStatus.UNOWNED) },
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
fun WishlistFilterSegment(text: String, isSelected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxHeight()
            .clickable { onClick() }
            .background(if (isSelected) Color.Black.copy(alpha = 0.05f) else Color.Transparent),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            fontSize = 13.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            color = if (isSelected) Color.Black else Color.Gray,
            textAlign = TextAlign.Center
        )
    }
}

// -------------------------------------------------------------------------
// Component แสดงรูปการ์ด
// -------------------------------------------------------------------------

@Composable
fun WishlistLocalCardGridItem(
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

    // ✅ นำสไตล์ตัวอักษรแบบเดียวกับใน SharedComponents.kt มาใช้เป๊ะๆ
    val labelStyle = TextStyle(
        fontSize = 9.sp,
        fontWeight = FontWeight.Bold,
        color = Color.White,
        platformStyle = PlatformTextStyle(includeFontPadding = false),
        lineHeight = 9.sp
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(0.71f)
            .clickable { onClick() },
        shape = RoundedCornerShape(8.dp),
        elevation = CardDefaults.cardElevation(4.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // รูปการ์ด
            coil.compose.AsyncImage(
                model = imageModel,
                contentDescription = card.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )

            // แถบสีด้านล่างสุด (Color Bar)
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(4.dp) // เปลี่ยนเป็น 4.dp ให้เท่า SharedComponents.kt
                    .background(stripeColor)
            )

            // ✅ Bottom Labels ซ้าย-ขวา แบบเป๊ะๆ ตาม SharedComponents.kt
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(bottom = 4.dp)
                    .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(topEnd = 4.dp))
                    .padding(horizontal = 3.dp, vertical = 2.dp)
            ) {
                Text(text = "${card.rarity} | ${card.cardId}", style = labelStyle)
            }

            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(bottom = 4.dp)
                    .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(topStart = 4.dp))
                    .padding(horizontal = 3.dp, vertical = 2.dp)
            ) {
                Text(text = card.cardNum, style = labelStyle)
            }

            // ปุ่มหัวใจ (ที่เพิ่มเข้ามาเฉพาะหน้า Wishlist)
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
                    .size(24.dp) // ย่อขนาดลงหน่อยให้เท่ากล่อง x จำนวน
                    .background(if (isWishlisted) Color.White.copy(alpha = 0.9f) else Color.Black.copy(alpha = 0.5f), CircleShape)
                    .clickable { onWishlistClick() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isWishlisted) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    contentDescription = "Wishlist",
                    tint = if (isWishlisted) Color(0xFFFF4081) else Color.White,
                    modifier = Modifier.size(14.dp)
                )
            }
        }
    }
}

@Composable
fun WishlistLocalCardListItem(
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
        Row(
            modifier = Modifier.padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            coil.compose.AsyncImage(
                model = imageModel,
                contentDescription = card.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.width(60.dp).aspectRatio(0.71f).clip(RoundedCornerShape(4.dp))
            )
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
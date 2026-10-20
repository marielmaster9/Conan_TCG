package com.example.conantcg.ui.screens

import android.content.ContentValues
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed as gridItemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderSpecial
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Menu
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.example.conantcg.R
import com.example.conantcg.data.model.WebCard
import com.example.conantcg.ui.components.FilterTable
import com.example.conantcg.ui.viewmodel.CardViewModel
import com.example.conantcg.utils.CategoryMap
import com.example.conantcg.utils.ConanTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.ceil

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WishlistScreen(viewModel: CardViewModel, onNavigate: (AppScreen) -> Unit, onBack: () -> Unit) {

    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var wishlistRefreshTrigger by remember { mutableStateOf(0) }
    var locallyRemovedCards by remember { mutableStateOf(setOf<String>()) }

    var isEditMode by remember { mutableStateOf(false) }

    val hasCustomFolders = viewModel.wishlistFolders.any { it != "Default" && it != "All" }

    val hasAnyWishlistedCard = remember(wishlistRefreshTrigger, viewModel.cardList, viewModel.wishlistFolders) {
        viewModel.cardList.any { card ->
            val isWishInAnyFolder = viewModel.wishlistFolders.any { folder ->
                viewModel.isWishlisted(card.cardNum, folder)
            } || viewModel.isWishlisted(card.cardNum)

            isWishInAnyFolder && viewModel.getQuantity(card.cardNum) == 0
        }
    }

    val hasFolderOrCard = hasCustomFolders || hasAnyWishlistedCard

    LaunchedEffect(hasFolderOrCard) {
        if (hasFolderOrCard && (viewModel.currentWishlistFolder.isBlank() || viewModel.currentWishlistFolder == "Default")) {
            viewModel.currentWishlistFolder = "All"
        }
    }

    val displayFolders = remember(viewModel.wishlistFolders, hasFolderOrCard, wishlistRefreshTrigger) {
        val list = viewModel.wishlistFolders.filter { it != "Default" && it != "All" }.toMutableList()
        if (hasFolderOrCard) {
            list.add(0, "All")
        }
        list
    }

    val folderCardCounts = remember(viewModel.cardList, viewModel.wishlistFolders, displayFolders, wishlistRefreshTrigger) {
        displayFolders.associateWith { folderName ->
            viewModel.cardList.count { card ->
                val isWish = if (folderName == "All") {
                    viewModel.wishlistFolders.any { f -> viewModel.isWishlisted(card.cardNum, f) } || viewModel.isWishlisted(card.cardNum)
                } else {
                    viewModel.isWishlisted(card.cardNum, folderName)
                }
                isWish && viewModel.getQuantity(card.cardNum) == 0
            }
        }
    }

    val wishlistedCards = remember(wishlistRefreshTrigger, viewModel.currentWishlistFolder, viewModel.cardList, viewModel.wishlistFolders, locallyRemovedCards) {
        viewModel.cardList.filter { card ->
            if (locallyRemovedCards.contains(card.cardNum)) return@filter false

            val isWish = if (viewModel.currentWishlistFolder == "All" || viewModel.currentWishlistFolder.isBlank()) {
                viewModel.wishlistFolders.any { folder ->
                    viewModel.isWishlisted(card.cardNum, folder)
                } || viewModel.isWishlisted(card.cardNum)
            } else {
                viewModel.isWishlisted(card.cardNum, viewModel.currentWishlistFolder)
            }
            isWish && viewModel.getQuantity(card.cardNum) == 0
        }
    }

    var previewIndex by remember { mutableStateOf<Int?>(null) }
    var isSavingAll by remember { mutableStateOf(false) }
    var saveProgress by remember { mutableStateOf(0) }

    var showCreateFolderDialog by remember { mutableStateOf(false) }
    var newFolderName by remember { mutableStateOf("") }

    var folderToRename by remember { mutableStateOf<String?>(null) }
    var folderToDelete by remember { mutableStateOf<String?>(null) }
    var showDeleteFolderDialog by remember { mutableStateOf(false) }

    var draggedIndex by remember { mutableStateOf<Int?>(null) }
    var dragAccumulatedX by remember { mutableStateOf(0f) }

    var viewMode by rememberSaveable { mutableStateOf(1) }
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
        wishlistedCards.forEach { card -> card.categories.forEach { if (it.isNotBlank()) allCats.add(it.trim()) } }
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
                    .shadow(elevation = 4.dp)
                    .background(Brush.verticalGradient(colors = listOf(ConanTheme.BlueSuit, Color(0xFF151C5E))))
                    .statusBarsPadding()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .padding(horizontal = 4.dp)
                ) {
                    IconButton(onClick = onBack) {
                        Image(painter = painterResource(id = R.drawable.arrowleft), contentDescription = "Back", modifier = Modifier.size(26.dp))
                    }
                    Spacer(Modifier.weight(1f))
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("My Wishlist", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 17.sp, letterSpacing = 0.5.sp)
                        Text("${displayCards.size} Cards", color = Color.White.copy(alpha = 0.7f), fontSize = 11.sp)
                    }
                    Spacer(Modifier.weight(1f))

                    if (displayCards.isNotEmpty()) {
                        if (isSavingAll) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(end = 8.dp)) {
                                Text("$saveProgress/${displayCards.size}", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                Spacer(Modifier.width(6.dp))
                                CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
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
                                Icon(Icons.Default.Download, contentDescription = "Save Filtered", tint = Color.White, modifier = Modifier.size(22.dp))
                            }
                        }
                    } else {
                        Spacer(Modifier.width(44.dp))
                    }
                }
            }
        },
        floatingActionButton = {
            var showAddMenu by remember { mutableStateOf(false) }

            Box(contentAlignment = Alignment.BottomEnd) {
                ExtendedFloatingActionButton(
                    onClick = { showAddMenu = true },
                    containerColor = if (isEditMode) Color(0xFFFF9800) else ConanTheme.BowTieRed,
                    contentColor = Color.White,
                    icon = {
                        Icon(
                            imageVector = if (isEditMode) Icons.Default.Check else Icons.Default.Menu,
                            contentDescription = "Menu",
                            modifier = Modifier.size(20.dp)
                        )
                    },
                    text = { Text("Menu", fontWeight = FontWeight.Bold, fontSize = 14.sp) },
                    shape = RoundedCornerShape(14.dp),
                    elevation = FloatingActionButtonDefaults.elevation(6.dp),
                    modifier = Modifier.height(44.dp)
                )

                MaterialTheme(
                    shapes = MaterialTheme.shapes.copy(extraSmall = RoundedCornerShape(14.dp))
                ) {
                    DropdownMenu(
                        expanded = showAddMenu,
                        onDismissRequest = { showAddMenu = false },
                        modifier = Modifier
                            .width(200.dp)
                            .background(Color.White)
                    ) {
                        DropdownMenuItem(
                            text = {
                                Text("Add Cards", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF333333))
                            },
                            leadingIcon = {
                                Box(
                                    modifier = Modifier.size(32.dp).background(Color(0xFFE3F2FD), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.Search, contentDescription = "Add Cards", tint = Color(0xFF1E88E5), modifier = Modifier.size(18.dp))
                                }
                            },
                            onClick = {
                                showAddMenu = false
                                onNavigate(AppScreen.WISHLIST_ADD)
                            },
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                        )

                        Spacer(
                            modifier = Modifier.fillMaxWidth().height(1.dp).padding(horizontal = 12.dp).background(Color.LightGray.copy(alpha = 0.3f))
                        )

                        DropdownMenuItem(
                            text = {
                                Text("Create Folder", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF333333))
                            },
                            leadingIcon = {
                                Box(
                                    modifier = Modifier.size(32.dp).background(Color(0xFFE8F5E9), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CreateNewFolder,
                                        contentDescription = "Create Folder",
                                        tint = Color(0xFF4CAF50),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            },
                            onClick = {
                                showAddMenu = false
                                newFolderName = ""
                                showCreateFolderDialog = true
                            },
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                        )

                        Spacer(
                            modifier = Modifier.fillMaxWidth().height(1.dp).padding(horizontal = 12.dp).background(Color.LightGray.copy(alpha = 0.3f))
                        )

                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = if (isEditMode) "Done Editing" else "Edit Folders",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (isEditMode) Color(0xFF4CAF50) else Color(0xFF333333)
                                )
                            },
                            leadingIcon = {
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .background(if (isEditMode) Color(0xFFE8F5E9) else Color(0xFFFFF3E0), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = if (isEditMode) Icons.Default.Check else Icons.Default.Edit,
                                        contentDescription = "Edit Folders",
                                        tint = if (isEditMode) Color(0xFF4CAF50) else Color(0xFFFF9800),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            },
                            onClick = {
                                showAddMenu = false
                                isEditMode = !isEditMode
                            },
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                        )
                    }
                }
            }
        },
        containerColor = Color.Transparent
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {

            if (showCreateFolderDialog) {
                AlertDialog(
                    onDismissRequest = { showCreateFolderDialog = false },
                    shape = RoundedCornerShape(16.dp),
                    containerColor = Color.White,
                    title = {
                        Text(
                            text = "Create New Folder",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF1E293B)
                        )
                    },
                    text = {
                        OutlinedTextField(
                            value = newFolderName,
                            onValueChange = { newFolderName = it },
                            placeholder = { Text("e.g., My Favorites", color = Color.LightGray, fontSize = 14.sp) },
                            singleLine = true,
                            textStyle = TextStyle(fontSize = 14.sp),
                            shape = RoundedCornerShape(10.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = ConanTheme.BlueSuit,
                                unfocusedBorderColor = Color(0xFFE2E8F0),
                                focusedContainerColor = Color(0xFFF8F9FA),
                                unfocusedContainerColor = Color(0xFFF8F9FA),
                                cursorColor = ConanTheme.BlueSuit
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                if (newFolderName.isNotBlank()) {
                                    viewModel.createWishlistFolder(newFolderName.trim())
                                    wishlistRefreshTrigger++
                                }
                                showCreateFolderDialog = false
                                newFolderName = ""
                            },
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = ConanTheme.BlueSuit,
                                contentColor = Color.White
                            ),
                            enabled = newFolderName.isNotBlank()
                        ) {
                            Text("Create", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                    },
                    dismissButton = {
                        TextButton(
                            onClick = {
                                showCreateFolderDialog = false
                                newFolderName = ""
                            }
                        ) {
                            Text("Cancel", color = Color.Gray, fontWeight = FontWeight.Medium, fontSize = 14.sp)
                        }
                    }
                )
            }

            folderToRename?.let { oldName ->
                var renameInputText by remember { mutableStateOf(oldName) }

                AlertDialog(
                    onDismissRequest = { folderToRename = null },
                    shape = RoundedCornerShape(16.dp),
                    containerColor = Color.White,
                    title = {
                        Text(
                            text = "Rename Folder",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF1E293B)
                        )
                    },
                    text = {
                        OutlinedTextField(
                            value = renameInputText,
                            onValueChange = { renameInputText = it },
                            singleLine = true,
                            textStyle = TextStyle(fontSize = 14.sp),
                            shape = RoundedCornerShape(10.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = ConanTheme.BlueSuit,
                                unfocusedBorderColor = Color(0xFFE2E8F0),
                                focusedContainerColor = Color(0xFFF8F9FA),
                                unfocusedContainerColor = Color(0xFFF8F9FA),
                                cursorColor = ConanTheme.BlueSuit
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                val trimmed = renameInputText.trim()
                                if (trimmed.isNotBlank() && trimmed != oldName) {
                                    viewModel.renameWishlistFolder(oldName, trimmed)
                                    if (viewModel.currentWishlistFolder == oldName) {
                                        viewModel.currentWishlistFolder = trimmed
                                    }
                                    wishlistRefreshTrigger++
                                }
                                folderToRename = null
                            },
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = ConanTheme.BlueSuit,
                                contentColor = Color.White
                            ),
                            enabled = renameInputText.isNotBlank()
                        ) {
                            Text("Save", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { folderToRename = null }) {
                            Text("Cancel", color = Color.Gray, fontWeight = FontWeight.Medium, fontSize = 14.sp)
                        }
                    }
                )
            }

            if (showDeleteFolderDialog && folderToDelete != null) {
                AlertDialog(
                    onDismissRequest = {
                        showDeleteFolderDialog = false
                        folderToDelete = null
                    },
                    title = { Text("Delete Folder", fontSize = 16.sp) },
                    text = { Text("Are you sure you want to delete '${folderToDelete}'?") },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                val targetFolder = folderToDelete
                                if (targetFolder != null && targetFolder != "All") {
                                    viewModel.deleteWishlistFolder(targetFolder)
                                    if (viewModel.currentWishlistFolder == targetFolder) {
                                        viewModel.currentWishlistFolder = "All"
                                    }
                                    wishlistRefreshTrigger++
                                }
                                showDeleteFolderDialog = false
                                folderToDelete = null
                            },
                            colors = ButtonDefaults.textButtonColors(contentColor = Color.Red)
                        ) { Text("Delete") }
                    },
                    dismissButton = {
                        TextButton(onClick = {
                            showDeleteFolderDialog = false
                            folderToDelete = null
                        }) { Text("Cancel") }
                    }
                )
            }

            // --- FOLDER BAR ---
            if (displayFolders.isNotEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.White)
                        .padding(vertical = 6.dp)
                ) {
                    AnimatedVisibility(visible = isEditMode) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "✏️ Tap to rename • Hold & drag to reorder • ✕ to delete",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFFD97706)
                            )
                        }
                    }

                    LazyRow(
                        state = rememberLazyListState(),
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(horizontal = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        itemsIndexed(
                            items = displayFolders,
                            key = { _, folder -> folder }
                        ) { index, folderName ->
                            val isSelected = viewModel.currentWishlistFolder == folderName
                            val isDragging = draggedIndex == index
                            val isAllFolder = folderName == "All"
                            val cardCount = folderCardCounts[folderName] ?: 0

                            val scaleState by animateFloatAsState(
                                targetValue = if (isSelected) 1.02f else 1.0f,
                                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                                label = "pillScale"
                            )

                            val activeGradient = Brush.horizontalGradient(
                                colors = listOf(ConanTheme.BlueSuit, Color(0xFF2563EB))
                            )
                            val inactiveColor = Color(0xFFF1F5F9)
                            val editBorderColor = Color(0xFFF59E0B)

                            val txtColor by animateColorAsState(
                                targetValue = if (isSelected) Color.White else Color(0xFF475467),
                                label = "tabText"
                            )

                            Box(
                                modifier = Modifier
                                    .graphicsLayer {
                                        scaleX = scaleState
                                        scaleY = scaleState
                                        if (isDragging) {
                                            translationX = dragAccumulatedX
                                            scaleX = 1.06f
                                            scaleY = 1.06f
                                            shadowElevation = 6.dp.toPx()
                                        }
                                    }
                                    .then(
                                        if (!isAllFolder && isEditMode) {
                                            Modifier.pointerInput(folderName, displayFolders.size) {
                                                detectDragGesturesAfterLongPress(
                                                    onDragStart = {
                                                        draggedIndex = index
                                                        dragAccumulatedX = 0f
                                                    },
                                                    onDrag = { change, dragAmount ->
                                                        change.consume()
                                                        dragAccumulatedX += dragAmount.x
                                                        val stepWidth = 110f

                                                        var currentIdx = draggedIndex ?: index

                                                        while (dragAccumulatedX > stepWidth && currentIdx < displayFolders.size - 1) {
                                                            val nextIdx = currentIdx + 1
                                                            viewModel.reorderWishlistFolders(currentIdx, nextIdx)
                                                            wishlistRefreshTrigger++
                                                            currentIdx = nextIdx
                                                            draggedIndex = nextIdx
                                                            dragAccumulatedX -= stepWidth
                                                        }

                                                        while (dragAccumulatedX < -stepWidth && currentIdx > 1) {
                                                            val prevIdx = currentIdx - 1
                                                            viewModel.reorderWishlistFolders(currentIdx, prevIdx)
                                                            wishlistRefreshTrigger++
                                                            currentIdx = prevIdx
                                                            draggedIndex = prevIdx
                                                            dragAccumulatedX += stepWidth
                                                        }
                                                    },
                                                    onDragEnd = {
                                                        draggedIndex = null
                                                        dragAccumulatedX = 0f
                                                    },
                                                    onDragCancel = {
                                                        draggedIndex = null
                                                        dragAccumulatedX = 0f
                                                    }
                                                )
                                            }
                                        } else Modifier
                                    )
                                    .shadow(
                                        elevation = if (isSelected) 2.dp else 0.dp,
                                        shape = RoundedCornerShape(16.dp),
                                        spotColor = ConanTheme.BlueSuit.copy(alpha = 0.35f)
                                    )
                                    .clip(RoundedCornerShape(16.dp))
                                    .then(
                                        if (isSelected) {
                                            Modifier.background(activeGradient)
                                        } else {
                                            Modifier.background(inactiveColor)
                                        }
                                    )
                                    .border(
                                        width = if (isEditMode && !isAllFolder) 1.5.dp else 1.dp,
                                        color = if (isEditMode && !isAllFolder) editBorderColor else if (isSelected) Color.Transparent else Color(0xFFE2E8F0),
                                        shape = RoundedCornerShape(16.dp)
                                    )
                                    .clickable {
                                        if (isEditMode) {
                                            if (!isAllFolder) {
                                                folderToRename = folderName
                                            }
                                        } else {
                                            viewModel.currentWishlistFolder = folderName
                                        }
                                    }
                                    .padding(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        imageVector = if (isAllFolder) Icons.Default.FolderSpecial else Icons.Default.Folder,
                                        contentDescription = null,
                                        tint = if (isSelected) Color.White.copy(alpha = 0.9f) else Color(0xFF64748B),
                                        modifier = Modifier.size(14.dp)
                                    )

                                    Spacer(Modifier.width(4.dp))

                                    Text(
                                        text = folderName,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        color = txtColor,
                                        fontSize = 12.sp,
                                        letterSpacing = 0.1.sp
                                    )

                                    Spacer(Modifier.width(4.dp))

                                    Box(
                                        modifier = Modifier
                                            .clip(CircleShape)
                                            .background(
                                                if (isSelected) Color.White.copy(alpha = 0.25f)
                                                else Color(0xFFE2E8F0)
                                            )
                                            .padding(horizontal = 5.dp, vertical = 1.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "$cardCount",
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isSelected) Color.White else Color(0xFF475467)
                                        )
                                    }

                                    if (isEditMode && !isAllFolder) {
                                        Spacer(Modifier.width(4.dp))
                                        Box(
                                            modifier = Modifier
                                                .size(16.dp)
                                                .clip(CircleShape)
                                                .background(if (isSelected) Color.White.copy(alpha = 0.3f) else Color(0xFFFEE2E2))
                                                .clickable {
                                                    folderToDelete = folderName
                                                    showDeleteFolderDialog = true
                                                },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Close,
                                                contentDescription = "Delete Folder",
                                                tint = if (isSelected) Color.White else Color(0xFFEF4444),
                                                modifier = Modifier.size(10.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (!hasFolderOrCard && wishlistedCards.isEmpty()) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Box(
                        modifier = Modifier.size(90.dp).background(Color.White.copy(0.1f), CircleShape).border(1.dp, Color.White.copy(0.3f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.FavoriteBorder, null, modifier = Modifier.size(44.dp), tint = Color.White.copy(alpha = 0.8f))
                    }
                    Spacer(Modifier.height(12.dp))
                    Text("Your wishlist is empty", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Color.White)
                    Spacer(Modifier.height(6.dp))
                    Text("Tap 'Menu' to add cards or folders", color = Color.LightGray, fontSize = 13.sp)
                }
            } else {
                Column(Modifier.fillMaxWidth().background(Color.White).padding(horizontal = 8.dp, vertical = 6.dp)) {
                    BasicTextField(
                        value = searchQuery, onValueChange = { searchQuery = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(38.dp)
                            .background(Color(0xFFF8F9FA), RoundedCornerShape(8.dp))
                            .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(8.dp)),
                        singleLine = true, textStyle = TextStyle(fontSize = 13.sp, color = Color.Black),
                        decorationBox = { innerTextField ->
                            Row(modifier = Modifier.fillMaxSize().padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Search, contentDescription = "Search", tint = Color.Gray, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Box(modifier = Modifier.weight(1f)) {
                                    if (searchQuery.isEmpty()) Text("Search your wishlist...", color = Color.Gray, fontSize = 13.sp)
                                    innerTextField()
                                }
                                if (searchQuery.isNotEmpty()) {
                                    IconButton(onClick = { searchQuery = "" }, modifier = Modifier.size(28.dp)) {
                                        Icon(Icons.Default.Close, contentDescription = "Clear", tint = Color.Gray, modifier = Modifier.size(18.dp))
                                    }
                                }
                                IconButton(onClick = { isFilterExpanded = !isFilterExpanded }, modifier = Modifier.size(28.dp)) {
                                    Icon(Icons.Default.Tune, contentDescription = "Filter", tint = if (isFilterExpanded) ConanTheme.BowTieRed else Color.Gray, modifier = Modifier.size(18.dp))
                                }
                                IconButton(onClick = { viewMode = if (viewMode == 0) 1 else 0 }, modifier = Modifier.size(28.dp)) {
                                    Icon(if (viewMode == 0) Icons.Default.GridView else Icons.AutoMirrored.Filled.List, contentDescription = "View Mode", tint = Color.Gray, modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                    )

                    if (isAnyFilterActive) {
                        Text(text = "Found: ${displayCards.size} cards", style = MaterialTheme.typography.bodySmall, color = Color.Gray, modifier = Modifier.padding(top = 4.dp, start = 4.dp))
                    }

                    AnimatedVisibility(visible = isFilterExpanded) {
                        Column(Modifier.padding(top = 6.dp)) {
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

                Box(Modifier.weight(1f)) {
                    if (displayCards.isEmpty()) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("No matching cards.", color = Color.White, fontSize = 14.sp)
                        }
                    } else {
                        if (viewMode == 0) {
                            LazyColumn(
                                contentPadding = PaddingValues(8.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                itemsIndexed(displayCards, key = { _, card -> card.cardNum }) { index, card ->
                                    val isWish = if (viewModel.currentWishlistFolder == "All" || viewModel.currentWishlistFolder.isBlank()) {
                                        viewModel.wishlistFolders.any { folder ->
                                            viewModel.isWishlisted(card.cardNum, folder)
                                        } || viewModel.isWishlisted(card.cardNum)
                                    } else {
                                        viewModel.isWishlisted(card.cardNum, viewModel.currentWishlistFolder)
                                    }

                                    WishlistScreenCardListItem(
                                        card = card,
                                        imageModel = viewModel.getCardImageModel(card),
                                        isWishlisted = isWish,
                                        onWishlistClick = {
                                            locallyRemovedCards = locallyRemovedCards + card.cardNum

                                            if (viewModel.currentWishlistFolder == "All" || viewModel.currentWishlistFolder.isBlank()) {
                                                val originalFolder = viewModel.currentWishlistFolder
                                                viewModel.wishlistFolders.forEach { folder ->
                                                    if (viewModel.isWishlisted(card.cardNum, folder)) {
                                                        viewModel.currentWishlistFolder = folder
                                                        viewModel.toggleWishlist(card.cardNum)
                                                    }
                                                }
                                                if (viewModel.isWishlisted(card.cardNum)) {
                                                    viewModel.currentWishlistFolder = "Default"
                                                    viewModel.toggleWishlist(card.cardNum)
                                                }
                                                viewModel.currentWishlistFolder = originalFolder
                                            } else {
                                                viewModel.toggleWishlist(card.cardNum)
                                            }

                                            wishlistRefreshTrigger++
                                        },
                                        onClick = {
                                            previewIndex = index
                                        }
                                    )
                                }
                            }
                        } else {
                            LazyVerticalGrid(
                                columns = GridCells.Fixed(3),
                                contentPadding = PaddingValues(8.dp),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                gridItemsIndexed(displayCards, key = { _, card -> card.cardNum }) { index, card ->
                                    val isWish = if (viewModel.currentWishlistFolder == "All" || viewModel.currentWishlistFolder.isBlank()) {
                                        viewModel.wishlistFolders.any { folder ->
                                            viewModel.isWishlisted(card.cardNum, folder)
                                        } || viewModel.isWishlisted(card.cardNum)
                                    } else {
                                        viewModel.isWishlisted(card.cardNum, viewModel.currentWishlistFolder)
                                    }

                                    WishlistScreenCardGridItem(
                                        card = card,
                                        imageModel = viewModel.getCardImageModel(card),
                                        isWishlisted = isWish,
                                        onWishlistClick = {
                                            locallyRemovedCards = locallyRemovedCards + card.cardNum

                                            if (viewModel.currentWishlistFolder == "All" || viewModel.currentWishlistFolder.isBlank()) {
                                                val originalFolder = viewModel.currentWishlistFolder
                                                viewModel.wishlistFolders.forEach { folder ->
                                                    if (viewModel.isWishlisted(card.cardNum, folder)) {
                                                        viewModel.currentWishlistFolder = folder
                                                        viewModel.toggleWishlist(card.cardNum)
                                                    }
                                                }
                                                if (viewModel.isWishlisted(card.cardNum)) {
                                                    viewModel.currentWishlistFolder = "Default"
                                                    viewModel.toggleWishlist(card.cardNum)
                                                }
                                                viewModel.currentWishlistFolder = originalFolder
                                            } else {
                                                viewModel.toggleWishlist(card.cardNum)
                                            }

                                            wishlistRefreshTrigger++
                                        },
                                        onClick = {
                                            previewIndex = index
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

    // --- Modern Slide Carousel Dialog ---
    previewIndex?.let { initialIndex ->
        val validInitialPage = initialIndex.coerceIn(0, (displayCards.size - 1).coerceAtLeast(0))
        val pagerState = rememberPagerState(
            initialPage = validInitialPage,
            pageCount = { displayCards.size }
        )

        Dialog(
            onDismissRequest = {
                previewIndex = null
                locallyRemovedCards = emptySet()
                wishlistRefreshTrigger++
            },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.65f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        previewIndex = null
                        locallyRemovedCards = emptySet()
                        wishlistRefreshTrigger++
                    },
                contentAlignment = Alignment.Center
            ) {
                val currentCard = displayCards.getOrNull(pagerState.currentPage)

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                        .navigationBarsPadding(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    // 1. Top Header: ตัวเลขมุมซ้ายบน + ปุ่มปิดมุมขวาบน
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp, start = 20.dp, end = 20.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.4f))
                                .border(1.dp, Color.White.copy(alpha = 0.25f), CircleShape)
                                .padding(horizontal = 14.dp, vertical = 6.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "${pagerState.currentPage + 1}",
                                    color = Color.White,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.ExtraBold
                                )
                                Text(
                                    text = " / ${displayCards.size}",
                                    color = Color.White.copy(alpha = 0.7f),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }

                        IconButton(
                            onClick = {
                                previewIndex = null
                                locallyRemovedCards = emptySet()
                                wishlistRefreshTrigger++
                            },
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.4f))
                                .border(1.dp, Color.White.copy(alpha = 0.25f), CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close Preview",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    // 2. Card Image Carousel
                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .padding(vertical = 10.dp),
                        contentPadding = PaddingValues(horizontal = 42.dp),
                        pageSpacing = 12.dp
                    ) { page ->
                        val card = displayCards.getOrNull(page)
                        val pageOffset = ((pagerState.currentPage - page) + pagerState.currentPageOffsetFraction)
                        val absOffset = abs(pageOffset)

                        val scale = 0.88f + (1f - 0.88f) * (1f - absOffset.coerceIn(0f, 1f))
                        val alpha = 0.45f + (1f - 0.45f) * (1f - absOffset.coerceIn(0f, 1f))

                        if (card != null) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(0.707f)
                                    .graphicsLayer {
                                        scaleX = scale
                                        scaleY = scale
                                        this.alpha = alpha
                                    }
                                    .shadow(
                                        elevation = if (absOffset < 0.5f) 16.dp else 0.dp,
                                        shape = RoundedCornerShape(16.dp),
                                        spotColor = Color.Black
                                    )
                                    .clip(RoundedCornerShape(16.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                AsyncImage(
                                    model = viewModel.getCardImageModel(card),
                                    contentDescription = card.name.orEmpty(),
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clip(RoundedCornerShape(16.dp))
                                )
                            }
                        }
                    }

                    // 3. Card Description Panel
                    currentCard?.let { card ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(Color.Black.copy(alpha = 0.5f))
                                .border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(16.dp))
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = card.name.orEmpty(),
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = card.cardNum.orEmpty(),
                                    color = Color(0xFFFFD700),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                            }

                            Spacer(Modifier.height(6.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                val typeMap = mapOf(
                                    "パートナー" to "Partner",
                                    "キャラ" to "Character",
                                    "イベント" to "Event",
                                    "事件" to "Case"
                                )

                                val rarity = card.rarity.orEmpty().trim()
                                if (rarity.isNotBlank() && rarity != "null" && rarity != "-") {
                                    PreviewDetailBadge(text = rarity, color = Color(0xFF3B82F6))
                                }

                                val rawType = card.type.orEmpty().trim()
                                val displayType = typeMap[rawType] ?: rawType
                                if (displayType.isNotBlank() && displayType != "null" && displayType != "-") {
                                    PreviewDetailBadge(text = displayType, color = Color(0xFF10B981))
                                }

                                val cost = card.cost.orEmpty().trim()
                                if (cost.isNotBlank() && cost != "null" && cost != "-") {
                                    PreviewDetailBadge(text = "Cost: $cost", color = Color(0xFFF59E0B))
                                }

                                val ap = card.ap.orEmpty().trim()
                                if (ap.isNotBlank() && ap != "null" && ap != "-") {
                                    PreviewDetailBadge(text = "AP: $ap", color = Color(0xFFEC4899))
                                }

                                val lp = card.lp.orEmpty().trim()
                                if (lp.isNotBlank() && lp != "null" && lp != "-") {
                                    PreviewDetailBadge(text = "LP: $lp", color = Color(0xFF8B5CF6))
                                }
                            }

                            val effectText = card.text.orEmpty()
                            if (effectText.isNotBlank()) {
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    text = effectText,
                                    color = Color.White.copy(alpha = 0.88f),
                                    fontSize = 11.sp,
                                    lineHeight = 15.sp,
                                    maxLines = 3,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }

                }
            }
        }
    }
}

@Composable
fun AnimatedWishlistButton(
    isWishlisted: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    activeColor: Color = ConanTheme.BowTieRed,
    inactiveColor: Color = Color.Gray,
    iconSize: Float = 22f
) {
    var isPressed by remember { mutableStateOf(false) }

    val scale by animateFloatAsState(
        targetValue = if (isPressed) 1.3f else 1.0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        finishedListener = { isPressed = false },
        label = "HeartScaleAnim"
    )

    IconButton(
        onClick = {
            isPressed = true
            onClick()
        },
        modifier = modifier.graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
    ) {
        AnimatedContent(
            targetState = isWishlisted,
            transitionSpec = {
                (scaleIn() + fadeIn()).togetherWith(scaleOut() + fadeOut())
            },
            label = "HeartContentAnim"
        ) { wishlisted ->
            Icon(
                imageVector = if (wishlisted) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                contentDescription = "Wishlist",
                tint = if (wishlisted) activeColor else inactiveColor,
                modifier = Modifier.size(iconSize.dp)
            )
        }
    }
}

@Composable
fun WishlistScreenCardListItem(
    card: WebCard,
    imageModel: Any?,
    isWishlisted: Boolean,
    onWishlistClick: () -> Unit,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.5.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = imageModel,
                contentDescription = card.name,
                modifier = Modifier
                    .size(width = 54.dp, height = 76.dp)
                    .clip(RoundedCornerShape(4.dp)),
                contentScale = ContentScale.Crop
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = card.name,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "${card.cardNum} • ${card.rarity}",
                    fontSize = 11.sp,
                    color = Color.Gray
                )
            }

            AnimatedWishlistButton(
                isWishlisted = isWishlisted,
                onClick = onWishlistClick,
                inactiveColor = Color.Gray,
                iconSize = 22f
            )
        }
    }
}

@Composable
fun WishlistScreenCardGridItem(
    card: WebCard,
    imageModel: Any?,
    isWishlisted: Boolean,
    onWishlistClick: () -> Unit,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(0.7f)
            .clickable { onClick() },
        shape = RoundedCornerShape(6.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.5.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            AsyncImage(
                model = imageModel,
                contentDescription = card.name,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )

            AnimatedWishlistButton(
                isWishlisted = isWishlisted,
                onClick = onWishlistClick,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(3.dp)
                    .size(24.dp)
                    .background(Color.Black.copy(alpha = 0.45f), CircleShape),
                activeColor = ConanTheme.BowTieRed,
                inactiveColor = Color.White,
                iconSize = 14f
            )
        }
    }
}

@Composable
fun PreviewDetailBadge(
    text: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(color.copy(alpha = 0.25f))
            .border(1.dp, color.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Text(
            text = text,
            color = Color.White,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}
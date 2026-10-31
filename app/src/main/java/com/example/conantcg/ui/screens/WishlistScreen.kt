
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

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed as gridItemsIndexed
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
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
import com.example.conantcg.utils.ConanCardSearch
import com.example.conantcg.utils.ConanTheme

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

import kotlin.math.abs
import kotlin.math.ceil

@OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalFoundationApi::class
)
@Composable
fun WishlistScreen(
    viewModel: CardViewModel,
    onNavigate: (AppScreen) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var wishlistRefreshTrigger by remember { mutableStateOf(0) }
    var locallyRemovedCards by remember {
        mutableStateOf(setOf<String>())
    }

    var isEditMode by remember { mutableStateOf(false) }
    var previewIndex by remember { mutableStateOf<Int?>(null) }
    var isSavingAll by remember { mutableStateOf(false) }
    var saveProgress by remember { mutableStateOf(0) }

    var showCreateFolderDialog by remember {
        mutableStateOf(false)
    }
    var newFolderName by remember {
        mutableStateOf("")
    }

    var folderToRename by remember {
        mutableStateOf<String?>(null)
    }
    var folderToDelete by remember {
        mutableStateOf<String?>(null)
    }
    var showDeleteFolderDialog by remember {
        mutableStateOf(false)
    }

    var viewMode by rememberSaveable {
        mutableStateOf(1)
    }
    var isFilterExpanded by rememberSaveable {
        mutableStateOf(false)
    }

    var searchQuery by rememberSaveable {
        mutableStateOf("")
    }
    var selectedColors by rememberSaveable {
        mutableStateOf(setOf<String>())
    }
    var selectedTypes by rememberSaveable {
        mutableStateOf(setOf<String>())
    }
    var selectedRarities by rememberSaveable {
        mutableStateOf(setOf<String>())
    }
    var selectedAps by rememberSaveable {
        mutableStateOf(setOf<String>())
    }
    var selectedLevels by rememberSaveable {
        mutableStateOf(setOf<String>())
    }
    var selectedLps by rememberSaveable {
        mutableStateOf(setOf<String>())
    }
    var selectedCardCategory by rememberSaveable {
        mutableStateOf("All")
    }
    var selectedPack by rememberSaveable {
        mutableStateOf<String?>("All")
    }

    // ------------------------------------------------
    // FOLDERS AND WISHLIST DATA
    // ------------------------------------------------

    val savedFolderNames = viewModel.wishlistFolders.toList()

    val customFolders = savedFolderNames.filter {
        it != "Default" && it != "All"
    }

    // Keep a temporary order for fluid drag animations; persist once on drop.
    var orderedFolders by remember { mutableStateOf(customFolders) }
    var draggingFolder by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(customFolders) {
        if (draggingFolder == null) {
            orderedFolders = customFolders
        }
    }

    val hasCustomFolders = customFolders.isNotEmpty()

    val hasAnyWishlistedCard = viewModel.cardList.any { card ->
        savedFolderNames.any { folder ->
            viewModel.isWishlisted(card.cardNum, folder)
        } && viewModel.getQuantity(card.cardNum) == 0
    }

    val hasFolderOrCard =
        hasCustomFolders || hasAnyWishlistedCard

    LaunchedEffect(hasFolderOrCard) {
        if (
            hasFolderOrCard &&
            (viewModel.currentWishlistFolder.isBlank() ||
                    viewModel.currentWishlistFolder == "Default")
        ) {
            viewModel.currentWishlistFolder = "All"
        }
    }

    val displayFolders = buildList {
        if (hasFolderOrCard) add("All")
        addAll(orderedFolders)
    }

    val folderCardCounts = displayFolders.associateWith {
            folderName ->
        viewModel.cardList.count { card ->
            val isWish = if (folderName == "All") {
                savedFolderNames.any { folder ->
                    viewModel.isWishlisted(card.cardNum, folder)
                }
            } else {
                viewModel.isWishlisted(
                    card.cardNum,
                    folderName
                )
            }
            isWish && viewModel.getQuantity(card.cardNum) == 0
        }
    }

    val currentFolder = viewModel.currentWishlistFolder

    val wishlistedCards = viewModel.cardList.filter { card ->
        if (card.cardNum in locallyRemovedCards) {
            false
        } else {
            val isWish = if (
                currentFolder == "All" ||
                currentFolder.isBlank()
            ) {
                savedFolderNames.any { folder ->
                    viewModel.isWishlisted(card.cardNum, folder)
                }
            } else {
                viewModel.isWishlisted(
                    card.cardNum,
                    currentFolder
                )
            }
            isWish && viewModel.getQuantity(card.cardNum) == 0
        }
    }

    val translatedCategories = remember(wishlistedCards) {
        val categories = mutableSetOf<String>()
        wishlistedCards.forEach { card ->
            card.categories.forEach { category ->
                if (category.isNotBlank()) {
                    categories.add(category.trim())
                }
            }
        }
        categories.associateWith {
            CategoryMap.translate(it)
        }
    }

    val availablePacks = remember(wishlistedCards) {
        wishlistedCards.map { it.pack }.distinct().sorted()
    }

    val availableCategories = remember(translatedCategories) {
        val translated = translatedCategories.values.distinct()
        val (others, normal) = translated.partition {
            it.equals("Other", true) ||
                    it.equals("Others", true) ||
                    it == "อื่นๆ"
        }
        normal.sorted() + others.sorted()
    }

    val colorMap = remember {
        mapOf(
            "Blue" to "青",
            "Green" to "緑",
            "White" to "白",
            "Red" to "赤",
            "Yellow" to "黄",
            "Black" to "黒",
            "Grey" to "グレー"
        )
    }

    val displayCards = remember(
        wishlistedCards,
        selectedPack,
        searchQuery,
        selectedColors,
        selectedTypes,
        selectedRarities,
        selectedAps,
        selectedLevels,
        selectedLps,
        selectedCardCategory
    ) {
        val baseList = if (
            selectedPack != null &&
            selectedPack != "All"
        ) {
            wishlistedCards.filter {
                it.pack == selectedPack
            }
        } else {
            wishlistedCards
        }

        baseList.filter { card ->
            val matchSearch =
                ConanCardSearch.matches(card, searchQuery)

            val matchColor =
                selectedColors.isEmpty() ||
                        selectedColors.any { color ->
                            card.color.contains(color, true) ||
                                    card.color.contains(
                                        colorMap[color] ?: "",
                                        true
                                    )
                        }

            val matchType =
                selectedTypes.isEmpty() ||
                        selectedTypes.any { type ->
                            val japaneseType = when (type) {
                                "Partner" -> "パートナー"
                                "Character" -> "キャラ"
                                "Event" -> "イベント"
                                "Case" -> "事件"
                                else -> ""
                            }

                            card.type.contains(type, true) ||
                                    (
                                            japaneseType.isNotEmpty() &&
                                                    card.type.contains(japaneseType, true)
                                            )
                        }

            val matchRarity =
                selectedRarities.isEmpty() ||
                        selectedRarities.contains(card.rarity)

            val matchAp =
                selectedAps.isEmpty() ||
                        selectedAps.contains(card.ap)

            val matchLevel =
                selectedLevels.isEmpty() ||
                        selectedLevels.contains(card.cost.trim())

            val matchLp =
                selectedLps.isEmpty() ||
                        selectedLps.contains(card.lp.trim())

            val matchCategory =
                selectedCardCategory == "All" ||
                        card.categories.any { category ->
                            translatedCategories[category.trim()] ==
                                    selectedCardCategory
                        }

            matchSearch &&
                    matchColor &&
                    matchType &&
                    matchRarity &&
                    matchAp &&
                    matchLevel &&
                    matchLp &&
                    matchCategory
        }
    }

    val isAnyFilterActive =
        searchQuery.isNotEmpty() ||
                selectedColors.isNotEmpty() ||
                selectedTypes.isNotEmpty() ||
                selectedRarities.isNotEmpty() ||
                selectedAps.isNotEmpty() ||
                selectedLevels.isNotEmpty() ||
                selectedLps.isNotEmpty() ||
                selectedCardCategory != "All" ||
                (selectedPack != null && selectedPack != "All")

    fun removeWishlistCard(cardNum: String) {
        locallyRemovedCards = locallyRemovedCards + cardNum

        if (
            viewModel.currentWishlistFolder == "All" ||
            viewModel.currentWishlistFolder.isBlank()
        ) {
            savedFolderNames.forEach { folder ->
                if (viewModel.isWishlisted(cardNum, folder)) {
                    viewModel.toggleWishlist(cardNum, folder)
                }
            }
        } else {
            viewModel.toggleWishlist(
                cardNum,
                viewModel.currentWishlistFolder
            )
        }
        wishlistRefreshTrigger++
    }

    // ------------------------------------------------
    // MAIN SCREEN
    // ------------------------------------------------

    Scaffold(
        topBar = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(4.dp)
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                ConanTheme.BlueSuit,
                                Color(0xFF151C5E)
                            )
                        )
                    )
                    .statusBarsPadding()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack) {
                        Image(
                            painter = painterResource(
                                R.drawable.arrowleft
                            ),
                            contentDescription = "Back",
                            modifier = Modifier.size(26.dp)
                        )
                    }

                    Spacer(Modifier.weight(1f))

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            "My Wishlist",
                            color = Color.White,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp
                        )
                        Text(
                            "${displayCards.size} Cards",
                            color = Color.White.copy(alpha = 0.7f),
                            fontSize = 11.sp
                        )
                    }

                    Spacer(Modifier.weight(1f))

                    if (displayCards.isNotEmpty()) {
                        if (isSavingAll) {
                            Row(
                                modifier = Modifier.padding(end = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "$saveProgress/${displayCards.size}",
                                    color = Color.White,
                                    fontSize = 11.sp
                                )
                                Spacer(Modifier.width(6.dp))
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    color = Color.White,
                                    strokeWidth = 2.dp
                                )
                            }
                        } else {
                            IconButton(
                                onClick = {
                                    isSavingAll = true
                                    saveProgress = 0

                                    scope.launch(Dispatchers.IO) {
                                        try {
                                            val downloaded =
                                                mutableListOf<Bitmap>()

                                            for (card in displayCards) {
                                                val request =
                                                    ImageRequest.Builder(context)
                                                        .data(
                                                            viewModel.getCardImageModel(
                                                                card
                                                            )
                                                        )
                                                        .allowHardware(false)
                                                        .build()

                                                val result =
                                                    context.imageLoader.execute(
                                                        request
                                                    )

                                                if (result is SuccessResult) {
                                                    val bitmap =
                                                        (result.drawable
                                                                as? BitmapDrawable)
                                                            ?.bitmap
                                                    if (bitmap != null) {
                                                        downloaded.add(bitmap)
                                                    }
                                                }

                                                withContext(Dispatchers.Main) {
                                                    saveProgress++
                                                }
                                            }

                                            if (downloaded.isNotEmpty()) {
                                                val columns = 4
                                                val cardWidth = 300
                                                val cardHeight = 422
                                                val imagePadding = 20

                                                val rows = ceil(
                                                    downloaded.size /
                                                            columns.toDouble()
                                                ).toInt()

                                                val width =
                                                    columns * cardWidth +
                                                            (columns + 1) * imagePadding

                                                val height =
                                                    rows * cardHeight +
                                                            (rows + 1) * imagePadding

                                                val combined =
                                                    Bitmap.createBitmap(
                                                        width,
                                                        height,
                                                        Bitmap.Config.ARGB_8888
                                                    )

                                                val canvas = Canvas(combined)
                                                canvas.drawColor(
                                                    android.graphics.Color.TRANSPARENT
                                                )

                                                var x = imagePadding
                                                var y = imagePadding

                                                downloaded.forEachIndexed {
                                                        index, bitmap ->

                                                    val scaled =
                                                        Bitmap.createScaledBitmap(
                                                            bitmap,
                                                            cardWidth,
                                                            cardHeight,
                                                            true
                                                        )

                                                    canvas.drawBitmap(
                                                        scaled,
                                                        x.toFloat(),
                                                        y.toFloat(),
                                                        null
                                                    )
                                                    x += cardWidth + imagePadding

                                                    if ((index + 1) % columns == 0) {
                                                        x = imagePadding
                                                        y += cardHeight +
                                                                imagePadding
                                                    }
                                                }

                                                val values = ContentValues().apply {
                                                    put(
                                                        MediaStore.MediaColumns.DISPLAY_NAME,
                                                        "Wishlist_${System.currentTimeMillis()}.jpg"
                                                    )
                                                    put(
                                                        MediaStore.MediaColumns.MIME_TYPE,
                                                        "image/jpeg"
                                                    )
                                                    if (
                                                        Build.VERSION.SDK_INT >=
                                                        Build.VERSION_CODES.Q
                                                    ) {
                                                        put(
                                                            MediaStore.MediaColumns.RELATIVE_PATH,
                                                            Environment.DIRECTORY_PICTURES +
                                                                    "/ConanTCG"
                                                        )
                                                    }
                                                }

                                                val resolver = context.contentResolver
                                                val uri = resolver.insert(
                                                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                                                    values
                                                )

                                                if (uri != null) {
                                                    resolver.openOutputStream(uri)
                                                        ?.use { stream ->
                                                            combined.compress(
                                                                Bitmap.CompressFormat.JPEG,
                                                                100,
                                                                stream
                                                            )
                                                        }
                                                }

                                                withContext(Dispatchers.Main) {
                                                    Toast.makeText(
                                                        context,
                                                        "Saved ${displayCards.size} cards to Gallery!",
                                                        Toast.LENGTH_LONG
                                                    ).show()
                                                }
                                            }
                                        } catch (error: Exception) {
                                            error.printStackTrace()
                                            withContext(Dispatchers.Main) {
                                                Toast.makeText(
                                                    context,
                                                    "Failed to create image.",
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                            }
                                        } finally {
                                            withContext(Dispatchers.Main) {
                                                isSavingAll = false
                                            }
                                        }
                                    }
                                }
                            ) {
                                Icon(
                                    Icons.Default.Download,
                                    contentDescription = "Save Filtered",
                                    tint = Color.White,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                    } else {
                        Spacer(Modifier.width(44.dp))
                    }
                }
            }
        },

        floatingActionButton = {
            var showAddMenu by remember {
                mutableStateOf(false)
            }

            Box(contentAlignment = Alignment.BottomEnd) {
                ExtendedFloatingActionButton(
                    onClick = { showAddMenu = true },
                    containerColor = if (isEditMode) {
                        Color(0xFFFF9800)
                    } else {
                        ConanTheme.BowTieRed
                    },
                    contentColor = Color.White,
                    icon = {
                        Icon(
                            if (isEditMode) {
                                Icons.Default.Check
                            } else {
                                Icons.Default.Menu
                            },
                            contentDescription = "Menu",
                            modifier = Modifier.size(20.dp)
                        )
                    },
                    text = {
                        Text(
                            "Menu",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    },
                    shape = RoundedCornerShape(14.dp),
                    elevation = FloatingActionButtonDefaults.elevation(
                        6.dp
                    ),
                    modifier = Modifier.height(44.dp)
                )

                MaterialTheme(
                    shapes = MaterialTheme.shapes.copy(
                        extraSmall = RoundedCornerShape(14.dp)
                    )
                ) {
                    DropdownMenu(
                        expanded = showAddMenu,
                        onDismissRequest = {
                            showAddMenu = false
                        },
                        modifier = Modifier
                            .width(200.dp)
                            .background(Color.White)
                    ) {
                        DropdownMenuItem(
                            text = {
                                Text(
                                    "Add Cards",
                                    fontSize = 14.sp,
                                    color = Color(0xFF333333)
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    Icons.Default.Search,
                                    contentDescription = null,
                                    tint = Color(0xFF1E88E5)
                                )
                            },
                            onClick = {
                                showAddMenu = false
                                onNavigate(AppScreen.WISHLIST_ADD)
                            }
                        )

                        HorizontalDivider()

                        DropdownMenuItem(
                            text = {
                                Text(
                                    "Create Folder",
                                    fontSize = 14.sp,
                                    color = Color(0xFF333333)
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    Icons.Default.CreateNewFolder,
                                    contentDescription = null,
                                    tint = Color(0xFF4CAF50)
                                )
                            },
                            onClick = {
                                showAddMenu = false
                                newFolderName = ""
                                showCreateFolderDialog = true
                            }
                        )

                        HorizontalDivider()

                        DropdownMenuItem(
                            text = {
                                Text(
                                    if (isEditMode) {
                                        "Done Editing"
                                    } else {
                                        "Edit Folders"
                                    },
                                    fontSize = 14.sp,
                                    color = Color(0xFF333333)
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    if (isEditMode) {
                                        Icons.Default.Check
                                    } else {
                                        Icons.Default.Edit
                                    },
                                    contentDescription = null,
                                    tint = Color(0xFFFF9800)
                                )
                            },
                            onClick = {
                                showAddMenu = false
                                isEditMode = !isEditMode
                            }
                        )
                    }
                }
            }
        },

        containerColor = Color.Transparent
    ) { padding ->

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {

            // -----------------------------------------
            // CREATE FOLDER
            // -----------------------------------------

            if (showCreateFolderDialog) {
                AlertDialog(
                    onDismissRequest = {
                        showCreateFolderDialog = false
                    },
                    shape = RoundedCornerShape(16.dp),
                    containerColor = Color.White,
                    title = {
                        Text(
                            "Create New Folder",
                            fontWeight = FontWeight.Bold
                        )
                    },
                    text = {
                        OutlinedTextField(
                            value = newFolderName,
                            onValueChange = {
                                newFolderName = it
                            },
                            placeholder = {
                                Text("e.g., My Favorites")
                            },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                val name = newFolderName.trim()
                                if (name.isNotBlank()) {
                                    viewModel.createWishlistFolder(name)
                                    wishlistRefreshTrigger++
                                }
                                showCreateFolderDialog = false
                                newFolderName = ""
                            },
                            enabled = newFolderName.isNotBlank()
                        ) {
                            Text("Create")
                        }
                    },
                    dismissButton = {
                        TextButton(
                            onClick = {
                                showCreateFolderDialog = false
                                newFolderName = ""
                            }
                        ) {
                            Text("Cancel")
                        }
                    }
                )
            }

            // -----------------------------------------
            // RENAME FOLDER
            // -----------------------------------------

            folderToRename?.let { oldName ->
                var renameText by remember(oldName) {
                    mutableStateOf(oldName)
                }

                AlertDialog(
                    onDismissRequest = {
                        folderToRename = null
                    },
                    shape = RoundedCornerShape(16.dp),
                    containerColor = Color.White,
                    title = {
                        Text(
                            "Rename Folder",
                            fontWeight = FontWeight.Bold
                        )
                    },
                    text = {
                        OutlinedTextField(
                            value = renameText,
                            onValueChange = {
                                renameText = it
                            },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                val newName = renameText.trim()

                                if (
                                    newName.isNotBlank() &&
                                    newName != oldName
                                ) {
                                    viewModel.renameWishlistFolder(
                                        oldName,
                                        newName
                                    )
                                    wishlistRefreshTrigger++
                                }
                                folderToRename = null
                            },
                            enabled = renameText.isNotBlank()
                        ) {
                            Text("Save")
                        }
                    },
                    dismissButton = {
                        TextButton(
                            onClick = {
                                folderToRename = null
                            }
                        ) {
                            Text("Cancel")
                        }
                    }
                )
            }

            // -----------------------------------------
            // DELETE FOLDER
            // -----------------------------------------

            if (
                showDeleteFolderDialog &&
                folderToDelete != null
            ) {
                AlertDialog(
                    onDismissRequest = {
                        showDeleteFolderDialog = false
                        folderToDelete = null
                    },
                    title = {
                        Text("Delete Folder")
                    },
                    text = {
                        Text(
                            "Are you sure you want to delete '${folderToDelete}'?"
                        )
                    },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                val target = folderToDelete

                                if (
                                    target != null &&
                                    target != "All"
                                ) {
                                    viewModel.deleteWishlistFolder(
                                        target
                                    )

                                    if (
                                        viewModel.currentWishlistFolder ==
                                        "Default"
                                    ) {
                                        viewModel.currentWishlistFolder =
                                            "All"
                                    }

                                    wishlistRefreshTrigger++
                                }

                                showDeleteFolderDialog = false
                                folderToDelete = null
                            }
                        ) {
                            Text(
                                "Delete",
                                color = Color.Red
                            )
                        }
                    },
                    dismissButton = {
                        TextButton(
                            onClick = {
                                showDeleteFolderDialog = false
                                folderToDelete = null
                            }
                        ) {
                            Text("Cancel")
                        }
                    }
                )
            }

            // =================================================
            // FOLDER BAR - DRAG TO REORDER ONLY IN EDIT MODE
            // =================================================

            if (displayFolders.isNotEmpty()) {
                val folderListState = rememberLazyListState()

                // The first LazyRow item is the pinned "All" folder.
                // The other items therefore start at index 1.
                val reorderState = rememberReorderableLazyListState(
                    lazyListState = folderListState
                ) { from, to ->
                    val fromCustomIndex = from.index - 1
                    val toCustomIndex = to.index - 1

                    // Do not reorder outside Edit Folders, and keep "All" fixed.
                    if (
                        isEditMode &&
                        fromCustomIndex in orderedFolders.indices &&
                        toCustomIndex in orderedFolders.indices
                    ) {
                        orderedFolders = orderedFolders.toMutableList().apply {
                            add(toCustomIndex, removeAt(fromCustomIndex))
                        }
                    }
                }

                // Save only after the finger is released. Reuse the existing
                // ViewModel method, which already persists folder ordering.
                val persistDragOrder: (String) -> Unit = { movedName ->
                    val original = viewModel.wishlistFolders.toList()
                    val originalCustom = original.filter {
                        it != "All" && it != "Default"
                    }
                    val targetCustomIndex = orderedFolders.indexOf(movedName)
                    val oldCustomIndex = originalCustom.indexOf(movedName)

                    if (
                        oldCustomIndex >= 0 &&
                        targetCustomIndex in originalCustom.indices &&
                        oldCustomIndex != targetCustomIndex
                    ) {
                        val from = original.indexOf(movedName)
                        val to = original.indexOf(
                            originalCustom[targetCustomIndex]
                        )
                        if (from >= 0 && to >= 0) {
                            viewModel.reorderWishlistFolders(from, to)
                            wishlistRefreshTrigger++
                        }
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.White)
                        .padding(vertical = 6.dp)
                ) {
                    AnimatedVisibility(visible = isEditMode) {
                        Text(
                            "Edit Folders: Hold & drag to reorder • ✎ rename • ✕ delete",
                            modifier = Modifier.padding(
                                horizontal = 12.dp,
                                vertical = 2.dp
                            ),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFFD97706)
                        )
                    }

                    // Swipe to scroll in both modes. Long-press drag is enabled
                    // only after the user selects Menu > Edit Folders.
                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        state = folderListState,
                        contentPadding = PaddingValues(horizontal = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // "All" is always first and cannot be dragged or deleted.
                        if (hasFolderOrCard) {
                            item(key = "__wishlist_pinned_all__") {
                                WishlistFolderPill(
                                    folderName = "All",
                                    cardCount = folderCardCounts["All"] ?: 0,
                                    isSelected = currentFolder == "All",
                                    isEditMode = isEditMode,
                                    isDragging = false,
                                    isAllFolder = true,
                                    onClick = {
                                        viewModel.currentWishlistFolder = "All"
                                        locallyRemovedCards = emptySet()
                                        previewIndex = null
                                    },
                                    onRename = {},
                                    onDelete = {}
                                )
                            }
                        }

                        items(
                            items = orderedFolders,
                            key = { it }
                        ) { folderName ->
                            ReorderableItem(
                                state = reorderState,
                                key = folderName
                            ) { isDragging ->
                                WishlistFolderPill(
                                    // In normal mode the drag gesture is not attached,
                                    // so holding a folder cannot change its position.
                                    modifier = if (isEditMode) {
                                        Modifier.longPressDraggableHandle(
                                            onDragStarted = {
                                                draggingFolder = folderName
                                            },
                                            onDragStopped = {
                                                if (isEditMode) {
                                                    persistDragOrder(folderName)
                                                } else {
                                                    orderedFolders = customFolders
                                                }
                                                draggingFolder = null
                                            }
                                        )
                                    } else {
                                        Modifier
                                    },
                                    folderName = folderName,
                                    cardCount = folderCardCounts[folderName] ?: 0,
                                    isSelected = currentFolder == folderName,
                                    isEditMode = isEditMode,
                                    isDragging = isDragging,
                                    isAllFolder = false,
                                    onClick = {
                                        viewModel.currentWishlistFolder = folderName
                                        locallyRemovedCards = emptySet()
                                        previewIndex = null
                                    },
                                    onRename = {
                                        folderToRename = folderName
                                    },
                                    onDelete = {
                                        folderToDelete = folderName
                                        showDeleteFolderDialog = true
                                    }
                                )
                            }
                        }
                    }
                }
            }

            // ------------------------------------------------
            // EMPTY WISHLIST
            // ------------------------------------------------

            if (
                !hasFolderOrCard &&
                wishlistedCards.isEmpty()
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(90.dp)
                            .background(
                                Color.White.copy(alpha = 0.1f),
                                CircleShape
                            )
                            .border(
                                1.dp,
                                Color.White.copy(alpha = 0.3f),
                                CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.FavoriteBorder,
                            contentDescription = null,
                            modifier = Modifier.size(44.dp),
                            tint = Color.White
                        )
                    }

                    Spacer(Modifier.height(12.dp))

                    Text(
                        "Your wishlist is empty",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(Modifier.height(6.dp))

                    Text(
                        "Tap 'Menu' to add cards or folders",
                        color = Color.LightGray,
                        fontSize = 13.sp
                    )
                }

            } else {

                // -----------------------------------------
                // SEARCH AND FILTER
                // -----------------------------------------

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.White)
                        .padding(
                            horizontal = 8.dp,
                            vertical = 6.dp
                        )
                ) {
                    BasicTextField(
                        value = searchQuery,
                        onValueChange = {
                            searchQuery = it
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(38.dp)
                            .background(
                                Color(0xFFF8F9FA),
                                RoundedCornerShape(8.dp)
                            )
                            .border(
                                1.dp,
                                Color(0xFFE2E8F0),
                                RoundedCornerShape(8.dp)
                            ),
                        singleLine = true,
                        textStyle = TextStyle(
                            fontSize = 13.sp,
                            color = Color.Black
                        ),
                        decorationBox = { innerTextField ->
                            Row(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 10.dp),
                                verticalAlignment =
                                    Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.Search,
                                    contentDescription = "Search",
                                    tint = Color.Gray,
                                    modifier = Modifier.size(18.dp)
                                )

                                Spacer(Modifier.width(6.dp))

                                Box(Modifier.weight(1f)) {
                                    if (searchQuery.isEmpty()) {
                                        Text(
                                            "Search your wishlist...",
                                            color = Color.Gray,
                                            fontSize = 13.sp
                                        )
                                    }
                                    innerTextField()
                                }

                                if (searchQuery.isNotEmpty()) {
                                    IconButton(
                                        onClick = {
                                            searchQuery = ""
                                        },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.Close,
                                            contentDescription =
                                                "Clear Search",
                                            tint = Color.Gray,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }

                                IconButton(
                                    onClick = {
                                        isFilterExpanded =
                                            !isFilterExpanded
                                    },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Tune,
                                        contentDescription = "Filter",
                                        tint =
                                            if (isFilterExpanded) {
                                                ConanTheme.BowTieRed
                                            } else {
                                                Color.Gray
                                            },
                                        modifier = Modifier.size(18.dp)
                                    )
                                }

                                IconButton(
                                    onClick = {
                                        viewMode =
                                            if (viewMode == 0) 1 else 0
                                    },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        imageVector =
                                            if (viewMode == 0) {
                                                Icons.Default.GridView
                                            } else {
                                                Icons.AutoMirrored.Filled.List
                                            },
                                        contentDescription = "View Mode",
                                        tint = Color.Gray,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    )

                    if (isAnyFilterActive) {
                        Text(
                            "Found: ${displayCards.size} cards",
                            color = Color.Gray,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(
                                top = 4.dp,
                                start = 4.dp
                            )
                        )
                    }

                    AnimatedVisibility(
                        visible = isFilterExpanded
                    ) {
                        Column(
                            modifier = Modifier.padding(top = 6.dp)
                        ) {
                            FilterTable(
                                onColorChange = {
                                    selectedColors = it
                                },
                                onTypeChange = {
                                    selectedTypes = it
                                },
                                onRarityChange = {
                                    selectedRarities = it
                                },
                                onLevelChange = {
                                    selectedLevels = it
                                },
                                onApChange = {
                                    selectedAps = it
                                },
                                onLpChange = {
                                    selectedLps = it
                                },
                                onPackChange = {
                                    selectedPack = it
                                },
                                onCategoryChange = {
                                    selectedCardCategory = it
                                },
                                currentColors = selectedColors,
                                currentTypes = selectedTypes,
                                currentRarities = selectedRarities,
                                currentAps = selectedAps,
                                currentLevels = selectedLevels,
                                currentLps = selectedLps,
                                currentPack = selectedPack ?: "All",
                                currentCategory = selectedCardCategory,
                                availablePacks =
                                    listOf("All") + availablePacks,
                                availableCategories =
                                    listOf("All") + availableCategories
                            )
                        }
                    }
                }

                // -----------------------------------------
                // CARD LIST OR GRID
                // -----------------------------------------

                Box(
                    modifier = Modifier.weight(1f)
                ) {
                    if (displayCards.isEmpty()) {

                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "No matching cards.",
                                color = Color.White,
                                fontSize = 14.sp
                            )
                        }

                    } else if (viewMode == 0) {

                        LazyColumn(
                            contentPadding = PaddingValues(8.dp),
                            verticalArrangement =
                                Arrangement.spacedBy(6.dp)
                        ) {
                            itemsIndexed(
                                items = displayCards,
                                key = { _, card -> card.cardNum }
                            ) { index, card ->

                                WishlistScreenCardListItem(
                                    card = card,
                                    imageModel =
                                        viewModel.getCardImageModel(card),
                                    isWishlisted = true,
                                    onWishlistClick = {
                                        removeWishlistCard(card.cardNum)
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
                            horizontalArrangement =
                                Arrangement.spacedBy(6.dp),
                            verticalArrangement =
                                Arrangement.spacedBy(6.dp)
                        ) {
                            gridItemsIndexed(
                                items = displayCards,
                                key = { _, card -> card.cardNum }
                            ) { index, card ->

                                WishlistScreenCardGridItem(
                                    card = card,
                                    imageModel =
                                        viewModel.getCardImageModel(card),
                                    isWishlisted = true,
                                    onWishlistClick = {
                                        removeWishlistCard(card.cardNum)
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

    // =================================================
    // CARD PREVIEW DIALOG
    // =================================================

    previewIndex?.let { initialIndex ->

        val validIndex = initialIndex.coerceIn(
            0,
            (displayCards.size - 1).coerceAtLeast(0)
        )

        val pagerState = rememberPagerState(
            initialPage = validIndex,
            pageCount = { displayCards.size }
        )

        Dialog(
            onDismissRequest = {
                previewIndex = null
                locallyRemovedCards = emptySet()
                wishlistRefreshTrigger++
            },
            properties = DialogProperties(
                usePlatformDefaultWidth = false
            )
        ) {

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Color.Black.copy(alpha = 0.65f)
                    )
                    .clickable(
                        interactionSource = remember {
                            MutableInteractionSource()
                        },
                        indication = null
                    ) {
                        previewIndex = null
                        locallyRemovedCards = emptySet()
                        wishlistRefreshTrigger++
                    },
                contentAlignment = Alignment.Center
            ) {

                val currentCard =
                    displayCards.getOrNull(pagerState.currentPage)

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                        .navigationBarsPadding(),
                    horizontalAlignment =
                        Alignment.CenterHorizontally,
                    verticalArrangement =
                        Arrangement.SpaceBetween
                ) {

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(
                                top = 12.dp,
                                start = 20.dp,
                                end = 20.dp
                            ),
                        horizontalArrangement =
                            Arrangement.SpaceBetween,
                        verticalAlignment =
                            Alignment.CenterVertically
                    ) {

                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(
                                    Color.Black.copy(alpha = 0.4f)
                                )
                                .border(
                                    1.dp,
                                    Color.White.copy(alpha = 0.25f),
                                    CircleShape
                                )
                                .padding(
                                    horizontal = 14.dp,
                                    vertical = 6.dp
                                )
                        ) {
                            Row(
                                verticalAlignment =
                                    Alignment.CenterVertically
                            ) {
                                Text(
                                    "${pagerState.currentPage + 1}",
                                    color = Color.White,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.ExtraBold
                                )
                                Text(
                                    " / ${displayCards.size}",
                                    color = Color.White.copy(
                                        alpha = 0.7f
                                    ),
                                    fontSize = 13.sp
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
                                .background(
                                    Color.Black.copy(alpha = 0.4f)
                                )
                                .border(
                                    1.dp,
                                    Color.White.copy(alpha = 0.25f),
                                    CircleShape
                                )
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Close Preview",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .padding(vertical = 10.dp),
                        contentPadding = PaddingValues(
                            horizontal = 42.dp
                        ),
                        pageSpacing = 12.dp
                    ) { page ->

                        val card = displayCards.getOrNull(page)

                        val pageOffset =
                            (pagerState.currentPage - page) +
                                    pagerState.currentPageOffsetFraction

                        val absOffset = abs(pageOffset)

                        val scale =
                            0.88f +
                                    (1f - 0.88f) *
                                    (1f - absOffset.coerceIn(0f, 1f))

                        val alpha =
                            0.45f +
                                    (1f - 0.45f) *
                                    (1f - absOffset.coerceIn(0f, 1f))

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
                                        elevation =
                                            if (absOffset < 0.5f) {
                                                16.dp
                                            } else {
                                                0.dp
                                            },
                                        shape =
                                            RoundedCornerShape(16.dp),
                                        spotColor = Color.Black
                                    )
                                    .clip(
                                        RoundedCornerShape(16.dp)
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                AsyncImage(
                                    model =
                                        viewModel.getCardImageModel(card),
                                    contentDescription = card.name,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }
                    }

                    currentCard?.let { card ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp)
                                .clip(
                                    RoundedCornerShape(16.dp)
                                )
                                .background(
                                    Color.Black.copy(alpha = 0.5f)
                                )
                                .border(
                                    1.dp,
                                    Color.White.copy(alpha = 0.2f),
                                    RoundedCornerShape(16.dp)
                                )
                                .padding(
                                    horizontal = 14.dp,
                                    vertical = 10.dp
                                ),
                            horizontalAlignment =
                                Alignment.CenterHorizontally
                        ) {

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement =
                                    Arrangement.SpaceBetween,
                                verticalAlignment =
                                    Alignment.CenterVertically
                            ) {
                                Text(
                                    card.name,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    card.cardNum,
                                    color = Color(0xFFFFD700),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                            }

                            Spacer(Modifier.height(6.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment =
                                    Alignment.CenterVertically,
                                horizontalArrangement =
                                    Arrangement.spacedBy(6.dp)
                            ) {
                                val typeMap = mapOf(
                                    "パートナー" to "Partner",
                                    "キャラ" to "Character",
                                    "イベント" to "Event",
                                    "事件" to "Case"
                                )

                                val rarity = card.rarity.trim()
                                if (
                                    rarity.isNotBlank() &&
                                    rarity != "null" &&
                                    rarity != "-"
                                ) {
                                    PreviewDetailBadge(
                                        rarity,
                                        Color(0xFF3B82F6)
                                    )
                                }

                                val rawType = card.type.trim()
                                val displayType =
                                    typeMap[rawType] ?: rawType

                                if (
                                    displayType.isNotBlank() &&
                                    displayType != "null" &&
                                    displayType != "-"
                                ) {
                                    PreviewDetailBadge(
                                        displayType,
                                        Color(0xFF10B981)
                                    )
                                }

                                val cost = card.cost.trim()
                                if (
                                    cost.isNotBlank() &&
                                    cost != "null" &&
                                    cost != "-"
                                ) {
                                    PreviewDetailBadge(
                                        "Cost: $cost",
                                        Color(0xFFF59E0B)
                                    )
                                }

                                val ap = card.ap.trim()
                                if (
                                    ap.isNotBlank() &&
                                    ap != "null" &&
                                    ap != "-"
                                ) {
                                    PreviewDetailBadge(
                                        "AP: $ap",
                                        Color(0xFFEC4899)
                                    )
                                }

                                val lp = card.lp.trim()
                                if (
                                    lp.isNotBlank() &&
                                    lp != "null" &&
                                    lp != "-"
                                ) {
                                    PreviewDetailBadge(
                                        "LP: $lp",
                                        Color(0xFF8B5CF6)
                                    )
                                }
                            }

                            val effectText = card.text.orEmpty()

                            if (effectText.isNotBlank()) {
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    effectText,
                                    color = Color.White.copy(
                                        alpha = 0.88f
                                    ),
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

// =====================================================
// WISHLIST FOLDER PILL (REORDERABLE DRAG HANDLE)
// =====================================================

@Composable
private fun WishlistFolderPill(
    folderName: String,
    cardCount: Int,
    isSelected: Boolean,
    isEditMode: Boolean,
    isDragging: Boolean,
    isAllFolder: Boolean,
    onClick: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scaleState by animateFloatAsState(
        targetValue = when {
            isDragging -> 1.06f
            isSelected -> 1.02f
            else -> 1f
        },
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy
        ),
        label = "pillScale"
    )

    val activeGradient = Brush.horizontalGradient(
        colors = listOf(
            ConanTheme.BlueSuit,
            Color(0xFF2563EB)
        )
    )

    val textColor by animateColorAsState(
        targetValue = if (isSelected) Color.White
        else Color(0xFF475467),
        label = "tabText"
    )

    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = scaleState
                scaleY = scaleState
            }
            .shadow(
                elevation = when {
                    isDragging -> 8.dp
                    isSelected -> 2.dp
                    else -> 0.dp
                },
                shape = RoundedCornerShape(16.dp),
                spotColor = ConanTheme.BlueSuit.copy(alpha = 0.35f)
            )
            .clip(RoundedCornerShape(16.dp))
            .then(
                if (isSelected) Modifier.background(activeGradient)
                else Modifier.background(Color(0xFFF1F5F9))
            )
            .border(
                width = if (isEditMode && !isAllFolder) 1.5.dp else 1.dp,
                color = when {
                    isDragging -> ConanTheme.BlueSuit
                    isEditMode && !isAllFolder -> Color(0xFFF59E0B)
                    isSelected -> Color.Transparent
                    else -> Color(0xFFE2E8F0)
                },
                shape = RoundedCornerShape(16.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = if (isAllFolder) {
                    Icons.Default.FolderSpecial
                } else {
                    Icons.Default.Folder
                },
                contentDescription = null,
                tint = if (isSelected) {
                    Color.White.copy(alpha = 0.9f)
                } else {
                    Color(0xFF64748B)
                },
                modifier = Modifier.size(14.dp)
            )

            Spacer(Modifier.width(4.dp))

            Text(
                text = folderName,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                color = textColor,
                fontSize = 12.sp,
                letterSpacing = 0.1.sp,
                maxLines = 1
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
                    "$cardCount",
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isSelected) Color.White else Color(0xFF475467)
                )
            }

            // Rename and delete remain available via Edit Folders, without
            // blocking the long-press drag gesture with a context menu.
            if (isEditMode && !isAllFolder) {
                Spacer(Modifier.width(4.dp))

                Box(
                    modifier = Modifier
                        .size(16.dp)
                        .clip(CircleShape)
                        .clickable(onClick = onRename),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Edit,
                        contentDescription = "Rename Folder",
                        tint = if (isSelected) Color.White
                        else Color(0xFF64748B),
                        modifier = Modifier.size(11.dp)
                    )
                }

                Spacer(Modifier.width(2.dp))

                Box(
                    modifier = Modifier
                        .size(16.dp)
                        .clip(CircleShape)
                        .background(
                            if (isSelected) Color.White.copy(alpha = 0.3f)
                            else Color(0xFFFEE2E2)
                        )
                        .clickable(onClick = onDelete),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Delete Folder",
                        tint = if (isSelected) Color.White
                        else Color(0xFFEF4444),
                        modifier = Modifier.size(10.dp)
                    )
                }
            }
        }
    }
}

// =====================================================
// ANIMATED WISHLIST BUTTON
// =====================================================

@Composable
fun AnimatedWishlistButton(
    isWishlisted: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    activeColor: Color = ConanTheme.BowTieRed,
    inactiveColor: Color = Color.Gray,
    iconSize: Float = 22f
) {
    var isPressed by remember {
        mutableStateOf(false)
    }

    val scale by animateFloatAsState(
        targetValue = if (isPressed) 1.3f else 1f,
        animationSpec = spring(
            dampingRatio =
                Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        finishedListener = {
            isPressed = false
        },
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
                (scaleIn() + fadeIn()).togetherWith(
                    scaleOut() + fadeOut()
                )
            },
            label = "HeartContentAnim"
        ) { wishlisted ->
            Icon(
                imageVector =
                    if (wishlisted) {
                        Icons.Default.Favorite
                    } else {
                        Icons.Default.FavoriteBorder
                    },
                contentDescription = "Wishlist",
                tint =
                    if (wishlisted) activeColor
                    else inactiveColor,
                modifier = Modifier.size(iconSize.dp)
            )
        }
    }
}

// =====================================================
// WISHLIST LIST ITEM
// =====================================================

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
            .clickable {
                onClick()
            },
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.White
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 1.5.dp
        )
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
                    .size(
                        width = 54.dp,
                        height = 76.dp
                    )
                    .clip(
                        RoundedCornerShape(4.dp)
                    ),
                contentScale = ContentScale.Crop
            )

            Spacer(Modifier.width(10.dp))

            Column(Modifier.weight(1f)) {
                Text(
                    card.name,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(Modifier.height(2.dp))

                Text(
                    "${card.cardNum} • ${card.rarity}",
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

// =====================================================
// WISHLIST GRID ITEM
// =====================================================

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
            .clickable {
                onClick()
            },
        shape = RoundedCornerShape(6.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.White
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 1.5.dp
        )
    ) {
        Box(
            modifier = Modifier.fillMaxSize()
        ) {
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
                    .background(
                        Color.Black.copy(alpha = 0.45f),
                        CircleShape
                    ),
                activeColor = ConanTheme.BowTieRed,
                inactiveColor = Color.White,
                iconSize = 14f
            )
        }
    }
}

// =====================================================
// CARD PREVIEW BADGE
// =====================================================

@Composable
fun PreviewDetailBadge(
    text: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(
                RoundedCornerShape(6.dp)
            )
            .background(
                color.copy(alpha = 0.25f)
            )
            .border(
                1.dp,
                color.copy(alpha = 0.5f),
                RoundedCornerShape(6.dp)
            )
            .padding(
                horizontal = 8.dp,
                vertical = 3.dp
            )
    ) {
        Text(
            text = text,
            color = Color.White,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

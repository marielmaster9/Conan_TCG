package com.example.conantcg.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.conantcg.R
import com.example.conantcg.ui.components.*
import com.example.conantcg.ui.viewmodel.CardViewModel
import com.example.conantcg.utils.ConanTheme
import kotlinx.coroutines.delay


enum class AppScreen {
    HOME,
    CATALOG,
    COLLECTION,
    DECK_LIST,
    DECK_BUILDER,
    WISHLIST,
    WISHLIST_ADD,
    SCANNER,
    BACKUP_RESTORE
}

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun MainAppScreen(vm: CardViewModel) {
    var screen by rememberSaveable { mutableStateOf(AppScreen.HOME) }

    // Handle Back Press
    BackHandler(enabled = screen != AppScreen.HOME) {
        screen = if (screen == AppScreen.DECK_BUILDER) AppScreen.DECK_LIST else AppScreen.HOME
    }

    Box(Modifier.fillMaxSize()) {
        // Background Image
        Image(
            painter = painterResource(R.drawable.bg),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )

        if (vm.isLoading && vm.cardList.isEmpty()) {
            LoadingScreen(vm.statusText, vm.progress)
        } else {
            AnimatedContent(
                targetState = screen,
                label = "ScreenTransition",
                transitionSpec = {
                    val spec = tween<IntOffset>(durationMillis = 500, easing = FastOutSlowInEasing)
                    val isNavigatingForward = targetState.ordinal > initialState.ordinal

                    if (isNavigatingForward) {
                        (slideInHorizontally(animationSpec = spec) { fullWidth -> fullWidth } + fadeIn(animationSpec = tween(500)))
                            .togetherWith(slideOutHorizontally(animationSpec = spec) { fullWidth -> -fullWidth / 3 } + fadeOut(animationSpec = tween(500)))
                    } else {
                        (slideInHorizontally(animationSpec = spec) { fullWidth -> -fullWidth } + fadeIn(animationSpec = tween(500)))
                            .togetherWith(slideOutHorizontally(animationSpec = spec) { fullWidth -> fullWidth } + fadeOut(animationSpec = tween(500)))
                    }
                }
            ) { s ->
                when (s) {
                    AppScreen.HOME -> HomeScreen(
                        total = vm.cardList.size,
                        owned = vm.collectedCounts.size,
                        isUpdating = vm.isUpdatingData,
                        onUpdateClick = { vm.refreshData(force = true) },
                        nav = { screen = it }
                    )
                    AppScreen.CATALOG -> CardBrowserScreen(vm, false) { screen = AppScreen.HOME }
                    AppScreen.COLLECTION -> CardBrowserScreen(vm, true) { screen = AppScreen.HOME }
                    AppScreen.DECK_LIST -> DeckListScreen(vm, { screen = it }, { screen = AppScreen.HOME })
                    AppScreen.DECK_BUILDER -> DeckBuilderScreen(vm) { screen = AppScreen.DECK_LIST }
                    AppScreen.WISHLIST -> {
                        WishlistScreen(
                            viewModel = vm,
                            onNavigate = { screen = it },
                            onBack = { screen = AppScreen.HOME }
                        )
                    }
                    AppScreen.WISHLIST_ADD -> WishlistAddScreen(
                        viewModel = vm,
                        onBack = { screen = AppScreen.WISHLIST }
                    )
                    AppScreen.SCANNER -> CardScannerScreen(
                        viewModel = vm,
                        onBackClick = { screen = AppScreen.HOME }
                    )
                    AppScreen.BACKUP_RESTORE -> BackupRestoreScreen(
                        viewModel = vm,
                        onBackClick = {
                            screen = AppScreen.HOME
                        }
                    )
                    AppScreen.BACKUP_RESTORE -> BackupRestoreScreen(
                        viewModel = vm,
                        onBackClick = {
                            screen = AppScreen.HOME
                        }
                    )

                }
            }
        }

        AnimatedVisibility(
            visible = vm.isLoading && vm.cardList.isNotEmpty(),
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF2D2D3A).copy(alpha = 0.95f)),
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 24.dp)
                    .navigationBarsPadding()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = vm.statusText.replace(Regex("\\s?\\d+%$"), ""),
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "${(vm.progress * 100).toInt()}%",
                            color = Color(0xFF4FC3F7),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { vm.progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = Color(0xFF4FC3F7),
                        trackColor = Color.DarkGray
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(total: Int, owned: Int, isUpdating: Boolean, onUpdateClick: () -> Unit, nav: (AppScreen) -> Unit) {
    var isVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { isVisible = true }

    Column(
        Modifier.fillMaxSize().statusBarsPadding(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {

        Spacer(Modifier.height(16.dp))

        // Banner Section
        AnimatedVisibility(
            visible = isVisible,
            enter = slideInVertically(initialOffsetY = { -100 }, animationSpec = tween(600, easing = FastOutSlowInEasing)) + fadeIn(tween(600))
        ) {
            Image(
                painter = painterResource(R.drawable.banner),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 140.dp)
            )
        }

        // Content Section
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = 24.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceEvenly
        ) {

            // -- Navigation Buttons --
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                AnimatedVisibility(
                    visible = isVisible,
                    enter = slideInVertically(initialOffsetY = { 100 }, animationSpec = tween(600, delayMillis = 100, easing = FastOutSlowInEasing)) + fadeIn(tween(600, 100))
                ) {
                    NavButton("Card Catalog", Icons.AutoMirrored.Filled.MenuBook) { nav(AppScreen.CATALOG) }
                }

                AnimatedVisibility(
                    visible = isVisible,
                    enter = slideInVertically(initialOffsetY = { 100 }, animationSpec = tween(600, delayMillis = 200, easing = FastOutSlowInEasing)) + fadeIn(tween(600, 200))
                ) {
                    NavButton("My Collection", Icons.Default.Star) { nav(AppScreen.COLLECTION) }
                }

                AnimatedVisibility(
                    visible = isVisible,
                    enter = slideInVertically(initialOffsetY = { 100 }, animationSpec = tween(600, delayMillis = 250, easing = FastOutSlowInEasing)) + fadeIn(tween(600, 250))
                ) {
                    NavButton("Card Scanner ", Icons.Default.QrCodeScanner) { nav(AppScreen.SCANNER) }
                }

                AnimatedVisibility(
                    visible = isVisible,
                    enter = slideInVertically(initialOffsetY = { 100 }, animationSpec = tween(600, delayMillis = 300, easing = FastOutSlowInEasing)) + fadeIn(tween(600, 300))
                ) {
                    NavButton("Deck Builder", Icons.Default.Build) { nav(AppScreen.DECK_LIST) }
                }

                AnimatedVisibility(
                    visible = isVisible,
                    enter = slideInVertically(initialOffsetY = { 100 }, animationSpec = tween(600, delayMillis = 400, easing = FastOutSlowInEasing)) + fadeIn(tween(600, 400))
                ) {
                    NavButton("Wishlist", Icons.Default.Favorite) { nav(AppScreen.WISHLIST) }
                }
            }


            // -- Case File Summary (Stats) --
            AnimatedVisibility(
                visible = isVisible,
                enter = slideInVertically(
                    initialOffsetY = { 50 },
                    animationSpec = tween(
                        600,
                        delayMillis = 500
                    )
                ) + fadeIn(tween(600, 500))
            ) {
                Box(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // Original Summary Card
                    Card(
                        onClick = onUpdateClick,
                        enabled = !isUpdating,
                        colors = CardDefaults.cardColors(
                            containerColor = ConanTheme.BrickBg
                        ),
                        shape = RoundedCornerShape(8.dp),
                        elevation = CardDefaults.cardElevation(4.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "CASE FILE SUMMARY",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = ConanTheme.TextSecondary,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.sp
                                )

                                if (isUpdating) {
                                    Spacer(Modifier.width(8.dp))

                                    CircularProgressIndicator(
                                        modifier = Modifier.size(14.dp),
                                        color = ConanTheme.BlueSuit,
                                        strokeWidth = 2.dp
                                    )
                                }
                            }

                            Spacer(Modifier.height(8.dp))

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(IntrinsicSize.Min),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier.weight(1f),
                                    contentAlignment = Alignment.Center
                                ) {
                                    StatNumberItem(
                                        "Total Cards",
                                        total.toString()
                                    )
                                }

                                Box(
                                    modifier = Modifier
                                        .width(1.dp)
                                        .fillMaxHeight(0.7f)
                                        .background(Color.LightGray)
                                )

                                Box(
                                    modifier = Modifier.weight(1f),
                                    contentAlignment = Alignment.Center
                                ) {
                                    StatNumberItem(
                                        "Collected",
                                        owned.toString()
                                    )
                                }
                            }
                        }
                    }

                    // Backup Icon: overlay, does not change Card size.
                    IconButton(
                        onClick = {
                            nav(AppScreen.BACKUP_RESTORE)
                        },
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(
                                top = 3.dp,
                                end = 8.dp
                            )
                            .size(34.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CloudUpload,
                            contentDescription = "Backup & Restore",
                            tint = ConanTheme.BlueSuit,
                            modifier = Modifier.size(19.dp)
                        )
                    }
                }
            }


            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
fun NavButton(text: String, icon: ImageVector, onClick: () -> Unit) {
    var isPressed by remember { mutableStateOf(false) }

    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.94f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "ButtonScale"
    )

    var shineOffset by remember { mutableStateOf(-1000f) }
    LaunchedEffect(isPressed) {
        if (isPressed) {
            shineOffset = -500f
            while (isPressed) {
                animate(
                    initialValue = -500f,
                    targetValue = 2000f,
                    animationSpec = tween(durationMillis = 600, easing = LinearEasing)
                ) { value, _ -> shineOffset = value }
                delay(800)
            }
        } else {
            shineOffset = -1000f
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(60.dp)
            .scale(scale)
            .clip(RoundedCornerShape(12.dp))
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        isPressed = true
                        tryAwaitRelease()
                        isPressed = false
                        onClick()
                    }
                )
            }
    ) {
        Row(Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .width(56.dp)
                    .fillMaxHeight()
                    .background(ConanTheme.BowTieRed),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(26.dp))
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .background(ConanTheme.BlueSuit)
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = text,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        fontSize = 16.sp
                    )
                    Icon(Icons.Default.ChevronRight, contentDescription = null, tint = Color.White)
                }
            }
        }

        if (isPressed) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        brush = Brush.linearGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color.White.copy(alpha = 0.3f),
                                Color.Transparent
                            ),
                            start = Offset(shineOffset, 0f),
                            end = Offset(shineOffset + 300f, 300f)
                        )
                    )
            )
        }
    }
}

@Composable
fun StatNumberItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = ConanTheme.BlueSuit
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = ConanTheme.TextSecondary
        )
    }
}
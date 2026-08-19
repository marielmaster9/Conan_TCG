package com.example.conantcg.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import coil.compose.SubcomposeAsyncImage
import com.example.conantcg.R // นำเข้ารูปภาพ R.drawable
import com.example.conantcg.data.model.Deck
import com.example.conantcg.data.model.WebCard
import com.example.conantcg.ui.viewmodel.CardViewModel
import com.example.conantcg.utils.ConanTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeckListScreen(viewModel: CardViewModel, onNavigate: (AppScreen) -> Unit, onBack: () -> Unit) {
    // --- State Variables ---
    var showMenu by remember { mutableStateOf(false) }

    var showDeckSelectorForTest by remember { mutableStateOf(false) }
    var showHandTestDialog by remember { mutableStateOf(false) }
    var handTestCards by remember { mutableStateOf<List<WebCard>>(emptyList()) }
    var selectedDeckForTest by remember { mutableStateOf<Deck?>(null) }

    // Import Dialog State
    var showImportDialog by remember { mutableStateOf(false) }
    var importCode by remember { mutableStateOf("") }
    var importStatus by remember { mutableStateOf("") }
    var isImporting by remember { mutableStateOf(false) }

    // Hand Test Hold State
    var heldCardsIndices by remember { mutableStateOf(setOf<Int>()) }

    // Map ของการ์ดเพื่อให้ค้นหา Partner ได้เร็วขึ้น
    val cardMap = remember(viewModel.cardList) {
        viewModel.cardList.associateBy { it.cardNum }
    }

    fun createAndGoToEditor() {
        val defaultName = "New Deck ${viewModel.allDecks.size + 1}"
        viewModel.createNewDeck(defaultName)

        val newDeck = viewModel.allDecks.firstOrNull()
        if (newDeck != null) {
            viewModel.setActiveDeck(newDeck.id)
            onNavigate(AppScreen.DECK_BUILDER)
        }
    }

    Scaffold(
        topBar = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(elevation = 8.dp)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(ConanTheme.BlueSuit, Color(0xFF151C5E))
                        )
                    )
                    .statusBarsPadding()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 8.dp)
                ) {
                    // ✅ แก้ไขปุ่ม Back ให้ใช้ Image และแสดงรูป popup_arrow_left.png
                    IconButton(onClick = onBack) {
                        Image(
                            painter = painterResource(id = R.drawable.arrowleft),
                            contentDescription = "Back",
                            modifier = Modifier.size(30.dp) // ปรับขนาดให้พอดี
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("My Deck", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp, letterSpacing = 1.sp)
                        Text("${viewModel.allDecks.size} Decks Saved", color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp)
                    }
                    Spacer(Modifier.weight(1f))
                    Spacer(Modifier.width(48.dp))
                }
            }
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showMenu = true },
                containerColor = ConanTheme.BowTieRed,
                contentColor = Color.White,
                icon = { Icon(Icons.Default.Add, "Menu") },
                text = { Text("Menu", fontWeight = FontWeight.Bold) },
                shape = RoundedCornerShape(16.dp),
                elevation = FloatingActionButtonDefaults.elevation(8.dp)
            )
        },
        containerColor = Color.Transparent // เปลี่ยนเป็น Transparent เพื่อให้เห็นรูปภาพพื้นหลัง
    ) { padding ->
        Box(Modifier.fillMaxSize()) {

            // --- 👇 ใส่รูปภาพ Background (bg1.jpg) ลงในหน้า My Deck 👇 ---
            // Image(
            //     painter = painterResource(id = R.drawable.bg),
            //   contentDescription = "Background",
            //    contentScale = ContentScale.Crop,
            //    modifier = Modifier.fillMaxSize()
            //)
            // ฟิลเตอร์สีดำจางๆ เพื่อไม่ให้ลายกำแพงอิฐกลืนกับตัวการ์ด
            //    Box(
            //    modifier = Modifier
            //      .fillMaxSize()
            // .background(Color.Black.copy(alpha = 0.4f))
            //   )
            // --- 👆 จบส่วน Background 👆 ---

            if (viewModel.allDecks.isEmpty()) {
                Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            modifier = Modifier.size(100.dp).background(Color.White.copy(0.1f), CircleShape).border(1.dp, Color.White.copy(0.3f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Style, null, modifier = Modifier.size(50.dp), tint = Color.White.copy(0.8f))
                        }
                        Spacer(Modifier.height(16.dp))
                        // ปรับสีตัวหนังสือให้สว่างขึ้นเพราะพื้นหลังมืดลง
                        Text("No decks yet", fontWeight = FontWeight.Bold, fontSize = 20.sp, color = Color.White)
                        Spacer(Modifier.height(8.dp))
                        Text("Tap 'Menu' to create your first deck", color = Color.LightGray, fontSize = 14.sp)
                    }
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(top = 16.dp, bottom = 100.dp, start = 16.dp, end = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.padding(padding).fillMaxSize()
                ) {
                    items(viewModel.allDecks) { deck ->
                        val partnerCard = deck.partner?.let { cardMap[it] }
                        AnimateInEffect {
                            DeckListItem(
                                deck = deck,
                                partnerCard = partnerCard,
                                viewModel = viewModel,
                                onEdit = { viewModel.setActiveDeck(deck.id); onNavigate(AppScreen.DECK_BUILDER) },
                                onDelete = { viewModel.deleteDeck(deck.id) }
                            )
                        }
                    }
                }
            }
        }
    }

    // --- Dialogs ---
    if (showMenu) {
        Dialog(onDismissRequest = { showMenu = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Card(modifier = Modifier.width(300.dp), shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = Color.White), elevation = CardDefaults.cardElevation(8.dp)) {
                Column {
                    Box(modifier = Modifier.fillMaxWidth().background(Brush.horizontalGradient(listOf(ConanTheme.BlueSuit, Color(0xFF151C5E)))).padding(20.dp), contentAlignment = Alignment.Center) { Text("DECK MENU", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp, letterSpacing = 2.sp) }
                    Column(Modifier.padding(vertical = 8.dp)) {
                        MenuItem(icon = Icons.Default.AddCircleOutline, text = "Create New Deck") { showMenu = false; createAndGoToEditor() }
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 24.dp), color = Color.LightGray.copy(0.3f))
                        MenuItem(icon = Icons.Default.FileDownload, text = "Import Deck") { showMenu = false; importCode = ""; importStatus = ""; showImportDialog = true }
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 24.dp), color = Color.LightGray.copy(0.3f))
                        MenuItem(icon = Icons.Default.Style, text = "Hand Test (5 Cards)") { if (viewModel.allDecks.isNotEmpty()) { showMenu = false; showDeckSelectorForTest = true } else { showMenu = false } }
                    }
                    Box(modifier = Modifier.fillMaxWidth().background(Color(0xFFF5F5F5)).clickable { showMenu = false }.padding(vertical = 16.dp), contentAlignment = Alignment.Center) { Text("Cancel", color = Color.Gray, fontWeight = FontWeight.Bold) }
                }
            }
        }
    }

    if (showImportDialog) {
        Dialog(onDismissRequest = { if(!isImporting) showImportDialog = false }) {
            Card(modifier = Modifier.width(320.dp), shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = Color.White), elevation = CardDefaults.cardElevation(8.dp)) {
                Column {
                    Box(modifier = Modifier.fillMaxWidth().background(Brush.horizontalGradient(listOf(ConanTheme.BlueSuit, Color(0xFF151C5E)))).padding(20.dp), contentAlignment = Alignment.Center) { Text("IMPORT DECK", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp, letterSpacing = 2.sp) }
                    Column(Modifier.padding(24.dp)) {
                        Text("Enter Deck Code or URL", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.DarkGray)
                        Spacer(Modifier.height(12.dp))
                        OutlinedTextField(value = importCode, onValueChange = { importCode = it }, placeholder = { Text("Paste code here...", color = Color.LightGray) }, singleLine = true, modifier = Modifier.fillMaxWidth(), enabled = !isImporting, shape = RoundedCornerShape(12.dp), colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = ConanTheme.BlueSuit, unfocusedBorderColor = Color.LightGray))
                        if (importStatus.isNotEmpty()) { Spacer(Modifier.height(8.dp)); Text(importStatus, color = if(importStatus.startsWith("Import Error") || importStatus.startsWith("Invalid") || importStatus.startsWith("Could not") || importStatus.startsWith("No deck")) Color.Red else Color(0xFF4CAF50), fontSize = 12.sp) }
                        Spacer(Modifier.height(24.dp))
                        if (isImporting) { Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = ConanTheme.BlueSuit) } }
                        else {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                TextButton(onClick = { showImportDialog = false }) { Text("Cancel", color = Color.Gray, fontWeight = FontWeight.Bold) }
                                Spacer(Modifier.width(8.dp))
                                Button(onClick = { if (importCode.isNotBlank()) { isImporting = true; importStatus = "Importing..."; viewModel.importDeckFromCode(importCode) { _, message -> isImporting = false; importStatus = message } } }, colors = ButtonDefaults.buttonColors(containerColor = ConanTheme.BlueSuit), shape = RoundedCornerShape(12.dp)) { Text("Import", fontWeight = FontWeight.Bold) }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showDeckSelectorForTest) {
        Dialog(onDismissRequest = { showDeckSelectorForTest = false }) {
            Card(modifier = Modifier.fillMaxWidth().heightIn(max = 500.dp), shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = Color.White), elevation = CardDefaults.cardElevation(8.dp)) {
                Column(Modifier.padding(24.dp)) {
                    Text("SELECT DECK", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = ConanTheme.BlueSuit, modifier = Modifier.padding(bottom = 16.dp), letterSpacing = 2.sp)
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(viewModel.allDecks) { deck ->
                            Card(modifier = Modifier.fillMaxWidth().clickable { selectedDeckForTest = deck; handTestCards = viewModel.generateHandTest(deck); heldCardsIndices = emptySet(); showDeckSelectorForTest = false; showHandTestDialog = true }, colors = CardDefaults.cardColors(containerColor = Color(0xFFF5F5F5)), border = BorderStroke(1.dp, Color.LightGray), shape = RoundedCornerShape(12.dp)) {
                                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Style, null, tint = ConanTheme.BlueSuit)
                                    Spacer(Modifier.width(16.dp))
                                    Column { Text(deck.name, fontWeight = FontWeight.Bold, fontSize = 16.sp); Text("${deck.cards.values.sum()} cards", fontSize = 12.sp, color = Color.Gray) }
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(24.dp))
                    Button(onClick = { showDeckSelectorForTest = false }, modifier = Modifier.fillMaxWidth().height(48.dp), colors = ButtonDefaults.buttonColors(containerColor = Color.LightGray), shape = RoundedCornerShape(12.dp)) { Text("Cancel", color = Color.DarkGray, fontWeight = FontWeight.Bold) }
                }
            }
        }
    }

    if (showHandTestDialog) {
        Dialog(onDismissRequest = { showHandTestDialog = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Card(modifier = Modifier.fillMaxWidth(0.95f), shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = Color.White), elevation = CardDefaults.cardElevation(8.dp)) {
                Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("HAND TEST", fontWeight = FontWeight.Bold, fontSize = 20.sp, color = ConanTheme.BlueSuit, letterSpacing = 2.sp)
                    Text("5 Random cards from: ${selectedDeckForTest?.name ?: ""}", fontSize = 12.sp, color = Color.Gray, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 4.dp))
                    Spacer(Modifier.height(24.dp))
                    if (handTestCards.isEmpty()) { Text("Deck is empty!", color = Color.Red, fontWeight = FontWeight.Bold) } else {
                        Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            handTestCards.forEachIndexed { index, card ->
                                val isHeld = heldCardsIndices.contains(index)
                                val imageModel = viewModel.getCardImageModel(card)
                                Box(modifier = Modifier.width(80.dp).aspectRatio(0.71f).clip(RoundedCornerShape(8.dp)).shadow(if(isHeld) 0.dp else 4.dp).clickable { heldCardsIndices = if (isHeld) { heldCardsIndices - index } else { heldCardsIndices + index } }) {
                                    AsyncImage(model = imageModel, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                                    if (isHeld) { Box(modifier = Modifier.fillMaxSize().border(3.dp, Color(0xFF4CAF50), RoundedCornerShape(8.dp)).background(Color.Black.copy(alpha = 0.4f))) { Text(text = "HOLD", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp, modifier = Modifier.align(Alignment.Center).background(Color(0xFF4CAF50), RoundedCornerShape(4.dp)).padding(horizontal = 6.dp, vertical = 2.dp)) } }
                                }
                            }
                        }
                        Text("Tap cards to HOLD/UNHOLD", fontSize = 12.sp, color = Color.Gray, modifier = Modifier.padding(top = 16.dp))
                    }
                    Spacer(Modifier.height(24.dp))
                    Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        Button(onClick = { if (selectedDeckForTest != null) { handTestCards = viewModel.generateHandWithHolds(selectedDeckForTest!!, handTestCards, heldCardsIndices) } }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE0E0E0)), shape = RoundedCornerShape(12.dp), modifier = Modifier.weight(1f).height(48.dp)) {
                            Icon(Icons.Default.Refresh, null, modifier = Modifier.size(18.dp), tint = Color.DarkGray); Spacer(Modifier.width(8.dp)); Text("Re-Draw", color = Color.DarkGray, fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.width(12.dp))
                        Button(onClick = { showHandTestDialog = false }, colors = ButtonDefaults.buttonColors(containerColor = ConanTheme.BowTieRed), shape = RoundedCornerShape(12.dp), modifier = Modifier.weight(1f).height(48.dp)) { Text("Close", fontWeight = FontWeight.Bold) }
                    }
                }
            }
        }
    }
}

@Composable
fun DeckListItem(deck: Deck, partnerCard: WebCard?, viewModel: CardViewModel, onEdit: () -> Unit, onDelete: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().shadow(4.dp, RoundedCornerShape(16.dp)).clickable { onEdit() }, shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.width(55.dp).aspectRatio(0.71f).background(Color(0xFFF0F0F0), RoundedCornerShape(6.dp)).border(1.dp, Color.LightGray, RoundedCornerShape(6.dp)).clip(RoundedCornerShape(6.dp)), contentAlignment = Alignment.Center) {
                if (partnerCard != null) { SubcomposeAsyncImage(model = viewModel.getCardImageModel(partnerCard), contentDescription = "Partner", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize(), loading = { Box(Modifier.fillMaxSize().background(Color.LightGray)) }) }
                else { Icon(Icons.Default.PersonSearch, null, tint = Color.Gray, modifier = Modifier.size(24.dp)) }
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(text = deck.name, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Color.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(4.dp))
                val count = deck.cards.values.sum()
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(color = if(count == 40) Color(0xFFE8F5E9) else Color(0xFFFFF3E0), shape = RoundedCornerShape(4.dp)) {
                        Text(text = "$count / 40 cards", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = if(count == 40) Color(0xFF2E7D32) else Color(0xFFEF6C00), modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                    }
                }
            }
            IconButton(onClick = onDelete) { Icon(Icons.Default.DeleteOutline, "Delete", tint = Color.Red.copy(alpha = 0.7f)) }
        }
    }
}

@Composable
fun MenuItem(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String, onClick: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 16.dp, horizontal = 24.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = ConanTheme.BlueSuit, modifier = Modifier.size(24.dp)); Spacer(modifier = Modifier.width(16.dp))
        Text(text, fontSize = 16.sp, fontWeight = FontWeight.Medium, color = Color.Black); Spacer(Modifier.weight(1f))
        Icon(Icons.AutoMirrored.Filled.ArrowForwardIos, contentDescription = null, tint = Color.LightGray, modifier = Modifier.size(16.dp))
    }
}

@Composable
fun AnimateInEffect(content: @Composable () -> Unit) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }
    AnimatedVisibility(visible = visible, enter = fadeIn(tween(300)) + slideInVertically(tween(300)) { it / 4 }) { content() }
}
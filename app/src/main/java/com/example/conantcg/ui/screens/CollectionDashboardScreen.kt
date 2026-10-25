package com.example.conantcg.ui.screens

import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.example.conantcg.R
import com.example.conantcg.data.model.WebCard
import com.example.conantcg.ui.components.SwipeableCardDetailDialog
import com.example.conantcg.ui.viewmodel.CardViewModel
import com.example.conantcg.utils.ConanTheme
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * Detective Conan TCG — Collection Archive V1.
 * New, independent screen. No database, ViewModel or NavGraph changes.
 * Collection completion counts unique Card Num (artworks), not Card ID.
 */
/** Match the original My Collection screen: blue header, clean white content. */
private object ArchiveTone {
    val bg = Color(0xFFF7F9FC)
    val surface = Color.White
    val raised = Color(0xFFEAF0F8)
    val border = Color(0xFFDCE4EF)
    val blue = ConanTheme.BlueSuit
    val gold = Color(0xFFB27A19)
    val red = ConanTheme.BowTieRed
    val green = Color(0xFF208459)
    val white = Color(0xFF1D293B) // Existing foreground text on light panels
    val muted = Color(0xFF63758C)
}

private fun archiveCode(cardNum: String): String =
    cardNum.trim().uppercase(Locale.ROOT)

private enum class ArchiveVariant(val title: String) {
    NORMAL("NORMAL"),
    PARALLEL("PARALLEL"),
    PARALLEL2("P2"),
    SEC1("SEC1"),
    SEC2("SEC2"),
    OTHER_SECRET("OTHER SECRET")
}

private fun archiveVariant(card: WebCard): ArchiveVariant {
    val num = archiveCode(card.cardNum)
    val rarity = card.rarity.uppercase(Locale.ROOT)
    return when {
        num.endsWith("SEC2") -> ArchiveVariant.SEC2
        num.endsWith("SEC1") -> ArchiveVariant.SEC1
        num.endsWith("P2") -> ArchiveVariant.PARALLEL2
        num.endsWith("P") -> ArchiveVariant.PARALLEL
        rarity.contains("SEC") -> ArchiveVariant.OTHER_SECRET
        else -> ArchiveVariant.NORMAL
    }
}

private data class ArchiveGroup(
    val label: String,
    val total: Int,
    val owned: Int
) {
    val fraction: Float get() = if (total > 0) owned.toFloat() / total else 0f
}

private data class ArchiveSnapshot(
    val cards: List<WebCard>,
    val quantity: Map<String, Int>
) {
    private val knownNumbers: Set<String> = cards.map { archiveCode(it.cardNum) }.toSet()
    val totalTypes: Int = cards.size
    val ownedTypes: Int = cards.count { (quantity[archiveCode(it.cardNum)] ?: 0) > 0 }
    val missingTypes: Int = totalTypes - ownedTypes
    val totalCopies: Long = quantity.values.sumOf { it.toLong() }
    val duplicateTypes: Int = cards.count { (quantity[archiveCode(it.cardNum)] ?: 0) >= 2 }
    val excessCopies: Long = quantity.values.sumOf { (it - 1).coerceAtLeast(0).toLong() }
    val unknownEntries: Int = quantity.count { (num, qty) -> qty > 0 && num !in knownNumbers }
    val completion: Float get() = if (totalTypes > 0) ownedTypes.toFloat() / totalTypes else 0f

    val packs: List<ArchiveGroup> = cards
        .groupBy { it.pack.trim().ifBlank { "Unknown Pack" } }
        .map { (name, list) ->
            ArchiveGroup(name, list.size, list.count { (quantity[archiveCode(it.cardNum)] ?: 0) > 0 })
        }
        .sortedWith(compareBy<ArchiveGroup> { it.label.uppercase(Locale.ROOT) })

    val rarities: List<ArchiveGroup> = cards
        .groupBy { it.rarity.trim().ifBlank { "Unknown" } }
        .map { (rarity, list) ->
            ArchiveGroup(rarity, list.size, list.count { (quantity[archiveCode(it.cardNum)] ?: 0) > 0 })
        }
        .sortedWith(compareByDescending<ArchiveGroup> { it.total }.thenBy { it.label })

    val artworks: List<ArchiveGroup> = ArchiveVariant.entries.map { kind ->
        val subset = cards.filter { archiveVariant(it) == kind }
        ArchiveGroup(kind.title, subset.size, subset.count { (quantity[archiveCode(it.cardNum)] ?: 0) > 0 })
    }.filter { it.total > 0 }
}

private enum class ArchiveMode { OWNED, MISSING, DUPLICATES, PACK, RARITY, ARTWORK }

private data class ArchiveView(
    val mode: ArchiveMode,
    val filter: String = "",
    val title: String = ""
)

private fun archiveCardsFor(
    snapshot: ArchiveSnapshot,
    view: ArchiveView
): List<WebCard> = snapshot.cards.filter { card ->
    val qty = snapshot.quantity[archiveCode(card.cardNum)] ?: 0
    when (view.mode) {
        ArchiveMode.OWNED -> qty > 0
        ArchiveMode.MISSING -> qty == 0
        ArchiveMode.DUPLICATES -> qty >= 2
        ArchiveMode.PACK -> card.pack.trim().ifBlank { "Unknown Pack" } == view.filter
        ArchiveMode.RARITY -> card.rarity.trim().ifBlank { "Unknown" } == view.filter
        ArchiveMode.ARTWORK -> archiveVariant(card).title == view.filter
    }
}.sortedWith(
    compareBy<WebCard> { it.pack.uppercase(Locale.ROOT) }
        .thenBy { it.cardNum.uppercase(Locale.ROOT) }
)

/**
 * Call from CardBrowserScreen only when isCollectionMode == true.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CollectionDashboardScreen(
    viewModel: CardViewModel,
    onBack: () -> Unit
) {
    val catalog = viewModel.cardList.toList()
        .filter { it.cardNum.isNotBlank() }
        .distinctBy { archiveCode(it.cardNum) }
    val quantities = viewModel.collectedCounts.toMap()
        .filterValues { it > 0 }
        .entries.groupBy { archiveCode(it.key) }
        .mapValues { (_, entries) -> entries.sumOf { it.value } }
    val snapshot = remember(catalog, quantities) {
        ArchiveSnapshot(catalog, quantities)
    }

    var selection by remember { mutableStateOf<ArchiveView?>(null) }
    var allPacks by remember { mutableStateOf(false) }
    var allRarities by remember { mutableStateOf(false) }
    val loading = viewModel.isLoading && catalog.isEmpty()
    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

    BackHandler(enabled = selection == null) { onBack() }

    // bg1 fills the Dashboard background. All existing white cards stay readable.
    // Place bg1.png at: app/src/main/res/drawable-nodpi/bg1.png
    Box(modifier = Modifier.fillMaxSize()) {
        Image(
            painter = painterResource(id = R.drawable.bg),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )

        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(ConanTheme.BlueSuit, Color(0xFF151C5E))
                            )
                        )
                        .statusBarsPadding()
                ) {
                    Box(
                        modifier = Modifier.fillMaxWidth()
                            .height(if (isLandscape) 40.dp else 48.dp)
                    ) {
                        IconButton(
                            onClick = onBack,
                            modifier = Modifier.align(Alignment.CenterStart)
                        ) {
                            Image(
                                painter = painterResource(id = R.drawable.arrowleft),
                                contentDescription = "Back to My Collection",
                                modifier = Modifier.size(30.dp)
                            )
                        }
                        Column(
                            modifier = Modifier.align(Alignment.Center)
                                .padding(horizontal = 56.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                "Collection Dashboard",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = if (isLandscape) 16.sp else 18.sp,
                                maxLines = 1
                            )
                            if (!isLandscape) {
                                Text("My Collection", color = Color.White.copy(alpha = 0.75f), fontSize = 11.sp)
                            }
                        }
                        Icon(
                            Icons.Default.BarChart,
                            contentDescription = null,
                            tint = ConanTheme.GoldBadge,
                            modifier = Modifier.align(Alignment.CenterEnd)
                                .padding(end = 16.dp).size(22.dp)
                        )
                    }
                }
            }
        ) { padding ->
            if (catalog.isEmpty()) {
                Box(
                    Modifier.fillMaxSize().padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        if (loading) "กำลังโหลดข้อมูลการ์ด..." else "ยังไม่มีข้อมูล Card Catalog\nโปรดกลับไปอัปเดตฐานข้อมูลก่อน",
                        color = Color.White,
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .navigationBarsPadding(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 28.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item(key = "hero") { ArchiveHero(snapshot) }

                    item(key = "summary_heading") {
                        ArchiveHeading("01", "Collection Summary", "ภาพรวม Collection", ArchiveTone.gold)
                    }
                    item(key = "summary_stats") {
                        Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                                ArchiveStatTile(
                                    "OWNED ARTWORKS", "${snapshot.ownedTypes}",
                                    ArchiveTone.blue, Icons.Default.CollectionsBookmark,
                                    Modifier.weight(1f)
                                ) { selection = ArchiveView(ArchiveMode.OWNED, title = "OWNED ARTWORKS") }
                                ArchiveStatTile(
                                    "TOTAL COPIES", "${snapshot.totalCopies}",
                                    ArchiveTone.green, Icons.Default.Inventory2,
                                    Modifier.weight(1f)
                                ) { selection = ArchiveView(ArchiveMode.OWNED, title = "MY COLLECTION") }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                                ArchiveStatTile(
                                    "MISSING", "${snapshot.missingTypes}",
                                    ArchiveTone.red, Icons.Default.SearchOff,
                                    Modifier.weight(1f)
                                ) { selection = ArchiveView(ArchiveMode.MISSING, title = "Missing Cards") }
                                ArchiveStatTile(
                                    "EXTRA COPIES", "${snapshot.excessCopies}",
                                    ArchiveTone.blue, Icons.Default.ContentCopy,
                                    Modifier.weight(1f)
                                ) { selection = ArchiveView(ArchiveMode.DUPLICATES, title = "DUPLICATE CARDS") }
                            }
                        }
                    }
                    if (snapshot.unknownEntries > 0) {
                        item(key = "unmatched") {
                            Text(
                                "หมายเหตุ: มี ${snapshot.unknownEntries} Card Num ใน Collection " +
                                        "ที่ไม่พบใน Catalog ล่าสุด จึงไม่นับในเปอร์เซ็นต์ความครบ (แต่ยังรวมในจำนวนการ์ดจริง)",
                                fontSize = 11.sp, color = Color(0xFFFFD77C)
                            )
                        }
                    }
                    item(key = "pack_heading") {
                        ArchiveHeading("02", "Pack Completion", "ความครบของแต่ละชุด", ArchiveTone.blue)
                    }
                    item(key = "pack_list") {
                        ArchivePanel {
                            val displayed = if (allPacks) snapshot.packs else snapshot.packs.take(5)
                            displayed.forEachIndexed { index, pack ->
                                ArchivePackProgressRow(pack, viewModel) {
                                    selection = ArchiveView(ArchiveMode.PACK, pack.label, "PACK: ${pack.label}")
                                }
                                if (index != displayed.lastIndex) {
                                    HorizontalDivider(color = ArchiveTone.border.copy(alpha = 0.45f),
                                        modifier = Modifier.padding(vertical = 10.dp))
                                }
                            }
                            if (snapshot.packs.size > 5) {
                                TextButton(onClick = { allPacks = !allPacks },
                                    modifier = Modifier.align(Alignment.CenterHorizontally)) {
                                    Text(if (allPacks) "SHOW LESS" else "VIEW ALL ${snapshot.packs.size} PACKS",
                                        color = ArchiveTone.gold)
                                    Icon(if (allPacks) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                        null, tint = ArchiveTone.gold)
                                }
                            }
                        }
                    }
                    item(key = "rarity_heading") {
                        ArchiveHeading("03", "Rarity Collection", "สะสมครบตามระดับความหายาก", ArchiveTone.gold)
                    }
                    item(key = "rarity_list") {
                        ArchivePanel {
                            val displayed = if (allRarities) snapshot.rarities else snapshot.rarities.take(6)
                            displayed.forEachIndexed { index, rarity ->
                                ArchiveProgressRow(rarity, ArchiveTone.gold) {
                                    selection = ArchiveView(ArchiveMode.RARITY, rarity.label, "RARITY: ${rarity.label}")
                                }
                                if (index != displayed.lastIndex) {
                                    Spacer(Modifier.height(12.dp))
                                }
                            }
                            if (snapshot.rarities.size > 6) {
                                TextButton(onClick = { allRarities = !allRarities },
                                    modifier = Modifier.align(Alignment.CenterHorizontally)) {
                                    Text(if (allRarities) "SHOW LESS" else "VIEW ALL RARITIES",
                                        color = ArchiveTone.gold)
                                    Icon(if (allRarities) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                        null, tint = ArchiveTone.gold)
                                }
                            }
                        }
                    }
                    if (snapshot.artworks.isNotEmpty()) {
                        item(key = "artwork_heading") {
                            ArchiveHeading("04", "Artwork Versions", "นับแยก Normal / Parallel / SEC", ArchiveTone.green)
                        }
                        item(key = "artwork_list") {
                            ArchivePanel {
                                snapshot.artworks.forEachIndexed { index, entry ->
                                    ArchiveProgressRow(entry, ArchiveTone.green) {
                                        selection = ArchiveView(ArchiveMode.ARTWORK, entry.label,
                                            "ARTWORK: ${entry.label}")
                                    }
                                    if (index != snapshot.artworks.lastIndex) Spacer(Modifier.height(12.dp))
                                }
                            }
                        }
                    }
                    item(key = "missing_heading") {
                        ArchiveHeading("05", "MISSING EVIDENCE", "การ์ดที่ยังไม่อยู่ในคอลเลกชัน", ArchiveTone.red)
                    }
                    item(key = "missing_action") {
                        ArchiveActionCard(
                            Icons.Default.SearchOff, "SEARCH UNCOLLECTED CARDS",
                            "เหลือ ${snapshot.missingTypes} Artwork จาก ${snapshot.totalTypes} Artwork",
                            ArchiveTone.red
                        ) { selection = ArchiveView(ArchiveMode.MISSING, title = "MISSING EVIDENCE") }
                    }
                    item(key = "duplicate_heading") {
                        ArchiveHeading("06", "Duplicate Finder", "การ์ดที่มีอย่างน้อยสองใบ", ArchiveTone.gold)
                    }
                    item(key = "duplicate_action") {
                        ArchiveActionCard(
                            Icons.Default.ContentCopy, "INVESTIGATE DUPLICATES",
                            "พบ ${snapshot.duplicateTypes} Artwork ที่มีซ้ำ · ส่วนเกิน ${snapshot.excessCopies} ใบ",
                            ArchiveTone.gold
                        ) { selection = ArchiveView(ArchiveMode.DUPLICATES, title = "DUPLICATE CARDS") }
                    }
                    item(key = "footer") {
                        Text(
                            "Collection data is calculated using unique Card Num from the current catalog",
                            color = Color.White.copy(alpha = 0.78f), fontSize = 10.sp,
                            textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    } // Dashboard background Box

    selection?.let { target ->
        ArchiveCardBrowserDialog(
            viewModel = viewModel,
            snapshot = snapshot,
            target = target,
            onDismiss = { selection = null }
        )
    }
}

@Composable
private fun ArchiveHero(data: ArchiveSnapshot) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = ArchiveTone.surface,
        border = BorderStroke(1.dp, ArchiveTone.border),
        shadowElevation = 2.dp
    ) {
        Column(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.CollectionsBookmark, null, tint = ArchiveTone.blue,
                    modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(9.dp))
                Text("Collection Progress", color = ArchiveTone.white,
                    fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                Text("${(data.completion * 100).toInt()}%",
                    color = ArchiveTone.blue, fontSize = 25.sp,
                    fontWeight = FontWeight.Bold)
            }
            LinearProgressIndicator(
                progress = { data.completion },
                modifier = Modifier.fillMaxWidth().height(9.dp)
                    .clip(RoundedCornerShape(9.dp)),
                color = ArchiveTone.blue,
                trackColor = ArchiveTone.raised
            )
            Text(
                "${data.ownedTypes} / ${data.totalTypes} Artwork ที่สะสมแล้ว",
                color = ArchiveTone.muted, fontSize = 12.sp
            )
        }
    }
}

@Composable
private fun ArchiveHeading(number: String, title: String, subtitle: String, tint: Color) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(top = 5.dp)
    ) {
        Box(
            Modifier.width(4.dp).height(33.dp)
                .background(ConanTheme.BowTieRed, RoundedCornerShape(4.dp))
        )
        Spacer(Modifier.width(11.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = Color.White, fontWeight = FontWeight.Bold,
                fontSize = 15.sp)
            Text(subtitle, color = Color.White.copy(alpha = 0.80f), fontSize = 11.sp)
        }
    }
}

@Composable
private fun ArchiveStatTile(
    label: String,
    value: String,
    tint: Color,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier,
        onClick = onClick,
        color = ArchiveTone.surface,
        shape = RoundedCornerShape(13.dp),
        border = BorderStroke(1.dp, ArchiveTone.border),
        shadowElevation = 1.dp
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, tint = tint, modifier = Modifier.size(19.dp))
                Spacer(Modifier.weight(1f))
                Icon(Icons.Default.ChevronRight, null,
                    tint = ArchiveTone.muted, modifier = Modifier.size(16.dp))
            }
            Text(value, color = ArchiveTone.white, fontSize = 25.sp,
                fontWeight = FontWeight.Black, maxLines = 1)
            Text(label, color = ArchiveTone.muted, fontSize = 10.sp,
                fontWeight = FontWeight.Bold, maxLines = 1)
        }
    }
}

@Composable
private fun ArchivePanel(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        color = ArchiveTone.surface,
        border = BorderStroke(1.dp, ArchiveTone.border),
        shape = RoundedCornerShape(14.dp),
        shadowElevation = 1.dp
    ) {
        Column(Modifier.fillMaxWidth().padding(14.dp), content = content)
    }
}

/** Use the same pack cover assets as My Collection's pack browser. */
@Composable
private fun ArchivePackProgressRow(
    group: ArchiveGroup,
    viewModel: CardViewModel,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        AsyncImage(
            model = viewModel.getPackCoverImage(group.label),
            contentDescription = group.label,
            modifier = Modifier.size(width = 54.dp, height = 65.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(ArchiveTone.raised),
            contentScale = ContentScale.Fit
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(group.label, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                    color = ArchiveTone.white, modifier = Modifier.weight(1f),
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${group.owned}/${group.total}", color = ArchiveTone.muted, fontSize = 11.sp)
                Icon(Icons.Default.ChevronRight, null, tint = ArchiveTone.blue,
                    modifier = Modifier.size(18.dp))
            }
            LinearProgressIndicator(
                progress = { group.fraction },
                modifier = Modifier.fillMaxWidth().height(7.dp)
                    .clip(RoundedCornerShape(8.dp)),
                color = ArchiveTone.blue, trackColor = ArchiveTone.raised
            )
            Text("${(group.fraction * 100).toInt()}% collected",
                color = ArchiveTone.muted, fontSize = 10.sp)
        }
    }
}

@Composable
private fun ArchiveProgressRow(group: ArchiveGroup, color: Color, onClick: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(group.label, color = ArchiveTone.white, fontWeight = FontWeight.Bold,
                fontSize = 12.sp, modifier = Modifier.weight(1f),
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("${group.owned}/${group.total}", color = ArchiveTone.muted, fontSize = 11.sp)
            Spacer(Modifier.width(6.dp))
            Icon(Icons.Default.ChevronRight, null, tint = color, modifier = Modifier.size(16.dp))
        }
        LinearProgressIndicator(
            progress = { group.fraction },
            modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(6.dp)),
            color = color, trackColor = ArchiveTone.raised
        )
    }
}

@Composable
private fun ArchiveActionCard(
    icon: ImageVector, title: String, subtitle: String,
    color: Color, onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = ArchiveTone.surface,
        border = BorderStroke(1.dp, color.copy(alpha = 0.65f))
    ) {
        Row(Modifier.fillMaxWidth().padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(color = color.copy(alpha = 0.13f), shape = RoundedCornerShape(10.dp)) {
                Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) {
                    Icon(icon, null, tint = color)
                }
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, fontWeight = FontWeight.Black, color = ArchiveTone.white,
                    fontSize = 12.sp)
                Text(subtitle, color = ArchiveTone.muted, fontSize = 11.sp,
                    lineHeight = 15.sp)
            }
            Icon(Icons.Default.ChevronRight, null, tint = color)
        }
    }
}

/** Image-only evidence gallery; details open through existing card detail dialog. */
@Composable
private fun ArchiveCardBrowserDialog(
    viewModel: CardViewModel,
    snapshot: ArchiveSnapshot,
    target: ArchiveView,
    onDismiss: () -> Unit
) {
    val cards = remember(snapshot, target) { archiveCardsFor(snapshot, target) }
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var search by remember(target) { mutableStateOf("") }
    var previewIndex by remember(target) { mutableStateOf<Int?>(null) }

    val shownCards = remember(cards, search) {
        val q = search.trim()
        if (q.isEmpty()) cards else cards.filter { card ->
            card.name.contains(q, ignoreCase = true) ||
                    card.cardNum.contains(q, ignoreCase = true) ||
                    card.cardId.contains(q, ignoreCase = true) ||
                    (card.nameTh?.contains(q, ignoreCase = true) == true)
        }
    }

    Dialog(onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = ArchiveTone.bg,
            shape = RoundedCornerShape(0.dp)
        ) {
            Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
                Row(
                    modifier = Modifier.fillMaxWidth()
                        .background(Brush.verticalGradient(
                            listOf(ConanTheme.BlueSuit, Color(0xFF151C5E))
                        ))
                        .height(54.dp)
                        .padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onDismiss) {
                        Image(
                            painter = painterResource(id = R.drawable.arrowleft),
                            contentDescription = "Back to Collection Dashboard",
                            modifier = Modifier.size(30.dp)
                        )
                    }
                    Column(Modifier.weight(1f)) {
                        Text(target.title, color = Color.White,
                            fontWeight = FontWeight.Bold, fontSize = 15.sp,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("${shownCards.size} Card Artworks", fontSize = 10.sp,
                            color = Color.White.copy(alpha = 0.75f))
                    }
                    Icon(Icons.Default.Collections, null, tint = ConanTheme.GoldBadge,
                        modifier = Modifier.padding(end = 12.dp))
                }
                OutlinedTextField(
                    value = search,
                    onValueChange = { search = it },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 5.dp),
                    singleLine = true,
                    placeholder = { Text("Search Card Num / Card ID / Name...", fontSize = 12.sp) },
                    leadingIcon = { Icon(Icons.Default.Search, null) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = ArchiveTone.white,
                        unfocusedTextColor = ArchiveTone.white,
                        focusedContainerColor = Color.White,
                        unfocusedContainerColor = Color.White,
                        focusedBorderColor = ArchiveTone.blue,
                        unfocusedBorderColor = ArchiveTone.border,
                        focusedLeadingIconColor = ArchiveTone.blue,
                        unfocusedLeadingIconColor = ArchiveTone.muted,
                        unfocusedPlaceholderColor = ArchiveTone.muted,
                        focusedPlaceholderColor = ArchiveTone.muted,
                        cursorColor = ArchiveTone.blue
                    ),
                    shape = RoundedCornerShape(12.dp)
                )
                if (target.mode == ArchiveMode.MISSING || target.mode == ArchiveMode.PACK ||
                    target.mode == ArchiveMode.RARITY || target.mode == ArchiveMode.ARTWORK) {
                    Text("แตะรูปเพื่อดูรายละเอียด • กดรูปหัวใจเพื่อเพิ่มการ์ดที่ยังไม่มีลง Wishlist",
                        color = ArchiveTone.muted, fontSize = 10.sp,
                        modifier = Modifier.padding(horizontal = 15.dp, vertical = 6.dp))
                } else {
                    Text("แตะรูปเพื่อดูรายละเอียดหรือแก้จำนวนการ์ดใน Collection",
                        color = ArchiveTone.muted, fontSize = 10.sp,
                        modifier = Modifier.padding(horizontal = 15.dp, vertical = 6.dp))
                }

                if (shownCards.isEmpty()) {
                    Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Text("ไม่พบการ์ดตามเงื่อนไข", color = ArchiveTone.muted)
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 104.dp),
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(9.dp),
                        horizontalArrangement = Arrangement.spacedBy(9.dp)
                    ) {
                        items(
                            count = shownCards.size,
                            key = { index -> archiveCode(shownCards[index].cardNum) }
                        ) { index ->
                            val card = shownCards[index]
                            val qty = snapshot.quantity[archiveCode(card.cardNum)] ?: 0
                            val inWishlist = viewModel.wishlistFolders.any { folder ->
                                viewModel.isWishlisted(card.cardNum, folder)
                            }
                            Box(
                                modifier = Modifier.fillMaxWidth()
                                    .aspectRatio(if (card.type.contains("Case", true) ||
                                        card.type.contains("事件", true)) 1.40f else 0.71f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .border(1.dp, if (qty > 0) ArchiveTone.green else ArchiveTone.border,
                                        RoundedCornerShape(8.dp))
                                    .background(ArchiveTone.raised)
                                    .clickable { previewIndex = index }
                            ) {
                                AsyncImage(
                                    model = viewModel.getCardImageModel(card),
                                    contentDescription = card.cardNum,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Fit
                                )
                                if (qty > 0) {
                                    Surface(
                                        modifier = Modifier.align(Alignment.BottomEnd).padding(3.dp),
                                        color = ArchiveTone.green,
                                        shape = RoundedCornerShape(5.dp)
                                    ) {
                                        Text("×$qty", fontSize = 10.sp,
                                            fontWeight = FontWeight.Black, color = Color.White,
                                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp))
                                    }
                                } else {
                                    IconButton(
                                        onClick = {
                                            if (!inWishlist) {
                                                viewModel.toggleWishlist(card.cardNum, "Default")
                                                scope.launch { snackbar.showSnackbar("Added ${card.cardNum} to Wishlist") }
                                            } else {
                                                scope.launch { snackbar.showSnackbar("Already in Wishlist") }
                                            }
                                        },
                                        modifier = Modifier.align(Alignment.TopEnd).size(35.dp)
                                            .background(Color.White.copy(alpha = 0.92f),
                                                RoundedCornerShape(bottomStart = 8.dp))
                                    ) {
                                        Icon(
                                            if (inWishlist) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                            contentDescription = if (inWishlist) "Already in wishlist" else "Add to wishlist",
                                            tint = if (inWishlist) ArchiveTone.red else ArchiveTone.blue,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                SnackbarHost(snackbar, modifier = Modifier.fillMaxWidth())
            }
        }
    }

    previewIndex?.let { index ->
        if (index in shownCards.indices) {
            SwipeableCardDetailDialog(
                initialCards = shownCards,
                startIndex = index,
                viewModel = viewModel,
                isCollectionMode = true,
                onDismiss = { previewIndex = null }
            )
        }
    }
}

package com.example.conantcg.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FactCheck
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.example.conantcg.data.model.WebCard
import com.example.conantcg.ui.viewmodel.CardViewModel
import kotlinx.coroutines.launch
import java.util.Locale

private val owNavy = Color(0xFF0D1525)
private val owPanel = Color(0xFF192740)
private val owGold = Color(0xFFF3CC7C)
private val owRed = Color(0xFFE44851)
private val owGreen = Color(0xFF62D6A0)
private val owText = Color(0xFFEAF1FC)
private val owSecondary = Color(0xFFAFC0D8)

private fun owNormalizeNum(value: String): String =
    value.uppercase(Locale.ROOT).filter { it in 'A'..'Z' || it in '0'..'9' }

// Card ID identifies interchangeable arts; full Card Num identifies the physical version.
private fun owIdentity(card: WebCard): String {
    val rawId = card.cardId.trim().uppercase(Locale.ROOT)
    val printedId = Regex("""ID\s*[\[(]?\s*([A-Z0-9]+)""").find(rawId)
        ?.groupValues?.getOrNull(1)
    val id = (printedId ?: owNormalizeNum(rawId))
        .takeUnless { it.isBlank() || it == "NULL" }
    return if (id == null) "NUM:${owNormalizeNum(card.cardNum)}" else "ID:$id"
}

private data class OwDemand(
    var required: Int = 0,
    val requestNums: MutableSet<String> = linkedSetOf(),
    val sections: MutableSet<String> = linkedSetOf()
)

private data class OwEntry(
    val identity: String,
    val title: String,
    val requested: Int,
    val owned: Int,
    val sections: String,
    val preferred: WebCard?,
    val allArtworkCount: Int,
    val ownedArtworkDetails: String,
    val unknown: Boolean
) {
    val missing: Int get() = (requested - owned).coerceAtLeast(0)
    val covered: Int get() = requested.coerceAtMost(owned)
}

private data class OwReport(
    val rows: List<OwEntry>,
    val mainCardCount: Int,
    val hasPartner: Boolean,
    val hasCase: Boolean
) {
    val required: Int get() = rows.sumOf { it.requested }
    val covered: Int get() = rows.sumOf { it.covered }
    val missing: Int get() = rows.sumOf { it.missing }
}

/**
 * Each owned physical copy is counted once per logical Card ID.
 * This prevents double-counting when a deck lists Normal + Parallel + P2 of the same card.
 * Unknown catalog entries use the exact Card Num as a conservative fallback.
 */
private fun buildOwnershipReport(
    catalog: List<WebCard>,
    mainDeck: Map<String, Int>,
    partnerNum: String?,
    caseNum: String?,
    collection: Map<String, Int>
): OwReport {
    val byNum = catalog
        .filter { it.cardNum.isNotBlank() }
        .distinctBy { owNormalizeNum(it.cardNum) }
        .associateBy { owNormalizeNum(it.cardNum) }
    val artworkById = byNum.values.groupBy { owIdentity(it) }
    val demands = linkedMapOf<String, OwDemand>()

    fun requireCard(rawNum: String, quantity: Int, section: String) {
        if (quantity <= 0 || rawNum.isBlank()) return
        val num = owNormalizeNum(rawNum)
        if (num.isBlank()) return
        val key = if (section == "MAIN") {
            byNum[num]?.let(::owIdentity) ?: "NUM:$num"
        } else {
            // Partner and Case are separate slots: do not count an ordinary
            // character card with the same ID as a substitute.
            "$section:NUM:$num"
        }
        val record = demands.getOrPut(key) { OwDemand() }
        record.required += quantity
        record.requestNums.add(num)
        record.sections.add(section)
    }

    mainDeck.forEach { (num, qty) -> requireCard(num, qty, "MAIN") }
    partnerNum?.takeIf { it.isNotBlank() }?.let { requireCard(it, 1, "PARTNER") }
    caseNum?.takeIf { it.isNotBlank() }?.let { requireCard(it, 1, "CASE") }

    val normalizedOwned = linkedMapOf<String, Int>()
    val ownedByIdentity = linkedMapOf<String, Int>()
    collection.forEach { (rawNum, qty) ->
        if (qty > 0) {
            val num = owNormalizeNum(rawNum)
            normalizedOwned[num] = (normalizedOwned[num] ?: 0) + qty
            val key = byNum[num]?.let(::owIdentity) ?: "NUM:$num"
            ownedByIdentity[key] = (ownedByIdentity[key] ?: 0) + qty
            for (slot in listOf("PARTNER", "CASE")) {
                val slotKey = "$slot:NUM:$num"
                ownedByIdentity[slotKey] = (ownedByIdentity[slotKey] ?: 0) + qty
            }
        }
    }

    val rows = demands.map { (key, request) ->
        val requestCards = request.requestNums.mapNotNull { byNum[it] }
        val arts = if (key.startsWith("PARTNER:NUM:") ||
            key.startsWith("CASE:NUM:")) {
            requestCards
        } else {
            artworkById[key].orEmpty()
        }
        val preferred = requestCards.firstOrNull() ?: arts.firstOrNull()
        val ownedArts = arts.mapNotNull { card ->
            val count = normalizedOwned[owNormalizeNum(card.cardNum)] ?: 0
            if (count > 0) "${card.cardNum} ×$count" else null
        }.joinToString("  •  ")
        OwEntry(
            identity = key,
            title = preferred?.name?.takeIf { it.isNotBlank() }
                ?: "Unknown card: ${request.requestNums.first()}",
            requested = request.required,
            owned = ownedByIdentity[key] ?: 0,
            sections = request.sections.joinToString(" + "),
            preferred = preferred,
            allArtworkCount = arts.size,
            ownedArtworkDetails = ownedArts,
            unknown = preferred == null
        )
    }.sortedWith(compareByDescending<OwEntry> { it.missing }.thenBy { it.title })

    return OwReport(
        rows = rows,
        mainCardCount = mainDeck.values.filter { it > 0 }.sum(),
        hasPartner = !partnerNum.isNullOrBlank(),
        hasCase = !caseNum.isNullOrBlank()
    )
}

/** Wishlist V1 stores a set of physical Card Num, not missing quantities. */
private fun addOwnershipMissingToWishlist(
    vm: CardViewModel,
    rows: Collection<OwEntry>
): Int {
    var added = 0
    rows.filter { it.missing > 0 }.forEach { row ->
        val cardNum = row.preferred?.cardNum ?: return@forEach
        val alreadyListed = vm.wishlistFolders.any { folder ->
            vm.isWishlisted(cardNum, folder)
        }
        if (!alreadyListed) {
            vm.toggleWishlist(cardNum, "Default")
            added++
        }
    }
    return added
}

@Composable
fun DeckOwnershipCheckerDialog(
    viewModel: CardViewModel,
    onDismiss: () -> Unit
) {
    // Materialize Compose snapshot collections so the report updates after editing/scanning.
    val catalog = viewModel.cardList.toList()
    val deck = viewModel.deckMap.toMap()
    val owned = viewModel.collectedCounts.toMap()
    val partner = viewModel.partnerCard.value
    val case = viewModel.caseCard.value
    val report = remember(catalog, deck, owned, partner, case) {
        buildOwnershipReport(catalog, deck, partner, case, owned)
    }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val fraction = if (report.required > 0) {
        report.covered.toFloat() / report.required
    } else 0f

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(0.96f).fillMaxHeight(0.92f),
            shape = RoundedCornerShape(18.dp),
            color = owNavy,
            border = BorderStroke(1.dp, owGold.copy(alpha = 0.35f))
        ) {
            Column(Modifier.fillMaxSize().padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.FactCheck, null, tint = owGold, modifier = Modifier.size(27.dp))
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text("DECK INVESTIGATION", color = owGold, fontSize = 11.sp,
                            fontWeight = FontWeight.Black, letterSpacing = 1.sp)
                        Text(viewModel.getActiveDeckName().ifBlank { "Current Deck" },
                            color = owText, fontSize = 18.sp, fontWeight = FontWeight.Bold,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = owText)
                    }
                }

                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OwStat("REQUIRED", report.required.toString(), owGold, Modifier.weight(1f))
                    OwStat("OWNED", report.covered.toString(), owGreen, Modifier.weight(1f))
                    OwStat("MISSING", report.missing.toString(), owRed, Modifier.weight(1f))
                }
                Spacer(Modifier.height(10.dp))
                LinearProgressIndicator(
                    progress = { fraction },
                    modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(8.dp)),
                    color = owGreen,
                    trackColor = owPanel
                )
                Spacer(Modifier.height(5.dp))
                Text("MAIN ${report.mainCardCount}/40  •  PARTNER ${if (report.hasPartner) "SET" else "EMPTY"}  •  CASE ${if (report.hasCase) "SET" else "EMPTY"}",
                    color = owSecondary, fontSize = 10.sp)
                Spacer(Modifier.height(12.dp))
                Text("MISSING EVIDENCE / CARD INVENTORY", color = owGold,
                    fontWeight = FontWeight.Bold, fontSize = 11.sp)
                Spacer(Modifier.height(7.dp))

                if (report.rows.isEmpty()) {
                    Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Text("Add cards to your deck first.", color = owSecondary)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(9.dp)
                    ) {
                        items(report.rows, key = { it.identity }) { entry ->
                            OwCardRow(entry, viewModel) {
                                val added = addOwnershipMissingToWishlist(viewModel, listOf(entry))
                                scope.launch {
                                    snackbar.showSnackbar(
                                        if (added > 0) "Added to Wishlist / Default"
                                        else "Already in Wishlist or card unavailable"
                                    )
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(9.dp))
                Button(
                    onClick = {
                        val added = addOwnershipMissingToWishlist(viewModel, report.rows)
                        scope.launch {
                            snackbar.showSnackbar(
                                if (added > 0) "$added card types added to Wishlist / Default"
                                else "Nothing new to add to Wishlist"
                            )
                        }
                    },
                    enabled = report.missing > 0,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF225FA8)),
                    modifier = Modifier.fillMaxWidth().height(46.dp),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.FavoriteBorder, contentDescription = null,
                        modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("ADD MISSING TO WISHLIST", fontWeight = FontWeight.Bold)
                }
                SnackbarHost(snackbar)
            }
        }
    }
}

@Composable
private fun OwStat(title: String, value: String, accent: Color, modifier: Modifier) {
    Surface(modifier = modifier, shape = RoundedCornerShape(11.dp),
        color = owPanel, border = BorderStroke(1.dp, accent.copy(alpha = 0.45f))) {
        Column(Modifier.padding(vertical = 11.dp, horizontal = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, color = accent, fontWeight = FontWeight.Black, fontSize = 24.sp)
            Text(title, color = owSecondary, fontWeight = FontWeight.Bold, fontSize = 10.sp)
        }
    }
}

@Composable
private fun OwCardRow(entry: OwEntry, vm: CardViewModel, onWishlist: () -> Unit) {
    Surface(color = owPanel, shape = RoundedCornerShape(11.dp),
        border = BorderStroke(1.dp,
            if (entry.missing > 0) owRed.copy(alpha = 0.5f) else owGreen.copy(alpha = 0.5f))) {
        Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            val preferred = entry.preferred
            if (preferred != null) {
                AsyncImage(
                    model = vm.getCardImageModel(preferred),
                    contentDescription = preferred.cardNum,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.width(60.dp).height(85.dp)
                        .clip(RoundedCornerShape(5.dp)).background(Color.Black)
                )
            } else {
                Box(Modifier.width(60.dp).height(85.dp).background(Color.Black),
                    contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.SearchOff, null, tint = owSecondary)
                }
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(entry.title, color = owText, fontSize = 13.sp,
                    fontWeight = FontWeight.Bold, maxLines = 2,
                    overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(3.dp))
                Text("${entry.sections} • ${entry.identity.substringAfterLast(':')}",
                    color = owGold, fontSize = 10.sp, maxLines = 1)
                Text("ต้องใช้ ${entry.requested} • มี ${entry.owned} • ขาด ${entry.missing}",
                    color = if (entry.missing > 0) Color(0xFFFFB0B0) else owGreen,
                    fontWeight = FontWeight.Bold, fontSize = 11.sp)
                if (entry.allArtworkCount > 1) {
                    Text("นับรวม ${entry.allArtworkCount} Artwork ที่ใช้ Card ID เดียวกัน",
                        color = owSecondary, fontSize = 10.sp, maxLines = 1)
                }
                if (entry.ownedArtworkDetails.isNotBlank()) {
                    Text(entry.ownedArtworkDetails, color = owSecondary,
                        fontSize = 9.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                if (entry.unknown) {
                    Text("Card Num not found in catalog", color = owRed, fontSize = 10.sp)
                }
                if (entry.missing > 0 && entry.preferred != null) {
                    TextButton(onClick = onWishlist, contentPadding = PaddingValues(0.dp)) {
                        Icon(Icons.Default.FavoriteBorder, null, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("ADD WISHLIST", fontSize = 10.sp)
                    }
                } else if (entry.missing == 0) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CheckCircle, null, tint = owGreen,
                            modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("EVIDENCE SECURED", color = owGreen, fontSize = 10.sp)
                    }
                }
            }
        }
    }
}

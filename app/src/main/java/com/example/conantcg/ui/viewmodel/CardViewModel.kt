package com.example.conantcg.ui.viewmodel

import android.app.Application
import android.content.Context
import androidx.compose.runtime.*
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.conantcg.R
import com.example.conantcg.data.model.*
import com.example.conantcg.data.repository.CardRepository
import kotlinx.coroutines.*
import org.json.JSONObject
import org.jsoup.Jsoup
import java.io.File
import java.net.URL

class CardViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = CardRepository(app)

    // --- 1. Data States ---
    var cardList = mutableStateListOf<WebCard>()
    var isLoading by mutableStateOf(true)
    var statusText by mutableStateOf("Init...")
    var progress by mutableFloatStateOf(0f)

    var isUpdatingData by mutableStateOf(false)
        private set

    // --- 2. Collection States ---
    var collectedCounts = mutableStateMapOf<String, Int>()

    // --- 3. Deck Builder States ---
    var allDecks = mutableStateListOf<Deck>()
    var activeDeckId by mutableStateOf<String?>(null)
    var deckMap = mutableStateMapOf<String, Int>()
    var partnerCard = mutableStateOf<String?>(null)
    var caseCard = mutableStateOf<String?>(null)

    // --- 4. Image & Download ---
    var downloadedPacks = mutableStateListOf<String>()
    var downloadingPacks = mutableStateMapOf<String, Float>()

    // ✅ --- 5. Wishlist States ---
    var wishlistedCards = mutableStateListOf<String>()

    // Helper property
    val activeDeck: Deck? get() = allDecks.find { it.id == activeDeckId }

    init {
        viewModelScope.launch {
            collectedCounts.putAll(repo.loadCollection())
            allDecks.addAll(repo.loadDecks())

            val prefs = getApplication<Application>().getSharedPreferences("Pref", Context.MODE_PRIVATE)
            val savedPacks = prefs.getStringSet("dl_packs", emptySet()) ?: emptySet()
            downloadedPacks.addAll(savedPacks)

            // ✅ โหลดข้อมูล Wishlist ตอนเปิดแอป
            val savedWishlist = prefs.getStringSet("wishlist_cards", emptySet()) ?: emptySet()
            wishlistedCards.addAll(savedWishlist)

            refreshData(force = false)
        }
    }

    // --- Master Data Logic ---
    fun refreshData(force: Boolean = false) {
        if (isUpdatingData) return

        viewModelScope.launch {
            try {
                isUpdatingData = true
                val prefs = getApplication<Application>().getSharedPreferences("Pref", Context.MODE_PRIVATE)
                val lastUpdate = prefs.getLong("last_card_update", 0L)
                val currentTime = System.currentTimeMillis()
                val oneDayMs = 24 * 60 * 60 * 1000L
                val isCacheExpired = (currentTime - lastUpdate) > oneDayMs
                val shouldFetchFromWeb = force || isCacheExpired

                if (cardList.isEmpty() || shouldFetchFromWeb) {
                    isLoading = true
                    val res = repo.getCards(shouldFetchFromWeb) { p, s ->
                        progress = p
                        statusText = s
                    }
                    if (res.isNotEmpty()) {
                        cardList.clear()
                        cardList.addAll(res)
                        if (shouldFetchFromWeb) prefs.edit().putLong("last_card_update", currentTime).apply()
                    }
                    isLoading = false
                }
            } finally {
                isUpdatingData = false
            }
        }
    }

    // --- Collection Logic (Auto-Save) ---

    fun updateCollectionCount(id: String, qty: Int) {
        if (qty <= 0) {
            collectedCounts.remove(id)
        } else {
            collectedCounts[id] = qty
        }
        repo.saveCollection(collectedCounts)
    }

    fun setCardQuantity(id: String, qty: Int) {
        updateCollectionCount(id, qty)
    }

    fun incrementCard(id: String) {
        val current = collectedCounts[id] ?: 0
        updateCollectionCount(id, current + 1)
    }

    fun decrementCard(id: String) {
        val current = collectedCounts[id] ?: 0
        if(current > 0) {
            updateCollectionCount(id, current - 1)
        }
    }

    fun getQuantity(id: String) = collectedCounts[id] ?: 0

    // ✅ --- Wishlist Logic ---

    fun isWishlisted(id: String): Boolean {
        return wishlistedCards.contains(id)
    }

    fun toggleWishlist(id: String) {
        if (wishlistedCards.contains(id)) {
            wishlistedCards.remove(id)
        } else {
            wishlistedCards.add(id)
        }
        // บันทึกลงเครื่องทันที
        val prefs = getApplication<Application>().getSharedPreferences("Pref", Context.MODE_PRIVATE)
        prefs.edit().putStringSet("wishlist_cards", wishlistedCards.toSet()).apply()
    }

    // --- Deck Logic ---

    fun createNewDeck(name: String) {
        val d = Deck(id = System.currentTimeMillis().toString(), name = name)
        allDecks.add(0, d)
        repo.saveDecks(allDecks)
        setActiveDeck(d.id)
    }

    fun deleteDeck(id: String) {
        allDecks.removeIf { it.id == id }
        repo.saveDecks(allDecks)
    }

    fun setActiveDeck(id: String) {
        allDecks.find { it.id == id }?.let {
            activeDeckId = id
            deckMap.clear()
            deckMap.putAll(it.cards)
            partnerCard.value = it.partner
            caseCard.value = it.case
        }
    }

    fun getActiveDeckName() = allDecks.find { it.id == activeDeckId }?.name ?: ""

    private fun syncDeck() {
        activeDeckId?.let { id ->
            val index = allDecks.indexOfFirst { it.id == id }
            if (index != -1) {
                val currentDeck = allDecks[index]
                val updatedDeck = currentDeck.copy(
                    cards = deckMap.toMap().toMutableMap(),
                    partner = partnerCard.value,
                    case = caseCard.value
                )
                allDecks[index] = updatedDeck
                repo.saveDecks(allDecks)
            }
        }
    }

    fun addToDeck(id: String) {
        val c = cardList.find { it.cardNum == id } ?: return
        if (c.type.contains("Partner") || c.type.contains("Case") || c.type.contains("事件")) return

        val sameIdCount = deckMap.entries.sumOf { (k, v) ->
            if (cardList.find { it.cardNum == k }?.cardId == c.cardId) v else 0
        }
        if (sameIdCount < 3) {
            deckMap[id] = (deckMap[id] ?: 0) + 1
            syncDeck()
        }
    }

    fun removeFromDeck(id: String) {
        val c = deckMap[id] ?: 0
        if(c > 0) {
            if(c == 1) deckMap.remove(id) else deckMap[id] = c - 1
            syncDeck()
        }
    }

    fun setPartner(id: String) {
        partnerCard.value = id
        syncDeck()
    }

    fun setCase(id: String) {
        caseCard.value = id
        syncDeck()
    }

    fun clearDeck() {
        deckMap.clear()
        partnerCard.value = null
        caseCard.value = null
        syncDeck()
    }

    // --- Import Logic ---
    fun importDeckFromCode(input: String, cb: (Boolean, String) -> Unit) = viewModelScope.launch(Dispatchers.IO) {
        try {
            val code = if (input.startsWith("http")) input.substringAfter("d_code=").substringBefore("&") else input
            val url = "https://www.takaratomy.co.jp/products/conan-cardgame/deckrecipe/create?d_code=$code"
            val doc = Jsoup.connect(url).timeout(10000).get()
            val deckJsonStr = doc.select("input[name=deck]").attr("value")

            if (deckJsonStr.isBlank()) {
                withContext(Dispatchers.Main) { cb(false, "No deck data found") }
                return@launch
            }

            val json = JSONObject(deckJsonStr)
            val partnerId = json.optJSONArray("partner")?.optString(0)
            val caseId = json.optJSONArray("incident")?.optString(0)
            val otherArray = json.optJSONArray("other")
            val map = mutableMapOf<String, Int>()

            if (otherArray != null) {
                for (i in 0 until otherArray.length()) {
                    val id = otherArray.getString(i)
                    if (id.isNotBlank()) map[id] = (map[id] ?: 0) + 1
                }
            }

            val newDeck = Deck(
                id = System.currentTimeMillis().toString(),
                name = "Imported ($code)",
                cards = map,
                partner = if (partnerId.isNullOrBlank()) null else partnerId,
                case = if (caseId.isNullOrBlank()) null else caseId
            )

            withContext(Dispatchers.Main) {
                allDecks.add(0, newDeck)
                repo.saveDecks(allDecks)
                cb(true, "Import Successful!")
            }
        } catch (e: Exception) {
            e.printStackTrace()
            withContext(Dispatchers.Main) { cb(false, "Import Error: ${e.message}") }
        }
    }

    // --- Image & Helpers ---
    fun isPackDownloaded(p: String) = downloadedPacks.contains(p)

    fun getPackStats(p: String): PackStats {
        val cards = cardList.filter { it.pack == p }
        val owned = cards.count { collectedCounts.containsKey(it.cardNum) }
        val rMap = cards.groupBy { it.rarity }.mapValues { (_, v) -> v.count { collectedCounts.containsKey(it.cardNum) } to v.size }
        return PackStats(cards.size, owned, rMap)
    }

    fun getCardImageModel(c: WebCard): Any {
        if (isPackDownloaded(c.pack)) {
            val f = File(getApplication<Application>().filesDir, "cards/${c.pack.replace(Regex("[^a-zA-Z0-9]"),"_")}/${c.cardNum.replace("/","_")}.jpg")
            if (f.exists()) return f
        }
        return c.imageUrl
    }

    fun getPackCoverImage(packName: String): Any {
        val isPrPack = packName.contains("Promo", ignoreCase = true) || packName.contains("PR", ignoreCase = true)
        return when {
            packName.contains("CT-D01") -> R.drawable.sd01
            packName.contains("CT-D02") -> R.drawable.sd02
            packName.contains("CT-D03") -> R.drawable.sd03
            packName.contains("CT-D04") -> R.drawable.sd04
            packName.contains("CT-D05") -> R.drawable.sd05
            packName.contains("CT-D06") -> R.drawable.sd06
            packName.contains("CT-D07") -> R.drawable.sd07
            packName.contains("CT-D08") -> R.drawable.sd08
            packName.contains("CT-D09") -> R.drawable.sd09
            packName.contains("CT-D10") -> R.drawable.sd10
            packName.contains("CT-D11") -> R.drawable.sd11
            packName.contains("CT-P01") -> R.drawable.bt01
            packName.contains("CT-P02") -> R.drawable.bt02
            packName.contains("CT-P03") -> R.drawable.bt03
            packName.contains("CT-P04") -> R.drawable.bt04
            packName.contains("CT-P05") -> R.drawable.bt05
            packName.contains("CT-P06") -> R.drawable.bt06
            packName.contains("CT-P07") -> R.drawable.bt07
            packName.contains("CT-P08") -> R.drawable.bt08
            packName.contains("CT-P09") -> R.drawable.bt09
            packName.contains("CT-P10") -> R.drawable.bt10

            isPrPack -> R.drawable.pr
            else -> R.drawable.ic_launcher_foreground
        }
    }

    fun downloadPack(pack: String) = viewModelScope.launch(Dispatchers.IO) {
        if(downloadingPacks.containsKey(pack)) return@launch
        withContext(Dispatchers.Main){ downloadingPacks[pack]=0f }
        val cards = cardList.filter { it.pack==pack }
        val dir = File(getApplication<Application>().filesDir, "cards/${pack.replace(Regex("[^a-zA-Z0-9]"),"_")}")
        dir.mkdirs()
        cards.forEachIndexed { i, c ->
            try { if(c.imageUrl.isNotEmpty()) { val f = File(dir,"${c.cardNum.replace("/","_")}.jpg"); if(!f.exists()) f.writeBytes(URL(c.imageUrl).readBytes()) } } catch(e:Exception){}
            withContext(Dispatchers.Main){ downloadingPacks[pack] = (i+1f)/cards.size }
        }
        withContext(Dispatchers.Main){
            downloadingPacks.remove(pack)
            if (!downloadedPacks.contains(pack)) downloadedPacks.add(pack)
            getApplication<Application>().getSharedPreferences("Pref", Context.MODE_PRIVATE).edit().putStringSet("dl_packs", downloadedPacks.toSet()).apply()
        }
    }

    // --- Helpers for Deck Builder Screens ---
    fun generateHandTest(d: Deck?): List<WebCard> {
        val src = d?.cards ?: deckMap
        return src.flatMap { (k,v) -> List(v){ cardList.find{it.cardNum==k} } }.filterNotNull().shuffled().take(5)
    }

    fun generateHandWithHolds(d: Deck, curr: List<WebCard>, holds: Set<Int>): List<WebCard> {
        val all = d.cards.flatMap { (k,v) -> List(v){ cardList.find{it.cardNum==k} } }.filterNotNull().toMutableList()
        val kept = holds.mapNotNull { if(it<curr.size) curr[it] else null }
        kept.forEach { k -> all.remove(k) }
        val newHand = MutableList<WebCard?>(5){null}
        holds.forEach { if(it<curr.size) newHand[it]=curr[it] }
        val pool = all.shuffled().iterator()
        return newHand.map { it ?: if(pool.hasNext()) pool.next() else null }.filterNotNull()
    }

    fun renameActiveDeck(newName: String) {
        val currentId = activeDeckId ?: return
        val index = allDecks.indexOfFirst { it.id == currentId }
        if (index != -1) {
            allDecks[index] = allDecks[index].copy(name = newName)
            repo.saveDecks(allDecks)
        }
    }

    fun duplicateCurrentDeck() {
        val currentName = getActiveDeckName()
        val baseName = if (currentName.endsWith("(Copy)")) currentName.substringBeforeLast("(Copy)").trim() else currentName
        val newName = "$baseName (Copy)"

        val copiedDeckMap = deckMap.toMap().toMutableMap()
        val copiedPartner = partnerCard.value
        val copiedCase = caseCard.value

        val duplicatedDeck = Deck(
            id = System.currentTimeMillis().toString(),
            name = newName,
            cards = copiedDeckMap,
            partner = copiedPartner,
            case = copiedCase
        )

        allDecks.add(0, duplicatedDeck)
        repo.saveDecks(allDecks)
    }

    fun saveBitmapToGallery(context: Context, bitmap: android.graphics.Bitmap) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val filename = "DeckRecipe_${System.currentTimeMillis()}.png"
                val resolver = context.contentResolver
                val contentValues = android.content.ContentValues().apply {
                    put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, filename)
                    put(android.provider.MediaStore.MediaColumns.MIME_TYPE, "image/png")
                    put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, android.os.Environment.DIRECTORY_PICTURES + "/ConanTCG")
                }

                val uri = resolver.insert(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
                if (uri != null) {
                    resolver.openOutputStream(uri)?.use { outputStream ->
                        bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, outputStream)
                    }
                    withContext(Dispatchers.Main) {
                        android.widget.Toast.makeText(context, "Deck recipe saved to Gallery!", android.widget.Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    android.widget.Toast.makeText(context, "Failed to save image.", android.widget.Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}
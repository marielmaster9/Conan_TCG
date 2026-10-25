package com.example.conantcg.ui.screens

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.util.AtomicFile
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.conantcg.R
import com.example.conantcg.data.model.Deck
import com.example.conantcg.ui.viewmodel.CardViewModel
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ============================================================
// BACKUP DATA MODEL + ENGINE
// Kept IN THIS FILE so ConanBackupData and ConanBackupEngine
// remain accessible to the existing Vault UI.
// ============================================================

private data class ConanBackupData(
    val createdAt: Long,
    val collection: Map<String, Int>,
    val decks: List<Deck>,
    val folders: List<String>,
    val wishlist: Map<String, Set<String>>
) {
    val cardTypes: Int get() = collection.size
    val cardCopies: Long get() = collection.values.sumOf { it.toLong() }
    val wishlistEntries: Int get() = wishlist.values.sumOf { it.size }
}

private object ConanBackupEngine {
    private const val FILE_FORMAT = "conan_tcg_backup"
    private const val FILE_VERSION = 1
    private const val MAX_BACKUP_SIZE = 10 * 1024 * 1024
    private const val COLLECTION_FILE = "user_collection.json"
    private const val DECKS_FILE = "user_decks.json"
    private const val RECOVERY_FILE = "conan_pre_restore_backup.json"
    private const val PREF_NAME = "Pref"
    private val gson = Gson()

    // Always call capture on Main thread: reading Compose snapshot state.
    fun capture(vm: CardViewModel): ConanBackupData {
        val folderNames = vm.wishlistFolders.toList()
        return ConanBackupData(
            createdAt = System.currentTimeMillis(),
            collection = vm.collectedCounts.toMap(),
            decks = vm.allDecks.map { deck ->
                deck.copy(cards = deck.cards.toMutableMap())
            },
            folders = folderNames,
            wishlist = folderNames.associateWith { folder ->
                vm.wishlistedCardsMap[folder]?.toSet() ?: emptySet()
            }
        )
    }

    fun encode(data: ConanBackupData): String {
        val collectionJson = JSONObject()
        data.collection.forEach { (cardNum, quantity) ->
            collectionJson.put(cardNum, quantity)
        }

        val deckArray = JSONArray()
        data.decks.forEach { deckArray.put(it.toJson()) }

        val folderArray = JSONArray()
        val contents = JSONObject()
        data.folders.forEach { folder ->
            folderArray.put(folder)
            val cards = JSONArray()
            data.wishlist[folder].orEmpty().sorted().forEach { cards.put(it) }
            contents.put(folder, cards)
        }

        val wishlistJson = JSONObject()
            .put("folders", folderArray)
            .put("cardsByFolder", contents)

        val root = JSONObject()
            .put("format", FILE_FORMAT)
            .put("version", FILE_VERSION)
            .put("createdAt", data.createdAt)
            .put("collection", collectionJson)
            .put("decks", deckArray)
            .put("wishlist", wishlistJson)

        val result = root.toString()
        require(result.toByteArray(Charsets.UTF_8).size <= MAX_BACKUP_SIZE) {
            "Backup exceeds 10 MB"
        }
        return result
    }

    fun decode(json: String): ConanBackupData {
        require(json.toByteArray(Charsets.UTF_8).size <= MAX_BACKUP_SIZE) {
            "Backup exceeds 10 MB"
        }
        val root = JSONObject(json)
        require(root.optString("format") == FILE_FORMAT) {
            "This is not a Conan TCG backup file"
        }
        require(root.optInt("version", -1) == FILE_VERSION) {
            "Unsupported Conan TCG backup version"
        }
        val timestamp = root.getLong("createdAt")
        require(timestamp > 0L) { "Invalid backup timestamp" }

        val collection = readPositiveCounts(root.getJSONObject("collection"))
        require(collection.size <= 50_000) { "Collection is too large" }

        val deckArray = root.getJSONArray("decks")
        require(deckArray.length() <= 10_000) { "Too many decks" }
        val decks = mutableListOf<Deck>()
        val deckIds = mutableSetOf<String>()
        for (i in 0 until deckArray.length()) {
            val item = deckArray.getJSONObject(i)
            val id = item.getString("id")
            val name = item.getString("name")
            require(id.isNotBlank() && id.length <= 200 && deckIds.add(id)) {
                "Invalid or duplicate deck ID"
            }
            require(name.isNotBlank() && name.length <= 500) {
                "Invalid deck name"
            }
            val cards = readPositiveCounts(item.getJSONObject("cards"))
            require(cards.size <= 10_000) { "Deck is too large" }
            decks.add(Deck.fromJson(item).copy(cards = cards.toMutableMap()))
        }

        val wishlistJson = root.getJSONObject("wishlist")
        val folderArray = wishlistJson.getJSONArray("folders")
        val folderContents = wishlistJson.getJSONObject("cardsByFolder")
        require(folderArray.length() in 1..1_000) {
            "Invalid number of wishlist folders"
        }
        val folders = mutableListOf<String>()
        for (i in 0 until folderArray.length()) {
            val name = folderArray.getString(i)
            require(name.isNotBlank() && name.length <= 150 && name !in folders) {
                "Invalid or duplicate wishlist folder"
            }
            folders.add(name)
        }
        require("Default" in folders) { "Missing Default wishlist folder" }
        for (key in folderContents.keys()) {
            require(key in folders) { "Unexpected wishlist folder" }
        }
        val wishlist = linkedMapOf<String, Set<String>>()
        for (folder in folders) {
            val cardsArray = folderContents.getJSONArray(folder)
            require(cardsArray.length() <= 50_000) { "Wishlist is too large" }
            val codes = linkedSetOf<String>()
            for (i in 0 until cardsArray.length()) {
                val number = cardsArray.getString(i)
                require(number.isNotBlank() && number.length <= 200) {
                    "Invalid wishlist card number"
                }
                codes.add(number)
            }
            wishlist[folder] = codes
        }
        return ConanBackupData(timestamp, collection, decks, folders, wishlist)
    }

    private fun readPositiveCounts(json: JSONObject): Map<String, Int> {
        val result = linkedMapOf<String, Int>()
        for (cardNum in json.keys()) {
            require(cardNum.isNotBlank() && cardNum.length <= 200) {
                "Invalid card number"
            }
            // Ints only. Do not accept floating point or negative quantities.
            val quantity = json.get(cardNum).toString().toIntOrNull()
            require(quantity != null && quantity > 0) {
                "Invalid quantity for $cardNum"
            }
            result[cardNum] = quantity
        }
        return result
    }

    fun saveDocument(context: Context, uri: Uri, json: String) {
        val stream = context.contentResolver.openOutputStream(uri, "wt")
            ?: throw IOException("Unable to open backup destination")
        stream.bufferedWriter(Charsets.UTF_8).use { it.write(json) }
    }

    fun openDocument(context: Context, uri: Uri): ConanBackupData {
        val input = context.contentResolver.openInputStream(uri)
            ?: throw IOException("Unable to open backup file")
        val data = input.use { readLimited(it) }
        return decode(data.toString(Charsets.UTF_8))
    }

    private fun readLimited(input: InputStream): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        var total = 0
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            if (count == 0) continue
            total += count
            require(total <= MAX_BACKUP_SIZE) { "Backup file is too large" }
            output.write(buffer, 0, count)
        }
        return output.toByteArray()
    }

    fun hasRecovery(context: Context): Boolean =
        File(context.filesDir, RECOVERY_FILE).exists()

    fun readRecovery(context: Context): ConanBackupData {
        val file = File(context.filesDir, RECOVERY_FILE)
        require(file.exists()) { "No previous restore backup was found" }
        val bytes = file.inputStream().use { readLimited(it) }
        return decode(bytes.toString(Charsets.UTF_8))
    }

    // Backup the old state first, write the two JSON files atomically,
    // then commit SharedPreferences. Attempt rollback on failure.
    // Individual files are atomic; the entire multi-file operation isn't.
    fun persistRestore(
        context: Context,
        replacement: ConanBackupData,
        previous: ConanBackupData
    ) {
        val collectionFile = File(context.filesDir, COLLECTION_FILE)
        val decksFile = File(context.filesDir, DECKS_FILE)
        val recoveryFile = File(context.filesDir, RECOVERY_FILE)
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

        val oldCollection = collectionFile.takeIf { it.exists() }?.readBytes()
        val oldDecks = decksFile.takeIf { it.exists() }?.readBytes()
        val oldPrefs = prefs.all.filterKeys { isWishlistPreference(it) }
            .mapValues { (_, v) ->
                if (v is Set<*>) v.filterIsInstance<String>().toSet() else v
            }

        // If this write fails, no user files have been changed.
        writeAtomic(recoveryFile, encode(previous).toByteArray(Charsets.UTF_8))
        try {
            writeAtomic(
                collectionFile,
                gson.toJson(replacement.collection).toByteArray(Charsets.UTF_8)
            )
            writeAtomic(
                decksFile,
                gson.toJson(replacement.decks).toByteArray(Charsets.UTF_8)
            )
            saveWishlistPreferences(prefs, replacement)
        } catch (failure: Exception) {
            runCatching { rollbackFile(collectionFile, oldCollection) }
                .onFailure { failure.addSuppressed(it) }
            runCatching { rollbackFile(decksFile, oldDecks) }
                .onFailure { failure.addSuppressed(it) }
            runCatching { restoreWishlistPreferences(prefs, oldPrefs) }
                .onFailure { failure.addSuppressed(it) }
            throw IOException(
                if (failure.suppressed.isEmpty()) {
                    "Restore failed. Original data was rolled back."
                } else {
                    "Restore failed; automatic rollback may be incomplete. Use the recovery file."
                },
                failure
            )
        }
    }

    private fun writeAtomic(file: File, bytes: ByteArray) {
        val atomic = AtomicFile(file)
        val output = atomic.startWrite()
        try {
            output.write(bytes)
            atomic.finishWrite(output)
        } catch (failure: Exception) {
            atomic.failWrite(output)
            throw failure
        }
    }

    private fun rollbackFile(file: File, original: ByteArray?) {
        if (original == null) {
            if (file.exists() && !file.delete()) {
                throw IOException("Unable to remove newly restored file")
            }
        } else {
            writeAtomic(file, original)
        }
    }

    private fun isWishlistPreference(key: String): Boolean =
        key == "wishlist_folders" ||
                key == "wishlist_folders_order" ||
                key == "wishlist_folders_json" ||
                key == "wishlist_cards" ||
                key.startsWith("wishlist_cards_")

    private fun saveWishlistPreferences(
        prefs: SharedPreferences,
        replacement: ConanBackupData
    ) {
        val editor = prefs.edit()
        prefs.all.keys.filter { isWishlistPreference(it) }.forEach { editor.remove(it) }
        editor.putString("wishlist_folders_order", replacement.folders.joinToString(","))
        editor.putString("wishlist_folders_json", JSONArray(replacement.folders).toString())
        editor.putStringSet("wishlist_folders", replacement.folders.toSet())
        replacement.folders.forEach { folder ->
            editor.putStringSet("wishlist_cards_$folder", replacement.wishlist[folder].orEmpty().toSet())
        }
        if (!editor.commit()) throw IOException("Failed to persist wishlist")
    }

    private fun restoreWishlistPreferences(
        prefs: SharedPreferences,
        previous: Map<String, Any?>
    ) {
        val editor = prefs.edit()
        prefs.all.keys.filter { isWishlistPreference(it) }.forEach { editor.remove(it) }
        previous.forEach { (key, value) ->
            when (value) {
                is String -> editor.putString(key, value)
                is Set<*> -> editor.putStringSet(key, value.filterIsInstance<String>().toSet())
                null -> editor.remove(key)
                else -> throw IOException("Unsupported preference value for $key")
            }
        }
        if (!editor.commit()) throw IOException("Failed to restore wishlist settings")
    }

    // This is called on Main thread AFTER all files have been persisted.
    fun applyToViewModel(vm: CardViewModel, data: ConanBackupData) {
        vm.collectedCounts.clear()
        vm.collectedCounts.putAll(data.collection)
        vm.allDecks.clear()
        vm.allDecks.addAll(data.decks.map { it.copy(cards = it.cards.toMutableMap()) })
        vm.activeDeckId = null
        vm.deckMap.clear()
        vm.partnerCard.value = null
        vm.caseCard.value = null
        vm.wishlistFolders.clear()
        vm.wishlistFolders.addAll(data.folders)
        vm.wishlistedCardsMap.clear()
        data.folders.forEach { folder ->
            vm.wishlistedCardsMap[folder] = data.wishlist[folder].orEmpty().toMutableSet()
        }
        vm.currentWishlistFolder = "Default"
    }

    fun dateString(timestamp: Long): String = SimpleDateFormat(
        "dd MMM yyyy HH:mm", Locale.getDefault()
    ).format(Date(timestamp))
}

private object VaultColors {
    val background = Color(0xFF0C1019)
    val surface = Color(0xFF141C2C)
    val surfaceRaised = Color(0xFF1C2940)
    val border = Color(0xFF33435A)
    val blue = Color(0xFF3389E5)
    val red = Color(0xFFE53935)
    val gold = Color(0xFFF5C453)
    val green = Color(0xFF61D3AB)
    val white = Color(0xFFF4F7FC)
    val muted = Color(0xFFBAC7D9)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupRestoreScreen(
    viewModel: CardViewModel,
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHost = remember { SnackbarHostState() }

    var busy by remember { mutableStateOf(false) }
    var exportJson by remember { mutableStateOf<String?>(null) }
    var pendingRestore by remember { mutableStateOf<ConanBackupData?>(null) }
    var hasRecovery by remember {
        mutableStateOf(ConanBackupEngine.hasRecovery(context))
    }
    var lastSavedAt by remember { mutableStateOf<Long?>(null) }

    val createDocument = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        val json = exportJson
        exportJson = null
        if (uri != null && json != null) {
            scope.launch {
                busy = true
                try {
                    withContext(Dispatchers.IO) {
                        ConanBackupEngine.saveDocument(context, uri, json)
                    }
                    lastSavedAt = System.currentTimeMillis()
                    snackbarHost.showSnackbar("Case file secured. Backup saved!")
                } catch (error: Exception) {
                    snackbarHost.showSnackbar("Backup failed: ${error.message}")
                } finally {
                    busy = false
                }
            }
        }
    }

    val openDocument = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                busy = true
                try {
                    pendingRestore = withContext(Dispatchers.IO) {
                        ConanBackupEngine.openDocument(context, uri)
                    }
                } catch (error: Exception) {
                    snackbarHost.showSnackbar("Invalid case file: ${error.message}")
                } finally {
                    busy = false
                }
            }
        }
    }

    fun beginBackup() {
        if (busy) return
        try {
            exportJson = ConanBackupEngine.encode(
                ConanBackupEngine.capture(viewModel)
            )
            val stamp = SimpleDateFormat(
                "yyyyMMdd_HHmmss", Locale.US
            ).format(Date())
            createDocument.launch("ConanTCG_CaseFile_$stamp.json")
        } catch (error: Exception) {
            scope.launch {
                snackbarHost.showSnackbar("Backup error: ${error.message}")
            }
        }
    }

    val collected = viewModel.collectedCounts.size
    val copies = viewModel.collectedCounts.values.sumOf { it.toLong() }
    val decks = viewModel.allDecks.size
    val folders = viewModel.wishlistFolders.size

    Scaffold(
        containerColor = VaultColors.background,
        snackbarHost = { SnackbarHost(snackbarHost) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            "CASE FILE VAULT",
                            color = VaultColors.white,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 1.sp
                        )
                        Text(
                            "DETECTIVE CONAN TCG  /  ARCHIVE 001",
                            color = VaultColors.gold,
                            fontSize = 9.sp,
                            letterSpacing = 0.7.sp
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick, enabled = !busy) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = VaultColors.white
                        )
                    }
                },
                actions = {
                    Icon(
                        Icons.Default.Shield,
                        contentDescription = null,
                        tint = VaultColors.gold,
                        modifier = Modifier.padding(end = 18.dp).size(22.dp)
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = VaultColors.background
                )
            )
        }
    ) { insets ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(insets)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            VaultHeroBanner()

            VaultSectionHeading(
                number = "01",
                title = "EVIDENCE INVENTORY",
                subtitle = "YOUR SAVED DATA",
                color = VaultColors.gold
            )

            Card(
                colors = CardDefaults.cardColors(
                    containerColor = VaultColors.surface
                ),
                border = BorderStroke(1.dp, VaultColors.border),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(Modifier.padding(14.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            Icons.Default.FolderSpecial,
                            contentDescription = null,
                            tint = VaultColors.gold,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "EVIDENCE FILES",
                            color = VaultColors.white,
                            fontWeight = FontWeight.Black,
                            fontSize = 12.sp,
                            letterSpacing = 1.sp,
                            modifier = Modifier.weight(1f)
                        )
                        VaultStamp("INDEXED", VaultColors.green)
                    }
                    Spacer(Modifier.height(14.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                        VaultDataTile(
                            modifier = Modifier.weight(1f),
                            number = collected.toString(),
                            label = "CARD TYPES",
                            icon = Icons.Default.Folder,
                            accent = VaultColors.blue
                        )
                        VaultDataTile(
                            modifier = Modifier.weight(1f),
                            number = copies.toString(),
                            label = "TOTAL COPIES",
                            icon = Icons.Default.Layers,
                            accent = VaultColors.red
                        )
                    }
                    Spacer(Modifier.height(9.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                        VaultDataTile(
                            modifier = Modifier.weight(1f),
                            number = decks.toString(),
                            label = "DECK FILES",
                            icon = Icons.Default.Style,
                            accent = VaultColors.gold
                        )
                        VaultDataTile(
                            modifier = Modifier.weight(1f),
                            number = folders.toString(),
                            label = "WISHLIST FOLDERS",
                            icon = Icons.Default.Favorite,
                            accent = VaultColors.green
                        )
                    }
                }
            }

            VaultSectionHeading(
                number = "02",
                title = "SEAL THE CASE",
                subtitle = "BACKUP OPERATION",
                color = VaultColors.blue
            )

            VaultOperationCard(
                caseNumber = "CASE ACTION / B-001",
                title = "Preserve the Evidence",
                body = "Archive your Collection, Decks and Wishlist into one portable JSON case file.",
                accent = VaultColors.blue,
                icon = Icons.Default.CloudUpload,
                buttonIcon = Icons.Default.SaveAlt,
                buttonLabel = "CREATE BACKUP FILE",
                filled = true,
                enabled = !busy,
                onClick = { beginBackup() }
            )

            lastSavedAt?.let { whenSaved ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = VaultColors.green,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(Modifier.width(7.dp))
                    Text(
                        "Saved this session: ${ConanBackupEngine.dateString(whenSaved)}",
                        color = VaultColors.green,
                        fontSize = 11.sp
                    )
                }
            }

            VaultSectionHeading(
                number = "03",
                title = "REOPEN THE CASE",
                subtitle = "RESTORE OPERATION",
                color = VaultColors.gold
            )

            VaultOperationCard(
                caseNumber = "CASE ACTION / R-002",
                title = "Recover the Evidence",
                body = "Open an existing Conan TCG backup. The evidence is verified before any data is replaced.",
                accent = VaultColors.gold,
                icon = Icons.Default.History,
                buttonIcon = Icons.Default.FolderOpen,
                buttonLabel = "SELECT CASE FILE",
                filled = false,
                enabled = !busy,
                onClick = { openDocument.launch(arrayOf("*/*")) }
            )

            if (hasRecovery) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = VaultColors.surfaceRaised
                    ),
                    border = BorderStroke(1.dp, VaultColors.blue),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Restore,
                            contentDescription = null,
                            tint = VaultColors.blue
                        )
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                "PREVIOUS CASE FOUND",
                                color = VaultColors.white,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                            Text(
                                "Data before the previous restore",
                                color = VaultColors.muted,
                                fontSize = 10.sp
                            )
                        }
                        TextButton(
                            enabled = !busy,
                            onClick = {
                                scope.launch {
                                    busy = true
                                    try {
                                        pendingRestore = withContext(Dispatchers.IO) {
                                            ConanBackupEngine.readRecovery(context)
                                        }
                                    } catch (error: Exception) {
                                        snackbarHost.showSnackbar(
                                            "Recovery error: ${error.message}"
                                        )
                                    } finally {
                                        busy = false
                                    }
                                }
                            }
                        ) {
                            Text(
                                "OPEN",
                                color = VaultColors.gold,
                                fontWeight = FontWeight.Black
                            )
                        }
                    }
                }
            }

            VaultSecurityNotice()

            if (busy) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth(),
                    color = VaultColors.gold,
                    trackColor = VaultColors.surfaceRaised
                )
            }

            Spacer(Modifier.height(20.dp))
        }
    }

    pendingRestore?.let { backup ->
        AlertDialog(
            onDismissRequest = { if (!busy) pendingRestore = null },
            containerColor = VaultColors.surface,
            titleContentColor = VaultColors.white,
            textContentColor = VaultColors.muted,
            shape = RoundedCornerShape(18.dp),
            icon = {
                Icon(
                    Icons.Default.WarningAmber,
                    contentDescription = null,
                    tint = VaultColors.gold
                )
            },
            title = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("CASE REOPEN REQUEST", fontWeight = FontWeight.Black)
                    Text(
                        "CONFIRM RESTORE",
                        color = VaultColors.gold,
                        fontSize = 11.sp,
                        letterSpacing = 1.1.sp
                    )
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "ARCHIVED: ${ConanBackupEngine.dateString(backup.createdAt)}",
                        color = VaultColors.gold,
                        fontSize = 11.sp
                    )
                    HorizontalDivider(color = VaultColors.border)
                    VaultPreviewRow("Card types", backup.cardTypes.toString())
                    VaultPreviewRow("Card copies", backup.cardCopies.toString())
                    VaultPreviewRow("Deck files", backup.decks.size.toString())
                    VaultPreviewRow("Wishlist folders", backup.folders.size.toString())
                    VaultPreviewRow("Wishlist entries", backup.wishlistEntries.toString())
                    HorizontalDivider(color = VaultColors.border)
                    Text(
                        "WARNING: Restoring replaces the current Collection, Decks and Wishlist. A local recovery copy is created first.",
                        color = Color(0xFFFF9690),
                        fontSize = 12.sp
                    )
                }
            },
            confirmButton = {
                Button(
                    enabled = !busy,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = VaultColors.red
                    ),
                    onClick = {
                        val previous = ConanBackupEngine.capture(viewModel)
                        scope.launch {
                            busy = true
                            try {
                                withContext(Dispatchers.IO) {
                                    ConanBackupEngine.persistRestore(
                                        context = context,
                                        replacement = backup,
                                        previous = previous
                                    )
                                }
                                ConanBackupEngine.applyToViewModel(viewModel, backup)
                                hasRecovery = true
                                pendingRestore = null
                                snackbarHost.showSnackbar("Case reopened. Restore complete!")
                            } catch (error: Exception) {
                                snackbarHost.showSnackbar(
                                    "Restore failed: ${error.message}"
                                )
                            } finally {
                                busy = false
                            }
                        }
                    }
                ) {
                    Text("RESTORE NOW", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    enabled = !busy,
                    onClick = { pendingRestore = null }
                ) {
                    Text("CANCEL", color = VaultColors.white)
                }
            }
        )
    }
}

@Composable
private fun VaultHeroBanner() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(192.dp)
            .clip(RoundedCornerShape(18.dp))
            .border(1.dp, VaultColors.border, RoundedCornerShape(18.dp))
    ) {
        Image(
            painter = painterResource(R.drawable.banner),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize().alpha(0.52f)
        )
        Box(
            Modifier.fillMaxSize().background(
                Brush.horizontalGradient(
                    listOf(
                        VaultColors.background.copy(alpha = 0.95f),
                        VaultColors.background.copy(alpha = 0.55f),
                        VaultColors.background.copy(alpha = 0.2f)
                    )
                )
            )
        )
        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                VaultStamp("CONFIDENTIAL", VaultColors.red)
                Spacer(Modifier.weight(1f))
                Text(
                    "FILE NO. 001",
                    color = VaultColors.gold,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.sp
                )
            }
            Column {
                Text(
                    "DETECTIVE CONAN",
                    color = VaultColors.gold,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.5.sp
                )
                Text(
                    "EVIDENCE\nVAULT",
                    color = VaultColors.white,
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Black,
                    lineHeight = 31.sp
                )
                Text(
                    "THE TRUTH IS IN THE DATA.",
                    color = VaultColors.muted,
                    fontSize = 10.sp,
                    letterSpacing = 1.sp
                )
            }
        }
    }
}

@Composable
private fun VaultStamp(title: String, color: Color) {
    Surface(
        color = color.copy(alpha = 0.13f),
        border = BorderStroke(1.dp, color),
        shape = RoundedCornerShape(5.dp)
    ) {
        Text(
            title,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            color = color,
            fontSize = 9.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 0.7.sp
        )
    }
}

@Composable
private fun VaultSectionHeading(
    number: String,
    title: String,
    subtitle: String,
    color: Color
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(32.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(color.copy(alpha = 0.15f))
                .border(1.dp, color, RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                number,
                color = color,
                fontWeight = FontWeight.Black,
                fontSize = 13.sp
            )
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                title,
                color = VaultColors.white,
                fontWeight = FontWeight.Black,
                fontSize = 14.sp,
                letterSpacing = 0.8.sp
            )
            Text(
                subtitle,
                color = color,
                fontSize = 10.sp,
                letterSpacing = 0.7.sp
            )
        }
        Box(Modifier.width(38.dp).height(1.dp).background(color))
    }
}

@Composable
private fun VaultDataTile(
    modifier: Modifier,
    number: String,
    label: String,
    icon: ImageVector,
    accent: Color
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = VaultColors.surfaceRaised,
        border = BorderStroke(1.dp, accent.copy(alpha = 0.45f))
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(17.dp)
                )
                Spacer(Modifier.weight(1f))
                Text("EVIDENCE", color = VaultColors.muted, fontSize = 8.sp)
            }
            Spacer(Modifier.height(7.dp))
            Text(
                number,
                color = VaultColors.white,
                fontWeight = FontWeight.Black,
                fontSize = 27.sp
            )
            Text(
                label,
                color = accent,
                fontWeight = FontWeight.Bold,
                fontSize = 10.sp,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun VaultOperationCard(
    caseNumber: String,
    title: String,
    body: String,
    accent: Color,
    icon: ImageVector,
    buttonIcon: ImageVector,
    buttonLabel: String,
    filled: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = VaultColors.surface),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, VaultColors.border)
    ) {
        Column {
            Box(
                Modifier.fillMaxWidth().height(5.dp).background(
                    Brush.horizontalGradient(
                        listOf(accent, accent.copy(alpha = 0.18f), accent)
                    )
                )
            )
            Column(
                Modifier.fillMaxWidth().padding(15.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(43.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(accent.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(icon, contentDescription = null, tint = accent)
                    }
                    Spacer(Modifier.width(11.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            caseNumber,
                            color = accent,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 1.sp
                        )
                        Text(
                            title,
                            color = VaultColors.white,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Icon(
                        Icons.Default.Search,
                        contentDescription = null,
                        tint = VaultColors.border,
                        modifier = Modifier.size(27.dp)
                    )
                }
                Text(body, color = VaultColors.muted, fontSize = 12.sp, lineHeight = 18.sp)
                if (filled) {
                    Button(
                        onClick = onClick,
                        enabled = enabled,
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = accent,
                            contentColor = Color.White
                        )
                    ) {
                        Icon(buttonIcon, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(buttonLabel, fontWeight = FontWeight.Black, fontSize = 12.sp)
                    }
                } else {
                    OutlinedButton(
                        onClick = onClick,
                        enabled = enabled,
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, accent),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = accent
                        )
                    ) {
                        Icon(buttonIcon, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(buttonLabel, fontWeight = FontWeight.Black, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun VaultSecurityNotice() {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF221A21)),
        border = BorderStroke(1.dp, VaultColors.red.copy(alpha = 0.5f)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(Modifier.fillMaxWidth().padding(13.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Info,
                    contentDescription = null,
                    tint = VaultColors.gold,
                    modifier = Modifier.size(17.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "CASE FILE PROTOCOL",
                    color = VaultColors.gold,
                    fontWeight = FontWeight.Black,
                    fontSize = 11.sp,
                    letterSpacing = 0.7.sp
                )
            }
            Spacer(Modifier.height(7.dp))
            Text(
                "RESTORE REPLACES existing Collection, Decks and Wishlist. " +
                        "Card images and translation cache are not included. " +
                        "JSON backups are not encrypted; keep your case file private.",
                color = VaultColors.muted,
                fontSize = 11.sp,
                lineHeight = 16.sp
            )
        }
    }
}

@Composable
private fun VaultPreviewRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = VaultColors.muted, fontSize = 12.sp)
        Text(value, color = VaultColors.white, fontWeight = FontWeight.Bold, fontSize = 12.sp)
    }
}

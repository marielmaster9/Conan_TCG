package com.example.conantcg.data.repository

import android.content.Context
import com.example.conantcg.data.model.Deck
import com.example.conantcg.data.model.WebCard
import com.example.conantcg.data.remote.ConanScraper
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.File

class CardRepository(private val context: Context) {
    // ไฟล์ Cache สำหรับรายการการ์ดทั้งหมด
    private val CACHE_FILE = "conan_cards_cache.json"

    // ไฟล์สำหรับเก็บข้อมูล User
    private val COLLECTION_FILE = "user_collection.json"
    private val DECKS_FILE = "user_decks.json"

    private val gson = Gson()

    // --- 1. Get Cards (ปรับปรุง: เพิ่ม Retry และ Fallback) ---
    suspend fun getCards(force: Boolean, onProgress: (Float, String) -> Unit): List<WebCard> = withContext(Dispatchers.IO) {
        val file = File(context.cacheDir, CACHE_FILE)

        // [Step 1] เตรียมข้อมูล Backup จากไฟล์ Cache เก่าไว้ก่อนเสมอ
        var backupCache: List<WebCard> = emptyList()
        if (file.exists()) {
            try {
                val jsonStr = file.readText()
                if (jsonStr.isNotBlank()) {
                    val arr = JSONArray(jsonStr)
                    val list = mutableListOf<WebCard>()
                    for (i in 0 until arr.length()) {
                        list.add(WebCard.fromJson(arr.getJSONObject(i)))
                    }
                    backupCache = list
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // [Step 2] ถ้า User ไม่ได้กด Refresh (force=false) และมีของเก่า -> ใช้ของเก่าเลย (เร็ว)
        if (!force && backupCache.isNotEmpty()) {
            onProgress(1.0f, "Loaded from cache")
            return@withContext backupCache
        }

        // [Step 3] ถ้าต้องโหลดใหม่ -> เข้าสู่โหมด Retry (ลอง 3 ครั้ง)
        var onlineCards: List<WebCard> = emptyList()
        var attempt = 0
        val maxRetries = 3

        while (attempt < maxRetries) {
            try {
                attempt++
                onProgress(0.1f * attempt, "Connecting to server (Attempt $attempt)...")

                // เรียก Scraper
                val result = ConanScraper.fetchAllCards(onProgress)

                if (result.isNotEmpty()) {
                    onlineCards = result
                    break // ถ้าได้ข้อมูลแล้ว ให้หยุดลองทันที
                } else {
                    // ถ้า Server ตอบ 500 หรือ Scraper พัง จะได้ list ว่าง -> ให้โยน Error เพื่อเข้า catch
                    throw Exception("Empty result from server")
                }
            } catch (e: Exception) {
                e.printStackTrace()
                if (attempt < maxRetries) {
                    onProgress(0.0f, "Server Error. Retrying in 2s...")
                    delay(2000) // รอ 2 วินาทีก่อนลองใหม่
                }
            }
        }

        // [Step 4] ตัดสินใจว่าจะใช้อะไร (หัวใจสำคัญ)
        if (onlineCards.isNotEmpty()) {
            // A. กรณีสำเร็จ: บันทึกของใหม่ลงไฟล์ แล้วส่งของใหม่กลับไป
            try {
                val arr = JSONArray()
                onlineCards.distinctBy { it.id }.forEach { arr.put(it.toJson()) }
                file.writeText(arr.toString())
            } catch (e: Exception) {
                e.printStackTrace()
            }
            return@withContext onlineCards
        } else {
            // B. กรณีล้มเหลว (Server 500 ทั้ง 3 รอบ):
            // "งัดของเก่ามาใช้ (Fallback)" แทนที่จะส่งค่าว่าง
            if (backupCache.isNotEmpty()) {
                onProgress(1.0f, "Server Error. Showing offline data.")
                return@withContext backupCache
            } else {
                // C. กรณีเลวร้ายสุด: เน็ตไม่มี และไฟล์เก่าก็ไม่มี
                onProgress(1.0f, "Failed to load data.")
                return@withContext emptyList()
            }
        }
    }

    // --- 2. Load/Save Collection (เหมือนเดิม) ---
    fun loadCollection(): Map<String, Int> {
        val file = File(context.filesDir, COLLECTION_FILE)
        if (!file.exists()) return emptyMap()

        return try {
            val type = object : TypeToken<Map<String, Int>>() {}.type
            gson.fromJson(file.readText(), type) ?: emptyMap()
        } catch (e: Exception) {
            e.printStackTrace()
            emptyMap()
        }
    }

    fun saveCollection(map: Map<String, Int>) {
        try {
            val jsonString = gson.toJson(map)
            context.openFileOutput(COLLECTION_FILE, Context.MODE_PRIVATE).use {
                it.write(jsonString.toByteArray())
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // --- 3. Load/Save Decks (เหมือนเดิม) ---
    fun loadDecks(): List<Deck> {
        val file = File(context.filesDir, DECKS_FILE)
        if (!file.exists()) return emptyList()

        return try {
            val type = object : TypeToken<List<Deck>>() {}.type
            gson.fromJson(file.readText(), type) ?: emptyList()
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    fun saveDecks(list: List<Deck>) {
        try {
            val jsonString = gson.toJson(list)
            context.openFileOutput(DECKS_FILE, Context.MODE_PRIVATE).use {
                it.write(jsonString.toByteArray())
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
package com.example.conantcg.data.remote

import android.util.Log
import com.example.conantcg.data.model.WebCard
import kotlinx.coroutines.*
import org.json.JSONObject
import org.jsoup.Jsoup

object ConanScraper {
    private const val TAG = "ConanScraper"
    private const val BASE_URL = "https://www.takaratomy.co.jp/products/conan-cardgame/cardlist/"

    suspend fun fetchAllCards(onProgress: (Float, String) -> Unit): List<WebCard> = withContext(Dispatchers.IO) {
        val allCards = mutableMapOf<String, WebCard>()
        Log.d(TAG, "=== Starting Fetch Process (No Filters) ===")

        try {
            var page = 1
            var hasMorePages = true
            var emptyPageCount = 0

            // วนลูปดึงข้อมูลทีละหน้า โดยไม่ต้องใส่ Filter แพ็กเกจหรือสี
            while (hasMorePages) {
                // คำนวณ Progress แบบประมาณการ (สมมติว่าเว็บมีประมาณ 30+ หน้า) จะได้ % ที่สมูท
                val fakeProgress = (page / 30f).coerceAtMost(0.95f)
                val percent = (fakeProgress * 100).toInt()
                onProgress(fakeProgress, "Downloading... $percent%") // แสดงผลเป็น % เหมือนเดิม

                try {
                    val doc = Jsoup.connect(BASE_URL)
                        .data("page", page.toString()) // ส่งแค่เลขหน้าไปอย่างเดียว
                        .userAgent("Mozilla/5.0")
                        .timeout(20000)
                        .maxBodySize(0)
                        .get()

                    val pageCards = parseHtml(doc.html(), "Page $page")

                    if (pageCards.isEmpty()) {
                        emptyPageCount++
                        // ถ้าดึงมาแล้วหน้าว่างติดกัน 2 ครั้ง แปลว่าหมดแล้วจริงๆ
                        if (emptyPageCount >= 2) {
                            hasMorePages = false
                        }
                    } else {
                        emptyPageCount = 0
                        var addedNewCard = false

                        pageCards.forEach { card ->
                            if (!allCards.containsKey(card.id)) {
                                allCards[card.id] = card
                                addedNewCard = true
                            }
                        }

                        // ถ้ายิง Request ไปแล้วได้การ์ดซ้ำกับที่มีอยู่ทั้งหมด
                        // (มักเกิดจากเว็บที่หน้าสุดท้ายแล้วกด Next ต่อ มันจะโชว์หน้าเดิม) ให้หยุดทำงาน
                        if (!addedNewCard) {
                            Log.d(TAG, "Page $page returned only existing cards. Stopping.")
                            hasMorePages = false
                        } else {
                            Log.d(TAG, "Page $page fetched ${pageCards.size} cards. Total unique: ${allCards.size}")
                            page++ // เพิ่มเลขหน้าเพื่อดึงรอบถัดไป
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error fetching page $page", e)
                    hasMorePages = false // หลุด Loop ถ้าเน็ตมีปัญหาหรือเว็บไม่ตอบสนอง
                }
            }

            // เมื่อเสร็จสมบูรณ์ ให้ปรับเป็น 100%
            onProgress(1.0f, "Downloading... 100%")

        } catch (e: Exception) {
            Log.e(TAG, "Critical Error", e)
        }

        Log.d(TAG, "=== FINISHED === Total unique cards: ${allCards.size}")
        return@withContext allCards.values.toList()
    }

    private fun parseHtml(html: String, source: String): List<WebCard> {
        val cards = mutableListOf<WebCard>()
        var idx = 0

        while (true) {
            val dIdx = html.indexOf("data=", idx); if (dIdx == -1) break
            val open = html.indexOf("{", dIdx)
            if (open == -1 || open - dIdx > 50) { idx = dIdx + 5; continue }

            var brace = 0; var i = open; var inStr = false; var esc = false; var close = -1
            while (i < html.length) {
                val c = html[i]
                if (c == '"' && !esc) inStr = !inStr
                if (!inStr) { if (c == '{') brace++ else if (c == '}') brace-- }
                esc = (c == '\\' && !esc)
                if (brace == 0) { close = i; break }
                i++
            }
            if (close != -1) {
                try {
                    val rawJson = html.substring(open, close + 1)
                        .replace("&quot;", "\"")
                        .replace("\\&quot;", "\\\"")

                    val json = JSONObject(rawJson)
                    if (json.has("card_num")) {
                        cards.add(WebCard.fromJson(json).copy(
                            imageUrl = "https://www.takaratomy.co.jp/products/conan-cardgame/storage/card/" + json.optString("main_path")
                        ))
                    }
                } catch (e: Exception) {
                    // ปล่อยผ่านกรณีเกิด Error ตอนแปลง JSON
                }
                idx = close + 1
            } else idx = open + 1
        }
        return cards
    }
}
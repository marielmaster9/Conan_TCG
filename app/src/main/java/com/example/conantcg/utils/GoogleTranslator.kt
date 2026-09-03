package com.example.conantcg.utils

import android.content.Context
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.generationConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

object GoogleTranslator {

    // ⚠️ นำ API Key ที่ได้จาก Google AI Studio มาใส่
    private const val API_KEY = ""

    // หน่วยความจำชั่วคราว (RAM) เพื่อความเร็วสูงสุดตอนเลื่อนจอไปมา
    private val memoryCache = ConcurrentHashMap<String, String>()

    // ตั้งค่าตัวโมเดล Gemini 2.5 Flash
    private val generativeModel = GenerativeModel(
        modelName = "gemini-2.5-flash",
        apiKey = API_KEY,
        generationConfig = generationConfig {
            temperature = 0.1f
        }
    )

    // [แก้ไข] เพิ่ม context: Context เข้ามาในพารามิเตอร์
    suspend fun translate(context: Context, text: String, targetLang: String): String? {
        return withContext(Dispatchers.IO) {
            if (text.isBlank() || text == "null" || text == "-") {
                return@withContext text
            }

            // เข้าถึงไฟล์เซฟของเครื่องชื่อ "TranslationCache"
            val prefs = context.getSharedPreferences("TranslationCache", Context.MODE_PRIVATE)

            // สร้าง Key ประจำตัวของข้อความ
            val cacheKey = "${targetLang.lowercase()}_${text.trim()}"

            // ขั้นที่ 1: เช็กใน RAM ก่อน (ความเร็ว 0.001 วินาที)
            if (memoryCache.containsKey(cacheKey)) {
                return@withContext memoryCache[cacheKey]
            }

            // ขั้นที่ 2: เช็กในไฟล์เซฟของเครื่อง (กรณีเพิ่งเปิดแอปใหม่)
            val savedTranslation = prefs.getString(cacheKey, null)
            if (savedTranslation != null) {
                // เจอในเครื่อง! ดึงมาใส่ RAM ไว้เลยรอบหน้าจะได้ไม่ต้องค้นไฟล์อีก
                memoryCache[cacheKey] = savedTranslation
                return@withContext savedTranslation
            }

            // ขั้นที่ 3: ถ้าไม่มีที่ไหนเลย ค่อยให้ AI แปล (รออินเทอร์เน็ต)
            val languageName = if (targetLang.lowercase() == "th") "Thai" else "English"

            val prompt = """
                You are an expert translator for the Detective Conan Trading Card Game (TCG).
                Translate the following Japanese card text into $languageName.
                
                Strict Rules for TCG Terminology:
                1. Maintain precise terms like AP, LP, FILE, Deck, Trash, Hand.
                2. Format keywords strictly with brackets as follows:
                   - 【スリープ】 -> [Sleep] 
                   - 【アクティブ】 -> [Active]
                   - 【事件解決】 -> [Case Resolved] (if English) or [ไขคดีสำเร็จ] (if Thai)
                   - 【ターン１回】 or 【ターン1回】 -> [Once per turn] (if English) or [เทิร์นละ 1 ครั้ง] (if Thai)
                   - 【アシスト】 -> [Assist]
                   - 【解決編】 -> [Resolution] (if English) or [ช่วงไขคดี] (if Thai)
                   - 【パートナー】 -> [Partner] (if English) or [พาร์ทเนอร์] (if Thai)
                3. Make the sentence structure sound natural for TCG players in $languageName.
                4. Output ONLY the final translated text. Do not add any conversational filler, quotes, or explanations.
                
                Japanese Text to translate:
                $text
            """.trimIndent()

            try {
                val response = generativeModel.generateContent(prompt)
                val translatedText = response.text?.trim()

                if (!translatedText.isNullOrEmpty()) {
                    // จำใส่ RAM
                    memoryCache[cacheKey] = translatedText
                    // [สำคัญ] เซฟลงเครื่อง! คราวหน้าปิดแอปเปิดใหม่ก็ยังอยู่
                    prefs.edit().putString(cacheKey, translatedText).apply()
                }

                translatedText
            } catch (e: Exception) {
                e.printStackTrace()
                text
            }
        }
    }
}

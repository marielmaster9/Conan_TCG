package com.example.conantcg.utils

import android.content.Context
import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.GenerativeBackend
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

/**
 * Detective Conan TCG translations using Firebase AI Logic.
 * No Gemini API key, service-account credential, or token belongs in this file.
 * Set up Firebase AI Logic and enforce Firebase App Check before use.
 */
object GoogleTranslator {

    private const val TAG = "GoogleTranslator"
    private val memoryCache = ConcurrentHashMap<String, String>()
    private val rateLimiter = Semaphore(1)

    // Supported stable models for Firebase AI Logic as of Oct 2026.
    private val fallbackModels = listOf(
        "gemini-3.5-flash-lite",
        "gemini-3.5-flash"
    )

    /** Compatible with existing 3-argument callers. */
    suspend fun translate(context: Context, text: String, targetLang: String): String =
        translate(
            context = context,
            cardId = text.hashCode().toString(),
            text = text,
            targetLang = targetLang
        )

    /** Compatible with existing CardDetailScreen 4-argument callers. */
    suspend fun translate(
        context: Context,
        cardId: String,
        text: String,
        targetLang: String
    ): String = withContext(Dispatchers.IO) {
        if (text.isBlank() || text == "null" || text == "-") return@withContext text

        val languageCode = if (targetLang.equals("th", ignoreCase = true)) "th" else "en"
        val languageName = if (languageCode == "th") "Thai" else "English"
        val prefs = context.getSharedPreferences("TranslationCache", Context.MODE_PRIVATE)
        // Include text hash to avoid returning stale translations when card text changes.
        val cacheKey = "${languageCode}_${cardId.trim()}_${text.hashCode()}"

        memoryCache[cacheKey]?.let { return@withContext it }
        prefs.getString(cacheKey, null)?.let { cached ->
            memoryCache[cacheKey] = cached
            return@withContext cached
        }

        rateLimiter.withPermit {
            // Another queued request may have filled the cache while we waited.
            memoryCache[cacheKey]?.let { return@withPermit it }

            val prompt = """
                You are an expert translator for the Detective Conan Trading Card Game (TCG).
                Translate the following Japanese card text into $languageName.

                Strict Rules for TCG Terminology:
                1. Preserve AP, LP, FILE, Deck, Trash, Hand and other game terms accurately.
                2. Format keywords strictly with brackets:
                   - 【スリープ】 -> [Sleep]
                   - 【アクティブ】 -> [Active]
                   - 【事件解決】 -> [Case Resolved] in English / [ไขคดีสำเร็จ] in Thai
                   - 【ターン１回】 or 【ターン1回】 -> [Once per turn] in English / [เทิร์นละ 1 ครั้ง] in Thai
                   - 【アシスト】 -> [Assist]
                   - 【解決編】 -> [Resolution] in English / [ช่วงไขคดี] in Thai
                   - 【パートナー】 -> [Partner] in English / [พาร์ทเนอร์] in Thai
                3. Use natural terminology for TCG players in $languageName.
                4. Output ONLY the translated text; no explanations or quotes.
                5. If the original contains the delimiter " ||| ", preserve it exactly.

                Japanese text:
                $text
            """.trimIndent()

            for (modelName in fallbackModels) {
                try {
                    val model = Firebase.ai(backend = GenerativeBackend.googleAI())
                        .generativeModel(
                            modelName = modelName,
                            generationConfig = generationConfig { temperature = 0.1f }
                        )

                    val result = model.generateContent(prompt).text?.trim().orEmpty()
                    if (result.isNotEmpty()) {
                        memoryCache[cacheKey] = result
                        prefs.edit().putString(cacheKey, result).apply()
                        return@withPermit result
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    Log.w(TAG, "Translation with $modelName failed: ${error.javaClass.simpleName}")
                    delay(800L)
                }
            }

            // Match prior behavior: show original text when all models are unavailable.
            text
        }
    }
}

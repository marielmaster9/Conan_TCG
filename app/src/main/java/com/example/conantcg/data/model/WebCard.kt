package com.example.conantcg.data.model
import com.example.conantcg.utils.CategoryMap
import org.json.JSONObject

data class WebCard(
    val id: String, val cardId: String, val cardNum: String, val name: String,
    val type: String, val color: String, val rarity: String,
    val ap: String, val lp: String, val cost: String,
    val feature: String, val cut_in: String, val hirameki: String, val henso: String,
    val imageUrl: String, val pack: String,
    val category1: String? = null, val category2: String? = null, val category3: String? = null
) {
    val categories: List<String> get() = listOfNotNull(category1, category2, category3)
        .flatMap { it.split(",", "、") }.map { it.trim() }
        .filter { it.isNotEmpty() && !it.equals("null", true) }
        .map { CategoryMap.translate(it) }.distinct().ifEmpty { listOf("Others") }

    fun getPackCategory(): PackCategory = when {
        pack.contains("CT-D", true) || pack.contains("Start", true) -> PackCategory.STARTER
        pack.contains("CT-P", true) || pack.contains("Booster", true) -> PackCategory.BOOSTER
        else -> PackCategory.PROMO
    }

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id); put("card_id", cardId); put("card_num", cardNum); put("name", name)
        put("type", type); put("color", color); put("rarity", rarity); put("ap", ap)
        put("lp", lp); put("cost", cost); put("feature", feature); put("cut_in", cut_in)
        put("hirameki", hirameki); put("henso", henso); put("imageUrl", imageUrl)
        // บันทึก pack ลง cache
        put("pack", pack)
        put("category1", category1); put("category2", category2); put("category3", category3)
    }

    companion object {
        fun fromJson(json: JSONObject): WebCard = WebCard(
            json.optString("id"), json.optString("card_id"), json.optString("card_num"),
            // รองรับทั้ง title (จากเว็บ) และ name (จาก cache)
            json.optString("title").ifEmpty { json.optString("name") },
            json.optString("type"), json.optString("color"),
            json.optString("rarity"), json.optString("ap"), json.optString("lp"),
            json.optString("cost"), json.optString("feature"), json.optString("cut_in"),
            json.optString("hirameki"), json.optString("henso"), json.optString("imageUrl"),

            // [จุดสำคัญที่ต้องแก้]
            // ดึงจาก "package" (เว็บ) ก่อน ถ้าไม่มีค่อยดึง "pack" (cache)
            json.optString("package").ifEmpty { json.optString("pack") },

            json.optString("category1").ifEmpty { null },
            json.optString("category2").ifEmpty { null }, json.optString("category3").ifEmpty { null }
        )
    }
}

enum class PackCategory(val title: String) { STARTER("Starter Deck"), BOOSTER("Booster Pack"), PROMO("Promo Cards") }
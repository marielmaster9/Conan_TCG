package com.example.conantcg.data.model

import com.example.conantcg.utils.CategoryMap
import com.google.gson.annotations.SerializedName
import org.json.JSONObject

data class WebCard(
    @SerializedName("id") val id: String = "",
    @SerializedName(value = "cardId", alternate = ["card_id"]) val cardId: String = "",
    @SerializedName(value = "cardNum", alternate = ["card_num"]) val cardNum: String = "",
    @SerializedName(value = "name", alternate = ["title"]) val name: String = "",
    @SerializedName("type") val type: String = "",
    @SerializedName("color") val color: String = "",
    @SerializedName("rarity") val rarity: String = "",
    @SerializedName("ap") val ap: String = "",
    @SerializedName("lp") val lp: String = "",
    @SerializedName("cost") val cost: String = "",
    @SerializedName("feature") val feature: String = "",
    @SerializedName("cut_in") val cut_in: String = "",
    @SerializedName("hirameki") val hirameki: String = "",
    @SerializedName("henso") val henso: String = "",
    @SerializedName(value = "imageUrl", alternate = ["image_url", "image"]) val imageUrl: String = "",
    @SerializedName(value = "pack", alternate = ["package"]) val pack: String = "",

    // ฟิลด์สำหรับคำแปลภาษาไทยและข้อมูลเสริม
    @SerializedName(value = "nameTh", alternate = ["name_th"]) val nameTh: String? = null,
    @SerializedName("text") val text: String? = null,
    @SerializedName("text_formatted") val textFormatted: String? = null,

    @SerializedName("category1") val category1: String? = null,
    @SerializedName("category2") val category2: String? = null,
    @SerializedName("category3") val category3: String? = null
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
        put("pack", pack)
        put("name_th", nameTh)
        put("text", text); put("text_formatted", textFormatted)
        put("category1", category1); put("category2", category2); put("category3", category3)
    }

    companion object {
        fun fromJson(json: JSONObject): WebCard = WebCard(
            id = json.optString("id"),
            cardId = json.optString("card_id").ifEmpty { json.optString("cardId") },
            cardNum = json.optString("card_num").ifEmpty { json.optString("cardNum") },
            name = json.optString("title").ifEmpty { json.optString("name") },
            type = json.optString("type"),
            color = json.optString("color"),
            rarity = json.optString("rarity"),
            ap = json.optString("ap"),
            lp = json.optString("lp"),
            cost = json.optString("cost"),
            feature = json.optString("feature"),
            cut_in = json.optString("cut_in"),
            hirameki = json.optString("hirameki"),
            henso = json.optString("henso"),
            imageUrl = json.optString("imageUrl").ifEmpty { json.optString("image_url") },
            pack = json.optString("package").ifEmpty { json.optString("pack") },
            nameTh = json.optString("name_th").ifEmpty { json.optString("nameTh") }.ifEmpty { null },
            text = json.optString("text").ifEmpty { null },
            textFormatted = json.optString("text_formatted").ifEmpty { null },
            category1 = json.optString("category1").ifEmpty { null },
            category2 = json.optString("category2").ifEmpty { null },
            category3 = json.optString("category3").ifEmpty { null }
        )
    }
}

enum class PackCategory(val title: String) { STARTER("Starter Deck"), BOOSTER("Booster Pack"), PROMO("Promo Cards") }
package com.example.conantcg.data.model
import org.json.JSONObject
import java.util.UUID

data class Deck(
    val id: String = UUID.randomUUID().toString(), var name: String,
    val cards: MutableMap<String, Int> = mutableMapOf(),
    var partner: String? = null, var case: String? = null,
    var dateCreated: Long = System.currentTimeMillis()
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id); put("name", name); put("partner", partner ?: ""); put("case", case ?: "")
        put("date", dateCreated); put("cards", JSONObject().apply { cards.forEach { (k, v) -> put(k, v) } })
    }
    companion object {
        fun fromJson(json: JSONObject): Deck {
            val deck = Deck(
                json.optString("id", UUID.randomUUID().toString()), json.optString("name", "New Deck"),
                partner = json.optString("partner").ifEmpty { null }, case = json.optString("case").ifEmpty { null },
                dateCreated = json.optLong("date", System.currentTimeMillis())
            )
            json.optJSONObject("cards")?.let { c -> c.keys().forEach { deck.cards[it] = c.getInt(it) } }
            return deck
        }
    }
}
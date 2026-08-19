package com.example.conantcg.data

import com.example.conantcg.utils.CategoryMap
import org.json.JSONObject
import java.util.UUID

// --- Enums for Data ---
// Enum สำหรับระบุหมวดหมู่หลักของการ์ด เพื่อใช้กรองในหน้า Catalog
enum class CardCategory(val title: String) {
    STARTER("Starter Decks"),   // สำหรับการ์ดจากชุดเริ่มต้น
    BOOSTER("Booster Packs"),   // สำหรับการ์ดจากซองสุ่ม
    PROMO("Promo / Other")      // สำหรับการ์ดโปรโมชั่นและอื่นๆ
}

// --- Data Models ---
// โมเดลหลักเก็บข้อมูลการ์ด 1 ใบ (Mapping ตรงกับข้อมูลจากเว็บ/JSON)
data class WebCard(
    val id: String,           // ID อ้างอิงในฐานข้อมูล
    val cardId: String,       // รหัสการ์ด (เช่น CD-001)
    val cardNum: String,      // หมายเลขการ์ดที่แสดงบนหน้าการ์ด (เช่น D01-001) - ใช้เป็น Key หลัก
    val name: String,         // ชื่อการ์ด
    val type: String,         // ประเภท (Character, Event, Partner, Case)
    val color: String,        // สีของการ์ด
    val rarity: String,       // ระดับความหายาก
    val ap: String,           // พลังโจมตี (AP)
    val lp: String,           // พลังชีวิต (LP)
    val cost: String,         // ค่าร่าย (Level/Cost)
    val feature: String,      // ความสามารถ (Text effect)
    val cut_in: String,        // ความสามารถ Cut-In
    val hirameki: String,     // ความสามารถ Hirameki
    val henso: String,        // ความสามารถ Henso (ปลอมตัว)
    val imageUrl: String,     // URL ของรูปภาพการ์ด
    val pack: String,         // ชื่อชุดการ์ด (Pack Name)
    val category1: String? = null, // หมวดหมู่ย่อย 1
    val category2: String? = null, // หมวดหมู่ย่อย 2
    val category3: String? = null  // หมวดหมู่ย่อย 3
) {
    // Property พิเศษ: ช่วยรวม category1-3 ให้อยู่ใน List เดียวกัน พร้อมแปลภาษาและกรองค่า null
    val categories: List<String>
        get() {
            val rawList = listOfNotNull(category1, category2, category3)
            val validCategories = rawList.flatMap { it.split(",") }
                .map { it.trim() }
                .filter { it.isNotEmpty() && !it.equals("null", ignoreCase = true) } // กรองค่า "null" ที่เป็น text ออก
                .map { CategoryMap.translate(it) } // แปลเป็นภาษาอังกฤษ (ถ้ามีใน Map)
                .distinct()

            // ถ้าไม่มีหมวดหมู่ที่ถูกต้องเลย ให้ใส่ "Others"
            return if (validCategories.isNotEmpty()) {
                validCategories
            } else {
                listOf("Others")
            }
        }

    // แปลงข้อมูลการ์ดเป็น JSON Object เพื่อบันทึกลงไฟล์ Cache
    fun toJson(): JSONObject {
        return JSONObject().apply {
            put("id", id); put("cardId", cardId); put("cardNum", cardNum)
            put("name", name); put("type", type); put("color", color)
            put("rarity", rarity); put("ap", ap); put("lp", lp)
            put("cost", cost); put("feature", feature)
            put("cut_in", cut_in); put("hirameki", hirameki); put("henso", henso)
            put("imageUrl", imageUrl); put("pack", pack)
            put("category1", category1); put("category2", category2); put("category3", category3)
        }
    }

    companion object {
        // สร้าง WebCard Object จาก JSON Object (ใช้ตอนโหลดกลับจาก Cache)
        fun fromJson(json: JSONObject): WebCard {
            return WebCard(
                id = json.optString("id"), cardId = json.optString("cardId"), cardNum = json.optString("cardNum"),
                name = json.optString("name"), type = json.optString("type"), color = json.optString("color"),
                rarity = json.optString("rarity"), ap = json.optString("ap"), lp = json.optString("lp"),
                cost = json.optString("cost"), feature = json.optString("feature"),
                cut_in = json.optString("cut_in"), hirameki = json.optString("hirameki"), henso = json.optString("henso"),
                imageUrl = json.optString("imageUrl"), pack = json.optString("pack"),
                category1 = json.optString("category1").ifEmpty { null },
                category2 = json.optString("category2").ifEmpty { null },
                category3 = json.optString("category3").ifEmpty { null }
            )
        }
    }

    // ฟังก์ชันช่วยระบุ CardCategory จากชื่อ Pack
    fun getCategory(): CardCategory {
        return when {
            pack.contains("CT-D") || pack.contains("Start", ignoreCase = true) -> CardCategory.STARTER
            pack.contains("CT-B") || pack.contains("Booster", ignoreCase = true) -> CardCategory.BOOSTER
            else -> CardCategory.PROMO
        }
    }
}

// โมเดลสำหรับเก็บข้อมูล Deck (เด็คการ์ดที่ผู้ใช้จัด)
data class Deck(
    val id: String = UUID.randomUUID().toString(), // ID ของเด็ค
    var name: String,                            // ชื่อเด็ค
    val cards: MutableMap<String, Int> = mutableMapOf(), // การ์ดในเด็ค (Key=CardNum, Value=จำนวน)
    var partner: String? = null,                 // การ์ด Partner
    var case: String? = null,                    // การ์ด Case
    var dateCreated: Long = System.currentTimeMillis() // วันที่สร้าง
) {
    // แปลง Deck เป็น JSON เพื่อบันทึก
    fun toJson(): JSONObject {
        val json = JSONObject()
        json.put("id", id)
        json.put("name", name)
        json.put("partner", partner ?: "")
        json.put("case", case ?: "")
        json.put("date", dateCreated)
        val cardsJson = JSONObject()
        cards.forEach { (k, v) -> cardsJson.put(k, v) }
        json.put("cards", cardsJson)
        return json
    }

    companion object {
        // สร้าง Deck จาก JSON
        fun fromJson(json: JSONObject): Deck {
            val deck = Deck(
                id = json.optString("id", UUID.randomUUID().toString()),
                name = json.optString("name", "New Deck"),
                partner = json.optString("partner").ifEmpty { null },
                case = json.optString("case").ifEmpty { null },
                dateCreated = json.optLong("date", System.currentTimeMillis())
            )
            val cardsJson = json.optJSONObject("cards")
            cardsJson?.keys()?.forEach { key ->
                deck.cards[key] = cardsJson.getInt(key)
            }
            return deck
        }
    }
}

// โมเดลสำหรับเก็บสถิติของ Pack (จำนวนที่มี / จำนวนทั้งหมด)
data class PackStats(
    val total: Int,
    val owned: Int,
    val rarityMap: Map<String, Pair<Int, Int>> // สถิติแยกตามความหายาก
)
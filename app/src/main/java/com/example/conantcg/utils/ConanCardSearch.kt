package com.example.conantcg.utils

import com.example.conantcg.data.model.WebCard
import java.text.Normalizer
import java.util.Locale

/**
 * Offline, multilingual search for Detective Conan TCG card names.
 *
 * Search order:
 * 1. Exact card number / ID and exact printed JP / stored Thai name.
 * 2. Partial match in printed JP / stored Thai name.
 * 3. Exact EN/TH/romaji alias mapped to the *printed Japanese name*.
 * 4. Prefix alias (>= 4 characters) so typing "cona" can find Conan.
 * 5. Existing pack search.
 *
 * Important: no substring matching against alias keys ("ai" or "ran"
 * must not accidentally match longer unrelated English names).
 * Add extra names by appending register(...) calls in buildAliasIndex().
 */
object ConanCardSearch {

    private fun normalize(value: String): String = Normalizer.normalize(value, Normalizer.Form.NFKC)
        .lowercase(Locale.ROOT)
        .replace(Regex("[\\s\\p{P}\\p{Z}]+"), "")
        .trim()

    // Keys: normalized English/Thai aliases. Values: Japanese *printed name fragments*.
    private val aliases: Map<String, Set<String>> by lazy { buildAliasIndex() }

    private fun buildAliasIndex(): Map<String, Set<String>> {
        val index = linkedMapOf<String, MutableSet<String>>()

        fun register(japaneseName: String, vararg alternativeNames: String) {
            alternativeNames.forEach { alternative ->
                val key = normalize(alternative)
                if (key.isNotEmpty()) index.getOrPut(key) { linkedSetOf() }.add(japaneseName)
            }
        }

        // Main cast — target actual printed Japanese names, not isolated kanji.
        register("江戸川コナン", "conan", "edogawa conan", "conan edogawa", "edogawa", "โคนัน", "เอโดงาวะ", "เอโดกาวะ", "เอโดงาวะ โคนัน", "โคนัน เอโดงาวะ")
        register("工藤新一", "shinichi", "kudo shinichi", "shinichi kudo", "kudou shinichi", "ชินอิจิ", "คุโด้ ชินอิจิ", "คุโด ชินอิจิ", "คุโด้ชินอิจิ")
        register("毛利蘭", "ran", "ran mouri", "mouri ran", "mori ran", "ran mori", "รัน", "โมริ รัน", "โมริรัน", "โมริ แรน")
        register("毛利小五郎", "kogoro", "kogorou", "kogoro mouri", "mouri kogoro", "kogoro mori", "โคโกโร่", "โคโกโร", "โมริ โคโกโร่", "โมริ โคโกโร")
        register("灰原哀", "haibara", "ai", "ai haibara", "haibara ai", "ไฮบาระ", "ไฮบาร่า", "ไฮบาระ ไอ", "ไอ ไฮบาระ", "ไอ")
        register("宮野志保", "shiho", "miyano shiho", "shiho miyano", "ชิโฮะ", "มิยาโนะ ชิโฮะ", "มิยาโนะ")
        register("シェリー", "sherry", "เชอร์รี่", "เชอร์รี")
        register("阿笠博士", "agasa", "hiroshi agasa", "professor agasa", "dr agasa", "ด็อกเตอร์อากาสะ", "ดร.อากาสะ", "อากาสะ", "อากาสะ ด็อกเตอร์")
        register("吉田歩美", "ayumi", "yoshida ayumi", "ayumi yoshida", "อายูมิ", "โยชิดะ อายูมิ")
        register("円谷光彦", "mitsuhiko", "tsuburaya mitsuhiko", "mitsuhiko tsuburaya", "มิซึฮิโกะ", "มิตสึฮิโกะ", "สึบุรายะ มิซึฮิโกะ")
        register("小嶋元太", "genta", "kojima genta", "genta kojima", "เก็นตะ", "เกนตะ", "โคจิมะ เก็นตะ")
        register("鈴木園子", "sonoko", "suzuki sonoko", "sonoko suzuki", "โซโนโกะ", "ซึซึกิ โซโนโกะ", "ซูซูกิ โซโนโกะ")
        register("京極真", "makoto", "kyogoku makoto", "makoto kyogoku", "kyougoku", "มาโคโตะ", "เคียวโงคุ มาโคโตะ")
        register("妃英理", "eri", "eri kisaki", "kisaki eri", "เอริ", "คิซากิ เอริ", "คิซากิ")
        register("工藤優作", "yusaku", "yuusaku", "kudo yusaku", "yusaku kudo", "ยูซากุ", "คุโด้ ยูซากุ")
        register("工藤有希子", "yukiko", "kudo yukiko", "yukiko kudo", "ยูกิโกะ", "ยูคิโกะ", "คุโด้ ยูกิโกะ")
        register("沖野ヨーコ", "yoko", "youko okino", "yoko okino", "okino yoko", "โยโกะ", "โอกิโนะ โยโกะ")
        register("小林澄子", "sumiko", "kobayashi sumiko", "sumiko kobayashi", "สุมิโกะ", "โคบายาชิ สุมิโกะ")
        register("本堂瑛祐", "eisuke", "hondo eisuke", "eisuke hondo", "เอย์สึเกะ", "ฮอนโด เอย์สึเกะ")
        register("本堂瑛海", "hidemi", "hondo hidemi", "hidemi hondo", "ฮอนโด ฮิเดมิ", "ฮิเดมิ")
        register("キール", "kir", "คีร์", "คีล")
        register("水無怜奈", "rena", "mizunashi rena", "rena mizunashi", "มิสึนาชิ เรนะ", "เรนะ")
        register("少年探偵団", "detective boys", "junior detective league", "detective children", "ขบวนการนักสืบเยาวชน", "ชมรมนักสืบเยาวชน", "กลุ่มนักสืบเยาวชน")

        // Osaka / Kyoto / Nagano / Tokyo and other police.
        register("服部平次", "heiji", "hattori heiji", "heiji hattori", "เฮย์จิ", "เฮจิ", "ฮัตโตริ เฮย์จิ")
        register("服部平蔵", "heizo", "heizou", "hattori heizo", "เฮย์โซ", "ฮัตโตริ เฮย์โซ")
        register("服部静華", "shizuka", "hattori shizuka", "ชิซึกะ", "ฮัตโตริ ชิซึกะ")
        register("遠山和葉", "kazuha", "kazuha toyama", "toyama kazuha", "คาสึฮะ", "คาซึฮะ", "โทยามะ คาสึฮะ", "โทยาม่า คาสึฮะ")
        register("遠山銀司郎", "ginshiro", "ginshirou", "toyama ginshiro", "โทยามะ กินชิโร่")
        register("大滝悟郎", "otaki", "ootaki", "goro otaki", "โอตากิ", "โอตากิ โกโร่")
        register("目暮十三", "megure", "juzo megure", "juuzou megure", "inspector megure", "สารวัตรเมงูเระ", "เมงูเระ", "เมกุเระ")
        register("高木渉", "takagi", "wataru takagi", "takagi wataru", "ทาคางิ", "ทาคากิ", "ทาคางิ วาตารุ")
        register("佐藤美和子", "sato", "satou", "miwako", "sato miwako", "miwako sato", "ซาโต้", "ซาโตะ", "ซาโต้ มิวาโกะ", "มิวาโกะ")
        register("白鳥任三郎", "shiratori", "ninzaburo", "ninzaburou", "shiratori ninzaburo", "ชิราโทริ", "ชิราโทริ นินซาบุโร่")
        register("千葉和伸", "chiba", "kazunobu chiba", "chiba kazunobu", "ชิบะ", "ชิบะ คาซึโนบุ")
        register("宮本由美", "yumi", "miyamoto yumi", "yumi miyamoto", "ยูมิ", "มิยาโมโตะ ยูมิ")
        register("三池苗子", "naeko", "miike naeko", "naeko miike", "นาเอโกะ", "มิอิเกะ นาเอโกะ")
        register("松本清長", "kiyonaga", "matsumoto kiyonaga", "มัตสึโมโตะ คิโยนากะ")
        register("黒田兵衛", "kuroda", "hyoue", "hyoe", "kuroda hyoe", "คุโรดะ", "คุโรดะ เฮียวเอะ")
        register("大和敢助", "yamato", "kansuke", "yamato kansuke", "kansuke yamato", "ยามาโตะ", "คันสึเกะ", "ยามาโตะ คันสึเกะ")
        register("上原由衣", "uehara", "yui", "uehara yui", "yui uehara", "อุเอฮาระ", "ยูอิ", "อุเอฮาระ ยูอิ")
        register("諸伏高明", "komei", "koumei", "takaaki", "morofushi takaaki", "takaaki morofushi", "โคเมย์", "ทาคาอากิ", "โมโรฟุชิ ทาคาอากิ")
        register("松田陣平", "matsuda", "jinpei", "matsuda jinpei", "jinpei matsuda", "มัตสึดะ", "จินเป", "มัตสึดะ จินเป")
        register("萩原研二", "hagiwara", "kenji", "hagiwara kenji", "kenji hagiwara", "ฮางิวาระ", "เคนจิ", "ฮางิวาระ เคนจิ")
        register("萩原千速", "chihaya", "hagiwara chihaya", "chihaya hagiwara", "จิฮายะ", "ฮางิวาระ จิฮายะ")
        register("伊達航", "wataru date", "date wataru", "wataru", "date", "ดาเตะ", "ดาเตะ วาตารุ")
        register("風見裕也", "kazami", "yuya", "yuya kazami", "kazami yuya", "คาซามิ", "ยูยะ", "คาซามิ ยูยะ")
        register("山村ミサオ", "yamamura", "misao yamamura", "ยามามูระ", "ยามามุระ มิซาโอะ")
        register("横溝参悟", "sango yokomizo", "yokomizo sango", "sango", "โยโกมิโซะ ซังโกะ")
        register("横溝重悟", "jugo yokomizo", "juugo yokomizo", "jugo", "โยโกมิโซะ จูโกะ")
        register("綾小路文麿", "ayanokoji", "fumimaro", "ayanokoji fumimaro", "อายาโนะโคจิ", "ฟุมิมาโร่")
        register("茶木神太郎", "chaki", "shintaro chaki", "ชากิ")

        // Public security / Black Organization / FBI / CIA.
        register("安室透", "amuro", "amuro toru", "toru amuro", "tooru amuro", "tooru", "toru", "อามุโร่", "อามุโระ", "อามุโร่ โทรุ", "โทรุ")
        register("降谷零", "furuya", "rei", "rei furuya", "furuya rei", "ฟุรุยะ", "เรย์", "ฟุรุยะ เรย์", "ฟุรุยะ เร")
        register("バーボン", "bourbon", "เบอร์เบิ้น", "เบอร์บอน", "เบอร์เบิน")
        register("諸伏景光", "hiromitsu", "morofushi hiromitsu", "hiromitsu morofushi", "ฮิโรมิสึ", "โมโรฟุชิ ฮิโรมิสึ")
        register("スコッチ", "scotch", "สก็อตช์", "สก๊อตช์")
        register("赤井秀一", "akai", "shuichi", "shuuichi", "shuichi akai", "akai shuichi", "อากาอิ", "ชูอิจิ", "อากาอิ ชูอิจิ")
        register("ライ", "rye", "ไรย์", "ไร")
        register("沖矢昴", "okiya", "subaru", "subaru okiya", "okiya subaru", "โอกิยะ", "สึบารุ", "โอกิยะ สึบารุ")
        register("世良真純", "sera", "masumi", "sera masumi", "masumi sera", "เซระ", "มาสึมิ", "เซระ มาสึมิ")
        register("羽田秀吉", "shukichi", "shuukichi", "haneda shukichi", "shukichi haneda", "ชูคิจิ", "ฮาเนดะ ชูคิจิ")
        register("メアリー", "mary", "เมรี่", "แมรี่")
        register("メアリー世良", "mary sera", "sera mary", "แมรี่ เซระ")
        register("赤井務武", "tsutomu", "akai tsutomu", "สึโตมุ", "อากาอิ สึโตมุ")
        register("赤井務武", "tsutomu akai")
        register("ジョディ", "jodie", "jody", "โจดี้")
        register("ジョディ・スターリング", "jodie starling", "jody starling", "โจดี้ สตาร์ลิ่ง")
        register("ジェイムズ・ブラック", "james", "james black", "เจมส์", "เจมส์ แบล็ก")
        register("ジェームズ・ブラック", "james black", "เจมส์ แบล็ก")
        register("アンドレ・キャメル", "camel", "andre camel", "andré camel", "อังเดร คาเมล", "คาเมล")
        register("キャメル", "camel", "คาเมล", "คาเมลล์")
        register("ジン", "gin", "ยิน", "จิน")
        register("ウォッカ", "vodka", "วอดก้า", "วอดกา")
        register("ベルモット", "vermouth", "เบลม็อท", "เบลม็อต", "เบลมอท", "เวอร์มุธ")
        register("キャンティ", "chianti", "เคียนติ", "คีอันติ")
        register("コルン", "korn", "คอร์น")
        register("ラム", "rum", "รัม")
        register("ピンガ", "pinga", "พิงก้า", "พิงกา")
        register("キュラソー", "curacao", "curaçao", "คูราโซ่")
        register("テキーラ", "tequila", "เตกีล่า", "เทกีล่า")
        register("ピスコ", "pisco", "พิสโก้")
        register("カルバドス", "calvados", "คาลวาโดส")
        register("キャンティ", "เคียนตี้")
        register("宮野明美", "akemi", "miyano akemi", "akemi miyano", "อาเคมิ", "มิยาโนะ อาเคมิ")
        register("宮野エレーナ", "elena", "miyano elena", "เอเลน่า", "มิยาโนะ เอเลน่า")
        register("宮野厚司", "atsushi", "miyano atsushi", "อัตสึชิ", "มิยาโนะ อัตสึชิ")
        register("烏丸蓮耶", "karasuma", "renya", "renya karasuma", "คาราสุมะ", "เร็นยะ")
        register("脇田兼則", "wakita", "kanenori", "wakita kanenori", "วากิตะ", "วากิตะ คาเนโนริ")
        register("黒ずくめの組織", "black organization", "black organisation", "organization in black", "องค์กรชุดดำ", "องค์กรชายชุดดำ", "ขบวนการชุดดำ")
        register("FBI", "fbi", "เอฟบีไอ")
        register("CIA", "cia", "ซีไอเอ")

        // Kaito Kid, associated characters.
        register("怪盗キッド", "kid", "kaito kid", "kaitou kid", "phantom thief kid", "จอมโจรคิด", "จอมโจรคิดส์", "คิด", "ไคโตะคิด")
        register("黒羽快斗", "kaito", "kaitou", "kuroba kaito", "kaito kuroba", "คุโรบะ ไคโตะ", "ไคโตะ", "คุโรบะ")
        register("中森青子", "aoko", "nakamori aoko", "aoko nakamori", "อาโอโกะ", "นากาโมริ อาโอโกะ")
        register("中森銀三", "ginzo", "ginzou", "nakamori ginzo", "นากาโมริ กินโซ")
        register("白馬探", "hakuba", "saguru", "saguru hakuba", "hakuba saguru", "ฮาคุบะ", "ฮาคุบะ ซางุรุ")
        register("小泉紅子", "akako", "koizumi akako", "อาคาโกะ", "โคอิซึมิ อาคาโกะ")
        register("寺井黄之助", "terai", "konosuke terai", "เทราอิ", "เทราอิ โคโนะสุเกะ")
        register("鈴木次郎吉", "jirokichi", "suzuki jirokichi", "จิโรคิจิ", "ซึซึกิ จิโรคิจิ")

        // Other recurring characters appearing in the card list.
        register("榎本梓", "azusa", "enomoto azusa", "azusa enomoto", "อาซึสะ", "เอโนโมโตะ อาซึสะ")
        register("大岡紅葉", "momiji", "oka momiji", "ooka momiji", "โมมิจิ", "โอกะ โมมิจิ")
        register("伊織無我", "iori", "muga", "iori muga", "อิโอริ", "อิโอริ มูกะ")
        register("若狭留美", "rumi", "wakasa rumi", "rumi wakasa", "รูมิ", "วาคาสะ รูมิ")
        register("浅香", "asaka", "อาซากะ")
        register("羽田浩司", "kohji", "koji haneda", "kohji haneda", "haneda kohji", "โคจิ", "ฮาเนดะ โคจิ")
        register("烏丸蓮耶", "karasuma renya", "คาราสุมะ เร็นยะ")
        register("赤木英雄", "hideo akagi", "akagi hideo", "อาคางิ ฮิเดโอะ")
        register("比護隆佑", "higo", "ryusuke higo", "ryuusuke higo", "ฮิโกะ", "ฮิโกะ ริวสุเกะ")
        register("新出智明", "araide", "tomoaki araide", "อาราอิเดะ", "อาราอิเดะ โทโมอากิ")
        register("米原桜子", "sakurako", "yonehara sakurako", "ซากุระโกะ", "โยเนฮาระ ซากุระโกะ")
        register("沖野ヨーコ", "okino", "โอกิโนะ")
        register("仮面ヤイバー", "kamen yaiba", "masked yaiba", "yaiba", "หน้ากากไยบะ", "ไยบะ")
        register("探偵", "detective", "นักสืบ")
        register("警察", "police", "ตำรวจ")
        register("公安", "public security", "security police", "ตำรวจสันติบาล", "ตำรวจความมั่นคง")

        // Family names: intentionally map to *multiple* related printed names.
        // Users searching only a surname expect to see all relevant family members.
        register("工藤", "kudo", "kudou", "คุโด", "คุโด้")
        register("毛利", "mouri", "mori", "โมริ")
        register("服部", "hattori", "ฮัตโตริ", "ฮัตโตริ")
        register("遠山", "toyama", "โทยามะ", "โทยาม่า")
        register("鈴木", "suzuki", "ซึซึกิ", "ซูซูกิ")
        register("宮野", "miyano", "มิยาโนะ")
        register("諸伏", "morofushi", "โมโรฟุชิ")
        register("萩原", "hagiwara family", "ฮางิวาระ")
        register("赤井", "akai family", "อากาอิ ตระกูล")

        return index.mapValues { (_, value) -> value.toSet() }
    }

    /** Scores > 0 are matches. Keep queries empty as "show all". */
    fun score(card: WebCard, query: String): Int {
        val needle = normalize(query)
        if (needle.isEmpty()) return 1

        val num = normalize(card.cardNum)
        val id = normalize(card.cardId)
        val jpName = normalize(card.name)
        val thName = normalize(card.nameTh.orEmpty())
        val pack = normalize(card.pack)

        if (needle == num || needle == id) return 1100
        if (needle == jpName || (thName.isNotEmpty() && needle == thName)) return 1050
        if (num.contains(needle) || id.contains(needle)) return 950
        if (jpName.contains(needle) || (thName.isNotEmpty() && thName.contains(needle))) return 900

        val mapped = aliases[needle]
        if (mapped != null && mapped.any { jpName.contains(normalize(it)) }) return 800

        // Only use prefix search for sufficiently long Latin aliases (never 'ai' or 'ran').
        if (needle.length >= 4 && needle.all { it in 'a'..'z' }) {
            val matchedTargets = aliases.asSequence()
                .filter { (alias, _) -> alias.startsWith(needle) }
                .flatMap { (_, targets) -> targets.asSequence() }
            if (matchedTargets.any { jpName.contains(normalize(it)) }) return 600
        }

        if (pack.contains(needle)) return 200
        return 0
    }

    fun matches(card: WebCard, query: String): Boolean = score(card, query) > 0

    /** Preserves source card order when there is no search; stable on equal scores. */
    fun sortByRelevance(cards: List<WebCard>, query: String): List<WebCard> =
        if (query.isBlank()) cards else cards.sortedByDescending { score(it, query) }
}

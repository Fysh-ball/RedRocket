package site.fysh.redrocket.util

import android.util.Log
import java.text.Normalizer

/**
 * Detection sensitivity level.
 *
 * LOW - life-threatening only: tornado warnings, destructive winds, missile, imminent threat.
 *          RED phrases always trigger. Non-RED score threshold = 9.
 *
 * MEDIUM - serious emergencies: RED phrases + strong action/danger combinations.
 *          Score threshold = 6 (default).
 *
 * HIGH - all alerts: watches, advisories, potential threats.
 *          Score threshold = 4.
 *
 * RED phrases (Step 4) ALWAYS trigger regardless of sensitivity and source trust.
 * The only thing that can prevent a RED phrase from triggering is the Hard Block
 * (explicit test phrase).
 */
enum class AlertSensitivity { HIGH, MEDIUM, LOW }

/**
 * Deterministic emergency detection system with full multilingual support.
 *
 * Decision flow - no middle states:
 *   1. Hard Override - trusted source + extreme action/danger  → TRIGGER instantly
 *   2. Override phrase ("this is not a test")                  → TRIGGER instantly
 *   3. Hard Block - explicit test phrase, no override      → DO NOT TRIGGER instantly
 *   4. RED phrase - life-threatening condition             → TRIGGER (bypasses sensitivity)
 *   5. Score - accumulate points; threshold varies by sensitivity
 *   6. Score >= threshold                                      → TRIGGER
 *   7. Fail-safe - trusted source + urgent structure
 *                       (ALL CAPS / heavy emphasis / case-free script) → TRIGGER
 *   8. Default                                                 → DO NOT TRIGGER
 *
 * Score breakdown:
 *   +5  trusted source (cell broadcast / system emergency alert)
 *   +4  strong action phrase ("evacuate immediately", "take shelter now", …)
 *   +2  moderate action phrase ("seek shelter", "take cover", …)   [exclusive with strong]
 *   +2  danger word ("emergency", "warning", "threat", "severe")
 *   +3  context boost - action + danger word both present
 *   +1  ALL CAPS structure (>70 % uppercase letters, ≥10 chars)
 *   +1  repetition structure (5+ char word appears ≥3 times)
 *   +2  user keyword match
 *   −2  soft test phrase ("exercise", "drill")
 *
 * Phrase list storage conventions:
 *   Latin-script phrases - pre-normalized: lowercase, accents stripped.
 *                           e.g. French "évacuez" → "evacuez",
 *                                German "Übung" → "ubung".
 *   Non-Latin phrases - stored in NFC Unicode form (no pre-normalization needed).
 *   normalize() handles both: strips Latin diacritics via NFD + U+0300–U+036F range strip,
 *   recomposes via NFC (restores e.g. Korean Hangul syllables), then strips all remaining
 *   non-letter/non-number characters. Non-Latin scripts (CJK, Cyrillic, Arabic, etc.)
 *   pass through intact.
 *
 * Languages covered: English, French, Spanish, Portuguese, German, Italian, Dutch,
 *                    Russian, Japanese, Korean, Chinese (Simplified + Traditional), Arabic,
 *                    Turkish, Polish, Ukrainian, Swedish, Norwegian, Danish,
 *                    Indonesian, Hindi, Bengali.
 */
object FalseAlarmDetector {

    private const val TAG = "FalseAlarmDetector"
    private const val TRIGGER_THRESHOLD = 6

    // Hard Override phrases: trusted source + ANY of these → instant TRIGGER
    // Strong action - direct shelter/evacuation commands
    private val STRONG_ACTION_PHRASES = listOf(
        // English
        "evacuate immediately", "evacuate now",
        "take shelter now", "take shelter immediately",
        "take cover now", "take cover immediately", "take immediate cover",
        "seek shelter immediately", "shelter immediately",
        // French (pre-normalized)
        "evacuez immediatement", "mettez vous a l abri immediatement",
        "prenez abri immediatement",
        // Spanish (pre-normalized)
        "evacue inmediatamente", "evacuese ahora",
        "busque refugio inmediatamente", "tome cubierta ahora",
        // Portuguese (pre-normalized)
        "evacue imediatamente", "busque abrigo imediatamente",
        "tome cobertura agora",
        // German
        "sofort evakuieren", "suchen sie sofort schutz",
        "begeben sie sich sofort in sicherheit",
        // Italian
        "evacuate immediatamente", "mettetevi al riparo subito",
        "cercate riparo subito",
        // Dutch
        "evacueer onmiddellijk", "zoek onmiddellijk beschutting",
        // Russian (NFC)
        "немедленно эвакуируйтесь", "немедленно укройтесь",
        "укройтесь немедленно",
        // Japanese (NFC)
        "直ちに避難してください", "今すぐ避難してください",
        "ただちに避難",
        // Korean (NFC)
        "즉시 대피하십시오", "즉시 대피하세요",
        // Chinese Simplified (NFC)
        "立即撤离", "立即避难", "立即疏散",
        // Chinese Traditional (NFC)
        "立即撤離", "立即避難", "立即疏散",
        // Arabic (NFC, no harakat)
        "الإخلاء الفوري", "ابتعد عن المنطقة فورا",
        // Turkish (pre-normalized: ğ→g, ş→s, ü→u, ö→o, ç→c; ı→i for uppercase-safe matching)
        "hemen tahliye edin", "hemen siginaga gidin",
        // Polish (pre-normalized: ą→a, ę→e, ó→o, ć→c, ś→s, ź/ż→z, ń→n; ł preserved)
        "natychmiastowa ewakuacja", "ewakuujcie sie natychmiast",
        // Ukrainian (NFC Cyrillic)
        "негайно евакуюйтеся", "негайно укрийтеся",
        // Swedish (pre-normalized: ä→a, ö→o, å→a)
        "evakuera omedelbart", "ta skydd omedelbart",
        // Norwegian / Danish (ø and æ preserved - no canonical decomposition)
        "evakuer umiddelbart", "ta dekning umiddelbart",
        // Indonesian / Malay
        "segera evakuasi", "segera cari perlindungan",
        // Hindi (NFC Devanagari)
        "तुरंत निकासी करें", "तुरंत शरण लें",
        // Bengali (NFC)
        "অবিলম্বে সরে যান", "অবিলম্বে আশ্রয় নিন"
    )

    // Extreme danger events - any of these from a trusted source = real emergency
    private val EXTREME_DANGER_PHRASES = listOf(
        // English
        "tornado", "missile", "nuclear", "destructive winds",
        "explosion", "detonation", "tsunami", "earthquake",
        "hurricane", "cyclone", "wildfire", "forest fire",
        "flash flood", "storm surge", "ballistic",
        "imminent threat", "life threatening", "life-threatening",
        "radioactive", "nuclear weapon", "nuclear explosion", "radiological",
        // French (pre-normalized)
        "tornade", "vents destructeurs", "ouragan", "tremblement de terre",
        "raz de maree", "eruption volcanique", "inondation", "menace imminente",
        // Spanish (pre-normalized - shared words like "tornado", "explosion" already in English)
        "misil", "vientos destructivos", "detonacion", "terremoto",
        "huracan", "ciclon", "incendio forestal", "inundacion repentina",
        "amenaza inminente", "peligro de vida",
        // Portuguese (pre-normalized)
        "missil", "explosao", "detonacao", "terremoto",
        "furacao", "ciclone", "incendio florestal",
        "ameaca iminente", "perigo de vida",
        // German
        "rakete", "nuklear", "atomar", "destruktive winde",
        "erdbeben", "waldbrand", "sturzflut", "drohende gefahr",
        // Italian
        "nucleare", "venti distruttivi", "esplosione",
        "detonazione", "terremoto", "uragano",
        "incendio boschivo", "alluvione lampo", "minaccia imminente",
        // Dutch
        "raket", "nucleair", "verwoestende winden",
        "aardbeving", "bosbrand", "dreigende gevaar",
        // Russian (NFC)
        "торнадо", "ракета", "ядерный", "ядерное оружие",
        "взрыв", "цунами", "землетрясение", "ураган",
        "лесной пожар", "наводнение", "угроза жизни",
        "неминуемая угроза", "радиоактивный",
        // Japanese (NFC)
        "竜巻", "ミサイル", "核兵器", "爆発", "津波",
        "地震", "台風", "山火事", "洪水", "差し迫った脅威",
        "生命の危険", "放射能",
        // Korean (NFC)
        "토네이도", "미사일", "핵무기", "폭발", "쓰나미",
        "지진", "태풍", "산불", "홍수", "긴박한 위협",
        "생명 위협", "방사능",
        // Chinese Simplified (NFC)
        "龙卷风", "导弹", "核武器", "爆炸", "海啸",
        "地震", "飓风", "山火", "洪水",
        "迫在眉睫的威胁", "生命威胁", "放射性",
        // Chinese Traditional (NFC)
        "龍卷風", "導彈", "核武器", "爆炸", "海嘯",
        "地震", "颶風", "山火", "洪水",
        "迫在眉睫的威脅", "生命威脅", "放射性",
        // Arabic (NFC, no harakat)
        "إعصار", "صاروخ", "نووي", "انفجار", "تسونامي",
        "زلزال", "حريق غابات", "فيضانات", "تهديد وشيك",
        // Turkish (pre-normalized)
        "deprem", "fuze", "nukleer", "kasirga", "firtina",
        "sel", "orman yangini", "ani tehdit",
        // Polish (pre-normalized)
        "trzesienie ziemi", "rakieta", "jadrowy", "huragan",
        "pozar", "powodz", "bezposrednie zagrozenie",
        // Ukrainian (NFC Cyrillic)
        "торнадо", "ракета", "ядерна зброя", "вибух", "цунамі",
        "землетрус", "ураган", "лісова пожежа", "повінь", "загроза життю",
        // Swedish (pre-normalized)
        "missil", "karnvapen", "jordbavning", "orkan", "skogsbrand",
        // Norwegian / Danish (ø preserved)
        "jordskjelv", "jordskælv", "atomvapen", "kjernefysisk",
        // Indonesian / Malay
        "rudal", "nuklir", "gempa bumi", "topan", "banjir",
        "kebakaran hutan", "ancaman mendesak",
        // Hindi (NFC Devanagari)
        "बवंडर", "मिसाइल", "परमाणु", "भूकंप", "सुनामी",
        "तूफान", "बाढ़", "जंगल की आग", "जानलेवा खतरा",
        // Bengali (NFC)
        "টর্নেডো", "ক্ষেপণাস্ত্র", "পারমাণবিক", "ভূমিকম্প",
        "সুনামি", "ঘূর্ণিঝড়", "বন্যা", "জীবনের হুমকি"
    )

    // Override phrases: bypass Hard Block, always TRIGGER
    private val OVERRIDE_PHRASES = listOf(
        // English
        "this is not a test",
        "this is not a drill",
        // French (pre-normalized)
        "ceci n est pas un test", "ceci n est pas un exercice",
        // Spanish (pre-normalized)
        "esto no es una prueba", "esto no es un simulacro",
        // Portuguese (pre-normalized)
        "isso nao e um teste", "isso nao e um simulacro",
        // German
        "dies ist kein test", "dies ist keine ubung",
        // Russian (NFC)
        "это не учения", "это не тренировка",
        // Japanese (NFC)
        "これは訓練ではありません", "これはテストではありません",
        // Korean (NFC)
        "이것은 훈련이 아닙니다", "이것은 테스트가 아닙니다",
        // Chinese Simplified (NFC)
        "这不是演习", "这不是测试",
        // Chinese Traditional (NFC)
        "這不是演習", "這不是測試",
        // Arabic (NFC)
        "هذا ليس تدريبا",
        // Turkish (pre-normalized)
        "bu bir test degil", "bu bir tatbikat degil",
        // Polish (pre-normalized)
        "to nie jest test", "to nie jest cwiczenie",
        // Ukrainian (NFC Cyrillic)
        "це не навчання", "це не тест",
        // Swedish (pre-normalized)
        "detta ar inte ett test", "detta ar inte en ovning",
        // Norwegian / Danish (ø preserved)
        "dette er ikke en øvelse", "dette er ikke en test",
        // Indonesian / Malay
        "ini bukan latihan", "ini bukan tes",
        // Hindi (NFC Devanagari)
        "यह अभ्यास नहीं है", "यह परीक्षण नहीं है",
        // Bengali (NFC)
        "এটি মহড়া নয়", "এটি পরীক্ষা নয়"
    )

    // Hard Block: explicit test phrases → instant DO NOT TRIGGER
    private val HARD_TEST_PHRASES = listOf(
        // English
        "this is a test",
        "this is only a test",
        "this is just a test",
        "this is a drill",
        "this is only a drill",
        "this is just a drill",
        "test of the alert system",
        "test of the emergency",
        // French (pre-normalized)
        "ceci est un test", "ceci est un exercice",
        "test du systeme d alerte",
        // Spanish (pre-normalized)
        "esto es una prueba", "esto es un simulacro",
        "prueba del sistema de alerta",
        // Portuguese (pre-normalized)
        "isso e um teste", "isso e um simulacro",
        "teste do sistema de alerta",
        // German
        "dies ist ein test", "dies ist eine ubung",
        "test des warnsystems",
        // Italian
        "questo e un test", "questa e una esercitazione",
        // Dutch
        "dit is een test", "dit is een oefening",
        // Russian (NFC)
        "это учения", "это тест системы",
        "проверка системы оповещения",
        // Japanese (NFC)
        "これは訓練です", "これはテストです",
        "試験放送", "訓練放送",
        // Korean (NFC)
        "이것은 훈련입니다", "이것은 테스트입니다",
        "경보 시스템 시험",
        // Chinese Simplified (NFC)
        "这是演习", "这是测试", "警报系统测试",
        // Chinese Traditional (NFC)
        "這是演習", "這是測試", "警報系統測試",
        // Arabic (NFC)
        "هذا تدريب", "اختبار نظام الانذار",
        // Turkish (pre-normalized)
        "bu bir test", "bu bir tatbikat",
        // Polish (pre-normalized)
        "to jest test", "to jest cwiczenie", "test systemu alertow",
        // Ukrainian (NFC Cyrillic)
        "це навчання", "це тест системи", "перевірка системи оповіщення",
        // Swedish (pre-normalized)
        "detta ar ett test", "detta ar en ovning",
        // Norwegian / Danish (ø preserved)
        "dette er en øvelse", "dette er en test",
        // Indonesian / Malay
        "ini adalah latihan", "ini adalah tes", "uji sistem peringatan",
        // Hindi (NFC Devanagari)
        "यह एक परीक्षण है", "यह एक अभ्यास है",
        // Bengali (NFC)
        "এটি একটি মহড়া", "এটি একটি পরীক্ষা"
    )

    // RED trigger phrases: life-threatening conditions that ALWAYS trigger
    // These bypass sensitivity (threshold has no effect) but still respect:
    //   • Hard Block (step 3) - explicit test phrase prevents triggering
    // LOW, MEDIUM, and HIGH sensitivity ALL trigger on these.
    private val RED_TRIGGER_PHRASES = listOf(
        // English (pre-normalized)
        "tornado warning", "tornado emergency", "confirmed tornado",
        "destructive winds",
        "ballistic missile", "missile inbound", "missile threat", "missile warning",
        "imminent threat", "imminent danger",
        "nuclear threat", "nuclear attack", "nuclear detonation",
        "nuclear weapon", "nuclear explosion",
        "radioactive", "radiological",
        "life threatening",
        "flash flood warning",
        // French (pre-normalized)
        "avertissement de tornade", "vents destructeurs",
        "menace imminente", "danger imminent",
        // Spanish (pre-normalized)
        "aviso de tornado", "amenaza de misil",
        "amenaza inminente", "peligro inminente", "peligro de vida",
        "vientos destructivos",
        // Portuguese (pre-normalized)
        "aviso de tornado", "ameaca de missil",
        "ameaca iminente", "perigo iminente", "perigo de vida",
        // German
        "tornadowarnung", "raketenwarnung",
        "drohende gefahr", "lebensgefahr", "atomarer angriff",
        // Russian (NFC)
        "предупреждение о торнадо", "ракетная угроза",
        "неминуемая угроза", "угроза жизни", "ядерная атака",
        "радиоактивный",
        // Japanese (NFC)
        "竜巻警報", "ミサイル警報", "ミサイル飛来",
        "生命の危険", "放射能", "緊急地震速報", "大津波警報",
        // Korean (NFC)
        "토네이도 경보", "미사일 경보", "미사일 위협",
        "긴박한 위협", "생명 위협", "방사능",
        "지진 긴급 경보", "대쓰나미 경보",
        // Chinese Simplified (NFC)
        "龙卷风警报", "导弹警报", "导弹威胁",
        "迫在眉睫的威胁", "生命威胁", "放射性物质",
        "海啸警报", "地震警报",
        // Chinese Traditional (NFC)
        "龍卷風警報", "導彈警報", "導彈威脅",
        "迫在眉睫的威脅", "生命威脅", "放射性物質",
        "海嘯警報", "地震警報",
        // Arabic (NFC)
        "تحذير إعصار", "تحذير صاروخي",
        "تهديد وشيك", "خطر وشيك",
        "خطر على الحياة", "مواد مشعة",
        // Turkish (pre-normalized)
        "deprem uyarisi", "fuze uyarisi", "fuze tehdidi",
        "ani tehdit", "yasam tehlikesi",
        // Polish (pre-normalized)
        "ostrzezenie tornadzie", "alert rakietowy",
        "zagrozenie zycia", "bezposrednie zagrozenie",
        // Ukrainian (NFC Cyrillic)
        "попередження про торнадо", "ракетна загроза",
        "ядерний удар", "загроза життю", "радіоактивний",
        // Swedish (pre-normalized)
        "tornadovarning", "robotvarning", "livsfara", "karnvapenanfall",
        // Norwegian / Danish (ø preserved)
        "tornadovarsel", "raketvarsel", "livsfare", "jordskjelvvarsel",
        // Indonesian / Malay
        "peringatan gempa", "peringatan rudal", "ancaman nyawa",
        "ancaman mendesak", "bahaya radioaktif",
        // Hindi (NFC Devanagari)
        "बवंडर चेतावनी", "मिसाइल खतरा", "जानलेवा खतरा",
        "परमाणु हमला", "तत्काल खतरा",
        // Bengali (NFC)
        "টর্নেডো সতর্কতা", "ক্ষেপণাস্ত্র হুমকি",
        "জীবনের হুমকি", "পারমাণবিক আক্রমণ"
    )

    // Strong action phrases: +4 (mutually exclusive with moderate)
    // (same list as STRONG_ACTION_PHRASES - reused for scoring)

    // Moderate action phrases: +2
    private val MODERATE_ACTION_PHRASES = listOf(
        // English
        "seek shelter", "take shelter", "take cover",
        "evacuate", "move to higher ground", "go to basement",
        "remain indoors", "stay indoors", "shelter in place",
        "avoid the area", "stay away",
        "stay in your home", "stay in your house", "stay in your own home",
        "do not go outside", "do not leave your home", "remain inside",
        // French (pre-normalized)
        "abritez vous", "refugiez vous", "evacuez", "prenez abri",
        "restez a l interieur", "eloignez vous",
        // Spanish (pre-normalized)
        "busque refugio", "evacuese", "evacue",
        "permanezca en su casa", "no salga de casa",
        "alejese del area", "dirigase a terreno mas alto",
        // Portuguese (pre-normalized)
        "busque abrigo", "evacue", "permanecer em casa",
        "nao saia de casa", "afaste se da area",
        // German
        "suchen sie schutz", "evakuieren sie", "evakuierung",
        "bleiben sie drinnen", "meiden sie das gebiet",
        // Italian
        "cercare riparo", "evacuare", "restare in casa",
        "non uscire", "allontanarsi",
        // Dutch
        "zoek beschutting", "evacueer", "blijf binnen",
        "vermijd het gebied",
        // Russian (NFC)
        "укройтесь", "эвакуируйтесь", "оставайтесь в помещении",
        "не выходите на улицу", "покиньте район",
        // Japanese (NFC)
        "避難してください", "避難する", "屋内に留まる",
        "外出しないでください", "高台に避難",
        // Korean (NFC)
        "대피하세요", "대피하십시오", "실내에 머무르세요",
        "외출을 자제하세요", "높은 곳으로 대피",
        // Chinese Simplified (NFC)
        "寻找避难所", "撤离", "疏散", "留在室内",
        "不要外出", "远离该地区", "转移到高地",
        // Chinese Traditional (NFC)
        "尋找避難所", "撤離", "疏散", "留在室內",
        "不要外出", "遠離該地區", "轉移到高地",
        // Arabic (NFC, no harakat)
        "ابتعد عن المنطقة", "ابق في الداخل",
        "لا تغادر المنزل", "اخل المنطقة",
        // Turkish (pre-normalized)
        "tahliye edin", "siginaga girin", "ic mekanda kalin",
        "disari cikmayiniz", "bolgeyi terk edin",
        // Polish (pre-normalized)
        "szukaj schronienia", "ewakuuj sie", "pozostan w domu",
        "nie wychodzic", "opusc obszar",
        // Ukrainian (NFC Cyrillic)
        "укрийтеся", "евакуюйтеся", "залишайтеся в приміщенні",
        "не виходьте", "покиньте район",
        // Swedish (pre-normalized)
        "sok skydd", "evakuera", "stanna inomhus",
        "lamna omradet",
        // Norwegian / Danish (ø preserved)
        "søk tilflukt", "evakuer", "bli innendørs",
        "forlat området",
        // Indonesian / Malay
        "cari perlindungan", "evakuasi", "tetap di dalam",
        "jauhi daerah",
        // Hindi (NFC Devanagari)
        "शरण लें", "निकासी करें", "घर के अंदर रहें",
        "क्षेत्र छोड़ें",
        // Bengali (NFC)
        "আশ্রয় নিন", "সরিয়ে নিন", "ঘরের ভেতরে থাকুন",
        "এলাকা ছেড়ে যান"
    )

    // Danger words: +2
    private val DANGER_WORDS = listOf(
        // English
        "emergency", "warning", "threat", "severe", "destructive winds",
        // French (pre-normalized)
        "urgence", "avertissement", "menace", "grave", "vents destructeurs",
        // Spanish (pre-normalized)
        "emergencia", "aviso", "amenaza", "severo",
        // Portuguese (pre-normalized)
        "emergencia", "aviso", "ameaca", "severo",
        // German
        "notfall", "warnung", "bedrohung", "stark",
        // Italian
        "emergenza", "avviso", "minaccia", "grave",
        // Dutch
        "noodgeval", "waarschuwing", "dreiging", "ernstig",
        // Russian (NFC)
        "чрезвычайная ситуация", "предупреждение", "угроза", "опасность",
        // Japanese (NFC)
        "緊急", "警報", "脅威", "危険",
        // Korean (NFC)
        "비상", "경보", "위협", "위험",
        // Chinese Simplified (NFC)
        "紧急", "警报", "威胁", "危险",
        // Chinese Traditional (NFC)
        "緊急", "警報", "威脅", "危險",
        // Arabic (NFC)
        "طوارئ", "تحذير", "تهديد", "خطير",
        // Turkish (pre-normalized)
        "acil durum", "uyari", "tehdit", "siddetli",
        // Polish (pre-normalized)
        "stan wyjatkowy", "ostrzezenie", "zagrozenie", "niebezpieczenstwo",
        // Ukrainian (NFC Cyrillic)
        "надзвичайна ситуація", "попередження", "загроза", "небезпека",
        // Swedish (pre-normalized)
        "nodlage", "varning", "hot", "allvarlig",
        // Norwegian / Danish (ø preserved)
        "nødsituasjon", "nødsituation", "advarsel", "alvorlig",
        // Indonesian / Malay
        "darurat", "peringatan", "ancaman", "parah",
        // Hindi (NFC Devanagari)
        "आपातकाल", "चेतावनी", "खतरा", "गंभीर",
        // Bengali (NFC)
        "জরুরি অবস্থা", "সতর্কতা", "হুমকি", "বিপজ্জনক"
    )

    // Soft test phrases: −2
    private val SOFT_TEST_PHRASES = listOf(
        // English
        "exercise", "drill",
        // French (pre-normalized)
        "exercice",
        // Spanish (pre-normalized)
        "ejercicio", "simulacro",
        // Portuguese (pre-normalized)
        "exercicio",
        // German
        "ubung",
        // Italian
        "esercitazione",
        // Dutch
        "oefening",
        // Russian (NFC)
        "учения", "тренировка",
        // Japanese (NFC)
        "訓練", "演習",
        // Korean (NFC)
        "훈련", "연습",
        // Chinese Simplified (NFC)
        "演习", "训练",
        // Chinese Traditional (NFC)
        "演習", "訓練",
        // Arabic (NFC)
        "تدريب", "مناورة",
        // Turkish (pre-normalized)
        "tatbikat", "egzersiz",
        // Polish (pre-normalized)
        "cwiczenie", "symulacja",
        // Ukrainian (NFC Cyrillic)
        "навчання", "тренування",
        // Swedish (pre-normalized)
        "ovning", "test",
        // Norwegian / Danish (ø preserved)
        "øvelse",
        // Indonesian / Malay
        "latihan", "simulasi",
        // Hindi (NFC Devanagari)
        "अभ्यास", "परीक्षण",
        // Bengali (NFC)
        "মহড়া", "পরীক্ষা"
    )

    // Generic words excluded from word-level keyword matching
    // These appear in nearly every EAS message and carry no scenario-specific meaning.
    private val GENERIC_WORDS = setOf(
        "warning", "alert", "emergency", "attack", "threat",
        "severe", "watch", "issue", "notice", "danger", "urgent"
    )

    // Pre-compiled normalize() regexes - compiled once at class load
    private val RE_COMBINING = Regex("[\u0300-\u036F]")
    private val RE_NON_WORD  = Regex("[^\\p{L}\\p{M}\\p{N}\\s]")
    private val RE_SPACES    = Regex("\\s+")

    /**
     * Returns true if [rawContent] should be suppressed even when a user keyword matched.
     *
     * The user's keyword is authoritative - the only things that override it are:
     *  • Hard Block - built-in test phrases ("this is a test") block triggering,
     *                 unless an Override phrase ("this is not a drill") is also present.
     *  • User Block Phrases - user-defined phrases (any language) checked the same way as
     *                         the built-in Hard Block. Override phrases still cancel them.
     */
    fun isBlockedDespiteKeywordMatch(
        rawContent: String,
        userBlockPhrases: List<String> = emptyList()
    ): Boolean {
        val content = normalize(rawContent)
        val normalizedUserPhrases = userBlockPhrases.map { normalize(it) }
        val hasHardTest = HARD_TEST_PHRASES.any { content.contains(it) }
                       || normalizedUserPhrases.any { it.isNotBlank() && content.contains(it) }
        if (!hasHardTest) return false
        val hasOverride = OVERRIDE_PHRASES.any { content.contains(it) }
        if (!hasOverride) {
            Log.i(TAG, "KEYWORD MATCH SUPPRESSED: Hard Block (explicit test/block phrase)")
            return true
        }
        return false
    }

    /**
     * Returns true if [keyword] matches [contentNorm] (already normalized).
     *
     * The keyword is normalized with the same pipeline as the alert content so that
     * accented user keywords ("évacuation", "Überschwemmung") match their
     * accent-stripped equivalents in the normalized content. Non-Latin keywords
     * (CJK, Arabic, etc.) pass through normalize() unchanged and match directly.
     *
     * Two-tier logic:
     *  1. Exact phrase - "volcano eruption" is a substring of content.
     *  2. Significant-word - any word from the keyword that is ≥4 chars and not a
     *     generic EAS term ("warning", "alert", "emergency", …) appears in content.
     *     Handles the common case where EAS says "Volcano emergency alert" but the
     *     user's keyword is "Volcano Eruption" - "volcano" still fires the match.
     */
    fun keywordMatchesContent(keyword: String, contentNorm: String): Boolean {
        val kwNorm = normalize(keyword)
        if (contentNorm.contains(kwNorm)) return true
        return kwNorm.split("\\s+".toRegex())
            .filter { it.length >= 4 && it !in GENERIC_WORDS }
            .any { word -> contentNorm.contains(word) }
    }

    /**
     * Determines whether an alert should fire.
     *
     * @param message          Full text of the incoming notification/broadcast.
     * @param triggerWords     User-defined keywords for this scenario (empty list = no boost).
     * @param isTrustedSource  True for cell broadcasts and system emergency alert packages.
     *                         Enables Hard Override and structural fail-safe.
     * @param sensitivity      Detection sensitivity - adjusts score threshold for non-RED messages.
     *                         HIGH = 4 (watches/advisories), MEDIUM = 6 (default), LOW = 9 (major threats only).
     *                         RED phrases (Step 4) ALWAYS bypass sensitivity - they trigger at any level.
     */
    fun shouldTrigger(
        message: String,
        triggerWords: List<String>,
        isTrustedSource: Boolean = false,
        sensitivity: AlertSensitivity = AlertSensitivity.MEDIUM
    ): Boolean {
        val threshold = when (sensitivity) {
            AlertSensitivity.HIGH   -> 4
            AlertSensitivity.MEDIUM -> TRIGGER_THRESHOLD
            AlertSensitivity.LOW    -> 9
        }

        // Normalize once - all subsequent checks operate on `content`
        val content = normalize(message)
        Log.d(TAG, "EVALUATING [trusted=$isTrustedSource sensitivity=$sensitivity threshold=$threshold]: \"${content.take(140)}\"")

        // Step 1: Hard Override - trusted source + extreme action or danger
        if (isTrustedSource) {
            val hasStrongAction = STRONG_ACTION_PHRASES.any { content.contains(it) }
            val hasExtremeDanger = EXTREME_DANGER_PHRASES.any { content.contains(it) }
            if (hasStrongAction || hasExtremeDanger) {
                Log.i(TAG, "HARD OVERRIDE: trusted + ${if (hasStrongAction) "strong action" else "extreme danger"} - TRIGGERING")
                return true
            }
        }

        // Step 2: Override phrase - bypasses Hard Block, always triggers
        val hasOverride = OVERRIDE_PHRASES.any { content.contains(it) }
        if (hasOverride) {
            Log.i(TAG, "OVERRIDE PHRASE - TRIGGERING (bypasses all blocks)")
            return true
        }

        // Step 3: Hard Block - explicit test phrase
        val hardTestHit = HARD_TEST_PHRASES.firstOrNull { content.contains(it) }
        if (hardTestHit != null) {
            Log.i(TAG, "HARD BLOCK: '$hardTestHit' - DO NOT TRIGGER")
            return false
        }

        // Step 4: RED phrase - life-threatening condition bypasses sensitivity
        val redHit = RED_TRIGGER_PHRASES.firstOrNull { content.contains(it) }
        if (redHit != null) {
            Log.i(TAG, "RED PHRASE: '$redHit' - life-threatening - TRIGGERING (bypasses sensitivity)")
            return true
        }

        // Step 5: Score accumulation
        var score = 0

        if (isTrustedSource) {
            score += 5
            Log.d(TAG, "SOURCE +5")
        }

        // Action: strong (+4) is mutually exclusive with moderate (+2)
        val hasStrongAction = STRONG_ACTION_PHRASES.any { content.contains(it) }
        val hasModerateAction = MODERATE_ACTION_PHRASES.any { content.contains(it) }
        val hasAnyAction = hasStrongAction || hasModerateAction
        when {
            hasStrongAction   -> { score += 4; Log.d(TAG, "ACTION +4 (strong)") }
            hasModerateAction -> { score += 2; Log.d(TAG, "ACTION +2 (moderate)") }
        }

        // Danger words
        val hasDanger = DANGER_WORDS.any { content.contains(it) }
        if (hasDanger) {
            score += 2
            Log.d(TAG, "DANGER +2")
        }

        // Context boost: action + danger together
        if (hasAnyAction && hasDanger) {
            score += 3
            Log.d(TAG, "CONTEXT BOOST +3")
        }

        // Structure: ALL CAPS
        if (isAllCaps(message)) {
            score += 1
            Log.d(TAG, "STRUCTURE +1 (ALL CAPS)")
        }

        // Structure: word repetition
        if (hasRepetition(content)) {
            score += 1
            Log.d(TAG, "STRUCTURE +1 (repetition)")
        }

        // User keyword match
        if (triggerWords.isNotEmpty() && triggerWords.any { keywordMatchesContent(it, content) }) {
            score += 2
            Log.d(TAG, "KEYWORD MATCH +2")
        }

        // Soft test penalty
        if (SOFT_TEST_PHRASES.any { content.contains(it) }) {
            score -= 2
            Log.d(TAG, "SOFT TEST −2")
        }

        Log.i(TAG, "SCORE: $score / $threshold  [trusted=$isTrustedSource sensitivity=$sensitivity]")

        // Step 6: Score threshold
        if (score >= threshold) {
            Log.i(TAG, "SCORE >= $threshold - TRIGGERING")
            return true
        }

        // Step 7: Structural fail-safe - trusted source + urgent structure
        // Catches alerts in any language: ALL CAPS, heavy punctuation emphasis, or
        // case-free scripts (CJK, Arabic, Hebrew) where isAllCaps() always returns false.
        if (isTrustedSource && isUrgentStructure(message)) {
            Log.i(TAG, "FAIL-SAFE: trusted source + urgent structure - TRIGGERING")
            return true
        }

        Log.i(TAG, "DO NOT TRIGGER: score=$score")
        return false
    }

    /**
     * Lowercase, strip Latin diacritics (NFD decompose → strip U+0300–U+036F combining
     * marks → NFC recompose), then remove all remaining non-letter/non-number characters.
     *
     * Latin scripts: accents are stripped ("é" → "e", "ü" → "u").
     * Non-Latin scripts (CJK, Cyrillic, Arabic, Korean, Hebrew, etc.) pass through intact.
     * \p{M} is preserved in the final filter so Indic combining marks (Devanagari/Bengali
     * vowel matras, which are category Mc/Mn not \p{L}) are not stripped - without this,
     * Hindi and Bengali words would be reduced to bare consonants.
     * NFC recompose after strip prevents Korean Hangul from remaining as decomposed jamo.
     */
    private fun normalize(text: String): String {
        val nfd = Normalizer.normalize(text.lowercase(), Normalizer.Form.NFD)
        val recomposed = Normalizer.normalize(RE_COMBINING.replace(nfd, ""), Normalizer.Form.NFC)
        return RE_SPACES.replace(RE_NON_WORD.replace(recomposed, " "), " ").trim()
    }

    /**
     * Returns true if the original (unnormalized) message is predominantly uppercase.
     * Requires ≥10 alphabetic characters and >70 % uppercase.
     */
    private fun isAllCaps(message: String): Boolean {
        val letters = message.filter { it.isLetter() }
        if (letters.length < 10) return false
        return letters.count { it.isUpperCase() }.toFloat() / letters.length > 0.70f
    }

    /**
     * Returns true if any significant word (≥5 chars) appears ≥3 times.
     * Efficient: single-pass frequency map, no regex.
     */
    private fun hasRepetition(content: String): Boolean {
        val words = content.split(' ')
        if (words.size < 3) return false
        val freq = HashMap<String, Int>(words.size)
        for (w in words) {
            if (w.length >= 5) {
                val count = (freq[w] ?: 0) + 1
                if (count >= 3) return true
                freq[w] = count
            }
        }
        return false
    }

    /**
     * Detects urgency through message structure alone - for unknown or non-Latin languages.
     *
     * Three checks:
     *  1. ALL CAPS (>70 % uppercase letters, ≥10 chars) - common in English/Latin EAS broadcasts.
     *  2. Multiple exclamation marks (≥2) - language-agnostic emphasis signal.
     *  3. Case-free script - if ≥10 letters are present and NONE have case (neither upper nor
     *     lower), the message is in a script without capitalisation (CJK, Arabic, Hebrew, etc.).
     *     Trusted-source messages in these scripts always pass the fail-safe (Step 7) since
     *     phrase matching may not cover every possible phrasing in every language.
     */
    private fun isUrgentStructure(message: String): Boolean {
        if (isAllCaps(message)) return true
        if (message.count { it == '!' } >= 2) return true
        val letters = message.filter { it.isLetter() }
        if (letters.length >= 10 && letters.none { it.isUpperCase() || it.isLowerCase() }) return true
        return false
    }
}

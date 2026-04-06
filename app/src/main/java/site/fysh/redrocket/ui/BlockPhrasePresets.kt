package site.fysh.redrocket.ui

/**
 * Region-specific block phrase presets for EAS/WEA/emergency alert system test messages.
 *
 * Phrases are pre-normalized where possible:
 *   - Latin-script: lowercase, common diacritics stripped (match FalseAlarmDetector normalize())
 *   - Non-Latin (CJK, Cyrillic, Arabic, Devanagari, etc.): stored in NFC Unicode form
 *
 * dialCode is stored so the region picker can display it and phone normalization logic
 * can use it as a hint (see normalizePhone(String, String) in PhoneUtils.kt).
 */
data class RegionPreset(
    val countryCode: String,    // ISO 3166-1 alpha-2
    val displayName: String,
    val flag: String,
    val dialCode: String,       // e.g. "+1", "+44" - informational + phone normalization hint
    val phrases: List<String>   // pre-normalized block phrases in the local language(s)
)

val REGION_PRESETS: List<RegionPreset> = listOf(

    RegionPreset("AU", "Australia", "🇦🇺", "+61", listOf(
        "this is a test",
        "standard emergency warning signal",
        "sews test",
        "emergency alert test",
        "this is a test message from emergency alert",
        "test of the emergency alert system"
    )),

    RegionPreset("BE", "Belgium", "🇧🇪", "+32", listOf(
        "dit is een test",
        "be alert test",
        "ceci est un test",
        "test be alert",
        "dies ist ein test"
    )),

    RegionPreset("BR", "Brazil", "🇧🇷", "+55", listOf(
        "isso e um teste",
        "teste do sistema de alerta",
        "alerta de emergencia teste",
        "teste do sistema nacional de alertas"
    )),

    RegionPreset("CA", "Canada", "🇨🇦", "+1", listOf(
        // English
        "this is a test",
        "this is only a test",
        "test of the emergency alert system",
        "alertready",
        "alert ready test",
        // French Canadian (pre-normalized: accents stripped)
        "ceci est un test",
        "ceci est un exercice",
        "ceci est un test du systeme d alertes au public",
        "alertes en direct test"
    )),

    RegionPreset("CN", "China", "🇨🇳", "+86", listOf(
        // NFC Simplified Chinese
        "这是演习",
        "这是测试",
        "应急广播测试",
        "国家预警信息发布系统测试"
    )),

    RegionPreset("DK", "Denmark", "🇩🇰", "+45", listOf(
        "dette er en test",
        "provealarm",
        "prøvealarm",
        "dk varsel test",
        "beredskabsstyrelsen test"
    )),

    RegionPreset("FI", "Finland", "🇫🇮", "+358", listOf(
        "tama on testi",
        "harjoitusviesti",
        "viranomaisviesti testi",
        "fi alert test",
        "vaestohalytin testi"
    )),

    RegionPreset("FR", "France", "🇫🇷", "+33", listOf(
        "ceci est un test",
        "ceci est un exercice",
        "test du systeme d alerte",
        "alerte aux populations test",
        "systeme d alerte et d information des populations test"
    )),

    RegionPreset("DE", "Germany", "🇩🇪", "+49", listOf(
        "dies ist ein test",
        "dies ist eine ubung",
        "probealarm",
        "warntag",
        "bundesweiter warntag",
        "test des warnsystems",
        "katastrophenschutz probe"
    )),

    RegionPreset("IN", "India", "🇮🇳", "+91", listOf(
        // English
        "this is a test",
        "common alerting protocol test",
        "cap test",
        // NFC Devanagari (Hindi)
        "यह परीक्षण है",
        "यह अभ्यास है"
    )),

    RegionPreset("ID", "Indonesia", "🇮🇩", "+62", listOf(
        "ini adalah tes",
        "ini adalah latihan",
        "pesan siaga darurat tes",
        "tes sistem peringatan dini",
        "simulasi bencana"
    )),

    RegionPreset("IE", "Ireland", "🇮🇪", "+353", listOf(
        "this is a test",
        "eu alert test",
        "test of the emergency alert system",
        "alerta test"
    )),

    RegionPreset("IT", "Italy", "🇮🇹", "+39", listOf(
        "questo e un test",
        "questo e un messaggio di prova",
        "prova del sistema di allerta",
        "it alert test",
        "questa e una esercitazione"
    )),

    RegionPreset("JP", "Japan", "🇯🇵", "+81", listOf(
        // NFC Japanese
        "訓練放送",
        "試験放送",
        "これは訓練です",
        "jアラート訓練",
        "緊急速報訓練"
    )),

    RegionPreset("MX", "Mexico", "🇲🇽", "+52", listOf(
        "esto es una prueba",
        "prueba del sistema de alertas civiles",
        "alerta sismica prueba",
        "sismo de prueba",
        "prueba del sistema de alerta temprana"
    )),

    RegionPreset("NL", "Netherlands", "🇳🇱", "+31", listOf(
        "dit is een test",
        "nl alert test",
        "proefmelding",
        "nlalert test",
        "test van het alarmeringssysteem"
    )),

    RegionPreset("NZ", "New Zealand", "🇳🇿", "+64", listOf(
        "this is a test",
        "civil defence test",
        "civil defense test",
        "emergency mobile alert test",
        "ema test"
    )),

    RegionPreset("NG", "Nigeria", "🇳🇬", "+234", listOf(
        "this is a test",
        "test of the emergency broadcast",
        "nema test",
        "national emergency management agency test"
    )),

    RegionPreset("NO", "Norway", "🇳🇴", "+47", listOf(
        "dette er en test",
        "varsling test",
        // ø has no NFD decomposition - stored as-is
        "prøvealarm",
        "nødvarsling test"
    )),

    RegionPreset("PH", "Philippines", "🇵🇭", "+63", listOf(
        // English
        "this is a test",
        "this is a drill",
        // Filipino/Tagalog
        "ito ay isang pagsubok",
        "ito ay isang pagsasanay",
        "pagsubok ng sistema ng babala",
        "ndrrmc test"
    )),

    RegionPreset("PL", "Poland", "🇵🇱", "+48", listOf(
        "to jest test",
        "to cwiczenia",
        "test systemu ostrzegania",
        "cwiczenia obronne",
        "alert rzadowy test"
    )),

    RegionPreset("PT", "Portugal", "🇵🇹", "+351", listOf(
        "isso e um teste",
        "teste do sistema de alerta",
        "siresp teste",
        "sistema integrado de redes de emergencia e seguranca de portugal teste"
    )),

    RegionPreset("RU", "Russia", "🇷🇺", "+7", listOf(
        // NFC Cyrillic
        "это тест системы",
        "это учения",
        "тестовое оповещение",
        "проверка системы оповещения",
        "учебная тревога"
    )),

    RegionPreset("SA", "Saudi Arabia", "🇸🇦", "+966", listOf(
        // NFC Arabic
        "هذا تدريب",
        "اختبار نظام التنبيه",
        "تجربة نظام الإنذار",
        "رسالة تجريبية"
    )),

    RegionPreset("ZA", "South Africa", "🇿🇦", "+27", listOf(
        "this is a test",
        "this is a drill",
        "emergency alert test",
        // Afrikaans
        "dis n toets",
        "dis slegs n toets"
    )),

    RegionPreset("KR", "South Korea", "🇰🇷", "+82", listOf(
        // NFC Korean
        "이것은 훈련입니다",
        "재난문자 훈련",
        "경보 시스템 시험",
        "민방위 훈련",
        "재난 대피 훈련"
    )),

    RegionPreset("ES", "Spain", "🇪🇸", "+34", listOf(
        "esto es una prueba",
        "prueba del sistema de alertas",
        "prueba del sistema es alert",
        "es alert prueba",
        "esto es un simulacro"
    )),

    RegionPreset("SE", "Sweden", "🇸🇪", "+46", listOf(
        "detta ar ett test",
        "detta ar en ovning",
        "vma test",
        "viktigt meddelande till allmanhet test",
        "provlarm",
        "raddningstjanst test"
    )),

    RegionPreset("TW", "Taiwan", "🇹🇼", "+886", listOf(
        // NFC Traditional Chinese
        "這是演習",
        "這是測試",
        "國家警報測試",
        "防災演練"
    )),

    RegionPreset("TR", "Turkey", "🇹🇷", "+90", listOf(
        // Pre-normalized: ğ→g, ş→s, ü→u, ö→o, ç→c, ı→i
        "bu bir test",
        "bu bir tatbikat",
        "acil durum uyarisi testi",
        "afad test mesaji",
        "tatbikat mesaji"
    )),

    RegionPreset("AE", "UAE", "🇦🇪", "+971", listOf(
        // NFC Arabic
        "هذا تدريب",
        "ncema test",
        "اختبار الانذار",
        "رسالة اختبارية"
    )),

    RegionPreset("UA", "Ukraine", "🇺🇦", "+380", listOf(
        // NFC Cyrillic
        "це навчання",
        "це тестова тривога",
        "тест системи оповіщення",
        "тренувальна тривога"
    )),

    RegionPreset("GB", "United Kingdom", "🇬🇧", "+44", listOf(
        "this is a test",
        "test of the emergency alerts service",
        "emergency alerts test",
        "this is a test of the uk emergency alert system",
        "gov uk alerts test"
    )),

    RegionPreset("US", "United States", "🇺🇸", "+1", listOf(
        "this is a test",
        "this is only a test",
        "this is a required monthly test",
        "this is a required weekly test",
        "this is a drill",
        "nationwide test",
        "test of the emergency alert system",
        "test of the emergency broadcasting system",
        "ipaws"
    ))
)

/**
 * Returns the RegionPreset for the given ISO 3166-1 alpha-2 country code.
 * Falls back to US if the country code is unknown.
 */
fun regionPresetFor(countryCode: String): RegionPreset =
    REGION_PRESETS.find { it.countryCode.equals(countryCode, ignoreCase = true) }
        ?: REGION_PRESETS.first { it.countryCode == "US" }

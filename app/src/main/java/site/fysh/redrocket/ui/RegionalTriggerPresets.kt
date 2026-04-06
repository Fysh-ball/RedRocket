package site.fysh.redrocket.ui

/**
 * Localized trigger keyword presets organized by language group.
 *
 * Keywords are stored in their natural form (with diacritics / native script).
 * FalseAlarmDetector.normalize() strips Latin diacritics and lowercases at match time,
 * so storing "Séisme" is equivalent to storing "seisme" for matching purposes.
 * Non-Latin keywords (CJK, Cyrillic, Arabic, Devanagari) are stored in NFC Unicode form.
 *
 * Category names are kept in English - the app UI is English.
 * Keywords inside each category are in the local language.
 *
 * "Hurricane" becomes "Typhoon" for East/South-East Asian regions where that term is used,
 * and "Cyclone" for India. All other category names are identical across regions.
 *
 * Categories (alphabetical):
 *   AMBER Alert · Chemical / Hazmat · Earthquake · Flood · General Emergency ·
 *   Hurricane / Cyclone · Nuclear / Radiological · Nuclear Missile Strike ·
 *   Severe Thunderstorm · Tornado · Tsunami · Volcano · Wildfire · Winter Storm
 */

// English
private val PRESETS_EN = listOf(
    TriggerPreset("AMBER Alert", listOf(
        "amber alert", "child abduction emergency", "missing child", "child abduction"
    ), "🚨"),
    TriggerPreset("Chemical / Hazmat", listOf(
        "hazmat", "hazardous materials", "chemical spill",
        "toxic release", "shelter in place", "chemical emergency"
    ), "⚠️"),
    TriggerPreset("Earthquake", listOf(
        "earthquake warning", "earthquake", "seismic alert",
        "tremor", "aftershock"
    ), "⚡"),
    TriggerPreset("Flood", listOf(
        "flash flood warning", "flood warning", "flood emergency",
        "flood advisory", "coastal flooding"
    ), "💧"),
    TriggerPreset("General Emergency", listOf(
        "evacuation immediate", "civil danger warning",
        "immediate threat to life", "disaster declaration"
    ), "🆘"),
    TriggerPreset("Hurricane / Cyclone", listOf(
        "hurricane warning", "hurricane emergency", "tropical storm warning",
        "storm surge warning", "cyclone warning", "extreme wind warning"
    ), "🌀"),
    TriggerPreset("Nuclear / Radiological", listOf(
        "nuclear power plant", "radiological hazard", "radiation warning",
        "fallout", "nuclear incident", "radioactive release"
    ), "☢️"),
    TriggerPreset("Nuclear Missile Strike", listOf(
        "missile warning", "ballistic missile", "nuclear strike",
        "incoming missile"
    ), "🚀"),
    TriggerPreset("Severe Thunderstorm", listOf(
        "severe thunderstorm warning", "severe thunderstorm",
        "destructive hail", "damaging winds", "thunderstorm emergency"
    ), "⛈️"),
    TriggerPreset("Tornado", listOf(
        "tornado warning", "tornado emergency", "confirmed tornado",
        "radar indicated tornado"
    ), "🌪️"),
    TriggerPreset("Tsunami", listOf(
        "tsunami warning", "tsunami advisory", "tsunami watch",
        "move to higher ground", "tsunami threat"
    ), "🌊"),
    TriggerPreset("Volcano", listOf(
        "volcanic eruption", "volcano warning", "lava flow",
        "ashfall warning", "eruption warning"
    ), "🌋"),
    TriggerPreset("Wildfire", listOf(
        "wildfire", "red flag warning", "fire evacuation order",
        "forest fire", "brush fire", "fire warning"
    ), "🔥"),
    TriggerPreset("Winter Storm", listOf(
        "winter storm warning", "blizzard warning", "ice storm warning",
        "winter storm watch", "heavy snow warning"
    ), "❄️")
)

// French
private val PRESETS_FR = listOf(
    TriggerPreset("AMBER Alert", listOf(
        "alerte enlèvement", "amber alert", "enfant disparu", "enlèvement d'enfant"
    ), "🚨"),
    TriggerPreset("Chemical / Hazmat", listOf(
        "matières dangereuses", "déversement chimique", "alerte chimique", "nuage toxique"
    ), "⚠️"),
    TriggerPreset("Earthquake", listOf(
        "séisme", "tremblement de terre", "alerte sismique", "secousse", "réplique sismique"
    ), "⚡"),
    TriggerPreset("Flood", listOf(
        "inondation", "crue soudaine", "alerte aux inondations", "montée des eaux", "débordement"
    ), "💧"),
    TriggerPreset("General Emergency", listOf(
        "urgence", "alerte", "catastrophe", "évacuation"
    ), "🆘"),
    TriggerPreset("Hurricane", listOf(
        "ouragan", "cyclone", "alerte cyclone", "tempête tropicale", "onde de tempête"
    ), "🌀"),
    TriggerPreset("Nuclear / Radiological", listOf(
        "nucléaire", "alerte nucléaire", "retombées radioactives", "centrale nucléaire", "incident radiologique"
    ), "☢️"),
    TriggerPreset("Nuclear Missile Strike", listOf(
        "missile", "missile balistique", "alerte missile", "menace missile"
    ), "🚀"),
    TriggerPreset("Severe Thunderstorm", listOf(
        "avertissement d'orage violent", "orage violent", "grêle destructrice", "vents violents"
    ), "⛈️"),
    TriggerPreset("Tornado", listOf(
        "avertissement de tornade", "tornade", "trombe", "risque de tornade"
    ), "🌪️"),
    TriggerPreset("Tsunami", listOf(
        "tsunami", "avertissement de tsunami", "raz-de-marée", "alerte côtière"
    ), "🌊"),
    TriggerPreset("Volcano", listOf(
        "éruption volcanique", "volcan", "lave", "cendres volcaniques", "avertissement volcanique"
    ), "🌋"),
    TriggerPreset("Wildfire", listOf(
        "incendie de forêt", "feu de forêt", "évacuer incendie", "incendie", "alerte incendie"
    ), "🔥"),
    TriggerPreset("Winter Storm", listOf(
        "avertissement de tempête hivernale", "tempête de neige", "blizzard", "verglas", "neige abondante"
    ), "❄️")
)

// German
private val PRESETS_DE = listOf(
    TriggerPreset("AMBER Alert", listOf(
        "AMBER Alert", "vermisstes Kind", "Kindesentführung", "Kind vermisst"
    ), "🚨"),
    TriggerPreset("Chemical / Hazmat", listOf(
        "Gefahrgut", "Chemieunfall", "Giftgas", "chemischer Unfall", "Schadstoffwarnung"
    ), "⚠️"),
    TriggerPreset("Earthquake", listOf(
        "Erdbeben", "Erdbebenwarnung", "seismisch", "Erschütterung", "Nachbeben"
    ), "⚡"),
    TriggerPreset("Flood", listOf(
        "Hochwasser", "Überschwemmung", "Hochwasserwarnung", "Sturzflut", "steigende Wasserpegel"
    ), "💧"),
    TriggerPreset("General Emergency", listOf(
        "Notfall", "Warnung", "Katastrophe", "Evakuierung", "Warnsignal"
    ), "🆘"),
    TriggerPreset("Hurricane", listOf(
        "Hurrikan", "Zyklon", "Tropensturm", "Sturmflut", "Orkanwarnung"
    ), "🌀"),
    TriggerPreset("Nuclear / Radiological", listOf(
        "nuklear", "radioaktiv", "Kernkraftunfall", "Strahlungswarnung", "nuklearer Zwischenfall"
    ), "☢️"),
    TriggerPreset("Nuclear Missile Strike", listOf(
        "Rakete", "Raketenwarnung", "ballistische Rakete", "Raketenalarm"
    ), "🚀"),
    TriggerPreset("Severe Thunderstorm", listOf(
        "Unwetterwarnung", "schweres Gewitter", "Hagel", "starke Böen", "Gewitterwarnung"
    ), "⛈️"),
    TriggerPreset("Tornado", listOf(
        "Tornadowarnung", "Tornado", "Windhose", "Tornadogefahr"
    ), "🌪️"),
    TriggerPreset("Tsunami", listOf(
        "Tsunami", "Tsunamiwarnung", "Flutwelle", "Küstenwarnung"
    ), "🌊"),
    TriggerPreset("Volcano", listOf(
        "Vulkanausbruch", "Vulkanwarnung", "Lavafluss", "Vulkanasche", "Eruption"
    ), "🌋"),
    TriggerPreset("Wildfire", listOf(
        "Waldbrand", "Waldbrandwarnung", "Feuerwarnung", "Evakuierung Feuer", "Flächenbrand"
    ), "🔥"),
    TriggerPreset("Winter Storm", listOf(
        "Wintersturnwarnung", "Schneesturm", "Blizzard", "Eisregen", "starker Schneefall"
    ), "❄️")
)

// Italian
private val PRESETS_IT = listOf(
    TriggerPreset("AMBER Alert", listOf(
        "amber alert", "bambino scomparso", "rapimento di minore", "child abduction"
    ), "🚨"),
    TriggerPreset("Chemical / Hazmat", listOf(
        "materiali pericolosi", "sversamento chimico", "nube tossica", "emergenza chimica"
    ), "⚠️"),
    TriggerPreset("Earthquake", listOf(
        "terremoto", "sisma", "allerta sismica", "scossa", "scosse di assestamento"
    ), "⚡"),
    TriggerPreset("Flood", listOf(
        "alluvione", "inondazione", "piena improvvisa", "allagamento", "rischio idrogeologico"
    ), "💧"),
    TriggerPreset("General Emergency", listOf(
        "emergenza", "allerta", "catastrofe", "evacuazione", "protezione civile"
    ), "🆘"),
    TriggerPreset("Hurricane", listOf(
        "uragano", "ciclone", "tempesta tropicale", "mareggiata", "allerta meteo"
    ), "🌀"),
    TriggerPreset("Nuclear / Radiological", listOf(
        "nucleare", "allerta nucleare", "ricadute radioattive", "centrale nucleare", "incidente nucleare"
    ), "☢️"),
    TriggerPreset("Nuclear Missile Strike", listOf(
        "missile", "missile balistico", "allarme missile", "minaccia missilistica"
    ), "🚀"),
    TriggerPreset("Severe Thunderstorm", listOf(
        "allerta temporale", "temporale violento", "grandine", "vento forte", "allerta meteo arancione"
    ), "⛈️"),
    TriggerPreset("Tornado", listOf(
        "avviso tornado", "tornado", "tromba d'aria", "pericolo tornado"
    ), "🌪️"),
    TriggerPreset("Tsunami", listOf(
        "tsunami", "allerta tsunami", "onda anomala", "allerta costiera"
    ), "🌊"),
    TriggerPreset("Volcano", listOf(
        "eruzione vulcanica", "vulcano", "lava", "cenere vulcanica", "allerta vulcanica"
    ), "🌋"),
    TriggerPreset("Wildfire", listOf(
        "incendio boschivo", "incendio forestale", "allerta incendi", "evacuazione incendio"
    ), "🔥"),
    TriggerPreset("Winter Storm", listOf(
        "allerta neve", "bufera di neve", "blizzard", "pioggia gelata", "neve abbondante"
    ), "❄️")
)

// Spanish
private val PRESETS_ES = listOf(
    TriggerPreset("AMBER Alert", listOf(
        "alerta amber", "niño desaparecido", "alerta de secuestro", "menor desaparecido"
    ), "🚨"),
    TriggerPreset("Chemical / Hazmat", listOf(
        "materiales peligrosos", "derrame químico", "nube tóxica", "emergencia química"
    ), "⚠️"),
    TriggerPreset("Earthquake", listOf(
        "terremoto", "sismo", "alerta sísmica", "temblor", "réplica sísmica"
    ), "⚡"),
    TriggerPreset("Flood", listOf(
        "inundación", "avenida repentina", "alerta de inundación", "crecida", "desbordamiento"
    ), "💧"),
    TriggerPreset("General Emergency", listOf(
        "emergencia", "alerta", "desastre", "evacuación"
    ), "🆘"),
    TriggerPreset("Hurricane", listOf(
        "huracán", "ciclón", "tormenta tropical", "marejada ciclónica", "alerta de ciclón"
    ), "🌀"),
    TriggerPreset("Nuclear / Radiological", listOf(
        "nuclear", "alerta nuclear", "lluvia radiactiva", "central nuclear", "incidente nuclear"
    ), "☢️"),
    TriggerPreset("Nuclear Missile Strike", listOf(
        "misil", "misil balístico", "alerta de misil", "amenaza de misil"
    ), "🚀"),
    TriggerPreset("Severe Thunderstorm", listOf(
        "aviso de tormenta severa", "tormenta severa", "granizo", "vientos dañinos", "tormenta eléctrica"
    ), "⛈️"),
    TriggerPreset("Tornado", listOf(
        "aviso de tornado", "tornado", "tromba de aire", "peligro de tornado"
    ), "🌪️"),
    TriggerPreset("Tsunami", listOf(
        "tsunami", "alerta de tsunami", "maremoto", "alerta costera"
    ), "🌊"),
    TriggerPreset("Volcano", listOf(
        "erupción volcánica", "volcán", "lava", "cenizas volcánicas", "alerta volcánica"
    ), "🌋"),
    TriggerPreset("Wildfire", listOf(
        "incendio forestal", "alerta de incendio", "evacuar incendio", "fuego forestal"
    ), "🔥"),
    TriggerPreset("Winter Storm", listOf(
        "aviso de tormenta invernal", "tormenta de nieve", "ventisca", "hielo", "nevada intensa"
    ), "❄️")
)

// Portuguese
private val PRESETS_PT = listOf(
    TriggerPreset("AMBER Alert", listOf(
        "alerta amber", "criança desaparecida", "rapto de criança", "menor desaparecido"
    ), "🚨"),
    TriggerPreset("Chemical / Hazmat", listOf(
        "materiais perigosos", "derramamento químico", "nuvem tóxica", "emergência química"
    ), "⚠️"),
    TriggerPreset("Earthquake", listOf(
        "terremoto", "sismo", "alerta sísmico", "tremor", "réplica sísmica"
    ), "⚡"),
    TriggerPreset("Flood", listOf(
        "inundação", "enchente repentina", "alerta de inundação", "cheia", "transbordamento"
    ), "💧"),
    TriggerPreset("General Emergency", listOf(
        "emergência", "alerta", "desastre", "evacuação"
    ), "🆘"),
    TriggerPreset("Hurricane", listOf(
        "furacão", "ciclone", "tempestade tropical", "maré de tempestade", "alerta de ciclone"
    ), "🌀"),
    TriggerPreset("Nuclear / Radiological", listOf(
        "nuclear", "alerta nuclear", "chuva radioativa", "usina nuclear", "incidente nuclear"
    ), "☢️"),
    TriggerPreset("Nuclear Missile Strike", listOf(
        "míssil", "míssil balístico", "alerta de míssil", "ameaça de míssil"
    ), "🚀"),
    TriggerPreset("Severe Thunderstorm", listOf(
        "aviso de tempestade severa", "tempestade severa", "granizo", "ventos fortes", "trovoada intensa"
    ), "⛈️"),
    TriggerPreset("Tornado", listOf(
        "aviso de tornado", "tornado", "tromba d'água", "perigo de tornado"
    ), "🌪️"),
    TriggerPreset("Tsunami", listOf(
        "tsunami", "alerta de tsunami", "maremoto", "alerta costeira"
    ), "🌊"),
    TriggerPreset("Volcano", listOf(
        "erupção vulcânica", "vulcão", "lava", "cinzas vulcânicas", "alerta vulcânico"
    ), "🌋"),
    TriggerPreset("Wildfire", listOf(
        "incêndio florestal", "alerta de incêndio", "fogo florestal", "evacuar incêndio"
    ), "🔥"),
    TriggerPreset("Winter Storm", listOf(
        "aviso de tempestade de inverno", "nevasca", "blizzard", "chuva gelada", "neve intensa"
    ), "❄️")
)

// Dutch
private val PRESETS_NL = listOf(
    TriggerPreset("AMBER Alert", listOf(
        "amber alert", "vermist kind", "kindontvoering", "kind vermist"
    ), "🚨"),
    TriggerPreset("Chemical / Hazmat", listOf(
        "gevaarlijke stoffen", "chemische lekkage", "gifwolk", "chemisch noodgeval"
    ), "⚠️"),
    TriggerPreset("Earthquake", listOf(
        "aardbeving", "aardbevingswaarschuwing", "seismisch", "naschok"
    ), "⚡"),
    TriggerPreset("Flood", listOf(
        "overstroming", "hoogwater", "overstromingswaarschuwing", "plotselinge vloed"
    ), "💧"),
    TriggerPreset("General Emergency", listOf(
        "noodgeval", "waarschuwing", "ramp", "evacuatie", "NL-Alert"
    ), "🆘"),
    TriggerPreset("Hurricane", listOf(
        "orkaan", "cycloon", "tropische storm", "stormaanval", "stormwaarschuwing"
    ), "🌀"),
    TriggerPreset("Nuclear / Radiological", listOf(
        "nucleair", "nucleaire dreiging", "radioactieve neerslag", "kerncentrale", "nucleair incident"
    ), "☢️"),
    TriggerPreset("Nuclear Missile Strike", listOf(
        "raket", "ballistische raket", "raketalarm", "raketdreiging"
    ), "🚀"),
    TriggerPreset("Severe Thunderstorm", listOf(
        "onweer waarschuwing", "zwaar onweer", "hagel", "stormachtige wind", "code rood onweer"
    ), "⛈️"),
    TriggerPreset("Tornado", listOf(
        "tornado waarschuwing", "tornado", "windhoos", "tornado gevaar"
    ), "🌪️"),
    TriggerPreset("Tsunami", listOf(
        "tsunami", "tsunamiwaarschuwing", "vloedgolf", "kustwaarschuwing"
    ), "🌊"),
    TriggerPreset("Volcano", listOf(
        "vulkaanuitbarsting", "vulkaan", "lava", "vulkaanas", "vulkaanwaarschuwing"
    ), "🌋"),
    TriggerPreset("Wildfire", listOf(
        "bosbrand", "brandalarm", "bosbrandwaarschuwing", "evacuatie brand"
    ), "🔥"),
    TriggerPreset("Winter Storm", listOf(
        "winter storm waarschuwing", "sneeuwstorm", "blizzard", "ijzel", "zware sneeuwval"
    ), "❄️")
)

// Swedish
private val PRESETS_SV = listOf(
    TriggerPreset("AMBER Alert", listOf(
        "amber alert", "saknat barn", "barnbortförande", "barn försvunnet"
    ), "🚨"),
    TriggerPreset("Chemical / Hazmat", listOf(
        "farligt gods", "kemikalieolycka", "giftig gas", "kemisk nödsituation"
    ), "⚠️"),
    TriggerPreset("Earthquake", listOf(
        "jordbävning", "jordbävningsvarning", "seismisk aktivitet", "skalv"
    ), "⚡"),
    TriggerPreset("Flood", listOf(
        "översvämning", "högvatten", "översvämningsvarning", "skyfall"
    ), "💧"),
    TriggerPreset("General Emergency", listOf(
        "nödläge", "varning", "katastrof", "evakuering", "VMA", "viktigt meddelande"
    ), "🆘"),
    TriggerPreset("Hurricane", listOf(
        "orkan", "cyklon", "tropisk storm", "stormflod", "orkanvarning"
    ), "🌀"),
    TriggerPreset("Nuclear / Radiological", listOf(
        "kärnkraft", "kärnvapenhot", "radioaktivt nedfall", "kärnkraftverk", "nukleär incident"
    ), "☢️"),
    TriggerPreset("Nuclear Missile Strike", listOf(
        "missil", "ballistisk missil", "missilvarning", "missillarm"
    ), "🚀"),
    TriggerPreset("Severe Thunderstorm", listOf(
        "oväder varning", "kraftigt åskväder", "skadlig hagel", "stormbyar", "oväder"
    ), "⛈️"),
    TriggerPreset("Tornado", listOf(
        "tornadovarning", "tornado", "virvelvind", "vindvartecken"
    ), "🌪️"),
    TriggerPreset("Tsunami", listOf(
        "tsunami", "tsunamivarning", "flodvåg", "kustlarm"
    ), "🌊"),
    TriggerPreset("Volcano", listOf(
        "vulkanutbrott", "vulkan", "lavaflöde", "vulkanska", "vulkanvarning"
    ), "🌋"),
    TriggerPreset("Wildfire", listOf(
        "skogsbrand", "brandvarning", "evakuera brand", "naturkatastrof brand"
    ), "🔥"),
    TriggerPreset("Winter Storm", listOf(
        "vinterstormvarning", "snöstorm", "blizzard", "isregn", "ymnig snöfall"
    ), "❄️")
)

// Norwegian
private val PRESETS_NO = listOf(
    TriggerPreset("AMBER Alert", listOf(
        "amber alert", "savnet barn", "barnebortføring", "barn forsvunnet"
    ), "🚨"),
    TriggerPreset("Chemical / Hazmat", listOf(
        "farlig gods", "kjemikalieutslipp", "giftig sky", "kjemisk nødsituasjon"
    ), "⚠️"),
    TriggerPreset("Earthquake", listOf(
        "jordskjelv", "jordskjelvvarsel", "seismisk aktivitet", "etterskjelv"
    ), "⚡"),
    TriggerPreset("Flood", listOf(
        "flom", "oversvømmelse", "flomvarsel", "styrtregn"
    ), "💧"),
    TriggerPreset("General Emergency", listOf(
        "nødsituasjon", "varsel", "katastrofe", "evakuering", "nødvarsling", "befolkningsvarsling"
    ), "🆘"),
    TriggerPreset("Hurricane", listOf(
        "orkan", "syklon", "tropisk storm", "stormflo", "orkanvarsel"
    ), "🌀"),
    TriggerPreset("Nuclear / Radiological", listOf(
        "kjernekraft", "radioaktivt nedfall", "kjernefysisk trussel", "atomkraftverk", "nukleær hendelse"
    ), "☢️"),
    TriggerPreset("Nuclear Missile Strike", listOf(
        "missil", "ballistisk missil", "missilvarsel", "missilalarm"
    ), "🚀"),
    TriggerPreset("Severe Thunderstorm", listOf(
        "uvær varsel", "kraftig tordenvær", "skadevoldende hagl", "sterk vind", "uvær"
    ), "⛈️"),
    TriggerPreset("Tornado", listOf(
        "tornadovarsel", "tornado", "vindhvirvel"
    ), "🌪️"),
    TriggerPreset("Tsunami", listOf(
        "tsunami", "tsunamivarsel", "flodbølge", "kystvarsel"
    ), "🌊"),
    TriggerPreset("Volcano", listOf(
        "vulkanutbrudd", "vulkan", "lavastrøm", "vulkansk aske", "vulkanvarsel"
    ), "🌋"),
    TriggerPreset("Wildfire", listOf(
        "skogbrann", "brannvarsel", "evakuer brann", "gressbrann"
    ), "🔥"),
    TriggerPreset("Winter Storm", listOf(
        "vinterstormvarsel", "snøstorm", "blizzard", "isregn", "kraftig snøfall"
    ), "❄️")
)

// Danish
private val PRESETS_DA = listOf(
    TriggerPreset("AMBER Alert", listOf(
        "amber alert", "savnet barn", "bortføring af barn", "barn forsvundet"
    ), "🚨"),
    TriggerPreset("Chemical / Hazmat", listOf(
        "farligt gods", "kemikalieudslip", "giftig sky", "kemisk nødsituation"
    ), "⚠️"),
    TriggerPreset("Earthquake", listOf(
        "jordskælv", "jordskælvsvarsel", "seismisk aktivitet"
    ), "⚡"),
    TriggerPreset("Flood", listOf(
        "oversvømmelse", "stormflod", "oversvømmelsesvarsel", "skybrud"
    ), "💧"),
    TriggerPreset("General Emergency", listOf(
        "nødsituation", "varsling", "katastrofe", "evakuering", "beredskab"
    ), "🆘"),
    TriggerPreset("Hurricane", listOf(
        "orkan", "cyklon", "tropisk storm", "stormflo", "orkanvarsel"
    ), "🌀"),
    TriggerPreset("Nuclear / Radiological", listOf(
        "kerneenergi", "radioaktivt nedfald", "nuklear trussel", "kernekraftværk", "nuklear hændelse"
    ), "☢️"),
    TriggerPreset("Nuclear Missile Strike", listOf(
        "missil", "ballistisk missil", "missilalarm", "misselvarsel"
    ), "🚀"),
    TriggerPreset("Severe Thunderstorm", listOf(
        "uvejr varsel", "kraftigt tordenvejr", "hagl", "kraftig vind", "uvejr"
    ), "⛈️"),
    TriggerPreset("Tornado", listOf(
        "tornadovarsel", "tornado", "trombe", "vindvarsel"
    ), "🌪️"),
    TriggerPreset("Tsunami", listOf(
        "tsunami", "tsunamivarsel", "flodbølge", "kystalarM"
    ), "🌊"),
    TriggerPreset("Volcano", listOf(
        "vulkanudbrud", "vulkan", "lavastrøm", "vulkansk aske", "vulkanvarsel"
    ), "🌋"),
    TriggerPreset("Wildfire", listOf(
        "skovbrand", "brandvarsel", "evakuer brand", "markbrand"
    ), "🔥"),
    TriggerPreset("Winter Storm", listOf(
        "vinterstorm varsel", "snestorm", "blizzard", "isregn", "kraftigt snefald"
    ), "❄️")
)

// Finnish
private val PRESETS_FI = listOf(
    TriggerPreset("AMBER Alert", listOf(
        "amber alert", "kadonnut lapsi", "lapsikaappaus", "lapsi kadonnut"
    ), "🚨"),
    TriggerPreset("Chemical / Hazmat", listOf(
        "vaarallinen aine", "kemikaalivuoto", "myrkyllinen pilvi", "kemikaalionnettomuus"
    ), "⚠️"),
    TriggerPreset("Earthquake", listOf(
        "maanjäristys", "maanjäristysvaroitus", "seisminen aktiviteetti"
    ), "⚡"),
    TriggerPreset("Flood", listOf(
        "tulva", "tulvavaroitus", "rankkasade", "vedenpaisumus"
    ), "💧"),
    TriggerPreset("General Emergency", listOf(
        "hätätilanne", "varoitus", "katastrofi", "evakuointi", "väestöhälytys"
    ), "🆘"),
    TriggerPreset("Hurricane", listOf(
        "hurrikaani", "sykloni", "trooppinen myrsky", "myrsky"
    ), "🌀"),
    TriggerPreset("Nuclear / Radiological", listOf(
        "ydinvoima", "radioaktiivinen laskeuma", "ydinuhka", "ydinvoimala", "ydinvaaratilanne"
    ), "☢️"),
    TriggerPreset("Nuclear Missile Strike", listOf(
        "ohjus", "ballistinen ohjus", "ohjushälytys", "ohjusuhka"
    ), "🚀"),
    TriggerPreset("Severe Thunderstorm", listOf(
        "ukkosmyrsky", "voimakas ukkosmyrsky", "raekuuroja", "vahingollinen tuuli", "ukkosvaroitus"
    ), "⛈️"),
    TriggerPreset("Tornado", listOf(
        "tornadovaroitus", "tornado", "pyörremyrsky"
    ), "🌪️"),
    TriggerPreset("Tsunami", listOf(
        "tsunami", "tsunamivaroitus", "hyökyaalto", "rannikkovaroitus"
    ), "🌊"),
    TriggerPreset("Volcano", listOf(
        "tulivuorenpurkaus", "tulivuori", "laava", "vulkaaninen tuhka"
    ), "🌋"),
    TriggerPreset("Wildfire", listOf(
        "metsäpalo", "palovaroitus", "evakuoi palo", "maastopalo"
    ), "🔥"),
    TriggerPreset("Winter Storm", listOf(
        "talvimyrsky", "lumimyrsky", "blizzard", "jäätävä sade", "runsas lumisade"
    ), "❄️")
)

// Polish
private val PRESETS_PL = listOf(
    TriggerPreset("AMBER Alert", listOf(
        "amber alert", "zaginięcie dziecka", "porwanie dziecka", "dziecko zaginęło"
    ), "🚨"),
    TriggerPreset("Chemical / Hazmat", listOf(
        "materiały niebezpieczne", "wyciek chemiczny", "chmura toksyczna", "awaria chemiczna"
    ), "⚠️"),
    TriggerPreset("Earthquake", listOf(
        "trzęsienie ziemi", "ostrzeżenie sejsmiczne", "wstrząs", "aktywność sejsmiczna"
    ), "⚡"),
    TriggerPreset("Flood", listOf(
        "powódź", "ostrzeżenie powodziowe", "nagła powódź", "wezbranie"
    ), "💧"),
    TriggerPreset("General Emergency", listOf(
        "nagłe zagrożenie", "alert", "katastrofa", "ewakuacja", "alert RCB"
    ), "🆘"),
    TriggerPreset("Hurricane", listOf(
        "huragan", "cyklon", "burza tropikalna", "fala sztormowa", "ostrzeżenie burzowe"
    ), "🌀"),
    TriggerPreset("Nuclear / Radiological", listOf(
        "nuklearny", "zagrożenie nuklearne", "opad radioaktywny", "elektrownia jądrowa", "incydent jądrowy"
    ), "☢️"),
    TriggerPreset("Nuclear Missile Strike", listOf(
        "rakieta", "rakieta balistyczna", "alarm rakietowy", "zagrożenie rakietowe"
    ), "🚀"),
    TriggerPreset("Severe Thunderstorm", listOf(
        "ostrzeżenie przed burzą", "gwałtowna burza", "grad", "silny wiatr", "burza z piorunami"
    ), "⛈️"),
    TriggerPreset("Tornado", listOf(
        "ostrzeżenie przed tornadem", "tornado", "trąba powietrzna"
    ), "🌪️"),
    TriggerPreset("Tsunami", listOf(
        "tsunami", "ostrzeżenie przed tsunami", "fala przypływu", "ostrzeżenie brzegowe"
    ), "🌊"),
    TriggerPreset("Volcano", listOf(
        "erupcja wulkanu", "wulkan", "lawa", "popioł wulkaniczny", "ostrzeżenie wulkaniczne"
    ), "🌋"),
    TriggerPreset("Wildfire", listOf(
        "pożar lasu", "ostrzeżenie pożarowe", "pożar", "ewakuacja pożar"
    ), "🔥"),
    TriggerPreset("Winter Storm", listOf(
        "ostrzeżenie przed zimową burzą", "zamieć śnieżna", "blizzard", "gołoledź", "intensywne opady śniegu"
    ), "❄️")
)

// Ukrainian
private val PRESETS_UK = listOf(
    TriggerPreset("AMBER Alert", listOf(
        "amber alert", "зникнення дитини", "викрадення дитини", "дитина зникла"
    ), "🚨"),
    TriggerPreset("Chemical / Hazmat", listOf(
        "небезпечні речовини", "хімічний витік", "токсична хмара", "хімічна аварія"
    ), "⚠️"),
    TriggerPreset("Earthquake", listOf(
        "землетрус", "сейсмічна активність", "поштовх", "афтершок"
    ), "⚡"),
    TriggerPreset("Flood", listOf(
        "повінь", "попередження про повінь", "паводок", "підтоплення"
    ), "💧"),
    TriggerPreset("General Emergency", listOf(
        "надзвичайна ситуація", "тривога", "катастрофа", "евакуація"
    ), "🆘"),
    TriggerPreset("Hurricane", listOf(
        "ураган", "циклон", "тропічний шторм", "штормова хвиля"
    ), "🌀"),
    TriggerPreset("Nuclear / Radiological", listOf(
        "ядерний", "ядерна загроза", "радіаційна небезпека", "ядерна станція", "радіаційна аварія"
    ), "☢️"),
    TriggerPreset("Nuclear Missile Strike", listOf(
        "ракета", "балістична ракета", "повітряна тривога", "ракетна атака", "ракетна небезпека"
    ), "🚀"),
    TriggerPreset("Severe Thunderstorm", listOf(
        "штормове попередження", "сильна гроза", "великий град", "шквальний вітер"
    ), "⛈️"),
    TriggerPreset("Tornado", listOf(
        "торнадо", "попередження про торнадо", "смерч"
    ), "🌪️"),
    TriggerPreset("Tsunami", listOf(
        "цунамі", "попередження про цунамі", "хвиля цунамі"
    ), "🌊"),
    TriggerPreset("Volcano", listOf(
        "виверження вулкана", "вулкан", "лава", "вулканічний попіл"
    ), "🌋"),
    TriggerPreset("Wildfire", listOf(
        "лісова пожежа", "пожежна небезпека", "евакуація пожежа"
    ), "🔥"),
    TriggerPreset("Winter Storm", listOf(
        "попередження про зимову бурю", "завірюха", "хуртовина", "ожеледиця", "сильний снігопад"
    ), "❄️")
)

// Russian
private val PRESETS_RU = listOf(
    TriggerPreset("AMBER Alert", listOf(
        "amber alert", "похищение ребёнка", "розыск ребёнка", "ребёнок пропал"
    ), "🚨"),
    TriggerPreset("Chemical / Hazmat", listOf(
        "опасные вещества", "химический выброс", "токсичное облако", "химическая авария"
    ), "⚠️"),
    TriggerPreset("Earthquake", listOf(
        "землетрясение", "сейсмическая активность", "толчок", "афтершок"
    ), "⚡"),
    TriggerPreset("Flood", listOf(
        "наводнение", "паводок", "предупреждение о наводнении", "затопление"
    ), "💧"),
    TriggerPreset("General Emergency", listOf(
        "чрезвычайная ситуация", "тревога", "катастрофа", "эвакуация"
    ), "🆘"),
    TriggerPreset("Hurricane", listOf(
        "ураган", "циклон", "тропический шторм", "штормовая волна"
    ), "🌀"),
    TriggerPreset("Nuclear / Radiological", listOf(
        "ядерный", "ядерная угроза", "радиационная опасность", "атомная станция", "радиационная авария"
    ), "☢️"),
    TriggerPreset("Nuclear Missile Strike", listOf(
        "ракета", "баллистическая ракета", "ракетная атака", "ракетная тревога"
    ), "🚀"),
    TriggerPreset("Severe Thunderstorm", listOf(
        "штормовое предупреждение", "сильная гроза", "крупный град", "шквальный ветер"
    ), "⛈️"),
    TriggerPreset("Tornado", listOf(
        "смерч", "торнадо", "предупреждение о торнадо"
    ), "🌪️"),
    TriggerPreset("Tsunami", listOf(
        "цунами", "предупреждение о цунами", "волна цунами"
    ), "🌊"),
    TriggerPreset("Volcano", listOf(
        "извержение вулкана", "вулкан", "лава", "вулканический пепел"
    ), "🌋"),
    TriggerPreset("Wildfire", listOf(
        "лесной пожар", "пожарная опасность", "эвакуация пожар"
    ), "🔥"),
    TriggerPreset("Winter Storm", listOf(
        "метель", "буран", "ледяной дождь", "сильный снегопад", "штормовое предупреждение метель"
    ), "❄️")
)

// Japanese
private val PRESETS_JA = listOf(
    TriggerPreset("AMBER Alert", listOf(
        "アンバーアラート", "子供の誘拐", "行方不明の子供", "緊急子ども安全情報"
    ), "🚨"),
    TriggerPreset("Chemical / Hazmat", listOf(
        "危険物", "化学物質漏洩", "有毒ガス", "ハザードマップ"
    ), "⚠️"),
    TriggerPreset("Earthquake", listOf(
        "緊急地震速報", "大地震", "地震警報", "震度", "余震"
    ), "⚡"),
    TriggerPreset("Flood", listOf(
        "洪水警報", "土砂災害警戒情報", "記録的短時間大雨", "浸水害", "河川氾濫"
    ), "💧"),
    TriggerPreset("General Emergency", listOf(
        "緊急速報", "避難指示", "緊急安全確保", "警戒レベル", "避難勧告"
    ), "🆘"),
    TriggerPreset("Typhoon", listOf(
        "台風警報", "台風", "暴風警報", "高潮警報", "特別警報"
    ), "🌀"),
    TriggerPreset("Nuclear / Radiological", listOf(
        "核", "放射線", "原発事故", "放射性物質", "核の脅威"
    ), "☢️"),
    TriggerPreset("Nuclear Missile Strike", listOf(
        "弾道ミサイル", "ミサイル警報", "Jアラート", "ミサイル発射"
    ), "🚀"),
    TriggerPreset("Severe Thunderstorm", listOf(
        "暴風警報", "大雨警報", "雷注意報", "ひょう", "竜巻注意情報"
    ), "⛈️"),
    TriggerPreset("Tornado", listOf(
        "竜巻警報", "竜巻", "突風"
    ), "🌪️"),
    TriggerPreset("Tsunami", listOf(
        "大津波警報", "津波警報", "津波注意報", "津波"
    ), "🌊"),
    TriggerPreset("Volcano", listOf(
        "噴火警報", "火山噴火", "噴火", "火山灰", "火口周辺警報"
    ), "🌋"),
    TriggerPreset("Wildfire", listOf(
        "山林火災", "火災警報", "火事", "延焼"
    ), "🔥"),
    TriggerPreset("Winter Storm", listOf(
        "暴風雪警報", "大雪警報", "吹雪", "着氷性の雨"
    ), "❄️")
)

// Korean
private val PRESETS_KO = listOf(
    TriggerPreset("AMBER Alert", listOf(
        "앰버 경보", "아동 실종", "어린이 납치", "아동 납치"
    ), "🚨"),
    TriggerPreset("Chemical / Hazmat", listOf(
        "화학물질 유출", "유독 가스", "화학 사고", "위험물"
    ), "⚠️"),
    TriggerPreset("Earthquake", listOf(
        "지진 경보", "지진", "긴급재난문자", "여진", "진도"
    ), "⚡"),
    TriggerPreset("Flood", listOf(
        "홍수 경보", "호우 경보", "침수", "범람"
    ), "💧"),
    TriggerPreset("General Emergency", listOf(
        "재난문자", "대피 명령", "긴급 재난", "비상", "경보"
    ), "🆘"),
    TriggerPreset("Typhoon", listOf(
        "태풍 경보", "태풍", "폭풍 경보", "해일 경보", "특보"
    ), "🌀"),
    TriggerPreset("Nuclear / Radiological", listOf(
        "핵", "방사선 위험", "원자력 발전소", "방사성 물질", "핵 위협"
    ), "☢️"),
    TriggerPreset("Nuclear Missile Strike", listOf(
        "탄도미사일", "미사일 경보", "공습 경보", "미사일 발사"
    ), "🚀"),
    TriggerPreset("Severe Thunderstorm", listOf(
        "강풍 경보", "뇌우 경보", "우박", "호우 경보", "강한 바람"
    ), "⛈️"),
    TriggerPreset("Tornado", listOf(
        "토네이도 경보", "토네이도", "회오리바람", "돌풍 경보"
    ), "🌪️"),
    TriggerPreset("Tsunami", listOf(
        "쓰나미 경보", "해일 경보", "쓰나미", "해일"
    ), "🌊"),
    TriggerPreset("Volcano", listOf(
        "화산 폭발", "화산 경보", "화산", "용암", "화산재"
    ), "🌋"),
    TriggerPreset("Wildfire", listOf(
        "산불 경보", "산불", "화재 대피", "산불 위기"
    ), "🔥"),
    TriggerPreset("Winter Storm", listOf(
        "대설 경보", "폭설 경보", "눈보라", "결빙", "강설 경보"
    ), "❄️")
)

// Chinese Simplified
private val PRESETS_ZH_HANS = listOf(
    TriggerPreset("AMBER Alert", listOf(
        "安珀警报", "儿童失踪", "儿童绑架", "寻找失踪儿童"
    ), "🚨"),
    TriggerPreset("Chemical / Hazmat", listOf(
        "危险化学品", "化学泄漏", "有毒气体", "化学事故"
    ), "⚠️"),
    TriggerPreset("Earthquake", listOf(
        "地震预警", "大地震", "地震警报", "地震", "余震"
    ), "⚡"),
    TriggerPreset("Flood", listOf(
        "洪水警报", "暴雨警告", "洪水", "山洪", "积水"
    ), "💧"),
    TriggerPreset("General Emergency", listOf(
        "紧急警报", "紧急疏散", "应急广播", "撤离命令", "预警信号"
    ), "🆘"),
    TriggerPreset("Typhoon", listOf(
        "台风警报", "台风", "暴风警报", "风暴潮", "超强台风"
    ), "🌀"),
    TriggerPreset("Nuclear / Radiological", listOf(
        "核威胁", "核", "辐射危险", "核电站", "核泄漏"
    ), "☢️"),
    TriggerPreset("Nuclear Missile Strike", listOf(
        "导弹警报", "弹道导弹", "导弹袭击", "导弹发射"
    ), "🚀"),
    TriggerPreset("Severe Thunderstorm", listOf(
        "强雷暴警告", "雷暴", "冰雹", "大风预警", "强对流天气"
    ), "⛈️"),
    TriggerPreset("Tornado", listOf(
        "龙卷风警报", "龙卷风", "大风警报", "龙卷风预警"
    ), "🌪️"),
    TriggerPreset("Tsunami", listOf(
        "海啸警报", "海啸", "海浪预警", "沿海警告"
    ), "🌊"),
    TriggerPreset("Volcano", listOf(
        "火山爆发", "火山警报", "火山", "熔岩", "火山灰"
    ), "🌋"),
    TriggerPreset("Wildfire", listOf(
        "森林火灾", "火灾警报", "山火", "撤离火灾"
    ), "🔥"),
    TriggerPreset("Winter Storm", listOf(
        "暴风雪警告", "暴雪", "冬季风暴", "冻雨", "大雪"
    ), "❄️")
)

// Chinese Traditional
private val PRESETS_ZH_HANT = listOf(
    TriggerPreset("AMBER Alert", listOf(
        "安珀警報", "兒童失蹤", "兒童綁架", "尋找失蹤兒童"
    ), "🚨"),
    TriggerPreset("Chemical / Hazmat", listOf(
        "危險化學品", "化學洩漏", "有毒氣體", "化學事故"
    ), "⚠️"),
    TriggerPreset("Earthquake", listOf(
        "地震預警", "大地震", "地震警報", "地震", "餘震"
    ), "⚡"),
    TriggerPreset("Flood", listOf(
        "洪水警報", "豪雨警告", "洪水", "山洪", "淹水"
    ), "💧"),
    TriggerPreset("General Emergency", listOf(
        "緊急警報", "緊急疏散", "國家警報系統", "撤離命令", "防空警報"
    ), "🆘"),
    TriggerPreset("Typhoon", listOf(
        "颱風警報", "颱風", "暴風警報", "風暴潮", "海上颱風警報"
    ), "🌀"),
    TriggerPreset("Nuclear / Radiological", listOf(
        "核威脅", "核", "輻射危險", "核電廠", "核洩漏"
    ), "☢️"),
    TriggerPreset("Nuclear Missile Strike", listOf(
        "導彈警報", "彈道飛彈", "國家警報", "導彈發射"
    ), "🚀"),
    TriggerPreset("Severe Thunderstorm", listOf(
        "強雷暴警告", "雷暴", "冰雹", "強風預警", "強對流天氣"
    ), "⛈️"),
    TriggerPreset("Tornado", listOf(
        "龍卷風警報", "龍卷風", "大風警報", "龍卷風預警"
    ), "🌪️"),
    TriggerPreset("Tsunami", listOf(
        "海嘯警報", "海嘯", "海浪預警", "沿海警告"
    ), "🌊"),
    TriggerPreset("Volcano", listOf(
        "火山爆發", "火山警報", "火山", "熔岩", "火山灰"
    ), "🌋"),
    TriggerPreset("Wildfire", listOf(
        "森林火災", "火災警報", "山火", "撤離火災"
    ), "🔥"),
    TriggerPreset("Winter Storm", listOf(
        "暴風雪警告", "暴雪", "冬季風暴", "凍雨", "大雪"
    ), "❄️")
)

// Arabic
private val PRESETS_AR = listOf(
    TriggerPreset("AMBER Alert", listOf(
        "تنبيه أمبر", "طفل مفقود", "اختطاف طفل", "نداء البحث عن طفل"
    ), "🚨"),
    TriggerPreset("Chemical / Hazmat", listOf(
        "مواد خطرة", "تسرب كيميائي", "غاز سام", "طوارئ كيميائية"
    ), "⚠️"),
    TriggerPreset("Earthquake", listOf(
        "زلزال", "تحذير زلزالي", "نشاط زلزالي", "هزة أرضية"
    ), "⚡"),
    TriggerPreset("Flood", listOf(
        "فيضان", "تحذير فيضان", "فيضان مفاجئ", "ارتفاع منسوب المياه"
    ), "💧"),
    TriggerPreset("General Emergency", listOf(
        "طوارئ", "إنذار", "كارثة", "إخلاء", "الدفاع المدني"
    ), "🆘"),
    TriggerPreset("Hurricane", listOf(
        "إعصار مداري", "عاصفة مدارية", "موجة عاصفة", "تحذير من الإعصار"
    ), "🌀"),
    TriggerPreset("Nuclear / Radiological", listOf(
        "تهديد نووي", "إشعاع", "محطة نووية", "تسرب إشعاعي", "حادث نووي"
    ), "☢️"),
    TriggerPreset("Nuclear Missile Strike", listOf(
        "صاروخ", "صاروخ باليستي", "تحذير صاروخي", "إطلاق صاروخ"
    ), "🚀"),
    TriggerPreset("Severe Thunderstorm", listOf(
        "تحذير عاصفة رعدية شديدة", "عاصفة رعدية", "برد", "رياح مدمرة", "عواصف قوية"
    ), "⛈️"),
    TriggerPreset("Tornado", listOf(
        "تحذير إعصار", "إعصار", "عاصفة رعدية", "رياح عاتية"
    ), "🌪️"),
    TriggerPreset("Tsunami", listOf(
        "تسونامي", "تحذير تسونامي", "موجة مد", "تحذير ساحلي"
    ), "🌊"),
    TriggerPreset("Volcano", listOf(
        "ثوران بركاني", "بركان", "حمم بركانية", "رماد بركاني", "تحذير بركاني"
    ), "🌋"),
    TriggerPreset("Wildfire", listOf(
        "حريق غابات", "تحذير حريق", "إخلاء حريق", "حريق"
    ), "🔥"),
    TriggerPreset("Winter Storm", listOf(
        "تحذير عاصفة شتوية", "عاصفة ثلجية", "مطر جليدي", "ثلوج كثيفة", "موجة برد"
    ), "❄️")
)

// Turkish
private val PRESETS_TR = listOf(
    TriggerPreset("AMBER Alert", listOf(
        "amber alert", "kayıp çocuk", "çocuk kaçırma", "çocuk kayboldu"
    ), "🚨"),
    TriggerPreset("Chemical / Hazmat", listOf(
        "tehlikeli madde", "kimyasal sızıntı", "zehirli gaz", "kimyasal acil durum"
    ), "⚠️"),
    TriggerPreset("Earthquake", listOf(
        "deprem uyarısı", "deprem", "sismik aktivite", "artçı sarsıntı"
    ), "⚡"),
    TriggerPreset("Flood", listOf(
        "sel uyarısı", "ani sel", "taşkın", "su baskını"
    ), "💧"),
    TriggerPreset("General Emergency", listOf(
        "acil durum", "uyarı", "afet", "tahliye", "AFAD uyarısı"
    ), "🆘"),
    TriggerPreset("Hurricane", listOf(
        "kasırga", "siklon", "tropikal fırtına", "fırtına dalgası", "fırtına uyarısı"
    ), "🌀"),
    TriggerPreset("Nuclear / Radiological", listOf(
        "nükleer tehdit", "radyasyon tehlikesi", "nükleer santral", "radyasyon sızıntısı"
    ), "☢️"),
    TriggerPreset("Nuclear Missile Strike", listOf(
        "füze uyarısı", "balistik füze", "füze saldırısı", "füze tehlikesi"
    ), "🚀"),
    TriggerPreset("Severe Thunderstorm", listOf(
        "şiddetli fırtına uyarısı", "şiddetli gök gürültülü fırtına", "dolu", "güçlü rüzgar"
    ), "⛈️"),
    TriggerPreset("Tornado", listOf(
        "hortum uyarısı", "hortum", "tornado", "kasırga uyarısı"
    ), "🌪️"),
    TriggerPreset("Tsunami", listOf(
        "tsunami uyarısı", "tsunami", "gel-git dalgası", "kıyı uyarısı"
    ), "🌊"),
    TriggerPreset("Volcano", listOf(
        "volkanik patlama", "yanardağ", "lav", "volkanik kül", "volkan uyarısı"
    ), "🌋"),
    TriggerPreset("Wildfire", listOf(
        "orman yangını", "yangın uyarısı", "yangın tahliyesi", "yangın tehlikesi"
    ), "🔥"),
    TriggerPreset("Winter Storm", listOf(
        "kış fırtınası uyarısı", "kar fırtınası", "blizzard", "dondurucu yağmur", "yoğun kar"
    ), "❄️")
)

// Indonesian
private val PRESETS_ID = listOf(
    TriggerPreset("AMBER Alert", listOf(
        "amber alert", "anak hilang", "penculikan anak", "anak diculik"
    ), "🚨"),
    TriggerPreset("Chemical / Hazmat", listOf(
        "bahan berbahaya", "kebocoran kimia", "gas beracun", "darurat kimia"
    ), "⚠️"),
    TriggerPreset("Earthquake", listOf(
        "gempa bumi", "peringatan gempa", "aktivitas seismik", "gempa susulan"
    ), "⚡"),
    TriggerPreset("Flood", listOf(
        "banjir", "peringatan banjir", "banjir bandang", "air naik", "banjir rob"
    ), "💧"),
    TriggerPreset("General Emergency", listOf(
        "darurat", "peringatan", "bencana", "evakuasi", "BMKG"
    ), "🆘"),
    TriggerPreset("Typhoon", listOf(
        "badai tropis", "topan", "gelombang badai", "siklon", "angin kencang"
    ), "🌀"),
    TriggerPreset("Nuclear / Radiological", listOf(
        "ancaman nuklir", "bahaya radiasi", "pembangkit nuklir", "kebocoran radioaktif"
    ), "☢️"),
    TriggerPreset("Nuclear Missile Strike", listOf(
        "rudal", "rudal balistik", "peringatan rudal", "serangan rudal"
    ), "🚀"),
    TriggerPreset("Severe Thunderstorm", listOf(
        "peringatan badai petir", "badai petir", "hujan es", "angin kencang", "cuaca ekstrem"
    ), "⛈️"),
    TriggerPreset("Tornado", listOf(
        "peringatan tornado", "tornado", "angin puting beliung"
    ), "🌪️"),
    TriggerPreset("Tsunami", listOf(
        "peringatan tsunami", "tsunami", "gelombang pasang", "peringatan pantai"
    ), "🌊"),
    TriggerPreset("Volcano", listOf(
        "letusan gunung berapi", "gunung berapi", "lava", "abu vulkanik", "siaga gunung api"
    ), "🌋"),
    TriggerPreset("Wildfire", listOf(
        "kebakaran hutan", "peringatan kebakaran", "evakuasi kebakaran"
    ), "🔥"),
    TriggerPreset("Winter Storm", listOf(
        "cuaca ekstrem dingin", "angin dingin kencang", "cold wave warning", "gelombang dingin"
    ), "❄️")
)

// Hindi (India)
private val PRESETS_HI = listOf(
    TriggerPreset("AMBER Alert", listOf(
        "amber alert", "बच्चा गुम", "बाल अपहरण", "missing child"
    ), "🚨"),
    TriggerPreset("Chemical / Hazmat", listOf(
        "रासायनिक रिसाव", "जहरीली गैस", "chemical emergency", "hazmat"
    ), "⚠️"),
    TriggerPreset("Earthquake", listOf(
        "भूकंप चेतावनी", "भूकंप", "earthquake warning", "seismic alert"
    ), "⚡"),
    TriggerPreset("Flood", listOf(
        "बाढ़ चेतावनी", "बाढ़", "flood warning", "flash flood"
    ), "💧"),
    TriggerPreset("General Emergency", listOf(
        "आपातकाल", "आपातकालीन चेतावनी", "emergency", "evacuation", "निकासी"
    ), "🆘"),
    TriggerPreset("Cyclone", listOf(
        "चक्रवात चेतावनी", "चक्रवात", "cyclone warning", "cyclone alert", "तूफान"
    ), "🌀"),
    TriggerPreset("Nuclear / Radiological", listOf(
        "परमाणु खतरा", "nuclear", "विकिरण चेतावनी", "परमाणु ऊर्जा संयंत्र", "रेडियोधर्मी रिसाव"
    ), "☢️"),
    TriggerPreset("Nuclear Missile Strike", listOf(
        "मिसाइल चेतावनी", "missile warning", "बैलिस्टिक मिसाइल", "मिसाइल हमला"
    ), "🚀"),
    TriggerPreset("Severe Thunderstorm", listOf(
        "गंभीर तूफान चेतावनी", "आंधी तूफान", "ओलावृष्टि", "severe thunderstorm warning"
    ), "⛈️"),
    TriggerPreset("Tornado", listOf(
        "बवंडर चेतावनी", "बवंडर", "tornado warning"
    ), "🌪️"),
    TriggerPreset("Tsunami", listOf(
        "सुनामी चेतावनी", "सुनामी", "tsunami warning", "coastal alert"
    ), "🌊"),
    TriggerPreset("Volcano", listOf(
        "ज्वालामुखी विस्फोट", "ज्वालामुखी चेतावनी", "volcanic eruption"
    ), "🌋"),
    TriggerPreset("Wildfire", listOf(
        "जंगल की आग", "दावाग्नि चेतावनी", "wildfire warning"
    ), "🔥"),
    TriggerPreset("Winter Storm", listOf(
        "शीत लहर चेतावनी", "घना कोहरा", "cold wave warning", "बर्फबारी"
    ), "❄️")
)

// Lookup
/**
 * Returns the localized TriggerPreset list for the given ISO 3166-1 alpha-2 region code.
 * Unknown regions default to English presets.
 */
fun localizedTriggerPresets(regionCode: String): List<TriggerPreset> =
    when (regionCode.uppercase()) {
        "FR"                     -> PRESETS_FR
        "DE", "AT"               -> PRESETS_DE
        "IT"                     -> PRESETS_IT
        "ES", "MX", "AR", "CO",
        "CL", "PE", "VE", "EC"   -> PRESETS_ES
        "BR", "PT"               -> PRESETS_PT
        "NL", "BE"               -> PRESETS_NL
        "SE"                     -> PRESETS_SV
        "NO"                     -> PRESETS_NO
        "DK"                     -> PRESETS_DA
        "FI"                     -> PRESETS_FI
        "PL"                     -> PRESETS_PL
        "UA"                     -> PRESETS_UK
        "RU"                     -> PRESETS_RU
        "JP"                     -> PRESETS_JA
        "KR"                     -> PRESETS_KO
        "CN"                     -> PRESETS_ZH_HANS
        "TW"                     -> PRESETS_ZH_HANT
        "SA", "AE", "EG", "JO",
        "KW", "QA", "BH", "OM"  -> PRESETS_AR
        "TR"                     -> PRESETS_TR
        "ID", "PH"               -> PRESETS_ID
        "IN"                     -> PRESETS_HI
        else                     -> PRESETS_EN   // US, CA, GB, AU, NZ, IE, ZA, NG, …
    }

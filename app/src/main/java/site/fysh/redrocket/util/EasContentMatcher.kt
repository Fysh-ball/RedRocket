package site.fysh.redrocket.util

/**
 * Content-based EAS/WEA detection: catches emergency alerts from OEM packages
 * that are not in the static known-package list, by looking at what the
 * notification says rather than who posted it.
 *
 * Bilingual on purpose. Canada's public alerting is French and English by law,
 * so a Quebec handset renders the same cell broadcast entirely in French and an
 * English-only phrase list sees nothing at all. That is the worst failure this
 * app has: silent, and only on the alerts it exists to catch.
 *
 * The phrases are the alert names Android itself renders, taken from AOSP
 * CellBroadcastReceiver's own string resources (values, values-fr, values-fr-rCA)
 * rather than invented, plus the EAS event names that appear in the alert body.
 * Writing them in their natural form and normalising them here means the list
 * stays readable and cannot drift from the matcher's own comparison rules.
 *
 * Matching runs on [FalseAlarmDetector.normalize] output, which lowercases,
 * strips diacritics and reduces punctuation to spaces. That is what lets one
 * entry cover the accented and unaccented spellings, and lets "alerte d urgence"
 * cover "Alerte d'urgence : extrême" and "Alerte d'urgence : grave" alike.
 */
internal object EasContentMatcher {

    /**
     * Written as a human would write them. Normalised once in [PHRASES].
     *
     * Deliberately excluded: "En Alerte", Canada's system branding. It is two
     * ordinary French words and would match plain prose, and every alert it
     * would catch already says "Alerte d'urgence" in the AOSP-rendered title.
     * This gate is opt-in (Global Keyword Detection) but a match here can reach
     * a wildcard scenario, so a loose phrase can cost a real SMS.
     */
    private val PHRASE_SOURCE = listOf(
        // --- English: AOSP CellBroadcastReceiver values/strings.xml ---
        "wireless emergency alert",      // app_label "Wireless emergency alerts"
        "emergency alert",               // emergency_alert, and the "Emergency alert: Extreme"
                                         // and ": Severe" variants that prefix from it
        "presidential alert",            // cmas_presidential_level_alert
        "extreme alert",
        "severe alert",
        "amber alert",                   // also matches "Child abduction (Amber alert)"
        "child abduction",
        "public safety message",         // public_safety_message: an FCC WEA category the
                                         // original English list did not cover at all
        // --- English: EAS event names that appear in the body, not the title ---
        "civil emergency",
        "national emergency",

        // --- French: AOSP values-fr-rCA (Canada) and values-fr (France) ---
        "alerte d urgence",              // emergency_alert + the ": extreme" / ": critique"
                                         // / ": grave" variants
        "alertes d urgence",             // app_label "Alertes d'urgence sans fil", plural
        "alerte presidentielle",         // cmas_presidential_level_alert (fr-rCA)
        "alerte nationale",              // cmas_presidential_level_alert (fr)
        "alerte amber",                  // "Enlevement enfant (alerte Amber)" (fr-rCA)
        "alerte enlevement",             // cmas_amber_alert (fr)
        "enlevement enfant",             // cmas_amber_alert (fr-rCA)
        "message de securite publique",  // public_safety_message
        // --- French: EAS event names ---
        "urgence civile",
        "urgence nationale",
    )

    /**
     * Normalised through the same function the content goes through, so the two
     * sides of [contains] are always produced by identical rules.
     */
    private val PHRASES: List<String> by lazy {
        PHRASE_SOURCE.map { FalseAlarmDetector.normalize(it) }.filter { it.isNotEmpty() }
    }

    /** True if [content] reads like a WEA/EAS alert in English or French. */
    fun looksLikeEASContent(content: String): Boolean {
        if (content.isBlank()) return false
        val normalized = FalseAlarmDetector.normalize(content)
        return PHRASES.any { normalized.contains(it) }
    }

    /** Exposed for tests: the phrase list as actually compared. */
    internal fun normalizedPhrases(): List<String> = PHRASES
}

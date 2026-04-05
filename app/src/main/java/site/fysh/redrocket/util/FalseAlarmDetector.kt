package site.fysh.redrocket.util

import android.util.Log

/**
 * Detection sensitivity level.
 *
 * LOW    — life-threatening only: tornado warnings, destructive winds, missile, imminent threat.
 *          RED phrases always trigger. Non-RED score threshold = 9.
 *
 * MEDIUM — serious emergencies: RED phrases + strong action/danger combinations.
 *          Score threshold = 6 (default).
 *
 * HIGH   — all alerts: watches, advisories, potential threats.
 *          Score threshold = 4.
 *
 * RED phrases (Step 4) ALWAYS trigger regardless of sensitivity and source trust.
 * The only things that can prevent a RED phrase from triggering are the Amber Block
 * (child safety) and the Hard Block (explicit test phrase).
 */
enum class AlertSensitivity { HIGH, MEDIUM, LOW }

/**
 * Deterministic emergency detection system.
 *
 * Decision flow — no middle states:
 *   0. Amber Block    — child safety alert → LOGGING ONLY, DO NOT TRIGGER ever
 *   1. Hard Override  — trusted source + extreme action/danger  → TRIGGER instantly
 *   2. Override phrase ("this is not a test")                  → TRIGGER instantly
 *   3. Hard Block     — explicit test phrase, no override      → DO NOT TRIGGER instantly
 *   4. RED phrase     — life-threatening condition             → TRIGGER (bypasses sensitivity)
 *   5. Score          — accumulate points; threshold varies by sensitivity
 *   6. Score >= threshold                                      → TRIGGER
 *   7. Fail-safe      — trusted source + urgent structure
 *                       (ALL CAPS / heavy emphasis)            → TRIGGER
 *   8. Default                                                 → DO NOT TRIGGER
 *
 * Score breakdown:
 *   +5  trusted source (cell broadcast / system emergency alert)
 *   +4  strong action phrase ("evacuate immediately", "take shelter now", …)
 *   +2  moderate action phrase ("seek shelter", "take cover", …)   [exclusive with strong]
 *   +2  danger word ("emergency", "warning", "threat", "severe")
 *   +3  context boost — action + danger word both present
 *   +1  ALL CAPS structure (>70 % uppercase letters, ≥10 chars)
 *   +1  repetition structure (5+ char word appears ≥3 times)
 *   +2  user keyword match
 *   −2  soft test phrase ("exercise", "drill")
 *
 * All keyword lists are pre-normalized (lowercase, no accents / punctuation) so
 * they match the output of normalize() without an extra per-call normalize pass.
 */
object FalseAlarmDetector {

    private const val TAG = "FalseAlarmDetector"
    private const val TRIGGER_THRESHOLD = 6

    // ── Amber Block: child safety alerts — log only, NEVER trigger auto-messages ───────
    private val AMBER_BLOCK_PHRASES = listOf("amber alert", "child abduction")

    // ── Hard Override phrases: trusted source + ANY of these → instant TRIGGER ───────
    // Strong action — direct shelter/evacuation commands
    private val STRONG_ACTION_PHRASES = listOf(
        "evacuate immediately", "evacuate now",
        "take shelter now", "take shelter immediately",
        "take cover now", "take cover immediately", "take immediate cover",
        "seek shelter immediately", "shelter immediately",
        // French (pre-normalized — no accents)
        "evacuez immediatement", "mettez vous a l abri immediatement",
        "prenez abri immediatement"
    )

    // Extreme danger events — any of these from a trusted source = real emergency
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
        "raz de maree", "eruption volcanique", "inondation", "menace imminente"
    )

    // ── Override phrases: bypass Hard Block, add +5, always TRIGGER ─────────────────
    private val OVERRIDE_PHRASES = listOf(
        "this is not a test",
        "this is not a drill"
    )

    // ── Hard Block: explicit test phrases → instant DO NOT TRIGGER ───────────────────
    private val HARD_TEST_PHRASES = listOf(
        "this is a test",
        "this is only a test",
        "this is just a test",
        "this is a drill",
        "this is only a drill",
        "this is just a drill",
        "test of the alert system",
        "test of the emergency"
    )

    // ── RED trigger phrases: life-threatening conditions that ALWAYS trigger ─────────
    // These bypass sensitivity (threshold has no effect) but still respect:
    //   • Amber Block (step 0) — child safety alerts never trigger
    //   • Hard Block  (step 3) — explicit test phrase prevents triggering
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
        "menace imminente", "danger imminent"
    )

    // ── Strong action phrases: +4 (mutually exclusive with moderate) ─────────────────
    // (same list as STRONG_ACTION_PHRASES — reused for scoring)

    // ── Moderate action phrases: +2 ──────────────────────────────────────────────────
    private val MODERATE_ACTION_PHRASES = listOf(
        "seek shelter", "take shelter", "take cover",
        "evacuate", "move to higher ground", "go to basement",
        "remain indoors", "stay indoors", "shelter in place",
        "avoid the area", "stay away",
        "stay in your home", "stay in your house", "stay in your own home",
        "do not go outside", "do not leave your home", "remain inside",
        // French (pre-normalized)
        "abritez vous", "refugiez vous", "evacuez", "prenez abri",
        "restez a l interieur", "eloignez vous"
    )

    // ── Danger words: +2 ─────────────────────────────────────────────────────────────
    private val DANGER_WORDS = listOf(
        "emergency", "warning", "threat", "severe", "destructive winds",
        // French (pre-normalized)
        "urgence", "avertissement", "menace", "grave", "vents destructeurs"
    )

    // ── Soft test phrases: −2 ────────────────────────────────────────────────────────
    private val SOFT_TEST_PHRASES = listOf("exercise", "drill")

    // ── Generic words excluded from word-level keyword matching ──────────────────────
    // These appear in nearly every EAS message and carry no scenario-specific meaning.
    private val GENERIC_WORDS = setOf(
        "warning", "alert", "emergency", "attack", "threat",
        "severe", "watch", "issue", "notice", "danger", "urgent"
    )

    /**
     * Returns true if [rawContent] should be suppressed even when a user keyword matched.
     *
     * Only two blocks are applied — everything else is intentionally bypassed because
     * the user's keyword is authoritative:
     *  • Amber Block        — child safety alerts never auto-trigger regardless of keywords.
     *  • Hard Block         — built-in test phrases ("this is a test") block triggering,
     *                         unless an Override phrase ("this is not a drill") is also present.
     *  • User Block Phrases — user-defined phrases (any language) checked the same way as
     *                         the built-in Hard Block. Override phrases still cancel them.
     */
    fun isBlockedDespiteKeywordMatch(
        rawContent: String,
        userBlockPhrases: List<String> = emptyList()
    ): Boolean {
        val content = normalize(rawContent)
        if (AMBER_BLOCK_PHRASES.any { content.contains(it) }) {
            Log.i(TAG, "KEYWORD MATCH SUPPRESSED: Amber Block (child safety alert)")
            return true
        }
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
     * Returns true if [keyword] matches [contentLower] (already lowercased).
     *
     * Two-tier logic:
     *  1. Exact phrase — "volcano eruption" is a substring of content.
     *  2. Significant-word — any word from the keyword that is ≥4 chars and not a
     *     generic EAS term ("warning", "alert", "emergency", …) appears in content.
     *     Handles the common case where EAS says "Volcano emergency alert" but the
     *     user's keyword is "Volcano Eruption" — "volcano" still fires the match.
     */
    fun keywordMatchesContent(keyword: String, contentLower: String): Boolean {
        val kwLower = keyword.lowercase()
        if (contentLower.contains(kwLower)) return true
        return kwLower.split("\\s+".toRegex())
            .filter { it.length >= 4 && it !in GENERIC_WORDS }
            .any { word -> contentLower.contains(word) }
    }

    /**
     * Determines whether an alert should fire.
     *
     * @param message          Full text of the incoming notification/broadcast.
     * @param triggerWords     User-defined keywords for this scenario (empty list = no boost).
     * @param isTrustedSource  True for cell broadcasts and system emergency alert packages.
     *                         Enables Hard Override and structural fail-safe.
     * @param sensitivity      Detection sensitivity — adjusts score threshold for non-RED messages.
     *                         HIGH = 4 (watches/advisories), MEDIUM = 6 (default), LOW = 9 (major threats only).
     *                         RED phrases (Step 4) ALWAYS bypass sensitivity — they trigger at any level.
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

        // Normalize once — all subsequent checks operate on `content`
        val content = normalize(message)
        Log.d(TAG, "EVALUATING [trusted=$isTrustedSource sensitivity=$sensitivity threshold=$threshold]: \"${content.take(140)}\"")

        // ── Step 0: Amber Block — child safety alerts must never trigger auto-messages ─
        if (AMBER_BLOCK_PHRASES.any { content.contains(it) }) {
            Log.i(TAG, "AMBER BLOCK: child safety alert — LOGGING ONLY, DO NOT TRIGGER")
            return false
        }

        // ── Step 1: Hard Override — trusted source + extreme action or danger ─────────
        if (isTrustedSource) {
            val hasStrongAction = STRONG_ACTION_PHRASES.any { content.contains(it) }
            val hasExtremeDanger = EXTREME_DANGER_PHRASES.any { content.contains(it) }
            if (hasStrongAction || hasExtremeDanger) {
                Log.i(TAG, "HARD OVERRIDE: trusted + ${if (hasStrongAction) "strong action" else "extreme danger"} — TRIGGERING")
                return true
            }
        }

        // ── Step 2: Override phrase — bypasses Hard Block, always triggers ────────────
        val hasOverride = OVERRIDE_PHRASES.any { content.contains(it) }
        if (hasOverride) {
            Log.i(TAG, "OVERRIDE PHRASE — TRIGGERING (bypasses all blocks)")
            return true
        }

        // ── Step 3: Hard Block — explicit test phrase ─────────────────────────────────
        val hardTestHit = HARD_TEST_PHRASES.firstOrNull { content.contains(it) }
        if (hardTestHit != null) {
            Log.i(TAG, "HARD BLOCK: '$hardTestHit' — DO NOT TRIGGER")
            return false
        }

        // ── Step 4: RED phrase — life-threatening condition bypasses sensitivity ───────
        val redHit = RED_TRIGGER_PHRASES.firstOrNull { content.contains(it) }
        if (redHit != null) {
            Log.i(TAG, "RED PHRASE: '$redHit' — life-threatening — TRIGGERING (bypasses sensitivity)")
            return true
        }

        // ── Step 5: Score accumulation ────────────────────────────────────────────────
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

        // ── Step 6: Score threshold ───────────────────────────────────────────────────
        if (score >= threshold) {
            Log.i(TAG, "SCORE >= $threshold — TRIGGERING")
            return true
        }

        // ── Step 7: Structural fail-safe — trusted source + urgent structure ──────────
        // Handles unknown-language alerts: ALL CAPS or heavy punctuation emphasis.
        if (isTrustedSource && isUrgentStructure(message)) {
            Log.i(TAG, "FAIL-SAFE: trusted source + urgent structure — TRIGGERING")
            return true
        }

        Log.i(TAG, "DO NOT TRIGGER: score=$score")
        return false
    }

    /**
     * Lowercase, strip punctuation (including accented chars → space), collapse spaces.
     * All keyword lists must be stored pre-normalized to match this output without
     * a per-call normalize() call on each phrase.
     */
    private fun normalize(text: String): String =
        text.lowercase()
            .replace(Regex("[^a-z0-9\\s]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

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
     * Detects urgency through message structure alone — for unknown languages.
     * Checks ALL CAPS and heavy exclamation/repetition emphasis.
     */
    private fun isUrgentStructure(message: String): Boolean {
        if (isAllCaps(message)) return true
        if (message.count { it == '!' } >= 2) return true
        return false
    }
}

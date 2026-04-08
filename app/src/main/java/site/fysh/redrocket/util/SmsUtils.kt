package site.fysh.redrocket.util

import kotlin.math.ceil

/**
 * Calculates SMS encoding and part count for a given message.
 *
 * GSM-7 mode  : 160 chars per single SMS, 153 per part in multi-part messages.
 *               Extended chars (| ^ € { } [ ] ~ \) each consume 2 units.
 * Unicode mode: 70 chars per single SMS, 67 per part in multi-part messages.
 *               Triggered by any character outside the GSM-7 alphabet.
 *
 * This is the unit count that matters for per-SMS billing on prepaid/credit plans.
 */
data class SmsStats(
    /** Number of SMS parts this message will be split into. */
    val parts: Int,
    /** GSM units consumed (extended chars count as 2) or character count in Unicode mode. */
    val units: Int,
    /** Total unit capacity for this part count (e.g. 2 parts × 153 = 306). */
    val capacity: Int,
    /** True if any character forced UCS-2 Unicode encoding. */
    val isUnicode: Boolean
)

fun smsStats(message: String): SmsStats {
    if (message.isEmpty()) return SmsStats(parts = 1, units = 0, capacity = 160, isUnicode = false)

    var units = 0
    for (char in message) {
        when {
            char in GSM7_EXTENDED -> units += 2
            char in GSM7_BASIC    -> units += 1
            else -> {
                // Character outside GSM-7 — entire message switches to Unicode (UCS-2)
                // `units` represents UTF-16 code units, not user-perceived grapheme clusters.
                // Surrogate pairs (basic emoji) already count as 2 in String.length, which is
                // correct for SMS billing. ZWJ sequences (e.g. family emoji 👨‍👩‍👧 = 8 code units
                // displayed as one glyph) inflate units beyond the visible character count;
                // this matches what the carrier bills for, but may surprise users reading the
                // UI counter.
                val len = message.length
                val parts = if (len <= 70) 1 else ceil(len / 67.0).toInt()
                val capacity = if (parts == 1) 70 else parts * 67
                return SmsStats(parts = parts, units = len, capacity = capacity, isUnicode = true)
            }
        }
    }

    val parts = if (units <= 160) 1 else ceil(units / 153.0).toInt()
    val capacity = if (parts == 1) 160 else parts * 153
    return SmsStats(parts = parts, units = units, capacity = capacity, isUnicode = false)
}

// GSM-7 basic character set — each character consumes 1 unit
private val GSM7_BASIC = setOf(
    '@', '£', '$', '¥', 'è', 'é', 'ù', 'ì', 'ò', 'Ç', '\n', 'Ø', 'ø', '\r', 'Å', 'å',
    'Δ', '_', 'Φ', 'Γ', 'Λ', 'Ω', 'Π', 'Ψ', 'Σ', 'Θ', 'Ξ', '\u001B', 'Æ', 'æ', 'ß', 'É',
    ' ', '!', '"', '#', '¤', '%', '&', '\'', '(', ')', '*', '+', ',', '-', '.', '/',
    '0', '1', '2', '3', '4', '5', '6', '7', '8', '9',
    ':', ';', '<', '=', '>', '?',
    '¡', 'A', 'B', 'C', 'D', 'E', 'F', 'G', 'H', 'I', 'J', 'K', 'L', 'M',
    'N', 'O', 'P', 'Q', 'R', 'S', 'T', 'U', 'V', 'W', 'X', 'Y', 'Z',
    'Ä', 'Ö', 'Ñ', 'Ü', '§', '¿',
    'a', 'b', 'c', 'd', 'e', 'f', 'g', 'h', 'i', 'j', 'k', 'l', 'm',
    'n', 'o', 'p', 'q', 'r', 's', 't', 'u', 'v', 'w', 'x', 'y', 'z',
    'ä', 'ö', 'ñ', 'ü', 'à'
)

// GSM-7 extended character set — each character consumes 2 units (escape + char)
private val GSM7_EXTENDED = setOf('|', '^', '€', '{', '}', '[', ']', '~', '\\')

package site.fysh.redrocket.util

/**
 * Shared phone number normalization — single source of truth.
 * Strips non-digits and takes the last 10 digits to handle country codes.
 * All phone matching in the app must use this function to prevent divergence.
 */
fun normalizePhone(number: String): String {
    val digits = number.filter { it.isDigit() }
    return if (digits.length > 10) digits.takeLast(10) else digits
}

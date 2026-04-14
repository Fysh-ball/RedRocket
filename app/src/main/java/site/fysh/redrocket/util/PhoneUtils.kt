package site.fysh.redrocket.util

/**
 * Shared phone number normalization - single source of truth.
 * All phone matching in the app must use this function to prevent divergence.
 *
 * Two overloads:
 *
 *   normalizePhone(number) - region-unaware (legacy behavior, takeLast(10))
 *   normalizePhone(number, regionCode) - region-aware; handles countries with local trunk prefixes
 *
 * The region-aware overload is preferred. Pass RegionSettings.effectiveRegion as the regionCode.
 * Both stored contacts and incoming SMS numbers must be normalized with the same regionCode
 * for matching to succeed - since normalization is applied at comparison time (not storage
 * time), changing the region code automatically fixes existing contacts.
 *
 * Region-specific behavior:
 *   AU (+61), NZ (+64): local numbers use a "0" trunk prefix ("0412345678", 10 digits).
 *     International strips it ("+61412345678", 11 digits). takeLast(10) gives "1412345678"
 *     for the international form - a mismatch. Both are normalized to 9-digit subscriber form.
 *   IN (+91): "+919876543210" (12 digits) → "9876543210" (10 digits). takeLast(10) works
 *     already but the explicit rule is cleaner.
 *   All other regions: standard takeLast(10) behavior.
 *
 * A complete solution for all edge cases requires libphonenumber.
 */
fun normalizePhone(number: String): String {
    val digits = number.filter { it.isDigit() }
    val hasPlus = number.trimStart().startsWith("+")
    // Don't strip international numbers
    if (hasPlus) return digits
    return if (digits.length == 11 && digits.startsWith("1")) digits.drop(1)
    else if (digits.length > 10) digits.takeLast(10)
    else digits
}

/**
 * Region-aware overload. See normalizePhone(String) for full documentation.
 * regionCode is ISO 3166-1 alpha-2 (e.g. "US", "AU", "GB") from RegionSettings.effectiveRegion.
 *
 * Known limitation: AU/NZ subscriber length is hardcoded to 9, which is correct for
 * mobiles (the primary target for emergency SMS) but is wrong for AU landlines whose
 * subscriber portion is 8 digits (e.g. "02 XXXX XXXX"). A proper fix requires libphonenumber.
 * Landlines are out of scope for the SMS-based emergency flow.
 */
fun normalizePhone(number: String, regionCode: String): String {
    val digits = number.filter { it.isDigit() }
    val hasPlus = number.trimStart().startsWith("+")
    return when (regionCode.uppercase()) {
        "AU" -> normalizeWithTrunkPrefix(digits, countryPrefix = "61", subscriberLength = 9)
        "NZ" -> normalizeWithTrunkPrefix(digits, countryPrefix = "64", subscriberLength = 9)
        "IN" -> {
            if (digits.length == 12 && digits.startsWith("91")) digits.drop(2)
            else if (digits.length > 10) digits.takeLast(10)
            else digits
        }
        "US", "CA" -> {
            // NANP: strip leading 1 from 11-digit numbers
            if (digits.length == 11 && digits.startsWith("1")) digits.drop(1)
            else if (digits.length > 10 && !hasPlus) digits.takeLast(10)
            else digits
        }
        "GB" -> normalizeWithTrunkPrefix(digits, countryPrefix = "44", subscriberLength = 10)
        else -> {
            // For unknown regions with international prefix, keep full digits
            if (hasPlus || digits.length <= 10) digits
            else if (digits.length == 11 && digits.startsWith("1")) digits.drop(1) // likely NANP
            else digits  // keep full international number
        }
    }
}

/** Masks a phone number for safe logging: "+1555****67" */
fun maskPhone(phone: String): String {
    if (phone.length < 4) return "***"
    return phone.take(2) + "*".repeat((phone.length - 4).coerceAtLeast(1)) + phone.takeLast(2)
}

/**
 * Normalizes numbers for countries whose local format includes a trunk prefix "0".
 * Collapses both local ("0XXXXXXXXX") and international ("+CCxxxxxxxxx") to the raw
 * subscriber number so they match each other.
 */
private fun normalizeWithTrunkPrefix(
    digits: String,
    countryPrefix: String,
    subscriberLength: Int
): String = when {
    // International: "61412345678" (countryPrefix + subscriberLength digits) → strip prefix
    digits.startsWith(countryPrefix) && digits.length == countryPrefix.length + subscriberLength ->
        digits.drop(countryPrefix.length)
    // Local with trunk "0": "0412345678" (1 + subscriberLength) → strip "0"
    digits.startsWith("0") && digits.length == subscriberLength + 1 ->
        digits.drop(1)
    // Already subscriber-length
    digits.length == subscriberLength -> digits
    // Fallback
    digits.length > 10 -> digits.takeLast(10)
    else -> digits
}

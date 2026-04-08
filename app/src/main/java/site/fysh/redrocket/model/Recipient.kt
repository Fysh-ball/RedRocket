package site.fysh.redrocket.model

/**
 * Represents a message recipient.
 */
data class Recipient(
    val name: String,
    val phoneNumber: String,
    val contactId: String? = null
) {
    /**
     * Basic validation for phone numbers.
     */
    fun isValid(): Boolean {
        val cleanNumber = phoneNumber.replace(Regex("[^0-9+]"), "")
        // Count digits only — the '+' prefix must not contribute to the minimum digit threshold
        val digitCount = cleanNumber.count { it.isDigit() }
        if (digitCount < 7) return false
        val plusCount = cleanNumber.count { it == '+' }
        // + is only valid as an international prefix (at position 0), never mid-number
        return plusCount == 0 || (plusCount == 1 && cleanNumber.startsWith("+"))
    }

    /**
     * Formats number for display or normalization.
     */
    fun getNormalizedNumber(): String {
        return phoneNumber.replace(Regex("[^0-9+]"), "")
    }
}

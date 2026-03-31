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
        return cleanNumber.length >= 7 && cleanNumber.all { it.isDigit() || it == '+' }
    }

    /**
     * Formats number for display or normalization.
     */
    fun getNormalizedNumber(): String {
        return phoneNumber.replace(Regex("[^0-9+]"), "")
    }
}

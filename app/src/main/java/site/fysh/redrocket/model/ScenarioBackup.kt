package site.fysh.redrocket.model

/**
 * Root object for scenario backup files (JSON export/import).
 * Scenarios are serialized directly (nested Groups and Recipients inline).
 * Block phrases are stored as plain strings.
 * Settings captures all user-configured app preferences for a full device clone.
 */
data class ScenarioBackup(
    val version: Int = 2,
    val exportedAt: Long = System.currentTimeMillis(),
    val scenarios: List<Scenario> = emptyList(),
    val blockPhrases: List<String> = emptyList(),
    val settings: AppSettingsBackup? = null
)

/**
 * User-configurable app preferences included in the backup.
 * All fields are nullable so missing fields in older backups default gracefully.
 */
data class AppSettingsBackup(
    val theme: String? = null,
    val alertSensitivity: String? = null,
    val replyListenHours: Int? = null,
    val forceSequential: Boolean? = null,
    val wideSpreadEnabled: Boolean? = null,
    val userRegion: String? = null
)

package site.fysh.redrocket.model

/**
 * Root object for scenario backup files (JSON export/import).
 * Scenarios are serialized directly (nested Groups and Recipients inline).
 * Block phrases are stored as plain strings.
 */
data class ScenarioBackup(
    val version: Int = 1,
    val exportedAt: Long = System.currentTimeMillis(),
    val scenarios: List<Scenario> = emptyList(),
    val blockPhrases: List<String> = emptyList()
)

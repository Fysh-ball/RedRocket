package site.fysh.redrocket.utils

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/** DataStore-backed persistence for user preferences. */
class AppSettings(private val context: Context) {

    companion object {
        val IS_FIRST_LAUNCH     = booleanPreferencesKey("is_first_launch")
        val DEBUG_ENABLED       = booleanPreferencesKey("debug_enabled")
        val FAILURE_RATE        = doublePreferencesKey("failure_rate")
        val FORCE_SEQUENTIAL    = booleanPreferencesKey("force_sequential")
        val WIDE_SPREAD_ENABLED = booleanPreferencesKey("wide_spread_enabled")
        val LAST_SCENARIO_ID    = stringPreferencesKey("last_scenario_id")
        val THEME               = stringPreferencesKey("theme")
        val REPLY_LISTEN_HOURS  = intPreferencesKey("reply_listen_hours")
        val PRESETS_OFFERED     = booleanPreferencesKey("presets_offered")
        val ALERT_SENSITIVITY   = stringPreferencesKey("alert_sensitivity")
        val FORCE_SEND_USED       = booleanPreferencesKey("force_send_used")
        val TUTORIAL_SHOWN        = booleanPreferencesKey("tutorial_shown")
        val AUTO_BACKUP_URI       = stringPreferencesKey("auto_backup_uri")
        val LAST_SEEN_VERSION     = stringPreferencesKey("last_seen_version")
    }

    val isFirstLaunch: Flow<Boolean>   = context.dataStore.data.map { it[IS_FIRST_LAUNCH] ?: true }
    val debugEnabled: Flow<Boolean>    = context.dataStore.data.map { it[DEBUG_ENABLED] ?: false }
    val failureRate: Flow<Double>      = context.dataStore.data.map { it[FAILURE_RATE] ?: 0.0 }
    val forceSequential: Flow<Boolean> = context.dataStore.data.map { it[FORCE_SEQUENTIAL] ?: false }
    val wideSpreadEnabled: Flow<Boolean> = context.dataStore.data.map { it[WIDE_SPREAD_ENABLED] ?: false }
    val lastScenarioId: Flow<String?>  = context.dataStore.data.map { it[LAST_SCENARIO_ID] }
    val theme: Flow<String>            = context.dataStore.data.map { it[THEME] ?: "SYSTEM" }
    val replyListenHours: Flow<Int>    = context.dataStore.data.map { it[REPLY_LISTEN_HOURS] ?: 1 }
    val presetsOffered: Flow<Boolean>  = context.dataStore.data.map { it[PRESETS_OFFERED] ?: false }
    val alertSensitivity: Flow<String> = context.dataStore.data.map { it[ALERT_SENSITIVITY] ?: "MEDIUM" }
    val forceSendUsed: Flow<Boolean>   = context.dataStore.data.map { it[FORCE_SEND_USED] ?: false }
    val tutorialShown: Flow<Boolean>   = context.dataStore.data.map { it[TUTORIAL_SHOWN] ?: false }
    val autoBackupUri: Flow<String>      = context.dataStore.data.map { it[AUTO_BACKUP_URI] ?: "" }
    val lastSeenVersion: Flow<String>    = context.dataStore.data.map { it[LAST_SEEN_VERSION] ?: "" }

    suspend fun setFirstLaunch(isFirst: Boolean) {
        context.dataStore.edit { it[IS_FIRST_LAUNCH] = isFirst }
    }

    suspend fun setDebugEnabled(enabled: Boolean) {
        context.dataStore.edit { it[DEBUG_ENABLED] = enabled }
    }

    suspend fun setFailureRate(rate: Double) {
        context.dataStore.edit { it[FAILURE_RATE] = rate }
    }

    suspend fun setForceSequential(enabled: Boolean) {
        context.dataStore.edit { it[FORCE_SEQUENTIAL] = enabled }
    }

    suspend fun setWideSpreadEnabled(enabled: Boolean) {
        context.dataStore.edit { it[WIDE_SPREAD_ENABLED] = enabled }
    }

    suspend fun setLastScenarioId(id: String) {
        context.dataStore.edit { it[LAST_SCENARIO_ID] = id }
    }

    suspend fun setTheme(theme: String) {
        context.dataStore.edit { it[THEME] = theme }
    }

    suspend fun setReplyListenHours(hours: Int) {
        context.dataStore.edit { it[REPLY_LISTEN_HOURS] = hours.coerceIn(1, 24) }
    }

    suspend fun setPresetsOffered(offered: Boolean) {
        context.dataStore.edit { it[PRESETS_OFFERED] = offered }
    }

    suspend fun setAlertSensitivity(sensitivity: String) {
        context.dataStore.edit { it[ALERT_SENSITIVITY] = sensitivity }
    }

    suspend fun setForceSendUsed(used: Boolean) {
        context.dataStore.edit { it[FORCE_SEND_USED] = used }
    }

    suspend fun setTutorialShown(shown: Boolean) {
        context.dataStore.edit { it[TUTORIAL_SHOWN] = shown }
    }

    suspend fun setAutoBackupUri(uri: String) {
        context.dataStore.edit { it[AUTO_BACKUP_URI] = uri }
    }

    suspend fun setLastSeenVersion(version: String) {
        context.dataStore.edit { it[LAST_SEEN_VERSION] = version }
    }

}

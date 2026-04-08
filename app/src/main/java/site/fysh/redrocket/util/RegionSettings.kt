package site.fysh.redrocket.util

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Global region state for the app. Initialized once at app start from SharedPreferences.
 *
 * Used by:
 *   - Block phrase preset picker (selects the right language's phrases)
 *   - normalizePhone(number, regionCode) (country-aware phone normalization)
 *   - SmsResponseReceiver (phone matching against scenario recipients)
 *
 * effectiveRegion = userRegion if set by user, otherwise auto-detected from SIM/network/locale.
 * BroadcastReceivers can read effectiveRegion synchronously without coroutines.
 */
object RegionSettings {

    private const val PREFS_NAME = "region_settings"
    private const val KEY_USER_REGION = "user_region"

    /** Auto-detected region from SIM / network / locale. Never empty after init(). */
    @Volatile var detectedRegion: String = "US"
        private set

    /**
     * User-chosen override (ISO 3166-1 alpha-2, e.g. "AU", "GB").
     * Empty string means "use detectedRegion".
     * Persisted to SharedPreferences.
     */
    @Volatile var userRegion: String = ""
        private set

    /** The region actually in use. Read this everywhere instead of detectedRegion/userRegion. */
    val effectiveRegion: String get() = userRegion.ifEmpty { detectedRegion }

    private val _userRegionFlow = MutableStateFlow("")
    val userRegionFlow: StateFlow<String> = _userRegionFlow.asStateFlow()

    private val _detectedRegionFlow = MutableStateFlow("US")
    val detectedRegionFlow: StateFlow<String> = _detectedRegionFlow.asStateFlow()

    @Volatile private var prefs: android.content.SharedPreferences? = null

    /**
     * Must be called once from EmergencyApp.onCreate() to restore persisted state.
     * Safe to call multiple times - subsequent calls are no-ops.
     */
    fun init(context: Context) {
        if (prefs != null) return  // fast path — no lock needed for initial null check
        synchronized(RegionSettings::class.java) {
            if (prefs != null) return  // re-check under lock
            val p = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            detectedRegion = RegionDetector.detect(context)
            _detectedRegionFlow.value = detectedRegion
            val saved = p.getString(KEY_USER_REGION, "") ?: ""
            userRegion = saved
            _userRegionFlow.value = saved
            prefs = p  // assign last so fast-path check is safe
        }
    }

    /**
     * Persists the user's chosen region and updates the live StateFlow.
     * Pass an empty string to revert to auto-detection.
     */
    fun setUserRegion(context: Context, code: String) {
        val normalized = code.uppercase().trim()
        userRegion = normalized
        _userRegionFlow.value = normalized
        val p = prefs ?: context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .also { prefs = it }
        p.edit().putString(KEY_USER_REGION, normalized).apply()
    }
}

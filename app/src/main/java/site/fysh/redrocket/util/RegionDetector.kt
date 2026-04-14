package site.fysh.redrocket.util

import android.content.Context
import android.telephony.TelephonyManager
import java.util.Locale

/**
 * Detects the user's region (ISO 3166-1 alpha-2 country code) without location permission.
 *
 * Priority order:
 *   1. SIM card country ISO - no permission required; most reliable, persists offline
 *   2. Network/carrier ISO - available when SIM absent or roaming
 *   3. System locale country - set by user in Android Settings
 *   4. Fallback: "US"
 */
object RegionDetector {

    fun detect(context: Context): String {
        val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager

        // 1. SIM card country - survives airplane mode, persists when no service
        val simCountry = tm?.simCountryIso?.uppercase()?.takeIf { it.length == 2 }
        if (!simCountry.isNullOrEmpty()) return simCountry

        // 2. Network country - available when connected to carrier but no SIM installed
        val networkCountry = tm?.networkCountryIso?.uppercase()?.takeIf { it.length == 2 }
        if (!networkCountry.isNullOrEmpty()) return networkCountry

        // 3. System locale country - user's chosen region in Android Settings
        val localeCountry = Locale.getDefault().country.uppercase().takeIf { it.length == 2 }
        if (!localeCountry.isNullOrEmpty()) return localeCountry

        return "US"
    }
}

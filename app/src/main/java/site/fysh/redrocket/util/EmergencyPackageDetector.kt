package site.fysh.redrocket.util

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log

/**
 * Detects and tracks which emergency alert / cell broadcast packages are present on the device.
 *
 * Android OEMs ship different cell broadcast receiver packages. This detector identifies
 * which variants are installed so the notification listener can accurately classify
 * incoming notifications as emergency alerts.
 *
 * Detection strategy (layered, most → least specific):
 *   1. Query installed packages by name (including disabled/hidden components)
 *   2. Query broadcast receivers registered for CB intent actions
 *   3. If both find nothing → fall back to trusting ALL known variants
 *
 * This ensures we never miss a real alert, even on unusual ROMs or carrier builds.
 */
object EmergencyPackageDetector {

    private const val TAG = "EmergencyPkgDetector"

    // ALL known cell broadcast / emergency alert package names
    // Organized by OEM / source for maintainability.

    /** AOSP / Pixel / stock Android */
    private val AOSP_PACKAGES = listOf(
        "com.android.cellbroadcastreceiver",
        "com.android.cellbroadcastreceiver.module",
        "com.android.cellbroadcastservice",
        "com.google.android.cellbroadcastreceiver",
        "com.google.android.cellbroadcastservice",
        "com.google.android.cellbroadcastservice.module",
        "com.android.emergency"
    )

    /** Samsung (One UI / TouchWiz / legacy) */
    private val SAMSUNG_PACKAGES = listOf(
        "com.samsung.android.cellbroadcastreceiver",
        "com.sec.android.app.cellbroadcastreceiver",
        "com.samsung.android.emergencylauncher",
        "com.samsung.android.app.emergencyalert",
        "com.sec.android.emergencylauncher",
        "com.samsung.android.app.telephony.cbs",
        "com.sec.android.app.safetyassurance",
        "com.samsung.android.emerge",
        "com.sec.android.cmas"
    )

    /** Xiaomi / MIUI / HyperOS */
    private val XIAOMI_PACKAGES = listOf(
        "com.xiaomi.cellbroadcastreceiver",
        "com.miui.cellbroadcastreceiver",
        "com.miui.emergencyassistant",
        "com.xiaomi.simactivate.service"
        // com.miui.misound removed - audio routing handler, sends non-emergency notifications
    )

    /** Huawei / EMUI / HarmonyOS */
    private val HUAWEI_PACKAGES = listOf(
        "com.huawei.cellbroadcastreceiver",
        "com.huawei.android.cellbroadcastreceiver",
        "com.huawei.emergency",
        "com.huawei.android.emergencyassistant"
    )

    /** Honor (post-split from Huawei, MagicOS) */
    private val HONOR_PACKAGES = listOf(
        "com.hihonor.cellbroadcastreceiver",
        "com.hihonor.android.cellbroadcastreceiver"
    )

    /** OnePlus / OxygenOS / ColorOS — also covers Oppo/Realme on newer ColorOS builds */
    private val ONEPLUS_PACKAGES = listOf(
        "com.oneplus.cellbroadcastreceiver",
        "com.oplus.cellbroadcastreceiver",     // shared with Oppo/Realme on newer builds
        "com.oplus.emergency"
    )

    /** Oppo / ColorOS */
    private val OPPO_PACKAGES = listOf(
        "com.coloros.cellbroadcastreceiver",
        "com.oppo.cellbroadcastreceiver",
        "com.coloros.emergency"
    )

    /** Realme (ColorOS-derived) */
    private val REALME_PACKAGES = listOf(
        "com.realme.cellbroadcastreceiver"
    )

    /** Vivo / OriginOS / FuntouchOS */
    private val VIVO_PACKAGES = listOf(
        "com.vivo.cellbroadcastreceiver",
        "com.vivo.emergency"
    )

    /** Motorola / Lenovo */
    private val MOTOROLA_PACKAGES = listOf(
        "com.motorola.cellbroadcastreceiver",
        "com.motorola.cmas",
        "com.motorola.android.cellbroadcastreceiver"
    )

    /** LG */
    private val LG_PACKAGES = listOf(
        "com.lge.cellbroadcastreceiver",
        "com.lge.cmas",
        "com.lge.android.cellbroadcastreceiver"
    )

    /** Sony (Xperia) */
    private val SONY_PACKAGES = listOf(
        "com.sonyericsson.cellbroadcastreceiver",
        "com.sony.cellbroadcastreceiver",
        "com.sonymobile.cellbroadcastreceiver"
    )

    /** ASUS (ZenFone / ROG) */
    private val ASUS_PACKAGES = listOf(
        "com.asus.cellbroadcastreceiver",
        "com.asus.cmas"
    )

    /** Nokia / HMD Global */
    private val NOKIA_PACKAGES = listOf(
        "com.nokia.cellbroadcastreceiver",
        "com.hmdglobal.cellbroadcastreceiver"
    )

    /** ZTE */
    private val ZTE_PACKAGES = listOf(
        "com.zte.cellbroadcastreceiver",
        "com.zte.cmas"
    )

    /** TCL / Alcatel */
    private val TCL_PACKAGES = listOf(
        "com.tcl.cellbroadcastreceiver",
        "com.alcatel.cellbroadcastreceiver"
    )

    /** Nothing (NothingOS - based on AOSP, may use stock package) */
    private val NOTHING_PACKAGES = listOf(
        "com.nothing.cellbroadcastreceiver"
    )

    /** Tecno / Infinix / Itel (Transsion group, HiOS / XOS) */
    private val TRANSSION_PACKAGES = listOf(
        "com.tecno.cellbroadcastreceiver",
        "com.transsion.cellbroadcastreceiver",
        "com.infinix.cellbroadcastreceiver"
    )

    /** Google-specific safety/emergency apps */
    private val GOOGLE_SAFETY_PACKAGES = listOf(
        "com.google.android.apps.safetyhub",        // Personal Safety app (Pixel)
        "com.google.android.apps.emergencyinfo"
        // com.google.android.gms removed - Google Play Services sends constant non-emergency
        // notifications (updates, sync, etc.) that would flood Alert History as false alerts
    )

    /** US carrier-specific CMAS/WEA and chipset-level apps */
    private val CARRIER_PACKAGES = listOf(
        "com.qualcomm.atfwd",
        "com.android.cmas",
        "com.qualcomm.cmas",
        "com.mediatek.cellbroadcastreceiver",        // MediaTek chipset CB handler
        "com.mediatek.cbr",
        "com.spreadtrum.cellbroadcastreceiver"       // Spreadtrum/Unisoc chipset
        // com.android.stk removed - SIM Toolkit handles many non-emergency carrier messages
        // (USSD, carrier menus, balance alerts) that would appear as false EAS alerts
    )

    /** Complete list of ALL known emergency alert packages across all OEMs */
    val ALL_KNOWN_PACKAGES: Set<String> = (
        AOSP_PACKAGES +
        SAMSUNG_PACKAGES +
        XIAOMI_PACKAGES +
        HUAWEI_PACKAGES +
        HONOR_PACKAGES +
        ONEPLUS_PACKAGES +
        OPPO_PACKAGES +
        REALME_PACKAGES +
        VIVO_PACKAGES +
        MOTOROLA_PACKAGES +
        LG_PACKAGES +
        SONY_PACKAGES +
        ASUS_PACKAGES +
        NOKIA_PACKAGES +
        ZTE_PACKAGES +
        TCL_PACKAGES +
        NOTHING_PACKAGES +
        TRANSSION_PACKAGES +
        GOOGLE_SAFETY_PACKAGES +
        CARRIER_PACKAGES
    ).toSet()

    // Broadcast actions used for secondary detection
    // If a package registers a receiver for any of these, it's an emergency alert app.

    private val CB_BROADCAST_ACTIONS = listOf(
        "android.provider.Telephony.SMS_EMERGENCY_CB_RECEIVED",
        "android.provider.Telephony.SMS_CB_RECEIVED",
        "android.provider.Telephony.CMAS_RECEIVED",
        "android.provider.Telephony.ETWS_RECEIVED",
        "com.android.internal.telephony.gsm.CB_AREA_INFO_RECEIVED",
        "android.cellbroadcast.action.SMS_CB_RECEIVED"
    )

    // Runtime state
    /** Packages actually detected on this device. Empty = detection hasn't run yet. */
    @Volatile
    private var detectedPackages: Set<String> = emptySet()

    /** True if detection found at least one known package. */
    @Volatile
    private var detectionSucceeded = false

    /** The OEM of this device (lowercase), cached once. */
    private val deviceManufacturer: String by lazy {
        Build.MANUFACTURER.lowercase()
    }

    /**
     * Scans the device's installed packages to find which emergency alert apps are present.
     * Call this once during [Application.onCreate].
     *
     * Uses two detection layers:
     *   1. Direct package lookup (including disabled components)
     *   2. Broadcast receiver query for known CB actions (catches unlisted/OEM-hidden packages)
     */
    fun detect(context: Context) {
        val pm = context.packageManager
        val found = mutableSetOf<String>()

        // Layer 1: Direct package lookup
        // Use MATCH_DISABLED_COMPONENTS to catch packages that OEMs disable by default
        // but re-enable when a cell broadcast arrives.
        val flags = PackageManager.MATCH_DISABLED_COMPONENTS

        for (pkg in ALL_KNOWN_PACKAGES) {
            try {
                pm.getPackageInfo(pkg, flags)
                found.add(pkg)
                Log.i(TAG, "FOUND emergency package (direct): $pkg")
            } catch (_: PackageManager.NameNotFoundException) {
                // Not installed - expected for most entries
            } catch (e: Exception) {
                Log.w(TAG, "Error checking package $pkg", e)
            }
        }

        // Layer 2: Broadcast receiver query
        // Some OEMs register cell broadcast receivers under non-standard package names.
        // Query the system for any package that handles CB intents. The manifest
        // <queries> block declares visibility for these actions on Android 11+.
        for (action in CB_BROADCAST_ACTIONS) {
            try {
                val intent = Intent(action)
                // Use flags=0: <queries> already grants visibility on API 30+, and
                // MATCH_ALL has been observed to throw SecurityException on hardened ROMs.
                val receivers = pm.queryBroadcastReceivers(intent, 0)
                for (info in receivers) {
                    val pkg = info.activityInfo?.packageName ?: continue
                    if (pkg !in found && pkg != context.packageName) {
                        found.add(pkg)
                        Log.i(TAG, "FOUND emergency package (broadcast query, action=$action): $pkg")
                    }
                }
            } catch (e: SecurityException) {
                // Some hardened ROMs throw on broadcast queries. Log distinctly so it's diagnosable.
                Log.w(TAG, "SecurityException querying broadcast receivers for action: $action - ROM restriction", e)
            } catch (e: Exception) {
                Log.w(TAG, "Error querying broadcast receivers for action: $action", e)
            }
        }

        detectedPackages = found
        detectionSucceeded = found.isNotEmpty()

        Log.i(TAG, "Device manufacturer: $deviceManufacturer")
        if (detectionSucceeded) {
            Log.i(TAG, "Detection complete: ${found.size} emergency package(s) found: $found")
        } else {
            Log.w(TAG, "Detection complete: NO known emergency packages found. " +
                "Will treat ALL known variants as potential emergency sources.")
        }
    }

    /**
     * Returns true if [packageName] is a known emergency alert source.
     *
     * The static [ALL_KNOWN_PACKAGES] list is always checked first. Runtime detection
     * adds any OEM/carrier packages discovered via broadcast-receiver queries that are
     * not already in the static list. The two sets are never mutually exclusive:
     * partial runtime detection (e.g. finding only one of several installed packages)
     * must never cause a package that IS in the static list to be rejected.
     */
    fun isEmergencyAlertPackage(packageName: String): Boolean {
        return packageName in ALL_KNOWN_PACKAGES || packageName in detectedPackages
    }

    /**
     * Returns the set of packages detected on this device, or ALL known packages if
     * auto-detection failed.
     */
    fun getActivePackages(): Set<String> {
        return if (detectionSucceeded) detectedPackages else ALL_KNOWN_PACKAGES
    }

    /** Re-runs detection. Useful if the user installs/updates emergency apps at runtime. */
    fun reDetect(context: Context) {
        Log.i(TAG, "Re-running emergency package detection...")
        detect(context)
    }

    /** For debug/settings UI: whether auto-detection found packages. */
    fun didDetectionSucceed(): Boolean = detectionSucceeded

    /** For debug/settings UI: the raw detected set. */
    fun getDetectedPackages(): Set<String> = detectedPackages

    /** For debug/settings UI: device manufacturer string. */
    fun getManufacturerName(): String = deviceManufacturer
}

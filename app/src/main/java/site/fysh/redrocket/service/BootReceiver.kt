package site.fysh.redrocket.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * Restores Red Rocket's runtime state after a device reboot.
 *
 * EmergencyBroadcastReceiver and EmergencyNotificationListener are manifest-registered
 * and resume automatically when their respective system events arrive. This receiver's
 * job is to start the app process early so EmergencyApp.onCreate() runs (if it has not
 * already), which:
 *   - Restores SmsResponseReceiver's active listen window from SharedPreferences
 *   - Reinitializes ForceSendAbuseTracker state
 *   - Detects installed emergency alert packages
 *
 * Note: EmergencyApp.onCreate() runs only if the process was not already alive when
 * the receiver fires. On a soft reboot or process resurrection where the process
 * survived, onCreate() is not called again — but in that case, state is already loaded.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED &&
            action != "com.htc.intent.action.QUICKBOOT_POWERON" &&
            action != "com.huawei.intent.action.QUICKBOOT_POWERON") return
        Log.i("BootReceiver", "Device booted ($action) - Red Rocket initialized and ready.")
    }
}

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
 * job is to start the app process early so EmergencyApp.onCreate() runs, which:
 *   - Restores SmsResponseReceiver's active listen window from SharedPreferences
 *   - Reinitializes ForceSendAbuseTracker state
 *   - Detects installed emergency alert packages
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_BOOT_COMPLETED) return
        Log.i("BootReceiver", "Device booted - Red Rocket initialized and ready.")
        // EmergencyApp.onCreate() is called automatically when this receiver fires,
        // restoring all persistent state. No further action needed here.
    }
}

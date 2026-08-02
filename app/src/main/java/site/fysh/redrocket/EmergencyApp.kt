package site.fysh.redrocket

import android.app.Application
import android.telephony.SmsManager
import android.util.Log
import site.fysh.redrocket.BuildConfig
import site.fysh.redrocket.model.AppDatabase
import site.fysh.redrocket.queue.AdaptiveSendController
import site.fysh.redrocket.queue.MessageQueueManager
import site.fysh.redrocket.service.LazarusRetrySystem
import site.fysh.redrocket.service.ManualSendGuard
import site.fysh.redrocket.service.SmsProvider
import site.fysh.redrocket.service.SmsSender
import site.fysh.redrocket.service.SmsResponseReceiver
import site.fysh.redrocket.utils.AppSettings
import site.fysh.redrocket.utils.DebugSimulator
import site.fysh.redrocket.utils.ForceSendAbuseTracker
import site.fysh.redrocket.utils.MockSmsSender
import site.fysh.redrocket.service.EmergencyWidget
import site.fysh.redrocket.util.EmergencyPackageDetector
import site.fysh.redrocket.util.RegionSettings
import site.fysh.redrocket.utils.NotificationHelper
import site.fysh.redrocket.utils.RateLimiter
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Main Application class for initializing core messaging components.
 */
class EmergencyApp : Application() {

    companion object {
        /**
         * Alert-log retention for rows that never fired a send. Age-out, not
         * count-out: emergency review happens on human timescales, so "did my
         * phone see that alert last month" has to be answerable.
         */
        private const val PAST_ALERT_RETENTION_MS = 90L * 24 * 60 * 60 * 1000

        /**
         * Untriggered rows always kept regardless of age, so a quiet phone never
         * empties its own Alert History. Rows that fired a send are never pruned
         * at all and are not counted against this floor.
         */
        private const val PAST_ALERT_KEEP_NEWEST_UNTRIGGERED = 200
    }

    lateinit var queueManager: MessageQueueManager
    lateinit var adaptiveController: AdaptiveSendController
    lateinit var rateLimiter: RateLimiter
    lateinit var smsSender: SmsSender
    lateinit var manualGuard: ManualSendGuard
    lateinit var lazarusSystem: LazarusRetrySystem
    lateinit var mockSender: MockSmsSender
    lateinit var debugSimulator: DebugSimulator
    lateinit var database: AppDatabase
    lateinit var settings: AppSettings
    lateinit var abuseTracker: ForceSendAbuseTracker

    @Volatile private var isDebugModeEnabled = false
    val appScope = CoroutineScope(
        SupervisorJob() + Dispatchers.Default +
        CoroutineExceptionHandler { _, t -> Log.e("EmergencyApp", "Unhandled exception in appScope", t) }
    )

    override fun onCreate() {
        super.onCreate()
        
        database = AppDatabase.getDatabase(this)
        settings = AppSettings(this)
        abuseTracker = ForceSendAbuseTracker(this)
        SmsResponseReceiver.init(this)
        RegionSettings.init(this)
        
        queueManager = MessageQueueManager(database.pendingMessageDao())
        adaptiveController = AdaptiveSendController()
        rateLimiter = RateLimiter()
        
        smsSender = SmsSender(this, { getSmsManager() }, rateLimiter, adaptiveController, queueManager)
        mockSender = MockSmsSender(rateLimiter, adaptiveController, queueManager)
        
        manualGuard = ManualSendGuard(queueManager, database.scenarioDao())
        
        // Lazarus system needs to use whatever provider is currently active
        lazarusSystem = LazarusRetrySystem(queueManager, adaptiveController) { getActiveSmsProvider() }

        debugSimulator = DebugSimulator(this, queueManager, mockSender)
        
        // Collect debug mode asynchronously - defaults to false (safe: real SMS mode)
        // until the preference is loaded. This avoids blocking the main thread.
        appScope.launch(Dispatchers.Main) {
            settings.debugEnabled.collect { enabled ->
                isDebugModeEnabled = enabled
                Log.d("EmergencyApp", "Debug Mode changed: $enabled")
            }
        }

        // Bound the alert log once per app start, off the main thread. Not on the
        // insert path: prune urgency is low, and a DB scan while an alert is being
        // processed is exactly the wrong time to spend I/O. Fails soft per rule 2 -
        // a failed prune must never take down startup.
        appScope.launch(Dispatchers.IO) {
            try {
                val cutoff = System.currentTimeMillis() - PAST_ALERT_RETENTION_MS
                val removed = database.pastAlertDao()
                    .pruneUntriggered(cutoff, PAST_ALERT_KEEP_NEWEST_UNTRIGGERED)
                if (removed > 0) Log.i("EmergencyApp", "Pruned $removed untriggered alert log row(s)")
            } catch (e: Exception) {
                Log.e("EmergencyApp", "PastAlert prune failed - log left unbounded this run", e)
            }
        }

        // Detect which emergency alert packages are installed on this device
        EmergencyPackageDetector.detect(this)

        NotificationHelper.ensureDebugChannel(this)

        // Push initial widget status
        EmergencyWidget.pushUpdate(this)

        Log.i("EmergencyApp", "Core messaging components initialized.")
    }

    /**
     * Obtains a fresh SmsManager instance on each call. This avoids caching a stale
     * reference at startup and handles dual-SIM devices where the default subscription
     * may change between sends.
     */
    fun getSmsManager(): SmsManager? {
        return try {
            getSystemService(SmsManager::class.java)
                ?: @Suppress("DEPRECATION") SmsManager.getDefault()
        } catch (e: Exception) {
            Log.e("EmergencyApp", "Failed to get SmsManager", e)
            try {
                @Suppress("DEPRECATION") SmsManager.getDefault()
            } catch (e2: Exception) {
                Log.e("EmergencyApp", "SmsManager.getDefault() also failed", e2)
                null
            }
        }
    }

    /**
     * Returns the active SMS provider based on current settings.
     * In production builds always returns the real sender regardless of debug flag.
     */
    fun getActiveSmsProvider(): SmsProvider {
        // Production builds always use the real sender regardless of the debug toggle.
        // This prevents mock sends in a production APK if debug mode was left on.
        return if (!BuildConfig.IS_PRODUCTION && isDebugModeEnabled) mockSender else smsSender
    }
}

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
        
        queueManager = MessageQueueManager()
        adaptiveController = AdaptiveSendController()
        rateLimiter = RateLimiter()
        
        // Robust SmsManager retrieval
        val smsManager = try {
            getSystemService(SmsManager::class.java) ?: @Suppress("DEPRECATION") SmsManager.getDefault()
        } catch (e: Exception) {
            Log.e("EmergencyApp", "Failed to get SmsManager via getSystemService, using getDefault()", e)
            @Suppress("DEPRECATION") SmsManager.getDefault()
        }
        
        smsSender = SmsSender(this, smsManager, rateLimiter, adaptiveController, queueManager)
        mockSender = MockSmsSender(rateLimiter, adaptiveController, queueManager)
        
        manualGuard = ManualSendGuard(queueManager)
        
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

        // Detect which emergency alert packages are installed on this device
        EmergencyPackageDetector.detect(this)

        NotificationHelper.ensureDebugChannel(this)

        Log.i("EmergencyApp", "Core messaging components initialized.")
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

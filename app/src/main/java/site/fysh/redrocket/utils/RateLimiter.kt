package site.fysh.redrocket.utils

import android.os.SystemClock
import android.util.Log
import kotlinx.coroutines.delay
import java.util.concurrent.atomic.AtomicLong

/**
 * Ensures SMS sending complies with carrier limits.
 * Uses AtomicLong + CAS to prevent concurrent coroutines from both passing the rate check.
 */
class RateLimiter {
    private val TAG = "RateLimiter"
    private val lastSendTimestamp = AtomicLong(0L)
    private val MIN_SAFETY_DELAY_MS = 150L

    suspend fun waitForNextSlot(adaptiveDelayMs: Long) {
        val requiredDelay = maxOf(MIN_SAFETY_DELAY_MS, adaptiveDelayMs)
        while (true) {
            val currentTime = SystemClock.elapsedRealtime()
            val last = lastSendTimestamp.get()
            val timeSinceLastSend = currentTime - last
            if (timeSinceLastSend >= requiredDelay) {
                // CAS: only one coroutine wins the slot — others loop and re-check
                if (lastSendTimestamp.compareAndSet(last, currentTime)) break
            } else {
                val sleepTime = requiredDelay - timeSinceLastSend
                Log.v(TAG, "Rate limiting: Sleeping for ${sleepTime}ms")
                delay(sleepTime)
            }
        }
    }

    fun reset() {
        lastSendTimestamp.set(0L)
    }
}

package site.fysh.redrocket.utils

import android.content.Context
import android.util.Log
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min

/**
 * Tracks force-send usage and applies a hidden point system to detect abuse.
 *
 * Point accrual:
 *   - First force send: +1 point
 *   - Each subsequent send: up to +25 points on an exponential curve based on
 *     time since last force send (20 seconds ago = +25 max, ~5 minutes ago = +1 min)
 *
 * Passive decay:
 *   - Normal: -1 point every 30 seconds
 *   - At 75+ points: -1 point every 60 seconds
 *   - At 3rd+ lockout: -1 point every 30 minutes (until points reach 0)
 *
 * Lockout tiers (by points):
 *   0:     No captcha / no timer
 *   1-24:  Captcha only
 *   25-50: Captcha + timer + "This app is for emergency automated messages"
 *   75-99: Captcha + timer + "This is not for group messaging, please use another messaging app"
 *   100+:  1-hour lockout: "Too many forced messages sent"
 *
 * Lockout escalation on repeated 100-point lockouts:
 *   1st: lock 1 hour, set to 50 after
 *   2nd: lock 3 hours, set to 75 after
 *   3rd+: lock 24 hours, decay slows to 1/30min; lifts only when points reach 0
 *
 * State (points, lockoutEndTime, lockoutCount, overrideUsed, isInExtendedSlowDecay,
 * lastForceSendTime) is persisted to SharedPreferences so lockouts survive process death.
 */
class ForceSendAbuseTracker(context: Context) {

    companion object {
        private const val TAG = "AbuseTracker"
        private const val MAX_POINTS = 150
        private const val LOCKOUT_THRESHOLD = 100
        private const val PREFS_NAME = "abuse_tracker_state"
        private const val KEY_POINTS = "points"
        private const val KEY_LOCKOUT_END = "lockout_end_time"
        private const val KEY_LOCKOUT_COUNT = "lockout_count"
        private const val KEY_OVERRIDE_USED = "override_used"
        private const val KEY_EXTENDED_SLOW_DECAY = "extended_slow_decay"
        private const val KEY_LAST_FORCE_SEND = "last_force_send_time"
        private const val KEY_LAST_DECAY = "last_decay_time"
    }

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private var points: Int = prefs.getInt(KEY_POINTS, 0)
    private var lastForceSendTime: Long = prefs.getLong(KEY_LAST_FORCE_SEND, 0L)
    private var lockoutEndTime: Long = prefs.getLong(KEY_LOCKOUT_END, 0L)
    private var lockoutCount: Int = prefs.getInt(KEY_LOCKOUT_COUNT, 0)
    private var overrideUsed: Boolean = prefs.getBoolean(KEY_OVERRIDE_USED, false)
    private var isInExtendedSlowDecay: Boolean = prefs.getBoolean(KEY_EXTENDED_SLOW_DECAY, false)

    /** Last time the decay timer ran — persisted so decay is accurate after process death. */
    private var lastDecayTime: Long = prefs.getLong(KEY_LAST_DECAY, System.currentTimeMillis())

    // ── Public API ─────────────────────────────────────────────────────────

    /**
     * Call this when the user successfully passes captcha / confirms a force send.
     * Returns the resulting lockout tier.
     */
    fun recordForceSend(): AbuseLevel {
        applyDecay()

        val now = System.currentTimeMillis()
        val pointsToAdd = if (lastForceSendTime == 0L) {
            1  // First ever force send
        } else {
            computePoints(now - lastForceSendTime)
        }
        lastForceSendTime = now
        points = min(MAX_POINTS, points + pointsToAdd)
        Log.d(TAG, "Force send recorded. +$pointsToAdd pts. Total: $points")

        // Check if we hit the 100-point lockout
        if (points >= LOCKOUT_THRESHOLD && !isLocked()) {
            triggerLockout()
        }

        persistState()
        return currentAbuseLevel()
    }

    /** Returns current abuse level without modifying state. */
    fun currentAbuseLevel(): AbuseLevel {
        applyDecay()
        return when {
            isLocked() -> AbuseLevel.HARD_LOCKOUT
            points >= 75 -> AbuseLevel.HIGH_WARNING
            points >= 25 -> AbuseLevel.MEDIUM_WARNING
            points >= 1  -> AbuseLevel.CAPTCHA_ONLY
            else         -> AbuseLevel.NONE
        }
    }

    /** Returns true if a timed lockout is currently active. */
    fun isLocked(): Boolean = System.currentTimeMillis() < lockoutEndTime

    /** Remaining lockout seconds, or 0 if not locked. */
    fun lockoutSecondsRemaining(): Long =
        max(0L, (lockoutEndTime - System.currentTimeMillis()) / 1000L)

    /** Current raw point value. */
    fun pointsValue(): Int {
        applyDecay()
        return points
    }

    /** Number of times the hard lockout has been triggered. */
    fun lockoutCount(): Int = lockoutCount

    /**
     * Activates the one-time override during a 100-point lockout.
     * Resets the lockout timer. Returns false if the override has already been used
     * (it won't be available again until points reach 0).
     */
    fun useOverride(): Boolean {
        if (overrideUsed) return false
        if (!isLocked()) return false
        overrideUsed = true
        lockoutEndTime = 0L  // Reset lockout timer
        persistState()
        Log.i(TAG, "Override activated — lockout cleared.")
        return true
    }

    /** True if the one-time override button should be shown during the current lockout. */
    fun overrideAvailable(): Boolean = isLocked() && !overrideUsed && lockoutCount <= 1

    // ── Private helpers ───────────────────────────────────────────────────

    /**
     * Applies passive point decay since the last time this was called.
     */
    private fun applyDecay() {
        val now = System.currentTimeMillis()
        val elapsedMs = now - lastDecayTime
        if (elapsedMs < 1000) return  // Don't apply sub-second decay

        val decayIntervalMs: Long = when {
            isInExtendedSlowDecay -> 30 * 60 * 1000L  // 30 minutes per point
            points >= 75          -> 60 * 1000L         // 60 seconds per point
            else                  -> 30 * 1000L         // 30 seconds per point
        }

        val pointsToRemove = (elapsedMs / decayIntervalMs).toInt()
        if (pointsToRemove > 0) {
            points = max(0, points - pointsToRemove)
            Log.v(TAG, "Decay: -$pointsToRemove pts. Remaining: $points")

            // If extended slow decay and points reach 0, lift the status
            if (isInExtendedSlowDecay && points == 0) {
                isInExtendedSlowDecay = false
                overrideUsed = false  // Reset override availability
                Log.i(TAG, "Extended slow decay complete — normal status restored.")
            }
            persistState()
        }
        lastDecayTime = now
    }

    /**
     * Computes points to add based on time since last force send.
     * - 20 seconds (20,000 ms) ago = 25 points (max)
     * - ~5 minutes (300,000 ms) ago = 1 point (min)
     * Uses exponential decay curve between those bounds.
     */
    private fun computePoints(timeSinceLastMs: Long): Int {
        val minMs = 20_000.0    // 20 seconds → 25 points
        val maxMs = 300_000.0   // 5 minutes  → 1 point
        val maxPts = 25.0
        val minPts = 1.0

        return when {
            timeSinceLastMs <= minMs -> maxPts.toInt()
            timeSinceLastMs >= maxMs -> minPts.toInt()
            else -> {
                // Exponential interpolation: pts = maxPts * e^(-k * t)
                // where k = -ln(minPts/maxPts) / (maxMs - minMs)
                val k = -ln(minPts / maxPts) / (maxMs - minMs)
                val pts = maxPts * exp(-k * (timeSinceLastMs - minMs))
                pts.toInt().coerceIn(minPts.toInt(), maxPts.toInt())
            }
        }
    }

    private fun triggerLockout() {
        lockoutCount++
        overrideUsed = false  // Reset override for new lockout

        val lockDurationMs: Long
        val pointsAfter: Int

        when (lockoutCount) {
            1 -> {
                lockDurationMs = 60 * 60 * 1000L  // 1 hour
                pointsAfter = 50
            }
            2 -> {
                lockDurationMs = 3 * 60 * 60 * 1000L  // 3 hours
                pointsAfter = 75
            }
            else -> {
                lockDurationMs = 24 * 60 * 60 * 1000L  // 24 hours
                pointsAfter = points  // keep current points, slow decay takes over
                isInExtendedSlowDecay = true
            }
        }

        lockoutEndTime = System.currentTimeMillis() + lockDurationMs
        Log.w(TAG, "Lockout #$lockoutCount triggered! Lock duration: ${lockDurationMs / 3600000}h. Points after: $pointsAfter")

        if (!isInExtendedSlowDecay) {
            points = pointsAfter  // will be reduced further by decay during lockout
        }
    }

    /** Persists all mutable state to SharedPreferences. */
    private fun persistState() {
        prefs.edit()
            .putInt(KEY_POINTS, points)
            .putLong(KEY_LOCKOUT_END, lockoutEndTime)
            .putInt(KEY_LOCKOUT_COUNT, lockoutCount)
            .putBoolean(KEY_OVERRIDE_USED, overrideUsed)
            .putBoolean(KEY_EXTENDED_SLOW_DECAY, isInExtendedSlowDecay)
            .putLong(KEY_LAST_FORCE_SEND, lastForceSendTime)
            .putLong(KEY_LAST_DECAY, lastDecayTime)
            .apply()
    }
}

/** Current abuse level controlling what UI restrictions apply. */
enum class AbuseLevel {
    /** No restrictions — accidental send timer still shows. */
    NONE,

    /** Captcha required before send. */
    CAPTCHA_ONLY,

    /** Captcha + timer + tip about app purpose. */
    MEDIUM_WARNING,

    /** Captcha + timer + stronger warning about group messaging misuse. */
    HIGH_WARNING,

    /** Hard lockout — send blocked for a timed duration. */
    HARD_LOCKOUT
}

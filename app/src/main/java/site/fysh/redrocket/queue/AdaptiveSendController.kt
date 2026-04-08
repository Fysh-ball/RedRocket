package site.fysh.redrocket.queue

import android.util.Log
import site.fysh.redrocket.BuildConfig
import site.fysh.redrocket.model.SendState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicInteger

/**
 * State machine governing the SMS sending strategy per PROJECT_SPEC.md:
 *
 *  MULTI_THREADED (default) → 3 consecutive failures → SEQUENTIAL
 *  SEQUENTIAL → 5 consecutive successes → MULTI_THREADED
 *  SEQUENTIAL (primary exhausted) → LAZARUS
 *  LAZARUS (retry complete) → SEQUENTIAL
 *
 * AtomicInteger counters prevent read-modify-write races in MULTI_THREADED mode.
 * Both counters are reset on every state transition.
 */
class AdaptiveSendController {

    private val TAG = "AdaptiveEngine"

    private val _currentState = MutableStateFlow(SendState.MULTI_THREADED)
    val currentState: StateFlow<SendState> = _currentState.asStateFlow()

    private val consecutiveFailures = AtomicInteger(0)
    private val consecutiveSuccesses = AtomicInteger(0)
    private val lazarusPassCount = AtomicInteger(0)
    @Volatile private var isForceSequential = false
    @Volatile private var keepTrying = true

    /**
     * Called after every send attempt. Drives state transitions per spec.
     *
     * Note: the read-check-write of `_currentState.value` here is not atomic. Two
     * concurrent reportResult(false) calls in MULTI_THREADED mode could both observe
     * MULTI_THREADED and both transition to SEQUENTIAL, double-resetting counters.
     * This is accepted because state transitions are idempotent — the second transition
     * is a no-op semantically (state ends in SEQUENTIAL with both counters at 0 either way).
     */
    fun reportResult(success: Boolean) {
        if (success) {
            val successes = consecutiveSuccesses.incrementAndGet()
            consecutiveFailures.set(0)
            Log.d(
                TAG,
                "reportResult(SUCCESS) consecutiveSuccesses=$successes " +
                    "consecutiveFailures=0 state=${_currentState.value}"
            )
            if (_currentState.value == SendState.SEQUENTIAL
                && !isForceSequential
                && successes >= 5
            ) {
                consecutiveSuccesses.set(0)
                consecutiveFailures.set(0)
                _currentState.value = SendState.MULTI_THREADED
                Log.i(
                    TAG,
                    "STATE TRANSITION: SEQUENTIAL → MULTI_THREADED " +
                        "(5 consecutive successes) consecutiveSuccesses=0 consecutiveFailures=0"
                )
            }
        } else {
            val failures = consecutiveFailures.incrementAndGet()
            consecutiveSuccesses.set(0)
            Log.d(
                TAG,
                "reportResult(FAILURE) consecutiveFailures=$failures " +
                    "consecutiveSuccesses=0 state=${_currentState.value}"
            )
            if (_currentState.value == SendState.MULTI_THREADED && failures >= 3) {
                consecutiveFailures.set(0)
                consecutiveSuccesses.set(0)
                _currentState.value = SendState.SEQUENTIAL
                Log.i(
                    TAG,
                    "STATE TRANSITION: MULTI_THREADED → SEQUENTIAL " +
                        "(3 consecutive failures) consecutiveFailures=0 consecutiveSuccesses=0"
                )
            }
        }
    }

    fun enterLazarusMode() {
        consecutiveFailures.set(0)
        consecutiveSuccesses.set(0)
        _currentState.value = SendState.LAZARUS
        Log.i(
            TAG,
            "STATE TRANSITION: → LAZARUS (primary queue exhausted) " +
                "consecutiveFailures=0 consecutiveSuccesses=0"
        )
    }

    fun exitLazarusMode() {
        lazarusPassCount.set(0)
        consecutiveFailures.set(0)
        consecutiveSuccesses.set(0)
        _currentState.value = SendState.SEQUENTIAL
        Log.i(
            TAG,
            "STATE TRANSITION: LAZARUS → SEQUENTIAL (retry phase complete) " +
                "consecutiveFailures=0 consecutiveSuccesses=0"
        )
    }

    fun incrementLazarusPass() {
        val pass = lazarusPassCount.incrementAndGet()
        Log.d(TAG, "Lazarus pass #$pass complete")
    }

    fun getLazarusPassCount(): Int = lazarusPassCount.get()

    /**
     * Required inter-message delay for the current mode.
     * MULTI_THREADED returns 0 - RateLimiter enforces a 150ms safety floor.
     * SEQUENTIAL and LAZARUS return 200ms.
     */
    fun getRequiredDelayMs(): Long = when (_currentState.value) {
        SendState.MULTI_THREADED -> BuildConfig.MULTI_THREADED_DELAY_MS
        SendState.SEQUENTIAL -> BuildConfig.SEQUENTIAL_DELAY_MS
        SendState.LAZARUS -> BuildConfig.SEQUENTIAL_DELAY_MS
    }

    fun setKeepTrying(enabled: Boolean) {
        keepTrying = enabled
        Log.d(TAG, "Keep Trying set to $enabled")
    }

    fun isKeepTrying(): Boolean = keepTrying

    fun setForceSequential(enabled: Boolean) {
        val wasForced = isForceSequential
        isForceSequential = enabled
        Log.d(TAG, "Force Sequential set to $enabled (was $wasForced) state=${_currentState.value}")
        if (enabled && _currentState.value == SendState.MULTI_THREADED) {
            consecutiveFailures.set(0)
            consecutiveSuccesses.set(0)
            _currentState.value = SendState.SEQUENTIAL
            Log.i(
                TAG,
                "STATE TRANSITION: MULTI_THREADED → SEQUENTIAL (forceSequential ON) " +
                    "consecutiveFailures=0 consecutiveSuccesses=0"
            )
        }
    }

    /** Resets the engine to its starting state (used when starting a new send batch). */
    fun reset() {
        consecutiveFailures.set(0)
        consecutiveSuccesses.set(0)
        lazarusPassCount.set(0)
        _currentState.value = if (isForceSequential) SendState.SEQUENTIAL else SendState.MULTI_THREADED
        Log.i(TAG, "AdaptiveEngine RESET → ${_currentState.value}")
    }
}

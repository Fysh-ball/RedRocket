package site.fysh.redrocket.util

import java.util.Collections
import java.util.LinkedHashMap

/**
 * Deduplicates emergency broadcasts that arrive via multiple paths
 * (cell broadcast receiver + notification listener) or rapid duplicates
 * from carrier retransmissions.
 *
 * Thread-safe: uses a synchronized LinkedHashMap with a 30-second window.
 */
object BroadcastDeduplicator {
    private const val MAX_ENTRIES = 50
    private const val DEDUP_WINDOW_MS = 30_000L

    // contentHash -> timestamp
    private val recent: MutableMap<String, Long> = Collections.synchronizedMap(
        object : LinkedHashMap<String, Long>(MAX_ENTRIES, 0.75f, false) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Long>): Boolean {
                return size > MAX_ENTRIES
            }
        }
    )

    /**
     * Returns true if this content should be processed (not a duplicate).
     * Returns false if the same content was seen within the last 30 seconds.
     */
    fun shouldProcess(content: String): Boolean {
        val hash = content.trim().lowercase().hashCode().toString()
        val now = System.currentTimeMillis()
        synchronized(recent) {
            val lastSeen = recent[hash]
            if (lastSeen != null && now - lastSeen < DEDUP_WINDOW_MS) {
                return false
            }
            recent[hash] = now
            return true
        }
    }
}

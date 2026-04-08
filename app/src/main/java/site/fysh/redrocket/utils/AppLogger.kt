package site.fysh.redrocket.utils

import android.util.Log
import site.fysh.redrocket.model.AppDatabase
import site.fysh.redrocket.model.LogEntry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Lightweight async logger that writes important system events to the Room database.
 * Automatically prunes logs beyond 500 entries. Fire-and-forget: never blocks the caller.
 *
 * Uses an internal application-lifetime scope so log writes are never dropped if a
 * caller's scope is cancelled mid-insert. The `scope` parameter is retained for
 * backwards compatibility but no longer determines the lifetime of the launch.
 */
object AppLogger {
    private const val TAG = "AppLogger"

    // Application-lifetime scope: never cancelled, decoupled from caller lifecycle.
    private val loggerScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Suppress("UNUSED_PARAMETER")
    fun log(database: AppDatabase, scope: CoroutineScope, eventType: String, description: String) {
        loggerScope.launch {
            try {
                database.logEntryDao().insertLog(
                    LogEntry(eventType = eventType, description = description)
                )
                database.logEntryDao().pruneOldLogs()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to write log entry ($eventType)", e)
            }
        }
    }
}

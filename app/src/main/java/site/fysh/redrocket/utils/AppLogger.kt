package site.fysh.redrocket.utils

import android.util.Log
import site.fysh.redrocket.model.AppDatabase
import site.fysh.redrocket.model.LogEntry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Lightweight async logger that writes important system events to the Room database.
 * Automatically prunes logs beyond 500 entries. Fire-and-forget: never blocks the caller.
 */
object AppLogger {
    private const val TAG = "AppLogger"

    fun log(database: AppDatabase, scope: CoroutineScope, eventType: String, description: String) {
        scope.launch(Dispatchers.IO) {
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

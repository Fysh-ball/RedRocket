package site.fysh.redrocket.util

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URL

object UpdateChecker {
    private const val TAG = "UpdateChecker"
    private const val RELEASES_URL =
        "https://api.github.com/repos/Fysh-ball/RedRocket/releases/latest"
    private const val RELEASE_BY_TAG_URL =
        "https://api.github.com/repos/Fysh-ball/RedRocket/releases/tags/v"

    /**
     * Fetches the latest GitHub release tag and returns it if it is newer than [currentVersion].
     * Returns null on any network failure or if already up to date — callers should treat null
     * as "no update available" and not surface an error to the user.
     */
    suspend fun checkForUpdate(currentVersion: String): String? = withContext(Dispatchers.IO) {
        val connection = URL(RELEASES_URL).openConnection() as java.net.HttpURLConnection
        try {
            connection.setRequestProperty("Accept", "application/vnd.github+json")
            connection.connectTimeout = 5_000
            connection.readTimeout = 5_000
            val json = connection.inputStream.use { it.bufferedReader().readText() }
            val tag = JSONObject(json).optString("tag_name", "").trimStart('v', 'V')
            if (tag.isNotBlank() && isNewerVersion(tag, currentVersion)) {
                Log.i(TAG, "Update available: $tag (current: $currentVersion)")
                tag
            } else {
                null
            }
        } catch (e: Exception) {
            Log.d(TAG, "Update check skipped: ${e.message}")
            null
        } finally {
            connection.disconnect()
        }
    }

    /**
     * Fetches the GitHub release body for [version] (e.g. "2.0.6" → tag "v2.0.6").
     * Returns the markdown body string, or null on any failure.
     */
    suspend fun fetchReleaseNotes(version: String): String? = withContext(Dispatchers.IO) {
        val connection = URL("$RELEASE_BY_TAG_URL$version").openConnection() as java.net.HttpURLConnection
        try {
            connection.setRequestProperty("Accept", "application/vnd.github+json")
            connection.connectTimeout = 5_000
            connection.readTimeout = 5_000
            val json = connection.inputStream.use { it.bufferedReader().readText() }
            JSONObject(json).optString("body", "").ifBlank { null }
        } catch (e: Exception) {
            Log.d(TAG, "Could not fetch release notes for v$version: ${e.message}")
            null
        } finally {
            connection.disconnect()
        }
    }

    /** Returns true if [latest] is a higher version number than [current]. */
    private fun isNewerVersion(latest: String, current: String): Boolean {
        val l = latest.split(".").mapNotNull { it.toIntOrNull() }
        val c = current.split(".").mapNotNull { it.toIntOrNull() }
        for (i in 0 until maxOf(l.size, c.size)) {
            val lv = l.getOrElse(i) { 0 }
            val cv = c.getOrElse(i) { 0 }
            if (lv > cv) return true
            if (lv < cv) return false
        }
        return false
    }
}

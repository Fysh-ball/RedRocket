package site.fysh.redrocket.util

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import site.fysh.redrocket.BuildConfig
import site.fysh.redrocket.utils.AppSettings
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

object AlertEnricher {
    private const val TAG = "AlertEnricher"

    suspend fun enrich(context: Context, timeoutMs: Long = 2000L): String? {
        return withTimeoutOrNull(timeoutMs) {
            try {
                enrichInternal(context)
            } catch (e: Exception) {
                Log.d(TAG, "Enrichment failed: ${e.message}")
                null
            }
        }
    }

    private suspend fun enrichInternal(context: Context): String? {
        val settings = AppSettings(context)
        if (!settings.locationEnrichmentEnabled.first()) return null

        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) {
            Log.d(TAG, "No location permission")
            return null
        }

        val location = getLastKnownLocation(context) ?: run {
            Log.d(TAG, "No cached location available")
            return null
        }

        return withContext(Dispatchers.IO) {
            fetchNearbyContext(location)
        }
    }

    @Suppress("MissingPermission")
    private fun getLastKnownLocation(context: Context): Location? {
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val providers = listOf(
            LocationManager.FUSED_PROVIDER,
            LocationManager.NETWORK_PROVIDER,
            LocationManager.GPS_PROVIDER
        )
        for (provider in providers) {
            try {
                val loc = lm.getLastKnownLocation(provider)
                if (loc != null) return loc
            } catch (e: Exception) { Log.d(TAG, "Location provider $provider failed: ${e.message}") }
        }
        return null
    }

    private fun fetchNearbyContext(location: Location): String? {
        val url = "${BuildConfig.EHW_API_URL}/api/v1/events/nearby" +
            "?lat=${location.latitude}&lon=${location.longitude}" +
            "&radius_km=50&since=1h&min_score=30&limit=3"
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 2_000
            connection.readTimeout = 2_000
            connection.setRequestProperty("Accept", "application/json")

            val responseCode = connection.responseCode
            if (responseCode !in 200..299) {
                Log.d(TAG, "EHW API returned HTTP $responseCode")
                return null
            }

            val json = connection.inputStream.use { it.bufferedReader().readText() }
            return parseTopEvent(json, location)
        } catch (e: Exception) {
            Log.d(TAG, "EHW API call failed: ${e.message}")
            return null
        } finally {
            connection.disconnect()
        }
    }

    private fun parseTopEvent(json: String, deviceLocation: Location): String? {
        val root = JSONObject(json)
        val events = root.optJSONArray("events") ?: return null
        if (events.length() == 0) return null

        val event = events.getJSONObject(0)
        val title = event.optString("title", "").ifBlank { return null }

        val evtLat = event.optDouble("latitude", Double.NaN)
        val evtLon = event.optDouble("longitude", Double.NaN)

        val distanceStr = if (!evtLat.isNaN() && !evtLon.isNaN()) {
            val km = haversineKm(
                deviceLocation.latitude, deviceLocation.longitude,
                evtLat, evtLon
            )
            val bearing = bearing(
                deviceLocation.latitude, deviceLocation.longitude,
                evtLat, evtLon
            )
            ", ${formatDistance(km)} ${compassDirection(bearing)}"
        } else {
            ""
        }

        return "Nearby: $title$distanceStr"
    }

    private fun haversineKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).pow(2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2)
        return r * 2 * atan2(sqrt(a), sqrt(1 - a))
    }

    private fun bearing(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dLon = Math.toRadians(lon2 - lon1)
        val rLat1 = Math.toRadians(lat1)
        val rLat2 = Math.toRadians(lat2)
        val y = sin(dLon) * cos(rLat2)
        val x = cos(rLat1) * sin(rLat2) - sin(rLat1) * cos(rLat2) * cos(dLon)
        return (Math.toDegrees(atan2(y, x)) + 360) % 360
    }

    private fun compassDirection(degrees: Double): String = when {
        degrees < 22.5 || degrees >= 337.5 -> "N"
        degrees < 67.5 -> "NE"
        degrees < 112.5 -> "E"
        degrees < 157.5 -> "SE"
        degrees < 202.5 -> "S"
        degrees < 247.5 -> "SW"
        degrees < 292.5 -> "W"
        else -> "NW"
    }

    private fun formatDistance(km: Double): String {
        return if (km < 1.0) "${(km * 1000).roundToInt()}m"
        else "${(km * 10).roundToInt() / 10.0}km"
    }
}

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

    /**
     * Search radius for the "Nearby:" line, in kilometres.
     *
     * City-sized, not metro-sized. This was 50km, which reaches well past a
     * city into neighbouring towns, so the line could report something an hour
     * away as "nearby" on an emergency SMS. 15km covers a typical city from a
     * central point while staying inside it.
     *
     * Deliberately conservative. A miss costs nothing, since enrichment is
     * optional decoration on the outbound message, but a hit that points
     * somewhere the recipient is not costs credibility at the worst moment.
     */
    private const val SEARCH_RADIUS_KM = 15

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

    // Internal so the query contract is pinned by a test. The endpoint ignores
    // unknown parameters rather than rejecting them, so a misspelled name here
    // would silently widen the search instead of failing.
    internal fun buildNearbyUrl(base: String, lat: Double, lon: Double): String =
        "$base/api/v1/events/nearby" +
            "?lat=$lat&lon=$lon" +
            "&radius_km=$SEARCH_RADIUS_KM&since=1h&min_score=30&limit=3"

    private fun fetchNearbyContext(location: Location): String? {
        val url = buildNearbyUrl(
            BuildConfig.EHW_API_URL, location.latitude, location.longitude
        )
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
            return parseTopEvent(json, location.latitude, location.longitude)
        } catch (e: Exception) {
            Log.d(TAG, "EHW API call failed: ${e.message}")
            return null
        } finally {
            connection.disconnect()
        }
    }

    // Takes plain doubles rather than an android.location.Location so the parsing
    // and geometry can be unit tested off-device against a real captured payload.
    internal fun parseTopEvent(json: String, devLat: Double, devLon: Double): String? {
        val root = JSONObject(json)
        val events = root.optJSONArray("events") ?: return null
        if (events.length() == 0) return null

        val event = events.getJSONObject(0)
        val title = event.optString("title", "").ifBlank { return null }

        // EHW nests coordinates under "location", it has no top-level latitude /
        // longitude keys. Reading them flat meant both values were always NaN, so
        // every enrichment silently degraded to the bare title and the distance
        // and bearing code below had never once run.
        val loc = event.optJSONObject("location")
        val evtLat = loc?.optDouble("lat", Double.NaN) ?: Double.NaN
        val evtLon = loc?.optDouble("lon", Double.NaN) ?: Double.NaN

        val distanceStr = if (!evtLat.isNaN() && !evtLon.isNaN()) {
            val km = haversineKm(devLat, devLon, evtLat, evtLon)
            val bearing = bearing(devLat, devLon, evtLat, evtLon)
            ", ${formatDistance(km)} ${compassDirection(bearing)}"
        } else {
            // Degrade to title-only, but say so. This branch being silent is what
            // let the flat-key bug survive: the output stayed plausible.
            Log.w(TAG, "Event ${event.optString("id", "?")} carried no usable coordinates - title only")
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

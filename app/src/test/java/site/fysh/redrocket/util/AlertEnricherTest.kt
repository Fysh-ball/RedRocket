package site.fysh.redrocket.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Regression cover for the EHW enrichment parser.
 *
 * The bug these exist for: parseTopEvent read "latitude" and "longitude" as
 * top-level keys. EHW nests them under "location", so both were always NaN,
 * the distance suffix was always empty, and the haversine and bearing helpers
 * had never executed in production. The output stayed plausible ("Nearby: X"),
 * which is why it went unnoticed.
 *
 * NEARBY_JSON below is a real response captured from
 * GET /api/v1/events/nearby?lat=40.7&lon=-74.0&radius_km=500, trimmed to two
 * events. Its shape is the contract, so if EHW ever moves the coordinates
 * again these fail rather than silently degrading.
 */
class AlertEnricherTest {

    // Device position used for every case, matching the captured request.
    private val devLat = 40.7
    private val devLon = -74.0

    private val nearbyJson = """
        {"center":{"lat":40.7,"lon":-74},"count":2,"radius_km":500,"events":[
          {"schema_version":1,"id":"gdelt:1316143044","source":"gdelt",
           "category":"civil_emergency","severity_score":55,"act_worthy":false,
           "title":"Fight reported in New York, United States",
           "summary":"Fight activity detected near New York, United States.",
           "language":"en",
           "location":{"lat":42.1497,"lon":-74.9384,"radius_km":50,
                       "name":"New York, United States"},
           "starts_at":1785458798,"observed_at":1785458798,
           "expires_at":1785480398,"country_iso":"USA","confidence_score":0.55},
          {"schema_version":1,"id":"nws:second","source":"noaa_nws",
           "category":"weather","severity_score":40,"act_worthy":false,
           "title":"Flood Warning","language":"en",
           "location":{"lat":41.0,"lon":-74.5,"radius_km":25,"name":"Somewhere"},
           "starts_at":1785450588,"observed_at":1785450588,
           "expires_at":1785480398,"country_iso":"USA","confidence_score":0.8}
        ]}
    """.trimIndent()

    /**
     * The headline assertion. Before the fix this returned the bare title with
     * no suffix, so this test fails against the old parser.
     *
     * 179.2km NW is the independently computed great-circle distance and
     * initial bearing from 40.7,-74.0 to 42.1497,-74.9384.
     */
    @Test
    fun `top event carries distance and bearing derived from nested location`() {
        assertEquals(
            "Nearby: Fight reported in New York, United States, 179.2km NW",
            AlertEnricher.parseTopEvent(nearbyJson, devLat, devLon)
        )
    }

    /** Only the first event is reported, regardless of how many are returned. */
    @Test
    fun `only the top event is used`() {
        val out = AlertEnricher.parseTopEvent(nearbyJson, devLat, devLon)!!
        assertEquals(false, out.contains("Flood Warning"))
    }

    /**
     * Guards the specific regression: a payload whose coordinates sit ONLY at
     * the top level, the shape the old code expected, must not produce a
     * distance. If someone reintroduces the flat read this starts passing a
     * distance through and this test fails.
     */
    @Test
    fun `flat top-level coordinates are not treated as a location`() {
        val flat = """
            {"count":1,"events":[
              {"id":"x","title":"Flat coords","latitude":42.1497,"longitude":-74.9384}
            ]}
        """.trimIndent()
        assertEquals("Nearby: Flat coords", AlertEnricher.parseTopEvent(flat, devLat, devLon))
    }

    /** A location object without usable numbers degrades to title only. */
    @Test
    fun `missing coordinates degrade to title only`() {
        val noCoords = """
            {"count":1,"events":[
              {"id":"x","title":"No coords","location":{"name":"Somewhere"}}
            ]}
        """.trimIndent()
        assertEquals("Nearby: No coords", AlertEnricher.parseTopEvent(noCoords, devLat, devLon))
    }

    /** An empty result set is not an enrichment. */
    @Test
    fun `empty event list yields null`() {
        assertNull(AlertEnricher.parseTopEvent("""{"count":0,"events":[]}""", devLat, devLon))
    }

    /** A response with no events array at all is not an enrichment. */
    @Test
    fun `absent events array yields null`() {
        assertNull(AlertEnricher.parseTopEvent("""{"count":0}""", devLat, devLon))
    }

    /** A blank title is not worth sending. */
    @Test
    fun `blank title yields null`() {
        val blank = """{"count":1,"events":[{"id":"x","title":"","location":{"lat":41.0,"lon":-74.5}}]}"""
        assertNull(AlertEnricher.parseTopEvent(blank, devLat, devLon))
    }

    /**
     * The search radius is city-sized on purpose. It was 50km, which reaches
     * past a city into neighbouring towns and could label something an hour
     * away as "nearby" on an emergency SMS. Widening it is a deliberate
     * decision, so it should have to break a test.
     */
    @Test
    fun `search radius stays city-sized`() {
        val url = AlertEnricher.buildNearbyUrl("https://x", 40.7, -74.0)
        assertEquals(true, url.contains("&radius_km=15&"))
    }

    /**
     * The endpoint ignores unknown query parameters instead of rejecting them,
     * so a typo here would silently drop a filter and widen the search rather
     * than fail. Pin the exact string.
     */
    @Test
    fun `nearby url matches the endpoint contract`() {
        assertEquals(
            "https://x/api/v1/events/nearby" +
                "?lat=40.7&lon=-74.0&radius_km=15&since=1h&min_score=30&limit=3",
            AlertEnricher.buildNearbyUrl("https://x", 40.7, -74.0)
        )
    }

    /** Sub-kilometre distances render in metres, not as "0.0km". */
    @Test
    fun `very close events render in metres`() {
        val close = """
            {"count":1,"events":[
              {"id":"x","title":"Right here","location":{"lat":40.7018,"lon":-74.0}}
            ]}
        """.trimIndent()
        val out = AlertEnricher.parseTopEvent(close, devLat, devLon)!!
        assertEquals("Nearby: Right here, 200m N", out)
    }
}

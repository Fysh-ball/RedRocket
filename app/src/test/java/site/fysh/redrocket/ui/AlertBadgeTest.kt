package site.fysh.redrocket.ui

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Alert History badges. The French cases are the regression: resolveAlertBadge
 * matched on a raw `lowercase()` against English-only phrases, so on a Quebec
 * handset an AMBER alert lost its AMBER badge and, worse, a monthly test
 * broadcast was badged as a real alert.
 */
class AlertBadgeTest {

    private fun label(source: String, content: String) =
        resolveAlertBadge(source, content).label

    // ---------- AMBER ----------

    @Test
    fun `English amber phrasings badge as AMBER`() {
        assertEquals("AMBER Alert", label("alert", "AMBER ALERT: Ottawa, silver sedan"))
        assertEquals("AMBER Alert", label("alert", "Child abduction (Amber alert)"))
    }

    @Test
    fun `French amber phrasings badge as AMBER`() {
        assertEquals("AMBER Alert", label("alert", "Enlèvement enfant (alerte Amber)"))
        assertEquals("AMBER Alert", label("alert", "Alerte Enlèvement"))
    }

    @Test
    fun `accents do not decide the AMBER badge`() {
        assertEquals("AMBER Alert", label("alert", "ALERTE ENLÈVEMENT"))
        assertEquals("AMBER Alert", label("alert", "alerte enlevement"))
    }

    // ---------- Test broadcasts ----------

    @Test
    fun `English test broadcasts badge as Test`() {
        assertEquals("Test Alert", label("alert", "This is a test of the emergency alert system"))
        assertEquals("Test Alert", label("alert", "Required Monthly Test"))
        assertEquals("Test Alert", label("cell_broadcast", "Nationwide test - no action required"))
    }

    @Test
    fun `French test broadcasts badge as Test`() {
        // The dangerous one: without this, a drill reads as a live alert.
        assertEquals("Test Alert", label("alert", "Ceci est un test du système d'alerte"))
        assertEquals("Test Alert", label("alert", "Test mensuel requis"))
        assertEquals("Test Alert", label("alert", "Ceci est un exercice"))
    }

    @Test
    fun `test vocabulary is shared with the trigger hard block`() {
        // Any language HARD_TEST_PHRASES covers must badge, not just the two the
        // badge used to know about. If these diverge, one copy has gone stale.
        assertEquals("Test Alert", label("alert", "Dies ist ein Test"))
        assertEquals("Test Alert", label("alert", "Esto es una prueba"))
        assertEquals("Test Alert", label("alert", "これはテストです"))
    }

    // ---------- Real alerts must not be badged as tests ----------

    @Test
    fun `real alerts are not badged as tests`() {
        val real = label("alert", "Emergency alert. Flash flooding in your area. Move to higher ground.")
        assertEquals("Alert", real.takeIf { it != "Test Alert" && it != "AMBER Alert" } ?: real)
        assert(real != "Test Alert") { "a real flood alert must not badge as Test, got $real" }
    }

    @Test
    fun `a real French alert is not badged as a test`() {
        val real = label("alert", "Alerte d'urgence. Inondation soudaine dans votre secteur.")
        assert(real != "Test Alert") { "a real French flood alert must not badge as Test, got $real" }
        assert(real != "AMBER Alert") { "not an abduction, got $real" }
    }

    @Test
    fun `the word exercise alone does not make it a test`() {
        // SOFT_TEST_PHRASES is deliberately excluded from isExplicitTestBroadcast:
        // a single loose word must not tell the user a live alert is a drill.
        val r = label("alert", "Emergency alert. Evacuate now. Follow the exercise yard route.")
        assert(r != "Test Alert") { "a bare 'exercise' must not badge as Test, got $r" }
    }

    // ---------- Source-based fallbacks still work ----------

    @Test
    fun `non-EAS notification keeps its source badge`() {
        assertEquals("Notification", label("notification", "Package delivered"))
    }
}

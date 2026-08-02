package site.fysh.redrocket.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The alert strings below are AOSP CellBroadcastReceiver's own rendered titles
 * (res/values, res/values-fr, res/values-fr-rCA), not invented text, so these
 * tests pin the matcher against what a handset actually shows.
 *
 * The French cases are the regression: every one of them returned false before
 * EasContentMatcher existed, which meant a Quebec phone detected nothing from an
 * OEM package outside the known list. Deleting the French entries from
 * PHRASE_SOURCE must turn this file red.
 */
class EasContentMatcherTest {

    // ---------- English: must keep working ----------

    @Test
    fun `matches AOSP English emergency alert title`() {
        assertTrue(EasContentMatcher.looksLikeEASContent("Emergency alert"))
    }

    @Test
    fun `matches AOSP English extreme and severe variants`() {
        assertTrue(EasContentMatcher.looksLikeEASContent("Emergency alert: Extreme"))
        assertTrue(EasContentMatcher.looksLikeEASContent("Emergency alert: Severe"))
    }

    @Test
    fun `matches AOSP English amber phrasing`() {
        assertTrue(EasContentMatcher.looksLikeEASContent("Child abduction (Amber alert)"))
        assertTrue(EasContentMatcher.looksLikeEASContent("AMBER ALERT: Ottawa, silver sedan"))
    }

    @Test
    fun `matches wireless emergency alerts app label`() {
        assertTrue(EasContentMatcher.looksLikeEASContent("Wireless emergency alerts"))
    }

    @Test
    fun `matches presidential and EAS event names`() {
        assertTrue(EasContentMatcher.looksLikeEASContent("Presidential alert"))
        assertTrue(EasContentMatcher.looksLikeEASContent("CIVIL EMERGENCY MESSAGE"))
        assertTrue(EasContentMatcher.looksLikeEASContent("National Emergency"))
    }

    @Test
    fun `matches public safety message which the English-only list missed`() {
        // An FCC WEA category that the previous hard-coded list did not contain,
        // so this one is new coverage in English, not only in French.
        assertTrue(EasContentMatcher.looksLikeEASContent("Public safety message"))
    }

    // ---------- French: the regression this change exists for ----------

    @Test
    fun `matches Canadian French emergency alert title`() {
        assertTrue(EasContentMatcher.looksLikeEASContent("Alerte d'urgence"))
    }

    @Test
    fun `matches Canadian French severity variants`() {
        assertTrue(EasContentMatcher.looksLikeEASContent("Alerte d'urgence : extrême"))
        assertTrue(EasContentMatcher.looksLikeEASContent("Alerte d'urgence : grave"))
        assertTrue(EasContentMatcher.looksLikeEASContent("Alerte d'urgence : critique"))
    }

    @Test
    fun `matches French wireless emergency alerts app label`() {
        assertTrue(EasContentMatcher.looksLikeEASContent("Alertes d'urgence sans fil"))
    }

    @Test
    fun `matches French amber phrasings from both locales`() {
        assertTrue(EasContentMatcher.looksLikeEASContent("Enlèvement enfant (alerte Amber)"))
        assertTrue(EasContentMatcher.looksLikeEASContent("Alerte Enlèvement"))
    }

    @Test
    fun `matches French presidential and national naming`() {
        assertTrue(EasContentMatcher.looksLikeEASContent("Alerte présidentielle"))
        assertTrue(EasContentMatcher.looksLikeEASContent("Alerte nationale"))
    }

    @Test
    fun `matches French public safety message`() {
        assertTrue(EasContentMatcher.looksLikeEASContent("Message de sécurité publique"))
    }

    @Test
    fun `matches a realistic bilingual Alert Ready broadcast`() {
        val body = "Alerte d'urgence. Inondation soudaine dans votre secteur. " +
            "Déplacez-vous vers un terrain plus élevé. " +
            "Emergency alert. Flash flooding in your area. Move to higher ground."
        assertTrue(EasContentMatcher.looksLikeEASContent(body))
    }

    // ---------- Accents and case must not decide the outcome ----------

    @Test
    fun `accented and unaccented spellings both match`() {
        assertTrue(EasContentMatcher.looksLikeEASContent("Alerte présidentielle"))
        assertTrue(EasContentMatcher.looksLikeEASContent("Alerte presidentielle"))
        assertTrue(EasContentMatcher.looksLikeEASContent("ALERTE PRÉSIDENTIELLE"))
    }

    @Test
    fun `apostrophe style does not decide the outcome`() {
        // Straight, typographic, and the space the normalizer leaves behind.
        assertTrue(EasContentMatcher.looksLikeEASContent("Alerte d'urgence"))
        assertTrue(EasContentMatcher.looksLikeEASContent("Alerte d’urgence"))
        assertTrue(EasContentMatcher.looksLikeEASContent("ALERTE D URGENCE"))
    }

    // ---------- Must NOT match ----------

    @Test
    fun `ordinary notifications do not match`() {
        assertFalse(EasContentMatcher.looksLikeEASContent("Your package has been delivered"))
        assertFalse(EasContentMatcher.looksLikeEASContent("3 nouveaux messages"))
        assertFalse(EasContentMatcher.looksLikeEASContent("Batterie faible"))
        assertFalse(EasContentMatcher.looksLikeEASContent("Rappel : rendez-vous à 14 h"))
    }

    @Test
    fun `blank content does not match`() {
        assertFalse(EasContentMatcher.looksLikeEASContent(""))
        assertFalse(EasContentMatcher.looksLikeEASContent("   "))
    }

    @Test
    fun `system branding alone does not match`() {
        // "En Alerte" is deliberately not a phrase: it is two ordinary French
        // words. If someone adds it, this test should start failing and the
        // false-positive cost should be argued explicitly.
        assertFalse(EasContentMatcher.looksLikeEASContent("Restez en alerte cette semaine"))
    }

    // ---------- The phrase list itself ----------

    @Test
    fun `every phrase survives normalization`() {
        val phrases = EasContentMatcher.normalizedPhrases()
        assertTrue("phrase list should not be empty", phrases.isNotEmpty())
        assertEquals("no phrase may normalize to empty", 0, phrases.count { it.isBlank() })
        // A phrase that still holds an apostrophe or accent would never match
        // normalized content, so it would be dead weight that silently covers
        // nothing.
        assertEquals(
            "phrases must already be in normalized form",
            emptyList<String>(),
            phrases.filter { it != FalseAlarmDetector.normalize(it) }
        )
    }

    @Test
    fun `list covers both languages`() {
        val phrases = EasContentMatcher.normalizedPhrases()
        assertTrue("expected English entries", phrases.any { it.contains("emergency") })
        assertTrue("expected French entries", phrases.any { it.contains("urgence") })
    }
}

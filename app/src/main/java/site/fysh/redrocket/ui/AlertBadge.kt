package site.fysh.redrocket.ui

import androidx.compose.ui.graphics.Color

/**
 * Resolved badge appearance for a PastAlert entry.
 * Used by both PastAlertsDialog and ResponseDashboard to ensure consistent
 * colours and labels across all alert history views.
 *
 * Only the badge pill is coloured - card backgrounds stay neutral.
 */
internal data class AlertBadge(
    val label: String,
    val badgeBackground: Color,  // pill background
    val badgeText: Color         // pill text
)

/**
 * Derives the correct [AlertBadge] from an alert's source and message content.
 *
 * Sources:
 *  "alert" - EAS/WEA notification from a known or content-detected package
 *  "cell_broadcast" - direct cell broadcast (CMAS/WEA/ETWS)
 *  "notification" - non-EAS app notification whose keywords matched a scenario
 *  "manual" - manual force send (logs only, not shown in Alert History)
 *
 * Content detection (Amber, Test) is applied before source-based grouping so the
 * correct badge appears regardless of which path the alert came from.
 */
internal fun resolveAlertBadge(source: String, content: String): AlertBadge {
    // Normalized, not just lowercased: Alert History has to badge French alerts as
    // correctly as English ones, and raw lowercase makes "Enlèvement" and
    // "Alerte d'urgence" miss on the accent and the apostrophe. Badging a French
    // test broadcast as a real alert is the failure that matters here.
    val lower = site.fysh.redrocket.util.FalseAlarmDetector.normalize(content)

    // Content: AMBER - highest priority, applies to all sources.
    // French names from AOSP CellBroadcastReceiver values-fr / values-fr-rCA.
    if (lower.contains("amber alert") || lower.contains("child abduction") ||
        lower.contains("alerte amber") || lower.contains("alerte enlevement") ||
        lower.contains("enlevement enfant")) {
        return AlertBadge(
            label = "AMBER Alert",
            badgeBackground = Color(0xFFE65100).copy(alpha = 0.15f),
            badgeText = Color(0xFFBF360C)
        )
    }

    // Content: Test broadcast - applies to all sources.
    // The "states outright that it is a test" half is delegated to
    // FalseAlarmDetector, which already carries that vocabulary in twenty-odd
    // languages and is the same list the trigger Hard Block uses; keeping a second
    // copy here is how this badge ended up English-only in the first place. What
    // stays local is the scheduled-test naming, which is regional rather than
    // linguistic. French names from AOSP values-fr-rCA ("Test mensuel requis").
    val isTest = site.fysh.redrocket.util.FalseAlarmDetector.isExplicitTestBroadcast(content) ||
                 lower.contains("required monthly test") ||
                 lower.contains("required weekly test") ||
                 lower.contains("required quarterly test") ||
                 lower.contains("nationwide test") ||
                 lower.contains("test mensuel") ||
                 lower.contains("test hebdomadaire") ||
                 lower.contains("test trimestriel") ||
                 lower.contains("test national") ||
                 lower.contains("ipaws") ||
                 (lower.contains("test") && lower.contains("emergency alert system")) ||
                 (lower.contains("test") && lower.contains("systeme d alerte"))
    if (isTest) {
        return AlertBadge(
            label = "Test Alert",
            badgeBackground = Color(0xFF2E7D32).copy(alpha = 0.15f),
            badgeText = Color(0xFF2E7D32)
        )
    }

    // Source: non-EAS app notification
    if (source == "notification" || source == "notification_wide") {
        return AlertBadge(
            label = "Notification",
            badgeBackground = Color(0xFF1565C0).copy(alpha = 0.15f),
            badgeText = Color(0xFF1565C0)
        )
    }

    // Source: EAS / cell broadcast
    if (source == "alert" || source == "cell_broadcast") {
        return AlertBadge(
            label = "Alert",
            badgeBackground = Color(0xFFC62828).copy(alpha = 0.15f),
            badgeText = Color(0xFFC62828)
        )
    }

    // Source: manual send
    if (source == "manual") {
        return AlertBadge(
            label = "Manual Send",
            badgeBackground = Color(0xFF37474F).copy(alpha = 0.12f),
            badgeText = Color(0xFF37474F)
        )
    }

    return AlertBadge(
        label = "Unknown",
        badgeBackground = Color(0xFF37474F).copy(alpha = 0.12f),
        badgeText = Color(0xFF37474F)
    )
}

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
    val lower = content.lowercase()

    // Content: AMBER - highest priority, applies to all sources
    if (lower.contains("amber alert") || lower.contains("child abduction")) {
        return AlertBadge(
            label = "AMBER Alert",
            badgeBackground = Color(0xFFE65100).copy(alpha = 0.15f),
            badgeText = Color(0xFFBF360C)
        )
    }

    // Content: Test broadcast - applies to all sources
    val isTest = lower.contains("this is a test") ||
                 lower.contains("required monthly test") ||
                 lower.contains("required weekly test") ||
                 lower.contains("required quarterly test") ||
                 lower.contains("nationwide test") ||
                 lower.contains("ipaws") ||
                 (lower.contains("test") && lower.contains("emergency alert system"))
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

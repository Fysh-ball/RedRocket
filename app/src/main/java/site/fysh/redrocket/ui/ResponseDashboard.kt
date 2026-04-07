package site.fysh.redrocket.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import site.fysh.redrocket.model.LogEntry
import site.fysh.redrocket.model.PastAlert
import site.fysh.redrocket.model.Recipient
import site.fysh.redrocket.model.ResponseRecord
import site.fysh.redrocket.model.Scenario
import site.fysh.redrocket.service.SmsResponseReceiver
import site.fysh.redrocket.util.normalizePhone
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

private fun timeAgo(millis: Long): String {
    val diff = System.currentTimeMillis() - millis
    return when {
        diff < 60_000 -> "just now"
        diff < 3_600_000 -> "${TimeUnit.MILLISECONDS.toMinutes(diff)} min ago"
        diff < 86_400_000 -> "${TimeUnit.MILLISECONDS.toHours(diff)} hr ago"
        else -> "${TimeUnit.MILLISECONDS.toDays(diff)} day(s) ago"
    }
}

/**
 * Shared dashboard across ALL scenarios.
 * Shows:
 *   Section 1 - Active Response System (responses/status per scenario)
 *   Section 2 - Logs (recent system events)
 *   Section 3 - Alert History (past triggered alerts)
 */
@Composable
fun ResponseDashboard(
    allScenarios: List<Scenario>,
    responses: List<ResponseRecord>,
    logs: List<LogEntry>,
    pastAlerts: List<PastAlert>,
    onDismiss: () -> Unit,
    onClearResponses: () -> Unit,
    onClearLogs: () -> Unit,
    onClearPastAlerts: () -> Unit,
    onStopListening: () -> Unit = {},
    inlineMode: Boolean = false
) {
    val configuration = LocalConfiguration.current
    val screenWidth = configuration.screenWidthDp.dp

    // Merge ALL recipients from ALL scenarios (all groups), dedup by normalized phone
    val recipients = remember(allScenarios) {
        allScenarios
            .flatMap { it.allRecipients() }
            .distinctBy { normalizePhone(it.phoneNumber) }
    }

    val responseMap = remember(responses) {
        responses
            .sortedByDescending { it.receivedAt }
            .distinctBy { normalizePhone(it.phoneNumber) }
            .associateBy { normalizePhone(it.phoneNumber) }
    }

    val totalCount = recipients.size
    val respondedCount = recipients.count { r -> responseMap.containsKey(normalizePhone(r.phoneNumber)) }
    val safeCount = recipients.count { responseMap[normalizePhone(it.phoneNumber)]?.responseCode == 1 }
    val updatesCount = recipients.count { responseMap[normalizePhone(it.phoneNumber)]?.responseCode == 2 }
    val urgentCount = recipients.count { responseMap[normalizePhone(it.phoneNumber)]?.responseCode == 3 }
    val progressFraction = if (totalCount > 0) respondedCount.toFloat() / totalCount else 0f

    // Contacts who were messaged but have not replied yet
    val noResponseRecipients = remember(recipients, responseMap) {
        recipients.filter { !responseMap.containsKey(normalizePhone(it.phoneNumber)) }
    }

    // Pulsing animation for urgent
    val infiniteTransition = rememberInfiniteTransition(label = "urgentPulse")
    val urgentAlpha by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(600), RepeatMode.Reverse),
        label = "urgentAlpha"
    )

    val safeGreen = Color(0xFF4CAF50)
    val updatesBlue = Color(0xFF2196F3)
    val primaryColor = MaterialTheme.colorScheme.primary
    val errorColor = MaterialTheme.colorScheme.error

    // Listening state
    // Driven by StateFlow - updates immediately on startListening()/stopListening() calls,
    // even across process death (listenStartTime is persisted to SharedPreferences).
    val listenStartTime by SmsResponseReceiver.listenStartTimeFlow.collectAsState()
    var isCurrentlyListening by remember { mutableStateOf(SmsResponseReceiver.isListening()) }
    var listenElapsedText by remember { mutableStateOf("") }
    // Ticker only runs when a listening window is active; restarts whenever the window changes.
    LaunchedEffect(listenStartTime) {
        if (listenStartTime == 0L) {
            isCurrentlyListening = false
            listenElapsedText = ""
            return@LaunchedEffect
        }
        while (true) {
            isCurrentlyListening = SmsResponseReceiver.isListening()
            listenElapsedText = if (isCurrentlyListening) {
                val elapsed = System.currentTimeMillis() - listenStartTime
                val hrs = TimeUnit.MILLISECONDS.toHours(elapsed)
                val mins = TimeUnit.MILLISECONDS.toMinutes(elapsed) % 60
                val secs = TimeUnit.MILLISECONDS.toSeconds(elapsed) % 60
                if (hrs > 0) "%d:%02d:%02d".format(hrs, mins, secs)
                else "%02d:%02d".format(mins, secs)
            } else ""
            if (!isCurrentlyListening) break
            delay(1000)
        }
    }

    // Status indicator
    val highestSeverity = when {
        urgentCount > 0 -> 3
        updatesCount > 0 -> 2
        safeCount > 0 -> 1
        else -> 0
    }

    val statusPulse by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            tween(if (highestSeverity == 3) 400 else 800),
            RepeatMode.Reverse
        ),
        label = "statusPulse"
    )

    // Date formatters
    val timeFormat = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }
    val dateFormat = remember { SimpleDateFormat("MMM d, h:mm a", Locale.getDefault()) }

    // Logs collapsed by default (user-controlled collapse per AGENTS.md)
    var logsExpanded by remember { mutableStateOf(false) }
    // Alert History collapsed by default - owned here so it survives inlineMode/dialog switches
    var alertHistoryExpanded by remember { mutableStateOf(false) }

    @Composable
    fun DashboardContent() {
        LazyColumn(modifier = Modifier.fillMaxSize()) {

            // Drag handle (dialog mode only)
            if (!inlineMode) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 10.dp, bottom = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(width = 36.dp, height = 4.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f))
                        )
                    }
                }
            }

            // Tab header (inline mode only)
            if (inlineMode) {
                item {
                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                        Text(
                            "Dashboard",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        HorizontalDivider(modifier = Modifier.padding(top = 6.dp))
                    }
                }
            }

            // Listening timer + Stop button
            if (isCurrentlyListening) {
                item {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(start = 14.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "Listening for responses",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (listenElapsedText.isNotEmpty()) {
                                    Text(
                                        listenElapsedText,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                            TextButton(
                                onClick = onStopListening,
                                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                            ) {
                                Text("Stop", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            }
                        }
                    }
                }
            }

            // Section 1: Active Response System
            when {
                recipients.isEmpty() -> {
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 40.dp),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                Icons.Default.Inbox,
                                contentDescription = null,
                                modifier = Modifier.size(56.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                            )
                            Spacer(Modifier.height(12.dp))
                            Text(
                                "No recipients added",
                                fontSize = 17.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                "Add contacts to your scenario\nto track their responses",
                                fontSize = 15.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }

                respondedCount == 0 && !isCurrentlyListening -> {
                    item {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 20.dp),
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            tonalElevation = 0.dp
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 32.dp, horizontal = 24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                val standbyPulse by infiniteTransition.animateFloat(
                                    initialValue = 0.4f,
                                    targetValue = 0.9f,
                                    animationSpec = infiniteRepeatable(tween(1200), RepeatMode.Reverse),
                                    label = "standbyPulse"
                                )
                                Box(
                                    modifier = Modifier
                                        .size(20.dp)
                                        .background(Color(0xFF4CAF50).copy(alpha = standbyPulse), CircleShape)
                                )
                                Spacer(Modifier.height(16.dp))
                                Text(
                                    "On Standby",
                                    fontSize = 22.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    "${recipients.size} contact(s) ready",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    "Monitoring for emergency alerts",
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }

                else -> {
                    item { Spacer(Modifier.height(14.dp)) }

                    // Stat cards
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            LargeStatCard(
                                label = "Safe",
                                count = safeCount,
                                numColor = safeGreen,
                                containerColor = safeGreen.copy(alpha = 0.1f),
                                modifier = Modifier.weight(1f)
                            )
                            LargeStatCard(
                                label = "Updates",
                                count = updatesCount,
                                numColor = updatesBlue,
                                containerColor = updatesBlue.copy(alpha = 0.1f),
                                modifier = Modifier.weight(1f)
                            )
                            LargeStatCard(
                                label = "URGENT",
                                count = urgentCount,
                                numColor = errorColor.copy(alpha = if (urgentCount > 0) urgentAlpha else 1f),
                                containerColor = errorColor.copy(alpha = if (urgentCount > 0) urgentAlpha * 0.15f else 0.07f),
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    item { Spacer(Modifier.height(12.dp)) }

                    // Progress bar
                    item {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp),
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.surface,
                            tonalElevation = 1.dp
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        "$respondedCount of $totalCount responded",
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 15.sp,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        "${(progressFraction * 100).toInt()}%",
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = primaryColor
                                    )
                                }
                                Spacer(Modifier.height(8.dp))
                                LinearProgressIndicator(
                                    progress = { progressFraction },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(8.dp)
                                        .clip(RoundedCornerShape(4.dp)),
                                    color = primaryColor,
                                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                                )
                            }
                        }
                    }

                    item { Spacer(Modifier.height(14.dp)) }

                    // Recipients header
                    item {
                        Text(
                            "Recipients (${recipients.size})",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 15.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                        Spacer(Modifier.height(4.dp))
                    }

                    // Recipient rows
                    items(recipients) { recipient ->
                        val normRecipient = normalizePhone(recipient.phoneNumber)
                        val response = responseMap[normRecipient]
                        Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                            RecipientRow(
                                recipient = recipient,
                                response = response,
                                urgentAlpha = urgentAlpha,
                                safeGreen = safeGreen,
                                updatesBlue = updatesBlue,
                                errorColor = errorColor
                            )
                        }
                    }
                }
            }

            // "No response" section — only shown when at least one recipient has been messaged
            // but has not yet replied (relevant during an active listening window)
            if (noResponseRecipients.isNotEmpty() && isCurrentlyListening) {
                item {
                    Spacer(Modifier.height(8.dp))
                    androidx.compose.material3.Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                        colors = androidx.compose.material3.CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                        ),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        )
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                "Waiting for reply (${noResponseRecipients.size})",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            noResponseRecipients.forEach { recipient ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.padding(vertical = 2.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .clip(androidx.compose.foundation.shape.CircleShape)
                                            .background(MaterialTheme.colorScheme.outlineVariant)
                                    )
                                    Text(
                                        recipient.name.ifBlank { recipient.phoneNumber },
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Clear responses button
            if (responses.isNotEmpty()) {
                item {
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        TextButton(
                            onClick = onClearResponses,
                            colors = ButtonDefaults.textButtonColors(contentColor = errorColor)
                        ) {
                            Text("Clear Responses", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                        }
                    }
                }
            }

            // Section 2: Logs (user-controlled collapsible per AGENTS.md)
            item {
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Logs",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (logsExpanded && logs.isNotEmpty()) {
                            TextButton(
                                onClick = onClearLogs,
                                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                            ) {
                                Text("Clear", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            }
                        }
                        TextButton(
                            onClick = { logsExpanded = !logsExpanded },
                            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Text(
                                if (logsExpanded) "Collapse" else "Show (${logs.size})",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp
                            )
                        }
                    }
                }
            }

            // Logs content - fade in/out, no height animation to avoid LazyColumn scroll jump
            item {
                AnimatedVisibility(
                    visible = logsExpanded,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    Column(modifier = Modifier.padding(bottom = 4.dp)) {
                        if (logs.isEmpty()) {
                            Text(
                                "No logs recorded yet.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                            )
                        } else {
                            logs.take(20).forEach { entry ->
                                Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 3.dp)) {
                                    DashboardLogEntry(entry = entry, timeFormat = timeFormat)
                                }
                            }
                        }
                    }
                }
            }

            // Section 3: Alert History (collapsible)
            item {
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Alert History",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (alertHistoryExpanded && pastAlerts.isNotEmpty()) {
                            TextButton(
                                onClick = onClearPastAlerts,
                                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                            ) {
                                Text("Clear", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            }
                        }
                        TextButton(
                            onClick = { alertHistoryExpanded = !alertHistoryExpanded },
                            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Text(
                                if (alertHistoryExpanded) "Collapse"
                                else "Show (${pastAlerts.size})",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp
                            )
                        }
                    }
                }
            }

            // Alert History content - fade in/out (no height animation to avoid LazyColumn jump)
            item {
                AnimatedVisibility(
                    visible = alertHistoryExpanded,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    Column(modifier = Modifier.padding(bottom = 4.dp)) {
                        if (pastAlerts.isEmpty()) {
                            Text(
                                "No alerts recorded yet.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                            )
                        } else {
                            pastAlerts.take(20).forEach { alert ->
                                Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                                    DashboardAlertCard(alert = alert, dateFormat = dateFormat)
                                }
                            }
                        }
                    }
                }
            }

            item { Spacer(Modifier.height(24.dp)) }
        }
    } // end DashboardContent

    // Render inline (as tab) or as a bottom-sheet dialog
    if (inlineMode) {
        DashboardContent()
    } else {
        Dialog(
            onDismissRequest = onDismiss,
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                modifier = Modifier
                    .width(screenWidth)
                    .fillMaxHeight(0.93f)
                    .padding(top = 28.dp),
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                color = MaterialTheme.colorScheme.background,
                tonalElevation = 0.dp
            ) {
                DashboardContent()
            }
        }
    }
}

@Composable
private fun LargeStatCard(
    label: String,
    count: Int,
    numColor: Color,
    containerColor: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = containerColor
    ) {
        Column(
            modifier = Modifier.padding(vertical = 14.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                count.toString(),
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = numColor
            )
            Text(
                label,
                fontSize = 14.sp,
                color = numColor.copy(alpha = 0.8f),
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun RecipientRow(
    recipient: Recipient,
    response: ResponseRecord?,
    urgentAlpha: Float,
    safeGreen: Color,
    updatesBlue: Color,
    errorColor: Color
) {
    val (statusText, statusColor) = when (response?.responseCode) {
        1 -> "Safe" to safeGreen
        2 -> "Wants Updates" to updatesBlue
        3 -> "URGENT" to errorColor.copy(alpha = urgentAlpha)
        else -> "No response" to Color.Gray.copy(alpha = 0.5f)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(
            0.5.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
        )
    ) {
        Row(modifier = Modifier.height(IntrinsicSize.Min)) {
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .fillMaxHeight()
                    .background(statusColor)
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        recipient.name.ifEmpty { recipient.phoneNumber },
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        recipient.phoneNumber,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.width(8.dp))
                Column(horizontalAlignment = Alignment.End) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = statusColor.copy(alpha = 0.12f)
                    ) {
                        Text(
                            statusText,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = statusColor
                        )
                    }
                    if (response != null) {
                        Spacer(Modifier.height(3.dp))
                        Text(
                            timeAgo(response.receivedAt),
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DashboardLogEntry(entry: LogEntry, timeFormat: SimpleDateFormat) {
    val label = when (entry.eventType) {
        "emergency_detected"  -> "Emergency Detected"
        "scenario_triggered"  -> "Scenario Triggered"
        "group_processed"     -> "Group Processed"
        "message_sent"        -> "Message Sent"
        "message_failed"      -> "Message Failed"
        "response_received"   -> "Response Received"
        "manual_send"         -> "Manual Send"
        else -> entry.eventType.split('_').joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    label,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    timeFormat.format(Date(entry.timestamp)),
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
            if (entry.description.isNotBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    entry.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun DashboardAlertCard(alert: PastAlert, dateFormat: SimpleDateFormat) {
    var expanded by remember { mutableStateOf(false) }

    val badge = remember(alert.source, alert.messageContent) {
        resolveAlertBadge(alert.source, alert.messageContent)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded },
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Header row: badge pill | date + chevron
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = badge.badgeBackground
                ) {
                    Text(
                        badge.label,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = badge.badgeText
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        dateFormat.format(Date(alert.triggeredAt)),
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                    Icon(
                        imageVector = if (expanded) Icons.Default.KeyboardArrowUp
                                      else Icons.Default.KeyboardArrowDown,
                        contentDescription = if (expanded) "Collapse" else "Expand",
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                }
            }
            // Message content
            Spacer(Modifier.height(6.dp))
            Text(
                alert.messageContent,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = if (expanded) Int.MAX_VALUE else 2,
                overflow = if (expanded) TextOverflow.Clip else TextOverflow.Ellipsis
            )
            if (alert.scenariosTriggered.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    "Triggered: ${alert.scenariosTriggered}",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f)
                )
            }
        }
    }
}

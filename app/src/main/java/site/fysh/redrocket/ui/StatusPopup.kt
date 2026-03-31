package site.fysh.redrocket.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import site.fysh.redrocket.model.SendState
import site.fysh.redrocket.queue.MessageStatus

/**
 * StatusPopup displays real-time messaging progress and a final completion summary.
 */
@Composable
fun StatusPopup(
    sendState: SendState,
    processed: Int,
    total: Int,
    failedCount: Int,
    elapsedTime: Long,
    isKeepTrying: Boolean,
    onKeepTryingToggle: (Boolean) -> Unit,
    statusIndicators: StatusIndicators,
    completionStats: CompletionStats? = null,
    currentMessageStatus: MessageStatus? = null,
    countdownSeconds: Int? = null,
    onCancel: () -> Unit = {},
    onDismiss: () -> Unit = {}
) {
    val isActive = statusIndicators.sending || statusIndicators.retrying
    val isDone = completionStats != null && !isActive

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(24.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {

            // ── COUNTDOWN VIEW ──────────────────────────────────────────────
            if (countdownSeconds != null && countdownSeconds > 0) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        "SENDING IN",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "$countdownSeconds",
                        fontSize = 64.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.error
                    )
                    Spacer(Modifier.height(16.dp))
                    Button(
                        onClick = onCancel,
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("CANCEL SEND", fontWeight = FontWeight.Bold)
                    }
                }
                return@Column
            }

            // ── HEADER: title + single active indicator ─────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    if (isDone) "Send Complete" else "Sending Status",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                when {
                    statusIndicators.sending ->
                        IndicatorChip("Sending", true, MaterialTheme.colorScheme.primary)
                    statusIndicators.failed ->
                        IndicatorChip("Failed", true, MaterialTheme.colorScheme.error)
                    else -> {}
                }
            }

            // Mode label — visible only while actively sending
            if (isActive) {
                val engineText = when (sendState) {
                    SendState.MULTI_THREADED -> "Multi-threaded – Quick Sending"
                    SendState.SEQUENTIAL     -> "Sequential – Degraded Service"
                    SendState.LAZARUS        -> "Lazarus – Retrying Failed Sends"
                }
                Text(
                    "Mode: $engineText",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }

            Spacer(Modifier.height(16.dp))

            // ── COMPLETION SUMMARY or IN-PROGRESS VIEW ─────────────────────
            if (isDone && completionStats != null) {
                // ── COMPLETION: 4-stat summary ──────────────────────────────
                val totalSent = completionStats.sentSuccessfully + completionStats.requiredRetries
                // Total successful sends count
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Total Sent", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                        Text("$totalSent", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary)
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    StatCard(
                        label = "Sent\nSuccessfully",
                        count = completionStats.sentSuccessfully,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f)
                    )
                    StatCard(
                        label = "Successfully\nRetried",
                        count = completionStats.requiredRetries,
                        color = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.weight(1f)
                    )
                    StatCard(
                        label = "Failed\nPermanently",
                        count = completionStats.failedPermanently,
                        color = if (completionStats.failedPermanently > 0)
                            MaterialTheme.colorScheme.error
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant,
                        highlightBackground = completionStats.failedPermanently > 0,
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    "Time: ${elapsedTime}s",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                // ── IN-PROGRESS view ────────────────────────────────────────
                val progress = if (total > 0) processed.toFloat() / total else 0f
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(10.dp)
                        .clip(RoundedCornerShape(5.dp)),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surface
                )
                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "$processed / $total sent",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    if (failedCount > 0) {
                        Text(
                            "Failed: $failedCount",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Text(
                        "Time: ${elapsedTime}s",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // ── Per-message live status ──────────────────────────────────
                if (currentMessageStatus != null && isActive) {
                    Spacer(Modifier.height(10.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.surface,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                currentMessageStatus.phoneNumber,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f)
                            )
                            val statusColor = when {
                                "Sent" in currentMessageStatus.statusText ->
                                    MaterialTheme.colorScheme.primary
                                "Failed" in currentMessageStatus.statusText ->
                                    MaterialTheme.colorScheme.error
                                "Retrying" in currentMessageStatus.statusText ->
                                    MaterialTheme.colorScheme.secondary
                                else -> MaterialTheme.colorScheme.onSurface
                            }
                            Text(
                                currentMessageStatus.statusText,
                                style = MaterialTheme.typography.labelSmall,
                                color = statusColor,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            // Keep Trying toggle — only shown while active
            if (isActive) {
                Spacer(Modifier.height(16.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Keep Trying (Lazarus Mode)",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f)
                    )
                    Switch(checked = isKeepTrying, onCheckedChange = onKeepTryingToggle)
                }
            }

            Spacer(Modifier.height(20.dp))

            // Action button — contextual
            if (isActive) {
                OutlinedButton(
                    onClick = onCancel,
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Stop Sending", fontWeight = FontWeight.Bold)
                }
            } else {
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Dismiss")
                }
            }
        }
    }
}

@Composable
private fun StatCard(
    label: String,
    count: Int,
    color: Color,
    modifier: Modifier = Modifier,
    highlightBackground: Boolean = false
) {
    Surface(
        color = if (highlightBackground) color.copy(alpha = 0.18f) else color.copy(alpha = 0.08f),
        shape = RoundedCornerShape(12.dp),
        modifier = modifier,
        border = if (highlightBackground) androidx.compose.foundation.BorderStroke(1.dp, color.copy(alpha = 0.4f)) else null
    ) {
        Column(
            modifier = Modifier.padding(vertical = 12.dp, horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "$count",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.ExtraBold,
                color = color
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
fun IndicatorChip(label: String, active: Boolean, activeColor: Color) {
    Surface(
        color = if (active) activeColor.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(4.dp),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (active) activeColor else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
        )
    ) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            style = MaterialTheme.typography.labelSmall,
            color = if (active) activeColor else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
            fontWeight = if (active) FontWeight.Bold else FontWeight.Normal
        )
    }
}

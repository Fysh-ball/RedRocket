package site.fysh.redrocket.ui

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull

@Composable
fun AbuseLockoutDialog(
    secondsRemaining: Long,
    overrideAvailable: Boolean,
    onOverride: () -> Unit,
    onDismiss: () -> Unit
) {
    var secsLeft by remember(secondsRemaining) { mutableLongStateOf(secondsRemaining) }
    LaunchedEffect(secondsRemaining) {
        secsLeft = secondsRemaining
        while (secsLeft > 0) {
            delay(1_000L)
            secsLeft = (secsLeft - 1).coerceAtLeast(0L)
        }
    }

    var showOverrideConfirm by remember { mutableStateOf(false) }

    if (showOverrideConfirm) {
        AlertDialog(
            onDismissRequest = { showOverrideConfirm = false },
            title = { Text("Use One-Time Override?", fontWeight = FontWeight.Bold) },
            text = { Text("The override button will only work once and will reset the lockout timer. Are you sure?") },
            confirmButton = {
                Button(
                    onClick = { showOverrideConfirm = false; onOverride() },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Yes, Override") }
            },
            dismissButton = {
                TextButton(onClick = { showOverrideConfirm = false }) { Text("Cancel") }
            }
        )
        return
    }

    val hours = secsLeft / 3600
    val mins = (secsLeft % 3600) / 60
    val secs = secsLeft % 60
    val timeText = if (hours > 0) "%d:%02d:%02d".format(hours, mins, secs)
                   else "%02d:%02d".format(mins, secs)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Send Blocked", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error) },
        text = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("Too many forced messages sent.", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Locked for", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.7f))
                        Text(timeText, fontSize = 40.sp, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.error)
                    }
                }
                if (overrideAvailable) {
                    Text(
                        "A one-time emergency override is available.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        textAlign = TextAlign.Center
                    )
                }
            }
        },
        confirmButton = {
            if (overrideAvailable) {
                Button(
                    onClick = { showOverrideConfirm = true },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Override", fontWeight = FontWeight.Bold) }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Dismiss") } }
    )
}

@Composable
fun ManualSendDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
    captcha: String,
    recipientCount: Int
) {
    var input by remember { mutableStateOf(TextFieldValue("")) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Security Check", fontWeight = FontWeight.Bold) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = captcha,
                    fontSize = 48.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 8.sp,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Will send $recipientCount SMS message${if (recipientCount != 1) "s" else ""}. Standard carrier charges apply.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    label = { Text("Please type in the code") },
                    placeholder = { Text("6-character code") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .pointerInput(Unit) {
                            awaitEachGesture {
                                awaitFirstDown(requireUnconsumed = false)
                                waitForUpOrCancellation()
                                val secondDown = withTimeoutOrNull(400) {
                                    awaitFirstDown(requireUnconsumed = false)
                                }
                                if (secondDown != null) {
                                    input = input.copy(selection = TextRange(0, input.text.length))
                                }
                            }
                        }
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(input.text) },
                enabled = input.text.length == 6
            ) { Text("Verify & Send") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

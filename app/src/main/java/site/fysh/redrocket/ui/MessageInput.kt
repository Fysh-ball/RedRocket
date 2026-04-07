package site.fysh.redrocket.ui

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MessageInput(
    message: String,
    onMessageChange: (String) -> Unit
) {
    var showSheet by remember { mutableStateOf(false) }
    val context = LocalContext.current

    // File picker - loads text file directly into message field, no dialog needed
    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            try {
                val inputStream = context.contentResolver.openInputStream(uri)
                val raw = inputStream?.bufferedReader()?.use { it.readText() } ?: ""
                val smsSafe = raw
                    .replace("\r\n", "\n")
                    .replace("\r", "\n")
                    .replace("\u0000", "")
                    .replace(Regex("\\n{3,}"), "\n\n")
                    .trim()
                    .take(1600)
                onMessageChange(smsSafe)
            } catch (e: Exception) {
                Toast.makeText(context, "Failed to read file", Toast.LENGTH_SHORT).show()
            }
        }
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Tappable display surface - opens the edit sheet on tap
        Surface(
            modifier = Modifier
                .weight(1f)
                .padding(vertical = 4.dp)
                .clickable { showSheet = true },
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
        ) {
            InputBoxContainer(modifier = Modifier.padding(8.dp)) {
                if (message.isBlank()) {
                    Text(
                        "Tap to enter the emergency message...",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp)
                    )
                } else {
                    Text(
                        message,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp)
                    )
                }
            }
        }

        // Upload button - load text from file directly
        Button(
            onClick = { filePicker.launch("text/plain") },
            modifier = Modifier.size(56.dp),
            shape = RoundedCornerShape(12.dp),
            contentPadding = PaddingValues(0.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
        ) {
            Icon(Icons.Default.UploadFile, contentDescription = "Upload", modifier = Modifier.size(28.dp))
        }
    }

    if (showSheet) {
        MessageEditSheet(
            initialMessage = message,
            onSave = onMessageChange,
            onDismiss = { showSheet = false }
        )
    }
}

/**
 * Bottom sheet editor - anchored to the bottom of the screen and floats above the keyboard.
 * The OutlinedTextField is always visible: imePadding() pushes the sheet content up when
 * the soft keyboard appears, matching the Discord/Telegram DM input pattern.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MessageEditSheet(
    initialMessage: String,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var textFieldValue by remember {
        mutableStateOf(TextFieldValue(initialMessage, TextRange(initialMessage.length)))
    }
    val focusRequester = remember { FocusRequester() }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    fun saveAndDismiss() {
        onSave(textFieldValue.text.replace("\u0000", "").take(1600))
        onDismiss()
    }

    ModalBottomSheet(
        onDismissRequest = { saveAndDismiss() },
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight()
                .padding(horizontal = 16.dp)
                .imePadding()
                .padding(bottom = 16.dp)
        ) {
            OutlinedTextField(
                value = textFieldValue,
                onValueChange = { newValue ->
                    val sanitized = newValue.text.replace("\u0000", "").take(1600)
                    textFieldValue = if (sanitized != newValue.text) {
                        newValue.copy(
                            text = sanitized,
                            selection = TextRange(
                                start = newValue.selection.start.coerceAtMost(sanitized.length),
                                end = newValue.selection.end.coerceAtMost(sanitized.length)
                            )
                        )
                    } else newValue
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .focusRequester(focusRequester),
                minLines = 4,
                shape = RoundedCornerShape(12.dp),
                textStyle = MaterialTheme.typography.bodyLarge,
                placeholder = { Text("Enter the emergency message to be sent...", style = MaterialTheme.typography.bodyLarge) },
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    focusedTextColor = MaterialTheme.colorScheme.onSurface,
                    unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                )
            )
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onDismiss) {
                    Text("Cancel")
                }
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = { saveAndDismiss() },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Save", fontWeight = FontWeight.Bold)
                }
            }
        }
    }

    // Request focus after the sheet has animated into position - this triggers the keyboard
    LaunchedEffect(Unit) {
        delay(200)
        focusRequester.requestFocus()
    }
}

package site.fysh.redrocket.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.ui.res.painterResource
import site.fysh.redrocket.R
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Warning
import site.fysh.redrocket.util.AlertSensitivity
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun SettingsDialog(
    uiState: MainUiState,
    onRunSimulation: (Int, Double) -> Unit,
    onToggleDebug: (Boolean) -> Unit,
    onForceSequentialChange: (Boolean) -> Unit,
    onWideSpreadToggle: (Boolean) -> Unit,
    onFailureRateChange: (Double) -> Unit,
    onThemeChange: (AppTheme) -> Unit,
    onReplyListenHoursChange: (Int) -> Unit,
    onAlertSensitivityChange: (AlertSensitivity) -> Unit,
    onSetAutoBackupFolder: (android.net.Uri) -> Unit = {},
    onExportScenarios: (android.net.Uri) -> Unit = {},
    onImportScenarios: (android.net.Uri) -> Unit = {},
    onSendTestMessage: (String) -> Unit = {},
    onReplayTutorial: () -> Unit = {},
    onDismiss: () -> Unit
) {
    var simCount by remember { mutableStateOf(TextFieldValue("10")) }
    var simFailureRatePercent by remember {
        mutableStateOf(TextFieldValue("${(uiState.failureRate * 100).toInt()}"))
    }
    var listenHoursInput by remember(uiState.replyListenHours) {
        mutableStateOf(TextFieldValue(uiState.replyListenHours.toString()))
    }
    var showTestSendDialog by remember { mutableStateOf(false) }
    var showSimConfirmDialog by remember { mutableStateOf(false) }
    var testPhoneInput by remember { mutableStateOf(TextFieldValue("")) }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri -> uri?.let { onExportScenarios(it) } }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let { onImportScenarios(it) } }

    val backupFolderLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri -> uri?.let { onSetAutoBackupFolder(it) } }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth().fillMaxHeight(0.87f),
            tonalElevation = 8.dp
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f))
                        .padding(vertical = 20.dp, horizontal = 24.dp)
                ) {
                    Text(
                        "Settings",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                val scrollState = rememberScrollState()
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .drawWithContent {
                            drawContent()
                            val viewport = size.height
                            if (scrollState.maxValue > 0) {
                                val totalContent = scrollState.maxValue.toFloat() + viewport
                                val thumbH = (viewport / totalContent) * viewport
                                val thumbY = (scrollState.value.toFloat() / scrollState.maxValue) * (viewport - thumbH)
                                drawRoundRect(
                                    color = androidx.compose.ui.graphics.Color.Gray.copy(alpha = 0.45f),
                                    topLeft = Offset(size.width - 6.dp.toPx(), thumbY),
                                    size = Size(4.dp.toPx(), thumbH),
                                    cornerRadius = CornerRadius(2.dp.toPx())
                                )
                            }
                        }
                        .verticalScroll(scrollState)
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Appearance section
                    SettingsSection(title = "Appearance", icon = Icons.Default.Palette) {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.DarkMode,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        "App Theme",
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        "Change the visual style",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            ThemeDropdown(selected = uiState.theme, onSelect = onThemeChange)
                        }
                    }

                    // Detection section
                    SettingsSection(title = "Detection", icon = Icons.Default.Notifications) {
                        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            // Sensitivity
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Default.Warning,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(Modifier.width(12.dp))
                                    Column {
                                        Text(
                                            "Alert Sensitivity",
                                            style = MaterialTheme.typography.bodyLarge,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            "High detects more, Low reduces false alarms",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                AlertSensitivityDropdown(selected = uiState.alertSensitivity, onSelect = onAlertSensitivityChange)
                            }

                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                            // Wide Spread
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.Notifications,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        "Global Keyword Detection",
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        "Monitor all app notifications",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Switch(checked = uiState.isWideSpreadEnabled, onCheckedChange = onWideSpreadToggle)
                            }
                        }
                    }

                    // Timeline section
                    SettingsSection(title = "Timeline", icon = Icons.Default.Timer) {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.Timer,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(Modifier.width(12.dp))
                                Column {
                                    Text(
                                        "Reply Listening Duration",
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        "Monitor replies after sending (1-24 hours)",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            OutlinedTextField(
                                value = listenHoursInput,
                                onValueChange = { newValue ->
                                    val numeric = newValue.text.filter { it.isDigit() }
                                    val clamped = numeric.toIntOrNull()?.coerceIn(1, 24)
                                    listenHoursInput = newValue.copy(text = clamped?.toString() ?: numeric)
                                    clamped?.let { onReplyListenHoursChange(it) }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                shape = RoundedCornerShape(12.dp),
                                singleLine = true,
                                suffix = { Text("hr") },
                                colors = OutlinedTextFieldDefaults.colors(
                                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                )
                            )
                            val currentHours = listenHoursInput.text.toIntOrNull() ?: 1
                            if (currentHours > 1) {
                                Surface(
                                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                                        Text("Extended monitoring uses more battery", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                                    }
                                }
                            }
                        }
                    }

                    // Data management section
                    SettingsSection(title = "Data", icon = Icons.Default.Storage) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(
                                "Back up or restore all scenarios and block phrases.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        exportLauncher.launch("redrocket-backup.json")
                                    },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Icon(Icons.Default.Archive, contentDescription = null, modifier = Modifier.size(20.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Export")
                                }
                                Button(
                                    onClick = {
                                        importLauncher.launch(arrayOf("application/json", "application/octet-stream", "*/*"))
                                    },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Icon(Icons.Default.FileOpen, contentDescription = null, modifier = Modifier.size(20.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Import")
                                }
                            }
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(
                                        "Import merges with existing data. Scenarios with matching IDs are updated.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    val context = LocalContext.current
                                    var folderName by remember { mutableStateOf("Default (app storage)") }
                                    LaunchedEffect(uiState.autoBackupUri) {
                                        folderName = if (uiState.autoBackupUri.isEmpty()) {
                                            "Default (app storage)"
                                        } else {
                                            withContext(Dispatchers.IO) {
                                                androidx.documentfile.provider.DocumentFile
                                                    .fromTreeUri(context, Uri.parse(uiState.autoBackupUri))
                                                    ?.name ?: uiState.autoBackupUri
                                            }
                                        }
                                    }
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            "Auto-backup folder: $folderName",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.weight(1f)
                                        )
                                        TextButton(onClick = { backupFolderLauncher.launch(null) }) {
                                            Text("Change", style = MaterialTheme.typography.bodyMedium)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Test send section
                    SettingsSection(title = "Test Send", icon = Icons.AutoMirrored.Filled.Send) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(
                                "Send a real SMS to verify your messaging pipeline is working. Standard carrier charges may apply.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Button(
                                onClick = { showTestSendDialog = true },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(20.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Send Test Message")
                            }
                        }
                    }

                    // Debug section
                    SettingsSection(title = "Debug Mode", icon = Icons.Default.BugReport, color = MaterialTheme.colorScheme.error) {
                        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.BugReport, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
                                Spacer(Modifier.width(12.dp))
                                Text("Developer Debug Tools", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                                Switch(checked = uiState.isDebugEnabled, onCheckedChange = onToggleDebug, colors = SwitchDefaults.colors(checkedThumbColor = MaterialTheme.colorScheme.error, checkedTrackColor = MaterialTheme.colorScheme.errorContainer))
                            }
                            if (uiState.isDebugEnabled) {
                                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text("Force Sequential Sending", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                                        Switch(checked = uiState.isForceSequential, onCheckedChange = onForceSequentialChange)
                                    }
                                    OutlinedTextField(
                                        value = simCount,
                                        onValueChange = { simCount = it.copy(text = it.text.filter { it.isDigit() }) },
                                        label = { Text("Simulation Batch Size") },
                                        modifier = Modifier.fillMaxWidth(),
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        shape = RoundedCornerShape(12.dp)
                                    )
                                    OutlinedTextField(
                                        value = simFailureRatePercent,
                                        onValueChange = { newValue ->
                                            val numeric = newValue.text.filter { it.isDigit() }
                                            val clamped = numeric.toIntOrNull()?.coerceIn(0, 100)?.toString() ?: ""
                                            simFailureRatePercent = newValue.copy(text = clamped)
                                            onFailureRateChange((clamped.toDoubleOrNull() ?: 0.0) / 100.0)
                                        },
                                        label = { Text("Simulated Network Failure") },
                                        suffix = { Text("%") },
                                        modifier = Modifier.fillMaxWidth(),
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        shape = RoundedCornerShape(12.dp)
                                    )
                                    Button(
                                        onClick = { showSimConfirmDialog = true },
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(12.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                                    ) {
                                        Text("Run Mock Sending Test")
                                    }
                                }
                            }
                        }
                    }

                    // Help section
                    SettingsSection(title = "Help \u0026 Resources", icon = Icons.AutoMirrored.Filled.MenuBook) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            var manualExpanded by remember { mutableStateOf(false) }
                            OutlinedButton(
                                onClick = { manualExpanded = !manualExpanded },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.AutoMirrored.Filled.MenuBook, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(12.dp))
                                Text("User Manual", modifier = Modifier.weight(1f))
                                Icon(if (manualExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown, contentDescription = if (manualExpanded) "Collapse" else "Expand")
                            }
                            if (manualExpanded) {
                                UserManualSection(onReplayTutorial = onReplayTutorial)
                            }
                        }
                    }
                }

                // Footer
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    contentAlignment = Alignment.CenterEnd
                ) {
                    Button(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.height(48.dp).width(100.dp)
                    ) {
                        Text("Done", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    if (showTestSendDialog) {
        AlertDialog(
            onDismissRequest = { showTestSendDialog = false; testPhoneInput = TextFieldValue("") },
            title = { Text("Send Test Message", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "A real SMS will be sent to this number with a [TEST] prefix. Use your own number to confirm delivery. Standard carrier charges may apply.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    val focusManager = LocalFocusManager.current
                    OutlinedTextField(
                        value = testPhoneInput,
                        onValueChange = { testPhoneInput = it },
                        label = { Text("Phone number") },
                        placeholder = { Text("+1 555 000 0000") },
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Phone,
                            imeAction = ImeAction.Send
                        ),
                        keyboardActions = KeyboardActions(onSend = {
                            val phone = testPhoneInput.text.trim()
                            if (phone.isNotBlank()) {
                                focusManager.clearFocus()
                                onSendTestMessage(phone)
                                showTestSendDialog = false
                                testPhoneInput = TextFieldValue("")
                            }
                        }),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val phone = testPhoneInput.text.trim()
                        if (phone.isNotBlank()) {
                            onSendTestMessage(phone)
                            showTestSendDialog = false
                            testPhoneInput = TextFieldValue("")
                        }
                    },
                    enabled = testPhoneInput.text.trim().isNotBlank()
                ) {
                    Text("Send")
                }
            },
            dismissButton = {
                TextButton(onClick = { showTestSendDialog = false; testPhoneInput = TextFieldValue("") }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showSimConfirmDialog) {
        val count = (simCount.text.toIntOrNull() ?: 10).coerceAtLeast(1)
        AlertDialog(
            onDismissRequest = { showSimConfirmDialog = false },
            title = { Text("Run Mock Sending Test?", fontWeight = FontWeight.Bold) },
            text = { Text("This will simulate sending $count message(s) using fake SMS. No real messages will be sent.") },
            confirmButton = {
                Button(onClick = {
                    showSimConfirmDialog = false
                    val rate = (simFailureRatePercent.text.toDoubleOrNull() ?: 0.0) / 100.0
                    onRunSimulation(count, rate)
                }) { Text("Run") }
            },
            dismissButton = {
                TextButton(onClick = { showSimConfirmDialog = false }) { Text("Cancel") }
            }
        )
    }
    }
}

@Composable
private fun SettingsSection(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: Color = MaterialTheme.colorScheme.primary,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 12.dp)) {
                Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    title.uppercase(),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.ExtraBold,
                    color = color,
                    letterSpacing = 1.sp
                )
            }
            content()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ThemeDropdown(
    selected: AppTheme,
    onSelect: (AppTheme) -> Unit
) {
    val options = listOf(
        AppTheme.SYSTEM to "Follow System",
        AppTheme.GRAY   to "Graphite Gray",
        AppTheme.LIGHT  to "Pure Light",
        AppTheme.NIGHT  to "Deep Night"
    )
    var expanded by remember { mutableStateOf(false) }
    val selectedLabel = options.firstOrNull { it.first == selected }?.second ?: "Follow System"

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = Modifier.fillMaxWidth()
    ) {
        OutlinedCard(
            onClick = {},
            modifier = Modifier.fillMaxWidth().height(52.dp).menuAnchor(MenuAnchorType.PrimaryNotEditable),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
        ) {
            Row(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(selectedLabel, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
            }
        }
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { (theme, label) ->
                DropdownMenuItem(
                    text = { Text(label, fontWeight = if (theme == selected) FontWeight.Bold else FontWeight.Normal) },
                    onClick = { onSelect(theme); expanded = false }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AlertSensitivityDropdown(
    selected: AlertSensitivity,
    onSelect: (AlertSensitivity) -> Unit
) {
    val options = listOf(
        AlertSensitivity.HIGH   to "High (All Alerts)",
        AlertSensitivity.MEDIUM to "Medium (Serious Emergencies)",
        AlertSensitivity.LOW    to "Low (Critical Only)"
    )
    var expanded by remember { mutableStateOf(false) }
    val selectedLabel = options.firstOrNull { it.first == selected }?.second ?: "Medium (Serious Emergencies)"

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = Modifier.fillMaxWidth()
    ) {
        OutlinedCard(
            onClick = {},
            modifier = Modifier.fillMaxWidth().height(52.dp).menuAnchor(MenuAnchorType.PrimaryNotEditable),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
        ) {
            Row(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(selectedLabel, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
            }
        }
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { (sensitivity, label) ->
                DropdownMenuItem(
                    text = { Text(label, fontWeight = if (sensitivity == selected) FontWeight.Bold else FontWeight.Normal) },
                    onClick = { onSelect(sensitivity); expanded = false }
                )
            }
        }
    }
}

@Composable
private fun UserManualSection(onReplayTutorial: () -> Unit) {
    val context = LocalContext.current
    fun openUrl(url: String) {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }
    val sections = listOf(
        "Quick Start" to "1. Create a scenario and add your activation keywords.\n2. Add the contacts you want to reach and write your message.\n3. That's it. Red Rocket runs in the background and listens for alerts.\n4. When a match hits, your message goes out to everyone on the list.",
        "Scenarios" to "A scenario is your plan for a specific situation. Each one has its own keywords, contacts, and message. You can have as many as you want: one for family, one for coworkers, one for your building. They each run independently.",
        "Alert Filters" to "Activation Keywords are the words Red Rocket looks for in incoming alerts. Hit the ⚡ button to pick from common disaster presets, or type in your own. Block Phrases are words or phrases (in any language) that mark an alert as a test or false alarm - matching ones will never trigger sending.",
        "Response Dashboard" to "Once messages go out, head to the Dashboard tab to see who's replied. Contacts can text back 1 (Safe), 2 (Need Updates), or 3 (Urgent). The list updates as replies come in.",
        "Listening for Replies" to "After a send, Red Rocket waits for replies for however long you've set (1 to 24 hours). You'll see the timer at the top of the Dashboard. Hit Stop if you want to end it early.",
        "FAQ" to "Q: Are you gonna steal my data?\nA: All data is stored locally. I do not want your data.\n\nQ: Will my messages be automatically sent?\nA: When a filter matches an alert, the app will first assess if it's a false alarm and if not, it'll send the message.\n\nQ: What's Global Keyword Detection?\nA: While it's not needed or recommended, you can enable the app to listen to every other notification for keywords.\n\nQ: Will this work when My phone is off?\nA: It should work even when your phone's locked, but to be safe check the app dashboard to see if it's listening for responses."
    )

    var expandedSection by remember { mutableStateOf<String?>(null) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        sections.forEach { (title, content) ->
            val isExpanded = expandedSection == title
            Surface(
                onClick = { expandedSection = if (isExpanded) null else title },
                shape = RoundedCornerShape(12.dp),
                color = if (isExpanded) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)
                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                border = BorderStroke(1.dp, if (isExpanded) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f) else Color.Transparent),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            title,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Icon(
                            if (isExpanded) Icons.Default.KeyboardArrowUp
                            else Icons.Default.KeyboardArrowDown,
                            contentDescription = if (isExpanded) "Collapse" else "Expand",
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (isExpanded) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            content,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 18.sp
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(4.dp))

        Button(
            onClick = onReplayTutorial,
            modifier = Modifier.fillMaxWidth().height(48.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer, contentColor = MaterialTheme.colorScheme.onSecondaryContainer)
        ) {
            Icon(Icons.Default.Info, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(12.dp))
            Text("Re-run Setup Tutorial", fontWeight = FontWeight.Bold)
        }

        HorizontalDivider(
            modifier = Modifier.padding(vertical = 4.dp),
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
        )

        // GitHub — check for updates (sideloaded builds have no auto-update)
        OutlinedButton(
            onClick = { openUrl("https://github.com/Fysh-ball/RedRocket") },
            modifier = Modifier.fillMaxWidth().height(48.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(painterResource(R.drawable.ic_github), contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(12.dp))
            Text("Check for Updates on GitHub", modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
        }

        // Ko-fi — support the developer
        OutlinedButton(
            onClick = { openUrl("https://ko-fi.com/fysh_yum") },
            modifier = Modifier.fillMaxWidth().height(48.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(painterResource(R.drawable.ic_kofi), contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(12.dp))
            Text("Wanna Buy Me a Cup of Rice?", modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
        }
    }
}       

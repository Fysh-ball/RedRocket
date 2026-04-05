package site.fysh.redrocket.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import site.fysh.redrocket.model.BlockPhrase

/** Preset trigger word categories with optional emoji icon. */
data class TriggerPreset(
    val name: String,
    val keywords: List<String>,
    val icon: String = ""
)

private val TRIGGER_PRESETS = listOf(
    TriggerPreset("Tornado", listOf(
        "tornado warning", "tornado emergency", "confirmed tornado",
        "tornado detected", "radar indicated tornado"
    ), "🌪️"),
    TriggerPreset("Volcano", listOf(
        "volcanic eruption", "volcano warning", "lava flow",
        "volcanic ash", "eruption warning"
    ), "🌋"),
    TriggerPreset("Nuclear", listOf(
        "nuclear", "missile", "ballistic missile", "nuclear threat",
        "fallout", "radiation warning"
    ), "☢️"),
    TriggerPreset("Tsunami", listOf(
        "tsunami", "tsunami warning", "tidal wave", "coastal flood",
        "ocean surge"
    ), "🌊"),
    TriggerPreset("Flood", listOf(
        "flood warning", "flash flood", "flood emergency",
        "flooding", "rising water"
    ), "💧"),
    TriggerPreset("Hurricane", listOf(
        "hurricane warning", "hurricane emergency", "tropical storm",
        "cyclone", "storm surge"
    ), "🌀"),
    TriggerPreset("Ballistic Missile", listOf(
        "ballistic missile", "missile threat", "missile warning",
        "missile inbound", "impact warning"
    ), "🚀"),
    TriggerPreset("Wildfire", listOf(
        "wildfire", "fire warning", "evacuate fire",
        "forest fire", "brush fire"
    ), "🔥"),
    TriggerPreset("Earthquake", listOf(
        "earthquake", "earthquake warning", "seismic activity",
        "tremor", "aftershock"
    ), "⚡"),
    TriggerPreset("Chemical / Hazmat", listOf(
        "hazmat", "chemical spill", "toxic release",
        "biohazard", "chemical emergency"
    ), "⚠️"),
    TriggerPreset("General Emergency", listOf(
        "emergency", "alert", "disaster", "evacuation",
        "shelter in place", "take cover"
    ), "🆘")
)

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun TriggerInput(
    keywordsString: String,
    onKeywordsChange: (String) -> Unit,
    blockPhrases: List<BlockPhrase> = emptyList(),
    onAddBlockPhrase: (String) -> Unit = {},
    onDeleteBlockPhrase: (BlockPhrase) -> Unit = {},
    onSheetDismissed: () -> Unit = {}
) {
    val keywords = remember(keywordsString) {
        keywordsString.split(",").filter { it.isNotBlank() }.map { it.trim() }
    }
    var showPresetPicker by remember { mutableStateOf(false) }
    var showKeywordSheet by remember { mutableStateOf(false) }
    var showBlockPhraseSheet by remember { mutableStateOf(false) }
    var showBlockPhraseInfo by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {

        // ── Activation Keywords sub-section ───────────────────────────────────
        SubSectionLabel("Activation Keywords")
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Surface(
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = 4.dp)
                    .clickable { showKeywordSheet = true },
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                InputBoxContainer(modifier = Modifier.padding(8.dp)) {
                    if (keywords.isEmpty()) {
                        Text(
                            "Tap to add trigger keywords...",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp)
                        )
                    } else {
                        FlowRow(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 6.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            keywords.forEach { keyword ->
                                KeywordChip(keyword) {
                                    val newList = keywords.toMutableList()
                                    newList.remove(keyword)
                                    onKeywordsChange(newList.joinToString(","))
                                }
                            }
                        }
                    }
                }
            }

            Button(
                onClick = { showPresetPicker = true },
                modifier = Modifier.size(56.dp),
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(0.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = "Add trigger keywords",
                    modifier = Modifier.size(28.dp)
                )
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

        // ── Block Phrases sub-section ──────────────────────────────────────────
        SubSectionLabel("Block Phrases")
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Surface(
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = 4.dp)
                    .clickable { showBlockPhraseSheet = true },
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                InputBoxContainer(modifier = Modifier.padding(8.dp)) {
                    if (blockPhrases.isEmpty()) {
                        Text(
                            "Tap to add block phrases...",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp)
                        )
                    } else {
                        FlowRow(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 6.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            blockPhrases.forEach { bp ->
                                BlockPhraseChip(bp.phrase) { onDeleteBlockPhrase(bp) }
                            }
                        }
                    }
                }
            }

            Button(
                onClick = { showBlockPhraseInfo = true },
                modifier = Modifier.size(56.dp),
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(0.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
            ) {
                Icon(
                    Icons.Default.Info,
                    contentDescription = "What are block phrases?",
                    modifier = Modifier.size(26.dp)
                )
            }
        }
    }

    // Keyword add sheet
    if (showKeywordSheet) {
        KeywordAddSheet(
            title = "Add Keyword",
            placeholder = "e.g. earthquake warning",
            onAdd = { word ->
                if (word.isNotBlank()) {
                    val combined = (keywords + listOf(word.trim())).distinct().joinToString(",")
                    onKeywordsChange(combined)
                }
            },
            onDismiss = {
                showKeywordSheet = false
                onSheetDismissed()
            }
        )
    }

    // Block phrase add sheet
    if (showBlockPhraseSheet) {
        KeywordAddSheet(
            title = "Add Block Phrase",
            placeholder = "e.g. this is a test",
            onAdd = { phrase ->
                if (phrase.isNotBlank()) onAddBlockPhrase(phrase.trim())
            },
            onDismiss = { showBlockPhraseSheet = false }
        )
    }

    // Block phrase info dialog
    if (showBlockPhraseInfo) {
        AlertDialog(
            onDismissRequest = { showBlockPhraseInfo = false },
            icon = { Icon(Icons.Default.Info, contentDescription = null) },
            title = { Text("Block Phrases", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "Block phrases are words or phrases that tell Red Rocket an alert is a test or false alarm.\n\nIf an incoming alert contains any of your block phrases, it will be ignored — even if it also matches your activation keywords.\n\nYou can add phrases in any language. Tap a chip to remove it.",
                    style = MaterialTheme.typography.bodyMedium,
                    lineHeight = 20.sp
                )
            },
            confirmButton = {
                Button(onClick = { showBlockPhraseInfo = false }, shape = RoundedCornerShape(12.dp)) {
                    Text("Got it")
                }
            },
            shape = RoundedCornerShape(16.dp)
        )
    }

    // Preset picker dialog
    if (showPresetPicker) {
        PresetPickerDialog(
            currentKeywords = keywords,
            onAddPreset = { preset ->
                val combined = (keywords + preset.keywords).distinct().joinToString(",")
                onKeywordsChange(combined)
            },
            onDismiss = {
                showPresetPicker = false
                scope.launch {
                    delay(600)
                    onSheetDismissed()
                }
            }
        )
    }
}

@Composable
private fun SubSectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        letterSpacing = 0.5.sp
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun KeywordAddSheet(
    title: String,
    placeholder: String,
    onAdd: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var textFieldValue by remember { mutableStateOf(TextFieldValue("", TextRange(0))) }
    val focusRequester = remember { FocusRequester() }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .imePadding()
                .padding(bottom = 16.dp)
        ) {
            Text(
                title,
                fontWeight = FontWeight.Bold,
                fontSize = 17.sp,
                modifier = Modifier.padding(bottom = 12.dp)
            )
            OutlinedTextField(
                value = textFieldValue,
                onValueChange = { newValue ->
                    if (newValue.text.endsWith(",")) {
                        val word = newValue.text.removeSuffix(",").trim()
                        if (word.isNotBlank()) {
                            onAdd(word)
                        }
                        textFieldValue = TextFieldValue("", TextRange(0))
                    } else {
                        textFieldValue = newValue
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester),
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                placeholder = { Text(placeholder) },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = {
                    val word = textFieldValue.text.trim()
                    if (word.isNotBlank()) {
                        onAdd(word)
                        textFieldValue = TextFieldValue("", TextRange(0))
                    }
                }),
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                    focusedContainerColor = MaterialTheme.colorScheme.surface
                )
            )
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onDismiss) { Text("Cancel") }
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = {
                        val word = textFieldValue.text.trim()
                        if (word.isNotBlank()) onAdd(word)
                        onDismiss()
                    },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Done", fontWeight = FontWeight.Bold)
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        delay(200)
        focusRequester.requestFocus()
    }
}

@Composable
private fun PresetPickerDialog(
    currentKeywords: List<String>,
    onAddPreset: (TriggerPreset) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Trigger Presets", fontWeight = FontWeight.Bold) },
        text = {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.heightIn(max = 400.dp)
            ) {
                items(TRIGGER_PRESETS) { preset ->
                    val alreadyAdded = preset.keywords.all { it in currentKeywords }
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        color = if (alreadyAdded) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                else MaterialTheme.colorScheme.surfaceVariant,
                        onClick = { if (!alreadyAdded) onAddPreset(preset) }
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            if (preset.icon.isNotEmpty()) {
                                Text(preset.icon, fontSize = 22.sp)
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    preset.name,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    preset.keywords.joinToString(", "),
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2
                                )
                            }
                            if (alreadyAdded) {
                                Text(
                                    "Added",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss, shape = RoundedCornerShape(12.dp)) {
                Text("Done")
            }
        },
        shape = RoundedCornerShape(16.dp)
    )
}

@Composable
fun KeywordChip(keyword: String, onRemove: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.height(32.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 12.dp)
        ) {
            Text(
                text = keyword,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Spacer(Modifier.width(6.dp))
            Icon(
                Icons.Default.Close,
                contentDescription = "Remove",
                modifier = Modifier
                    .size(16.dp)
                    .clip(CircleShape)
                    .clickable { onRemove() }
                    .padding(2.dp),
                tint = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}

@Composable
private fun BlockPhraseChip(phrase: String, onRemove: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.height(32.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 12.dp)
        ) {
            Text(
                text = phrase,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
            Spacer(Modifier.width(6.dp))
            Icon(
                Icons.Default.Close,
                contentDescription = "Remove",
                modifier = Modifier
                    .size(16.dp)
                    .clip(CircleShape)
                    .clickable { onRemove() }
                    .padding(2.dp),
                tint = MaterialTheme.colorScheme.onErrorContainer
            )
        }
    }
}

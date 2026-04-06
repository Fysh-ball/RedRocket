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
import androidx.compose.material.icons.filled.ArrowDropDown
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

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun TriggerInput(
    keywordsString: String,
    onKeywordsChange: (String) -> Unit,
    blockPhrases: List<BlockPhrase> = emptyList(),
    onAddBlockPhrase: (String) -> Unit = {},
    onDeleteBlockPhrase: (BlockPhrase) -> Unit = {},
    onSheetDismissed: () -> Unit = {},
    userRegion: String = "",
    detectedRegion: String = "US",
    onSetRegion: (String) -> Unit = {}
) {
    val keywords = remember(keywordsString) {
        keywordsString.split(",").filter { it.isNotBlank() }.map { it.trim() }
    }
    val currentPhraseTexts = remember(blockPhrases) { blockPhrases.map { it.phrase } }
    val effectiveRegion = userRegion.ifEmpty { detectedRegion }
    val regionPreset = remember(effectiveRegion) { regionPresetFor(effectiveRegion) }
    var showPresetPicker by remember { mutableStateOf(false) }
    var showKeywordSheet by remember { mutableStateOf(false) }
    var showBlockPhraseSheet by remember { mutableStateOf(false) }
    var showBlockPhrasePresetPicker by remember { mutableStateOf(false) }
    var showRegionPicker by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {

        // Region selector
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            onClick = { showRegionPicker = true }
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(regionPreset.flag, fontSize = 16.sp)
                Spacer(Modifier.width(8.dp))
                Text(
                    regionPreset.displayName,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                if (userRegion.isEmpty()) {
                    Text(
                        "auto",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                }
                Text(
                    regionPreset.dialCode,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.padding(end = 4.dp)
                )
                Icon(
                    Icons.Default.ArrowDropDown,
                    contentDescription = "Change region",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

        // Activation Keywords sub-section
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

        // Block Phrases sub-section
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
                onClick = { showBlockPhrasePresetPicker = true },
                modifier = Modifier.size(56.dp),
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(0.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = "Add block phrase presets",
                    modifier = Modifier.size(28.dp)
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

    // Block phrase preset picker
    if (showBlockPhrasePresetPicker) {
        BlockPhrasePresetPicker(
            regionCode = effectiveRegion,
            currentPhrases = currentPhraseTexts,
            onAddPhrase = { onAddBlockPhrase(it) },
            onRemovePhrase = { phrase -> blockPhrases.find { it.phrase == phrase }?.let { onDeleteBlockPhrase(it) } },
            onDismiss = { showBlockPhrasePresetPicker = false }
        )
    }

    // Region picker dialog
    if (showRegionPicker) {
        RegionPickerDialog(
            currentCode = effectiveRegion,
            detectedCode = detectedRegion,
            onSelect = { region ->
                onSetRegion(region.countryCode)
                showRegionPicker = false
            },
            onDismiss = { showRegionPicker = false }
        )
    }

    // Keyword preset picker dialog
    if (showPresetPicker) {
        PresetPickerDialog(
            currentKeywords = keywords,
            regionCode = effectiveRegion,
            onAddPreset = { preset ->
                val combined = (keywords + preset.keywords).distinct().joinToString(",")
                onKeywordsChange(combined)
            },
            onRemovePreset = { preset ->
                val combined = keywords.filter { it !in preset.keywords }.joinToString(",")
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
                    if (newValue.text.contains(",")) {
                        val parts = newValue.text.split(",").map { it.trim() }.filter { it.isNotBlank() }
                        val endsWithComma = newValue.text.endsWith(",")
                        if (parts.isEmpty()) {
                            textFieldValue = TextFieldValue("", TextRange(0))
                        } else {
                            val toAdd = if (endsWithComma) parts else parts.dropLast(1)
                            toAdd.forEach { onAdd(it) }
                            val remaining = if (endsWithComma) "" else parts.last()
                            textFieldValue = TextFieldValue(remaining, TextRange(remaining.length))
                        }
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
    regionCode: String,
    onAddPreset: (TriggerPreset) -> Unit,
    onRemovePreset: (TriggerPreset) -> Unit,
    onDismiss: () -> Unit
) {
    val presets = remember(regionCode) { localizedTriggerPresets(regionCode) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Trigger Presets", fontWeight = FontWeight.Bold) },
        text = {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.heightIn(max = 400.dp)
            ) {
                items(presets, key = { it.name }) { preset ->
                    val alreadyAdded = preset.keywords.all { it in currentKeywords }
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        color = if (alreadyAdded) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                else MaterialTheme.colorScheme.surfaceVariant,
                        onClick = { if (alreadyAdded) onRemovePreset(preset) else onAddPreset(preset) }
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
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "Remove ${preset.name}",
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(18.dp)
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
private fun RegionPickerDialog(
    currentCode: String,
    detectedCode: String,
    onSelect: (RegionPreset) -> Unit,
    onDismiss: () -> Unit
) {
    var query by remember { mutableStateOf("") }
    val filteredRegions = remember(query) {
        if (query.isBlank()) REGION_PRESETS
        else REGION_PRESETS.filter {
            it.displayName.contains(query, ignoreCase = true) ||
            it.countryCode.contains(query, ignoreCase = true)
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Select Region", fontWeight = FontWeight.Bold) },
        text = {
            Column {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Search regions...") },
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                        focusedContainerColor = MaterialTheme.colorScheme.surface
                    )
                )
                Spacer(Modifier.height(8.dp))
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.heightIn(max = 360.dp)
                ) {
                    items(filteredRegions, key = { it.countryCode }) { region ->
                        val isSelected = region.countryCode == currentCode
                        val isDetected = region.countryCode == detectedCode
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp),
                            color = if (isSelected)
                                MaterialTheme.colorScheme.primaryContainer
                            else
                                MaterialTheme.colorScheme.surfaceVariant,
                            onClick = { onSelect(region) }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Text(region.flag, fontSize = 18.sp)
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        region.displayName,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        fontSize = 13.sp,
                                        color = if (isSelected)
                                            MaterialTheme.colorScheme.onPrimaryContainer
                                        else
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    if (isDetected) {
                                        Text(
                                            "Auto-detected",
                                            fontSize = 10.sp,
                                            color = MaterialTheme.colorScheme.primary,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }
                                Text(
                                    region.dialCode,
                                    fontSize = 12.sp,
                                    color = if (isSelected)
                                        MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                                    else
                                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
        shape = RoundedCornerShape(16.dp)
    )
}

@Composable
fun KeywordChip(keyword: String, onRemove: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        shape = RoundedCornerShape(20.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 12.dp, end = 6.dp, top = 8.dp, bottom = 8.dp)
        ) {
            Text(
                text = keyword,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.weight(1f, fill = false)
            )
            Spacer(Modifier.width(4.dp))
            Icon(
                Icons.Default.Close,
                contentDescription = "Remove $keyword",
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .clickable { onRemove() }
                    .padding(8.dp),
                tint = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}

@Composable
private fun BlockPhraseChip(phrase: String, onRemove: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        shape = RoundedCornerShape(20.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 12.dp, end = 6.dp, top = 8.dp, bottom = 8.dp)
        ) {
            Text(
                text = phrase,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.weight(1f, fill = false)
            )
            Spacer(Modifier.width(4.dp))
            Icon(
                Icons.Default.Close,
                contentDescription = "Remove $phrase",
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .clickable { onRemove() }
                    .padding(8.dp),
                tint = MaterialTheme.colorScheme.onErrorContainer
            )
        }
    }
}

package site.fysh.redrocket.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Bottom sheet showing block phrase presets for the user's region.
 *
 * - Each phrase row toggles: tap to add if not present, tap to remove if already added.
 * - "Add All" adds every phrase not yet in the list. "Remove All" appears when all are added.
 *
 * @param regionCode      Effective ISO 3166-1 alpha-2 code (already resolved by caller).
 * @param currentPhrases  Phrase strings already in the user's block list.
 * @param onAddPhrase     Called with the phrase string when the user adds it.
 * @param onRemovePhrase  Called with the phrase string when the user removes it.
 * @param onDismiss       Called when the sheet is dismissed.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BlockPhrasePresetPicker(
    regionCode: String,
    currentPhrases: List<String>,
    onAddPhrase: (String) -> Unit,
    onRemovePhrase: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val selectedRegion = remember(regionCode) { regionPresetFor(regionCode) }
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
                .navigationBarsPadding()
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "Block Phrase Presets",
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp
                )
                Button(
                    onClick = onDismiss,
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text("Done", fontWeight = FontWeight.Bold)
                }
            }

            Spacer(Modifier.height(12.dp))

            // Add All / Remove All button
            val notYetAdded = selectedRegion.phrases.filter { it !in currentPhrases }
            if (notYetAdded.isNotEmpty()) {
                OutlinedButton(
                    onClick = { notYetAdded.forEach { onAddPhrase(it) } },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Add All (${notYetAdded.size})", fontWeight = FontWeight.SemiBold)
                }
            } else {
                OutlinedButton(
                    onClick = { selectedRegion.phrases.forEach { onRemovePhrase(it) } },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error)
                ) {
                    Text(
                        "Remove All",
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            // Phrase list
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 360.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(selectedRegion.phrases) { phrase ->
                    val added = phrase in currentPhrases
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        color = if (added)
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                        else
                            MaterialTheme.colorScheme.surfaceVariant,
                        onClick = { if (added) onRemovePhrase(phrase) else onAddPhrase(phrase) }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                phrase,
                                modifier = Modifier.weight(1f),
                                fontSize = 13.sp,
                                color = if (added)
                                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                                else
                                    MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (added) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "Remove $phrase",
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(18.dp)
                                )
                            } else {
                                Icon(
                                    Icons.Default.Add,
                                    contentDescription = "Add",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }

}

package site.fysh.redrocket.ui

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import site.fysh.redrocket.model.Scenario
import androidx.compose.foundation.lazy.rememberLazyListState
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

/**
 * ScenarioDropdown handles scenario selection, management, and editing.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ScenarioDropdown(
    scenarios: List<Scenario>,
    selectedScenario: Scenario?,
    onScenarioSelected: (Scenario) -> Unit,
    onAddScenario: () -> Unit,
    onDeleteScenarios: (List<String>) -> Unit,
    onToggleFavorite: (String) -> Unit,
    onRenameScenario: (String, String) -> Unit,
    onScenariosReordered: (List<Scenario>) -> Unit,
    onDropdownOpened: () -> Unit = {},
    onDropdownClosed: () -> Unit = {},
    onPositioned: ((LayoutCoordinates) -> Unit)? = null
) {
    var showDialog by remember { mutableStateOf(false) }
    var multiSelectMode by remember { mutableStateOf(false) }
    val selectedIds = remember { mutableStateListOf<String>() }
    var renamingScenarioId by remember { mutableStateOf<String?>(null) }
    var newName by remember { mutableStateOf("") }

    // Surface for the dropdown header
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .onGloballyPositioned { onPositioned?.invoke(it) }
            .combinedClickable(
                onClick = {
                    multiSelectMode = false
                    showDialog = true
                    onDropdownOpened()
                },
                onLongClick = {
                    selectedScenario?.let {
                        renamingScenarioId = it.id
                        newName = it.name
                    }
                }
            )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = selectedScenario?.name ?: "Select Scenario",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
            Icon(Icons.Default.ArrowDropDown, contentDescription = null, modifier = Modifier.size(24.dp))
        }
    }

    if (showDialog) {
        // Use a stable mutable list that is NOT keyed on `scenarios` to prevent reset during drag.
        val localScenarios = remember { mutableStateListOf<Scenario>() }
        var isReordering by remember { mutableStateOf(false) }

        // Sync from parent only when NOT actively dragging
        LaunchedEffect(scenarios) {
            if (!isReordering) {
                localScenarios.clear()
                localScenarios.addAll(scenarios)
            }
        }

        val lazyListState = rememberLazyListState()
        val reorderState = rememberReorderableLazyListState(lazyListState) { from, to ->
            isReordering = true
            val item = localScenarios.removeAt(from.index)
            localScenarios.add(to.index, item)
        }

        Dialog(onDismissRequest = {
            showDialog = false
            multiSelectMode = false
            selectedIds.clear()
            onDropdownClosed()
        }) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 500.dp),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (multiSelectMode) "Select to Delete" else "Select Scenario",
                            style = MaterialTheme.typography.titleLarge
                        )
                        Row {
                            if (multiSelectMode) {
                                TextButton(onClick = {
                                    val nonFavorites = localScenarios.filter { !it.isFavorite }
                                    if (selectedIds.size == nonFavorites.size) selectedIds.clear()
                                    else {
                                        selectedIds.clear()
                                        selectedIds.addAll(nonFavorites.map { it.id })
                                    }
                                }) {
                                    Text("All")
                                }
                                IconButton(onClick = {
                                    onDeleteScenarios(selectedIds.toList())
                                    multiSelectMode = false
                                    selectedIds.clear()
                                }) {
                                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                                }
                                IconButton(onClick = { multiSelectMode = false }) {
                                    Icon(Icons.Default.Close, contentDescription = "Exit")
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(8.dp))

                    LazyColumn(
                        state = lazyListState,
                        modifier = Modifier.weight(1f, fill = false)
                    ) {
                        items(localScenarios, key = { it.id }) { scenario ->
                            ReorderableItem(reorderState, key = scenario.id) { isDragging ->
                                val elevation by animateDpAsState(
                                    targetValue = if (isDragging) 8.dp else 0.dp,
                                    label = "elevation"
                                )
                                val scale by animateFloatAsState(
                                    targetValue = if (isDragging) 1.03f else 1f,
                                    label = "scale"
                                )

                                val showCheckbox = multiSelectMode && !scenario.isFavorite

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .scale(scale)
                                        .shadow(elevation, RoundedCornerShape(8.dp))
                                        .combinedClickable(
                                            onClick = {
                                                if (multiSelectMode) {
                                                    if (!scenario.isFavorite) {
                                                        if (selectedIds.contains(scenario.id)) selectedIds.remove(scenario.id)
                                                        else selectedIds.add(scenario.id)
                                                    }
                                                } else {
                                                    onScenarioSelected(scenario)
                                                    showDialog = false
                                                    onDropdownClosed()
                                                }
                                            },
                                            onLongClick = {
                                                if (!scenario.isFavorite) {
                                                    multiSelectMode = true
                                                    if (!selectedIds.contains(scenario.id)) selectedIds.add(scenario.id)
                                                }
                                            }
                                        )
                                        .padding(vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Drag handle always visible (not hidden behind long-press)
                                    Icon(
                                        imageVector = Icons.Default.DragHandle,
                                        contentDescription = "Drag to reorder",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier
                                            .size(24.dp)
                                            .draggableHandle(
                                                onDragStopped = {
                                                    onScenariosReordered(localScenarios.toList())
                                                    isReordering = false
                                                }
                                            )
                                    )

                                    if (showCheckbox) {
                                        Checkbox(
                                            checked = selectedIds.contains(scenario.id),
                                            onCheckedChange = { checked ->
                                                if (checked) selectedIds.add(scenario.id)
                                                else selectedIds.remove(scenario.id)
                                            }
                                        )
                                    }

                                    Column(
                                        modifier = Modifier.weight(1f).padding(start = 8.dp)
                                    ) {
                                        Text(
                                            scenario.name,
                                            style = MaterialTheme.typography.bodyLarge,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        if (!multiSelectMode) {
                                            val count = scenario.allRecipients().size
                                            if (count > 0) {
                                                Spacer(Modifier.height(2.dp))
                                                Surface(
                                                    shape = RoundedCornerShape(50),
                                                    color = MaterialTheme.colorScheme.primaryContainer
                                                ) {
                                                    Text(
                                                        text = "$count ${if (count == 1) "recipient" else "recipients"}",
                                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                                        fontSize = 14.sp,
                                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                                        fontWeight = FontWeight.Medium
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    if (!multiSelectMode && scenario.isLocked) {
                                        Icon(
                                            imageVector = Icons.Default.Lock,
                                            contentDescription = "Locked",
                                            tint = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.size(20.dp).padding(end = 4.dp)
                                        )
                                    }

                                    if (!multiSelectMode) {
                                        IconButton(onClick = { onToggleFavorite(scenario.id) }) {
                                            Icon(
                                                imageVector = if (scenario.isFavorite) Icons.Default.Star else Icons.Outlined.StarBorder,
                                                contentDescription = "Favorite",
                                                tint = if (scenario.isFavorite) MaterialTheme.colorScheme.tertiary
                                                       else MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                    TextButton(
                        onClick = { onAddScenario() },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Add New Scenario")
                    }
                }
            }
        }
    }

    // Rename dialog triggered from main surface long-press
    if (renamingScenarioId != null) {
        AlertDialog(
            onDismissRequest = { renamingScenarioId = null },
            title = { Text("Rename Scenario") },
            text = {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (newName.isNotBlank()) {
                        onRenameScenario(renamingScenarioId!!, newName)
                    }
                    renamingScenarioId = null
                }) {
                    Text("Rename")
                }
            },
            dismissButton = {
                TextButton(onClick = { renamingScenarioId = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

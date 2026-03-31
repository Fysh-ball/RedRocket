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
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import site.fysh.redrocket.model.Group
import site.fysh.redrocket.model.Recipient
import site.fysh.redrocket.model.Scenario
import org.burnoutcrew.reorderable.ReorderableItem
import org.burnoutcrew.reorderable.detectReorder
import org.burnoutcrew.reorderable.rememberReorderableLazyListState
import org.burnoutcrew.reorderable.reorderable

/**
 * GroupsSection handles group selection, management, and editing.
 * Mirrors ScenarioDropdown exactly: drag reorder, star/favorite protection,
 * long-press multi-select delete, rename via long-press on header.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun GroupsSection(
    scenario: Scenario,
    onAddGroup: () -> Unit,
    onDeleteGroups: (List<String>) -> Unit,
    onRenameGroup: (groupId: String, newName: String) -> Unit,
    onAddRecipientsToGroup: (groupId: String, recipients: List<Recipient>) -> Unit,
    onRemoveRecipientFromGroup: (groupId: String, recipient: Recipient) -> Unit,
    onGroupMessageChange: (groupId: String, message: String) -> Unit,
    onToggleGroupFavorite: (groupId: String) -> Unit,
    onGroupsReordered: (List<Group>) -> Unit,
    onGroupHeaderPositioned: ((LayoutCoordinates) -> Unit)? = null,
    onMessageInputPositioned: ((LayoutCoordinates) -> Unit)? = null
) {
    val groups = scenario.groups

    // Selected group ID — defaults to first group
    var selectedGroupId by remember(scenario.id) {
        mutableStateOf(groups.firstOrNull()?.id ?: "")
    }

    // Auto-select newly added group
    var prevGroupIds by remember(scenario.id) { mutableStateOf(groups.map { it.id }.toSet()) }
    LaunchedEffect(groups) {
        val currentIds = groups.map { it.id }.toSet()
        val newIds = currentIds - prevGroupIds
        if (newIds.isNotEmpty()) {
            selectedGroupId = newIds.first()
        }
        prevGroupIds = currentIds
    }

    // If selected group was deleted, fall back to first
    val selectedGroup: Group? = groups.find { it.id == selectedGroupId } ?: groups.firstOrNull()
    if (selectedGroup != null && selectedGroupId != selectedGroup.id) {
        selectedGroupId = selectedGroup.id
    }

    var showDialog by remember { mutableStateOf(false) }
    var multiSelectMode by remember { mutableStateOf(false) }
    val selectedIds = remember { mutableStateListOf<String>() }
    var renamingGroupId by remember { mutableStateOf<String?>(null) }
    var newName by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {

        // ── Group selector header ────────────────────────────────────────
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .onGloballyPositioned { onGroupHeaderPositioned?.invoke(it) }
                .combinedClickable(
                    onClick = {
                        multiSelectMode = false
                        showDialog = true
                    },
                    onLongClick = {
                        selectedGroup?.let {
                            renamingGroupId = it.id
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
                    text = selectedGroup?.name ?: "—",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
                Icon(Icons.Default.ArrowDropDown, contentDescription = null, modifier = Modifier.size(24.dp))
            }
        }

        // ── Selected group content ───────────────────────────────────────
        if (selectedGroup != null) {
            key(selectedGroup.id) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "Recipients",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    RecipientsInput(
                        recipients = selectedGroup.recipients,
                        onAddRecipients = { onAddRecipientsToGroup(selectedGroup.id, it) },
                        onRemoveRecipient = { onRemoveRecipientFromGroup(selectedGroup.id, it) }
                    )
                    Column(
                        modifier = Modifier.onGloballyPositioned { onMessageInputPositioned?.invoke(it) },
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            "Message",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        MessageInput(
                            message = selectedGroup.message,
                            onMessageChange = { onGroupMessageChange(selectedGroup.id, it) }
                        )
                    }
                }
            }
        }
    }

    // ── Group picker dialog — matches ScenarioDropdown dialog exactly ────
    if (showDialog) {
        // Pre-populated with current groups so the first frame is never blank
        val localGroups = remember { mutableStateListOf<Group>().also { it.addAll(groups) } }
        var isReordering by remember { mutableStateOf(false) }

        // Sync from parent only when NOT actively dragging
        LaunchedEffect(groups) {
            if (!isReordering) {
                localGroups.clear()
                localGroups.addAll(groups)
            }
        }

        val reorderState = rememberReorderableLazyListState(
            onMove = { from, to ->
                isReordering = true
                val item = localGroups.removeAt(from.index)
                localGroups.add(to.index, item)
            },
            onDragEnd = { _, _ ->
                onGroupsReordered(localGroups.toList())
                isReordering = false
            }
        )

        Dialog(onDismissRequest = {
            showDialog = false
            multiSelectMode = false
            selectedIds.clear()
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
                            text = if (multiSelectMode) "Select to Delete" else "Select Group",
                            style = MaterialTheme.typography.titleLarge
                        )
                        Row {
                            if (multiSelectMode) {
                                TextButton(onClick = {
                                    val nonFavorites = localGroups.filter { !it.isFavorite }
                                    if (selectedIds.size == nonFavorites.size) selectedIds.clear()
                                    else {
                                        selectedIds.clear()
                                        selectedIds.addAll(nonFavorites.map { it.id })
                                    }
                                }) {
                                    Text("All")
                                }
                                IconButton(onClick = {
                                    onDeleteGroups(selectedIds.toList())
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
                        state = reorderState.listState,
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .reorderable(reorderState)
                    ) {
                        items(localGroups, key = { it.id }) { group ->
                            ReorderableItem(reorderState, key = group.id) { isDragging ->
                                val elevation by animateDpAsState(
                                    targetValue = if (isDragging) 8.dp else 0.dp,
                                    label = "elevation"
                                )
                                val scale by animateFloatAsState(
                                    targetValue = if (isDragging) 1.03f else 1f,
                                    label = "scale"
                                )

                                val showCheckbox = multiSelectMode && !group.isFavorite

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .scale(scale)
                                        .shadow(elevation, RoundedCornerShape(8.dp))
                                        .combinedClickable(
                                            onClick = {
                                                if (multiSelectMode) {
                                                    if (!group.isFavorite) {
                                                        if (selectedIds.contains(group.id)) selectedIds.remove(group.id)
                                                        else selectedIds.add(group.id)
                                                    }
                                                } else {
                                                    selectedGroupId = group.id
                                                    showDialog = false
                                                }
                                            },
                                            onLongClick = {
                                                if (!group.isFavorite) {
                                                    multiSelectMode = true
                                                    if (!selectedIds.contains(group.id)) selectedIds.add(group.id)
                                                }
                                            }
                                        )
                                        .padding(vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Drag handle always visible (left side)
                                    Icon(
                                        imageVector = Icons.Default.DragHandle,
                                        contentDescription = "Drag to reorder",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier
                                            .size(24.dp)
                                            .detectReorder(reorderState)
                                    )

                                    if (showCheckbox) {
                                        Checkbox(
                                            checked = selectedIds.contains(group.id),
                                            onCheckedChange = { checked ->
                                                if (checked) selectedIds.add(group.id)
                                                else selectedIds.remove(group.id)
                                            }
                                        )
                                    }

                                    Column(
                                        modifier = Modifier
                                            .weight(1f)
                                            .padding(start = 8.dp)
                                    ) {
                                        Text(
                                            group.name,
                                            style = MaterialTheme.typography.bodyLarge,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        if (!multiSelectMode) {
                                            val count = group.recipients.size
                                            if (count > 0) {
                                                Spacer(Modifier.height(2.dp))
                                                Surface(
                                                    shape = RoundedCornerShape(50),
                                                    color = MaterialTheme.colorScheme.primaryContainer
                                                ) {
                                                    Text(
                                                        text = "$count ${if (count == 1) "recipient" else "recipients"}",
                                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                                        fontSize = 11.sp,
                                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                                        fontWeight = FontWeight.Medium
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    // Star button always visible (right side) — starred groups cannot be deleted
                                    if (!multiSelectMode) {
                                        IconButton(onClick = { onToggleGroupFavorite(group.id) }) {
                                            Icon(
                                                imageVector = if (group.isFavorite) Icons.Default.Star else Icons.Outlined.StarBorder,
                                                contentDescription = if (group.isFavorite) "Unstar group" else "Star group",
                                                tint = if (group.isFavorite) MaterialTheme.colorScheme.tertiary
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
                        onClick = { onAddGroup() },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Add New Group")
                    }
                }
            }
        }
    }

    // Rename dialog triggered from header long-press
    val capturedRenamingId = renamingGroupId
    if (capturedRenamingId != null) {
        AlertDialog(
            onDismissRequest = { renamingGroupId = null },
            title = { Text("Rename Group") },
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
                        onRenameGroup(capturedRenamingId, newName)
                    }
                    renamingGroupId = null
                }) {
                    Text("Rename")
                }
            },
            dismissButton = {
                TextButton(onClick = { renamingGroupId = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

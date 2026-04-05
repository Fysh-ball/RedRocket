package site.fysh.redrocket.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import site.fysh.redrocket.model.Recipient
import kotlinx.coroutines.delay

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun RecipientsInput(
    recipients: List<Recipient>,
    onAddRecipients: (List<Recipient>) -> Unit,
    onRemoveRecipient: (Recipient) -> Unit = {}
) {
    var showContactPicker by remember { mutableStateOf(false) }
    var showAddSheet by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val chipScrollState = rememberScrollState()
    val blockParent = remember(chipScrollState) {
        object : NestedScrollConnection {
            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource
            ): Offset = if (chipScrollState.maxValue > 0) available else Offset.Zero
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            showContactPicker = true
        }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = 4.dp)
                    .clickable { showAddSheet = true },
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Column(
                    modifier = Modifier
                        .padding(8.dp)
                        .heightIn(min = 56.dp, max = 120.dp)
                        .nestedScroll(blockParent)
                        .drawWithContent {
                            drawContent()
                            val range = chipScrollState.maxValue.toFloat()
                            if (range > 0f) {
                                val barH = (size.height * size.height / (size.height + range))
                                    .coerceAtLeast(24.dp.toPx())
                                val barTop = (chipScrollState.value.toFloat() / range) * (size.height - barH)
                                drawRoundRect(
                                    color = Color.Gray.copy(alpha = 0.45f),
                                    topLeft = Offset(size.width - 4.dp.toPx(), barTop + 2.dp.toPx()),
                                    size = Size(3.dp.toPx(), (barH - 4.dp.toPx()).coerceAtLeast(4f)),
                                    cornerRadius = CornerRadius(2.dp.toPx())
                                )
                            }
                        }
                        .verticalScroll(chipScrollState)
                ) {
                    if (recipients.isEmpty()) {
                        Text(
                            "Tap to add recipient numbers...",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp)
                        )
                    } else {
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            recipients.forEach { recipient ->
                                RecipientChip(recipient) { onRemoveRecipient(recipient) }
                            }
                        }
                    }
                }
            }

            Button(
                onClick = {
                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED) {
                        showContactPicker = true
                    } else {
                        permissionLauncher.launch(Manifest.permission.READ_CONTACTS)
                    }
                },
                modifier = Modifier.size(56.dp),
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(0.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Recipient", modifier = Modifier.size(28.dp))
            }
        }
    }

    if (showAddSheet) {
        RecipientAddSheet(
            onAdd = { number ->
                if (recipients.none { it.phoneNumber == number }) {
                    onAddRecipients(listOf(Recipient("", number)))
                }
            },
            onDismiss = { showAddSheet = false }
        )
    }

    if (showContactPicker) {
        MultiContactPickerDialog(
            existingRecipients = recipients,
            onDismiss = { showContactPicker = false },
            onContactsSelected = { selected ->
                onAddRecipients(selected)
                showContactPicker = false
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RecipientAddSheet(
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
                "Add Recipient",
                fontWeight = FontWeight.Bold,
                fontSize = 17.sp,
                modifier = Modifier.padding(bottom = 12.dp)
            )
            OutlinedTextField(
                value = textFieldValue,
                onValueChange = { newValue ->
                    if (newValue.text.endsWith(",")) {
                        val raw = newValue.text.removeSuffix(",")
                        val filtered = raw.filter { it.isDigit() || (it == '+' && raw.indexOf(it) == 0) }.trim()
                        if (filtered.length in 7..15) {
                            onAdd(filtered)
                        }
                        textFieldValue = TextFieldValue("", TextRange(0))
                    } else {
                        val raw = newValue.text
                        val filtered = buildString {
                            raw.forEachIndexed { i, c ->
                                if (c.isDigit() || (c == '+' && i == 0)) append(c)
                            }
                        }.take(16)
                        textFieldValue = newValue.copy(
                            text = filtered,
                            selection = TextRange(filtered.length)
                        )
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester),
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                placeholder = { Text("e.g. +1234567890") },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(onDone = {
                    val raw = textFieldValue.text.trim()
                    val digitsOnly = raw.filter { it.isDigit() }
                    if (digitsOnly.length in 7..15) {
                        onAdd(raw)
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
                        val raw = textFieldValue.text.trim()
                        val digitsOnly = raw.filter { it.isDigit() }
                        if (digitsOnly.length in 7..15) onAdd(raw)
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
fun MultiContactPickerDialog(
    existingRecipients: List<Recipient>,
    onDismiss: () -> Unit,
    onContactsSelected: (List<Recipient>) -> Unit
) {
    val context = LocalContext.current
    var contactList by remember { mutableStateOf(listOf<Recipient>()) }
    var searchQuery by remember { mutableStateOf(TextFieldValue("")) }
    val selectedContacts = remember { mutableStateListOf<Recipient>() }

    val filteredContacts = remember(contactList, searchQuery.text) {
        if (searchQuery.text.isBlank()) contactList
        else contactList.filter {
            it.name.contains(searchQuery.text, ignoreCase = true) || it.phoneNumber.contains(searchQuery.text)
        }
    }

    LaunchedEffect(Unit) {
        val loaded = withContext(Dispatchers.IO) {
            val tempContacts = mutableListOf<Recipient>()
            val cursor = context.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                arrayOf(
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                    ContactsContract.CommonDataKinds.Phone.NUMBER
                ),
                null, null,
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " ASC"
            )
            cursor?.use {
                val nameIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                val numberIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                while (it.moveToNext()) {
                    val name = it.getString(nameIndex)
                    val number = it.getString(numberIndex).replace(" ", "").replace("-", "")
                    tempContacts.add(Recipient(name, number))
                }
            }
            tempContacts.distinctBy { it.phoneNumber }
        }
        contactList = loaded
    }

    val configuration = LocalConfiguration.current
    val screenWidth = configuration.screenWidthDp.dp

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .width(screenWidth * 0.95f)
                .fillMaxHeight(0.9f),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text(
                    "Select Contacts",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp)
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onDoubleTap = {
                                    searchQuery = searchQuery.copy(
                                        selection = TextRange(0, searchQuery.text.length)
                                    )
                                }
                            )
                        }
                ) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Search contacts...") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = {
                        val available = filteredContacts.filter { contact -> 
                            existingRecipients.none { it.phoneNumber == contact.phoneNumber } 
                        }
                        if (selectedContacts.size == available.size && available.isNotEmpty()) {
                            selectedContacts.clear()
                        } else {
                            selectedContacts.clear()
                            selectedContacts.addAll(available)
                        }
                    }) {
                        val availableCount = filteredContacts.count { contact -> existingRecipients.none { it.phoneNumber == contact.phoneNumber } }
                        Text(if (selectedContacts.size == availableCount && availableCount > 0) "Deselect All" else "Select All")
                    }
                    Text("${selectedContacts.size} selected", style = MaterialTheme.typography.bodyMedium)
                }
                
                LazyColumn(modifier = Modifier.weight(1f)) {
                    items(filteredContacts) { contact ->
                        val isAlreadyAdded = existingRecipients.any { it.phoneNumber == contact.phoneNumber }
                        val isSelected = selectedContacts.contains(contact)
                        
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable(enabled = !isAlreadyAdded) {
                                    if (isSelected) selectedContacts.remove(contact)
                                    else selectedContacts.add(contact)
                                }
                                .padding(vertical = 12.dp, horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = isSelected || isAlreadyAdded,
                                onCheckedChange = null,
                                enabled = !isAlreadyAdded
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Column {
                                Text(
                                    contact.name,
                                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                                    color = if (isAlreadyAdded) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f) else MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    contact.phoneNumber,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (isAlreadyAdded) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                )
                            }
                            if (isAlreadyAdded) {
                                Spacer(modifier = Modifier.weight(1f))
                                Text(
                                    "Added",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 8.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    }
                }
                
                Spacer(Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel")
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Button(
                        onClick = { onContactsSelected(selectedContacts.toList()) },
                        enabled = selectedContacts.isNotEmpty(),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.height(48.dp)
                    ) {
                        Text("Add Selected (${selectedContacts.size})", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun RecipientChip(recipient: Recipient, onRemove: () -> Unit) {
    val context = LocalContext.current
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .height(32.dp)
            .clickable {
                // Deep-link into default SMS app with number pre-filled
                val intent = Intent(Intent.ACTION_SENDTO).apply {
                    data = Uri.parse("smsto:${recipient.phoneNumber}")
                }
                context.startActivity(intent)
            }
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 12.dp)
        ) {
            Text(
                text = recipient.name.ifEmpty { recipient.phoneNumber },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
            Spacer(Modifier.width(6.dp))
            Icon(
                Icons.Default.Close,
                contentDescription = "Remove",
                modifier = Modifier
                    .size(16.dp)
                    .clip(CircleShape)
                    .clickable { onRemove() }
                    .background(MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.1f), CircleShape)
                    .padding(2.dp),
                tint = MaterialTheme.colorScheme.onSecondaryContainer
            )
        }
    }
}

package site.fysh.redrocket.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.animation.*
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import site.fysh.redrocket.utils.AbuseLevel
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.NotificationsPaused
import androidx.compose.material.icons.filled.SystemUpdateAlt
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.geometry.Rect as ComposeRect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.mutableLongStateOf
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(viewModel: MainViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    val responses by viewModel.allResponses.collectAsState()
    val pastAlerts by viewModel.pastAlerts.collectAsState()
    val logs by viewModel.logs.collectAsState()
    val haptic = LocalHapticFeedback.current
    LaunchedEffect(Unit) {
        viewModel.countdownTickFlow.collect {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        }
    }
    var showSettings by remember { mutableStateOf(false) }
    var scenarioToDelete by remember { mutableStateOf<List<String>?>(null) }
    val focusManager = LocalFocusManager.current
    val context = LocalContext.current
    // 0 = Alert System, 1 = Dashboard
    var selectedTab by remember { mutableStateOf(0) }
    var lastSeenDashboardAt by remember { mutableLongStateOf(0L) }
    val hasUnreadResponses = responses.any { it.receivedAt > lastSeenDashboardAt }

    // Hoisted so the tutorial step-4 LaunchedEffect can programmatically scroll to the message field.
    // verticalScroll(enabled=false) blocks gestures but animateScrollTo() still works.
    val scrollState = rememberScrollState()

    // One-shot user messages (export/import results, test send feedback) surfaced from the ViewModel.
    LaunchedEffect(uiState.userMessage) {
        val msg = uiState.userMessage ?: return@LaunchedEffect
        Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
        viewModel.clearUserMessage()
    }

    // Tutorial position tracking - store Rect directly (computed at layout time via boundsInRoot()
    // inside the onGloballyPositioned callback) so we never read a stale LayoutCoordinates
    // reference on a subsequent composition pass.
    var scenarioBoundsRect   by remember { mutableStateOf<ComposeRect?>(null) }
    var triggerBoundsRect    by remember { mutableStateOf<ComposeRect?>(null) }
    var groupHeaderBoundsRect by remember { mutableStateOf<ComposeRect?>(null) }
    var messageBoundsRect    by remember { mutableStateOf<ComposeRect?>(null) }
    var dashboardTabBoundsRect by remember { mutableStateOf<ComposeRect?>(null) }

    // Auto-scroll to the message field when tutorial reaches step 4 (5/6: Your Message).
    // The main column scroll is disabled during the tutorial so the user can't scroll away from
    // spotlights, but animateScrollTo() bypasses the gesture lock and works programmatically.
    LaunchedEffect(uiState.tutorialStep) {
        if (uiState.tutorialStep == 4 && uiState.showTutorial) {
            delay(150) // allow layout to report updated messageBoundsRect
            scrollState.animateScrollTo(scrollState.maxValue)
        }
    }

    // Auto-advance tutorial step 0 when the user CLOSES the scenario dropdown
    var tutorialDropdownClosed by remember { mutableStateOf(false) }
    LaunchedEffect(tutorialDropdownClosed) {
        if (tutorialDropdownClosed && uiState.showTutorial && uiState.tutorialStep == 0) {
            delay(400)
            viewModel.advanceTutorialStep(6)
        }
        tutorialDropdownClosed = false
    }

    Box(modifier = Modifier.fillMaxSize()) {
    Scaffold(
        topBar = {},
        bottomBar = {
            BrowserTabBar(
                selectedTab = selectedTab,
                onTabSelected = { tab ->
                    selectedTab = tab
                    if (tab == 1) lastSeenDashboardAt = System.currentTimeMillis()
                    // Auto-advance tutorial step 5 when the user taps the Dashboard tab
                    if (tab == 1 && uiState.showTutorial && uiState.tutorialStep == 5) {
                        viewModel.advanceTutorialStep(6)
                    }
                },
                hasUnreadResponses = hasUnreadResponses,
                onSettingsClick = { showSettings = true },
                onDashboardTabPositioned = { dashboardTabBoundsRect = it.boundsInRoot() }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val up = waitForUpOrCancellation()
                        if (up != null && !down.isConsumed) {
                            focusManager.clearFocus()
                        }
                    }
                }
        ) {

            // Tab 1: Dashboard (shared across ALL scenarios)
            if (selectedTab == 1) {
                LaunchedEffect(Unit) {
                    lastSeenDashboardAt = System.currentTimeMillis()
                }
                ResponseDashboard(
                    allScenarios = uiState.scenarios,
                    responses = responses,
                    logs = logs,
                    pastAlerts = pastAlerts,
                    onDismiss = { selectedTab = 0 },
                    onClearResponses = { viewModel.clearAllResponses() },
                    onClearLogs = { viewModel.clearLogs() },
                    onClearPastAlerts = { viewModel.clearPastAlerts() },
                    onStopListening = { viewModel.stopListening() },
                    inlineMode = true
                )
            } else {
            // Tab 0: Alert System
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(scrollState, enabled = !uiState.showTutorial)
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Tab header
                Column {
                    Text(
                        "Alert System",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    HorizontalDivider(modifier = Modifier.padding(top = 6.dp))
                }

                // Scenario selector - placed directly under the title
                ScenarioDropdown(
                    scenarios = uiState.scenarios,
                    selectedScenario = uiState.currentScenario,
                    onScenarioSelected = { viewModel.onScenarioSelected(it) },
                    onAddScenario = { viewModel.onAddScenario() },
                    onDeleteScenarios = { ids -> scenarioToDelete = ids },
                    onToggleFavorite = { viewModel.onToggleFavorite(it) },
                    onRenameScenario = { id, name -> viewModel.onRenameScenario(id, name) },
                    onScenariosReordered = { viewModel.onScenariosReordered(it) },
                    onDropdownClosed = { tutorialDropdownClosed = true },
                    onPositioned = { scenarioBoundsRect = it.boundsInRoot() }
                )

                // Debug mode warning banner (hidden in production builds) - pulsing red
                if (uiState.isDebugEnabled) {
                    val infiniteTransition = rememberInfiniteTransition(label = "debugPulse")
                    val pulseAlpha by infiniteTransition.animateFloat(
                        initialValue = 0.82f,
                        targetValue = 1.0f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(600),
                            repeatMode = RepeatMode.Reverse
                        ),
                        label = "pulse"
                    )
                    Surface(
                        color = Color(0xFFB71C1C).copy(alpha = pulseAlpha),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Text(
                                "DEBUG MODE - Messages will NOT be sent",
                                color = Color.White,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                // Abuse-level warning banner (shown for MEDIUM_WARNING and HIGH_WARNING tiers)
                val abuseWarningText = when (uiState.abuseLevel) {
                    AbuseLevel.MEDIUM_WARNING ->
                        "This app is for emergency automated messages. Please use it responsibly."
                    AbuseLevel.HIGH_WARNING ->
                        "This is not for group messaging. Please use another messaging app for non-emergencies."
                    else -> null
                }
                abuseWarningText?.let { warning ->
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.8f),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            warning,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                // Send cooldown / error banner
                uiState.errorMessage?.let { errorMsg ->
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            errorMsg,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }

                // Re-check notification permission on resume so returning from the system
                // Notification Access settings page reflects instantly.
                val notifLifecycleOwner = LocalLifecycleOwner.current
                DisposableEffect(notifLifecycleOwner) {
                    val observer = LifecycleEventObserver { _, event ->
                        if (event == Lifecycle.Event.ON_RESUME) viewModel.refreshNotificationPermission()
                    }
                    notifLifecycleOwner.lifecycle.addObserver(observer)
                    onDispose { notifLifecycleOwner.lifecycle.removeObserver(observer) }
                }

                // Notification Permission Warning
                if (!uiState.isNotificationPermissionGranted) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer
                        ),
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                        }
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Icon(Icons.Default.NotificationsPaused, contentDescription = null)
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Detection Offline", fontWeight = FontWeight.Bold)
                                Text(
                                    "The app cannot monitor for emergency alerts. Tap to enable Notification Access.",
                                    fontSize = 14.sp
                                )
                            }
                        }
                    }
                }

                // Battery optimization warning - shown if still active (can prevent background sending)
                val powerManager = remember { context.getSystemService(PowerManager::class.java) }
                fun isExempt() = powerManager?.isIgnoringBatteryOptimizations(context.packageName) == true
                var batteryOptDisabled by remember { mutableStateOf(isExempt()) }
                // Re-check on resume (user returns from battery settings) and on power-save mode change.
                val lifecycleOwner = LocalLifecycleOwner.current
                DisposableEffect(lifecycleOwner) {
                    val observer = LifecycleEventObserver { _, event ->
                        if (event == Lifecycle.Event.ON_RESUME) batteryOptDisabled = isExempt()
                    }
                    val receiver = object : BroadcastReceiver() {
                        override fun onReceive(ctx: Context, intent: Intent?) { batteryOptDisabled = isExempt() }
                    }
                    lifecycleOwner.lifecycle.addObserver(observer)
                    context.registerReceiver(receiver, IntentFilter(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED))
                    onDispose {
                        lifecycleOwner.lifecycle.removeObserver(observer)
                        context.unregisterReceiver(receiver)
                    }
                }
                if (!batteryOptDisabled) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer
                        ),
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            try {
                                val intent = android.content.Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                                intent.data = Uri.parse("package:${context.packageName}")
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                context.startActivity(android.content.Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS))
                            }
                        }
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Icon(Icons.Default.NotificationsPaused, contentDescription = null)
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Battery Optimization Active", fontWeight = FontWeight.Bold)
                                Text(
                                    "Messages may not send when the phone is locked. Tap to disable.",
                                    fontSize = 14.sp
                                )
                            }
                        }
                    }
                }

                // OEM background restriction warning for manufacturers known to aggressively
                // kill background apps beyond the standard battery optimization setting.
                val oemName = remember { Build.MANUFACTURER.lowercase() }
                val isRestrictiveOem = remember {
                    oemName.contains("xiaomi") || oemName.contains("huawei") ||
                    oemName.contains("honor") || oemName.contains("oppo") ||
                    oemName.contains("vivo") || oemName.contains("realme")
                }
                if (isRestrictiveOem && !batteryOptDisabled) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.7f),
                            contentColor = MaterialTheme.colorScheme.onErrorContainer
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Icon(Icons.Default.Warning, contentDescription = null)
                            Column(modifier = Modifier.weight(1f)) {
                                Text("${Build.MANUFACTURER} Detected", fontWeight = FontWeight.Bold)
                                Text(
                                    "This device may also restrict background apps via AutoStart or Power Center settings. Enable AutoStart for Red Rocket in your device's battery settings.",
                                    fontSize = 14.sp
                                )
                            }
                        }
                    }
                }

                // Update available banner — shown once per session, dismissible
                if (uiState.updateAvailable != null) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                            contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                        ),
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            try {
                                context.startActivity(
                                    android.content.Intent(
                                        android.content.Intent.ACTION_VIEW,
                                        android.net.Uri.parse("https://github.com/Fysh-ball/RedRocket/releases")
                                    )
                                )
                            } catch (_: Exception) {}
                        }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(Icons.Default.SystemUpdateAlt, contentDescription = null)
                            Column(modifier = Modifier.weight(1f)) {
                                Text("v${uiState.updateAvailable} available", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                Text("Tap to download from GitHub", fontSize = 14.sp)
                            }
                            IconButton(onClick = { viewModel.dismissUpdate() }, modifier = Modifier.size(32.dp)) {
                                Icon(Icons.Default.Close, contentDescription = "Dismiss", modifier = Modifier.size(20.dp))
                            }
                        }
                    }
                }

                // key() forces full recomposition of input sections when scenario switches,
                // clearing any stale local draft state (typed-but-not-committed text, open dialogs).
                key(uiState.currentScenario.id) {
                    // Alert Filters Section
                    SectionCard(
                        title = "Alert Filters",
                        modifier = Modifier.onGloballyPositioned { triggerBoundsRect = it.boundsInRoot() }
                    ) {
                        TriggerInput(
                            keywordsString = uiState.currentScenario.description,
                            onKeywordsChange = { viewModel.onKeywordsChange(it) },
                            blockPhrases = viewModel.blockPhrases.collectAsState().value,
                            onAddBlockPhrase = { viewModel.addBlockPhrase(it) },
                            onDeleteBlockPhrase = { viewModel.deleteBlockPhrase(it) },
                            onSheetDismissed = {
                                if (uiState.showTutorial && uiState.tutorialStep == 2 &&
                                    uiState.currentScenario.description.isNotBlank()) {
                                    viewModel.advanceTutorialStep(6)
                                }
                            },
                            userRegion = viewModel.userRegion.collectAsState().value,
                            detectedRegion = viewModel.detectedRegion.collectAsState().value,
                            onSetRegion = { viewModel.setUserRegion(it) }
                        )
                    }

                    // Groups Section (recipients + message per group)
                    SectionCard(title = "Groups") {
                        GroupsSection(
                            scenario = uiState.currentScenario,
                            onAddGroup = { viewModel.onAddGroup() },
                            onDeleteGroups = { ids -> viewModel.onDeleteGroups(ids) },
                            onRenameGroup = { groupId, name -> viewModel.onRenameGroup(groupId, name) },
                            onAddRecipientsToGroup = { groupId, recipients ->
                                viewModel.onAddRecipientsToGroup(groupId, recipients)
                            },
                            onRemoveRecipientFromGroup = { groupId, recipient ->
                                viewModel.onRemoveRecipientFromGroup(groupId, recipient)
                            },
                            onGroupMessageChange = { groupId, msg ->
                                viewModel.onGroupMessageChange(groupId, msg)
                            },
                            onToggleGroupFavorite = { groupId ->
                                viewModel.onToggleGroupFavorite(groupId)
                            },
                            onGroupsReordered = { groups ->
                                viewModel.onGroupsReordered(groups)
                            },
                            onGroupHeaderPositioned = { groupHeaderBoundsRect = it.boundsInRoot() },
                            onMessageInputPositioned = { messageBoundsRect = it.boundsInRoot() }
                        )
                    }
                }

                // Scenario Lock Status
                val isLocked = uiState.currentScenario.isLocked
                if (isLocked) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "Scenario Locked",
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                                Text(
                                    "Triggers are blocked until this scenario is reset.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.7f)
                                )
                            }
                            Button(
                                onClick = { viewModel.onToggleLock(uiState.currentScenario.id) },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                            ) {
                                Text("Reset / Unlock", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                // --- Cooldown state ---
                var nowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
                LaunchedEffect(uiState.lastSendCompletedAt) {
                    if (uiState.lastSendCompletedAt == 0L) return@LaunchedEffect
                    val cooldownDuration = 60_000L
                    while (true) {
                        delay(1000L)
                        nowMs = System.currentTimeMillis()
                        if ((nowMs - uiState.lastSendCompletedAt) >= cooldownDuration) break
                    }
                }
                val cooldownMs = 60_000L
                val cooldownSecondsRemaining = if (uiState.lastSendCompletedAt > 0) {
                    ((cooldownMs - (nowMs - uiState.lastSendCompletedAt)) / 1000L).coerceAtLeast(0L)
                } else 0L
                val isInCooldown = cooldownSecondsRemaining > 0 && !isLocked

                // Swipe to Force Send - hidden entirely when scenario is locked
                if (!isLocked) {
                    if (isInCooldown) {
                        val mins = cooldownSecondsRemaining / 60
                        val secs = cooldownSecondsRemaining % 60
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(80.dp)
                                .clip(RoundedCornerShape(40.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Available in %02d:%02d".format(mins, secs),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        SwipeToConfirm(
                            text = "Swipe to Force Send",
                            onConfirm = {
                                focusManager.clearFocus()
                                viewModel.initiateManualSend()
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

            } // end Alert System Column
            } // end selectedTab == 0 else

            // Undo Popup
            AnimatedVisibility(
                visible = uiState.showUndoPopup,
                enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 32.dp)
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.inverseSurface,
                    contentColor = MaterialTheme.colorScheme.inverseOnSurface,
                    shape = RoundedCornerShape(24.dp),
                    tonalElevation = 6.dp,
                    modifier = Modifier.padding(horizontal = 16.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(start = 4.dp, end = 4.dp, top = 2.dp, bottom = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(
                            onClick = { viewModel.onUndo() },
                            contentPadding = PaddingValues(horizontal = 12.dp),
                            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.inversePrimary)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = null, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Undo", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        }
                        IconButton(
                            onClick = { viewModel.onUndoPopupDismissed() },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Dismiss", modifier = Modifier.size(20.dp))
                        }
                    }
                }
            }

            if (uiState.isSending || uiState.isManualCountdownActive || uiState.showSuccessPopup) {
                Box(modifier = Modifier.align(Alignment.BottomCenter)) {
                    StatusPopup(
                        sendState = uiState.currentSendState,
                        processed = uiState.processedCount,
                        total = uiState.totalCount,
                        failedCount = uiState.failedCount,
                        elapsedTime = uiState.elapsedTimeSeconds,
                        isKeepTrying = uiState.isKeepTryingEnabled,
                        onKeepTryingToggle = { viewModel.toggleKeepTrying(it) },
                        statusIndicators = uiState.statusIndicators,
                        countdownSeconds = if (uiState.isManualCountdownActive) uiState.manualCountdownSeconds else null,
                        onCancel = {
                            if (uiState.isManualCountdownActive) {
                                viewModel.onCancelManualSend()
                            } else {
                                viewModel.onStopSending()
                            }
                        },
                        onDismiss = { viewModel.dismissSuccessPopup() },
                        completionStats = if (uiState.showSuccessPopup) uiState.completionStats else null,
                        currentMessageStatus = uiState.currentMessageStatus
                    )
                }
            }

            if (showSettings) {
                SettingsDialog(
                    uiState = uiState,
                    onRunSimulation = { count, rate ->
                        viewModel.runDebugSimulation(count, rate)
                        showSettings = false
                    },
                    onToggleDebug = { viewModel.toggleDebug(it) },
                    onForceSequentialChange = { viewModel.toggleForceSequential(it) },
                    onWideSpreadToggle = { viewModel.toggleWideSpread(it) },
                    onFailureRateChange = { viewModel.setFailureRate(it) },
                    onThemeChange = { viewModel.setTheme(it) },
                    onReplyListenHoursChange = { viewModel.setReplyListenHours(it) },
                    onAlertSensitivityChange = { viewModel.setAlertSensitivity(it) },
                    onSetAutoBackupFolder = { uri -> viewModel.setAutoBackupFolder(uri) },
                    onExportScenarios = { uri -> viewModel.exportScenarios(uri) },
                    onImportScenarios = { uri -> viewModel.importScenarios(uri) },
                    onSendTestMessage = { phone -> viewModel.sendTestMessage(phone) },
                    onReplayTutorial = {
                        showSettings = false
                        viewModel.resetTutorial()
                    },
                    onDismiss = { showSettings = false }
                )
            }

            uiState.duplicateContactError?.let { error ->
                AlertDialog(
                    onDismissRequest = { viewModel.dismissDuplicateError() },
                    title = { Text("Contact Already Added", fontWeight = FontWeight.Bold) },
                    text = {
                        Text("Cannot add ${error.contactName}. They are already in:\nScenario: ${error.scenarioName}\nGroup: ${error.groupName}")
                    },
                    confirmButton = {
                        Button(onClick = { viewModel.dismissDuplicateError() }) { Text("OK") }
                    }
                )
            }

            scenarioToDelete?.let { ids ->
                val favorites = uiState.scenarios.filter { it.id in ids && it.isFavorite }
                AlertDialog(
                    onDismissRequest = { scenarioToDelete = null },
                    title = { Text(if (favorites.isNotEmpty()) "Cannot Delete" else "Are you sure?") },
                    text = {
                        if (favorites.isNotEmpty()) {
                            Text("Favorited scenarios cannot be deleted.")
                        } else {
                            Text("Do you really want to delete the selected scenario(s)?")
                        }
                    },
                    confirmButton = {
                        if (favorites.isEmpty()) {
                            TextButton(onClick = {
                                viewModel.onDeleteScenarios(ids)
                                scenarioToDelete = null
                            }) { Text("Delete") }
                        } else {
                            TextButton(onClick = { scenarioToDelete = null }) { Text("OK") }
                        }
                    },
                    dismissButton = {
                        if (favorites.isEmpty()) {
                            TextButton(onClick = { scenarioToDelete = null }) { Text("Cancel") }
                        }
                    }
                )
            }

            if (uiState.showManualSendDialog) {
                ManualSendDialog(
                    onDismiss = { viewModel.dismissManualSend() },
                    onConfirm = { captcha -> viewModel.onManualSendConfirmed(captcha) },
                    captcha = uiState.currentCaptcha,
                    recipientCount = uiState.currentScenario.allRecipients().size
                )
            }

            if (uiState.showAbuseLockoutDialog) {
                AbuseLockoutDialog(
                    secondsRemaining = uiState.abuseLockoutSecondsRemaining,
                    overrideAvailable = uiState.abuseOverrideAvailable,
                    onOverride = { viewModel.onAbuseOverride() },
                    onDismiss = { viewModel.dismissAbuseLockout() }
                )
            }

        }
    }

    if (uiState.showTutorial) {
        TutorialSpotlightOverlay(
            step = uiState.tutorialStep,
            scenarioBounds = scenarioBoundsRect,
            triggerBounds = triggerBoundsRect,
            groupHeaderBounds = groupHeaderBoundsRect,
            messageBounds = messageBoundsRect,
            tabBarBounds = dashboardTabBoundsRect,
            uiState = uiState,
            onAdvance = { viewModel.advanceTutorialStep(6) },
            onDismiss = { viewModel.dismissTutorial() }
        )
    }

    if (uiState.showTutorialComplete) {
        TutorialCompleteOverlay(onDismiss = { viewModel.completeTutorial() })
    }
    } // closes outer Box
}


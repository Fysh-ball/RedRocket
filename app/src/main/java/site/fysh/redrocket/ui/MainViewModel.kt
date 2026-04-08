package site.fysh.redrocket.ui

import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import com.google.gson.Gson
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import site.fysh.redrocket.EmergencyApp
import site.fysh.redrocket.model.*
import site.fysh.redrocket.util.AlertSensitivity
import site.fysh.redrocket.util.RegionSettings
import site.fysh.redrocket.util.normalizePhone
import site.fysh.redrocket.utils.AppLogger
import java.util.UUID
import site.fysh.redrocket.queue.*
import site.fysh.redrocket.service.*
import site.fysh.redrocket.utils.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.util.ArrayDeque

enum class AppTheme { SYSTEM, LIGHT, GRAY, NIGHT }

class MainViewModelFactory(private val app: EmergencyApp) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MainViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return MainViewModel(
                app,
                app.queueManager,
                app.adaptiveController,
                app.manualGuard,
                app.debugSimulator,
                app.database.scenarioDao(),
                app.database.responseRecordDao(),
                app.database.pastAlertDao(),
                app.settings
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

class MainViewModel(
    private val app: EmergencyApp,
    private val queueManager: MessageQueueManager,
    private val adaptiveController: AdaptiveSendController,
    private val manualGuard: ManualSendGuard,
    private val debugSimulator: DebugSimulator,
    private val scenarioDao: ScenarioDao,
    private val responseRecordDao: ResponseRecordDao,
    private val pastAlertDao: PastAlertDao,
    private val settings: AppSettings
) : ViewModel() {
    private val TAG = "MainViewModel"
    // Shared Gson instance — Gson is thread-safe and intended to be reused
    private val gson = Gson()
    companion object {
        /** Increment when ScenarioBackup, Scenario, Group, or Recipient fields change in a breaking way. */
        private const val BACKUP_CURRENT_VERSION = 2
    }

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    private val _responses = MutableStateFlow<List<ResponseRecord>>(emptyList())
    val responses: StateFlow<List<ResponseRecord>> = _responses.asStateFlow()

    private val _allResponses = MutableStateFlow<List<ResponseRecord>>(emptyList())
    val allResponses: StateFlow<List<ResponseRecord>> = _allResponses.asStateFlow()

    private val _pastAlerts = MutableStateFlow<List<PastAlert>>(emptyList())
    val pastAlerts: StateFlow<List<PastAlert>> = _pastAlerts.asStateFlow()

    private val _blockPhrases = MutableStateFlow<List<BlockPhrase>>(emptyList())
    val blockPhrases: StateFlow<List<BlockPhrase>> = _blockPhrases.asStateFlow()

    /** ISO 3166-1 alpha-2 code auto-detected from SIM/network/locale. Never empty. */
    val detectedRegion: StateFlow<String> = RegionSettings.detectedRegionFlow

    /** User-chosen region override. Empty string = use detectedRegion. */
    val userRegion: StateFlow<String> = RegionSettings.userRegionFlow

    private val _logs = MutableStateFlow<List<LogEntry>>(emptyList())
    val logs: StateFlow<List<LogEntry>> = _logs.asStateFlow()

    private val _countdownTickFlow = MutableSharedFlow<Int>(extraBufferCapacity = 1)
    val countdownTickFlow: SharedFlow<Int> = _countdownTickFlow.asSharedFlow()

    // Main-thread-only — all access points (onDeleteGroup, onDeleteGroups, onUndo,
    // showUndoPopup) run on viewModelScope which defaults to Dispatchers.Main.
    // Never access from a background dispatcher.
    private val undoStack = ArrayDeque<Scenario>()
    @Volatile private var sendStartTime: Long = 0
    @Volatile private var lastTestSendTime: Long = 0
    @Volatile private var cachedLastScenarioId: String? = null
    private var undoTimerJob: Job? = null
    private var monitoringJob: Job? = null

    /** File written automatically whenever scenarios or block phrases change. */
    val autoBackupFile: File
        get() = File(app.getExternalFilesDir(null), "redrocket_backup.json")

    init {
        // Load settings
        viewModelScope.launch {
            settings.isFirstLaunch.collect { first ->
                _uiState.update { it.copy(isFirstLaunch = first, isInitializing = false) }
            }
        }
        viewModelScope.launch {
            settings.debugEnabled.collect { debug ->
                _uiState.update { it.copy(isDebugEnabled = debug) }
            }
        }
        viewModelScope.launch {
            settings.failureRate.collect { rate ->
                _uiState.update { it.copy(failureRate = rate) }
            }
        }
        viewModelScope.launch {
            settings.forceSequential.collect { sequential ->
                _uiState.update { it.copy(isForceSequential = sequential) }
                adaptiveController.setForceSequential(sequential)
            }
        }
        viewModelScope.launch {
            settings.wideSpreadEnabled.collect { wideSpread ->
                _uiState.update { it.copy(isWideSpreadEnabled = wideSpread) }
            }
        }
        viewModelScope.launch {
            settings.alertSensitivity.collect { sensitivityStr ->
                val sensitivity = try { AlertSensitivity.valueOf(sensitivityStr) } catch (_: Exception) { AlertSensitivity.MEDIUM }
                _uiState.update { it.copy(alertSensitivity = sensitivity) }
            }
        }
        viewModelScope.launch {
            settings.forceSendUsed.collect { used ->
                _uiState.update { it.copy(forceSendUsed = used) }
            }
        }
        viewModelScope.launch {
            settings.autoBackupUri.collect { uri ->
                _uiState.update { it.copy(autoBackupUri = uri) }
            }
        }
        viewModelScope.launch {
            settings.theme.collect { themeStr ->
                val theme = when (themeStr) {
                    "LIGHT" -> AppTheme.LIGHT
                    "GRAY"  -> AppTheme.GRAY
                    "NIGHT" -> AppTheme.NIGHT
                    "DARK"  -> AppTheme.NIGHT
                    else    -> AppTheme.SYSTEM
                }
                _uiState.update { it.copy(theme = theme) }
            }
        }

        // Cache lastScenarioId so the scenarios collector never blocks on DataStore
        viewModelScope.launch {
            settings.lastScenarioId.collect { cachedLastScenarioId = it }
        }

        // Load scenarios from database
        viewModelScope.launch {
            scenarioDao.getAllScenarios().collect { scenarios ->
                if (scenarios.isEmpty()) {
                    val initialScenario = Scenario(
                        name = "My Scenario",
                        message = "",
                        description = "",
                        groups = listOf(Group(name = "Default"))
                    )
                    scenarioDao.insertScenario(initialScenario)
                } else {
                    // Runtime migration: convert legacy scenarios (recipients + message) to groups
                    val migrated = scenarios.map { scenario ->
                        when {
                            scenario.groups.isNotEmpty() -> scenario
                            scenario.recipients.isNotEmpty() || scenario.message.isNotBlank() -> {
                                scenario.copy(groups = listOf(Group(
                                    name = "Default",
                                    recipients = scenario.recipients,
                                    message = scenario.message
                                )))
                            }
                            else -> scenario.copy(groups = listOf(Group(name = "Default")))
                        }
                    }
                    // Persist any scenarios that needed migration
                    scenarios.zip(migrated).forEach { (original, updated) ->
                        if (original.groups != updated.groups) {
                            scenarioDao.insertScenario(updated)
                        }
                    }

                    val lastId = cachedLastScenarioId
                    _uiState.update { state ->
                        val current = if (lastId != null) {
                            migrated.find { it.id == lastId } ?: migrated.first()
                        } else {
                            migrated.first()
                        }
                        state.copy(scenarios = migrated, currentScenario = current)
                    }
                }
            }
        }

        // Real-time synchronization with Adaptive Controller
        viewModelScope.launch {
            adaptiveController.currentState.collect { newState ->
                _uiState.update { it.copy(currentSendState = newState) }
            }
        }

        // Check for Notification Listener Permission (cancelled when ViewModel is cleared).
        // Polls at 2s until granted, then backs off to 30s.
        // Call refreshNotificationPermission() from ON_RESUME for an immediate re-check.
        viewModelScope.launch {
            while (currentCoroutineContext().isActive) {
                val isEnabled = PermissionUtils.isNotificationServiceEnabled(app)
                _uiState.update { it.copy(isNotificationPermissionGranted = isEnabled) }
                delay(if (isEnabled) 30_000L else 2_000L)
            }
        }

        // Live per-message status from the queue
        viewModelScope.launch {
            queueManager.currentMessageStatus.collect { status ->
                _uiState.update { it.copy(currentMessageStatus = status) }
            }
        }

        // Collect latest responses for the currently-viewed scenario
        viewModelScope.launch {
            uiState
                .map { it.currentScenario.id }
                .distinctUntilChanged()
                .collectLatest { scenarioId ->
                    responseRecordDao.getLatestResponsePerRecipient(scenarioId).collect { records ->
                        _responses.value = records
                    }
                }
        }

        // Also expose all responses across all scenarios for aggregate views
        viewModelScope.launch {
            responseRecordDao.getAllLatestResponses().collect { records ->
                _allResponses.value = records
            }
        }

        // Collect past alerts
        viewModelScope.launch {
            pastAlertDao.getAllAlerts().collect { alerts ->
                _pastAlerts.value = alerts
            }
        }

        // Collect system logs
        viewModelScope.launch {
            app.database.logEntryDao().getRecentLogs().collect { entries ->
                _logs.value = entries
            }
        }

        // Collect block phrases
        viewModelScope.launch {
            app.database.blockPhraseDao().getAll().collect { phrases ->
                _blockPhrases.value = phrases
            }
        }

        // Auto-export: write backup silently whenever scenarios or block phrases change.
        // Debounced so rapid consecutive edits only trigger one write.
        viewModelScope.launch(Dispatchers.IO) {
            combine(
                scenarioDao.getAllScenarios(),
                app.database.blockPhraseDao().getAll()
            ) { scenarios, phrases -> Pair(scenarios, phrases) }
                .debounce(3_000L)
                .collect { (scenarios, phrases) ->
                    if (scenarios.isNotEmpty()) autoExportBackup(scenarios, phrases)
                }
        }

        // Collect reply listen hours setting
        viewModelScope.launch {
            settings.replyListenHours.collect { hours ->
                _uiState.update { it.copy(replyListenHours = hours) }
                SmsResponseReceiver.setListenWindowHours(hours)
            }
        }

        // Collect presets offered setting
        viewModelScope.launch {
            settings.presetsOffered.collect { offered ->
                _uiState.update { it.copy(presetsOffered = offered) }
            }
        }

        // Check for an available update once per session (best-effort, failures silently ignored)
        viewModelScope.launch {
            val latest = site.fysh.redrocket.util.UpdateChecker.checkForUpdate(
                site.fysh.redrocket.BuildConfig.VERSION_NAME
            )
            if (latest != null) _uiState.update { it.copy(updateAvailable = latest) }
        }

        // Show What's New dialog when the app version has changed since the last launch.
        // Fetches release notes from GitHub so the content never needs to be hardcoded.
        viewModelScope.launch {
            val lastSeen = settings.lastSeenVersion.first()
            val current = site.fysh.redrocket.BuildConfig.VERSION_NAME
            when {
                lastSeen.isEmpty() -> {
                    // First ever launch — record version silently, no dialog
                    settings.setLastSeenVersion(current)
                }
                lastSeen != current -> {
                    _uiState.update { it.copy(showWhatsNew = true) }
                    // Fetch release notes from GitHub in the background
                    val body = site.fysh.redrocket.util.UpdateChecker.fetchReleaseNotes(current)
                    _uiState.update { it.copy(whatsNewBody = body) }
                }
            }
        }

        startMonitoring()
    }

    private fun startMonitoring() {
        monitoringJob?.cancel()
        monitoringJob = viewModelScope.launch {
            queueManager.queueStatusFlow.collect { status ->
                val remaining = status.remaining
                val failedCount = status.failedCount
                val total = status.totalEnqueued
                val processed = status.processed

                if (total > 0) {
                    if (sendStartTime == 0L) sendStartTime = System.currentTimeMillis()
                    val elapsed = (System.currentTimeMillis() - sendStartTime) / 1000

                    val indicators = when {
                        status.retrySize > 0 -> StatusIndicators(retrying = true)
                        status.primarySize > 0 -> StatusIndicators(sending = true)
                        processed > 0 && remaining == 0 && failedCount > 0 -> StatusIndicators(failed = true)
                        else -> StatusIndicators()
                    }

                    // Freeze the timer once all messages are processed
                    val frozenElapsed = if (remaining > 0) {
                        elapsed
                    } else {
                        _uiState.value.elapsedTimeSeconds.takeIf { it > 0 } ?: elapsed
                    }

                    // Merge completion detection into the same atomic update to avoid
                    // a race where isSending=false is visible before showSuccessPopup=true,
                    // which would cause the popup to briefly vanish.
                    val isCompleted = remaining == 0 && total > 0
                    if (isCompleted) sendStartTime = 0L  // reset so the next send starts fresh

                    _uiState.update { state ->
                        val base = state.copy(
                            processedCount = processed,
                            totalCount = total,
                            failedCount = failedCount,
                            isSending = remaining > 0,
                            elapsedTimeSeconds = frozenElapsed,
                            statusIndicators = indicators
                        )
                        if (isCompleted && !state.showSuccessPopup) {
                            Log.i(TAG, "All messages processed. Showing completion popup. success=${status.successCount} failed=${status.failedCount}")
                            val stats = CompletionStats(
                                sentSuccessfully = (status.successCount - status.retrySuccessCount).coerceAtLeast(0),
                                requiredRetries = status.retrySuccessCount,
                                failedPermanently = status.failedCount
                            )
                            base.copy(showSuccessPopup = true, completionStats = stats)
                        } else {
                            base
                        }
                    }
                }
            }
        }
    }

    private fun autoSave() {
        val current = _uiState.value.currentScenario
        viewModelScope.launch {
            scenarioDao.insertScenario(current)
            showUndoPopup()
        }
    }

    private fun showUndoPopup() {
        undoTimerJob?.cancel()
        _uiState.update { it.copy(showUndoPopup = true) }
        undoTimerJob = viewModelScope.launch {
            delay(5000)
            _uiState.update { it.copy(showUndoPopup = false) }
        }
    }

    fun onUndoPopupDismissed() {
        undoTimerJob?.cancel()
        _uiState.update { it.copy(showUndoPopup = false) }
    }

    fun completeFirstLaunch() {
        viewModelScope.launch {
            settings.setFirstLaunch(false)
        }
    }

    fun resetTutorial() {
        startTutorial()
    }

    // --- Scenario & Content Actions ---

    fun onScenarioSelected(scenario: Scenario) {
        undoStack.clear()
        _uiState.update { it.copy(currentScenario = scenario, showUndoPopup = false) }
        viewModelScope.launch {
            settings.setLastScenarioId(scenario.id)
        }
    }

    fun onMessageChange(newMessage: String) {
        if (_uiState.value.currentScenario.message == newMessage) return
        undoStack.addLast(_uiState.value.currentScenario.copy())
        _uiState.update {
            it.copy(currentScenario = it.currentScenario.copy(message = newMessage))
        }
        autoSave()
    }

    fun onKeywordsChange(newKeywords: String) {
        if (_uiState.value.currentScenario.description == newKeywords) return
        undoStack.addLast(_uiState.value.currentScenario.copy())
        _uiState.update {
            it.copy(currentScenario = it.currentScenario.copy(description = newKeywords))
        }
        autoSave()
    }

    fun onToggleFavorite(scenarioId: String) {
        viewModelScope.launch {
            val scenario = scenarioDao.getScenarioById(scenarioId)
            scenario?.let {
                val updated = it.copy(isFavorite = !it.isFavorite)
                scenarioDao.insertScenario(updated)
            }
        }
    }

    fun onRenameScenario(scenarioId: String, newName: String) {
        viewModelScope.launch {
            val scenario = scenarioDao.getScenarioById(scenarioId)
            scenario?.let {
                val updated = it.copy(name = newName)
                scenarioDao.insertScenario(updated)
                if (_uiState.value.currentScenario.id == scenarioId) {
                    _uiState.update { it.copy(currentScenario = updated) }
                }
            }
        }
    }

    fun onDeleteScenarios(ids: List<String>) {
        viewModelScope.launch {
            val validIds = ids.filter { id ->
                _uiState.value.scenarios.find { id == it.id }?.isFavorite == false
            }
            scenarioDao.deleteScenariosByIds(validIds)
        }
    }

    fun onAddScenario() {
        viewModelScope.launch {
            val newScenario = Scenario(
                name = "New Scenario",
                message = "",
                description = "",
                orderIndex = _uiState.value.scenarios.size,
                groups = listOf(Group(name = "Default"))
            )
            // Cache the new ID before the insert so the getAllScenarios() Flow collector
            // picks up the correct scenario when it emits in response to the insert.
            // Without this, the collector would still see the old cachedLastScenarioId and
            // select a different scenario, which our subsequent update() would then race against.
            cachedLastScenarioId = newScenario.id
            scenarioDao.insertScenario(newScenario)
            _uiState.update { it.copy(currentScenario = newScenario) }
            settings.setLastScenarioId(newScenario.id)
        }
    }

    fun onScenariosReordered(reorderedScenarios: List<Scenario>) {
        viewModelScope.launch {
            reorderedScenarios.forEachIndexed { index, scenario ->
                scenarioDao.insertScenario(scenario.copy(orderIndex = index))
            }
        }
    }

    // --- Group Management ---

    fun onAddGroup() {
        val current = _uiState.value.currentScenario
        val newGroup = Group(name = "Group ${current.groups.size + 1}")
        val updated = current.copy(groups = current.groups + newGroup)
        _uiState.update { it.copy(currentScenario = updated) }
        viewModelScope.launch { scenarioDao.insertScenario(updated) }
    }

    fun onDeleteGroup(groupId: String) {
        val current = _uiState.value.currentScenario
        if (current.groups.size <= 1) return
        val group = current.groups.find { it.id == groupId } ?: return
        if (group.isFavorite) return  // Starred groups cannot be deleted
        undoStack.addLast(current)
        val updated = current.copy(groups = current.groups.filter { it.id != groupId })
        _uiState.update { it.copy(currentScenario = updated) }
        viewModelScope.launch { scenarioDao.insertScenario(updated) }
        showUndoPopup()
    }

    fun onDeleteGroups(groupIds: List<String>) {
        val current = _uiState.value.currentScenario
        val validIds = groupIds.filter { id ->
            current.groups.find { it.id == id }?.isFavorite == false
        }.toSet()
        if (validIds.isEmpty()) return  // Nothing to delete (all were starred or not found)
        val remaining = current.groups.filter { it.id !in validIds }
        if (remaining.isEmpty()) return  // Never delete all groups
        undoStack.addLast(current)
        val updated = current.copy(groups = remaining)
        _uiState.update { it.copy(currentScenario = updated) }
        viewModelScope.launch { scenarioDao.insertScenario(updated) }
        showUndoPopup()
    }

    fun onToggleGroupFavorite(groupId: String) {
        val current = _uiState.value.currentScenario
        val updated = current.copy(
            groups = current.groups.map { if (it.id == groupId) it.copy(isFavorite = !it.isFavorite) else it }
        )
        _uiState.update { it.copy(currentScenario = updated) }
        viewModelScope.launch { scenarioDao.insertScenario(updated) }
    }

    fun onGroupsReordered(groups: List<Group>) {
        val current = _uiState.value.currentScenario
        if (groups.size != current.groups.size) return  // Stale reorder from mid-drag DB update
        val updated = current.copy(groups = groups)
        _uiState.update { it.copy(currentScenario = updated) }
        viewModelScope.launch { scenarioDao.insertScenario(updated) }
    }

    fun onRenameGroup(groupId: String, newName: String) {
        val current = _uiState.value.currentScenario
        val updated = current.copy(
            groups = current.groups.map { if (it.id == groupId) it.copy(name = newName) else it }
        )
        _uiState.update { it.copy(currentScenario = updated) }
        viewModelScope.launch { scenarioDao.insertScenario(updated) }
    }

    fun onAddRecipientsToGroup(groupId: String, recipients: List<Recipient>) {
        val current = _uiState.value.currentScenario
        val region = RegionSettings.effectiveRegion
        val currentGroupPhones = current.groups.find { it.id == groupId }
            ?.recipients?.map { normalizePhone(it.phoneNumber, region) }?.toSet() ?: emptySet()

        var firstDuplicate: DuplicateContactInfo? = null

        val valid = recipients.filter { r ->
            val norm = normalizePhone(r.phoneNumber, region)

            // Already in this group - silently skip
            if (norm in currentGroupPhones) return@filter false

            // Check same scenario's other groups
            val sameScenarioConflict = current.groups
                .filter { it.id != groupId }
                .firstOrNull { g -> g.recipients.any { normalizePhone(it.phoneNumber, region) == norm } }
            if (sameScenarioConflict != null) {
                if (firstDuplicate == null) {
                    firstDuplicate = DuplicateContactInfo(
                        contactName = r.name.ifBlank { r.phoneNumber },
                        scenarioName = current.name,
                        groupName = sameScenarioConflict.name
                    )
                }
                return@filter false
            }

            // Check other scenarios
            val otherScenario = _uiState.value.scenarios
                .filter { it.id != current.id }
                .firstOrNull { s -> s.allRecipients().any { normalizePhone(it.phoneNumber, region) == norm } }
            if (otherScenario != null) {
                val conflictGroup = otherScenario.groups
                    .firstOrNull { g -> g.recipients.any { normalizePhone(it.phoneNumber, region) == norm } }
                if (firstDuplicate == null) {
                    firstDuplicate = DuplicateContactInfo(
                        contactName = r.name.ifBlank { r.phoneNumber },
                        scenarioName = otherScenario.name,
                        groupName = conflictGroup?.name ?: "Unknown"
                    )
                }
                return@filter false
            }

            true
        }

        // Show popup for first duplicate found
        if (firstDuplicate != null) {
            _uiState.update { it.copy(duplicateContactError = firstDuplicate) }
        }

        if (valid.isEmpty()) return

        undoStack.addLast(current)
        _uiState.update { state ->
            state.copy(currentScenario = state.currentScenario.copy(
                groups = state.currentScenario.groups.map { g ->
                    if (g.id == groupId) g.copy(recipients = g.recipients + valid) else g
                }
            ))
        }
        autoSave()
    }

    fun dismissDuplicateError() {
        _uiState.update { it.copy(duplicateContactError = null) }
    }

    fun onRemoveRecipientFromGroup(groupId: String, recipient: Recipient) {
        undoStack.addLast(_uiState.value.currentScenario)
        _uiState.update { state ->
            state.copy(currentScenario = state.currentScenario.copy(
                groups = state.currentScenario.groups.map { g ->
                    if (g.id == groupId) g.copy(recipients = g.recipients.filter { it != recipient }) else g
                }
            ))
        }
        autoSave()
    }

    fun onGroupMessageChange(groupId: String, message: String) {
        val current = _uiState.value.currentScenario
        val group = current.groups.find { it.id == groupId } ?: return
        if (group.message == message) return
        undoStack.addLast(current)
        _uiState.update { state ->
            state.copy(currentScenario = state.currentScenario.copy(
                groups = state.currentScenario.groups.map { g ->
                    if (g.id == groupId) g.copy(message = message) else g
                }
            ))
        }
        autoSave()
    }

    // --- Recipient Management ---

    fun onRemoveRecipient(recipient: Recipient) {
        // All recipients live in groups — the legacy flat `recipients` field is always empty
        // for multi-group scenarios. Route to the group that owns this recipient so the
        // removal is actually reflected in the UI and persisted correctly.
        val owningGroup = _uiState.value.currentScenario.groups
            .firstOrNull { g -> g.recipients.any { it.phoneNumber == recipient.phoneNumber } }
            ?: return
        onRemoveRecipientFromGroup(owningGroup.id, recipient)
    }

    // --- Global Controls ---

    fun onUndo() {
        if (undoStack.isNotEmpty()) {
            val previous = undoStack.removeLast()
            _uiState.update { it.copy(currentScenario = previous, showUndoPopup = false) }
            viewModelScope.launch {
                scenarioDao.insertScenario(previous)
            }
        }
    }

    fun dismissSuccessPopup() {
        Log.i(TAG, "dismissSuccessPopup() called")
        // Cancel current monitoring first to stop it from fighting the state reset
        monitoringJob?.cancel()
        monitoringJob = null
        viewModelScope.launch {
            queueManager.hardReset()
        }
        EmergencySendingService.stopService(app)
        sendStartTime = 0L
        _uiState.update {
            it.copy(
                isSending = false,
                showSuccessPopup = false,
                errorMessage = null,
                failedCount = 0,
                totalCount = 0,
                processedCount = 0,
                elapsedTimeSeconds = 0,
                completionStats = null,
                manualCountdownSeconds = 5,
                isManualCountdownActive = false,
                lastSendCompletedAt = System.currentTimeMillis()
            )
        }
        // Restart monitoring so the next send is tracked correctly
        startMonitoring()
    }

    // --- Manual Send & Security ---

    fun initiateManualSend() {
        // Check if there are ANY valid, unlocked scenarios to send
        val validScenarios = _uiState.value.scenarios.filter { it.isValid() && !it.isLocked }
        if (validScenarios.isEmpty()) {
            Log.i(TAG, "[LOCKOUT] Manual send blocked - no valid unlocked scenarios available.")
            return
        }
        // Cooldown is bypassed in debug mode (messages aren't actually sent)
        val skipCooldown = _uiState.value.isDebugEnabled
        if (!skipCooldown) {
            val cooldownMs = 60_000L
            val timeSinceLast = System.currentTimeMillis() - _uiState.value.lastSendCompletedAt
            if (_uiState.value.lastSendCompletedAt > 0 && timeSinceLast < cooldownMs) {
                return // Cooldown shown in swipe bar - no error message needed
            }

            // Check abuse tracker - read current level without recording yet.
            // Recording only happens after captcha is verified (see onManualSendConfirmed)
            // so stress-tapping the button cannot accrue points without passing captcha.
            val abuseLevel = app.abuseTracker.currentAbuseLevel()
            if (abuseLevel == AbuseLevel.HARD_LOCKOUT) {
                val secsRemaining = app.abuseTracker.lockoutSecondsRemaining()
                val overrideAvail = app.abuseTracker.overrideAvailable()
                _uiState.update {
                    it.copy(
                        abuseLevel = abuseLevel,
                        abuseLockoutSecondsRemaining = secsRemaining,
                        abuseOverrideAvailable = overrideAvail,
                        showAbuseLockoutDialog = true
                    )
                }
                return
            }
            _uiState.update { it.copy(abuseLevel = abuseLevel) }
        }
        // First ever force send: skip captcha entirely - countdown starts immediately
        if (!_uiState.value.forceSendUsed) {
            viewModelScope.launch {
                settings.setForceSendUsed(true)
            }
            viewModelScope.launch {
                executeSend()
            }
            return
        }

        val captcha = manualGuard.generateNewCaptcha()
        _uiState.update { it.copy(currentCaptcha = captcha, showManualSendDialog = true, errorMessage = null) }
    }

    /** Core send execution - shared by captcha-verified and first-use (captcha-free) paths. */
    private suspend fun executeSend() {
        _uiState.update { it.copy(isManualCountdownActive = true, errorMessage = null, showManualSendDialog = false) }

        val allScenarios = _uiState.value.scenarios
        val validScenarios = allScenarios.filter { it.isValid() && !it.isLocked }
        val totalRecipients = validScenarios.sumOf { it.allRecipients().size }

        if (validScenarios.isEmpty()) {
            _uiState.update { it.copy(isManualCountdownActive = false, errorMessage = "No valid unlocked scenarios to send") }
            return
        }

        Log.i(TAG, "Manual send: ${validScenarios.size} scenario(s), $totalRecipients total recipient(s)")
        adaptiveController.reset()

        manualGuard.secureSendAll(
            scope = viewModelScope,
            scenarios = validScenarios,
            onCountdownTick = { seconds ->
                _uiState.update { it.copy(manualCountdownSeconds = seconds) }
                if (seconds > 0) {
                    viewModelScope.launch { _countdownTickFlow.emit(seconds) }
                }
                if (seconds == 0) {
                    sendStartTime = System.currentTimeMillis()
                    val newAbuseLevel = app.abuseTracker.recordForceSend()
                    _uiState.update { it.copy(
                        isSending = true,
                        isManualCountdownActive = false,
                        totalCount = totalRecipients,
                        abuseLevel = newAbuseLevel
                    ) }
                    AppLogger.log(app.database, viewModelScope, "manual_send",
                        "${validScenarios.size} scenario(s) triggered manually - $totalRecipients contact(s)")
                    for (scenario in validScenarios) {
                        // Use the same atomic lock as auto-trigger paths so a concurrent EBR/ENL
                        // trigger during the 4-second countdown cannot cause duplicate sends.
                        val rowsUpdated = scenarioDao.lockIfUnlocked(scenario.id)
                        if (rowsUpdated == 0) {
                            Log.i(TAG, "[LOCKOUT] Scenario '${scenario.name}' already locked by concurrent trigger during countdown - skipping")
                            continue
                        }
                        val locked = scenario.copy(isLocked = true)
                        if (_uiState.value.currentScenario.id == scenario.id) {
                            _uiState.update { state -> state.copy(currentScenario = locked) }
                        }
                        Log.i(TAG, "[LOCKOUT] Scenario '${scenario.name}' locked after manual send.")
                    }
                    viewModelScope.launch {
                        val historyDao = app.database.contactSendHistoryDao()
                        val region = RegionSettings.effectiveRegion
                        for (scenario in validScenarios) {
                            for (r in scenario.allRecipients()) {
                                val norm = normalizePhone(r.phoneNumber, region)
                                historyDao.recordSend(norm)
                            }
                        }
                    }
                    SmsResponseReceiver.startListening()
                    EmergencySendingService.startService(app)
                }
            }
        )
    }

    fun onAbuseOverride() {
        val success = app.abuseTracker.useOverride()
        if (success) {
            _uiState.update { it.copy(showAbuseLockoutDialog = false, abuseLockoutSecondsRemaining = 0) }
        }
    }

    fun dismissAbuseLockout() {
        _uiState.update { it.copy(showAbuseLockoutDialog = false) }
    }

    fun dismissManualSend() {
        _uiState.update { it.copy(showManualSendDialog = false) }
    }

    fun onManualSendConfirmed(captchaInput: String) {
        viewModelScope.launch {
            if (!manualGuard.verifyCaptcha(captchaInput)) {
                _uiState.update { it.copy(errorMessage = "Invalid Captcha") }
                return@launch
            }
            executeSend()
        }
    }

    fun onToggleLock(scenarioId: String) {
        viewModelScope.launch {
            val scenario = scenarioDao.getScenarioById(scenarioId)
            scenario?.let {
                if (it.isLocked) {
                    // Immediate unlock - no countdown delay
                    val unlocked = it.copy(isLocked = false)
                    scenarioDao.insertScenario(unlocked)
                    Log.i(TAG, "[LOCKOUT] Scenario '${it.name}' UNLOCKED by user.")
                    if (_uiState.value.currentScenario.id == scenarioId) {
                        _uiState.update { state -> state.copy(currentScenario = unlocked) }
                    }
                } else {
                    val locked = it.copy(isLocked = true)
                    scenarioDao.insertScenario(locked)
                    Log.i(TAG, "[LOCKOUT] Scenario '${it.name}' (id=$scenarioId) LOCKED by user.")
                    if (_uiState.value.currentScenario.id == scenarioId) {
                        _uiState.update { state -> state.copy(currentScenario = locked) }
                    }
                }
            }
        }
    }

    fun onCancelManualSend() {
        manualGuard.cancelSend()
        _uiState.update { it.copy(isManualCountdownActive = false, manualCountdownSeconds = 0) }
    }

    fun onStopSending() {
        Log.i(TAG, "onStopSending() called")
        monitoringJob?.cancel()
        monitoringJob = null
        viewModelScope.launch {
            queueManager.hardReset()
        }
        EmergencySendingService.stopService(app)
        sendStartTime = 0L
        _uiState.update {
            it.copy(
                isSending = false,
                showSuccessPopup = false,
                errorMessage = null,
                failedCount = 0,
                totalCount = 0,
                processedCount = 0,
                elapsedTimeSeconds = 0,
                completionStats = null,
                isManualCountdownActive = false
            )
        }
        startMonitoring()
    }

    // --- Block Phrases ---

    fun addBlockPhrase(phrase: String) {
        val trimmed = phrase.trim()
        if (trimmed.isBlank()) return
        viewModelScope.launch {
            app.database.blockPhraseDao().insert(BlockPhrase(phrase = trimmed.lowercase()))
        }
    }

    fun deleteBlockPhrase(blockPhrase: BlockPhrase) {
        viewModelScope.launch {
            app.database.blockPhraseDao().delete(blockPhrase)
        }
    }

    /**
     * Persists the user's chosen region (ISO 3166-1 alpha-2).
     * Pass an empty string to revert to auto-detection.
     * Affects phone number normalization and block phrase preset selection immediately.
     */
    fun setUserRegion(code: String) {
        RegionSettings.setUserRegion(app, code)
    }

    // --- Settings & Debugging ---

    fun toggleDebug(enabled: Boolean) {
        viewModelScope.launch {
            settings.setDebugEnabled(enabled)
            NotificationHelper.showDebugModeNotification(app, enabled)
        }
    }

    fun toggleForceSequential(enabled: Boolean) {
        viewModelScope.launch {
            settings.setForceSequential(enabled)
        }
    }

    fun setFailureRate(rate: Double) {
        viewModelScope.launch {
            settings.setFailureRate(rate)
        }
    }

    fun runDebugSimulation(count: Int, failureRate: Double) {
        viewModelScope.launch {
            _uiState.update { it.copy(isSending = true, totalCount = count) }
            debugSimulator.runLoadTest(count, failureRate)
        }
    }

    fun toggleKeepTrying(enabled: Boolean) {
        adaptiveController.setKeepTrying(enabled)
        _uiState.update { it.copy(isKeepTryingEnabled = enabled) }
    }

    fun toggleWideSpread(enabled: Boolean) {
        viewModelScope.launch {
            settings.setWideSpreadEnabled(enabled)
        }
    }


    /** Immediately re-checks notification listener permission. Call from ON_RESUME. */
    fun refreshNotificationPermission() {
        _uiState.update {
            it.copy(isNotificationPermissionGranted = PermissionUtils.isNotificationServiceEnabled(app))
        }
    }

    fun setTheme(theme: AppTheme) {
        viewModelScope.launch {
            settings.setTheme(theme.name)
        }
    }

    fun openResponseDashboard() {
        _uiState.update { it.copy(showResponseDashboard = true) }
    }

    fun dismissResponseDashboard() {
        _uiState.update { it.copy(showResponseDashboard = false) }
    }

    fun clearResponses() {
        viewModelScope.launch {
            responseRecordDao.clearResponsesForScenario(_uiState.value.currentScenario.id)
        }
    }

    fun clearAllResponses() {
        viewModelScope.launch {
            responseRecordDao.clearAllResponses()
        }
    }

    fun clearAllContacts() {
        undoStack.addLast(_uiState.value.currentScenario.copy())
        _uiState.update { state ->
            state.copy(currentScenario = state.currentScenario.copy(
                recipients = emptyList(),
                groups = state.currentScenario.groups.map { it.copy(recipients = emptyList()) }
            ))
        }
        autoSave()
    }

    fun resendToRecipients(recipients: List<site.fysh.redrocket.model.Recipient>, scenarioId: String) {
        viewModelScope.launch {
            val scenario = scenarioDao.getScenarioById(scenarioId) ?: _uiState.value.currentScenario
            // Use group.message per group - scenario.message is a legacy flat field (blank for all
            // multi-group scenarios). Match each resend recipient to their owning group.
            val region = RegionSettings.effectiveRegion
            val recipientPhones = recipients.map { normalizePhone(it.phoneNumber, region) }.toSet()
            var enqueued = 0
            for (group in scenario.groups) {
                val groupRecipients = group.recipients.filter { normalizePhone(it.phoneNumber, region) in recipientPhones }
                if (groupRecipients.isNotEmpty() && group.message.isNotBlank()) {
                    queueManager.enqueueScenario(groupRecipients, group.message, scenarioId)
                    enqueued += groupRecipients.size
                }
            }
            if (enqueued == 0) {
                Log.w(TAG, "resendToRecipients: no recipients matched any group for scenario $scenarioId")
                return@launch
            }
            adaptiveController.reset()  // start resend in MULTI_THREADED mode regardless of prior send state
            sendStartTime = System.currentTimeMillis()
            _uiState.update { it.copy(isSending = true, totalCount = enqueued) }
            SmsResponseReceiver.startListening()
            EmergencySendingService.startService(app)
            Log.i(TAG, "Resending to $enqueued recipients for scenario $scenarioId")
        }
    }

    // --- Past Alerts ---

    fun clearPastAlerts() {
        viewModelScope.launch {
            pastAlertDao.clearAll()
        }
    }

    fun stopListening() {
        SmsResponseReceiver.stopListening()
    }

    // --- Logs ---

    fun clearLogs() {
        viewModelScope.launch {
            app.database.logEntryDao().clearAll()
        }
    }

    // --- Detection Settings ---

    fun setAlertSensitivity(sensitivity: AlertSensitivity) {
        viewModelScope.launch {
            settings.setAlertSensitivity(sensitivity.name)
        }
    }

    fun setReplyListenHours(hours: Int) {
        viewModelScope.launch {
            settings.setReplyListenHours(hours)
        }
    }

    // --- Tutorial ---

    fun startTutorial() {
        // Build cleared scenario synchronously - avoids race where the coroutine's
        // _uiState.update fires AFTER the user has already added keywords in step 2.
        val scenario = _uiState.value.currentScenario
        val initialName = scenario.name
        val cleared = scenario.copy(
            description = "",
            groups = scenario.groups.map { it.copy(name = "Default", message = "", recipients = emptyList()) }
        )
        _uiState.update { it.copy(
            showTutorial = true,
            tutorialStep = 0,
            tutorialInitialScenarioName = initialName,
            currentScenario = cleared
        ) }
        // Persist to DB in the background - UI already reflects the cleared state above.
        viewModelScope.launch { scenarioDao.insertScenario(cleared) }
    }

    fun dismissTutorial() {
        _uiState.update { it.copy(showTutorial = false, showTutorialComplete = false, tutorialStep = 0) }
        viewModelScope.launch { settings.setTutorialShown(true) }
    }

    fun advanceTutorialStep(totalSteps: Int) {
        val nextStep = _uiState.value.tutorialStep + 1
        if (nextStep >= totalSteps) {
            // Switch to the congratulations screen rather than dismissing silently
            _uiState.update { it.copy(showTutorial = false, showTutorialComplete = true) }
        } else {
            _uiState.update { it.copy(tutorialStep = nextStep) }
        }
    }

    /** Called when the user taps "Let's Go" on the tutorial complete screen. */
    fun completeTutorial() {
        _uiState.update { it.copy(showTutorialComplete = false, tutorialStep = 0) }
        viewModelScope.launch { settings.setTutorialShown(true) }
    }

    // -- Data management --

    private suspend fun currentSettingsBackup(): AppSettingsBackup {
        return AppSettingsBackup(
            theme = app.settings.theme.first(),
            alertSensitivity = app.settings.alertSensitivity.first(),
            replyListenHours = app.settings.replyListenHours.first(),
            forceSequential = app.settings.forceSequential.first(),
            wideSpreadEnabled = app.settings.wideSpreadEnabled.first(),
            userRegion = RegionSettings.userRegion.ifEmpty { null }
        )
    }

    private suspend fun autoExportBackup(scenarios: List<Scenario>, blockPhrases: List<BlockPhrase>) {
        try {
            val json = gson.toJson(ScenarioBackup(
                scenarios = scenarios,
                blockPhrases = blockPhrases.map { it.phrase },
                settings = currentSettingsBackup()
            ))
            val uriString = _uiState.value.autoBackupUri
            if (uriString.isNotEmpty()) {
                val folder = DocumentFile.fromTreeUri(app, Uri.parse(uriString))
                if (folder != null && folder.canWrite()) {
                    val file = folder.findFile("redrocket_backup.json")
                        ?: folder.createFile("application/json", "redrocket_backup")
                    if (file != null) {
                        app.contentResolver.openOutputStream(file.uri, "wt")?.use { it.write(json.toByteArray()) }
                        Log.d(TAG, "Auto-backup written to user folder: ${file.uri}")
                        return
                    }
                }
            }
            // Fallback to app-private external storage
            autoBackupFile.writeText(json)
            Log.d(TAG, "Auto-backup written to fallback: ${autoBackupFile.path}")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Auto-backup failed", e)
            _uiState.update { it.copy(userMessage = "Auto-backup failed — check storage permissions") }
        }
    }

    fun setAutoBackupFolder(uri: Uri) {
        app.contentResolver.takePersistableUriPermission(
            uri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        )
        viewModelScope.launch { app.settings.setAutoBackupUri(uri.toString()) }
    }

    fun exportScenarios(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val scenarios = app.database.scenarioDao().getAllScenariosOnce()
                val blockPhrases = app.database.blockPhraseDao().getAllOnce().map { it.phrase }
                val backup = ScenarioBackup(
                    scenarios = scenarios,
                    blockPhrases = blockPhrases,
                    settings = currentSettingsBackup()
                )
                val json = gson.toJson(backup)
                val stream = app.contentResolver.openOutputStream(uri)
                if (stream == null) {
                    _uiState.update { it.copy(userMessage = "Export failed: could not open file") }
                    return@launch
                }
                stream.use { it.write(json.toByteArray()) }
                _uiState.update { it.copy(userMessage = "Exported ${scenarios.size} scenario(s)") }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Export failed", e)
                _uiState.update { it.copy(userMessage = "Export failed") }
            }
        }
    }

    fun importScenarios(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val json = app.contentResolver.openInputStream(uri)?.use { stream ->
                    val bytes = stream.readBytes()
                    if (bytes.size > 5 * 1024 * 1024) null  // reject files > 5 MB
                    else bytes.toString(Charsets.UTF_8)
                } ?: run {
                    _uiState.update { it.copy(userMessage = "Import failed — file is too large or unreadable") }
                    return@launch
                }
                val backup = gson.fromJson(json, ScenarioBackup::class.java)
                // Gson bypasses constructors — null-safe all fields that lack non-null defaults
                val scenarios = backup?.scenarios.orEmpty().map { s ->
                    s.copy(
                        name = s.name ?: "Imported Scenario",
                        description = s.description ?: "",
                        message = s.message ?: "",
                        groups = s.groups.orEmpty().map { g ->
                            g.copy(
                                name = g.name ?: "Group",
                                message = g.message ?: "",
                                recipients = g.recipients.orEmpty()
                            )
                        }
                    )
                }
                val blockPhrases = backup?.blockPhrases.orEmpty()
                if ((backup?.version ?: 0) > BACKUP_CURRENT_VERSION) {
                    _uiState.update { it.copy(userMessage = "Backup was created by a newer version of Red Rocket. Import may be incomplete.") }
                }
                // Scenarios: REPLACE on ID conflict so existing data is updated correctly
                app.database.scenarioDao().insertScenarios(scenarios)
                // Block phrases: deduplicated by phrase text
                val existingPhrases = app.database.blockPhraseDao().getAllOnce().map { it.phrase }.toSet()
                blockPhrases
                    .filter { it !in existingPhrases }
                    .forEach { phrase -> app.database.blockPhraseDao().insert(BlockPhrase(phrase = phrase)) }
                // Settings: apply if present in backup
                backup?.settings?.let { s ->
                    s.theme?.let { app.settings.setTheme(it) }
                    s.alertSensitivity?.let { app.settings.setAlertSensitivity(it) }
                    s.replyListenHours?.let { app.settings.setReplyListenHours(it) }
                    s.forceSequential?.let { app.settings.setForceSequential(it) }
                    s.wideSpreadEnabled?.let { app.settings.setWideSpreadEnabled(it) }
                    s.userRegion?.let { RegionSettings.setUserRegion(app, it) }
                }
                if (scenarios.isNotEmpty()) {
                    settings.setLastScenarioId(scenarios.first().id)
                    _uiState.update { it.copy(currentScenario = scenarios.first()) }
                }
                _uiState.update { it.copy(userMessage = "Imported ${scenarios.size} scenario(s)") }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Import failed", e)
                _uiState.update { it.copy(userMessage = "Import failed - check file format") }
            }
        }
    }

    fun sendTestMessage(phoneNumber: String) {
        val normalized = normalizePhone(phoneNumber.trim(), RegionSettings.effectiveRegion)
        if (normalized.length < 7) {
            _uiState.update { it.copy(userMessage = "Invalid phone number") }
            return
        }
        viewModelScope.launch {
            if (_uiState.value.isSending) {
                _uiState.update { it.copy(userMessage = "Cannot send test while an emergency send is active.") }
                return@launch
            }
            val now = System.currentTimeMillis()
            val secondsSinceLast = (now - lastTestSendTime) / 1000
            if (lastTestSendTime > 0 && secondsSinceLast < 60) {
                val remaining = 60 - secondsSinceLast
                _uiState.update { it.copy(userMessage = "Please wait ${remaining}s before sending another test.") }
                return@launch
            }
            lastTestSendTime = now
            val recipient = Recipient(name = "Test Recipient", phoneNumber = normalized)
            val message = "[TEST] Red Rocket is active on this device. Emergency messaging is configured and ready."
            // Note: "test-send-<uuid>" IDs are transient and have no matching Scenario entity.
            // Responses to test messages are intentionally dropped by SmsResponseReceiver.
            adaptiveController.reset()
            // Stop the service before clearing the queue. isSending may have become false
            // while the service is still winding down (dismissSuccessPopup sets isSending=false
            // after sending the stop intent). Stopping here is idempotent and ensures the
            // service's processQueue() coroutine is not holding live references into the queue
            // we are about to repopulate.
            EmergencySendingService.stopService(app)
            app.queueManager.clearQueue()
            app.queueManager.enqueueScenario(listOf(recipient), message, "test-send-${UUID.randomUUID()}")
            EmergencySendingService.startService(app)
            AppLogger.log(app.database, app.appScope, "test_send", "Test message sent to $normalized")
        }
    }

    /** Called from the UI after a userMessage has been displayed to clear it from state. */
    fun clearUserMessage() {
        _uiState.update { it.copy(userMessage = null) }
    }

    /** Dismisses the update banner for this session. */
    fun dismissUpdate() {
        _uiState.update { it.copy(updateAvailable = null) }
    }

    fun dismissWhatsNew() {
        _uiState.update { it.copy(showWhatsNew = false) }
        viewModelScope.launch {
            settings.setLastSeenVersion(site.fysh.redrocket.BuildConfig.VERSION_NAME)
        }
    }

    // --- Preset offering ---

    fun markPresetsOffered() {
        viewModelScope.launch {
            settings.setPresetsOffered(true)
        }
    }

    override fun onCleared() {
        super.onCleared()
        debugSimulator.cancel()
    }
}

data class DuplicateContactInfo(
    val contactName: String,
    val scenarioName: String,
    val groupName: String
)

/** Final summary stats shown in the completion screen of the status popup. */
data class CompletionStats(
    val sentSuccessfully: Int,
    val requiredRetries: Int,
    val failedPermanently: Int
)

data class StatusIndicators(
    val sending: Boolean = false,
    val retrying: Boolean = false,
    val failed: Boolean = false
)

@Stable
data class MainUiState(
    val scenarios: List<Scenario> = emptyList(),
    val currentScenario: Scenario = Scenario(id = "Default", name = "Default", message = ""),
    val currentSendState: SendState = SendState.MULTI_THREADED,
    val isSending: Boolean = false,
    val processedCount: Int = 0,
    val totalCount: Int = 0,
    val failedCount: Int = 0,
    val elapsedTimeSeconds: Long = 0,
    val isKeepTryingEnabled: Boolean = true,
    val isDebugEnabled: Boolean = false,
    val isForceSequential: Boolean = false,
    val failureRate: Double = 0.0,
    val errorMessage: String? = null,
    val showSuccessPopup: Boolean = false,
    val completionStats: CompletionStats? = null,
    val currentMessageStatus: MessageStatus? = null,
    val statusIndicators: StatusIndicators = StatusIndicators(),
    val isFirstLaunch: Boolean = false,
    val isInitializing: Boolean = true,
    val isWideSpreadEnabled: Boolean = false,
    val isManualCountdownActive: Boolean = false,
    val manualCountdownSeconds: Int = 0,
    val currentCaptcha: String = "",
    val showManualSendDialog: Boolean = false,
    val showUndoPopup: Boolean = false,
    val isNotificationPermissionGranted: Boolean = false,
    val lastSendCompletedAt: Long = 0L,
    val theme: AppTheme = AppTheme.SYSTEM,
    val showResponseDashboard: Boolean = false,
    val abuseLevel: AbuseLevel = AbuseLevel.NONE,
    val showAbuseLockoutDialog: Boolean = false,
    val abuseLockoutSecondsRemaining: Long = 0L,
    val abuseOverrideAvailable: Boolean = false,
    val replyListenHours: Int = 1,
    val presetsOffered: Boolean = false,
    val duplicateContactError: DuplicateContactInfo? = null,
    val alertSensitivity: AlertSensitivity = AlertSensitivity.MEDIUM,
    val forceSendUsed: Boolean = false,
    val showTutorial: Boolean = false,
    val tutorialStep: Int = 0,
    val showTutorialComplete: Boolean = false,
    val tutorialInitialScenarioName: String = "",
    /** One-shot message for the UI to surface as a Toast. Cleared by calling clearUserMessage(). */
    val userMessage: String? = null,
    /** Non-null when a newer GitHub release is available; contains the version tag string. */
    val updateAvailable: String? = null,
    /** True when the app has just been updated and the What's New dialog should be shown. */
    val showWhatsNew: Boolean = false,
    /** Release notes fetched from GitHub for the What's New dialog. Null while loading. */
    val whatsNewBody: String? = null,
    /** URI string of the user-chosen auto-backup folder. Empty = use app-private fallback. */
    val autoBackupUri: String = ""
)

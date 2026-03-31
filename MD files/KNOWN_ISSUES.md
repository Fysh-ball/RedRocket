# KNOWN_ISSUES.md

## Purpose
Tracks known bugs, edge cases, and unresolved issues.

---

## CURRENT ISSUES

- Regional wording differences (Canada alerts) — partial fix via FalseAlarmDetector French phrases
- Bilingual alerts (EN/FR parsing) — partial fix, pre-normalized French phrases in FalseAlarmDetector
- Device restrictions (background limitations) — no warning shown yet if SMS restricted while locked
- `reorderable:0.9.6` library abandoned (2022) — will break on future Compose BOM updates; replace with official foundation drag APIs
- `kapt` in maintenance mode — migrate to KSP when Room fully supports project's Kotlin version
- stale `app/src/myapplication/` template directory — should be deleted; ExampleInstrumentedTest and ExampleUnitTest are stubs
- `onAddRecipients()` legacy flat-recipients path in MainViewModel — never called from UI, modifies deprecated field; mark `@Deprecated` or remove

---

## FIXED ISSUES (pending stable confirmation)

- **[CRITICAL] RateLimiter.kt — race condition on `lastSendTimestamp`** → replaced plain `Long` with `AtomicLong` + CAS loop; two concurrent coroutines can no longer both pass the rate check simultaneously
- **[CRITICAL] AdaptiveSendController.kt — `@Volatile` counters not atomic** → replaced `consecutiveFailures`, `consecutiveSuccesses`, `lazarusPassCount` with `AtomicInteger`; read-modify-write ops are now atomic
- **[CRITICAL] AppDatabase.kt — `fallbackToDestructiveMigration()` data-loss bomb** → removed; `exportSchema = true`; added Room schema export location to `build.gradle.kts`
- **[CRITICAL] ForceSendAbuseTracker.kt — all state in-memory, lost on process death** → added `Context` param; `points`, `lockoutEndTime`, `lockoutCount`, `overrideUsed`, `isInExtendedSlowDecay`, `lastForceSendTime` persisted to SharedPreferences; EmergencyApp initializes with `ForceSendAbuseTracker(this)`
- **[CRITICAL] EmergencySendingService.kt — `startForeground()` not called on ACTION_STOP path** → `startForeground()` now called unconditionally before any intent-action branching (Android 12+ RemoteServiceException fix)
- **[CRITICAL] EmergencySendingService.kt — `processQueue` infinite loop on coroutine cancellation** → wrapped send block in `try/finally`; `handleFailure` called via `withContext(NonCancellable)` on cancellation path so `inFlightCount` always decrements
- **[CRITICAL] MessageQueueManager.kt — mutable `MessageTask` data race** → `MessageTask` is now fully immutable (all `var` → `val`); `handleFailure` and `requeueForLazarus` create new copies via `.copy()` inside mutex
- **[HIGH] EmergencyBroadcastReceiver.kt — orphaned `CoroutineScope` per `onReceive`** → replaced `CoroutineScope(SupervisorJob() + Dispatchers.IO).launch` with `app.appScope.launch(Dispatchers.IO)`
- **[HIGH] SmsSender.kt — SMS timeout treated as success** → `TimeoutCancellationException` now returns `false`; congested-carrier timeouts correctly enter Lazarus retry
- **[HIGH] SmsResponseReceiver.kt — no SEND_SMS permission check in `sendAutoReply()`** → `ContextCompat.checkSelfPermission` check added; skips silently if permission revoked
- **[HIGH] MainViewModel.kt — `recordForceSend()` called before captcha verification** → moved to `onManualSendConfirmed()` after `verifyCaptcha()` passes; `initiateManualSend()` now calls read-only `currentAbuseLevel()` instead; first-ever (captcha-free) send still records correctly
- **[HIGH] MainViewModel.kt — `startMonitoring()` polling loop violated AGENTS.md** → replaced `while (true) + delay(500)` with `queueManager.queueStatusFlow.collect { }`; `MessageQueueManager` now emits `QueueStatus` via `queueStatusFlow: StateFlow<QueueStatus>` on every state change
- **[MEDIUM] SmsResponseReceiver.kt — over-broad SAFE matching** → `"ok"`, `"fine"`, `"good"` now use exact message equality (`text == "ok"`) instead of substring `contains()` to prevent "sounds fine" or "ok got it" false matches
- **[MEDIUM] `normalizePhone()` duplicated in 4 places** → extracted to `util/PhoneUtils.kt`; `SmsResponseReceiver`, `MainViewModel`, and `ResponseDashboard` all delegate to the shared function; inline normalization in send flow replaced
- **[BATTERY] `EmergencySendingService.notificationJob` polled `getDetailedStatus()` every 1s** → replaced with `queueManager.queueStatusFlow.collect { }` — notification updates on state change, no polling
- **[BATTERY] `MainViewModel` notification permission check polled every 2s forever** → backs off to 60s once permission is granted
- **[SAFETY] `EmergencyNotificationListener` scope could be cancelled after Android rebind** → `serviceScope` is now a `var`; recreated in `onListenerConnected()` if the prior scope was cancelled
- **[CODE QUALITY] `SmsResponseReceiver` companion `normalizePhone` alias** → removed; call sites use `util.normalizePhone` directly
- **[CODE QUALITY] `ManualSendGuard` created `new Random()` per captcha generation** → replaced with class-level `random` instance
- **[CODE QUALITY] `DebugSimulator` scope lacked `SupervisorJob()`** → added
- **[CODE QUALITY] `Group.guide` field never read or set anywhere** → removed
- **[CODE QUALITY] `ContactSendHistoryDao.getAll()` and `getRecentSend()` never called** → removed
- **[CODE QUALITY] `grayTheme` dead code** → removed `grayTheme` parameter from `EmergencyAppTheme`, removed unreachable `GrayColorScheme` branch, cleaned `MainActivity`
- **[DOCS] ARCHITECTURE.md failure threshold said 5, code is 3** → fixed to 3
- **[DOCS] ARCHITECTURE.md SmsSender timeout said "assumed success"** → fixed to "treated as failure (Lazarus will retry)"
- **[DOCS] ARCHITECTURE.md FalseAlarmDetector missing Step 4 (RED_PHRASES)** → corrected to 8-step engine with accurate step list
- **[CRITICAL] ForceSendAbuseTracker.kt — `lastDecayTime` not persisted** → added `KEY_LAST_DECAY` constant; `lastDecayTime` now initialised from and saved to SharedPreferences so decay is accurate after process death
- **[CRITICAL] SmsResponseReceiver.kt — `listenStartTime` lost on process death** → added SharedPreferences persistence, `listenStartTimeFlow: StateFlow<Long>` companion property, and `init(context)` called from `EmergencyApp.onCreate()`; window is restored on restart if still valid
- **[CRITICAL] MainViewModel.kt — `resendToRecipients()` used `scenario.message` (blank for multi-group)** → now iterates `scenario.groups`, matches recipients to their group, uses `group.message` per group
- **[HIGH] ResponseDashboard.kt — `LaunchedEffect(Unit)` polling loop for listening state** → replaced with `SmsResponseReceiver.listenStartTimeFlow.collectAsState()` + `LaunchedEffect(listenStartTime)` ticker that only runs while a window is active
- **[HIGH] MainScreen.kt — cooldown timer ran at 4Hz with `LaunchedEffect(Unit)`** → changed to `LaunchedEffect(uiState.lastSendCompletedAt)` with `delay(1000L)`; ticker now only runs during an active cooldown
- **[HIGH] MainScreen.kt — BrowserTabBar used solid `primary` background (AGENTS.md violation)** → changed to `primary.copy(alpha = 0.15f)`; text changed from `onPrimary` to `onSurface`
- **[HIGH] SmsResponseReceiver.kt — PII (recipient name + phone) logged in production** → wrapped with `BuildConfig.DEBUG`; production log omits name and number
- **[HIGH] EmergencySendingService.kt — `SmsDeliveryReceiver.pendingSent` not cleared on destroy** → `pendingSent.clear()` added to `onDestroy()` to release stale callbacks
- **[MEDIUM] MainUiState — unstable type causing unnecessary recompositions** → added `@Stable` annotation
- **[MEDIUM] EmergencyNotificationListener.kt — no `CoroutineExceptionHandler` on scope** → added handler that logs uncaught exceptions; applied to both initial scope and scope recreated on rebind
- **[MEDIUM] RecipientsInput.kt — `contentResolver.query()` used null projection (loads all columns)** → changed to explicit `arrayOf(DISPLAY_NAME, NUMBER)` to prevent OOM on large contact lists

- Detection inconsistency (some alerts not triggering) → FalseAlarmDetector fully rewritten; Hard Override fires on trusted source + EXTREME_DANGER_PHRASES regardless of keyword match
- Amber alerts triggering system → Step 0 AMBER_BLOCK added to FalseAlarmDetector before all other steps
- Dashboard hiding bug (Logs/Alert History disappearing during listening) → removed isCurrentlyListening gate; sections always in layout
- Input system instability → message input replaced with ModalBottomSheet (keyboard-anchored); trigger keyword input replaced with ModalBottomSheet
- UI alignment drift (buttons) → trigger preset button fixed to 56dp matching recipients/upload buttons; emoji icons replaced with vector icons (Icons.Filled.Bolt); button row verticalAlignment fixed to CenterVertically
- Logs not collapsible → user-controlled collapse added to Logs section (default collapsed)
- "You're hearing another device" in Alert History → content filter added in EmergencyNotificationListener.onNotificationPosted before processNotification; skips known system audio routing notifications
- Tab highlight missing → blue rectangular highlight restored on selected tab (primary.copy(alpha=0.15f) background)
- Dashboard standby layout → wrapped in Surface card with improved spacing and contextual subtext
- Theme dropdown too small → Button height increased to 52dp
- User Manual missing → USER MANUAL section added to SettingsDialog with expandable accordion cards (Quick Start, Scenarios, Trigger Keywords, Dashboard, FAQ) and Replay Tutorial button
- First-time tutorial not skippable → Skip setup button added to WelcomePage and PermissionsPage in FirstLaunchScreen

---

## EDGE CASES

- Alerts without "test" indicator
- Multiple alerts in short time
- Messages from non-recipient contacts
- Device restrictions (background limitations)

---

## RULE

When fixing:
- Document the fix here
- Remove issue only when confirmed stable

---

## FINAL PRINCIPLE

What gets tracked gets fixed.

# Red Rocket v2.02 Beta — Change Log

---

## Session: 2026-04-04 (Block Phrases UI + Alert History Fixes + Dependency Modernisation)

### Block Phrases — moved into Alert Filters section, same card as Activation Keywords
- **Files:** `ui/TriggerInput.kt`, `ui/MainScreen.kt`, `ui/SettingsDialog.kt`, `ui/TutorialOverlay.kt`
- Block Phrases UI promoted from Settings into the same `SectionCard` as Activation Keywords, as a clearly labelled sub-section.
- Section card renamed: `"Trigger"` → `"Alert Filters"`.
- Keyword sub-section renamed: `"Alert Keywords"` → `"Activation Keywords"`.
- Block Phrase chips use `errorContainer`/`onErrorContainer` colors to distinguish visually from green keyword chips.
- Info button (`Icons.Default.Info`, secondary color) opens an `AlertDialog` explaining what block phrases do. Box size unchanged.
- `KeywordAddSheet` parameterised with `title` and `placeholder` — reused for both keyword and block phrase input.
- Tutorial step 2 updated: title `"3 / 6: Alert Filters"`, body covers both Activation Keywords and Block Phrases.
- SettingsDialog: Block Phrases section removed; User Manual updated to describe "Alert Filters" (Activation Keywords + Block Phrases).

### Alert History — fixed two bugs introduced in prior sessions
- **File:** `service/EmergencyNotificationListener.kt`
- **Bug 1 (phantom entries):** `hadKeywordMatch` variable caused non-EAS notifications to be logged whenever keywords matched, even if the scenario was locked/invalid and never fired. Removed `hadKeywordMatch` entirely; non-EAS entries now only written when `triggeredCount > 0`.
- **Bug 2 (WEA not logged):** `isEmergencyAlertPackage()` gated on `detectionSucceeded` — partial detection (e.g. Personal Safety app found, WEA package missed) set `detectionSucceeded = true` but left the WEA package absent from `detectedPackages`, silently excluding it. Fixed: `isEmergencyAlertPackage()` now always returns `packageName in ALL_KNOWN_PACKAGES || packageName in detectedPackages`.
- Added `"EMERGENCY ALERT"` to `looksLikeEASContent()` content-based fallback.

### Dependency modernisation
- **kapt → KSP** (`gradle/libs.versions.toml`, `app/build.gradle.kts`): replaced `id("kotlin-kapt")` with `alias(libs.plugins.ksp)`, `kapt(room.compiler)` with `ksp(room.compiler)`, and `javaCompileOptions { annotationProcessorOptions { ... } }` with top-level `ksp { arg(...) }`. KSP version `2.0.21-1.0.25` added to version catalog.
- **`reorderable:0.9.6` → `sh.calvin.reorderable:2.4.0`** (`GroupsSection.kt`, `ScenarioDropdown.kt`): migrated to maintained fork. API changes: `Modifier.reorderable()` removed (not needed in 2.x); `detectReorder()` replaced with `.draggableHandle(onDragStopped = {...})`; explicit `lazyListState` passed to `rememberReorderableLazyListState`.

### Dead code removal
- **File:** `ui/MainViewModel.kt`
- Removed `onAddRecipients()` — was modifying the deprecated flat `Scenario.recipients` field and was never called from any UI composable.

---

## Session: 2026-03-30 (Alert History — keyword-gated logging + input box polish)

### Alert History — correct logging rules per source
- **Files:** `service/EmergencyNotificationListener.kt` (two passes this session)
- **Rules implemented:**
  1. **Cell broadcasts (WEA/EAS/CMAS/ETWS)** — `EmergencyBroadcastReceiver` already logs ALL unconditionally before evaluation, regardless of type (test, AMBER, presidential, etc.). No change needed here. Source = `"cell_broadcast"`.
  2. **EAS companion notifications** — notifications from known emergency alert packages (`EmergencyPackageDetector`): always logged. Source = `"alert"`.
  3. **Wide-spread ("listen to all") app notifications** — logged ONLY when `triggeredCount > 0`, i.e., the notification actually caused the system to send. Keyword hits that were blocked by lock/invalidity, and non-matching notifications, are silently discarded. Source = `"notification_wide"`.
- **Problem fixed:** Previous version gated on `keywordMatched` (any keyword hit, even blocked ones) which still let non-triggering notifications through. Changed gate to `triggeredCount > 0`.
- Removed now-unused `var keywordMatched` flag.

### Hint text style — Trigger and Message boxes match Recipients
- **Files:** `ui/TriggerInput.kt`, `ui/MessageInput.kt`
- Changed hint/placeholder style from `bodyMedium` + `onSurfaceVariant.copy(alpha = 0.38f)` to `bodyLarge` + `onSurfaceVariant` (no alpha) — matching the Material3 TextField default used by the Recipients box.
- Message body text (non-empty state) also updated to `bodyLarge` for consistency.

---

## Session: 2026-03-30 (Input Box Polish — Scrollbar + Scroll Isolation + Uniform Sizing)

### RecipientsInput — scrollbar, scroll isolation, uniform min height
- **File:** `ui/RecipientsInput.kt`
- **Problem:** Recipients box was missing the scrollbar indicator, scroll events inside the box also scrolled the outer page, and the box had no minimum height (could collapse smaller than the other two).
- **Fix:**
  - Added `val chipScrollState = rememberScrollState()` (named, so the `drawWithContent` scrollbar lambda can read it).
  - Added `blockParent` `NestedScrollConnection` (`onPostScroll` returns `available`) so the outer page Column never receives scroll events while the user's finger is inside the box.
  - Column modifier chain: `padding(8.dp)` → `heightIn(min = 56.dp, max = 120.dp)` → `nestedScroll(blockParent)` → `drawWithContent { scrollbar }` → `verticalScroll(chipScrollState)`.
  - Scrollbar drawn identically to `InputBoxContainer`: 3dp wide rounded rect, `Color.Gray.copy(alpha = 0.45f)`, right-edge offset, only visible when content overflows.
  - New imports: `drawWithContent`, `CornerRadius`, `Offset`, `Size`, `NestedScrollConnection`, `NestedScrollSource`, `nestedScroll`.
- All three input boxes (Trigger, Message, Recipients) now have identical scroll behavior, scrollbar style, and size envelope (`min 56dp`, `max 120dp`).

---

## Session: 2026-03-29 (Alert History Fixes + Input Box Sizing + UX Polish)

### Alert History Fixes

#### Only triggerable notifications logged to history
- **File:** `service/EmergencyNotificationListener.kt`
- **Problem:** ALL notifications (including battery/charging/audio system notifications) were unconditionally logged to PastAlert before any filtering. This caused irrelevant device status notifications to appear in Alert History even with wide-spread mode off.
- **Fix:** PastAlert insert moved after the mode check. Only logged if `isSystemEmergencyAlert || wideSpreadEnabled`.

#### Source labels corrected
- **Files:** `service/EmergencyNotificationListener.kt`, `ui/PastAlertsDialog.kt`, `ui/ResponseDashboard.kt`
- **Problem:** System emergency alert notifications were stored with source `"notification"` and displayed as "Notification" — but they are alerts. Wide-spread app notifications fell through to `else -> "Alert"` in `DashboardAlertCard`, showing non-alert notifications as "Alert".
- **Fix:**
  - Source for system emergency alerts changed from `"notification"` to `"alert"` in the listener.
  - Label mappings updated in both dialog and dashboard: `"alert"/"notification"` → "Alert", `"notification_wide"` → "App Notification", `"cell_broadcast"` → "Cell Broadcast".
  - Backward compat: existing records with source `"notification"` still display as "Alert".
  - `computeAlertSeverity` updated: `"alert"` and `"notification"` sources default to RED (same as cell broadcasts).

### Input Box UX — No auto-preset on empty trigger tap
- **File:** `ui/TriggerInput.kt`
- **Problem:** Tapping an empty trigger box automatically opened the preset picker dialog. Users who wanted to type a custom keyword had to dismiss the preset dialog first.
- **Fix:** Empty trigger box tap now always opens the keyword add sheet. The preset picker is only reachable via the `+` button.

### Input Box Sizing — All three boxes now match

#### Uniform sizing: Trigger, Message, Recipients surfaces identical
- **Files:** `ui/TriggerInput.kt`, `ui/MessageInput.kt`, `ui/RecipientsInput.kt`
- All three surfaces now share the same structure:
  - Modifier: `weight(1f)` + `padding(vertical = 4.dp)`
  - Shape: `RoundedCornerShape(12.dp)`
  - Border: `BorderStroke(1.dp, MaterialTheme.colorScheme.outline)` (no alpha)
  - Inner: `Column(padding(8.dp))` with `heightIn(min = 48.dp, max = 120.dp)` + `verticalScroll`
- Min height (48dp content + 16dp padding = ~64dp surface) matches the recipients TextField intrinsic height.
- `MessageInput` border corrected from `outline.copy(alpha = 0.5f)` → `outline` to match recipients.
- `MessageInput` removed `maxLines = 6` limit — container scroll handles long messages.

#### Scrollable containers — expand up to 120dp then scroll internally
- **Files:** `ui/TriggerInput.kt`, `ui/MessageInput.kt`, `ui/RecipientsInput.kt`
- Each input box expands naturally as content grows; once content would push the outer page scroll, the box caps at 120dp and its interior scrolls instead.
- Prevents the main screen from requiring excessive scrolling to reach recipients or the send button.

#### Upload button icon size matches "+" button
- **File:** `ui/MessageInput.kt`
- `UploadFile` icon: `size(24.dp)` → `size(28.dp)` to match the `Add` icon in `RecipientsInput`.

---

## Session: 2026-03-29 (Tutorial UX + Build Fix + Refactor)

### Bug Fixes

#### Build Fix — `CompositingStrategy` wrong import
- **File:** `ui/TutorialOverlay.kt`
- **Problem:** `androidx.compose.ui.graphics.layer.CompositingStrategy` and `androidx.compose.ui.graphics.CompositingStrategy` are two distinct types. The `graphicsLayer { compositingStrategy = ... }` property expects the latter. Using the `.layer.` subpackage caused a type mismatch compile error.
- **Fix:** Import changed to `androidx.compose.ui.graphics.CompositingStrategy`.

---

### Tutorial UX Fixes

#### 1. Spotlight corners now match element shapes
- **File:** `ui/TutorialOverlay.kt`
- **Problem:** The spotlight overlay was cutting a rectangular hole, leaving visible corners on rounded UI elements.
- **Fix:** Replaced the 5-region rectangular dim layout with a single `Canvas` using `BlendMode.Clear` + `CompositingStrategy.Offscreen` to punch a true transparent rounded hole. Per-step corner radii match the actual element shape:
  - Steps 0, 1 (ScenarioDropdown): `8dp`
  - Step 2 (TriggerInput): `12dp`
  - Step 3 (GroupsSection header): `8dp`
  - Step 4 (MessageInput): `12dp`
  - Step 5 (Dashboard tab): `10dp`
- Touch passthrough preserved via invisible 5-region touch-blocking composables behind the spotlight gap.

#### 2. Tutorial step 0 advances on dropdown CLOSE, not open
- **Files:** `ui/ScenarioDropdown.kt`, `ui/MainScreen.kt`
- **Problem:** Tutorial was advancing step 0 the moment the dropdown opened, before the user could interact with it.
- **Fix:** `ScenarioDropdown` gained an `onDropdownClosed` callback fired when the dialog dismisses. `MainScreen` uses a `tutorialDropdownClosed` state flag + `LaunchedEffect` to advance only after the dropdown closes.

#### 3. Tutorial step 2 advances only after keyword sheet is dismissed
- **Files:** `ui/TriggerInput.kt`, `ui/MainScreen.kt`
- **Problem:** `isComplete` check for step 2 fired while the keyword sheet was still open.
- **Fix:** `TriggerInput` gained an `onSheetDismissed` callback fired when the `KeywordAddSheet` or `PresetPickerDialog` closes. `isComplete` for step 2 removed from `TutorialSpotlightOverlay`; advancement is callback-driven only.

#### 4. Preset keywords disappearing during tutorial (race condition)
- **File:** `ui/MainViewModel.kt`
- **Problem:** `startTutorial()` cleared the scenario inside a coroutine, so the DB write could race with the user selecting a preset — the preset would be overwritten by the delayed clear.
- **Fix:** `_uiState.update { currentScenario = cleared }` moved to run synchronously before the coroutine. Only the `scenarioDao.insertScenario()` DB persist remains inside `viewModelScope.launch`.

#### 5. Confetti renders above the congratulations card
- **File:** `ui/TutorialOverlay.kt`
- **Problem:** The confetti `Canvas` was not the last child in `BoxWithConstraints`, so the card rendered on top of it.
- **Fix:** Confetti `Canvas` moved to be the last child (highest z-order). `Canvas` has no pointer input so the card's button remains fully clickable beneath it.

---

### Refactor — MainScreen.kt split into separate files

`MainScreen.kt` was ~1380 lines containing multiple unrelated composables. All self-contained UI components extracted to their own files. `MainScreen.kt` now contains only the `MainScreen` composable (~340 lines).

| New File | Composables Extracted |
|---|---|
| `ui/BrowserTabBar.kt` | `BrowserTabBar` |
| `ui/SectionCard.kt` | `SectionCard` |
| `ui/SwipeToConfirm.kt` | `SwipeToConfirm` |
| `ui/SendDialogs.kt` | `AbuseLockoutDialog`, `ManualSendDialog` |
| `ui/TutorialOverlay.kt` | `TutorialSpotlightOverlay`, `TutorialCard`, `TutorialCompleteOverlay`, `ConfettiParticle` |

---

## Session: 2026-03-28/29 (Code Review — Critical / High / Medium fixes)

### Critical Fixes
- **RateLimiter.kt** — `AtomicLong` + CAS loop for `lastSendTimestamp` (was non-atomic)
- **AdaptiveSendController.kt** — `AtomicInteger` for failure/success/lazarus counters
- **AppDatabase.kt** — Removed `fallbackToDestructiveMigration()`, enabled `exportSchema = true`, added schema path in `build.gradle.kts`
- **ForceSendAbuseTracker.kt** — Takes `Context`, persists 6 state fields to `SharedPreferences`; `lastDecayTime` also persisted (`KEY_LAST_DECAY`)
- **EmergencySendingService.kt** — `startForeground()` called before `ACTION_STOP` branch; `try/finally` with `withContext(NonCancellable)` in `processQueue`; `SmsDeliveryReceiver.pendingSent.clear()` added to `onDestroy()`
- **MessageQueueManager.kt** — `MessageTask` fully immutable; `handleFailure`/`requeueForLazarus` use `.copy()`

### High Fixes
- **EmergencyBroadcastReceiver.kt** — Uses `app.appScope` instead of orphaned scope
- **SmsSender.kt** — Timeout returns `false` (triggers Lazarus retry)
- **SmsResponseReceiver.kt** — `SEND_SMS` permission check before auto-reply; `listenStartTime` persisted to `SharedPreferences`; `listenStartTimeFlow: StateFlow<Long>` added; `init(context)` called from `EmergencyApp.onCreate()`
- **MainViewModel.kt** — `recordForceSend()` moved to after captcha passes; polling loop replaced with `queueStatusFlow.collect { }`; `resendToRecipients()` iterates `scenario.groups`, matches by normalized phone, uses `group.message` per group

### Medium Fixes
- **SmsResponseReceiver.kt** — `"ok"/"fine"/"good"` exact-match only; PII log guarded with `BuildConfig.DEBUG`; companion `normalizePhone` alias removed
- **PhoneUtils.kt** (new) — `normalizePhone()` consolidated from 4 duplicates
- **EmergencyNotificationListener.kt** — All notifications logged to `PastAlert` before filtering; `CoroutineExceptionHandler` added to scope (both initial and rebind)
- **RecipientsInput.kt** — Contacts query uses explicit projection `arrayOf(DISPLAY_NAME, NUMBER)`

### Optimization Pass (2026-03-29)
- **EmergencySendingService.notificationJob** — Replaced 1s polling with `queueStatusFlow.collect`
- **MainViewModel** notification permission poll — Backs off to 60s once granted
- **EmergencyNotificationListener** scope rebind safety — `var scope`, recreated on rebind
- **ManualSendGuard** — Class-level `Random` instance (was `new Random()` per call)
- **DebugSimulator** — Added `SupervisorJob()` to scope
- **Group.guide** dead field — Removed
- **ContactSendHistoryDao** dead read methods — Removed
- **grayTheme** dead code — Removed from `Theme.kt` and `MainActivity`
- **ResponseDashboard** — Polling loop replaced with `listenStartTimeFlow.collectAsState()` + `LaunchedEffect` ticker
- **MainScreen** cooldown timer — `LaunchedEffect(uiState.lastSendCompletedAt)` + `delay(1000L)`, no longer runs when not in cooldown
- **MainUiState** — `@Stable` annotation added
- **ARCHITECTURE.md** — 3 doc fixes (failure threshold, timeout behavior, FalseAlarmDetector step count)

---

## Known Open Items

- Regional wording differences (Canada alerts) — partial fix via FalseAlarmDetector French phrases
- Bilingual alerts (EN/FR parsing) — partial fix, pre-normalized French phrases in FalseAlarmDetector
- Device restrictions (background limitations) — no warning shown yet if SMS restricted while locked

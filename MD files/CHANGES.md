# Red Rocket v2.02 Beta - Change Log

---

## Session: 2026-04-08 (v2.0.7 — Bug fixes + version label + What's New dialog)

### service/EmergencyNotificationListener.kt — dead branches removed
- The `else { -1L }` branch in `easAlertRowId` and the `if (!isSystemEmergencyAlert && triggeredCount > 0)` block at the end of `processNotification()` were unreachable — the function hard-returns at line 119 for non-emergency packages, so both branches could never execute. Removed to eliminate misleading code.

### ui/MainViewModel.kt — onRemoveRecipient rerouted to correct data model
- `onRemoveRecipient()` was modifying the legacy flat `recipients` field (which is always empty for multi-group scenarios) instead of the recipient's owning group. Removals would silently write a stale record to the DB and corrupt the undo stack without any visible effect in the UI. Now routes to `onRemoveRecipientFromGroup()` on the correct group.

### ui/MainScreen.kt — registerReceiver crash on Android 14+
- Dynamic `BroadcastReceiver` for power-save mode changes was registered without the required `RECEIVER_EXPORTED` flag on API 34+. Android 14 throws a `SecurityException` on `registerReceiver()` without an explicit exported/not-exported flag. Added `RECEIVER_EXPORTED` flag on API 34+ (system broadcasts require it).

### ui/MainScreen.kt — version number displayed next to "Alert System" title
- App version (e.g. "v2.0.7") now appears to the right of the "Alert System" heading so users can always see which version they are running.

### ui/MainScreen.kt + ui/MainViewModel.kt + util/UpdateChecker.kt + utils/AppSettings.kt — What's New dialog on update
- On first launch after an update, a dialog shows the release notes for the installed version fetched live from the GitHub releases API. No changelog text is hardcoded — the dialog content comes from the GitHub release body, so it is always accurate and requires no code changes to update. Shows a spinner while loading and locks the "Got it" button until content arrives. Dismissed state is persisted in DataStore so the dialog appears exactly once per version upgrade.

---

## Session: 2026-04-07 (v2.0.6 — Deep audit fixes: concurrency, correctness, reliability)

### service/EmergencyBroadcastReceiver.kt — CancellationException no longer swallowed
- Added `catch (e: CancellationException) { throw e }` before the broad exception handler, matching the pattern already in SmsResponseReceiver. Coroutine cancellation signals were previously silently discarded.

### service/EmergencyBroadcastReceiver.kt + service/EmergencyNotificationListener.kt — atomic scenario locking
- Both trigger paths now use `scenarioDao.lockIfUnlocked()` instead of `insertScenario(copy(isLocked=true))`. If a cell broadcast and a notification listener event fire within milliseconds of each other (same WEA alert triggering both), only one path wins the atomic SQL UPDATE and enqueues messages. The other path sees 0 rows updated and skips — preventing duplicate sends to all recipients.

### model/ScenarioDao.kt — lockIfUnlocked() added
- New `@Query("UPDATE scenarios SET isLocked = 1 WHERE id = :id AND isLocked = 0")` DAO method returns rows affected, enabling atomic conditional locking across concurrent trigger paths.

### ui/MainViewModel.kt — manual-send countdown locking race closed
- `executeSend()` now uses `scenarioDao.lockIfUnlocked()` instead of `insertScenario(copy(isLocked=true))` when locking scenarios at tick 0. If an auto-trigger fires during the 4-second countdown, the countdown no longer also enqueues the same scenario — preventing duplicate sends during a real emergency.

### ui/MainViewModel.kt — resendToRecipients resets adaptive controller
- `resendToRecipients()` now calls `adaptiveController.reset()` before starting the service. Previously, if a prior send ended in SEQUENTIAL mode, the resend would start at degraded throughput.

### service/ManualSendGuard.kt — enqueue protected against ViewModel cancellation
- The message enqueue loop is now wrapped in `withContext(NonCancellable)`. If the ViewModel is destroyed during the 4-second countdown (app backgrounded under memory pressure), the enqueue and service start now complete regardless.

### service/SmsResponseReceiver.kt — listen window restored correctly after process death
- `init()` now reads the user-configured listen hours from SharedPreferences before validating the saved timestamp. Previously, a user who configured a 2 or 3-hour window would have their active window incorrectly expired on process restart because the hardcoded 1-hour default was used for comparison.
- `setListenWindowHours()` now persists the configured hours to SharedPreferences so `init()` can restore them synchronously on next launch.
- `init()` uses double-checked locking (`synchronized`) to prevent a race between the main thread and a Binder-thread BootReceiver calling init() concurrently.
- Notification channel now created once in `init()` instead of on every incoming SMS response (removing a Binder IPC call per response).

### service/EmergencySendingService.kt — survives START_STICKY restart
- `serviceScope` changed from `val` to `var`. If Android kills and restarts the service under memory pressure, the scope is now recreated at the top of `onStartCommand()` before any coroutines are launched. Previously, launching on the cancelled scope silently failed.
- Completion notification PendingIntent uses `requestCode = 2` and `FLAG_UPDATE_CURRENT`, distinct from the foreground open-app (requestCode 0) and stop-action (requestCode 1) intents.

### utils/ForceSendAbuseTracker.kt — thread safety
- All public and private methods annotated `@Synchronized`. Concurrent calls from a rapid double-tap racing through the `isSending` guard could previously corrupt the abuse point total or fire duplicate lockout triggers.

### util/RegionSettings.kt — double-checked locking in init()
- Same pattern applied as SmsResponseReceiver: `synchronized(RegionSettings::class.java)` with `prefs` assigned last. Prevents double-initialization from concurrent main-thread and Binder-thread callers.

### service/SmsSender.kt — DateTimeFormatter replaces SimpleDateFormat
- `SimpleDateFormat` is not thread-safe and was constructed fresh per message. Replaced with a class-level `DateTimeFormatter` instance (thread-safe, available from API 26 which matches minSdk).

### util/UpdateChecker.kt — HTTP connection always released
- `HttpURLConnection.disconnect()` now called in a `finally` block. Previously, any exception before the input stream was opened leaked the underlying socket.

### model/Recipient.kt — isValid() digit count excludes '+' prefix
- The 7-character minimum was checked against `cleanNumber.length`, which includes the `+` sign. A number like `+12345` (6 digits + prefix) would pass with only 6 actual digits. Fixed to count digits only via `count { it.isDigit() }`.

---

## Session: 2026-04-07 (v2.0.5 — UX and accessibility improvements)

### ui/BrowserTabBar.kt — inactive tab contrast improved
- Inactive tab text opacity raised from 0.7 to 0.85. The previous dimming was too subtle to distinguish selected vs unselected tabs in bright ambient light.
- Font sizes increased: selected tab 14sp → 16sp, inactive tab 15sp → 16sp (previously 14sp selected was smaller than 15sp inactive — now both are 16sp with bold weight differentiating selected state).

### ui/RecipientsInput.kt — recipient chip close button touch target enlarged
- Close button on recipient chips resized from 32dp to 40dp with padding increased from 8dp to 12dp, meeting the Material Design 48dp minimum touch target guideline.

### ui/TriggerInput.kt — keyword and block-phrase chip close button touch target enlarged
- Same fix as RecipientsInput applied to KeywordChip and BlockPhraseChip close buttons (32dp → 40dp, padding 8dp → 12dp).

### ui/SettingsDialog.kt — info box text sizes increased
- Four informational text blocks (battery optimization warning, import info, backup folder label, "Change" button label) changed from `bodySmall` (12sp) to `bodyMedium` (14sp) for readability.

### ui/StatusPopup.kt — jargon removed from send mode labels
- "Sequential – Degraded Service" → "Sequential – Reduced Speed"
- "Lazarus – Retrying Failed Sends" → "Retrying – Resending Failed Messages"
- "Keep Trying (Lazarus Mode)" → "Keep Retrying Failed Messages"

### ui/TutorialOverlay.kt — tutorial text readability and plain language
- Tutorial body text style changed from `bodySmall` to `bodyMedium`; line height increased from 18sp to 22sp.
- Skip button text style changed from `labelSmall` to `labelMedium`; vertical padding increased from 2dp to 4dp.
- "Long-press this bar" → "Press and hold this bar" (Step 1)
- "Long-press the group pill" → "Press and hold the group button" (Step 3)
- "recipients" → "contacts" (Step 3) — consistent with the rest of the UI

---

## Session: 2026-04-07 (v2.0.4 — Stress-test bug fixes)

### service/SmsResponseReceiver.kt — parseResponseCode: digit takes priority over keywords
- A single valid digit (1, 2, or 3) in a message now returns that code immediately without falling through to keyword matching. Previously, a reply like "help 1" would match the "help" keyword and return code 3 (URGENT) instead of code 1 (Safe) — incorrectly overriding the contact's explicit numeric reply.

### service/SmsResponseReceiver.kt — notification ID collision
- Response notification IDs now use `AtomicInteger.getAndIncrement()` instead of `(System.currentTimeMillis() % Int.MAX_VALUE).toInt()`. Two responses arriving in the same millisecond could generate the same ID, causing one notification to silently replace the other.

### ui/MainViewModel.kt — import file size guard
- `importScenarios()` now rejects files larger than 5 MB before reading into memory. A malformed or oversized backup file could previously cause an OOM crash during `readBytes()`.

### ui/MainViewModel.kt — auto-backup failure now visible to user
- When `autoExportBackup()` fails (e.g., storage permission revoked, folder deleted), the error is now surfaced to the user as a status message instead of being silently swallowed after logging.

---

## Session: 2026-04-07 (v2.0.3 — Detection Offline banner shows instantly)

### ui/MainViewModel.kt — isNotificationPermissionGranted default changed to false
- Default was `true`, so on a fresh install (or any launch before the first poll ran) the "Detection Offline" banner was invisible until the polling loop's first iteration fired. Changed to `false` so the banner shows immediately on launch and disappears only once the permission is confirmed.

### ui/MainViewModel.kt — refreshNotificationPermission() added
- New public function that synchronously re-checks notification listener permission and updates `uiState` immediately. Called from the Screen on `ON_RESUME`.

### ui/MainViewModel.kt — notification permission poll interval reduced
- "Already granted" poll interval reduced from 60s to 30s. If permission is silently revoked (e.g. by Android or a device admin), the banner now appears within 30s instead of up to 60s.

### ui/MainScreen.kt — ON_RESUME re-check for notification permission
- Added `DisposableEffect` lifecycle observer that calls `viewModel.refreshNotificationPermission()` on every `ON_RESUME`. When the user taps the "Detection Offline" banner, goes to Android's Notification Access settings, and returns, the banner now disappears (or stays) instantly — no polling delay.

---

## Session: 2026-04-06 (v2.0.2 — Full QA audit fixes)

### service/SmsResponseReceiver.kt — prefs write inside synchronized block
- Moved `prefs?.edit()?.putLong(KEY_LISTEN_START, now)?.apply()` from outside the `synchronized` block into it. Previously, two concurrent `startListening()` calls could interleave their `prefs.apply()` writes with the in-memory state update, leaving SharedPreferences out of sync with `listenStartTime`.

### service/SmsResponseReceiver.kt — stopListening uses commit() not apply()
- `stopListening()` now calls `.commit()` instead of `.apply()` for the SharedPreferences write. If the process dies immediately after `stopListening()`, `apply()` may not flush before death and listening would incorrectly restore on next launch.

### service/SmsResponseReceiver.kt — atomic per-contact window tracking
- Replaced `contactFirstResponseTime[normalizedSender]` get + conditional put (check-then-act race) with `contactFirstResponseTime.putIfAbsent(normalizedSender, now)`. Two Binder threads processing simultaneous SMS from the same contact can no longer both see null and both start independent 1-minute windows.

### service/SmsResponseReceiver.kt — DB timeout on scenario load
- `getAllScenariosOnce()` in `onReceive` is now wrapped in `withTimeoutOrNull(5_000L)`. A locked or slow database can no longer stall the SMS receiver indefinitely.

### service/SmsResponseReceiver.kt — CancellationException not swallowed
- Added `catch (e: CancellationException) { throw e }` before the broad `catch (e: Exception)` block. Coroutine cancellation from `app.appScope` is no longer swallowed and silently logged.
- Added `import kotlinx.coroutines.CancellationException` and `import kotlinx.coroutines.withTimeoutOrNull`.

### service/EmergencyNotificationListener.kt — DB timeout on scenario load
- `getAllScenariosOnce()` now wrapped in `withTimeoutOrNull(5_000L)`. Matches the existing timeout already applied to the block phrases query. Prevents a slow DB from stalling the notification listener.

### service/EmergencyNotificationListener.kt — locked scenario logged to AppLogger
- When a locked scenario silently skips a trigger, an `AppLogger` entry is written so the user can see why their scenario didn't fire (visible in the in-app log screen).

### service/SmsSender.kt — null SmsManager surfaced to AppLogger
- When `smsManager` is null (device cannot send SMS), an `AppLogger` entry is written. Previously this was only a logcat error, leaving the user with no in-app explanation for why all sends failed.

### model/Recipient.kt — isValid() phone number validation fixed
- Removed the tautological `.all { it.isDigit() || it == '+' }` check — always true since the string is already filtered to only digits and `+`.
- Added proper `+` position validation: `+` is only valid as an international prefix (position 0). A number like `+1234+5678` is now correctly rejected.

---

## Session: 2026-04-06 (v2.0.1 — Armed/disarmed system + trigger source fix + code review fixes)

### CRITICAL: service/EmergencyNotificationListener.kt — non-emergency apps can no longer trigger scenarios
- Added hard source filter at the top of `processNotification()`. If `isSystemEmergencyAlert` is false (notification is not from a known OEM emergency package and does not contain FCC-mandated WEA phrases), the function returns immediately before touching the database or evaluating any scenario. YouTube, social media, games, and every other non-emergency app are now unconditionally blocked regardless of keyword matches or armed state.
- Previously: keyword-based scenarios had no source check, so any app notification containing a matching word (e.g. "nuclear" in a YouTube title) would trigger a send.
- Removed the now-redundant per-scenario `continue` that was inside the loop — replaced with a single early `return` before the loop.

### service/EmergencyNotificationListener.kt — armed/disarmed gate
- Added `isArmed` check at the top of `processNotification()`. When disarmed, the function returns immediately. Cell broadcasts (`EmergencyBroadcastReceiver`) are unaffected.

### utils/AppSettings.kt — isArmed setting
- Added `IS_ARMED` DataStore key (`booleanPreferencesKey`), `isArmed: Flow<Boolean>` (default `true`), and `setArmed(Boolean)` suspend function.

### model/ScenarioBackup.kt — isArmed included in device clone
- Added `isArmed: Boolean?` to `AppSettingsBackup`. Export includes armed state; import restores it.

### ui/MainViewModel.kt — armed state wired through
- Added `isArmed: Boolean = true` to `MainUiState`.
- Added collector for `settings.isArmed` in `init`.
- Added `setArmed(Boolean)` function.
- `currentSettingsBackup()` and `importScenarios()` include `isArmed`.

### ui/MainScreen.kt — Armed/Disarmed toggle card
- Added armed/disarmed card above the scenario section. Green (`primaryContainer`) when armed, red (`errorContainer`) when disarmed.
- Disarmed subtitle: "Auto-trigger is off. Emergency broadcasts still active."

### ui/MainViewModel.kt — import bug fix
- After `importScenarios()` completes, `settings.setLastScenarioId(scenarios.first().id)` AND `_uiState.update { it.copy(currentScenario = scenarios.first()) }` are both called. This prevents the race where the DataStore write hasn't committed before the Room flow collector reads `lastScenarioId`, leaving the UI stuck on the empty default scenario.

### ui/MainViewModel.kt — Gson null-safety on import
- Imported scenarios are sanitized after Gson deserialization: `name ?: "Imported Scenario"`, `description ?: ""`, `message ?: ""`, `groups.orEmpty()`, `g.name ?: "Group"`, `g.message ?: ""`, `g.recipients.orEmpty()`. Malformed or hand-edited backup files can no longer NPE at runtime.

### ui/MainViewModel.kt — CancellationException no longer swallowed
- `autoExportBackup()`, `exportScenarios()`, and `importScenarios()` all now rethrow `CancellationException` before catching general `Exception`. Structured concurrency is preserved.
- Added `import kotlinx.coroutines.CancellationException`.

### ui/MainScreen.kt — cooldown timer no longer runs forever
- `LaunchedEffect` cooldown loop now `break`s when `(nowMs - lastSendCompletedAt) >= cooldownDuration`. Previously the loop ran indefinitely after the cooldown expired, re-triggering recomposition every second.

### ui/RecipientsInput.kt — SMS intent crash fix
- `context.startActivity(intent)` in `RecipientChip` is now wrapped in a `try/catch ActivityNotFoundException`. Tablets and restricted enterprise devices without an SMS app no longer crash.

### ui/RecipientsInput.kt — contact picker performance
- `items(filteredContacts)` now uses `key = { it.phoneNumber }`. Compose can now diff the list correctly when the search query changes instead of rebinding every visible item.

### ui/SettingsDialog.kt — DocumentFile off main thread
- `DocumentFile.fromTreeUri()` (a Binder IPC call) moved from `remember { }` on the composition thread to `LaunchedEffect + withContext(Dispatchers.IO)`. Eliminates potential jank or ANR on cold content providers.
- Added `import kotlinx.coroutines.Dispatchers` and `import kotlinx.coroutines.withContext`.

---

## Session: 2026-04-06 (Full setup clone on export/import + chip size)

### model/ScenarioBackup.kt — settings included in backup
- Added `AppSettingsBackup` data class with nullable fields: `theme`, `alertSensitivity`, `replyListenHours`, `forceSequential`, `wideSpreadEnabled`, `userRegion`.
- Added `settings: AppSettingsBackup? = null` to `ScenarioBackup`. Nullable so v1 backups (no settings block) still import cleanly.
- Bumped default `version` from 1 to 2.

### MainViewModel.kt — export includes all settings, import applies them
- Added `currentSettingsBackup()` suspend helper that reads all relevant settings from DataStore and RegionSettings.
- `exportScenarios()` and `autoExportBackup()` now include `currentSettingsBackup()` in the backup JSON.
- `importScenarios()` applies each settings field if present: theme, alertSensitivity, replyListenHours, forceSequential, wideSpreadEnabled, userRegion. Missing fields (older backups) are silently skipped.
- `BACKUP_CURRENT_VERSION` bumped to 2.

### ui/RecipientsInput.kt + ui/TriggerInput.kt — slimmer chips
- All chips (RecipientChip, KeywordChip, BlockPhraseChip): vertical padding `8dp → 4dp`, close icon `40dp → 32dp`, removed redundant `Spacer` between text and icon. Chips are visibly shorter and narrower, allowing two to fit per line in most cases.

---

## Session: 2026-04-06 (Auto-export + APK naming)

### MainViewModel.kt — auto-export backup
- Added `autoBackupFile: File` property pointing to `getExternalFilesDir(null)/redrocket_backup.json`.
- Added `autoExportBackup()` private function that serializes all scenarios and block phrases to JSON and writes to the auto-backup file silently.
- Added a debounced (`3_000L`) `combine(scenarioDao.getAllScenarios(), blockPhraseDao.getAll())` collector in `init` that calls `autoExportBackup()` whenever either flow emits. Skips write when scenarios list is empty (initial state before first insert). Runs on `Dispatchers.IO`.

### SettingsDialog.kt — auto-backup path display
- Added `autoBackupPath: String` parameter.
- Auto-backup file path shown below the import note in the Data section when non-empty.

### MainScreen.kt — passes autoBackupPath to SettingsDialog
- `viewModel.autoBackupFile.absolutePath` passed as `autoBackupPath`.

### app/build.gradle.kts — APK output filename
- Added `applicationVariants.all` block. Production release APK is now named `Red Rocket v{versionName}.apk` instead of `app-production-release.apk`. Updates automatically when `versionName` changes.

### README.md + MD files/RELEASES.md
- README rewritten to reflect v2.0 feature set while preserving the original author voice.
- RELEASES.md created to track public-facing release notes separately from internal session logs.

---

## Session: 2026-04-06 (v2.0 release — Input fixes + UI text audit + Ko-fi icon)

### ui/RecipientsInput.kt — hold-to-repeat backspace fix
- Changed `keyboardType = KeyboardType.Number` → `KeyboardType.Phone`. The numeric keypad on most Android keyboards does not fire hold-to-repeat backspace events; the phone dialer keyboard does. Backspace now works correctly on hold in the recipient number field.

### ui/RecipientsInput.kt + ui/MessageInput.kt — text selection + delete fix
- `TextRange(newValue.selection.end.coerceAtMost(filtered.length))` was creating a zero-width (collapsed) cursor, discarding any drag-to-select selection the user had made. Fixed to `TextRange(start = newValue.selection.start.coerceAtMost(filtered.length), end = newValue.selection.end.coerceAtMost(filtered.length))`. Both `start` and `end` are independently clamped so selecting text and pressing delete now works correctly in both the recipient number field and the message edit sheet.

### res/drawable/ic_kofi.xml — Ko-fi icon viewport redesign
- Mug body previously occupied x=4–17, y=8–20 of the 24×24 viewport (lower-right quadrant only), making the icon render visually smaller than the GitHub icon at the same dp size. Redesigned to fill the full viewport: body x=1–19 y=3–21 with handle path extending to x=23.5. Icon now matches the visual density of the GitHub Octocat icon.

### ui/SettingsDialog.kt — Ko-fi button text
- Button text changed to `"Wanna Buy Me a Cup of Rice? 🍚"`.

### Comprehensive UI text size audit — all major UI files
All files audited for text and icons that were too small on real devices (including S24 Ultra). Changes applied:

- **SettingsDialog.kt**: All `bodySmall` → `bodyMedium` for description texts; `labelSmall` → `bodySmall` for battery warning and import merge note; `labelMedium` → `labelLarge` for section card headers; warning icon `14dp` → `18dp`; Archive/FileOpen/Send icons `16dp` → `20dp`.
- **BrowserTabBar.kt**: Tab font `14sp/13sp` (selected/unselected) → `15sp/14sp`.
- **ScenarioDropdown.kt**: Recipient count badge `11sp` → `14sp`.
- **GroupsSection.kt**: "Recipients" and "Message" labels `labelMedium` → `bodyMedium`; SMS counter `labelSmall` → `bodySmall`; group recipient count badge `11sp` → `14sp`.
- **MainScreen.kt**: Abuse/error warning banners `labelMedium` → `bodyMedium`; warning card descriptions `12sp` → `14sp`; update banner text `13sp/11sp` → `15sp/14sp`; locked scenario description `bodySmall` → `bodyMedium`; undo/dismiss icons `16dp` → `20dp`; undo text `13sp` → `15sp`.
- **SendDialogs.kt**: Override description and carrier charge text `bodySmall` → `bodyMedium`.
- **StatusPopup.kt**: All `bodySmall` → `bodyMedium`; `labelSmall` → `bodySmall` for chip and stat card labels.
- **TriggerInput.kt**: "auto" label `10sp` → `13sp`; dial code in header `11sp` → `13sp`; `SubSectionLabel` `labelSmall` → `labelMedium`; preset name `14sp` → `15sp`; preset keywords `11sp` → `13sp`; region display name `13sp` → `15sp`; "Auto-detected" `10sp` → `13sp`; region dial code `12sp` → `14sp`.
- **ResponseDashboard.kt**: "Stop" button `13sp` → `15sp`; "Add contacts" hint `13sp` → `15sp`; contacts ready count `14sp` → `15sp`; monitoring label `12sp` → `14sp`; responded/percentage labels `13sp` → `15sp`; "Recipients (N)" header `14sp` → `15sp`; "Waiting for reply" header `labelMedium` → `bodyMedium`; no-response list `bodySmall` → `bodyMedium`; "Clear Responses" `13sp` → `15sp`; logs/history buttons `12sp` → `14sp`; stat card label `12sp` → `14sp`; recipient name `14sp` → `15sp`; phone `12sp` → `14sp`; status badge `11sp` → `13sp`; time-ago `10sp` → `12sp`; log label `12sp` → `14sp`; log timestamp `11sp` → `13sp`; log description `bodySmall` → `bodyMedium`; alert badge and date `11sp` → `13sp`; alert message `bodySmall` → `bodyMedium`; triggered label `11sp` → `13sp`.
- **LogsDialog.kt**: Header count `bodySmall` → `bodyMedium`; log entry label `13sp` → `15sp`; timestamp `11sp` → `13sp`.
- **PastAlertsDialog.kt**: Header count `bodySmall` → `bodyMedium`; all `11sp` badges/timestamps/triggered text → `13sp`.

---

## Session: 2026-04-06 (v2.0 patch — Bug fixes + scheduling feature removed)

### Scheduling feature removed
- `model/Scenario.kt`, `AppDatabase.kt`, `EmergencyBroadcastReceiver.kt`, `EmergencyNotificationListener.kt`, `ui/MainScreen.kt`, `ui/MainViewModel.kt` — scenario scheduling (time-window gate) removed entirely. WEA alerts have already alerted the device at full volume; a time-based suppression gate could silently block real emergency sends. DB version reverted from 11 back to 10; `MIGRATION_10_11` removed (v2.0 was never publicly released).

### ui/MainViewModel.kt — test send rate limit
- `sendTestMessage()` now enforces a 60-second cooldown between sends. Rapid repeated sends are blocked with a "Please wait Xs" message. Prevents the test button being used to spam a number.

### model/ResponseRecordDao.kt — fix duplicate rows in response dashboard
- `getLatestResponsePerRecipient()` was returning every record for the scenario. Rewritten with `SELECT MAX(id) ... GROUP BY phoneNumber` subquery so only the latest response per contact is returned.

### model/Converters.kt — fix latent NPE on corrupted DB column
- `toRecipientList()` and `toGroupList()` returned `null` when column was NULL, which Room would assign to a non-null field and crash. Both now return `emptyList()` and have non-null return types.

### model/ContactSendHistoryDao.kt — fix send-count race condition
- `recordSend()` used a read-then-write pattern that allowed concurrent calls to both insert `sendCount = 1`. Replaced with `insertIfAbsent()` (INSERT IGNORE) + `incrementCount()` (SQL UPDATE); atomic at the SQLite level.

### utils/NotificationHelper.kt — fix spurious "Debug Mode OFF" notification
- `showDebugModeNotification(false)` was cancelling the ON notification then posting an OFF one. Added `return` after the cancel; disabling debug mode now just dismisses the notification.

### service/SmsResponseReceiver.kt — fix startListening() race + remove false-positive keyword
- `startListening()`: wrapped the `!isListening()` check and per-contact state reset in `synchronized` to prevent two concurrent callers from both clearing contact state.
- Removed `"hurry"` from the EMERGENCY keyword list — too common in casual replies (e.g. "hurry, I'm safe!") and was triggering automatic "Call 911" replies incorrectly.

### util/UpdateChecker.kt — close HTTP InputStream
- Wrapped `getInputStream()` in `.use { }` to release the socket immediately after reading.

### utils/DebugSimulator.kt + ui/MainViewModel.kt — cancel simulator on ViewModel clear
- Added `DebugSimulator.cancel()` method. `MainViewModel.onCleared()` now calls it, preventing load-test coroutines from running after the ViewModel is destroyed.

### service/EmergencySendingService.kt — two fixes
- `processQueue()`: added `delay(50)` when `nextTask()` returns null (queue drained between `getDetailedStatus()` and `nextTask()` calls) to prevent a tight CPU spin.
- `onDestroy()`: moved notification cancel to before `stopForeground()` and `super.onDestroy()`.

### utils/ForceSendAbuseTracker.kt — clock-jump decay spike fix
- `applyDecay()` early-return path now updates `lastDecayTime = now`; a clock-forward jump after a sub-second call can no longer cause a massive one-shot point decay.

### service/ManualSendGuard.kt — Kotlin Random
- Replaced `java.util.Random` instance with `kotlin.random.Random` singleton.

---

## Session: 2026-04-06 (v2.0 — Update Check + No-Response Flag + Scheduling + Widget)

### ⚠ New permission: INTERNET
- **File:** `AndroidManifest.xml`
- `android.permission.INTERNET` added. Required for the in-app update check (one HTTP request to the GitHub releases API on app start).
- **This is the first version of Red Rocket that requires network access.** The permission is declared as a normal permission — Android grants it automatically, no runtime prompt is shown to the user. No user data leaves the device; only an outbound read-only request to `api.github.com/repos/Fysh-ball/RedRocket/releases/latest` is made.
- Users who sideload the APK will see INTERNET listed under App Info → Permissions.

### util/UpdateChecker.kt — in-app update notification (new file)
- **File:** `util/UpdateChecker.kt`
- Suspend function hits the GitHub releases API on app start. Compares `tag_name` against `BuildConfig.VERSION_NAME` using semver-style part comparison.
- On network failure, timeout, or any exception: silently returns null (no error shown to user).
- If a newer tag is found: `MainUiState.updateAvailable` is set to the tag string.
- `MainViewModel.dismissUpdate()` clears it for the session.

### MainScreen.kt — dismissible update banner
- **File:** `ui/MainScreen.kt`
- Shown when `uiState.updateAvailable != null`. Tap opens GitHub releases page in browser. Dismiss button (×) clears for the session.
- Uses `tertiaryContainer` color so it's visually distinct from the error-colored warning cards.

### model/Scenario.kt — scenario scheduling (3 new fields)
- **File:** `model/Scenario.kt`
- Added `scheduleEnabled: Boolean = false`, `scheduleStartMinutes: Int = 480` (8 AM), `scheduleEndMinutes: Int = 1320` (10 PM).
- Added `isWithinSchedule()`: returns true when schedule is disabled (default) or current time falls within the window. Handles overnight windows (e.g. 22:00–06:00) where start > end.

### model/AppDatabase.kt — migration v10 → v11
- **File:** `model/AppDatabase.kt`
- `MIGRATION_10_11`: three `ALTER TABLE scenarios ADD COLUMN` statements with safe defaults (`scheduleEnabled = 0`, `scheduleStartMinutes = 480`, `scheduleEndMinutes = 1320`). All existing scenarios behave identically — scheduling is opt-in.

### EmergencyBroadcastReceiver.kt + EmergencyNotificationListener.kt — schedule gate
- **Files:** `service/EmergencyBroadcastReceiver.kt`, `service/EmergencyNotificationListener.kt`
- Both receivers now call `scenario.isWithinSchedule()` after the locked-scenario check. Scenarios outside their active window are skipped with a log entry. No change to detection logic.

### MainScreen.kt — Schedule section
- **File:** `ui/MainScreen.kt`
- New `SectionCard("Schedule")` between Alert Filters and Groups.
- Toggle to enable/disable the window. When enabled: two `OutlinedButton`s (From / Until) each open a Material3 `TimeInput` dialog. Window label shown in the toggle row subtitle.

### ResponseDashboard.kt — "Waiting for reply" no-response card
- **File:** `ui/ResponseDashboard.kt`
- Added `noResponseRecipients`: recipients who were messaged but have no response record. Shown as a card listing each contact by name (or number if nameless) during an active listening window only.

### service/EmergencyWidget.kt — home screen widget (new file)
- **File:** `service/EmergencyWidget.kt`
- `AppWidgetProvider` subclass. Shows app name, current status text (sending / listening / monitoring), and an "Open App" button (PendingIntent to MainActivity).
- `pushUpdate(context)` is a static companion method called from `EmergencyApp.onCreate()` for the initial status push. Widget system also calls `onUpdate()` on its own schedule (every 30 min per `widget_emergency_info.xml`).
- Widget layout: `res/layout/widget_emergency.xml`. Provider metadata: `res/xml/widget_emergency_info.xml`.

### AndroidManifest.xml — widget registration
- `EmergencyWidget` receiver registered with `android.appwidget.action.APPWIDGET_UPDATE` intent filter and `android.appwidget.provider` metadata pointing to `@xml/widget_emergency_info`.

---

## Session: 2026-04-06 (Boot Receiver + Battery Warning + Export/Import + Test Send + Bug Fixes)

### MainScreen.kt - tutorial step 5 auto-scroll
- **File:** `ui/MainScreen.kt`
- `rememberScrollState()` hoisted from inside the `else` branch to `MainScreen` top level, enabling access from `LaunchedEffect`.
- Added `LaunchedEffect(uiState.tutorialStep)` that calls `scrollState.animateScrollTo(scrollState.maxValue)` with a 150ms delay when `tutorialStep == 4 && showTutorial`. Programmatic scroll works even with `verticalScroll(enabled = false)`.

### MainScreen.kt - battery optimization warning cards
- **File:** `ui/MainScreen.kt`
- Added battery optimization warning card shown when `PowerManager.isIgnoringBatteryOptimizations()` returns false. Tapping opens ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS. Polled via `LaunchedEffect(Unit)` every 5s (cancels automatically with composable lifecycle).
- Added OEM-specific secondary warning card for Xiaomi/Huawei/Honor/OPPO/Vivo/Realme devices (manufacturer check via `Build.MANUFACTURER.lowercase()`), shown only when battery opt is not yet exempted.
- Removes "Device restrictions" entry from open issues in KNOWN_ISSUES.md.

### service/BootReceiver.kt - new boot receiver
- **File:** `service/BootReceiver.kt` (new)
- Logs `ACTION_BOOT_COMPLETED`. Registered in `AndroidManifest.xml` with `RECEIVE_BOOT_COMPLETED` permission.
- Note: `ACTION_BOOT_COMPLETED` fires after first user unlock (FBE). Does not use `LOCKED_BOOT_COMPLETED` — Room DB is in credential-protected storage and is inaccessible before unlock.

### app/build.gradle.kts - product flavors (dev/production)
- Added `flavorDimensions += "environment"` with `dev` and `production` flavors.
- `dev`: `applicationIdSuffix = ".dev"`, `versionNameSuffix = "-dev"`, `IS_PRODUCTION = false`.
- `production`: `IS_PRODUCTION = true`.
- Safe fallback `buildConfigField("Boolean", "IS_PRODUCTION", "false")` added to `defaultConfig` so any future build type without an explicit flavor override compiles safely.

### EmergencyApp.kt - IS_PRODUCTION guards MockSmsSender
- `getActiveSmsProvider()` now returns mock sender only when `!BuildConfig.IS_PRODUCTION && isDebugModeEnabled`. Production builds are always forced to real SMS regardless of debug toggle.

### model/ScenarioBackup.kt - new backup model
- **File:** `model/ScenarioBackup.kt` (new)
- `data class ScenarioBackup(version, exportedAt, scenarios, blockPhrases)` used for export/import JSON serialization via Gson.

### MainViewModel.kt + SettingsDialog.kt - scenario export/import
- **Files:** `ui/MainViewModel.kt`, `ui/SettingsDialog.kt`
- `exportScenarios(uri)`: serializes all scenarios + block phrases to JSON via Gson and writes to SAF URI. Explicit null check on `openOutputStream` — reports failure toast instead of silently writing nothing.
- `importScenarios(uri)`: deserializes with null-safe `.orEmpty()` guards (Gson bypasses Kotlin constructors; defaults not applied). Warns if backup `version > 1` (newer app version). Block phrases deduplicated by phrase text before insert.
- SettingsDialog: Data section added (Export / Import buttons + merge note). Uses `CreateDocument` and `OpenDocument` activity result launchers.

### MainViewModel.kt + SettingsDialog.kt - test send
- **Files:** `ui/MainViewModel.kt`, `ui/SettingsDialog.kt`
- `sendTestMessage(phoneNumber)`: validates number (min 7 chars, must contain a digit), blocks if `isSending` is active to prevent injecting a test message into a live emergency queue.
- SettingsDialog: Test Send section added with phone number input dialog.

### SettingsDialog.kt - section reorder + height fix
- `heightIn(max = 650.dp)` → `fillMaxHeight(0.87f)` to accommodate all sections on all screen sizes.
- Section order: Appearance → Detection → Timeline → **Data** → **Test Send** → Debug → Help.

### Architecture review fixes (from android-tech-lead agent)

#### MainViewModel.kt + MainScreen.kt - Toast MVVM violation
- `exportScenarios`, `importScenarios`, and `sendTestMessage` all called `Toast.makeText` via `withContext(Dispatchers.Main)` directly from the ViewModel — MVVM violation that can't be tested and may not surface if the process is backgrounded.
- Added `userMessage: String?` to `MainUiState` and `clearUserMessage()` to the ViewModel. All feedback now sets `userMessage`. `withContext(Dispatchers.Main)` and `Toast` import removed from ViewModel entirely.
- `MainScreen`: added `LaunchedEffect(uiState.userMessage)` that shows a `Toast` and calls `clearUserMessage()`.

#### MainScreen.kt - battery optimization polling loop replaced with BroadcastReceiver + lifecycle observer
- `LaunchedEffect(Unit) { while(true) { delay(5000) } }` was polling `isIgnoringBatteryOptimizations` every 5 seconds. This caused up to a 5-second lag after the user grants exemption, and ran unnecessary wake-ups while the screen was visible.
- Replaced with a `DisposableEffect` that registers a `BroadcastReceiver` for `ACTION_POWER_SAVE_MODE_CHANGED` and a `LifecycleEventObserver` for `ON_RESUME`. State updates immediately when power mode changes or the user returns from battery settings. Both are cleaned up in `onDispose`.

#### MainViewModel.kt - Gson singleton
- `Gson()` was instantiated inline on every `exportScenarios`/`importScenarios` call. `Gson` is thread-safe and designed to be reused. Added `private val gson = Gson()` instance field.

#### MainViewModel.kt - sendTestMessage phone validation uses normalizePhone
- Manual digit check (`normalized.none { it.isDigit() }`) replaced with `normalizePhone(phoneNumber.trim(), RegionSettings.effectiveRegion)` — consistent with the rest of the send pipeline.

#### MainViewModel.kt - BACKUP_CURRENT_VERSION constant
- Replaced the magic `> 1` literal in the version check with `private val BACKUP_CURRENT_VERSION = 1` with a comment instructing when to increment it.

### Code audit - 4 critical bug fixes (from android-code-reviewer agent)

#### MainViewModel.kt - importScenarios NPE on Gson null fields
- Gson bypasses Kotlin constructors; `backup.scenarios` and `backup.blockPhrases` can be `null` even though the data class declares `emptyList()` defaults.
- All downstream reads now use `backup?.scenarios.orEmpty()` and `backup?.blockPhrases.orEmpty()`. Prevents NPE on partial/truncated/corrupted backup files.

#### MainViewModel.kt - sendTestMessage could inject into live emergency queue
- Added `isSending` guard before enqueue: aborts with a toast if an emergency send is already in flight. A test message injected mid-emergency would corrupt recipient counts and send "[TEST]" to real emergency contacts.

#### MainViewModel.kt - exportScenarios silent success on null stream
- `openOutputStream(uri)` can return null (SAF provider failure). Previously the write was skipped silently but the success toast still fired.
- Now checks for null stream explicitly, shows "Export failed: could not open file" toast, and returns early.

#### build.gradle.kts - IS_PRODUCTION not in defaultConfig (compile failure on unknown variant)
- `IS_PRODUCTION` was only declared inside product flavors. Any build variant not combining with `dev` or `production` would fail with "Unresolved reference: IS_PRODUCTION".
- Added `buildConfigField("Boolean", "IS_PRODUCTION", "false")` to `defaultConfig` as a safe fallback.

---

## Session: 2026-04-05 (Preset Toggle Removal + UX/Touch + Code Audit)

### BlockPhrasePresetPicker.kt + TriggerInput.kt - preset rows now toggle (remove on second tap)
- **Files:** `ui/BlockPhrasePresetPicker.kt`, `ui/TriggerInput.kt`
- **Before:** Tapping an already-added preset row did nothing. Once added, a preset could only be removed by closing the sheet and deleting chips individually.
- **After:**
  - `BlockPhrasePresetPicker`: row `onClick` calls `onRemovePhrase(phrase)` when the phrase is already present, `onAddPhrase(phrase)` otherwise. Icon changes from `Add` (primary tint) to `Close` (error tint) to indicate state. "Add All" button replaced by "Remove All" (`OutlinedButton`, error style) when all phrases in the region are already active.
  - `PresetPickerDialog` (TriggerInput): `onRemovePreset` parameter added. Row `onClick` calls `onRemovePreset(preset)` when added (all keywords in preset are present), `onAddPreset(preset)` otherwise. Same icon swap pattern.
  - Call site in `TriggerInput`: `onRemovePreset = { preset -> keywords.filter { it !in preset.keywords }.joinToString(",") }` passed to `onKeywordsChange`.
  - Call site for block phrases: `onRemovePhrase = { phrase -> blockPhrases.find { it.phrase == phrase }?.let { onDeleteBlockPhrase(it) } }`.

### RecipientsInput.kt + TriggerInput.kt - chip close icon touch targets increased to 40dp
- **Files:** `ui/RecipientsInput.kt`, `ui/TriggerInput.kt`
- `RecipientChip`, `KeywordChip`, and `BlockPhraseChip` close icon areas all changed to `Modifier.size(40.dp).clip(CircleShape).clickable { ... }.padding(8.dp)`. Touch area is 40dp; visual icon remains 24dp (the inner 24dp after 8dp padding on each side).
- Content descriptions made specific: `"Remove ${recipient.name.ifEmpty { recipient.phoneNumber }}"`, `"Remove $keyword"`, `"Remove $phrase"`.

### TriggerInput.kt - RegionPickerDialog search field added
- **File:** `ui/TriggerInput.kt`
- `RegionPickerDialog` now has an `OutlinedTextField` search bar above the region list.
- `filteredRegions` computed via `remember(query)` - filters by display name or country code, case-insensitive.
- `LazyColumn` max height reduced from 420dp to 360dp to keep the search field visible without scrolling.
- `items(filteredRegions, key = { it.countryCode })` - stable keys prevent full-list recomposition on query change.

### TriggerInput.kt - KeywordAddSheet paste handling
- **File:** `ui/TriggerInput.kt`
- `onValueChange` in `KeywordAddSheet` detects comma in pasted text, splits on `","`, trims and filters blanks, adds all-but-last as keywords, leaves the last part in the text field (unless the paste ended with a comma, in which case all parts are added and the field is cleared).
- Guard for empty `parts` (e.g. input `", "`) prevents `NoSuchElementException` on `parts.last()`.

### TriggerInput.kt + BlockPhrasePresetPicker.kt - lazy list stable keys
- `PresetPickerDialog`: `items(presets, key = { it.name })`
- `RegionPickerDialog`: `items(filteredRegions, key = { it.countryCode })`
- `BlockPhrasePresetPicker`: region rows now keyed by phrase text.

### Code audit - 3 real fixes

#### EmergencySendingService.kt - NotificationManager null safety
- `getSystemService(NotificationManager::class.java)` returns a Kotlin platform type (`T!`) treated as non-null. Added `?.` to all three call sites: `updateNotification()`, `createNotificationChannel()`, and the post-completion notification.

#### MainViewModel.kt - region-aware phone normalization for duplicate detection
- `onAddRecipientsToGroup()`, `historyDao.recordSend` block, and `resendToRecipients()` all called `normalizePhone(phone)` without a region. Region-unaware normalization uses `takeLast(10)` which maps "0412345678" to "0412345678" but "+61412345678" to "1412345678" - mismatch, causing duplicate recipients for AU/NZ users.
- All three sites now compute `val region = RegionSettings.effectiveRegion` once and pass it to `normalizePhone(phone, region)`. The region-aware path strips the trunk prefix ("0" for AU/NZ) producing "412345678" for both forms.

#### EmergencyBroadcastReceiver.kt + EmergencyNotificationListener.kt - goAsync timeout for block phrase DB read
- `blockPhraseDao().getAllOnce()` had no timeout. A slow DB (first-boot WAL checkpoint, I/O pressure) could hold the coroutine indefinitely - past the 10s `goAsync()` deadline in `EmergencyBroadcastReceiver`, causing the process to be killed mid-evaluation.
- Both files now wrap the DB read in `withTimeoutOrNull(5_000L) { } ?: emptyList()`. On timeout: no block phrases are treated as active, which is safer than dropping the alert.

---

## Session: 2026-04-05 (AMBER Alert Behavior Change)

### FalseAlarmDetector - AMBER alert hard block removed
- **File:** `util/FalseAlarmDetector.kt`
- **Change:** Removed Step 0 (AMBER_BLOCK) from the detection pipeline. Removed `AMBER_BLOCK_PHRASES` list entirely. Removed the AMBER check from `isBlockedDespiteKeywordMatch()`.
- **Before:** AMBER alerts were hard-blocked from ever triggering any scenario, regardless of keywords.
- **After:** AMBER alerts pass through the 7-step pipeline like any other alert. A scenario with "amber alert" as an Activation Keyword will trigger on them. A scenario without matching keywords will not.
- **Migration:** Users who do not want AMBER alerts to trigger their scenarios can add "amber alert" or "child abduction" as Block Phrases on that scenario.
- **Why:** AMBER alerts are a legitimate trigger scenario for some users (e.g. someone who wants to notify family when a child abduction alert is issued in their area). The hard block was too aggressive.
- Updated: `DETECTION_RULES.md` (7-step pipeline, AMBER note), `ARCHITECTURE.md` (step count), `TESTING.md`, `KNOWN_ISSUES.md`.

---

## Session: 2026-04-05 (UI Polish + Regional Presets + Code Cleanup)

### RegionalTriggerPresets.kt - full rewrite with 14 categories across 22 languages
- **File:** `ui/RegionalTriggerPresets.kt`
- Added 3 missing common WEA/EAS categories: AMBER Alert, Severe Thunderstorm, Winter Storm.
- Split "Nuclear / Missile" into two separate categories: Nuclear / Radiological (power plant incidents, radiation leaks) and Nuclear Missile Strike (inbound nuclear-armed ballistic missile). These have different protective actions and should not be the same trigger.
- Removed generic action phrases ("take shelter immediately", "seek higher ground", etc.) from all disaster-specific categories. Phrases like those appear across too many event types and cause false positives.
- "move to higher ground" kept only in Tsunami, where it is a specific NWS instruction unique to that hazard.
- Hurricane renamed to Typhoon in East/South-East Asian presets (JP/KR/CN/TW/ID) and to Cyclone in Hindi (IN).
- All 14 categories sorted alphabetically across all 22 language presets.
- Philippines (PH) added to localizedTriggerPresets() lookup via PRESETS_ID.

### BlockPhrasePresets.kt - Philippines added, all regions sorted alphabetically
- **File:** `ui/BlockPhrasePresets.kt`
- Added Philippines (PH, +63) with English and Tagalog test phrases.
- All 35 regions reordered alphabetically by display name.

### BlockPhrasePresetPicker.kt - region selector removed from sheet
- **File:** `ui/BlockPhrasePresetPicker.kt`
- Removed the region selector chip and associated state from the sheet. Region selection is now the caller's responsibility. Sheet signature simplified to (regionCode, currentPhrases, onAddPhrase, onDismiss).

### TriggerInput.kt - RegionPickerDialog added, region selector restyled
- **File:** `ui/TriggerInput.kt`
- Added private RegionPickerDialog composable (AlertDialog with scrollable region list showing flag, name, dial code, and auto-detected badge).
- Region selector Surface restyled to match ScenarioDropdown: 48dp height, surfaceVariant background, bodyLarge Bold text, ArrowDropDown 24dp icon.

### Chip sizing - RecipientChip, KeywordChip, BlockPhraseChip all updated
- **Files:** `ui/RecipientsInput.kt`, `ui/TriggerInput.kt`
- Removed fixed height(32.dp) from RecipientChip. All chips now size from padding (8dp vertical) so long phrases never clip the X button.
- Text style upgraded from bodySmall to bodyMedium. Close icon increased from 16dp to 24dp.
- Added weight(1f, fill = false) on text in all chip types so the icon is always visible regardless of phrase length.
- Corner radius unified to 20dp across all chip types.

### Code cleanup - em dashes and box-drawing dividers removed from all .kt files
- Replaced all em dash (U+2014) characters in comments and strings with plain hyphens.
- Replaced all decorative box-drawing divider comments (// -- Label -----) with plain // Label comments or removed them where they added no information.
- 40 files affected.

---

## Session: 2026-04-05 (Indic Script Fix + 8 New Languages + Code Audit)

### FalseAlarmDetector - Indic script normalization fix
- **File:** `util/FalseAlarmDetector.kt`
- **Bug:** `RE_NON_WORD = Regex("[^\\p{L}\\p{N}\\s]")` stripped Devanagari and Bengali vowel marks (matras). These are Unicode category `\p{M}` (spacing combining marks), not `\p{L}`. "आपातकाल" (Hindi: emergency) had all vowel marks destroyed, making Hindi phrase matching impossible.
- **Fix:** `RE_NON_WORD = Regex("[^\\p{L}\\p{M}\\p{N}\\s]")` - `\p{M}` added to the preserved set.
- Arabic harakat (short vowel marks U+0610+) are also `\p{M}` and now preserved; formal Arabic EAS text rarely uses harakat so this has no practical downside.

### FalseAlarmDetector - keywordMatchesContent() normalization fix
- **Bug:** `keywordMatchesContent()` only called `.lowercase()` on the user keyword. If a user saved a keyword with an accent (e.g. "évacuation"), it would NOT match normalized alert content ("evacuation") because the accent was never stripped.
- **Fix:** Changed `val kwLower = keyword.lowercase()` → `val kwNorm = normalize(keyword)`. Renamed parameter `contentLower` → `contentNorm` throughout for consistency.

### FalseAlarmDetector - 8 new languages (total: 20)
All 9 phrase lists extended with translations for 8 new language groups:
- **Turkish** - pre-normalized (ğ→g, ş→s, ü→u, ö→o, ç→c, ı→i for uppercase-safe matching)
- **Polish** - pre-normalized (ą→a, ę→e, ó→o, ć→c, ś→s, ź/ż→z, ń→n; ł preserved)
- **Ukrainian** - NFC Cyrillic
- **Swedish** - pre-normalized (ä→a, ö→o, å→a)
- **Norwegian / Danish** - ø and æ preserved (no canonical NFD decomposition)
- **Indonesian / Malay** - Latin, no diacritics
- **Hindi** - NFC Devanagari; relies on `\p{M}` fix above
- **Bengali** - NFC Bengali; relies on `\p{M}` fix above

Full language list: English, French, Spanish, Portuguese, German, Italian, Dutch, Russian, Japanese, Korean, Chinese (Simplified + Traditional), Arabic, Turkish, Polish, Ukrainian, Swedish, Norwegian, Danish, Indonesian, Hindi, Bengali.

### SmsResponseReceiver - prefs!! null assertion removed
- **File:** `service/SmsResponseReceiver.kt`
- **Bug:** `prefs!!.getLong(...)` immediately after assignment - a race between the assignment and the `!!` dereference if the assignment somehow interleaved.
- **Fix:** Assigned to local val `p` first, then assigned `prefs = p`, then used `p.getLong(...)`. Eliminates the nullable dereference entirely.

### AppDatabase - SQL injection style fix + migration contract comment
- **File:** `model/AppDatabase.kt`
- **Fix:** String interpolation in `MIGRATION_9_10` `execSQL` calls changed to parameterized form: `execSQL("INSERT INTO block_phrases (phrase) VALUES (?)", arrayOf(phrase))`. Values are hardcoded strings (not user input), but parameterized form is good practice.
- **Added:** Prominent migration contract banner comment above `getDatabase()` documenting the 4-step process required for every schema bump, and warning that missing migrations will erase all user data for v6+ users.

### SmsSender - stale timeout comment corrected
- **File:** `service/SmsSender.kt`
- **Bug:** Comment on `sendWithConfirmation()` said "Timeout with no error is treated as success". Code correctly returns `false` on timeout. Comment was wrong.
- **Fix:** Updated to "Timeout after 10 s is treated as failure - triggers Lazarus retry."

### AndroidManifest - allowBackup disabled
- **File:** `app/src/main/AndroidManifest.xml`
- `android:allowBackup="true"` → `android:allowBackup="false"`.
- Prevents emergency contacts, scenarios, and response history from being backed up to Google Cloud unencrypted.

### PhoneUtils - limitation documented
- **File:** `util/PhoneUtils.kt`
- Expanded docstring to explicitly document `takeLast(10)` behavior: correct for US/UK/Germany, broken for Australia (11-digit local numbers whose international form "1412345678" collides with a different US number). Notes that a full solution requires `libphonenumber`.

### MD files updated
- `DETECTION_RULES.md` - language count updated to 20, new languages added to all step tables, `\p{M}` noted in normalization section.
- `ARCHITECTURE.md` - language count updated to 20, schema version updated to current.
- `CHANGES.md` - this entry.

---

## Session: 2026-04-05 (Full Multilingual EAS Detection)

### FalseAlarmDetector - full multilingual rewrite
- **File:** `util/FalseAlarmDetector.kt`

#### normalize() - Unicode-aware, non-Latin scripts now preserved
- **Old:** `[^a-z0-9\s]` regex after lowercase - destroyed ALL non-ASCII text (CJK, Arabic, Cyrillic, Korean, Hebrew).
- **New:** NFD decompose → strip U+0300–U+036F (Latin combining diacritics only) → NFC recompose → `[^\p{L}\p{N}\s]` cleanup.
- Latin accents stripped transparently ("é"→"e", "ü"→"u"). Non-Latin scripts (CJK, Arabic, Cyrillic, etc.) pass through intact.
- NFC recompose step prevents Korean Hangul from staying as decomposed jamo after NFD.
- Arabic harakat (short vowel marks, U+0610+) are `\p{M}` not `\p{L}`, stripped by the final filter - Arabic base letters preserved.
- Added `import java.text.Normalizer`.
- Pre-compiled regexes (`RE_COMBINING`, `RE_NON_WORD`, `RE_SPACES`) at object level - no recompilation per call.

#### isUrgentStructure() - case-free script detection (Step 7 fail-safe)
- Added third check: if ≥10 letters are present and NONE have case (neither `.isUpperCase()` nor `.isLowerCase()`), the message is in a case-free script (CJK, Arabic, Hebrew). Returns `true`, enabling the trusted-source fail-safe for these scripts even when no phrase matched.
- This ensures a Japanese/Korean/Arabic cell broadcast from a trusted source always passes Step 7 if it didn't already trigger at Steps 1–4.

#### Phrase lists - 12-language coverage
All 9 phrase lists updated with translations for: English, French, Spanish, Portuguese, German, Italian, Dutch, Russian, Japanese, Korean, Chinese Simplified, Chinese Traditional, Arabic.

Lists updated:
- `AMBER_BLOCK_PHRASES` - child safety equivalents in 7 languages
- `STRONG_ACTION_PHRASES` - immediate evacuation/shelter commands in all 12 languages
- `EXTREME_DANGER_PHRASES` - catastrophic event terms in all 12 languages
- `OVERRIDE_PHRASES` - "this is not a test" equivalents in 11 languages
- `HARD_TEST_PHRASES` - explicit test/drill phrases in all 12 languages
- `RED_TRIGGER_PHRASES` - life-threatening compound phrases in all 12 languages
- `MODERATE_ACTION_PHRASES` - shelter/evacuate/stay-indoors phrases in all 12 languages
- `DANGER_WORDS` - emergency/warning/threat words in all 12 languages
- `SOFT_TEST_PHRASES` - exercise/drill words in all 12 languages

Storage convention documented in class KDoc:
- Latin phrases: pre-normalized (lowercase, accents stripped)
- Non-Latin phrases: NFC Unicode form (pass through `normalize()` unchanged)

### MD files updated
- `DETECTION_RULES.md` - fully rewritten to reflect 8-step pipeline, 12-language phrase lists, new normalize() algorithm, case-free script fail-safe, and phrase storage conventions.
- `CHANGES.md` - this entry.

---

## Session: 2026-04-04 (Block Phrases UI + Alert History Fixes + Dependency Modernisation)

### Block Phrases - moved into Alert Filters section, same card as Activation Keywords
- **Files:** `ui/TriggerInput.kt`, `ui/MainScreen.kt`, `ui/SettingsDialog.kt`, `ui/TutorialOverlay.kt`
- Block Phrases UI promoted from Settings into the same `SectionCard` as Activation Keywords, as a clearly labelled sub-section.
- Section card renamed: `"Trigger"` → `"Alert Filters"`.
- Keyword sub-section renamed: `"Alert Keywords"` → `"Activation Keywords"`.
- Block Phrase chips use `errorContainer`/`onErrorContainer` colors to distinguish visually from green keyword chips.
- Info button (`Icons.Default.Info`, secondary color) opens an `AlertDialog` explaining what block phrases do. Box size unchanged.
- `KeywordAddSheet` parameterised with `title` and `placeholder` - reused for both keyword and block phrase input.
- Tutorial step 2 updated: title `"3 / 6: Alert Filters"`, body covers both Activation Keywords and Block Phrases.
- SettingsDialog: Block Phrases section removed; User Manual updated to describe "Alert Filters" (Activation Keywords + Block Phrases).

### Alert History - fixed two bugs introduced in prior sessions
- **File:** `service/EmergencyNotificationListener.kt`
- **Bug 1 (phantom entries):** `hadKeywordMatch` variable caused non-EAS notifications to be logged whenever keywords matched, even if the scenario was locked/invalid and never fired. Removed `hadKeywordMatch` entirely; non-EAS entries now only written when `triggeredCount > 0`.
- **Bug 2 (WEA not logged):** `isEmergencyAlertPackage()` gated on `detectionSucceeded` - partial detection (e.g. Personal Safety app found, WEA package missed) set `detectionSucceeded = true` but left the WEA package absent from `detectedPackages`, silently excluding it. Fixed: `isEmergencyAlertPackage()` now always returns `packageName in ALL_KNOWN_PACKAGES || packageName in detectedPackages`.
- Added `"EMERGENCY ALERT"` to `looksLikeEASContent()` content-based fallback.

### Dependency modernisation
- **kapt → KSP** (`gradle/libs.versions.toml`, `app/build.gradle.kts`): replaced `id("kotlin-kapt")` with `alias(libs.plugins.ksp)`, `kapt(room.compiler)` with `ksp(room.compiler)`, and `javaCompileOptions { annotationProcessorOptions { ... } }` with top-level `ksp { arg(...) }`. KSP version `2.0.21-1.0.25` added to version catalog.
- **`reorderable:0.9.6` → `sh.calvin.reorderable:2.4.0`** (`GroupsSection.kt`, `ScenarioDropdown.kt`): migrated to maintained fork. API changes: `Modifier.reorderable()` removed (not needed in 2.x); `detectReorder()` replaced with `.draggableHandle(onDragStopped = {...})`; explicit `lazyListState` passed to `rememberReorderableLazyListState`.

### Dead code removal
- **File:** `ui/MainViewModel.kt`
- Removed `onAddRecipients()` - was modifying the deprecated flat `Scenario.recipients` field and was never called from any UI composable.

---

## Session: 2026-03-30 (Alert History - keyword-gated logging + input box polish)

### Alert History - correct logging rules per source
- **Files:** `service/EmergencyNotificationListener.kt` (two passes this session)
- **Rules implemented:**
  1. **Cell broadcasts (WEA/EAS/CMAS/ETWS)** - `EmergencyBroadcastReceiver` already logs ALL unconditionally before evaluation, regardless of type (test, AMBER, presidential, etc.). No change needed here. Source = `"cell_broadcast"`.
  2. **EAS companion notifications** - notifications from known emergency alert packages (`EmergencyPackageDetector`): always logged. Source = `"alert"`.
  3. **Wide-spread ("listen to all") app notifications** - logged ONLY when `triggeredCount > 0`, i.e., the notification actually caused the system to send. Keyword hits that were blocked by lock/invalidity, and non-matching notifications, are silently discarded. Source = `"notification_wide"`.
- **Problem fixed:** Previous version gated on `keywordMatched` (any keyword hit, even blocked ones) which still let non-triggering notifications through. Changed gate to `triggeredCount > 0`.
- Removed now-unused `var keywordMatched` flag.

### Hint text style - Trigger and Message boxes match Recipients
- **Files:** `ui/TriggerInput.kt`, `ui/MessageInput.kt`
- Changed hint/placeholder style from `bodyMedium` + `onSurfaceVariant.copy(alpha = 0.38f)` to `bodyLarge` + `onSurfaceVariant` (no alpha) - matching the Material3 TextField default used by the Recipients box.
- Message body text (non-empty state) also updated to `bodyLarge` for consistency.

---

## Session: 2026-03-30 (Input Box Polish - Scrollbar + Scroll Isolation + Uniform Sizing)

### RecipientsInput - scrollbar, scroll isolation, uniform min height
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
- **Problem:** System emergency alert notifications were stored with source `"notification"` and displayed as "Notification" - but they are alerts. Wide-spread app notifications fell through to `else -> "Alert"` in `DashboardAlertCard`, showing non-alert notifications as "Alert".
- **Fix:**
  - Source for system emergency alerts changed from `"notification"` to `"alert"` in the listener.
  - Label mappings updated in both dialog and dashboard: `"alert"/"notification"` → "Alert", `"notification_wide"` → "App Notification", `"cell_broadcast"` → "Cell Broadcast".
  - Backward compat: existing records with source `"notification"` still display as "Alert".
  - `computeAlertSeverity` updated: `"alert"` and `"notification"` sources default to RED (same as cell broadcasts).

### Input Box UX - No auto-preset on empty trigger tap
- **File:** `ui/TriggerInput.kt`
- **Problem:** Tapping an empty trigger box automatically opened the preset picker dialog. Users who wanted to type a custom keyword had to dismiss the preset dialog first.
- **Fix:** Empty trigger box tap now always opens the keyword add sheet. The preset picker is only reachable via the `+` button.

### Input Box Sizing - All three boxes now match

#### Uniform sizing: Trigger, Message, Recipients surfaces identical
- **Files:** `ui/TriggerInput.kt`, `ui/MessageInput.kt`, `ui/RecipientsInput.kt`
- All three surfaces now share the same structure:
  - Modifier: `weight(1f)` + `padding(vertical = 4.dp)`
  - Shape: `RoundedCornerShape(12.dp)`
  - Border: `BorderStroke(1.dp, MaterialTheme.colorScheme.outline)` (no alpha)
  - Inner: `Column(padding(8.dp))` with `heightIn(min = 48.dp, max = 120.dp)` + `verticalScroll`
- Min height (48dp content + 16dp padding = ~64dp surface) matches the recipients TextField intrinsic height.
- `MessageInput` border corrected from `outline.copy(alpha = 0.5f)` → `outline` to match recipients.
- `MessageInput` removed `maxLines = 6` limit - container scroll handles long messages.

#### Scrollable containers - expand up to 120dp then scroll internally
- **Files:** `ui/TriggerInput.kt`, `ui/MessageInput.kt`, `ui/RecipientsInput.kt`
- Each input box expands naturally as content grows; once content would push the outer page scroll, the box caps at 120dp and its interior scrolls instead.
- Prevents the main screen from requiring excessive scrolling to reach recipients or the send button.

#### Upload button icon size matches "+" button
- **File:** `ui/MessageInput.kt`
- `UploadFile` icon: `size(24.dp)` → `size(28.dp)` to match the `Add` icon in `RecipientsInput`.

---

## Session: 2026-03-29 (Tutorial UX + Build Fix + Refactor)

### Bug Fixes

#### Build Fix - `CompositingStrategy` wrong import
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
- **Problem:** `startTutorial()` cleared the scenario inside a coroutine, so the DB write could race with the user selecting a preset - the preset would be overwritten by the delayed clear.
- **Fix:** `_uiState.update { currentScenario = cleared }` moved to run synchronously before the coroutine. Only the `scenarioDao.insertScenario()` DB persist remains inside `viewModelScope.launch`.

#### 5. Confetti renders above the congratulations card
- **File:** `ui/TutorialOverlay.kt`
- **Problem:** The confetti `Canvas` was not the last child in `BoxWithConstraints`, so the card rendered on top of it.
- **Fix:** Confetti `Canvas` moved to be the last child (highest z-order). `Canvas` has no pointer input so the card's button remains fully clickable beneath it.

---

### Refactor - MainScreen.kt split into separate files

`MainScreen.kt` was ~1380 lines containing multiple unrelated composables. All self-contained UI components extracted to their own files. `MainScreen.kt` now contains only the `MainScreen` composable (~340 lines).

| New File | Composables Extracted |
|---|---|
| `ui/BrowserTabBar.kt` | `BrowserTabBar` |
| `ui/SectionCard.kt` | `SectionCard` |
| `ui/SwipeToConfirm.kt` | `SwipeToConfirm` |
| `ui/SendDialogs.kt` | `AbuseLockoutDialog`, `ManualSendDialog` |
| `ui/TutorialOverlay.kt` | `TutorialSpotlightOverlay`, `TutorialCard`, `TutorialCompleteOverlay`, `ConfettiParticle` |

---

## Session: 2026-03-28/29 (Code Review - Critical / High / Medium fixes)

### Critical Fixes
- **RateLimiter.kt** - `AtomicLong` + CAS loop for `lastSendTimestamp` (was non-atomic)
- **AdaptiveSendController.kt** - `AtomicInteger` for failure/success/lazarus counters
- **AppDatabase.kt** - Removed `fallbackToDestructiveMigration()`, enabled `exportSchema = true`, added schema path in `build.gradle.kts`
- **ForceSendAbuseTracker.kt** - Takes `Context`, persists 6 state fields to `SharedPreferences`; `lastDecayTime` also persisted (`KEY_LAST_DECAY`)
- **EmergencySendingService.kt** - `startForeground()` called before `ACTION_STOP` branch; `try/finally` with `withContext(NonCancellable)` in `processQueue`; `SmsDeliveryReceiver.pendingSent.clear()` added to `onDestroy()`
- **MessageQueueManager.kt** - `MessageTask` fully immutable; `handleFailure`/`requeueForLazarus` use `.copy()`

### High Fixes
- **EmergencyBroadcastReceiver.kt** - Uses `app.appScope` instead of orphaned scope
- **SmsSender.kt** - Timeout returns `false` (triggers Lazarus retry)
- **SmsResponseReceiver.kt** - `SEND_SMS` permission check before auto-reply; `listenStartTime` persisted to `SharedPreferences`; `listenStartTimeFlow: StateFlow<Long>` added; `init(context)` called from `EmergencyApp.onCreate()`
- **MainViewModel.kt** - `recordForceSend()` moved to after captcha passes; polling loop replaced with `queueStatusFlow.collect { }`; `resendToRecipients()` iterates `scenario.groups`, matches by normalized phone, uses `group.message` per group

### Medium Fixes
- **SmsResponseReceiver.kt** - `"ok"/"fine"/"good"` exact-match only; PII log guarded with `BuildConfig.DEBUG`; companion `normalizePhone` alias removed
- **PhoneUtils.kt** (new) - `normalizePhone()` consolidated from 4 duplicates
- **EmergencyNotificationListener.kt** - All notifications logged to `PastAlert` before filtering; `CoroutineExceptionHandler` added to scope (both initial and rebind)
- **RecipientsInput.kt** - Contacts query uses explicit projection `arrayOf(DISPLAY_NAME, NUMBER)`

### Optimization Pass (2026-03-29)
- **EmergencySendingService.notificationJob** - Replaced 1s polling with `queueStatusFlow.collect`
- **MainViewModel** notification permission poll - Backs off to 60s once granted
- **EmergencyNotificationListener** scope rebind safety - `var scope`, recreated on rebind
- **ManualSendGuard** - Class-level `Random` instance (was `new Random()` per call)
- **DebugSimulator** - Added `SupervisorJob()` to scope
- **Group.guide** dead field - Removed
- **ContactSendHistoryDao** dead read methods - Removed
- **grayTheme** dead code - Removed from `Theme.kt` and `MainActivity`
- **ResponseDashboard** - Polling loop replaced with `listenStartTimeFlow.collectAsState()` + `LaunchedEffect` ticker
- **MainScreen** cooldown timer - `LaunchedEffect(uiState.lastSendCompletedAt)` + `delay(1000L)`, no longer runs when not in cooldown
- **MainUiState** - `@Stable` annotation added
- **ARCHITECTURE.md** - 3 doc fixes (failure threshold, timeout behavior, FalseAlarmDetector step count)

---

## Known Open Items

- Device restrictions (background limitations) - no warning shown yet if SMS restricted while locked
- Phone number normalization: the AU/NZ trunk prefix issue ("0412..." vs "+61412...") is fixed in MainViewModel via `normalizePhone(phone, RegionSettings.effectiveRegion)`. A fully general fix for all regions (e.g. countries where the local form is not simply "0" + subscriber number) still requires `libphonenumber`.

## Fixed / Resolved

- Regional wording differences (Canada alerts) - fixed: full French phrase set in FalseAlarmDetector
- Bilingual alerts (EN/FR parsing) - fixed: pre-normalized French phrases cover Canadian bilingual EAS alerts
- English-only detection - fixed: 20-language phrase lists now cover all major alert-sending regions

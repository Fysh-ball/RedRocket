# ARCHITECTURE.md

## Purpose
Defines the complete structure of the Red Rocket system. Every component, its role, and how it connects to others. Read this before touching any file.

---

## ENTRY POINTS

There are three ways the system receives an alert:

### 1. EmergencyBroadcastReceiver (cell broadcast)
- Receives Android WEA/CMAS/ETWS system broadcasts
- Extracts message body from 9 possible intent extra keys (OEM variations)
- Uses goAsync() to keep the process alive during async processing
- Evaluates ALL scenarios independently against the alert
- Logs to PastAlert regardless of trigger outcome
- File: `service/EmergencyBroadcastReceiver.kt`

### 2. EmergencyNotificationListener (notification listener service)
- Monitors all notifications posted to the system
- Extracts: title, text, subText, ticker from notification extras
- Skips own package notifications (loop prevention)
- Skips system audio routing notifications ("hearing another device")
- Respects wideSpreadEnabled setting — if OFF, only processes system emergency packages
- File: `service/EmergencyNotificationListener.kt`

### 3. ManualSendGuard (user-initiated)
- Force Send: user swipes slider, enters 6-char OTP captcha
- 5-second countdown before messages are enqueued
- Sends all valid, unlocked scenarios simultaneously
- File: `service/ManualSendGuard.kt`

---

## DETECTION PIPELINE

### EmergencyPackageDetector
- Detects which OEM emergency alert packages are installed on the device
- Layered detection: direct package lookup + broadcast receiver query
- Fallback: if detection finds nothing, trusts ALL known packages
- `isEmergencyAlertPackage()` ALWAYS checks `ALL_KNOWN_PACKAGES` first — partial runtime detection can never cause a known EAS package to be rejected
- Determines isTrustedSource flag passed to FalseAlarmDetector
- File: `util/EmergencyPackageDetector.kt`

### FalseAlarmDetector (8-step engine)
Every alert passes through all steps in order. First decisive step wins.

- Step 0: AMBER_BLOCK — "amber alert", "child abduction" → NEVER trigger
- Step 1: HARD_OVERRIDE — trusted source + extreme danger phrase → ALWAYS trigger
- Step 2: OVERRIDE_PHRASES — "this is not a test" → ALWAYS trigger
- Step 3: HARD_TEST_PHRASES — "this is a test", "only a test", "drill" → NEVER trigger
- Step 4: RED_PHRASES — life-threatening condition (e.g. "take shelter", "missile") → ALWAYS trigger (bypasses sensitivity)
- Step 5: SCORE ACCUMULATION (+5 trusted, +4 strong action, +2 moderate, +2 danger, +3 context boost, +1 CAPS, +1 repetition, +2 keyword match, −2 soft test)
- Step 6: THRESHOLD — score ≥ 6 → trigger
- Step 7: STRUCTURAL FAIL-SAFE — trusted source + urgent structure → trigger
- DEFAULT: no trigger

Full scoring rules documented in DETECTION_RULES.md
File: `util/FalseAlarmDetector.kt`

---

## SCENARIO ENGINE

### Scenario
- Top-level unit: name, trigger keywords (comma-separated), groups, isLocked, isFavorite, orderIndex
- isLocked: set to true after triggering; must be manually reset to re-trigger
- isValid(): must have at least one group with recipients AND a message
- File: `model/Scenario.kt`

### Group
- Sub-unit of a Scenario: name, recipients list, message text, isFavorite
- Each group sends its own message to its own recipients
- Triggers are shared across all groups in a scenario
- All valid groups fire simultaneously when scenario triggers
- File: `model/Group.kt`

### Recipient
- name, phoneNumber, optional contactId
- isValid(): phone number has ≥7 digits
- File: `model/Recipient.kt`

### ScenarioDao
- Room DAO: getAllScenarios() (Flow), getAllScenariosOnce(), getScenarioById(), insertScenario(), deleteScenario()
- File: `model/ScenarioDao.kt`

---

## MESSAGING PIPELINE

### MessageQueueManager
- Thread-safe queue with mutex protection
- Two queues: primary (first attempt) and retry (Lazarus mode)
- Failed queue: permanently failed messages
- Tracks: successCount, retrySuccessCount, totalEnqueued, inFlightCount
- Per-message status via StateFlow
- Up to 5 retries before permanent failure (Lazarus has no limit)
- File: `queue/MessageQueueManager.kt`

### AdaptiveSendController (state machine)
Three states:
- MULTI_THREADED: parallel sends, 0ms delay (minimum 150ms via RateLimiter)
- SEQUENTIAL: serial sends, 200ms delay
- LAZARUS: retry sends, 200ms delay

Transitions:
- MULTI_THREADED → SEQUENTIAL: 3 consecutive failures
- SEQUENTIAL → MULTI_THREADED: 5 consecutive successes
- SEQUENTIAL → LAZARUS: primary queue exhausted
- LAZARUS → SEQUENTIAL: retry phase complete
- File: `queue/AdaptiveSendController.kt`

### RateLimiter
- Enforces minimum 150ms between all SMS sends (carrier compliance)
- Adaptive delay from AdaptiveSendController layered on top
- File: `utils/RateLimiter.kt`

### SmsSender (real sends)
- Sends SMS via Android SmsManager
- Runtime SEND_SMS permission check before each send
- Splits long messages into multipart SMS automatically
- Appends: "Reply: 1=Safe, 2=Safe+updates, 3=EMERGENCY"
- 10-second PendingIntent timeout — timeout = treated as failure (Lazarus will retry)
- Reports results to AdaptiveSendController
- File: `service/SmsSender.kt`

### MockSmsSender (debug mode only)
- Simulates SMS sending with configurable failure rate
- Uses DebugSimulator to inject failures
- Not active in production builds
- File: `utils/MockSmsSender.kt`

### EmergencySendingService (foreground service)
- Foreground service with live status notification
- Processes primary queue, then retry queue (Lazarus)
- Updates notification per message: "Sending…", "Sent ✓", "Failed ✗", "Retrying…"
- Stops automatically when all queues are empty
- File: `service/EmergencySendingService.kt`

### LazarusRetrySystem
- Activated when primary queue exhausted but retry queue has failures
- No retry limit — re-queues failed messages to end of retry queue indefinitely
- 5-second wait between passes (if Keep Trying = ON)
- Exits when retry queue empty AND (Keep Trying = OFF OR no new failures in last pass)
- File: `service/LazarusRetrySystem.kt`

### SmsDeliveryReceiver
- BroadcastReceiver for SMS_SENT and SMS_DELIVERED
- Maps callback IDs to responses via ConcurrentHashMap
- Reports: OK, GENERIC_FAILURE, NO_SERVICE, NULL_PDU, RADIO_OFF
- File: `service/SmsDeliveryReceiver.kt`

---

## RESPONSE TRACKING

### SmsResponseReceiver
- Listens for incoming SMS during active listen window
- Global window: starts at first send, duration = replyListenHours setting (1–24h)
- Per-contact window: 1 minute after first response from that contact
- Parses codes: "1"=Safe, "2"=Updates, "3"=Emergency
- Maps phrases: "safe"/"ok"→1, "urgent"/"help"/"sos"→3, "keep me updated"→2
- Priority: 3 > 2 > 1 (Emergency always wins, cannot be downgraded)
- Ignores messages from numbers not in recipients list
- Stores ResponseRecord in database
- File: `service/SmsResponseReceiver.kt`

### ResponseRecord
- scenarioId, phoneNumber, recipientName, responseCode (1/2/3), receivedAt
- Unique constraint on (scenarioId, phoneNumber) — one record per contact per scenario
- File: `model/ResponseRecord.kt`

---

## FORCE SEND ABUSE SYSTEM

### ForceSendAbuseTracker
Hidden point system that detects manual send abuse:

Point accrual per force send (based on time since last send):
- ≤ 20 seconds: +25 points
- ~1 minute: ~18 points
- ~3 minutes: ~4 points
- ≥ 5 minutes: +1 point (min)
- First ever send: +1 point

Passive decay:
- Normal: −1 point / 30 seconds
- At 75+ points: −1 point / 60 seconds
- At 3rd+ lockout: −1 point / 30 minutes

Tiers:
- 0 points: NONE
- 1–24: CAPTCHA_ONLY
- 25–74: MEDIUM_WARNING (banner: "for emergency use")
- 75–99: HIGH_WARNING (banner: "not for group messaging")
- 100+: HARD_LOCKOUT (timed block)

Lockout escalation:
- 1st: 1 hour, reset to 50 pts
- 2nd: 3 hours, reset to 75 pts
- 3rd+: 24 hours, extended slow decay, no reset

One-time override available on 1st lockout only (genuine emergency escape).
File: `utils/ForceSendAbuseTracker.kt`

---

## LOGGING SYSTEM

### AppLogger
- Async fire-and-forget logging to Room database
- Never blocks the caller
- Prunes to 500 entries (oldest deleted first)
- File: `utils/AppLogger.kt`

### LogEntry
- id, timestamp, eventType, description
- Displayed in Logs section of Dashboard
- File: `model/LogEntry.kt`

### PastAlert
- messageContent (500 char max), triggeredAt, source ("cell_broadcast"/"alert"/"notification"/"manual"), scenariosTriggered
- Source values: `"cell_broadcast"` = WEA/CMAS/ETWS broadcast; `"alert"` = EAS companion notification from known package; `"notification"` = keyword-triggered non-EAS notification; `"manual"` = force send
- Logged for ALL system emergency alerts, regardless of whether they triggered; non-EAS notifications only logged when they actually caused a trigger
- File: `model/PastAlert.kt`

### ContactSendHistory
- Tracks which contacts have been sent to (for dedup and history)
- File: `model/ContactSendHistory.kt`

### BlockPhrase
- User-defined phrase that suppresses a keyword trigger even when keywords match
- Evaluated in `FalseAlarmDetector.isBlockedDespiteKeywordMatch(content, userBlockPhrases)` for keyword-gated scenarios
- Any language supported
- File: `model/BlockPhrase.kt`

---

## PERSISTENCE LAYER

### AppDatabase (Room)
- Entities: Scenario, ResponseRecord, PastAlert, ContactSendHistory, LogEntry, BlockPhrase
- Version: 8
- Migrations: v6→v7 (groups column), v7→v8 (app_logs table)
- No destructive migration fallback — explicit migrations only; schema exported to `app/schemas/`
- File: `model/AppDatabase.kt`

### AppSettings (DataStore)
- IS_FIRST_LAUNCH: Boolean (default true)
- DEBUG_ENABLED: Boolean (default false)
- FAILURE_RATE: Double (default 0.0)
- FORCE_SEQUENTIAL: Boolean (default false)
- WIDE_SPREAD_ENABLED: Boolean (default false)
- LAST_SCENARIO_ID: String (default "")
- THEME: String ("SYSTEM"/"LIGHT"/"GRAY"/"NIGHT")
- REPLY_LISTEN_HOURS: Int (default 1, range 1–24)
- PRESETS_OFFERED: Boolean (default false)
- ALERT_SENSITIVITY: String ("HIGH"/"MEDIUM"/"LOW", default "MEDIUM") — controls FalseAlarmDetector threshold for wildcard scenarios
- File: `utils/AppSettings.kt`

---

## UI LAYER

### MainActivity
- Reads isFirstLaunch from uiState
- If true: shows FirstLaunchScreen (3-page onboarding)
- If false: PermissionHandler + MainScreen
- Theme applied from settings (SYSTEM/LIGHT/GRAY/NIGHT)
- File: `ui/MainActivity.kt`

### MainViewModel
- Central state manager for all UI state
- Holds: MainUiState (current scenario, theme, send state, abuse level, etc.)
- Exposes: allResponses, pastAlerts, logs as StateFlows
- Handles: scenario CRUD, group CRUD, send initiation, abuse tracking, settings sync
- File: `ui/MainViewModel.kt`

### MainScreen
- Tab 0 (Alert System): scenario dropdown, trigger input, groups section, force send slider
- Tab 1 (Dashboard): ResponseDashboard inline
- Bottom: BrowserTabBar with tab weights animated via animateFloatAsState
- Undo popup (bottom-center, slides up)
- File: `ui/MainScreen.kt`

### ResponseDashboard
- Shows: stat cards (Safe/Updates/Urgent), progress bar, per-recipient rows
- Listening timer with Stop button
- Collapsible Logs (default collapsed, user-controlled)
- Collapsible Alert History (default collapsed)
- On Standby state: Surface card with pulsing green dot
- File: `ui/ResponseDashboard.kt`

### SettingsDialog
- APPEARANCE: theme dropdown (52dp height)
- DETECTION: wide spread toggle, alert sensitivity, reply listen hours
- USER MANUAL: expandable accordion (6 sections) + Replay Tutorial button; covers Alert Filters (Activation Keywords + Block Phrases)
- DEBUG (non-production only): debug toggle, force sequential, simulation tools
- File: `ui/SettingsDialog.kt`

### FirstLaunchScreen
- 3-page HorizontalPager (non-swipeable, manual navigation)
- Page 1 (WelcomePage): intro, About/FAQ/Disclaimer dialogs, Skip button
- Page 2 (PermissionsPage): 6 permission cards, Skip button
- Page 3 (ReadyPage): confirmation, Launch App button
- Polls notification access + battery opt every 1 second
- File: `ui/FirstLaunchScreen.kt`

---

## DATA FLOW

```
Cell Broadcast / Notification / Manual
         ↓
EmergencyPackageDetector (trust level)
         ↓
FalseAlarmDetector (8-step)
         ↓
Scenario matching (ALL scenarios evaluated independently)
         ↓
MessageQueueManager (enqueue per group)
         ↓
AdaptiveSendController (MULTI_THREADED → SEQUENTIAL → LAZARUS)
         ↓
RateLimiter (150ms minimum)
         ↓
SmsSender / MockSmsSender
         ↓
SmsDeliveryReceiver (delivery confirmation)
         ↓
SmsResponseReceiver (1/2/3 replies, configurable window)
         ↓
ResponseRecord (database) → ResponseDashboard (UI)
         ↓
AppLogger + PastAlert (always logged, always visible)
```

---

## ARCHITECTURE RULES

- NEVER duplicate logic — reuse existing systems
- Logging BEFORE filtering — always
- Detection must evaluate ALL scenarios independently (not just current)
- Scenario locking is permanent until manual reset
- UI must reflect actual system state — no fake states
- Soft failures everywhere — no crash under any input
- Both entry points (broadcast + notification) share the same detection + queue path

---

## FINAL PRINCIPLE

Simple systems are reliable systems.

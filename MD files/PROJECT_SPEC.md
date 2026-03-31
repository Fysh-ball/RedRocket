# PROJECT_SPEC.md

## Project Name
Red Rocket — Emergency Auto-Messaging System

---

## OVERVIEW

Red Rocket is an Android emergency response automation tool. It listens for emergency alerts, evaluates them against user-defined scenarios, and automatically sends pre-written SMS messages to selected contacts. After sending, it tracks responses and displays them on a live dashboard.

All sending happens through the device's native SMS system. No cloud service, no account, no internet required.

---

## CORE GOALS

1. Send emergency messages fast — minimal user action required during a crisis
2. Avoid false triggers — never fire on test alerts or amber alerts
3. Retry until delivered — Lazarus mode ensures delivery even under network stress
4. Track responses — know who is safe, who needs updates, who is in danger
5. Full transparency — every alert logged, every action visible

---

## SYSTEM INVENTORY

### 1. DETECTION SYSTEM

**Entry points:**
- `EmergencyBroadcastReceiver` — cell broadcast (WEA/CMAS/ETWS)
- `EmergencyNotificationListener` — all system notifications (wide spread optional)

**Pipeline:**
1. Receive alert content
2. Filter system noise (audio routing, own notifications)
3. Determine trust level via `EmergencyPackageDetector`
4. Run through `FalseAlarmDetector` (7-step pipeline)
5. If triggered: enqueue all valid groups from matching scenarios

**Key behavior:**
- ALL scenarios are evaluated independently for every alert
- Triggered scenarios are locked immediately to prevent re-trigger
- Every system emergency alert is logged to PastAlert regardless of outcome

Full rules: see DETECTION_RULES.md

---

### 2. SCENARIO SYSTEM

A Scenario is the top-level unit. Each scenario has:
- **Name**: user-defined label
- **Trigger keywords**: comma-separated phrases that FalseAlarmDetector uses for keyword matching
- **Groups**: one or more groups, each with its own recipients and message
- **isLocked**: true after triggering — must be manually reset
- **isFavorite**: protected from deletion

Multiple scenarios can exist. All valid, unlocked scenarios fire simultaneously when a matching alert arrives.

---

### 3. GROUP SYSTEM

A Group is a sub-unit of a Scenario. Each group has:
- **Recipients**: list of phone numbers/contacts
- **Message**: the SMS text to send to those recipients
- **isFavorite**: protected from deletion

Groups can be reordered by drag. Trigger keywords are shared across all groups in a scenario.
When a scenario fires, ALL valid groups (non-empty recipients + non-empty message) are queued simultaneously.

---

### 4. MESSAGING SYSTEM

#### Queue
- `MessageQueueManager`: thread-safe mutex-protected dual queue (primary + retry)
- Up to 5 retries before permanent failure
- Lazarus mode: unlimited retries

#### Adaptive sending strategy (`AdaptiveSendController`)
Three states:
- **MULTI_THREADED**: parallel, 0ms base delay (150ms floor via RateLimiter)
- **SEQUENTIAL**: serial, 200ms delay
- **LAZARUS**: retry mode, 200ms delay

Transitions: 5 consecutive failures → SEQUENTIAL. 5 consecutive successes → back to MULTI_THREADED.

#### Rate limiter
- Minimum 150ms between every send (carrier compliance)

#### Lazarus retry system
- Activates when primary queue exhausted but failures remain
- Infinite retry — no cap
- 5-second pause between retry passes
- Exits when retry queue empty AND Keep Trying = OFF

#### SMS sending
- Real sends: `SmsSender` via Android SmsManager
- Debug sends: `MockSmsSender` with configurable failure rate
- Every message appended with: "Reply: 1=Safe, 2=Safe+updates, 3=EMERGENCY"
- Long messages split into multipart SMS automatically

---

### 5. FORCE SEND SYSTEM

The user can manually trigger a send via the "Swipe to Force Send" slider.

**Guards:**
1. **Captcha**: 6-character alphanumeric OTP must be entered correctly
2. **Countdown**: 5-second countdown with cancel option
3. **Abuse tracking**: hidden point system (see below)

#### Force Send Abuse Tracker

Points accrue on each force send based on how fast sends are happening:
- Rapid sends (≤20s apart): +25 points each
- Slow sends (≥5 min apart): +1 point each

Passive decay: −1 point every 30 seconds normally; slower at high points or after lockouts.

| Points | Restriction |
|---|---|
| 0 | None |
| 1–24 | Captcha required |
| 25–74 | Captcha + usage warning banner |
| 75–99 | Captcha + strong warning banner |
| 100+ | Hard lockout (timed block) |

Lockout escalation: 1st = 1 hour, 2nd = 3 hours, 3rd+ = 24 hours with extended slow decay.
One-time emergency override available on the 1st lockout only.

---

### 6. RESPONSE TRACKING SYSTEM

After sending, `SmsResponseReceiver` listens for replies within a configurable window.

**Response codes:**
- 1 = Safe
- 2 = Safe + wants updates
- 3 = EMERGENCY

**Phrase mapping:**
- "safe", "ok" → 1
- "urgent", "help", "sos" → 3
- "keep me updated" → 2

**Priority rule:** 3 overrides 2 overrides 1 — once urgent, always urgent.

**Windows:**
- Global: starts at first send, lasts replyListenHours setting (1–24h)
- Per-contact: 1 minute after their first response (prevents duplicate counting)

**Data stored:** `ResponseRecord` with unique index on (scenarioId, phoneNumber).

---

### 7. LOGGING SYSTEM

#### Logs (system events)
- All detection decisions, trigger events, send outcomes, retry activity
- Stored as `LogEntry` in Room database
- Pruned to 500 entries
- Visible in Dashboard → Logs section (collapsible, default collapsed)

#### Alert History (PastAlert)
- Every system emergency alert regardless of trigger outcome
- Source: "cell_broadcast", "notification", or "manual"
- Stores triggered scenario names
- Visible in Dashboard → Alert History (collapsible, default collapsed)

---

### 8. SETTINGS SYSTEM

Stored in DataStore (`AppSettings`):

| Key | Default | Description |
|---|---|---|
| IS_FIRST_LAUNCH | true | Show onboarding on first run |
| THEME | SYSTEM | Color theme |
| WIDE_SPREAD_ENABLED | false | Detect from all apps, not just system |
| REPLY_LISTEN_HOURS | 1 | Response listening window (1–24h) |
| DEBUG_ENABLED | false | Use mock SMS, show debug tools |
| FAILURE_RATE | 0.0 | Simulated failure rate (debug only) |
| FORCE_SEQUENTIAL | false | Lock to sequential sending |
| LAST_SCENARIO_ID | "" | Restore last selected scenario |
| PRESETS_OFFERED | false | Whether preset dialog has been shown |

---

### 9. ONBOARDING SYSTEM

`FirstLaunchScreen` — shown on first launch (isFirstLaunch = true):
- Page 1: Welcome + About/FAQ/Disclaimer + Skip option
- Page 2: 6 permission cards (SMS, Contacts, Notifications, Notification Access, Battery Opt, Receive SMS) + Skip option
- Page 3: Confirmation screen

Completing or skipping sets isFirstLaunch = false.
Tutorial can be replayed via Settings → User Manual → Replay Setup Tutorial (resets isFirstLaunch = true).

---

## UI STRUCTURE

### Tab 0 — Alert System

Top to bottom:
1. "Alert System" header
2. Scenario dropdown (manage/reorder/rename/delete scenarios)
3. Debug mode banner (non-production, when enabled)
4. Abuse warning banner (when points ≥ 25)
5. Notification permission warning (if not granted)
6. Trigger section (keyword chips + add button → preset picker → keyword sheet)
7. Groups section (group selector + recipients + message per group)
8. Scenario locked banner (when locked, with Reset button)
9. Cooldown timer (60s between sends)
10. Swipe to Force Send (or cooldown indicator)

### Tab 1 — Dashboard

Top to bottom:
1. "Dashboard" header
2. Listening timer + Stop button (when active)
3. On Standby card (when inactive, recipients configured)
4. No Recipients empty state (when no contacts)
5. Stat cards: Safe / Updates / Urgent
6. Progress bar (responded / total)
7. Recipient list (per-contact status)
8. Clear Responses button
9. Logs section (collapsible, default collapsed)
10. Alert History section (collapsible, default collapsed)

### Settings (dialog)
Opened via gear icon in tab bar.

---

## PERFORMANCE GOALS

- Minimal battery usage (event-driven, no polling)
- Efficient background listening via registered services
- No unnecessary database reads on main thread
- Async logging never blocks sender

---

## RELIABILITY REQUIREMENTS

- Must not crash under any input
- Must work while device is locked (within Android background limits)
- Must fail gracefully if SMS restricted
- No data lost if app closed during send (queue persists in service)

---

## DESIGN PHILOSOPHY

Simple under stress. Clear at a glance. No ambiguity. No hidden behavior.

This is not a messaging app. It is a real-time emergency tool.
Every decision must prioritize: Speed. Clarity. Reliability.

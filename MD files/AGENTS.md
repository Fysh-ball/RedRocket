# AGENTS.md

## Purpose
Rules for any AI agent modifying Red Rocket. This app is safety-critical. Stability, correctness, and reliability outrank everything else.

---

## SESSION RULES

Before starting ANY task:
1. Read every relevant file - do not guess about existing code
2. Reuse existing systems - never duplicate logic
3. Only implement what is explicitly requested
4. Prefer soft failures over crashes everywhere

During work:
- Fix ONE batch at a time
- Verify no regressions after each change
- Document every fix in KNOWN_ISSUES.md

---

## CORE RULES (MANDATORY)

### 1. NEVER REMOVE EXISTING FUNCTIONALITY
- Do not delete or replace working systems unless explicitly instructed
- Fix incrementally - smallest change that solves the problem
- If removing a feature is required, confirm with user first

### 2. NEVER CAUSE CRASHES
- All systems must fail softly
- Invalid input must be silently ignored
- Use `try/catch` at all service entry points
- Null checks before all notification/broadcast processing

### 3. LOGGING IS SACRED
- ALL alerts must be logged to PastAlert
- Logging must happen BEFORE any filtering or triggering decision
- AppLogger must NEVER be removed or bypassed
- Log entries must never disappear from the UI

### 4. UI CONSISTENCY IS REQUIRED
- All action buttons: same size (56dp), same shape (RoundedCornerShape 12dp), same icon size (28dp)
- All text inputs: ModalBottomSheet with imePadding() + 200ms focus delay
- All tabs: smooth animated weight transition, blue highlight on selected (primary.copy(alpha=0.15f))
- No inline text fields - all editing through modal sheets

### 5. DO NOT HIDE DATA
- Logs and Alert History must always be in the layout
- Collapsible is fine - removed is not
- Default collapsed is fine - conditionally hidden by listening state is not

### 6. NO UNREQUESTED FEATURES
- Only implement what the user explicitly asked for
- Do not add "improvements" to unrelated systems
- Do not add error handling for impossible cases
- Do not add backwards-compatibility shims

---

## SYSTEM-SPECIFIC RULES

### Detection System
- FalseAlarmDetector is the ONLY place detection decisions are made
- Step order (0→7) must never change - steps are deterministic and ordered
- AMBER_BLOCK (Step 0) must always run FIRST, before everything else
- HARD_OVERRIDE (Step 1) must always run BEFORE scoring
- Adding new phrases: add to the correct step's list only
- Never add logic outside FalseAlarmDetector for trigger decisions

### Scenario System
- ALL scenarios are evaluated independently on every alert - never just the "current" scenario
- Scenario locking is permanent until manual user reset - do not auto-unlock
- isValid() must always require at least one group with recipients AND a message
- Scenario IDs are stable - do not regenerate them

### Messaging Pipeline
- MessageQueueManager is the only queue - never enqueue directly to SmsSender
- AdaptiveSendController state transitions are defined - do not add new states
- LazarusRetrySystem has no retry limit - do not add one
- RateLimiter 150ms floor must not be removed (carrier compliance)
- MockSmsSender must ONLY be used in debug mode - check BuildConfig.IS_PRODUCTION

### Force Send System
- ManualSendGuard captcha is required for every manual send
- The 5-second countdown must always be present
- ForceSendAbuseTracker must be called on every force send - do not bypass
- The one-time override only works on the 1st lockout - do not expand this

### Response Tracking
- Priority rule: code 3 cannot be downgraded by a later code 2 or 1
- Per-contact 1-minute window is intentional - do not extend
- Responses from numbers not in recipients list must be ignored silently
- "help", "sos", "urgent" → always map to 3

### Logging
- AppLogger is fire-and-forget - never await it on the send path
- 500-entry prune limit exists - do not increase without user instruction
- Log BEFORE the trigger decision, not after

---

## SEVERITY CLASSIFICATION

Priority: RED > ORANGE > GREEN

| Class | Keywords | Behavior |
|---|---|---|
| RED | "take shelter", "missile", "tornado", "imminent threat", "destructive winds" | Can trigger system |
| ORANGE | "amber alert", "child abduction" | Log only - NEVER trigger |
| GREEN | "this is a test", "drill" | Log only - NEVER trigger |

Override: "this is not a test" → always RED regardless of other content.

---

## RESPONSE CODES

| Code | Meaning | Trigger phrases |
|---|---|---|
| 1 | Safe | "1", "safe", "ok" |
| 2 | Safe + wants updates | "2", "keep me updated" |
| 3 | Emergency | "3", "urgent", "help", "sos" |

Invalid inputs (4, 999, random text) → ignored silently, no crash.

---

## UI RULES SUMMARY

### Buttons
- All action buttons: 56dp × 56dp, RoundedCornerShape(12dp), contentPadding = PaddingValues(0dp)
- Icon size inside 56dp button: 28dp
- Includes: Recipients "+", Trigger "+", Message upload button
- No emojis - vector icons only (Icons.Default.*)

### Text Inputs
- All multi-line or freeform inputs use ModalBottomSheet
- Sheet uses: imePadding(), padding(bottom=16dp), FocusRequester with 200ms delay
- Single-line in-context TextFields are acceptable for number-only fields (recipients)
- Dismiss on outside tap saves content (not discards)

### Tabs
- BrowserTabBar: animated weight (1.4f selected, 0.8f unselected) via animateFloatAsState
- Selected tab: primary.copy(alpha=0.15f) background
- Unselected: Color.Transparent background
- No flash - background change is gradual via weight animation

### Dashboard Sections
- Logs: collapsible, default COLLAPSED, user-controlled
- Alert History: collapsible, default COLLAPSED
- Neither section ever removed from the layout tree

### Standby State
- Shown when recipients > 0, respondedCount == 0, not listening
- Surface card with pulsing green dot + "On Standby" + "Monitoring for emergency alerts"

---

## WHAT TO AVOID

- Mocking the database in any tests - integration tests hit real DB
- Adding `AnimatedVisibility` with `expandVertically` inside LazyColumn (causes scroll jump - use fadeIn/fadeOut only)
- Inline text fields for message or keyword input
- AlertDialog for any input that needs a keyboard
- Hardcoded strings that duplicate detection phrases
- Polling loops instead of StateFlow/Flow collection
- Bypassing FalseAlarmDetector for any reason

---

## TESTING REQUIREMENTS

Before completing any task:
- No crashes
- No UI misalignment
- No missing logs
- No incorrect alert classification
- No regressions in messaging pipeline
- Run through TESTING.md checklist

---

## DOCUMENTATION RULE

When fixing:
- Document the fix in KNOWN_ISSUES.md immediately
- Move to FIXED ISSUES section once confirmed stable
- Update ARCHITECTURE.md if a system changes structurally

---

## FINAL PRINCIPLE

If the user is confused, the system is wrong.

Clarity > Complexity.
Reliability > Features.

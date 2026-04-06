# TESTING.md

## Purpose
Defines how to test Red Rocket. Every system must be verified before any release or after any significant change.

---

## TESTING RULES

1. NEVER skip tests after making changes
2. Test on a REAL DEVICE - not emulator only
3. Test with device LOCKED and UNLOCKED
4. Test BOTH triggered and non-triggered paths
5. If ANY crash occurs → fix before continuing

---

## TEST CATEGORIES

1. Crash Safety
2. Detection Pipeline
3. False Alarm Prevention
4. Messaging Pipeline
5. Response Tracking
6. Force Send & Abuse System
7. Adaptive Send & Lazarus
8. UI / UX
9. Logging & Alert History
10. Edge Cases
11. Performance

---

## 1. CRASH SAFETY (CRITICAL)

Test every invalid input path:

**Response inputs:**
- "1" → Safe logged
- "2" → Updates logged
- "3" → Emergency logged
- "4" → ignored, no crash
- "999" → ignored, no crash
- "0" → ignored, no crash
- "" (empty) → ignored, no crash
- Random text → ignored, no crash
- Very long string → ignored, no crash

**Notification content:**
- Null title → handled, no crash
- Null text → handled, no crash
- Empty notification → skipped cleanly

**Scenarios:**
- Scenario with no groups → isValid() = false, not triggered
- Scenario with groups but no recipients → isValid() = false, not triggered
- Scenario with no message → isValid() = false, not triggered
- Scenario with all fields → isValid() = true

---

## 2. DETECTION PIPELINE

Test each FalseAlarmDetector step:

**Step 1 - Hard Override (trusted source + extreme danger):**
- Simulated cell broadcast: "Ballistic missile inbound. Seek immediate shelter." → TRIGGER
- Simulated cell broadcast: "Nuclear threat detected. Evacuate immediately." → TRIGGER

**Step 2 - Override Phrase:**
- "This is not a test. Take shelter." → TRIGGER regardless of source

**Step 3 - Hard Block:**
- "This is a test of the emergency alert system" → NOT trigger, log as GREEN
- "This is only a test" → NOT trigger
- "Drill in progress" → NOT trigger

**Step 4–5 - Score:**
- "Take shelter now. Destructive winds approaching. EMERGENCY." → TRIGGER (score ≥ 6)
- "Weather advisory: some gusty winds possible" → NOT trigger (score < 6)
- User keyword present + moderate action phrase → TRIGGER

**Step 6 - Structural Fail-Safe:**
- Trusted source + short message with "emergency" and location → TRIGGER

---

## 3. FALSE ALARM PREVENTION

- EAS test broadcasts → logged as GREEN, never trigger
- AMBER alerts → pass through the pipeline like any other alert; a scenario with "amber alert" as a keyword will trigger on them
- "This is not a test" in same message as "this is a test" → TRIGGER (Step 2 wins)
- Weather watch/advisory without action language → NOT trigger
- System audio routing notification ("You're hearing another device") → silently ignored, not logged

---

## 4. MESSAGING PIPELINE

**Queue:**
- Enqueue 1 message → processes correctly
- Enqueue 10 messages → all process without loss
- Enqueue then close app → service continues in foreground

**Adaptive states:**
- Simulate 5 consecutive failures → verify state switches to SEQUENTIAL
- Simulate 5 consecutive successes from SEQUENTIAL → verify returns to MULTI_THREADED
- Force sequential toggle → verify stays in SEQUENTIAL regardless of success/failure count

**Rate limiter:**
- Verify minimum 150ms between sends (check log timestamps)

**Mock sending (debug):**
- Set failure rate 0% → all succeed
- Set failure rate 50% → approximately half fail
- Set failure rate 100% → all fail, Lazarus activates

---

## 5. RESPONSE TRACKING

**Basic codes:**
- Send "1" from recipient → Safe count increases, per-contact shows "Safe"
- Send "2" from recipient → Updates count increases
- Send "3" from recipient → Urgent count increases, card pulses red

**Priority:**
- Contact sends "1" then "3" → stays at Urgent (3 cannot be downgraded)
- Contact sends "3" then "2" → stays at Urgent

**Phrase mapping:**
- "safe" → Safe (1)
- "ok" → Safe (1)
- "help" → Urgent (3)
- "sos" → Urgent (3)
- "urgent" → Urgent (3)

**Non-recipient rejection:**
- Message from unknown number → ignored, no crash, not added to dashboard

**Window behavior:**
- After replyListenHours expires → responses no longer accepted
- Stop Listening button → window closes immediately, responses no longer accepted

**Dashboard:**
- Progress bar updates correctly as responses come in
- Elapsed listen time ticks correctly (HH:MM:SS)

---

## 6. FORCE SEND & ABUSE SYSTEM

**Captcha:**
- Correct captcha → proceed to countdown
- Wrong captcha → rejected, new captcha generated
- Cancel from captcha dialog → no send initiated

**Countdown:**
- 5 seconds display correctly
- Cancel during countdown → no messages sent

**Abuse tracker:**
- Send rapidly (< 20s apart) → points accumulate at 25/send
- Send slowly (> 5 min apart) → points accumulate at 1/send
- Verify MEDIUM_WARNING banner at 25+ points
- Verify HIGH_WARNING banner at 75+ points
- Verify hard lockout at 100+ points (send button blocked)
- Wait for passive decay → points reduce, restrictions lift
- 1st lockout: one-time override button visible, clears lockout when used
- 2nd lockout: no override available, 3-hour duration
- 3rd+ lockout: 24-hour duration, very slow decay

---

## 7. ADAPTIVE SEND & LAZARUS

- Start with clean queue, all succeeds → MULTI_THREADED throughout
- Force 5 failures → transitions to SEQUENTIAL, verify in logs
- 5 successes from SEQUENTIAL → transitions back to MULTI_THREADED
- All primary queue fails → Lazarus activates
- Keep Trying = ON → Lazarus continues retrying indefinitely
- Keep Trying = OFF → Lazarus stops after current pass
- Messages eventually deliver on retry → success count increments
- Status notification updates per message state

---

## 8. UI / UX

**Buttons:**
- Recipients "+", Trigger "+", Upload button → all 56×56dp, identical appearance
- No misalignment in any screen orientation

**Text inputs:**
- Tap message box → ModalBottomSheet slides up, keyboard appears, field focused
- Tap trigger chip area → opens preset picker (empty) or keyword sheet (has keywords)
- Dismiss by tapping outside → content saved
- Long text entry → no cursor jump, no text reset

**Tabs:**
- Click Alert System → smooth weight animation, blue highlight on tab
- Click Dashboard → smooth animation, unread dot clears
- No flash, no stutter

**Dashboard:**
- No recipients: "No recipients added" empty state
- Recipients configured, not listening: "On Standby" card with pulsing dot
- Actively listening: timer + Stop button at top
- Logs section: collapsed by default, expand/collapse works
- Alert History section: collapsed by default, expand/collapse works
- Clear buttons work correctly

**Undo popup:**
- Appears after recipient removed / keyword removed
- Auto-dismisses in 5 seconds
- Undo restores state
- Close button dismisses immediately

---

## 9. LOGGING & ALERT HISTORY

- Every EAS alert logged to PastAlert (triggered OR not)
- Every notification alert logged when system emergency package
- Logs section shows system events with timestamps
- Alert History shows all past alerts with source + triggered scenarios
- Clearing logs → Logs section shows empty state
- Clearing alerts → Alert History shows empty state
- Neither section disappears from layout

---

## 10. EDGE CASES

**Locked device:**
- Trigger alert with screen locked → detection fires, messages sent if permissions allow

**Scenario locked:**
- Alert arrives matching locked scenario → silently skipped (scenario must be manually reset)
- Unlock via Reset button → scenario fires on next matching alert

**Multiple rapid alerts:**
- Two alerts arrive within 1 second → both evaluated, both logged, no crash

**Multiple scenarios:**
- 3 scenarios configured, 2 match → both trigger independently, all groups enqueued

**Bilingual alert (EN/FR):**
- "Alerte: prendre un abri maintenant" → detected correctly via French phrases
- Mixed EN/FR alert → both sets of phrases evaluated

**Empty scenario list:**
- No scenarios → no crash on alert receipt
- First scenario auto-created with default content

**Database corruption / migration:**
- App starts fresh → migrations run, fallback to destructive if needed, no crash

---

## 11. PERFORMANCE

- App running 30+ minutes in background → no excessive battery drain
- Notification listener active → responds within 2 seconds of alert
- 50-message queue → processes without UI lag
- 500 log entries → Logs section scrolls smoothly, pruning works

---

## FINAL CHECKLIST (pre-release)

- [ ] No crashes on any tested input
- [ ] All alerts logged regardless of trigger outcome
- [ ] Test alerts (GREEN) never trigger
- [ ] AMBER alerts trigger when a matching keyword is configured
- [ ] "This is not a test" always triggers
- [ ] Lazarus retries indefinitely with Keep Trying ON
- [ ] Adaptive state transitions work correctly
- [ ] Captcha required for every force send
- [ ] Abuse lockout activates at 100 points
- [ ] Response priority (3 > 2 > 1) enforced
- [ ] All buttons identical size and style
- [ ] All text inputs use ModalBottomSheet
- [ ] Tabs animate smoothly, no flash
- [ ] Logs/Alert History always in layout
- [ ] Works with device locked

---

## FAILURE RULE

If ANY test fails:
→ DO NOT proceed
→ Fix immediately
→ Re-test ALL related systems

If it fails under stress, it fails the user. Test like it matters - because it does.

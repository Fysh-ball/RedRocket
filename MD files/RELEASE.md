# RELEASE.md

## Purpose
Defines the process for safely releasing new versions of Red Rocket.

This app is safety-critical. A bad release can cause:
- False alerts
- Missed alerts
- App crashes during emergencies
- Messages sent to wrong contacts
- Response tracking failures

---

## RELEASE RULES

1. NEVER release without full TESTING.md pass
2. NEVER release after any detection or messaging change without real-device testing
3. ALWAYS verify both product flavors (dev + production) before release
4. If unsure → DO NOT RELEASE

---

## PRODUCT FLAVORS

This project has two build flavors:

| Flavor | Package ID | IS_PRODUCTION | Send delay |
|---|---|---|---|
| dev | com.example.nuclearattackautomessageconcept.dev | false | 0ms / 200ms |
| production | com.example.nuclearattackautomessageconcept | true | 1000ms / 2000ms |

Both flavors use the same source code from `app/src/main/`.
Always build and install BOTH after significant changes to avoid stale production APK on device.

To switch variant in Android Studio: `Build → Build Variants → productionDebug`

---

## PRE-RELEASE CHECKLIST

All items must pass:

**Detection:**
- [ ] Amber alerts do NOT trigger
- [ ] Test alerts do NOT trigger (log as GREEN)
- [ ] "This is not a test" always triggers (RED)
- [ ] Real EAS messages trigger correctly
- [ ] Bilingual (EN/FR) alerts detected
- [ ] System audio routing notifications ignored silently

**Messaging:**
- [ ] Messages send in MULTI_THREADED mode successfully
- [ ] Adaptive state switches on 5 failures
- [ ] Lazarus retries indefinitely with Keep Trying ON
- [ ] Rate limiter 150ms floor intact
- [ ] Production build uses 1000ms/2000ms delays (not 0ms)
- [ ] MockSmsSender NOT active in production builds

**Force Send:**
- [ ] Captcha required every time
- [ ] 5-second countdown present
- [ ] Abuse tracker fires at correct thresholds

**Response System:**
- [ ] 1/2/3 codes tracked correctly
- [ ] Priority: 3 > 2 > 1 enforced
- [ ] Listen window stops at configured duration
- [ ] Stop Listening button works

**Logging:**
- [ ] All alerts logged to PastAlert (triggered AND non-triggered)
- [ ] System events logged to Logs
- [ ] Neither section disappears from layout

**UI:**
- [ ] No crashes on any input
- [ ] All buttons 56×56dp and identical
- [ ] All text inputs use ModalBottomSheet
- [ ] Tabs animate correctly with highlight
- [ ] No layout gaps or scroll issues
- [ ] Works with device locked (or shows warning if restricted)

---

## RISKY CHANGES — EXTRA TESTING REQUIRED

If you modify any of these systems, re-test ALL related systems:

| System | Files | Risk |
|---|---|---|
| Detection logic | FalseAlarmDetector.kt | False triggers or missed alerts |
| Notification listener | EmergencyNotificationListener.kt | Alerts not received |
| Broadcast receiver | EmergencyBroadcastReceiver.kt | Cell broadcasts missed |
| Message queue | MessageQueueManager.kt | Messages lost or duplicated |
| Adaptive controller | AdaptiveSendController.kt | Wrong send mode, timing issues |
| Lazarus system | LazarusRetrySystem.kt | Retries stuck or halted |
| Response parser | SmsResponseReceiver.kt | Wrong status, missed responses |
| Rate limiter | RateLimiter.kt | Carrier blocks |
| Abuse tracker | ForceSendAbuseTracker.kt | Lockout at wrong threshold |
| AppSettings keys | AppSettings.kt | Settings lost on upgrade |
| Database schema | AppDatabase.kt | Data loss on migration |

---

## REAL DEVICE TEST (MANDATORY)

Test on:
- Primary device
- At least one other Android device if possible

Verify:
- Notifications received and processed correctly
- SMS sends complete (use debug mode with mock sender, or test recipient)
- Background behavior works (screen off, app minimized)
- Response tracking works end-to-end (send → receive reply → dashboard updates)

---

## RELEASE STEPS

1. Run full TESTING.md checklist
2. Fix all failing tests
3. Switch to `productionDebug` build variant
4. Build APK
5. Install on real device
6. Smoke test: detection, send, response, UI
7. Switch back to `devDebug` for continued development
8. Tag the release in version control if using git

---

## ROLLBACK RULE

If a release causes:
- Crashes in production
- Missed alerts in real emergency scenario
- Wrong triggers (false alarm)
- Messages not sending

→ IMMEDIATELY revert to previous known-good APK
→ Document the failure in KNOWN_ISSUES.md
→ Fix root cause before re-releasing

---

## FINAL PRINCIPLE

A delayed release is better than a broken one.

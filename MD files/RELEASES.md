# Red Rocket — Public Release Notes

Public-facing release notes for each version. These are what users see on GitHub.

---

## v2.0.2 — Reliability Update (2026-04-06)

### Bug Fixes

- **Fixed a race condition in the response listener:** Two simultaneous SMS responses from the same contact could both open separate 1-minute reply windows. The per-contact window is now set atomically, so only the first response starts the clock.
- **Fixed response listening state not persisting correctly on sudden process death:** The "stop listening" flag is now written synchronously to storage so it cannot be reverted by a process crash occurring in the brief window after stopping.
- **Fixed coroutine cancellation being swallowed in the SMS response receiver:** Cancellation signals were caught by a broad exception handler and silently discarded. They are now re-thrown correctly.
- **Fixed potential indefinite hang when the database is slow at trigger time:** Database queries in the notification listener and SMS receiver now time out after 5 seconds instead of waiting forever.
- **Fixed phone number validation accepting `+` in invalid positions:** A `+` sign is now only accepted as an international dialing prefix (e.g. `+1...`), not mid-number.
- **Improved visibility when SMS is unavailable:** If the device cannot send SMS, the reason is now recorded in the in-app activity log instead of being silently swallowed.
- **Improved visibility when a locked scenario skips a trigger:** The in-app activity log now records when a trigger fires but is ignored because the scenario is locked.

---

## v2.0.1 — Hotfix (2026-04-06)

### Bug Fixes

- **Fixed critical trigger bug:** Non-emergency app notifications (YouTube, social media, news apps, etc.) can no longer trigger a scenario regardless of keyword matches. Only official device emergency alert packages and government-issued wireless emergency alerts are valid trigger sources.
- **Fixed import not switching to imported scenario:** After importing a backup, the app now correctly switches to the imported scenario instead of staying on the empty default one. Contacts now appear in the recipients box immediately after import.
- **Fixed app crash when tapping a recipient chip on devices without an SMS app** (tablets, restricted enterprise devices)

---

## v2.0 — The Global Update (2026-04-06)

Red Rocket now speaks your language. Whether you're in Japan, Brazil, the Philippines, or anywhere in between, v2.0 brings regional alert presets, local dial codes, and detection keywords in 22 languages across 14 disaster categories so Red Rocket is ready for whatever emergency alert system your country uses.

### New Features

- **Regional trigger presets:** 14 alert categories (earthquakes, tsunamis, nuclear, AMBER, and more) with keywords in 22 languages. Tap a preset to add it, tap again to remove it.
- **Region picker with search:** find your country instantly instead of scrolling a full list
- **Home screen widget:** see Red Rocket's current status at a glance without opening the app
- **Scenario export and import:** clone your entire setup to a new device — scenarios, groups, contacts, keywords, block phrases, and all settings transfer in one file. Red Rocket also auto-saves a backup whenever your scenarios change. You can choose where auto-backups are saved in Settings.
- **In-app update notifications:** get notified when a new version is available
- **Test send:** send a test message to any number to verify everything is working
- **Boot recovery:** Red Rocket restores its state automatically after a device reboot
- **Battery optimization prompt:** get reminded to exempt Red Rocket from battery killers so it never misses an alert
- **AMBER alert support:** AMBER alerts are no longer hard-blocked. Add "amber alert" as an activation keyword if you want your scenario to trigger on them.

### Improvements

- Response dashboard now shows who has not replied during an active listening window
- Preset rows toggle on second tap instead of requiring you to remove chips manually
- Text fields now support proper text selection and deletion
- All text and UI elements have been resized for better readability on all screen sizes
- Support buttons added in Settings (Ko-fi and GitHub)

### Bug Fixes

- Fixed duplicate rows appearing in the response dashboard
- Fixed race condition that could cause two callers to both reset contact state on listen start
- Fixed app crash on corrupted or partially written database columns
- Fixed send count race condition on rapid concurrent sends
- Fixed spurious "Debug Mode OFF" notification appearing when toggling debug mode
- Fixed clock-jump causing a massive one-shot abuse tracker decay

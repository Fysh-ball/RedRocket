# Red Rocket — Public Release Notes

Public-facing release notes for each version. These are what users see on GitHub.

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

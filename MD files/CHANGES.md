# Red Rocket v2.02 Beta - Change Log

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

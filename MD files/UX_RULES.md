# UX_RULES.md

## Purpose
Defines strict UX rules to maintain clarity and consistency across every screen.

---

## CORE UX RULE

If a user hesitates, the UI is wrong.

---

## BUTTONS

ALL action buttons must match exactly:
- Size: 56dp × 56dp
- Shape: RoundedCornerShape(12dp)
- Content padding: PaddingValues(0dp)
- Icon size: 28dp
- Container color: MaterialTheme.colorScheme.primary

This applies to ALL of:
- Recipients "+" (add contact)
- Trigger "+" (add keyword)
- Message upload button

No exceptions. Any new action button added must match this spec.

---

## TEXT INPUTS

### Rule: ALL text inputs that accept freeform or multi-word content must use ModalBottomSheet.

Required sheet behavior:
- `skipPartiallyExpanded = true`
- `imePadding()` applied to sheet content column
- `padding(bottom = 16dp)` inside the sheet
- `FocusRequester` with `LaunchedEffect { delay(200); focusRequester.requestFocus() }`
- Dismiss on tap outside → save content (not discard)
- Cancel button discards, Save/Add button confirms

Exception: single-line number-only fields (phone number input in RecipientsInput) may remain inline as TextField.

---

## TABS

Tab bar (BrowserTabBar):
- Two tabs: "Alert System" and "Dashboard"
- Tab weights animated via `animateFloatAsState`: selected = 1.4f, unselected = 0.8f
- Selected tab background: `primary.copy(alpha = 0.15f)`
- Unselected tab background: `Color.Transparent`
- Text: Bold + 14sp when selected, Normal + 13sp when unselected
- Text color: `primary` when selected, `onSurfaceVariant.copy(0.7f)` when unselected
- NO flash - transitions are smooth via the weight animation
- Unread dot (red, 8dp circle) appears on Dashboard tab when new responses exist

---

## DASHBOARD LAYOUT

### When inactive (no responses, not listening)
Show "On Standby" card:
- Surface with RoundedCornerShape(16dp), surfaceVariant.copy(0.5f) background
- Pulsing green dot (20dp, animates 0.4f→0.9f alpha at 1200ms)
- "On Standby" text: 22sp, bold
- "{n} contact(s) ready" subtitle: 14sp, semibold
- "Monitoring for emergency alerts" caption: 12sp, 60% opacity

### When listening
Show at top of LazyColumn:
- Listening timer Surface card: "Listening for responses" + elapsed time (HH:MM:SS)
- Stop button: red TextButton, right-aligned

### When responses received
Show:
- Stat cards row: Safe (green), Updates (blue), Urgent (red, pulsing)
- Progress bar: responded / total
- Recipient list with per-contact status and timestamp

---

## COLLAPSIBLE SECTIONS

| Section | Default | Controlled by |
|---|---|---|
| Logs | Collapsed | User toggle (Collapse / Show(n)) |
| Alert History | Collapsed | User toggle |

Rules:
- Must NEVER disappear - always present in layout tree
- `AnimatedVisibility` with `fadeIn()/fadeOut()` only inside LazyColumn items
- No `expandVertically` inside LazyColumn (causes scroll jump)
- Clear button for Logs only visible when expanded AND logs non-empty
- Show button displays count: "Show (5)"

---

## SCENARIO DROPDOWN

- Tapping opens group picker Dialog
- Long-press enters multi-select delete mode
- Long-press on header → rename in-place
- Drag handle visible - reorder by drag
- Favorite scenarios show star icon, cannot be deleted
- New scenario auto-selected when added

---

## GROUPS SECTION

- Group selector: 48dp Surface, tap to open group picker Dialog
- Group picker matches Scenario dropdown behavior exactly (drag, long-press, favorite)
- Selected group content shows: Recipients label, RecipientsInput, Message label, MessageInput
- Long-press group in picker → rename

---

## ALERT SYSTEM LAYOUT ORDER

1. "Alert System" title
2. Scenario dropdown
3. Debug banner (non-production, debug enabled only)
4. Abuse warning banner (when force send points ≥ 25)
5. Notification permission warning (when access not granted)
6. Trigger section card (keywords + add button)
7. Groups section card (group selector + recipients + message)
8. Scenario locked banner + Reset button (when locked)
9. Cooldown indicator (60s cooldown between sends)
10. Swipe to Force Send (or cooldown progress indicator)

---

## UNDO POPUP

- Slides up from bottom center
- Surface: inverseSurface background, RoundedCornerShape(24dp)
- Row padding: start=4dp, end=4dp, top=2dp, bottom=2dp
- Contains: Undo TextButton (inversePrimary) + Close IconButton (28dp)
- Auto-dismisses after 5 seconds

---

## SETTINGS DIALOG

Sections in order:
1. "Settings" title
2. APPEARANCE header → Theme dropdown (52dp height)
3. DETECTION header → Wide Spread toggle + Reply Listen Hours field
4. USER MANUAL header → Expandable accordion cards + Replay Tutorial button
5. DEBUG header (non-production only) → Debug toggle + tools
6. Done button (right-aligned)

---

## STATUS POPUP

Shown during active send or countdown:
- Displayed at bottom of screen
- Shows: send state (MULTI_THREADED / SEQUENTIAL / LAZARUS), progress, elapsed time
- Keep Trying toggle visible during Lazarus
- Cancel button changes to "Stop" when in non-countdown state
- Completion stats shown on success

---

## SWIPE TO FORCE SEND

- Height: 80dp, RoundedCornerShape(40dp)
- Thumb: 68dp circle, slides on drag
- Trigger: drag ≥ 85% of track width
- Haptic feedback on confirm
- Spring animation returns thumb to start position
- Disabled appearance when scenario locked or in cooldown

---

## WHAT TO AVOID

- Hidden actions
- Inconsistent button sizes
- Text fields without clear visual boundaries
- AlertDialog for keyboard input
- UI elements that conditionally vanish (hiding is OK, removing is not)
- Scroll positions that jump unexpectedly
- Any animation that feels like a flash or glitch

---

## FINAL PRINCIPLE

Clarity beats style.
Consistency builds trust.

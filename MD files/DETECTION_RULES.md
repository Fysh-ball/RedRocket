# DETECTION_RULES.md

## Purpose
Defines exactly how alerts are evaluated, scored, and classified. This is the single source of truth for FalseAlarmDetector behavior.

---

## PRIORITY SYSTEM

RED > ORANGE > GREEN

When in doubt → treat as RED (fail-safe principle).

---

## THE 7-STEP PIPELINE

Every alert is evaluated in this exact order. First decisive step wins. Steps never skip.

---

### STEP 0 — AMBER BLOCK (child safety)

Phrases (case-insensitive, substring match):
- "amber alert"
- "child abduction"
- "alerte amber" (French)
- "enlèvement d'enfant" (French)

Result: NEVER trigger. Return false immediately.
These must NEVER fire the system under any circumstances.

---

### STEP 1 — HARD OVERRIDE (trusted source + extreme danger)

Only fires when BOTH conditions are true:
1. isTrustedSource = true (cell broadcast / system emergency package)
2. Content contains ANY extreme danger phrase

EXTREME_DANGER_PHRASES:
- "ballistic missile"
- "nuclear"
- "missile inbound"
- "impact imminent"
- "seek immediate shelter"
- "evacuate immediately"
- "this is not a drill"
- "missile menace" (French)
- "évacuez immédiatement" (French)

Result: ALWAYS trigger. Return true immediately.
No score needed — trusted source + confirmed extreme language is definitive.

---

### STEP 2 — OVERRIDE PHRASES (explicit "not a test" signal)

Phrases (case-insensitive):
- "this is not a test"
- "ce n'est pas un exercice" (French)
- "ceci n'est pas un test" (French)

Result: ALWAYS trigger. Bypasses any hard block below.
These phrases explicitly confirm reality regardless of other content.

---

### STEP 3 — HARD TEST BLOCK

Phrases (case-insensitive, substring match):
- "this is a test"
- "only a test"
- "this is only a test"
- "c'est un test" (French)
- "ceci est un test" (French)
- "drill"
- "exercice" (French)
- "simulation"

Result: NEVER trigger. Return false immediately.
Note: Only reached if Step 2 did NOT match. If both "not a test" AND "this is a test" appear, Step 2 wins.

---

### STEP 4 — SCORE ACCUMULATION

Points are added/subtracted based on content analysis:

#### +5 — Trusted Source
Applied when isTrustedSource = true (cell broadcast / known emergency app package).

#### +4 — Strong Action Phrases
Any of:
- "take shelter now"
- "take shelter immediately"
- "evacuate now"
- "evacuate immediately"
- "seek shelter immediately"
- "seek cover now"
- "get to safety now"
- "mettez-vous à l'abri" (French)
- "évacuez maintenant" (French)
- "cherchez un abri" (French)

#### +2 — Moderate Action Phrases
Any of:
- "take shelter"
- "seek shelter"
- "evacuate"
- "shelter in place"
- "seek cover"
- "move to higher ground"
- "leave the area"
- "réfugiez-vous" (French)
- "quittez la zone" (French)

#### +2 — Danger Words
Any of:
- "emergency"
- "warning"
- "threat"
- "severe"
- "danger"
- "hazard"
- "destructive"
- "life-threatening"
- "urgence" (French)
- "avertissement" (French)
- "menace" (French)

#### +3 — Context Boost
Applied when BOTH a strong/moderate action phrase AND a danger word are present in the same message.

#### +1 — ALL CAPS Signal
Applied when: message is ≥10 characters AND >70% of alphabetic characters are uppercase.
Real emergency broadcasts are often all-caps.

#### +1 — Repetition Signal
Applied when: any word ≥5 characters appears 3 or more times.
Broadcast systems often repeat critical words.

#### +2 — User Keyword Match
Applied when: content contains any user-defined trigger keyword from the active scenario.

#### −2 — Soft Test Penalty
Applied when content contains:
- "exercise"
- "drill" (if not already blocked in Step 3)
- "test" (soft penalty only if Step 3 didn't catch it)
These don't hard-block but reduce score.

---

### STEP 5 — SCORE THRESHOLD

If accumulated score ≥ 6 → trigger.
If accumulated score < 6 → continue to Step 6.

---

### STEP 6 — STRUCTURAL FAIL-SAFE

Applied when isTrustedSource = true AND the message contains structural urgency signals:
- All-caps with action language
- Contains "emergency" + a location reference
- Short message (< 80 chars) with at least one danger word

Result: trigger even if score was below threshold.
This catches legitimate alerts that use unusual phrasing.

---

### STEP 7 — DEFAULT

No trigger. Return false.

---

## CLASSIFICATION LABELS

| Label | Meaning |
|---|---|
| RED | Real threat — can trigger system |
| ORANGE | Child safety alert — log only, never trigger |
| GREEN | Test alert — log only, never trigger |

---

## MATCHING RULES

- Case insensitive throughout
- Partial/substring match (not whole-word only)
- Extra surrounding text is ignored
- Bilingual alerts supported (EN + FR phrases pre-loaded)
- Both languages evaluated simultaneously

---

## TRUST LEVELS

| Source | isTrustedSource |
|---|---|
| Cell broadcast (WEA/CMAS/ETWS) | true |
| Known OEM emergency packages | true |
| System emergency packages (com.google.android.gms etc.) | true |
| All other apps (wide spread mode) | false |

---

## FAILSAFE RULE

If the system cannot determine whether an alert is real:
→ TREAT AS REAL (RED)

Better to over-warn than to miss a real emergency.

---

## WHAT NEVER TRIGGERS

These are absolute blocks regardless of any other content:
- "amber alert" or "child abduction" → ORANGE block (Step 0)
- "this is a test", "only a test", "drill" when Step 2 is not present → GREEN block (Step 3)
- System audio routing notifications ("hearing another device") → filtered before FalseAlarmDetector

---

## FINAL PRINCIPLE

Better to over-warn than miss a real threat.

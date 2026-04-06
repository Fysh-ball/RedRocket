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

AMBER alerts pass through the pipeline like any other alert. They can trigger scenarios if keywords match or if the content scores above threshold. Users who do not want AMBER alerts to trigger their scenarios can add block phrases like "amber alert" or "child abduction" to their scenario's Block Phrases list.

---

### STEP 1: HARD OVERRIDE (trusted source + extreme danger)

Only fires when BOTH conditions are true:
1. isTrustedSource = true (cell broadcast / system emergency package)
2. Content contains ANY phrase from STRONG_ACTION_PHRASES or EXTREME_DANGER_PHRASES

**STRONG_ACTION_PHRASES** (direct evacuation/shelter commands - pre-normalized Latin, NFC non-Latin):
- English: "evacuate immediately", "take shelter now", "take cover now", "seek shelter immediately", …
- French: "evacuez immediatement", "mettez vous a l abri immediatement", …
- Spanish: "evacue inmediatamente", "busque refugio inmediatamente", …
- Portuguese: "evacue imediatamente", "busque abrigo imediatamente", …
- German: "sofort evakuieren", "suchen sie sofort schutz", …
- Italian: "evacuate immediatamente", "mettetevi al riparo subito", …
- Dutch: "evacueer onmiddellijk", "zoek onmiddellijk beschutting"
- Russian: "немедленно эвакуируйтесь", "укройтесь немедленно", …
- Japanese: "直ちに避難してください", "今すぐ避難してください", "ただちに避難"
- Korean: "즉시 대피하십시오", "즉시 대피하세요"
- Chinese Simplified: "立即撤离", "立即避难", "立即疏散"
- Chinese Traditional: "立即撤離", "立即避難", "立即疏散"
- Arabic: "الإخلاء الفوري", "ابتعد عن المنطقة فورا"
- Turkish (pre-normalized): "hemen tahliye edin", "hemen siginaga gidin", …
- Polish (pre-normalized): "natychmiastowa ewakuacja", "ewakuujcie sie natychmiast", …
- Ukrainian (NFC): "негайно евакуюйтеся", "негайно укрийтеся", …
- Swedish (pre-normalized): "evakuera omedelbart", "ta skydd omedelbart", …
- Norwegian/Danish: "evakuer umiddelbart", "ta dekning umiddelbart", …
- Indonesian: "segera evakuasi", "segera cari perlindungan", …
- Hindi (NFC Devanagari): "तुरंत निकासी करें", "तुरंत शरण लें", …
- Bengali (NFC): "অবিলম্বে সরে যান", "অবিলম্বে আশ্রয় নিন", …

**EXTREME_DANGER_PHRASES** (catastrophic events - same 20-language coverage):
- English: "tornado", "missile", "nuclear", "earthquake", "tsunami", "radioactive", "life threatening", …
- French: "tornade", "vents destructeurs", "tremblement de terre", "menace imminente", …
- Spanish: "misil", "terremoto", "huracan", "amenaza inminente", …
- Portuguese: "missil", "terremoto", "furacao", "ameaca iminente", …
- German: "rakete", "nuklear", "erdbeben", "drohende gefahr", …
- Italian: "nucleare", "terremoto", "esplosione", "minaccia imminente", …
- Dutch: "raket", "nucleair", "aardbeving", "dreigende gevaar", …
- Russian: "ракета", "ядерный", "землетрясение", "угроза жизни", …
- Japanese: "ミサイル", "地震", "津波", "生命の危険", "放射能", …
- Korean: "미사일", "지진", "쓰나미", "생명 위협", "방사능", …
- Chinese Simplified: "导弹", "地震", "海啸", "生命威胁", "放射性", …
- Chinese Traditional: "導彈", "地震", "海嘯", "生命威脅", "放射性", …
- Arabic: "صاروخ", "زلزال", "تسونامي", "تهديد وشيك", …
- Turkish (pre-normalized): "nukleer", "deprem", "tsunami", "hava saldirisi", …
- Polish (pre-normalized): "trąbą powietrzną" → "traba powietrzna", "trzesienie ziemi", "tsunami", …
- Ukrainian (NFC): "ракета", "землетрус", "цунамі", "ядерний", …
- Swedish (pre-normalized): "jordbavning" → "jordbavning", "tsunami", "karnkraft", …
- Norwegian/Danish: "jordskjelv", "tsunami", "rakettangrep", …
- Indonesian: "gempa bumi", "tsunami", "rudal", "nuklir", …
- Hindi (NFC Devanagari): "भूकंप", "सुनामी", "परमाणु", "मिसाइल", …
- Bengali (NFC): "ভূমিকম্প", "সুনামি", "পারমাণবিক", "ক্ষেপণাস্ত্র", …

Result: ALWAYS trigger. Return true immediately.

---

### STEP 2: OVERRIDE PHRASES (explicit "not a test" signal)

Phrases (case-insensitive, substring match - 20 languages):
- English: "this is not a test", "this is not a drill"
- French: "ceci n est pas un test", "ceci n est pas un exercice"
- Spanish: "esto no es una prueba", "esto no es un simulacro"
- Portuguese: "isso nao e um teste", "isso nao e um simulacro"
- German: "dies ist kein test", "dies ist keine ubung"
- Russian: "это не учения", "это не тренировка"
- Japanese: "これは訓練ではありません", "これはテストではありません"
- Korean: "이것은 훈련이 아닙니다", "이것은 테스트가 아닙니다"
- Chinese Simplified: "这不是演习", "这不是测试"
- Chinese Traditional: "這不是演習", "這不是測試"
- Arabic: "هذا ليس تدريبا"
- Turkish (pre-normalized): "bu bir test degil", "bu bir tatbikat degil"
- Polish (pre-normalized): "to nie jest test", "to nie cwiczenia"
- Ukrainian (NFC): "це не навчання", "це не тренування"
- Swedish (pre-normalized): "detta ar inte ett test", "detta ar inte en ovning"
- Norwegian/Danish: "dette er ikke en test", "dette er ikke en øvelse"
- Indonesian: "ini bukan tes", "ini bukan latihan"
- Hindi (NFC): "यह परीक्षण नहीं है", "यह अभ्यास नहीं है"
- Bengali (NFC): "এটি পরীক্ষা নয়", "এটি মহড়া নয়"

Result: ALWAYS trigger. Bypasses any hard block below.

---

### STEP 3: HARD TEST BLOCK

Phrases (case-insensitive, substring match - 20 languages):
- English: "this is a test", "this is only a test", "this is a drill", "test of the alert system", …
- French: "ceci est un test", "ceci est un exercice", "test du systeme d alerte"
- Spanish: "esto es una prueba", "esto es un simulacro", "prueba del sistema de alerta"
- Portuguese: "isso e um teste", "isso e um simulacro", "teste do sistema de alerta"
- German: "dies ist ein test", "dies ist eine ubung", "test des warnsystems"
- Italian: "questo e un test", "questa e una esercitazione"
- Dutch: "dit is een test", "dit is een oefening"
- Russian: "это учения", "это тест системы", "проверка системы оповещения"
- Japanese: "これは訓練です", "これはテストです", "試験放送", "訓練放送"
- Korean: "이것은 훈련입니다", "이것은 테스트입니다", "경보 시스템 시험"
- Chinese Simplified: "这是演习", "这是测试", "警报系统测试"
- Chinese Traditional: "這是演習", "這是測試", "警報系統測試"
- Arabic: "هذا تدريب", "اختبار نظام الانذار"
- Turkish (pre-normalized): "bu bir test", "bu bir tatbikat"
- Polish (pre-normalized): "to jest test", "to cwiczenia"
- Ukrainian (NFC): "це навчання", "це тренування"
- Swedish (pre-normalized): "detta ar ett test", "detta ar en ovning"
- Norwegian/Danish: "dette er en test", "dette er en øvelse"
- Indonesian: "ini adalah tes", "ini adalah latihan"
- Hindi (NFC): "यह परीक्षण है", "यह अभ्यास है"
- Bengali (NFC): "এটি পরীক্ষা", "এটি মহড়া"

Result: NEVER trigger. Return false immediately.
Note: Only reached if Step 2 did NOT match. If both "not a test" AND "this is a test" appear, Step 2 wins.

---

### STEP 4: RED TRIGGER PHRASES (life-threatening, bypass sensitivity)

Always trigger regardless of sensitivity setting. Still respect Amber Block (Step 0) and Hard Block (Step 3).

Phrases (20 languages - same coverage as EXTREME_DANGER_PHRASES but as compound phrases):
- English: "tornado warning", "missile inbound", "imminent threat", "nuclear attack", "life threatening", "flash flood warning", …
- French: "avertissement de tornade", "menace imminente", "danger imminent", …
- Spanish: "aviso de tornado", "amenaza inminente", "peligro de vida", …
- Portuguese: "aviso de tornado", "ameaca iminente", "perigo de vida", …
- German: "tornadowarnung", "raketenwarnung", "lebensgefahr", "drohende gefahr", …
- Russian: "ракетная угроза", "неминуемая угроза", "угроза жизни", …
- Japanese: "竜巻警報", "ミサイル警報", "ミサイル飛来", "生命の危険", "大津波警報", "緊急地震速報"
- Korean: "토네이도 경보", "미사일 경보", "긴박한 위협", "생명 위협", …
- Chinese Simplified: "龙卷风警报", "导弹警报", "迫在眉睫的威胁", "生命威胁", "海啸警报", …
- Chinese Traditional: "龍卷風警報", "導彈警報", "迫在眉睫的威脅", "生命威脅", "海嘯警報", …
- Arabic: "تحذير إعصار", "تحذير صاروخي", "تهديد وشيك", "خطر على الحياة", …
- Turkish (pre-normalized): "tornado uyarisi", "fuzе saldirisi", "yasam tehlikesi", …
- Polish (pre-normalized): "ostrzezenie przed tornadem", "atak rakietowy", "zagrozenie zycia", …
- Ukrainian (NFC): "ракетна загроза", "загроза життю", "надзвичайна ситуація", …
- Swedish (pre-normalized): "tornadovarning", "missilattack", "livsfara", …
- Norwegian/Danish: "tornadovarsel", "missilangrep", "livsfare", …
- Indonesian: "peringatan tornado", "serangan rudal", "bahaya jiwa", …
- Hindi (NFC Devanagari): "बवंडर चेतावनी", "मिसाइल हमला", "जानलेवा खतरा", …
- Bengali (NFC): "টর্নেডো সতর্কতা", "ক্ষেপণাস্ত্র আক্রমণ", "জীবনের হুমকি", …

---

### STEP 5: SCORE ACCUMULATION

Points are added/subtracted based on content analysis:

#### +5 - Trusted Source
Applied when isTrustedSource = true (cell broadcast / known emergency app package).

#### +4 - Strong Action Phrases
Same list as Step 1 STRONG_ACTION_PHRASES, all 20 languages (mutually exclusive with +2 below).

#### +2 - Moderate Action Phrases
20 languages: "seek shelter", "evacuate", "remain indoors", "stay away", …
Russian: "укройтесь", "эвакуируйтесь", "оставайтесь в помещении", …
Japanese: "避難してください", "高台に避難", …
Korean: "대피하세요", "실내에 머무르세요", …
Chinese: "撤离"/"撤離", "疏散", "留在室内"/"留在室內", …
Arabic: "ابتعد عن المنطقة", "ابق في الداخل", …
Turkish/Polish/Ukrainian/Swedish/Norwegian/Danish/Indonesian/Hindi/Bengali - all covered.

#### +2 - Danger Words
20 languages: "emergency"/"urgence"/"emergencia"/"emergenza"/"noodgeval"/
"чрезвычайная ситуация"/"緊急"/"비상"/"紧急"/"緊急"/"طوارئ"/
"acil"(TR)/"nagłe"(PL)/"надзвичайна"(UK)/"nödläge"(SV)/"nødsituasjon"(NO)/
"darurat"(ID)/"आपातकाल"(HI)/"জরুরি অবস্থা"(BN)/…

#### +3 - Context Boost
Applied when BOTH a strong/moderate action phrase AND a danger word are present.

#### +1 - ALL CAPS Signal
Applied when: ≥10 alphabetic characters AND >70% are uppercase.

#### +1 - Repetition Signal
Applied when: any word ≥5 characters appears 3 or more times.

#### +2 - User Keyword Match
Applied when content contains any user-defined trigger keyword.

#### −2 - Soft Test Penalty
20 languages: "exercise"/"exercice"/"ejercicio"/"ubung"/"esercitazione"/"oefening"/
"учения"/"тренировка"/"訓練"/"演習"/"훈련"/"演习"/"训练"/"تدريب"/
"tatbikat"(TR)/"cwiczenia"(PL)/"навчання"(UK)/"ovning"(SV)/"øvelse"(NO)/
"latihan"(ID)/"अभ्यास"(HI)/"মহড়া"(BN)/…

---

### STEP 6: SCORE THRESHOLD

| Sensitivity | Threshold |
|---|---|
| HIGH   | 4  |
| MEDIUM | 6 (default) |
| LOW    | 9  |

If score ≥ threshold → trigger.
If score < threshold → continue to Step 7.

---

### STEP 7: STRUCTURAL FAIL-SAFE

Applied when isTrustedSource = true AND isUrgentStructure() returns true.

isUrgentStructure() fires when ANY of:
1. ALL CAPS - >70% uppercase letters, ≥10 chars (Latin scripts)
2. ≥2 exclamation marks - language-agnostic emphasis
3. Case-free script - ≥10 letters present and NONE have case (upper or lower).
   Fires for CJK, Arabic, Hebrew, and other scripts without capitalisation.
   Ensures trusted-source alerts in these scripts always pass the fail-safe even
   when no phrase matched.

Result: trigger even if score was below threshold.

---

### STEP 8: DEFAULT

No trigger. Return false.

---

## NORMALIZATION

All alert text is normalized before matching:
1. Lowercase
2. NFD Unicode decomposition (splits "é" → "e" + combining acute)
3. Strip U+0300–U+036F combining diacritical marks (removes accent from "e")
4. NFC recompose (prevents Korean Hangul from staying as decomposed jamo)
5. Strip all remaining non-letter (`\p{L}`), non-combining-mark (`\p{M}`), non-number (`\p{N}`) characters → space
6. Collapse multiple spaces, trim

**Result**: Latin-script accents are removed transparently. Non-Latin scripts (CJK, Cyrillic,
Arabic, Korean, Hebrew, Devanagari, Bengali, etc.) pass through unchanged. "é" matches "e", "ü" matches "u".

**`\p{M}` preservation**: Devanagari and Bengali vowel marks (matras) are Unicode category
`\p{M}` (spacing combining marks), not `\p{L}`. Preserving `\p{M}` prevents Hindi and Bengali
words from being destroyed by the cleanup step.

**User keywords are also normalized** via `normalize()` before matching - an accented keyword
("évacuation") correctly matches normalized content ("evacuation").

Phrase lists are stored pre-normalized:
- Latin phrases: lowercase, accents stripped ("évacuez" → "evacuez", "Übung" → "ubung")
- Turkish: pre-normalized (ğ→g, ş→s, ü→u, ö→o, ç→c, ı→i for uppercase-safe matching)
- Polish: pre-normalized (ą→a, ę→e, ó→o, ć→c, ś→s, ź/ż→z, ń→n; ł preserved)
- Swedish: pre-normalized (ä→a, ö→o, å→a)
- Non-Latin phrases (Russian/Ukrainian/Japanese/Korean/Chinese/Arabic/Hindi/Bengali): NFC Unicode form (no pre-processing needed)
- Norwegian/Danish: ø and æ have no NFD decomposition, stored as-is

---

## USER BLOCK PHRASES

User-defined block phrases (any language) are normalized with the same pipeline before
matching. A Spanish/Arabic/Korean block phrase works identically to an English one.

---

## CLASSIFICATION LABELS

| Label | Meaning |
|---|---|
| RED | Real threat - can trigger system |
| ORANGE | Child safety alert - log only, never trigger |
| GREEN | Test alert - log only, never trigger |

---

## MATCHING RULES

- Case insensitive throughout (handled by normalize)
- Partial/substring match (not whole-word only)
- Extra surrounding text is ignored
- 20 languages evaluated simultaneously

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
- "amber alert" / "child abduction" (any language equivalent) → ORANGE block (Step 0)
- Explicit test phrase when Step 2 is not present → GREEN block (Step 3)
- System audio routing notifications ("hearing another device") → filtered before FalseAlarmDetector

---

## FINAL PRINCIPLE

Better to over-warn than miss a real threat.

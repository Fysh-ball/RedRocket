# How Red Rocket was built

## The short version

Red Rocket was written with AI assistance. Specifically: Claude, driven by me,
working inside a written engineering specification that I wrote and enforce.

I am stating that up front because this is an app people may end up relying on
during an emergency, and because several open source app catalogues now ask
about it directly. You should not have to guess, and you should not have to
take my word for it either. Everything below is checkable against this repo.

What that phrase does not mean here is a prompt, a generated app, and a
release. This document explains the difference, because the difference is the
whole point.

## The method

The app is governed by documents, not by conversations. Before the AI touches
anything, the rules already exist in the repo:

| Document | What it fixes in place |
|---|---|
| `MD files/PROJECT_SPEC.md` | What the app is and is not allowed to become |
| `MD files/ARCHITECTURE.md` | The systems, their boundaries, and who owns what |
| `MD files/DETECTION_RULES.md` | The alert classification logic, step by step |
| `MD files/AGENTS.md` | Binding rules for any AI agent modifying this code |
| `MD files/TESTING.md` | The checklist that must pass on a real device |
| `MD files/UX_RULES.md` | Interface invariants, down to button sizes |
| `MD files/KNOWN_ISSUES.md` | Every bug found, its status, and its fix |

`AGENTS.md` is the important one. It is not advice. It is a list of things that
must remain true, written specifically to stop an eager assistant from
"improving" a safety path. A few of its actual rules:

- The false-alarm detector's eight steps run in a fixed order, 0 through 7. The
  order is deterministic and must never change. The AMBER block runs first,
  before anything else, always.
- All trigger decisions happen in exactly one place. No detection logic may
  exist outside `FalseAlarmDetector`, for any reason.
- Every alert is logged before any filtering or triggering decision, never
  after. Logging is never awaited on the send path and is never bypassed.
- Everything fails soft. Invalid input is ignored silently. No crashes.
- Nothing gets deleted or replaced because it looked untidy. Smallest change
  that solves the problem, one batch at a time, regressions checked after each.
- No unrequested features, no speculative error handling, no compatibility
  shims nobody asked for.

Work happens in small batches. Each fix is documented in `KNOWN_ISSUES.md` as
it is made, and only moves to the fixed section once it has held up in use.
That file currently carries 92 tracked entries, including the ones still open
and the edge cases that have no clean answer yet.

## What I do

I decide what gets built and what does not. I write and maintain the specs
above. I review the diffs. I run the `TESTING.md` checklist on a real phone,
locked and unlocked, on both the triggered and non-triggered paths, because an
emulator cannot tell you what happens when the screen is off and the radio is
asleep.

I also find the bugs that matter. The commit log is the honest record of this:
fixes like "wakelock failure must degrade, never disable detection" and "dedup
gates the send, not the log" are not the kind of thing that surfaces from
generating code. They surface from running the app, watching it do the wrong
thing, and working out which invariant was violated.

## Check it yourself

Nothing here asks for trust. Clone the repo and count:

```sh
find app/src -name '*.kt' | wc -l          # 83 Kotlin files
find app/src -name '*.kt' -exec cat {} + | wc -l   # 16,885 lines
ls 'MD files' | wc -l                      # 11 specification documents
git log --format=%ad --date=short | sort -u | wc -l  # 17 working days
```

| Measure | Value |
|---|---|
| Kotlin source | 83 files, 16,885 lines |
| Specification and process docs | 11 files, ~210 KB |
| Development span | 30 Mar 2026 to 11 Aug 2026 |
| Commits, distinct working days | 50 across 17 days |
| Tracked issues in `KNOWN_ISSUES.md` | 92 |
| Detection pipeline | 8 ordered steps, single decision point |

The specs are longer than a fifth of the source they govern. That ratio is the
clearest evidence I can offer about how this was actually made.

## Where it is weak

An honest disclosure has to include the parts that are not flattering.

- **Automated test coverage is thin.** Three unit test files, covering the
  content matchers and the alert enricher. Verification leans on the
  `TESTING.md` device checklist and on real use, not on a suite.
- **There is no CI.** No pipeline runs those tests or the build on push. If the
  checklist is skipped, nothing catches it.
- **One person reviews everything.** There is no second pair of eyes on the
  diffs, which is exactly the condition under which a confident wrong answer
  survives.

These are the next things to fix, and I would rather you read them here than
discover them yourself.

## Why this document exists

Two reasons.

The IzzyOnDroid inclusion policy currently states that apps created fully or in
part by generative AI tools may be rejected. Red Rocket falls under that
description. I would rather disclose it plainly and be turned down than get
listed by being vague about it.

The other reason is simpler. This app sends messages to your family on the
strength of an alert it classified by itself. Anyone deciding whether to
install that is entitled to know how it was made, what constrains it, and where
it is thin. That is not a disclaimer. It is the same reason the detection rules
are written down in public in the first place.

If you find something wrong with it, open an issue. I use this app too.

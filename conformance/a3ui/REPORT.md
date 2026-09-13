# a3ui conformance report

Suite `:a3ui:conformance`. Fixtures reuse showcase-v0.3 hotel/calendar/train plus flight FACT.

| Test | Invariante | Evidence | PASS |
|------|------------|----------|------|
| AC-001 | CONTRADICTED → CTA enabled → fail | `fixtures/violations/ac-001-contradicted-cta-enabled.json` | **PASS** |
| AC-002 | PENDING → CTA enabled without confirmation → fail | `ac-002-pending-cta-enabled.json` | **PASS** |
| AC-003 | UNKNOWN → CTA enabled without confirmation → fail | `ac-003-unknown-cta-enabled.json` | **PASS** |
| AC-004 | STALE → CTA without warning → fail | `ac-004-stale-no-warning.json` | **PASS** |
| AC-005 | FACT → CTA disabled without reason → fail | `ac-005-fact-disabled-silent.json` | **PASS** |
| AC-006 | Mark without stateDescription → fail | `ac-006-missing-state-description.json` | **PASS** |
| AC-007 | Reduced motion hides marks → fail | `ac-007-reduced-motion-hides-marks.json` | **PASS** |
| AC-008 | Blur OFF unreadable marks → fail | `ac-008-blur-off-unreadable.json` | **PASS** |
| AC-009 | Android CONTRADICTED CTA disabled + reason | `screenshots/android/CONTRADICTED.png` + `.tree.json` | **PASS** |
| AC-010 | Mac CONTRADICTED same | `screenshots/mac/CONTRADICTED.png` | **PASS** |
| AC-011 | Android STALE warning `stale 2h` | `screenshots/android/STALE.png` | **PASS** |
| AC-012 | Mac STALE same | `screenshots/mac/STALE.png` | **PASS** |
| AC-013 | Android PENDING reserved slot, no spinner | `screenshots/android/PENDING.png` | **PASS** |
| AC-014 | Mac PENDING same | `screenshots/mac/PENDING.png` | **PASS** |
| AC-015 | Android FACT CTA enabled, baseline mark | `screenshots/android/FACT.png` | **PASS** |
| AC-016 | Mac FACT same | `screenshots/mac/FACT.png` | **PASS** |
| AC-017 | Android reduced motion keeps marks, motion 0 | `screenshots/android/reduced-motion.png` | **PASS** |
| AC-018 | Mac reduced motion same | `screenshots/mac/reduced-motion.png` | **PASS** |
| AC-019 | Android blur OFF marks readable | `screenshots/android/blur-off.png` | **PASS** |
| AC-020 | Mac blur OFF same | `screenshots/mac/blur-off.png` | **PASS** |
| AC-021 | Android TalkBack type+reason+forbidden action | parse tree `stateDescription` | **PASS** |
| AC-022 | Mac VoiceOver type+reason+forbidden action | parse tree `stateDescription` | **PASS** |
| AC-023 | Freeze core/broker/agent/renderers/launcher/overlay/adapters | git diff empty | **PASS** |

Gate: **verde** when `./gradlew :a3ui:conformance:test` is BUILD SUCCESSFUL.

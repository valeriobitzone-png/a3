# REVIEW_A3UI_CONFORMANCE

AUDIT-FIRST. Protocol: FASE A3UI-CONFORMANCE — suite visiva+comportamentale mark→CTA. Nuovo: `:a3ui:conformance` (+ `:a3ui:conformance:android`, `:a3ui:conformance:mac`). Frozen: `core/`, `broker/`, `agent/`, `renderers/`, `launcher/`, `overlay/`, `adapters/`. Niente push. Tag `a3ui-conformance-v0.1` solo a gate verde.

**Contratto:** un renderer che abilita una CTA su uno stato vietato da SPEC_A3UI §4 fallisce. Fixture: showcase-v0.3 hotel STALE / calendar CONTRADICTED / train PENDING + flight FACT. Host di conformance (showcase non ha FACT né `stateDescription`; showcase non è modificato).

---

## Freeze (AC-023)

```
git diff --stat -- core/ broker/ agent/ renderers/ launcher/ overlay/ adapters/
```

vuoto. Porcelain nuovo: `conformance/a3ui/`, `settings.gradle.kts` (`:a3ui:conformance*`), `spec/` allowlist REVIEW, questo REVIEW.

---

## Esecuzione

```
./gradlew :a3ui:conformance:test --no-daemon -Pkotlin.compiler.execution.strategy=in-process
```

Screenshot + parse tree: `conformance/a3ui/screenshots/{android,mac}/`. Report: `conformance/a3ui/REPORT.md`.

---

## Fixture di categoria (AC-001..008)

| Code | File | Reject esplicito |
|------|------|------------------|
| AC-001 | `ac-001-contradicted-cta-enabled.json` | CONTRADICTED → CTA enabled |
| AC-002 | `ac-002-pending-cta-enabled.json` | PENDING → CTA enabled without confirmation |
| AC-003 | `ac-003-unknown-cta-enabled.json` | UNKNOWN → CTA enabled without confirmation |
| AC-004 | `ac-004-stale-no-warning.json` | STALE → CTA without warning |
| AC-005 | `ac-005-fact-disabled-silent.json` | FACT → CTA disabled without reason |
| AC-006 | `ac-006-missing-state-description.json` | Mark without stateDescription |
| AC-007 | `ac-007-reduced-motion-hides-marks.json` | reduced motion ON → marks disappeared |
| AC-008 | `ac-008-blur-off-unreadable.json` | blur OFF → marks unreadable |

Nessun silenzio. Un mapping vietato è un fail, non un warning.

---

## Tabella audit — AC-001..023

| Test | Invariante | Percorso | PASS |
|------|------------|----------|------|
| AC-001 | CONTRADICTED → CTA enabled → fail | `CategoryViolationTest` | **PASS** |
| AC-002 | PENDING → CTA enabled without confirmation → fail | `CategoryViolationTest` | **PASS** |
| AC-003 | UNKNOWN → CTA enabled without confirmation → fail | `CategoryViolationTest` | **PASS** |
| AC-004 | STALE → CTA without warning → fail | `CategoryViolationTest` | **PASS** |
| AC-005 | FACT → CTA disabled without reason → fail | `CategoryViolationTest` | **PASS** |
| AC-006 | Mark without stateDescription → fail | `CategoryViolationTest` | **PASS** |
| AC-007 | Reduced motion hides marks → fail | `CategoryViolationTest` | **PASS** |
| AC-008 | Blur OFF unreadable marks → fail | `CategoryViolationTest` | **PASS** |
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

Gate: **verde**. Tag locale `a3ui-conformance-v0.1`. Niente push.

---

## Vietato (rispettato)

- Nessuna CTA enabled su CONTRADICTED.
- Marks restano con reduced motion; motion = 0.
- Marks leggibili con blur OFF.
- `stateDescription` presente (tipo + reason + azione vietata/permessa).
- Moduli congelati non toccati.
- Nessun push.

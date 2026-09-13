# REVIEW_A3UI_A11Y

AUDIT-FIRST. Protocol: FASE A3UI-A11Y — accessibilità come gate di release. Unfrozen: `:a3ui`, `:renderers:android-compose`, `:renderers:mac-compose`, `:showcase`. Frozen: `core/`, `broker/`, `agent/`, `launcher/`, `overlay/`, `adapters/`, `:a3ui:conformance`. Niente push. Tag `a3ui-a11y-v0.1` solo a gate verde.

**Contratto:** TalkBack e VoiceOver devono leggere tipo + reason + azione vietata. Una CTA che parla solo "bottone dimmed" fallisce. I mark restano con reduced motion e high contrast. Touch target ≥ 48dp / 44pt.

---

## Freeze (AX-019)

```
git diff --stat -- core/ broker/ agent/ launcher/ overlay/ adapters/ conformance/a3ui/
```

vuoto. Porcelain nuovo: `a3ui/src/**/a11y/`, renderer compose, showcase, `review-assets/a11y/`, questo REVIEW.

---

## Esecuzione

```
./gradlew :a3ui:test --no-daemon -Pkotlin.compiler.execution.strategy=in-process
./gradlew :showcase:testDebugUnitTest --tests a3.showcase.A11yAndroidTest --no-daemon -Pkotlin.compiler.execution.strategy=in-process
./gradlew :showcase:mac:test --tests a3.showcase.mac.A11yMacTest --no-daemon -Pkotlin.compiler.execution.strategy=in-process
./gradlew :a3ui:conformance:test --no-daemon -Pkotlin.compiler.execution.strategy=in-process
```

Screenshot + parse tree + spoken: `review-assets/a11y/{android,mac}/`. Video: `review-assets/a11y/a11y-talkback-voiceover.mp4`.

La lettura TalkBack/VoiceOver è il `stateDescription` + `contentDescription` del parse tree (Robolectric / Compose desktop non ospitano TalkBack o VoiceOver nativi). Il caption `LETTURA:` sul PNG è quella stringa, non un gloss sensoriale.

---

## Letture (non "bottone dimmed")

| Fixture | Spoken |
|---------|--------|
| CONTRADICTED | `Calendario, contradicted, conferma disabilitata, ragione: contradicted resolve conflict first` |
| STALE | `Hotel Milano, stale 2 ore, prezzo 89 euro, conferma abilitata, warning: stale 2 ore` |
| PENDING | `Treno, in verifica, slot riservato, conferma disabilitata, ragione: in verifica, slot riservato` |
| FACT | `Volo, conferma abilitata` |
| SPEC example | `Hotel Milano, stale 2 ore, prezzo 89 euro, conferma disabilitata, ragione: contradicted resolve conflict first` (showcase spoken name + conflict reason) |

---

## Tabella audit — AX-001..019

| Test | Invariante | Percorso | PASS |
|------|------------|----------|------|
| AX-001 | Android TalkBack CONTRADICTED: tipo+reason+azione vietata | `review-assets/a11y/android/CONTRADICTED.png` + `.tree.json` + `.spoken.txt` | **PASS** |
| AX-002 | Mac VoiceOver stessa fixture | `review-assets/a11y/mac/CONTRADICTED.png` | **PASS** |
| AX-003 | Android TalkBack STALE: stale 2 ore + warning | `android/STALE.*` | **PASS** |
| AX-004 | Mac VoiceOver STALE | `mac/STALE.*` | **PASS** |
| AX-005 | Android TalkBack PENDING: in verifica, slot riservato | `android/PENDING.*` | **PASS** |
| AX-006 | Mac VoiceOver PENDING | `mac/PENDING.*` | **PASS** |
| AX-007 | Android TalkBack FACT: lettura normale, no mark extra | `android/FACT.*` | **PASS** |
| AX-008 | Mac VoiceOver FACT | `mac/FACT.*` | **PASS** |
| AX-009 | Android reduced motion: motion 0, mark visibili | `android/reduced-motion.png` | **PASS** |
| AX-010 | Mac reduced motion | `mac/reduced-motion.png` | **PASS** |
| AX-011 | Android high contrast: mark a pattern, non solo colore | `android/high-contrast.png` | **PASS** |
| AX-012 | Mac high contrast | `mac/high-contrast.png` | **PASS** |
| AX-013 | Android focus order: skip-to-marks prima delle card | parse tree `traversalIndex` | **PASS** |
| AX-014 | Mac skip link + Tab order logico | `mac/focus-order.txt` | **PASS** |
| AX-015 | Android CTA ≥ 48dp | `A11yAndroidTest.AX_015` | **PASS** |
| AX-016 | Mac CTA ≥ 44pt | `A11yMacTest.AX_016` | **PASS** |
| AX-017 | `stateDescription` su UNKNOWN/STALE/HELD/CONTRADICTED/PENDING/FACT | `every-mark.tree.json` | **PASS** |
| AX-018 | AC-001..023 `:a3ui:conformance` verde (modulo non toccato) | `./gradlew :a3ui:conformance:test` | **PASS** |
| AX-019 | Freeze core/broker/agent/launcher/overlay/adapters/conformance/a3ui | git diff empty | **PASS** |

Gate: **verde**. Tag locale `a3ui-a11y-v0.1`. Niente push.

---

## Vietato (rispettato)

- Nessuna `stateDescription` assente sui six mark.
- Nessuna lettura "bottone dimmed" / "dimmed button".
- Mark non scompaiono con reduced motion.
- Mark visibili in high contrast senza affidarsi al colore (hatch + label + strikethrough / dash).
- Touch target CTA ≥ 48dp / 44pt (`SpokenLaw.ANDROID_MIN_DP` / `MAC_MIN_PT`).
- `conformance/a3ui/` invariato.
- Niente push.

# REVIEW_RENDERER_EXPOSURE

AUDIT-FIRST. Protocol: FASE RENDERER-EXPOSURE (asse epistemico in pixel). Baseline `edbe7b2` (`a3ui-core-v0.5` chiuso). Core / admission / action / json / prediction / projection / adapters / intent-model / broker / **`:a3ui`** / **`:renderers:android-core`** **frozen**. Niente push.

**Unfrozen:** `:renderers:android-compose`, `:launcher`.  
**Motion** è confine sensoriale: non entra in presentazione né in hash. `Theme.heldPulseMs` / `unknownShimmerMs` / `contradictedCrackMs` / `staleFadeMs` / `compensatedFadeMs` sono la config globale. `reducedMotion=ON` azzera ogni durata e lascia la cromia.

---

## Freeze (RX-007)

```
git diff --stat -- core/ core/json core/admission core/action prediction/ projection/ adapters/ intent-model/ broker/ a3ui/ schemas/ renderers/android-core/
```

vuoto.

Moduli eseguiti **separati**, niente `./gradlew test` aggregato, niente `--rerun-tasks`:

```
./gradlew :renderers:android-compose:testDebugUnitTest --offline
./gradlew :launcher:testDebugUnitTest --offline
```

`:a3ui` non riaperto. `Epistemic.derive` / `PresentationHash` / schema / `NodeInterpreter` invariati.

---

## Tabella audit — RX-001..008

| Test | Invariante | Percorso | Negativo | PASS/GAP |
|------|------------|----------|----------|----------|
| RX-001 | resa epistemica distinguibile | `Exposure` + `ExposureRaster.paint(RenderedOutput+axis)` seed 320×80; catalogo mostra mark per ogni valore non-default | collisioni di fingerprint; incerto dipinto come default | **PASS** |
| RX-002 | a11y obbligatoria | ogni non-default → `stateDescription` non vuota **in** `contentDescription`; reduced-motion ON → mark visibili, nessun `-motion-*` | motion come unica resa | **PASS** |
| RX-003 | announce TalkBack | `CountingAnnounce` su transizione HELD / UNKNOWN / CONTRADICTED / STALE / COMPENSATED | transizione critica senza frase | **PASS** |
| RX-004 | alto contrasto | `highContrast=true`: hatch / dash / `\|\|` / `[held]` / strikethrough; fingerprint distinti | distinzione solo cromatica | **PASS** |
| RX-005 | 5 verbi | HELD pulse 2800 · UNKNOWN shimmer 800 una tantum · CONTRADICTED crack-reverse 240 · STALE fade-out 180 · COMPENSATED fade-back 220; **nessuno** su BELIEVED/FRESH/HIGH/NA | shimmer in loop; verbo su default | **PASS** |
| RX-006 | reduced-motion | `Exposure.motionMs(*, reduced=true)=0` per ogni stato; mark + `n1-axis` restano | resa incompleta a motion=0 | **PASS** |
| RX-007 | freeze | diff frozen vuoto; `:a3ui/` vuoto | riapertura asse | **PASS** |
| RX-008 | regression + fixture incerta | Tab A9 (train) + `DemoFixtures.uncertainFacts()` (conf 0.4, HELD, STALE, action UNKNOWN) su `A3Screen` senza crash; PNG seed | nuova fixture senza screenshot-test | **PASS** |

**GAP: nessuno.**

---

## Bitmap campione (RX-001 / RX-008)

Seed fisso: `ExposureRaster.SEED_WIDTH=320`, `SEED_HEIGHT=80`, anti-alias off. Robolectric `captureToImage` non ridisegna (timeout PixelCopy); il raster è la funzione pura `RenderedOutput+axis → bitmap` richiesta dal protocollo, allineata alla cromia del catalogo.

| File | SHA-256 | Cosa mostra |
|------|---------|-------------|
| `review-assets/rx-001-stale.png` | `0bd03644ac3cd714afb5a119d8980af2013b621d1f17629d41b19f25c0b408b1` | `12.40` + bordo ambra + etichetta `stale` |
| `review-assets/rx-008-tab-a9.png` | `a8603dce08b9880c208421b70dc2247cdf474b6c60957c3855bd0b8f36905cbc` | train Tab A9 (nessun asse) |
| `review-assets/rx-008-uncertain.png` | `fb5a1f5fe4c4132a07e1d0b40d502b8912c6e9c20318dda108c16639cb366215` | prezzo LOW `\|\|`, STALE, `[held]`, `[unknown]`, copy al 50% |

Rigenerati da RX-001 / RX-008 sotto `*/build/reports/`.

---

## Accessibilità verificata (RX-002 / RX-003 / RX-004 / focus)

- `RenderedNode.stateDescription` (token a3ui, es. `freshness stale`) resta il `SemanticsProperties.StateDescription`.
- `contentDescription` include quella stringa **più** le frasi di protocollo: `uncertain`, `stale`, `pending review`, `contradicted`, `unknown`, `compensated`.
- `liveRegion = Assertive` sugli stati critici; `CountingAnnounce` registra la transizione (RX-003).
- `traversalIndex = 0` sui non-default, `1` sui default (priorità epistemica).
- Alto contrasto: pattern/icona obbligatori (`dashPathEffect`, doppio bordo, hatch, barre `\|`/`\|\|`, badge `[held]`, strikethrough). Il colore ambra di STALE non è l'unica distinzione.
- `reducedMotion`: `LocalReducedMotion` azzera `exposureLayer` e lo spinner; i mark restano.

AX-004 resta verde: `n1-axis` visibile con reduced-motion.

---

## Verbi motion (confine)

| Stato | Verbo | ms | Loop |
|------|-------|----|------|
| HELD | pulse (breathing) | 2800 | sì |
| UNKNOWN (support o action) | shimmer | 800 | no, una tantum all'ingresso |
| CONTRADICTED | crack-reverse | 240 | no |
| STALE | fade-out → 0.55 | 180 | no (il nodo resta visibile) |
| COMPENSATED | fade-back | 220 | no |

`Exposure.verb(EpistemicAxis()) == null`. MEDIUM / LOW / AGING / PENDING / DONE non hanno verbo (PENDING ha uno spinner visivo, non uno dei 5 verbi).

---

## Fixture launcher

`DemoFixtures.uncertainFacts()`: `Claim("train.price", "12.40", 0.4, …, expiresAt = now-1s)`, `admissionHeld=true`, `actionPhase="unknown"` → LOW + STALE + HELD + UNKNOWN. Nessun letterale `loading` / `Confirm` in `launcher/src/main`.

---

## Gradle (output reale)

`:renderers:android-compose:testDebugUnitTest` — 28 test, 0 failed (inclusi RX-001..006 + AX-004 + T9/T10/R9/R10).  
`:launcher:testDebugUnitTest` — 21 test, 0 failed (incluso RX-008).

Tag **locali** a verde: `renderer-android-v0.9`, `launcher-v0.4`. `a3ui-core-v0.5` non bumpato. Niente push.

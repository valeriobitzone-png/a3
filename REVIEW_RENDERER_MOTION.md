# REVIEW_RENDERER_MOTION

AUDIT-FIRST. Protocol: FASE RENDERER-MOTION (L3 + ripple/rubber). Spec `a3ui-graphics-v0.1` `tokens/motion.json`. Scongelati: `:renderers:android-compose`, `:renderers:mac-compose`. Frozen: `core/`, `core/json/`, `core/admission/`, `core/action/`, `prediction/`, `projection/`, `:a3ui`, `:renderers:android-core`, `adapters/`, `intent-model/`, `:broker`, `:launcher`. Niente push. Niente animazione lineare sul sistema nuovo. Niente reset velocità su interruzione. Ripple dal contatto, non dal centro. Rubber-band con spring, non snap.

**Contratto:** snapshot `motion.json` in `src/main/resources/a3ui-graphics/` accanto a colors/elevation/surfaces. ODE clock-free in `MotionPhysics`; Compose/Mac consumano gli stessi numeri. Verbi epistemici (HELD pulse 2800, UNKNOWN shimmer 800) restano in `ExposureMotion` / `MacMotion` e non vengono deformati.

---

## Freeze (MO-009)

```
git diff --stat -- core/ a3ui/ renderers/android-core/ launcher/ broker/ prediction/ projection/ adapters/ intent-model/
```

vuoto. Solo android-compose + mac-compose (+ REVIEW + review-assets).

---

## Token usati (`motion.json` v0.1)

| Chiave | Valore |
|--------|--------|
| `springs.compact` | mass 1, stiffness 400, damping 28, hint 180ms |
| `springs.comfortable` | mass 1, stiffness 280, damping 24, hint 240ms |
| `springs.spacious` | mass 1, stiffness 180, damping 22, hint 320ms |
| `easings.standard` | cubic-bezier(0.4, 0.0, 0.2, 1) |
| `easings.emphasized` | cubic-bezier(0.2, 0.0, 0.0, 1) — ingressi morbidissimi |
| `reducedMotion.zeroAllDurations` | true |
| `epistemicVerbs.heldPulseMs` | 2800 |
| `epistemicVerbs.unknownShimmerMs` | 800 |
| `holdScale` / `crackMs` / `crackPeak` | 1.03 / 80 / 0.97 |

`SpringParams` (android-core, frozen) non ha mass: il renderer sceglie lo spring token per nearest stiffness+damping.

ζ compact = `d / (2√(k m))` = 28 / (2×20) ≈ **0.70** (sottosmorzato → overshoot). Con damping ×3 overshoot assente.

---

## Equazioni verificate

Spring (semi-implicit Euler, dt = 1/240):

```
a = (−stiffness · (x − target) − damping · v) / mass
v ← v + a·dt
x ← x + v·dt
```

Bezier unitario: Newton su x(s)=t, y(s) da control points. Ascolto/crack in Compose usano `CubicBezierEasing` degli stessi array (non `LinearEasing`).

Ripple: `r = rMax · (1 − e^(−t/τ))`, `α = e^(−t/τ)`, origine = (x,y) del pointer (pass Initial), non il centro del controllo.

Rubber: stretch = `o / (1 + o / 48)`, ritorno con la stessa ODE verso 0 (eredita v del fling). Reduced-motion: stretch = 0.

Inertia: `inheritVelocity(state, gestureV)` tiene x, sostituisce v; dopo 50ms la posizione è coerente con v, non con v=0.

Shared element: `nodeIdentity(id, text)` invariato; bounds interpolati con bezier emphasized; morph Compose è **scale** 0.4→1 (non dissolve alpha).

Fluid resize: width e box 1-col→2-col interpolati con lo **stesso** u bezier (non grow-then-relayout).

---

## Tabella audit — MO-001..009

| Test | Invariante | Percorso | Negativo | PASS/GAP |
|------|------------|----------|----------|----------|
| MO-001 | A→B con mass/k/d token; non lineare; overshoot se k alto; damping alto smorza | `MotionPhysics.integrateSettled` compact vs damping×3; ODE `accel` vs formula | curva lineare; Compose `spring()` senza mass | **PASS** |
| MO-002 | nodo→fullscreen, hash invariato; frame intermedi continui | `sharedRect` start ⊂ mid; gif 8 frame; morph `scaleX` | fade alpha; hash che cambia | **PASS** |
| MO-003 | 200→400px, reflow parallelo | a t=0.5 item[1].x ∈ (0, 200) **e** width mid | prima cresce poi relayout (x resta 0) | **PASS** |
| MO-004 | interrupt + v gesto; t+50ms ≠ reset | `inheritVelocity` poi `afterMs(50)` vs v=0 | snap a 0 | **PASS** |
| MO-005 | ripple da (24,96), non da (60,60) | peak luma più vicino all'origine; `contactRipple` + `indication=null` | onda dal centro bottone | **PASS** |
| MO-006 | overscroll stretch visibile; ritorno spring | `contentBottom` cresce; stretch &lt; lineare; integrateSettled → 0 | snap lineare | **PASS** |
| MO-007 | reduced ON → 0ms, no ripple, no rubber | integrateSettled size 1 a target; rubber bottom identico; morph/motion `if (reduced)` | motion come unica resa | **PASS** |
| MO-008 | RX-001..006 + RX-008 + MR-001..007 + LG-001..008; asse anima, non si deforma | compose 49 verdi; mac 23; launcher RX_008; pulse 2800 / shimmer 800 `LinearEasing` | badge distorto; freeze rotto | **PASS** |
| MO-009 | freeze | diff frozen vuoto | tocco a3ui/core | **PASS** |

**GAP: nessuno.**

---

## Bitmap / video campione

| File | SHA-1 | Cosa mostra |
|------|--------|-------------|
| `review-assets/spring-overshoot.mp4` | `9049d844e5a41b48d4628eb02d4f68e14bbf2a34` | massa 1 k=400 d=28, overshoot visibile |
| `review-assets/shared-element.gif` | `c1906380dd3ae0ba8b65e773e39b1e2be2644b1d` | card → fullscreen, stesso label |
| `review-assets/ripple.png` | `0d2ce2b32aeb3a29ca226a57ec14036a6e9d18c2` | anello da (24,96) |
| `review-assets/rubber-band-stretch.png` | `d7c1e324fad2e240d1a0c022a2e7e88a9957c1a0` | contenuto stirato oltre il clip |
| `review-assets/reduced-motion-comparison.gif` | `18ff19d65140557e20f6b090a0a48936e86d843d` | sinistra spring, destra già al target |

---

## Cosa è in questa fase

- ODE spring da token (mass, stiffness, damping) su `ComposeMotionApplier` / `MacMotionApplier`
- Bezier emphasized in ascolto, standard su crack; morph scale spring
- Shared-element: identità nodo + bounds interpolati (raster) / scale continuità (Compose)
- `FluidResizeContainer`: width animata bezier, contenuto misurato al width corrente
- Inertia: `inheritVelocity` su interrupt; rubber fling eredita v
- Ripple dal pointer; rubber-band nested scroll + spring return
- Reduced-motion: appliers identity, ripple/rubber off, integrate istantaneo

## Non toccato

Asse epistemico (`ExposureMotion` / `MacMotion` LinearEasing pulse/shimmer). `a3ui`, core, broker, android-core, launcher. Refraction/shader.

---

## Gate

```
./gradlew :renderers:android-compose:testDebugUnitTest --offline
./gradlew :renderers:mac-compose:test --offline
./gradlew :launcher:testDebugUnitTest --offline --tests a3.launcher.RendererExposureLauncherTest
```

`:renderers:android-compose:testDebugUnitTest` — **49** test, 0 failed (MO-001..009 + LG-001..008 + RX-001..006 + T9/T10/R/FX).  
`:renderers:mac-compose:test` — **23** test, 0 failed (MO Mac + LG Mac + MR-001..007).  
`:launcher` RX_008 + L7 verdi.

Tag: `renderer-android-v0.12` / `renderer-mac-v0.3` solo a verde. Niente push.

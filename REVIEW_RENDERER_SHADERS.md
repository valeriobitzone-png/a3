# REVIEW_RENDERER_SHADERS

AUDIT-FIRST. Protocol: FASE RENDERER-SHADERS (L6 parziale gradienti + L5 modal/ambient + L2 parallasse). Spec `a3ui-graphics-v0.1` (`surfaces.json` glass/acrylic, `elevation.json` zIndex, `motion.json` springs, `colors.json` palette). Scongelati: `:renderers:android-compose`, `:renderers:mac-compose`. Frozen: `core/`, `core/json/`, `core/admission/`, `core/action/`, `prediction/`, `projection/`, `:a3ui`, `:renderers:android-core`, `adapters/`, `intent-model/`, `:broker`, `:launcher`. Niente push.

**Contratto:** una formula, tre programmi (GLSL ES / AGSL / Metal) più eval CPU. Parametri da token: `speed` 0.18, `amplitude` 0.35, `mixAmt` = `glass.fillOpacity × 0.45` (wash, non velo sull'asse). Modal blur = `acrylic.blurRadiusPx` (20) × spring; scrim = `glass.fillOpacity` (0.38). Parallasse solo con tilt reale. AmbientIndicator è chrome compatto, **non** Apple Dynamic Island / Live Activities / hardware iOS.

Differiti: distorsione liquida, particle, masking avanzato.

---

## Freeze (SH-009)

```
git diff --stat -- core/ a3ui/ renderers/android-core/ launcher/ broker/ prediction/ projection/ adapters/ intent-model/
```

vuoto. Solo android-compose + mac-compose (+ REVIEW + review-assets).

---

## Token usati

| Chiave | Valore | Uso |
|--------|--------|-----|
| `glass.fillOpacity` | 0.38 | mixAmt gradiente; scrim modale; fill ambient |
| `acrylic.blurRadiusPx` | 20 | DoF backdrop aperto |
| `zIndex.overlay` / `chrome` | 3 / 4 | ModalSurface / AmbientIndicator |
| `springs.comfortable` | m=1 k=280 d=24 | transizione modal + morph ambient |
| `springs.compact` | m=1 k=400 d=28 | overshoot SH-004 |
| `palette.ink/paper/amber` | #000 / #FFF / #CC8800 | fallback shader; DynamicPalette se estratta |

Nessun ruolo catalogo `island` / `modal` / `toast`. Overlay `approve`/`return` non toccati.

---

## Shader source (stessa semantica)

CPU `ShaderGradient.shade` = GLSL `main` = AGSL `main` = Metal `gradientFragment`:

```
n = 0.5 + 0.5 * sin(τ · (u·0.70 + v·0.40 + t·speed))
m = 0.5 + 0.5 * sin(τ · (u·0.30 − v·0.80 + t·speed·0.73) + amplitude)
color = mix(paper, mix(amber, ink, n), m · mixAmt)
```

File: `src/main/resources/a3ui-graphics/shaders/animated-gradient.{glsl,agsl,metal}`. Android RuntimeShader AGSL è probe (`agslBinds`); Robolectric/raster usano l'eval CPU. Mac: Metal è il programma GPU dichiarato; Compose desktop rasterizza la stessa formula.

---

## FPS (SH-002)

| Piattaforma | Frame | Size | FPS medio |
|-------------|-------|------|-----------|
| Android CPU | 100 | 80×48 | **2965** |
| Mac CPU | 100 | 80×48 | **5140** |

Soglia gate > 55. Target GPU 60: la formula è O(1) per pixel, senza noise/refraction/particle.

---

## AmbientIndicator ≠ Dynamic Island

Chrome flottante (pill → expanded) con spring + morph vettoriale. Host dichiarato: Android `SYSTEM_ALERT_WINDOW` / picture-in-picture; Mac `NSWindow` level `floating`. Permission negata → testo onesto `ambient indicator unavailable`, niente crash e niente silenzio. Non è Live Activities, non è hardware iPhone, non è un ruolo A3UI.

---

## Tabella audit — SH-001..011

| Test | Invariante | Percorso | Negativo | PASS/GAP |
|------|------------|----------|----------|----------|
| SH-001 | t=0 vs t=2s fingerprint e pixel diversi; GLSL/AGSL/Metal presenti; mp4 flusso | `shade` + `encodeFlow` | freeze statico | **PASS** |
| SH-002 | 100 frame FPS > 55 | `ShaderRaster.fps` | jank | **PASS** |
| SH-003 | aperto: blur>0 alpha>0, edgeEnergy giù; chiuso: 0/0 nitido | `ModalBackdrop` + `modalFrame` | dim arbitrario | **PASS** |
| SH-004 | spring non lineare; compact overshoot; reduced → 1 sample | `MotionPhysics` + acrylic | lerp | **PASS** |
| SH-005 | FG > MID > BG; gif tilt | `ParallaxLayers.movement` | stesso offset | **PASS** |
| SH-006 | gyro off → movement 0, layer comunque disegnati | `available=false` | wobble finto | **PASS** |
| SH-007 | fingerprint 8×8 identico Android/Mac | `ddddeeee…fffff` | GPU byte-identity | **PASS** |
| SH-008 | RX/MR/LG/MO/DT verdi; HELD + vetro + glyph + shader; no ruolo island | suite 71 / 45 / launcher | asse coperto | **PASS** |
| SH-009 | freeze | diff frozen vuoto | tocco a3ui/core | **PASS** |
| SH-010 | collapsed→expanded spring; timer/audio/call; host overlay/window | `AmbientChrome.morph` + gif | Dynamic Island claim | **PASS** |
| SH-011 | permission off → messaggio onesto | `ambient-unavailable` | crash / silenzio | **PASS** |

**GAP: nessuno.**

---

## Media campione

| File | SHA-1 | Cosa mostra |
|------|--------|-------------|
| `review-assets/gradient-flow.mp4` | `55a4a8e6eefb16157d63a5c4c39c2533587f6e28` | 2s di flusso (24 frame @ 12 fps) |
| `review-assets/modal-backdrop-before.png` | `9980e46df9b5b3752c6895ecf20e96ea3c2bc6cf` | checker nitido, open=0 |
| `review-assets/modal-backdrop-after.png` | `aa64f7ec4dcf5f6107ecd665f590f21392beb86d` | blur acrylic 20 + scrim 0.38 |
| `review-assets/parallax-tilt.gif` | `b6959ec1e8f9e080c7184ee4a7b6dfc28f8bc6f3` | 3 layer, FG più veloce |
| `review-assets/parallax-fallback.png` | `5b73b51a6689aae043d466c3e6a655fab0569d3e` | stack statico, tilt ignorato |
| `review-assets/ambient-collapsed-expanded.gif` | `78768d7a17708ea3c0d68e8b7cc7887232dd099e` | pill → expanded, spring |
| `review-assets/ambient-fallback.png` | `eb8d25c5181a8b32887dce5c80db1fe1bc343b8a` | `ambient indicator unavailable` |

---

## Cosa è in questa fase

- Gradiente animato token-locked (palette dinamica o ink/paper/amber)
- Modal DoF: Gaussian + scrim da acrylic/glass, ODE comfortable
- ParallaxIcon 3 layer; fallback statico se gyro/mouse assenti
- AmbientIndicator chrome (zIndex.chrome), morph spring, fallback onesto
- Reduced-motion: gradient t=0, modal/ambient snap, parallasse comunque statica senza tilt

## Non toccato

Asse epistemico (pulse/shimmer LinearEasing). Fill vetro catalogo. Occupancy 18sp. `a3ui`, core, broker, android-core, launcher. Refraction, noise, particle.

---

## Gate

```
./gradlew :renderers:android-compose:testDebugUnitTest --offline
./gradlew :renderers:mac-compose:test --offline
./gradlew :launcher:testDebugUnitTest --offline --tests a3.launcher.RendererExposureLauncherTest --tests a3.launcher.LauncherAcceptanceTest
```

`:renderers:android-compose:testDebugUnitTest` — **71** test, 0 failed (SH-001..011 + DT + MO + LG + RX + T9/T10/R/FX).  
`:renderers:mac-compose:test` — **45** test, 0 failed.  
`:launcher` RX_008 + L7 verdi.

Tag: `renderer-android-v0.14` / `renderer-mac-v0.5`. Niente push.

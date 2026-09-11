# REVIEW_RENDERER_DYNAMIC

AUDIT-FIRST. Protocol: FASE RENDERER-DYNAMIC (L1 colore + L2 type/glyph). Spec `a3ui-graphics-v0.1` `tokens/colors.json` + `tokens/typography.json`. Scongelati: `:renderers:android-compose`, `:renderers:mac-compose`. Frozen: `core/`, `core/json/`, `core/admission/`, `core/action/`, `prediction/`, `projection/`, `:a3ui`, `:renderers:android-core`, `adapters/`, `intent-model/`, `:broker`, `:launcher`. Niente push. Contrasti sotto WCAG AA vietati. Glyph allineati all'asse epistemico. Reduced-motion senza transizione. Palette che viola i token vietata.

**Contratto:** snapshot `colors.json` + `typography.json` in `src/main/resources/a3ui-graphics/` (SHA-1 typography `7f692913c3db2d5896ca99b2f511f1ffd9dcd86e`, colors `aae9984c044b0b2e0d833e61b5a55bad42605a10`). Estrazione e assi type sono adapter clock-free (`DynamicPalette`, `DynamicType`, `GlyphGeometry`); Compose/Mac duplicano la matematica, Mac non importa `a3.renderers.android.compose`. Ink/paper restano `#000`/`#FFF`. Vetro, occupancy 18sp, verbi epistemici (pulse 2800 / shimmer 800 LinearEasing) non deformati.

---

## Freeze (DT-011)

```
git diff --stat -- core/ a3ui/ renderers/android-core/ launcher/ broker/ prediction/ projection/ adapters/ intent-model/
```

vuoto. Solo android-compose + mac-compose (+ REVIEW + review-assets).

---

## Token usati (`colors.json` / `typography.json` v0.1)

| Chiave | Valore |
|--------|--------|
| `palette.ink` | `#000000` luminance 0 |
| `palette.paper` | `#FFFFFF` luminance 1 |
| `palette.amber` | `#CC8800` luminance 0.302 |
| `contrast.minRatio` | 4.5 (WCAG AA) |
| `contrast.highContrastMinRatio` | 7.0 (superficie confusa) |
| `semantic.accent` / `success` | palette ink |
| `semantic.anticipation_highlight` | palette amber |
| `fase1Fallback` | 18sp, weight 400, colorToken ink |
| `variable.axes` | wght, opsz (wdth è mapping renderer da `densityHint`) |
| `stack` | system-ui, sans-serif |

`Theme.type` / `MacTheme.type` restano 18sp ink: tracking a 18sp = 0, width comfortable = 1, quindi occupancy e fingerprint RX/MR non rifluiscono.

---

## Equazioni verificate

**Palette (Monet-like, non WallpaperManager):** hue-bucket dominante sui pixel cromatici → primary/secondary/tertiary ruotati; se chroma &lt; 12 o bitmap vuota → `token-fallback` (ink / amber / ink / paper). Ink e paper sono sempre i token. `ensureContrast(fg, paper, 4.5)` prima di accettare la Tonal.

**Luminanza relativa WCAG:** `L = 0.2126 R + 0.7152 G + 0.0722 B` con sRGB linearizzato. Contrasto `(Lmax+0.05)/(Lmin+0.05)`.

**Superficie confusa:** `detailEnergy` = media |Δluma| 4-neighbor. Se ≥ `CONFUSION_MIN` 0.15 → testo adattato a `highContrastMinRatio` 7.0 contro la media del backdrop. Indipendente da motion.

**Type:** pressed → `wght + 50` (400→450). `densityHint==compact` → `wdth` 0.88; comfortable 1; spacious 1.06. Tracking em: `0.08 * (18/size − 1)` → 12sp più largo di 24sp; 18sp = 0.

**Glyph:** polilinee 16 punti (non asset illustrati). PENDING → spinner (`PendingMark` + `id-pending`, rotazione 800ms LinearEasing). DONE → check (`id-glyph-check`). HELD → pause (`id-glyph-held`). Morph = lerp dei punti. Spring `comfortable` sul parametro t; reduced → t=1, nessuna transizione.

---

## Tabella audit — DT-001..011

| Test | Invariante | Percorso | Negativo | PASS/GAP |
|------|------------|----------|----------|----------|
| DT-001 | wallpaper fixture → Tonal non vuota, source extracted, ink/paper token, contrasti ≥ 4.5; bitmap vuota → fallback amber | `DynamicPalette.extract` + `fixtureWallpaper` 80×80; `valid` | palette vuota; ink/paper riassegnati | **PASS** |
| DT-002 | checker alto dettaglio → `confused`; gray 120 adattato, Δ ratio &gt; 0.5 e ≥ AA | `checker` cell 2; `adaptText` usa 7.0 | contrasto che scende | **PASS** |
| DT-003 | pressed → weight +50; inkCount pressed &gt; rest (Android) / ≥ (Mac) | `DynamicType.weight`; `typeSample` fake-bold | weight invariato | **PASS** |
| DT-004 | compact → width 0.88; contentRight condensed &lt; comfortable | `DynamicType.width("compact")`; `textScaleX` / scaleX | expanded in densità alta | **PASS** |
| DT-005 | 12sp tracking em &gt; 24sp; 18sp = 0 | `trackingEm`; bitmap 12sp/24sp | tracking identico | **PASS** |
| DT-006 | morph spinner→check: fingerprint start ≠ mid ≠ end | `GlyphGeometry.morph` 0.45; strip 5 frame | salto senza intermedi | **PASS** |
| DT-007 | PENDING → spinner `id-pending`; DONE → `glyph-check`; HELD → `glyph-held` | Compose `axisOut` / Mac calendar hotel+meeting | glyph slegato dall'asse | **PASS** |
| DT-008 | reduced → glyph già a t=1; contrasto adatta comunque | `points(..., reduced=true)` == t=1; `adaptText` senza clock | motion come unica resa | **PASS** |
| DT-009 | stesso fixture → stessi ink/paper token, 6 ruoli, source extracted su Android e Mac | extract 64×64 identico nei due moduli | palette che diverge dai token | **PASS** |
| DT-010 | RX-001..008 + MR-001..007 + LG-001..008 + MO-001..009 verdi; HELD + vetro + glyph coesistono | suite compose 60 / mac 34 / launcher RX_008+L7; pulse 2800 | conflitto asse/vetro/motion/colore | **PASS** |
| DT-011 | freeze | diff frozen vuoto | tocco a3ui/core/broker | **PASS** |

**GAP: nessuno.**

---

## Bitmap campione

| File | SHA-1 | Cosa mostra |
|------|--------|-------------|
| `review-assets/palette-extracted.png` | `6679ef10813358c3c693b7b2e9da86efba43a7fa` | 6 swatch: primary, secondary, tertiary, neutral, ink, paper |
| `review-assets/adaptive-contrast-before.png` | `cb92935e0aa09f0d4dc52821e9f57c05d7db825f` | testo gray 120 sul checker |
| `review-assets/adaptive-contrast-after.png` | `174aa3985017348569436e69fb0583ff7f0c86c0` | stesso checker, testo portato a contrasto ≥ 7.0 |
| `review-assets/variable-weight-pressed.png` | `114aa40b4bf41d6ece0986c6279c755d362fffc8` | "Ag" a weight 450 |
| `review-assets/optical-sizing-12sp.png` | `8c1a02626fa56bcff6ae98c9f445a80f5f7a3618` | "AVAV" 12sp, tracking più largo |
| `review-assets/optical-sizing-24sp.png` | `1c0ef433cdacc39353776e88944c5a991d87d9f5` | "AVAV" 24sp, tracking più stretto |
| `review-assets/glyph-morph-frames.png` | `df6f28fccc679c4cb375d818a2fdb6eb8ea7eecd` | 5 frame spinner → check |

---

## Cosa è in questa fase

- Estrazione tonale dal wallpaper fixture (primary/secondary/tertiary/neutral) con fallback token
- Contrasto adattivo su superficie ad alto dettaglio, soglia WCAG AA / AAA-ish 7.0
- Variable type: weight +50 on press (solo `role==action`), width da densità, optical tracking
- AnimatedGlyph geometrico: spinner/check/pause legati a PENDING/DONE/HELD; morph lerp; spring sul t
- Reduced-motion: glifo allo stato finale, contrasto ancora adattato

## Non toccato

Asse epistemico (`ExposureMotion` / `MacMotion` LinearEasing pulse/shimmer). Fill vetro. `ExposureRaster` / `MacRaster`. `a3ui`, core, broker, android-core, launcher. Shader gradienti animati (S-D4). Icone 3D/parallasse (S-D4).

---

## Gate

```
./gradlew :renderers:android-compose:testDebugUnitTest --offline
./gradlew :renderers:mac-compose:test --offline
./gradlew :launcher:testDebugUnitTest --offline --tests a3.launcher.RendererExposureLauncherTest --tests a3.launcher.LauncherAcceptanceTest
```

`:renderers:android-compose:testDebugUnitTest` — **60** test, 0 failed (DT-001..011 + MO-001..009 + LG-001..008 + RX-001..006 + T9/T10/R/FX).  
`:renderers:mac-compose:test` — **34** test, 0 failed (DT Mac + MO Mac + LG Mac + MR-001..007).  
`:launcher` RX_008 + L7 verdi.

Tag: `renderer-android-v0.13` / `renderer-mac-v0.4`. Niente push.

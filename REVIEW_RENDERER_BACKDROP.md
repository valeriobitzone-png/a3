# REVIEW_RENDERER_BACKDROP

AUDIT-FIRST. Protocol: FASE RENDERER-BACKDROP. Scongelati: `:renderers:android-compose`, `:renderers:mac-compose`. Frozen: `core/`, `a3ui/`, `broker/`, `agent/`, `launcher/`, `adapters/`, `:showcase`. Niente push.

Diagnosi: `GlassSurface` sfocava un layer bianco, non la scena.

---

## Diagnosi (SV-001, verificata)

`GlassSurface` / `MacGlassSurface` sfocavano un layer `.background(Color.White)` e ci stendevano fill nero a `fillOpacity` 0.38. `RenderEffect` / `Modifier.blur` filtrano i pixel **del layer**, non la scena (non è CSS `backdrop-filter`). Bianco + 38% nero = lastra grigia. I test LG sembravano vetro perché `GlassRaster.paint()` / `MacGlassRaster.paint()` già compositavano una replica dello sfondo *dentro* il bitmap — un percorso diverso dalla scena Compose.

---

## Fix: shared-background blur

Stessa tecnica su Android e Mac (`GlassBackdrop.TECHNIQUE = "shared-background"`):

1. `GlassSceneHost` dipinge il wallpaper fixture **nitido** dietro il catalogo (cell 10, stessi 4 colori di `DynamicPalette.fixtureWallpaper`).
2. Ogni `GlassSurface` / `MacGlassSurface` dipinge una **replica** window-aligned dello stesso wallpaper, clippata alla squircle, e **sfoca quella replica** (`RenderEffect.createBlurEffect` / `Modifier.blur` + vibrancy ColorMatrix).
3. Fill token, highlight inner/outer, ambient/key restano sopra. I badge restano content a opacità 100%.

Il catalogo reale è vetro, non solo la showcase. Robolectric non fa `PixelCopy`; il bitmap Android `catalogo-reale-dopo.png` è il percorso misurabile `GlassRaster` (replica sfocata + overlay catalogo). Mac cattura il Compose vivo (`catalogo-reale-dopo-mac.png`).

---

## Adozioni open (licenze verificate)

| Libreria | Licenza | Uso | Esito |
|----------|---------|-----|-------|
| **material-color-utilities** (algoritmo Google, packaging [MaterialKolor 1.7.1](https://github.com/jordond/materialkolor)) | Sorgenti quantize/score/palettes: **Apache-2.0 Google LLC**. Artifact Maven: **MIT** | `QuantizerCelebi` + `Score` + `CorePalette.contentOf` in `DynamicPalette.extract`. Ink/paper restano i token a3ui-graphics. Contrasti WCAG AA via `ensureContrast`. | **usata** |
| **BlurView** (EightBitLab) | Apache-2.0 | Backdrop nativo View, non Compose, non Mac | **scartato** — shared-background è sufficiente e identico cross-platform |

**Tenuti custom:** spring Compose, squircle n=5, audio PCM, haptic.

---

## Freeze (GB-007)

```
git diff --stat -- core/ a3ui/ broker/ agent/ launcher/ adapters/ showcase/ prediction/ projection/ intent-model/ renderers/android-core/
```

vuoto. Solo android-compose + mac-compose + questo REVIEW + `review-assets/`.

---

## Tabella audit — GB-001..007

| Test | Invariante | Percorso | PASS |
|------|------------|----------|------|
| GB-001 | anello sotto-vetro = replica sfocata, chroma ≫ lastra bianca | `GlassRaster.paint(wallpaper)` vs `paint(WHITE)` + replica Compose | **PASS** |
| GB-002 | gutter nitido vs anello sfocato, stesso wallpaper | edge energy blur/sharp < 0.75 | **PASS** |
| GB-003 | MCU: stessa palette per stesso wallpaper; AA ≥ 4.5 | `extract` due volte + `QuantizerCelebi` | **PASS** |
| GB-004 | badge epistemici α=255 sopra il vetro | overlay stamp + `n1-uncertain` / hotel-held | **PASS** |
| GB-005 | Android e Mac stessa tecnica shared-background | `GlassBackdrop.TECHNIQUE` | **PASS** |
| GB-006 | regression RX/MR/LG/MO/DT/SH/SE | suite compose | **PASS** |
| GB-007 | freeze core/a3ui/broker/agent/showcase | `git diff` | **PASS** |

Gate:

```
./gradlew :renderers:android-compose:testDebugUnitTest --offline
./gradlew :renderers:mac-compose:test --offline
```

Entrambi verdi.

---

## Bitmap

| File | SHA-1 | Note |
|------|-------|------|
| `catalogo-reale-prima.png` | `add84ae3e673cefb2413e68c33b64df249fb0956` | v0.1 Phone 3, lastra grigia (copia `showcase/v0.1/glass.png`) |
| `catalogo-reale-dopo.png` | `285342aa18fb75903ae9e652feac1cbd06bf07bd` | wallpaper fixture sfocato sotto-vetro + catalogo calendar |
| `anello-sfocato.png` | `e9b061e331f527e4dc1633eae544c8df582aabf8` | gutter nitido (angoli squircle) vs interno sfocato |
| `catalogo-reale-dopo-mac.png` | `0672f86b2c59d64d85280f87471575f7049e1415` | MacRenderer calendar Compose, stessa tecnica |
| `anello-sfocato-mac.png` | `c57c7c1f4e80b234348f11dfc7726b8db3b04b31` | raster Mac |
| `gb-004-badge-on-glass.png` | `c38ede6d6b26c03222d6c3bcc720e79e961ff452` | UNKNOWN α=255 sopra il vetro |
| `palette-extracted.png` | `c330e0c3b111067537fca4a22e2110afd4380348` | MCU: 6 ruoli, ink/paper token |

---

## Tag

`renderer-android-v0.16` / `renderer-mac-v0.7` solo a verde. Niente push.

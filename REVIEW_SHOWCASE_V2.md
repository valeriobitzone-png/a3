# REVIEW_SHOWCASE_V2

AUDIT-FIRST. Protocol: FASE SHOWCASE-V2. Scongelati: `:showcase`, `:showcase:mac`. Frozen: core, a3ui, broker, launcher, adapters, renderers. Niente push.

flat-gray-because-white-glass-layer-plus-missing-wallpaper

---

## SV-001 Diagnosi verificata (non ipotizzata)

Causa del vetro grigio piatto in v0.1, letta da screenshot Phone 3 / Mac **e** dal source frozen.

**Non** era un checkerboard di trasparenza Compose. Su `all.png` v0.1 il reticolo è `ShowcaseGradient` a step 32 (presente solo su ALL/shaders). Su `glass.png` v0.1 quel reticolo sparisce e resta un campo grigio uniforme.

Tre fatti concatenati:

1. **Wallpaper fixture non montato.** `DynamicPalette.fixtureWallpaper` (amber / teal / ink / paper, cella 10px) vive nel renderer (`internal`) e i test DT_001 / `GlassRaster.checker` lo usano solo nei bitmap di laboratorio. `:showcase` dipingeva `paper` come host. I livelli glass / axis / motion / dynamic spegnevano anche il gradient. Dietro il catalogo non c'era materia cromatica.

2. **GlassSurface non sfoca il backdrop.** In `GlassSurface.kt` / `MacGlassSurface.kt` (frozen) il layer con RenderEffect / `Modifier.blur` è `.background(Color.White)`, poi un overlay `fillColor` `#000000` a `fillOpacity` **0.38**. `RenderEffect.createBlurEffect` filtra i pixel **del layer**, non la scena dietro (non è CSS `backdrop-filter`). Bianco sfocato + nero al 38% = lastra grigia. I test LG sembrano vetro perché `GlassRaster.paint()` composita il checker **dentro** il bitmap, un percorso diverso dalla scena Compose.

3. **I container del catalogo avevano già GlassSurface.** `ComposeCatalog` / `MacCatalog` wrappano stack / row / list / item. La showcase non applicava un secondo vetro; sembrava assente perché il vetro non aveva nulla da frostare. I controlli erano chip `RoundedCornerShape` su barra `paper` opaca, fuori scena glass.

`Color.White` è ancora nel renderer (frozen, corretto: la showcase non lo tocca). v0.2 monta il wallpaper fixture dietro ogni schermata, lascia un gutter nitido, frost sotto-vetro misurabile, plate squircle + highlight, chip glass.

---

## Freeze (SV-006)

```
git diff --stat -- core/ a3ui/ broker/ launcher/ adapters/ prediction/ projection/ intent-model/ renderers/
```

vuoto. Solo `:showcase` / `:showcase:mac` + questo REVIEW + `review-assets/showcase/`.

---

## Prima / dopo

| | v0.1 | v0.2 |
|--|------|------|
| Dietro il catalogo | paper, gradient solo ALL/shaders | fixture wallpaper (stessa formula di `fixtureWallpaper`) in **ogni** livello |
| Vetro catalogo | bianco + fill 0.38, grigio | plate squircle, wallpaper sfocato sotto, overlay frost, highlight inner/outer |
| Controlli | chip Material-like su barra carta | `ShowcaseGlassChip` squircle + highlight |
| `glass.png` | campo grigio | wallpaper visibile nel gutter + lastra frostata |

Copia v0.1: `review-assets/showcase/v0.1/`.

---

## Tabella audit — SV-001..006

| Test | Invariante | Percorso | PASS |
|------|------------|----------|------|
| SV-001 | diagnosi scritta e verificata (`Color.White` + wallpaper assente) | questo file + `ShowcaseDiagnosis` + source renderer | **PASS** |
| SV-002 | wallpaper opaco e cromatico in ogni schermata | tag `showcase-wallpaper` | **PASS** |
| SV-003 | edge energy sotto-vetro < 75% del nitido | `ShowcaseWallpaper.blurredAt` vs `colorAt` + gutter | **PASS** |
| SV-004 | squircle n=5, highlight inner/outer, plate in scena | token + tag `showcase-glass-highlight` | **PASS** |
| SV-005 | badge HELD / CONTRADICTED visibili sopra il vetro | catalog tags | **PASS** |
| SV-006 | SC-001..008 + freeze renderer/core/a3ui | suite + `git diff` | **PASS** |

Gate:

```
./gradlew :showcase:testDebugUnitTest --offline
./gradlew :showcase:mac:test --offline
```

---

## Media v0.2

Phone 3 `adb screenrecord` 40.09s, 1260×2800, metadata Nothing Phone (3) / Android 17. Mac: sessione Compose 7.00s Skiko (TCC display-camera ancora assente, `MAC.txt`).

| File | SHA-1 | Note |
|------|-------|------|
| `showcase-android.mp4` | `15e089ee5eb4c86d039f7915a00c1ea22c8d782c` | tour ALL + livelli, wallpaper visibile |
| `showcase-mac.mp4` | `990428ce347856dad905c0cddc718de2ad6c902e` | stessa navigazione Compose |
| `all.png` | `c5afb0c16a6a04606eb0921c0eb1d9d86e73844f` | Phone 3, gutter nitido + plate frostato |
| `glass.png` | `03b3060bfbb8cff86b5ba7669ed289ae0e8c4d4f` | Phone 3, vetro visibile (non grigio piatto) |
| `axis.png` | `a7439518b17c56abb99130ae27ac2e372638bf31` | |
| `motion.png` | `f6b09413e4e558d7393cb7eddbcec726bf35546f` | |
| `dynamic.png` | `cdc2b26fbcc2f3b6e795871b2dd680da7a7effac` | |
| `shaders.png` | `617d569809adbce909bc629b7b884fbc918fde1c` | |
| `sensory.png` | `48d75c44fd2b061f9f4e5f2d74e50c4579cf894c` | |
| `mac/all.png` | `4c6264f07e9a5a224a48caeb3d0fa6edf4bc1c30` | |
| `mac/glass.png` | `12a4193d806e5ce6bb2f7f7da2f91cfab9bcaa7e` | |
| `mac/axis.png` | `e7371f9096d8dead1013ecc12451b10850a04a2b` | |
| `mac/motion.png` | `ccef65cbfb4be3e07da51269f53ad9817629678e` | |
| `mac/dynamic.png` | `3c447af79a3cf9b99ccc1727fad80aaf7c67c4e9` | |
| `mac/shaders.png` | `b83daf54511387aa02127c295e2a73489c86b077` | |
| `mac/sensory.png` | `5c07d6c508f7ac439aa66ee318ff302529af14c3` | |
| `showcase-haptic.json` | `1b911cab2227b18ead461f5a934c85b03b827ddc` | VibrationEffect, sessione Phone 3 |
| `v0.1/` | (gallery precedente) | prima: campo grigio, niente wallpaper |

---

## Tag

`showcase-v0.2` solo a gate verde. Niente push.

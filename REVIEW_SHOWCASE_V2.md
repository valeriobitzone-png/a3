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

`Color.White` è ancora nel renderer (frozen, corretto: la showcase non lo tocca). Il catalogo *interno* resta una lastra grigia: è il renderer frozen. Il vetro **visibile** in v0.2 è fuori da quel fill: wallpaper nitido nel gutter, anello frostato (wallpaper sfocato) sotto la lastra squircle, highlight inner/outer, chip frost al posto dei pill Material.

v0.2 monta il wallpaper fixture dietro ogni schermata, lascia un gutter nitido, frost sotto-vetro misurabile, plate squircle + highlight, chip glass.

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
| Vetro catalogo | bianco + fill 0.38, grigio a tutto schermo | plate squircle, wallpaper sfocato nell'anello, highlight inner/outer; interno catalogo ancora grigio (renderer frozen) |
| Controlli | chip Material bianchi (`fill 0.62`) su barra carta | `ShowcaseGlassChip` squircle + frost wallpaper, nessun fill `0.62` |
| `glass.png` | campo grigio, niente gutter | wallpaper nitido nel gutter + anello frostato visibile |

Copia v0.1: `review-assets/showcase/v0.1/`.

Confronto bitmap (SV-003): `ShowcaseWallpaper.colorAt` (nitido) vs `blurredAt` (sotto-vetro): edge energy blur / sharp < 0.75. Negli screenshot reali il gutter è a celle nette; l'anello sotto il rim squircle è lo stesso pattern sfocato.

---

## Tabella audit — SV-001..006

| Test | Invariante | Percorso | PASS |
|------|------------|----------|------|
| SV-001 | diagnosi scritta e verificata (`Color.White` + wallpaper assente) | questo file + `ShowcaseDiagnosis` + source renderer | **PASS** |
| SV-002 | wallpaper opaco e cromatico in ogni schermata | tag `showcase-wallpaper` + bitmap device non trasparente | **PASS** |
| SV-003 | edge energy sotto-vetro < 75% del nitido | `ShowcaseWallpaper.blurredAt` vs `colorAt` + gutter vs anello | **PASS** |
| SV-004 | squircle n=5, highlight inner/outer, plate in scena | token + tag `showcase-glass-highlight` + rim visibile in gallery | **PASS** |
| SV-005 | badge HELD / CONTRADICTED visibili sopra il vetro | catalog tags, opacità 100%, ink sul catalogo | **PASS** |
| SV-006 | SC-001..008 + freeze renderer/core/a3ui | suite + `git diff` | **PASS** |

Gate:

```
./gradlew :showcase:testDebugUnitTest --offline
./gradlew :showcase:mac:test --offline
```

verde dopo recapture Phone 3 + Mac.

---

## Media v0.2

Phone 3 `adb screenrecord` 40.04s, 1260×2800, metadata Nothing Phone (3) / Android 17. Mac: sessione Compose 7.00s Skiko (TCC display-camera ancora assente, `MAC.txt`).

| File | SHA-1 | Note |
|------|-------|------|
| `showcase-android.mp4` | `7f793a5698e44d09a366a19b348aa95e425c1f82` | tour ALL + livelli, wallpaper + frost chips |
| `showcase-mac.mp4` | `309bddba628cd15d3521cd51e78e0eac0c370536` | stessa navigazione Compose |
| `all.png` | `533a9874564ba633901239812e988d94eb9b9954` | Phone 3, gutter nitido + anello frostato + chip glass |
| `glass.png` | `217f6b4f3e1213465b015911bbd2d804b93f99d6` | Phone 3, vetro visibile (non grigio piatto) |
| `axis.png` | `6dd4e5ba487aa5fd413b4860d41be41135a9d434` | |
| `motion.png` | `c43cf72c6d9c7749cef63be60e01ab709bd5c2c6` | |
| `dynamic.png` | `286c8bcd0cc43d1d3def4a07aa6755be3c916e9e` | |
| `shaders.png` | `227b1c62c06bcf2c855128ed45a2a2d621c0efec` | |
| `sensory.png` | `de607da1ce861f84cbc1c7241cbbc1b9c0875a5f` | |
| `mac/all.png` | `97d12c68aa2d412bfd633b07494d2fa7bee56d3c` | |
| `mac/glass.png` | `1449f31509f4f6666cb602ea9b04f907f1f53ae1` | gutter + anello frost + chip glass |
| `mac/axis.png` | `eed6363afcfa52c87198d739b6971ac6f5b82cd1` | |
| `mac/motion.png` | `729049ab5914c26e6d564483894740e06b93b92b` | |
| `mac/dynamic.png` | `8c46f846d688b9c47495d4931d36d6ba4faf0e88` | |
| `mac/shaders.png` | `74f34f4c80a7bededae4be0ec53d1c5b90749c3c` | |
| `mac/sensory.png` | `d986c44752fe9eca7140c771cee38dbb790daa47` | |
| `showcase-haptic.json` | `547f15c2122a9e3bc0e1ea6b02d2ae4e4fea351d` | VibrationEffect, sessione Phone 3 |
| `v0.1/` | (gallery precedente) | prima: campo grigio, chip Material, niente wallpaper |

---

## Tag

`showcase-v0.2` solo a gate verde. Niente push.

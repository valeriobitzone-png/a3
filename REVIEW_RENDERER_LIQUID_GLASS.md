# REVIEW_RENDERER_LIQUID_GLASS

AUDIT-FIRST. Protocol: FASE RENDERER-LIQUID-GLASS (Fase 1). Spec `a3ui-graphics-v0.1`. Scongelati: `:renderers:android-compose`, `:renderers:mac-compose`. Frozen: `core/`, `a3ui/`, `android-core`, `adapters/`, `intent-model`, `broker/`, `launcher/`, prediction/projection. Niente push. Niente refraction / noise / shader custom.

**Contratto:** snapshot `a3ui-graphics-v0.1` in `src/main/resources/a3ui-graphics/` (colors, elevation, surfaces). Glass è materializzazione, non un ruolo. Badge epistemici restano a opacità 100% sopra il vetro.

---

## Freeze (LG-008)

```
git diff --stat -- core/ a3ui/ renderers/android-core/ launcher/ broker/ prediction/ projection/ adapters/ intent-model/
```

vuoto. Solo android-compose + mac-compose (+ REVIEW + review-assets).

---

## Tabella audit — LG-001..008

| Test | Invariante | Percorso | Negativo | PASS/GAP |
|------|------------|----------|----------|----------|
| LG-001 | blur visibile; API &lt; 31 fallback | `GlassRaster` Gaussian vs sharp, energy ratio &lt; 0.75; `RenderEffect.createBlurEffect` su API 31+; SDK 30 → `glass-fallback` + `"blur unavailable"` | blur senza fallback; ratio ≥ 1 | **PASS** |
| LG-002 | vibrancy | sat(blur+vibrancy) − sat(blur) &gt; `SATURATION_DELTA_MIN` (0.01), ColorMatrix 1.4 | boost assente; desaturazione | **PASS** |
| LG-003 | highlight 1px | anello outer scuro + inner chiaro vs stesso raster senza rim | solo fill | **PASS** |
| LG-004 | squircle ≠ round-rect | (4,4) su 80×80 r=20 n=5: round fuori, squircle dentro | stesso fingerprint | **PASS** |
| LG-005 | nested radius | `max(4, outer − padding)`; 14−16 → 4, non zero | inner 0 | **PASS** |
| LG-006 | cross-platform | blurRatio Android 0.082 / Mac 0.080; sat ~0.11 / 0.10; inner true. Non byte-identity | una piattaforma senza vetro | **PASS** |
| LG-007 | RX + asse | RX-001..006 compose verdi; MR-001..007 verdi; overlay UNKNOWN stampato α=255, pixel identici all'inchiostro | badge sfocato / alpha ridotta | **PASS** |
| LG-008 | freeze | diff frozen vuoto; snapshot JSON presente | tocco a3ui/core | **PASS** |

**GAP: nessuno.**

---

## Bitmap campione

| File | SHA-1 | Cosa mostra |
|------|--------|-------------|
| `review-assets/android-glass.png` | `1264741be6b7b446cebf83629d397e340eb7bf1c` | checker + vetro (blur 12, vibrancy 1.4, fill 0.38, rim) + badge UNKNOWN |
| `review-assets/mac-glass.png` | `4f5bdec2a596487797743bbb99fadc1dbb0fecbe` | stessa semantica, raster AWT |
| `review-assets/squircle-vs-round.png` | `8bf57fee348a5fd065c8786f34e70310d15f8f7e` | round-rect a sinistra, squircle n=5 a destra |
| `review-assets/api-fallback.png` | `322c8fd0ce081770d9752e8f0ed265a7ba395e68` | niente blur, overlay + testo `blur unavailable` |

Fingerprint qualitativo (LG-006): `blurRatio≈0.08` su entrambe le piattaforme (energia dei bordi dopo Gaussian). GPU byte-identity non richiesta.

---

## Cosa è in Fase 1

- Gaussian blur (software raster misurabile; Android S+ `RenderEffect.createBlurEffect` + ColorMatrix chain; Mac Compose `Modifier.blur` + `ColorMatrix.setToSaturation`)
- Vibrancy 1.40
- Inner/outer highlight 1px (token alpha 0.28 / 0.40)
- Ambient + key shadow da `elevation.json` (`azimuthDeg` 135)
- Squircle n=5, r=14; nested `max(4, outer − padding)`
- Container glass: catalogo + `stack` / `row` / `list` / `item`. Copy e marks sopra il chrome.

## Differito

Refraction, noise, Monet, contrasto adattivo, variable type, icone animate, spring/morph, ripple, island/modal, audio/haptic nuovi.

---

## Gate

```
./gradlew :renderers:android-compose:testDebugUnitTest --offline
./gradlew :renderers:mac-compose:test --offline
```

`:renderers:android-compose:testDebugUnitTest` — 40 test, 0 failed (LG-001..008 + RX-001..006 + T9/T10/R/FX).  
`:renderers:mac-compose:test` — 14 test, 0 failed (LG Mac + MR-001..007).

Tag: `renderer-android-v0.11` / `renderer-mac-v0.2` solo a verde. Niente push.

# REVIEW_RENDERER_FIX_UNKNOWN

AUDIT-FIRST. Protocol: FASE RENDERER-FIX-UNKNOWN (resa UNKNOWN distinta da LOW). Baseline `4b26b91` (`launcher-v0.5` chiuso). Core / admission / action / json / prediction / projection / adapters / intent-model / broker / **`:a3ui`** / **`:renderers:android-core`** **frozen**. Niente push.

**Unfrozen:** `:renderers:android-compose` (solo resa UNKNOWN), `:launcher` (test).  
**Tesi A3UI-3:** LOW e UNKNOWN non possono apparire simili. LOW resta opacità 50% + barre. UNKNOWN è un riquadro tratteggiato + badge `uncertain` a opacità 100%.

---

## Freeze (FX-004)

```
git diff --stat -- core/ core/json core/admission core/action prediction/ projection/ adapters/ intent-model/ broker/ a3ui/ schemas/ renderers/android-core/
```

vuoto.

Moduli eseguiti **separati**, niente aggregato, niente `--rerun-tasks`:

```
./gradlew :renderers:android-compose:testDebugUnitTest --offline
./gradlew :launcher:testDebugUnitTest --offline
```

LP-006 (fase precedente) ora congela `renderers/android-core/` e non tutto `renderers/`, perché compose è scongelato in questa fase.

---

## Tabella audit — FX-001..004

| Test | Invariante | Percorso | Negativo | PASS/GAP |
|------|------------|----------|----------|----------|
| FX-001 | LOW = 50% + barre, no tratteggio | conf 0.4 → `EpistemicSupport.LOW`; tag `-low`; colonne `LOW_BAR_X1/X2` piene; riga inset senza frame dashed | tratteggio su LOW; badge `uncertain` | **PASS** |
| FX-002 | UNKNOWN = dash + `uncertain`, opacità 100%, no barre | `support UNKNOWN`; `textAlpha=1`; tag `-uncertain`; inset dashed con gap; `contentDescription` contiene `uncertain` | opacità ridotta; barre `\|\|` | **PASS** |
| FX-003 | bitmap diverse | stesso nodo (`12.40` / `train.price`), asse LOW vs UNKNOWN → fingerprint ≠ | collisioni di seed | **PASS** |
| FX-004 | regression + freeze | RX-001..006 compose + RX-008 launcher verdi; diff frozen vuoto | regressione RX; tocco a3ui/core | **PASS** |

**GAP: nessuno.**

Il campo a3ui `stateDescription` resta `support unknown` (catalogo congelato). La frase di protocollo `uncertain` è badge + `contentDescription`. Non si riapre `:a3ui`.

---

## Bitmap LOW vs UNKNOWN (FX-001 / FX-002)

Seed `ExposureRaster` 320×80, anti-alias off, stesso copy `12.40`.

| File | SHA-256 | Cosa mostra |
|------|---------|-------------|
| `review-assets/fx-001-low.png` | `7fdde2860d03aa3c66cc2a0b5b1491f8812e6d3b96c4b2bbb55ea9875d1a9a93` | `12.40` al 50% + barre laterali `\|\|`; **nessun** riquadro tratteggiato |
| `review-assets/fx-002-unknown.png` | `af9559a8a38959b83eb247b52dbd317ad80638aaec07ba9598ce438013e23e2a` | `12.40` a opacità piena + riquadro tratteggiato + badge `uncertain`; **nessuna** barra |

Fingerprint diversi (FX-003).

---

## Resa UNKNOWN (solo compose)

- **Chrome:** padding 10.dp + `dashPathEffect` inset (8.dp / 6.dp), stroke 3.dp. Non condivide le colonne delle barre LOW (x=2, x=7). Raster: inset 12px, dash 16/10.
- **Opacità:** `Exposure.textAlpha(UNKNOWN) = 1f` (invariato). Lo shimmer NON moltiplica più l'alpha (partiva da 0.4 e rendeva UNKNOWN simile a LOW 0.50). Verbo invariato (800ms, una tantum): translationX one-shot, non fade.
- **LOW invariato:** due `drawLine`, alpha 0.50, badge `\|\|`.

---

## Gradle (output reale)

`:renderers:android-compose:testDebugUnitTest` — 31 test, 0 failed (FX-001..003 + RX-001..006 + AX-004 + T9/T10/R9/R10).  
`:launcher:testDebugUnitTest` — 30 test, 0 failed (FX-001..004 + RX-008 + LP/L/G).

Tag **locali** a verde: `renderer-android-v0.10`, `launcher-v0.6`. `a3ui-core-v0.5` non bumpato. Niente push.

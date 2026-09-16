# REVIEW_RENDERER_MAC

AUDIT-FIRST. Protocol: FASE RENDERER-MAC (profilo macOS via Compose Multiplatform desktop). Baseline `dc9637d` (`renderer-android-v0.10` chiuso). Core / admission / action / json / prediction / projection / adapters / intent-model / broker / **`:a3ui`** / **`:renderers:android-core`** / **`:renderers:android-compose`** / **`:launcher`** **frozen**. Niente push. Niente KMP prodotto (niente target Android/iOS): modulo JVM + `org.jetbrains.compose` desktop.

**Nuovo:** `:renderers:mac-compose`.  
**Contratto:** stessa semantica di android-compose v0.10. I tipi arrivano da `RenderedOutput` (`:renderers:android-core`). Main non importa `a3.a3ui` / `a3.core` / `a3.renderers.android.compose` / `a3.broker`.

---

## Freeze (MR-007)

```
git diff --stat -- core/ a3ui/ renderers/android-core/ renderers/android-compose/ launcher/ broker/ prediction/ projection/ adapters/ intent-model/
```

vuoto.

```
./gradlew :renderers:mac-compose:test --offline
```

Niente aggregato. `:android-compose` / `:a3ui` / `:core` non riaperti.

---

## Tabella audit — MR-001..007

| Test | Invariante | Percorso | Negativo | PASS/GAP |
|------|------------|----------|----------|----------|
| MR-001 | 4 assi S-C + UNKNOWN dashed | fixture calendario → meeting DONE, flight AGING+PENDING, hotel LOW+STALE+HELD+action UNKNOWN, dinner CONTRADICTED+COMPENSATED; nodo `train.price` conf 0 → dash + `uncertain`, no barre | LOW dipinto come UNKNOWN; 4 assi identici | **PASS** |
| MR-002 | PresentationHash byte-identity | `PresentationHash.of(SurfaceComposer.compose(calendar))` = `4159f43f6537a84eea0ea920983a974d97c4ca6805df3efb9607e89302ee5dde` (Android S-C) | hash diverso; renderer che muta il tree | **PASS** |
| MR-003 | VoiceOver | `semantics.contentDescription` = copy + stateDescription + frasi protocollo; `stateDescription` a3ui sul nodo | nodo non-default muto | **PASS** |
| MR-004 | Reduce Motion | `MacExposure.motionMs(*, true)=0`; mark visibili; nessun `-motion-*`; raster identico al normale (motion fuori dal paint) | motion come unica resa | **PASS** |
| MR-005 | alto contrasto | hatch AGING, doppio bordo hotel, fingerprint 4 nodi distinti | distinzione solo ambra | **PASS** |
| MR-006 | ArchUnit | `a3.renderers.mac.compose` ↛ `android.compose` / `a3.core` / `a3.a3ui` / `a3.broker`; gradle `implementation` solo `:renderers:android-core` + compose desktop | import a3ui in main | **PASS** |
| MR-007 | freeze | diff frozen vuoto | tocco android-compose/core | **PASS** |

**GAP: nessuno.**

---

## Semantica (v0.10, parse di `stateDescription`)

| Asse | Resa |
|------|------|
| support HIGH | default |
| MEDIUM | opacità 75% + barra `\|` |
| LOW | opacità 50% + barre `\|\|` |
| UNKNOWN | opacità 100%, riquadro tratteggiato inset, badge `uncertain`, **no** barre |
| AGING | wash `/` (hatch in alto contrasto) |
| STALE | bordo ambra (doppio nero in alto contrasto) + `stale` |
| HELD | `[held]` + pulse 2800 |
| CONTRADICTED | strikethrough + `contradicted` + crack-reverse 240 |
| PENDING | spinner |
| action UNKNOWN | `[unknown]` + shimmer translationX 800 |
| DONE | checkmark |
| COMPENSATED | strikethrough + `compensated` + fade-back 220 |

Reduce Motion di sistema: `defaults read com.apple.universalaccess reduceMotion` (e property `a3.reduce.motion`). Alto contrasto: `increaseContrast` / `differentiateWithoutColor` (e `a3.high.contrast`).

---

## presentationHash campione (MR-002)

```
4159f43f6537a84eea0ea920983a974d97c4ca6805df3efb9607e89302ee5dde
```

Identico a `REVIEW_LAUNCHER_PROOF.md`. Motion e formFactor `desktop` fuori dal digest.

---

## stateDescription / VoiceOver campione

```
Hotel Milano support low freshness stale status held action unknown stale pending review unknown
Prenotazione ristorante support medium status contradicted action compensated contradicted compensated
Volo 14:30 — FCO→LIN support medium freshness aging action pending
Riunione 09:00 — Ada action done
```

---

## Screenshot (MR-001 / 003 / 004 / 005)

Seed raster AWT 320×80 per foglia (stesso contratto di `ExposureRaster`, senza dipendere da android-compose). Reduce-motion e normal condividono il fingerprint: il motion non entra nei pixel.

| Path | SHA-256 | Cosa prova |
|------|---------|------------|
| `review-assets/mac-renderer-normal.png` | `f27f961dca1770124453f9696dada85e99a74440822fd96ccebfafff6c715546` | 4 nodi S-C: strikethrough, wash+spinner, LOW+held, checkmark |
| `review-assets/mac-renderer-voiceover.png` | `e6eca383308c554f2c322bdd923980d0b81ab8cfa6cf4285feaab236521dc7be` | stessi nodi + contentDescription sotto ogni riga |
| `review-assets/mac-renderer-reduce-motion.png` | `f27f961dca1770124453f9696dada85e99a74440822fd96ccebfafff6c715546` | identico al normale (motion=0, resa completa) |
| `review-assets/mac-renderer-high-contrast.png` | `63b833800c293eadadc1262ddd1a0b0399976a6745f75457917305e5bce26620` | hatch sul volo; hotel bordo nero doppio |

---

## Gradle (output reale)

`:renderers:mac-compose:test` — 7 test, 0 failed (MR-001..007). Compose Multiplatform **1.9.3** desktop, Kotlin 2.2.10. Dipendenza dichiarata: `compose.desktop.currentOs` (`org.jetbrains.compose.desktop`).

Tag **locale** a verde: `renderer-mac-v0.1`. `renderer-android-v0.10` / `a3ui-core-v0.5` / `launcher-v0.6` non bumpati. Niente push.

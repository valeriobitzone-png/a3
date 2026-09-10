# REVIEW_LAUNCHER_PROOF

AUDIT-FIRST. Protocol: FASE LAUNCHER-PROOF (fixture incerta + prova fisica). Baseline `0b7ee2d` (`renderer-android-v0.9` chiuso). Core / admission / action / json / prediction / projection / adapters / intent-model / broker / **`:a3ui`** / **`:renderers:android-compose`** / **`:renderers:android-core`** **frozen**. Niente push.

**Unfrozen:** `:launcher` only.  
**Tesi:** stato epistemico ↔ esperienza verificabile. L'asse esiste nel catalogo (S-A); il renderer lo dipinge (S-B); il launcher lo compone e lo gira sul device.

---

## Device

| Campo | Valore |
|------|--------|
| Modello | Nothing Phone (3) (`ro.product.brand_device_name`) |
| SKU | A024 / `MetroidEEA` |
| **Seriale** | **`<redacted-device-id>`** |
| OS | Nothing OS 5.0 (`ro.nothing.version.id`) |
| Android | 17 (API 37) — stock, moderno; Tab A9 resta riferimento storico |

APK: `launcher/build/outputs/apk/debug/launcher-debug.apk` installato con `adb -s <redacted-device-id> install -r`.

---

## Freeze (LP-006)

```
git diff --stat -- core/ core/json core/admission core/action prediction/ projection/ adapters/ intent-model/ broker/ a3ui/ renderers/
```

vuoto. Solo `:launcher` + README + REVIEW + `review-assets/launcher-proof-*`.

```
./gradlew :launcher:testDebugUnitTest --offline
./gradlew :launcher:assembleDebug --offline
```

Niente aggregato, niente `--rerun-tasks`.

---

## Tabella audit — LP-001..006

| Test | Invariante | Percorso | Negativo | PASS/GAP |
|------|------------|----------|----------|----------|
| LP-001 | 4 nodi, asse corretto | `A3HostViewModel.uncertain()` → `text_cal.{meeting,flight,hotel,dinner}`; digest 2/3/4 ≠ nodo 1 | 4 stati non rappresentati; hash identici | **PASS** |
| LP-002 | composizione | `SurfaceComposer.compose` + `PresentationHash.of(tree)` = hash sul ViewModel; `RenderedOutput.stateDescription` non vuota | hash assente; output senza asse | **PASS** |
| LP-003 | TalkBack mock | `contentDescription` contiene copy + `stateDescription` (e frasi protocollo su hotel/dinner) | nodo non-default muto | **PASS** |
| LP-004 | reduced-motion | `Exposure.motionMs(*, true)=0`; `stateDescription` identica; mark visibili, nessun `-motion-*` | motion come unica resa | **PASS** |
| LP-005 | prova fisica | APK su Nothing Phone 3; 4 PNG in `review-assets/` | fixture senza i 4 stati | **PASS** |
| LP-006 | freeze | diff frozen vuoto | tocco a3ui/renderers | **PASS** |

**GAP: nessuno.**

---

## Fixture (dati, meaning chiusi)

Quattro atomi `price` (meaning G2 già ammesso — non un ruolo nuovo). Copy da calendario personale; l'asse arriva da `EpistemicFacts` già derivato in `:a3ui` (congelato).

| Nodo | Copy | conf | Asse derivato | Resa sul device |
|------|------|------|---------------|----------------|
| `text_cal.meeting` | Riunione 09:00 — Ada | 1.0 | HIGH / FRESH / BELIEVED / **DONE** | checkmark `✓` |
| `text_cal.flight` | Volo 14:30 — FCO→LIN | 0.7 | **MEDIUM** / **AGING** / BELIEVED / **PENDING** | opacità 75%, hatch `/`, spinner |
| `text_cal.hotel` | Hotel Milano | 0.4 | **LOW** / **STALE** / **HELD** / action **UNKNOWN** | opacità 50%, bordo ambra, `\|\|`, `[held]`, `[unknown]` |
| `text_cal.dinner` | Prenotazione ristorante | 0.9 | MEDIUM / FRESH / **CONTRADICTED** / **COMPENSATED** | strikethrough + `contradicted` / `compensated` |

Ordine visivo = ordine canonico delle chiavi (`dinner`, `flight`, `hotel`, `meeting`).

Il renderer congelato mappa `uncertain` + contorno tratteggiato su **support UNKNOWN** (conf 0). Il protocollo chiede conf 0.4 sul nodo 3 → **LOW** (opacità 50%) + action UNKNOWN. Non si può avere insieme tratteggio e LOW senza riaprire compose. Sul device: 50% + ambra + `[held]` + `[unknown]`; `contentDescription` include `stale pending review unknown`.

---

## presentationHash campione (LP-002)

```
4159f43f6537a84eea0ea920983a974d97c4ca6805df3efb9607e89302ee5dde
```

`PresentationHash.of(SurfaceComposer.compose(uncertainCalendar, asOf, facts))` == `HostUi.presentationHash`. Clock-free (motion fuori).

---

## stateDescription campione

**Hotel (nodo 3), campo a3ui:**

```
support low freshness stale status held action unknown
```

**contentDescription (albero accessibilità device, TalkBack):**

```
Hotel Milano support low freshness stale status held action unknown stale pending review unknown
Prenotazione ristorante support medium status contradicted action compensated contradicted compensated
Volo 14:30 — FCO→LIN support medium freshness aging action pending
Riunione 09:00 — Ada action done
```

Dump: `review-assets/launcher-proof-talkback-tree.xml`.

---

## Screenshot (LP-005)

| Path | Cosa prova |
|------|------------|
| `review-assets/launcher-proof-normal.png` | Tutti e 4 i nodi con le rese dichiarate |
| `review-assets/launcher-proof-talkback.png` | TalkBack attivo (toast *TalkBack attivato* + focus verde su dinner); i 4 nodi restano dipinti |
| `review-assets/launcher-proof-reduced-motion.png` | `a3_reduced_motion=true` + scale animazione 0; resa completa, nessun verbo |
| `review-assets/launcher-proof-high-contrast.png` | hatch su Volo (AGING); Hotel con doppio bordo nero (non solo ambra); badge/pattern |

SHA-256:

```
93f2decd248666b4aa84554e070b4956f15a7e063edd346a56ba0adc6a4e7cd2  launcher-proof-normal.png
45f216f2165ac7a1e3d06eccbd41b899f0f742c8899315aa9dc4c0d381204731  launcher-proof-talkback.png
0325784f9b76f33380df8aae7450ee37de2be433547c2cbe86cfcc19c29092ea  launcher-proof-reduced-motion.png
06a12f1d29d0669bdb2c23c014e93e2b66d7467bed077f10630df28dc2e7c867  launcher-proof-high-contrast.png
```

---

## Gradle (output reale)

`:launcher:testDebugUnitTest` — 26 test, 0 failed (LP-001..004, LP-006 + suite esistente).  
`:launcher:assembleDebug` — SUCCESS. Install `Success` su `<redacted-device-id>`.

Tag **locale** a verde: `launcher-v0.5`. `a3ui-core-v0.5` / `renderer-android-v0.9` non bumpati. Niente push.

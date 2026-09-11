# REVIEW_SHOWCASE_V3

AUDIT-FIRST. Protocol: FASE SHOWCASE-V3 — scena epistemica completa, silenzio > materia. Scongelati: `:showcase`, `:showcase:mac`. Frozen: `core/`, `a3ui/`, `broker/`, `agent/`, `launcher/`, `adapters/`, `prediction/`, `projection/`, `intent-model/`, `renderers/`. Niente push.

**Contratto:** una scena navigabile per l'intent *Appuntamento a Milano alle 9 da Roma*. Tre surface simultanee (PENDING / STALE / CONTRADICTED), provenance visibile, età continua, azioni vietate con ragione, chrome ambient che si espande. I mark portano il significato. Il vetro resta chrome dell'host, non una claim.

---

## Freeze (SV3-012)

```
git diff --stat -- core/ a3ui/ broker/ agent/ launcher/ adapters/ prediction/ projection/ intent-model/ renderers/
```

vuoto. Solo `:showcase` / `:showcase:mac` + questo REVIEW + `review-assets/showcase-v3/`.

Gallery v0.2 (`review-assets/showcase/`) non è stata riscritta.

---

## Scena

ALL non monta più il catalogo renderer. Il renderer è frozen, quindi PENDING outline, età, provenance e conferma disabilitata vivono in `ShowcaseScene` / `ShowcaseScenePane` (modulo showcase). GLASS / AXIS / MOTION / DYNAMIC / SHADERS / SENSORY restano sul catalogo `ComposeRenderer` / `MacRenderer` + `CalendarFixture`, così SC-001..008 e SV-001..006 restano verdi.

| Surface | Stato | Cosa si legge senza audio |
|---------|-------|---------------------------|
| Treno | PENDING | slot riservato, outline tratteggiato, label `in verifica`, stesso volume delle altre card, nessun dato, nessuno spinner |
| Hotel | STALE | prezzo `€89`, bordo ambra, badge `stale 2h`, età `2h fa`, barra di decay (orizzonte 8h) |
| Calendario | CONTRADICTED | `9:00` e `9:30` con strikethrough tenue, badge `contradicted`, `conferma` disabilitata, ragione `contradicted: resolve conflict first` |

Provenance su ogni surface: diamante `modello` (treno), quadrato `API firmata` (hotel), cerchio tratteggiato `inferita` (calendario). Età continua: `ora` / `2h fa` / `30m fa`.

Chrome ambient sta nella riga dell'intent (non sopra il PENDING). Collassato: `09:00`. Espanso: `scade 09:00`.

---

## Adozioni

Nessuna claim nuova di vetro o materia. `ShowcaseGlassPlate` frost è host chrome; spegnere blur (`toggle-blur` / `--blur-off` / `a3_glass false`) toglie il frost e lascia i mark. Audio e haptic sono opzionali: `silent` azzera l'emissione, i mark restano.

Vietato rispettato: nessuno spinner PENDING, nessuna opacità al posto del mark, nessuna preview senza stato, nessuna conferma senza ragione visibile, niente push.

---

## Si capisce in silenzio

Verifica: si capisce in silenzio. I mark restano quando motion, blur e audio sono spenti.

| Condizione | File | PENDING | STALE | CONTRADICTED | Provenance |
|------------|------|---------|-------|--------------|------------|
| scena completa, ambient espanso | `scena-completa.png` | outline + `in verifica`, slot vuoto | `€89` + `stale 2h` + `2h fa` | strikethrough + ragione | icona + label |
| reduced motion ON | `reduced-motion.png` | invariato | invariato | invariato | invariato |
| blur OFF (glass disabilitato) | `blur-off.png` | invariato | invariato | invariato | invariato |
| provenance / default | `provenance.png` | invariato | invariato | invariato | tre fonti distinte |

Silenzio (audio/haptic OFF): SV3-008 — journal `emitted: false`, stessi tag visibili. Non serve tono o vibrazione per leggere la scena.

---

## Tabella audit — SV3-001..012

| Test | Invariante | Percorso | PASS |
|------|------------|----------|------|
| SV3-001 | tre surface simultanee, stati diversi, intent visibile | tag `scene-train` / `scene-hotel` / `scene-calendar` | **PASS** |
| SV3-002 | PENDING: outline + `in verifica`, no spinner, volume uguale | tag outline/label, altezze ±4px, source senza `CircularProgressIndicator` | **PASS** |
| SV3-003 | STALE: età visibile, prezzo, badge, decay ∈ (0,1) | `€89`, `2h fa`, `stale 2h`, `scene-hotel-decay` | **PASS** |
| SV3-004 | CONTRADICTED: strikethrough, badge, conferma disabilitata + ragione | `TextDecoration.LineThrough`, `assertIsNotEnabled`, `contradicted: resolve conflict first` | **PASS** |
| SV3-005 | provenance su ogni surface (icona + label) | API / modello / inferita | **PASS** |
| SV3-006 | reduced motion ON → mark restano | host `reduced=true` | **PASS** |
| SV3-007 | blur OFF → mark restano | tag `scene-blur-off`, poi frost-on al toggle | **PASS** |
| SV3-008 | silenzio → comprensione invariata, emit spento | `silent=true`, audio/haptic `!emitted` | **PASS** |
| SV3-009 | chrome ambient espanso, deadline visibile, non copre PENDING | `scade 09:00`, bounds ambient sopra il label treno | **PASS** |
| SV3-010 | azione vietata visibile (non solo tasto grigio) | ragione accanto a conferma disabilitata | **PASS** |
| SV3-011 | SC-001..008 + SV-001..006 restano verdi | suite `:showcase` / `:showcase:mac` + livelli catalogo | **PASS** |
| SV3-012 | freeze core/a3ui/renderers/broker/agent; solo showcase + review-assets v3 | `git diff --stat` vuoto + porcelain allowlist | **PASS** |

Gate:

```
./gradlew :showcase:testDebugUnitTest --offline
./gradlew :showcase:mac:test --offline
```

---

## Media v0.3

Phone 3 `adb screenrecord` 18.00s, 1260×2800, metadata Nothing Phone (3) / Android 17. Mac: sessione Compose 5.00s Skiko, 1024×768.

| File | SHA-1 | Note |
|------|-------|------|
| `scena-completa.png` | `ec72e90dc370d25ef4953edf1ecbc85099f6ef51` | Phone 3, ambient espanso, tre stati |
| `provenance.png` | `490cb017dc283f4640c2ef80b9ed45756d19b425` | Phone 3, tre fonti |
| `reduced-motion.png` | `9fa94878dc44fe26a6884f5bd63906ce71e37449` | Phone 3, reduced on, mark invariati |
| `blur-off.png` | `b34391904baae60895c2102920ea185311c68c9a` | Phone 3, glass off, mark invariati |
| `showcase-v3-android.mp4` | `c0921855d72d86761511f9dce1b566af32959c03` | tour ambient / reduced / blur-off+silent |
| `showcase-v3-mac.mp4` | `b54044d2a2fa9d17b2b7464780984c9fa9161b63` | stessa scena Compose |
| `mac/scena-completa.png` | `24a936fa33cfd148d92c034778c9be1d7f20b6c9` | Compose Desktop |
| `mac/provenance.png` | `98266994a6885cd9ffd3c6209d81ea6d098c58e9` | |
| `mac/reduced-motion.png` | `54516ee9a6d3d4c9e33467fb82e2772c9d24442d` | |
| `mac/blur-off.png` | `983c876efce3805f3833d6103dcafcdcb83dc919` | |

---

## Tag

`showcase-v0.3` solo a gate verde. Niente push.

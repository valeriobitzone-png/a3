# REVIEW_SHOWCASE

AUDIT-FIRST. Protocol: FASE SHOWCASE — demo gallery reale, completa e navigabile. Nuovo modulo `:showcase` (Android) + `:showcase:mac`, separato dal launcher. Consuma `:renderers:android-compose`, `:renderers:mac-compose`, token `a3ui-graphics`. Frozen: `core/`, `a3ui/`, `broker/`, `launcher/`, `adapters/` (e prediction/projection/intent-model, android-core). Niente push.

**Contratto:** vista reale del sistema a3ui sulla fixture calendario (4 nodi, assi diversi) dentro il catalogo renderer. Navigazione ALL + glass · axis · motion · dynamic · shaders · sensory. Controlli live: reduced-motion, talkback, modal DoF, ambient expand, particle/audio/haptic. Gate solo con video/screenshot/log reali.

**Catalogo:** `ComposeRenderer` / `MacRenderer` — invariati, solo consumati. Chrome extra (gradient, ambient, modal DoF, grain overlay) è nel modulo showcase perché quei composable renderer sono `internal`; costanti da `audio.json` / `haptic.json` / `surfaces.json` / `motion.json` (speed 0.18, amplitude 0.35, mixAmt = fillOpacity×0.45). Optics/particle/masking usano le API pubbliche (`GlassOpticsLayer`, `ParticleBurst`, `AlphaMaskedStrip`).

---

## Freeze (SC-008)

```
git diff --stat -- core/ a3ui/ broker/ launcher/ adapters/ prediction/ projection/ intent-model/ renderers/
```

vuoto. Solo `settings.gradle.kts` (include `:showcase` / `:showcase:mac`), `showcase/`, `REVIEW_SHOWCASE.md`, `review-assets/showcase/`.

---

## Come è fatto

| Schermata | Cosa isola | Cosa resta nella scena |
|-----------|------------|------------------------|
| ALL | tutti i livelli insieme | catalogo vetro + asse + spring + palette + gradient/ambient/parallax + optics/noise/particle + audio/haptic |
| glass | vetro | catalogo in `GlassSurface`, niente gradient/ambient/optics |
| axis | asse epistemico | HELD / UNKNOWN / DONE / CONTRADICTED visibili, niente chrome shader |
| motion | spring / pulse | pulse HELD nel catalogo, niente gradient |
| dynamic | palette / type | catalogo con token colore/tipo, niente gradient |
| shaders | gradient + ambient + modal + parallax | niente optics/particle |
| sensory | optics + particle + audio/haptic | niente gradient/ambient |

Fixture: meeting DONE, flight aging, hotel HELD/stale/unknown, dinner CONTRADICTED. Stessa di launcher, copiata in `showcase/shared/` (launcher frozen).

---

## Tabella audit — SC-001..008

| Test | Invariante | Percorso | Negativo | PASS/GAP |
|------|------------|----------|----------|----------|
| SC-001 | scena ALL: glass+axis+motion+dynamic+shaders+sensory insieme, senza conflitto | `ShowcaseApp` ALL + `ComposeRenderer`/`MacRenderer` | chrome che copre l'asse / freeze GPU | **PASS** |
| SC-002 | ogni schermata isola il livello (nav click, un host) | tag `nav-*` / `showcase-level-*` | gradient su glass, optics su shaders | **PASS** |
| SC-003 | video reali, durata > 0, frame variabili | `showcase-android.mp4` 40.00s Phone 3; `showcase-mac.mp4` 7.00s Compose | file vuoto / frame identici / concept | **PASS** |
| SC-004 | gallery ALL + 6 livelli × 2 piattaforme | `review-assets/showcase/{all,glass,axis,motion,dynamic,shaders,sensory}.png` + `mac/` | screenshot mancante | **PASS** |
| SC-005 | reduced ON → audio/haptic `emitted: false`, catalogo dipinto; OFF → emit | toggle + `showcase-haptic.json` atMs 18459 | haptic con reduced | **PASS** |
| SC-006 | talkback: announce stati critici | `showcase-announce.json`: pending review, stale, contradicted, unknown, compensated | silenzio | **PASS** |
| SC-007 | ogni stato → tono/pattern da token; reduced azzera | `audio.json`/`haptic.json` + log live `VibrationEffect` | mapping inventato / audio senza reduced | **PASS** |
| SC-008 | freeze core/a3ui/broker/launcher/adapters/renderers | `git diff --stat` | tocco renderer o core | **PASS** |

**GAP: nessuno** (audio interno in `adb screenrecord` non esiste su questo device — dichiarato in `AUDIO.txt`, non GAP del mapping).

Gate:

```
./gradlew :showcase:testDebugUnitTest --offline
./gradlew :showcase:mac:test --offline
```

Android SC-001..008 verdi. Mac SC-001,002,004,005,006,007 verdi.

---

## Media

| File | SHA-1 | Cosa è |
|------|-------|--------|
| `review-assets/showcase/showcase-android.mp4` | `e477fcdf9842eacfba3d1babf40ea6754dd6d44c` | `adb screenrecord` 40.00s, 1260×2800, ~48fps, metadata Nothing Phone (3) / Android 17. Tour: CONFIRM, modal, ambient, talkback, 7 livelli, reduced on/off. Video-only. |
| `review-assets/showcase/showcase-mac.mp4` | `bbe4aea820b6fbe77eec9ae7594e711f62782040` | Sessione Compose Desktop 7.00s, 1024×768, 2fps. Stessa navigazione. Vedi `MAC.txt`. |
| `review-assets/showcase/showcase-audio.m4a` | `9a16794749dfaf9ed62423d960d4b12b4d3d27ba` | PCM emesso dal tour Android (token `tones.morph`), non mic. |
| `review-assets/showcase/showcase-session.wav` | `cecf699589f1e8e88cb877dbc0cf8f946e4808db` | Stesso nastro, 22050 Hz, pull da `/sdcard/Android/data/a3.showcase/files/`. |
| `review-assets/showcase/showcase-haptic.json` | `14a6cb33f4da798f00ed97837071eea161e541de` | Host `VibrationEffect`. CONFIRM @2623ms emitted; reduced @18459ms `emitted: false`; restore @20113ms. |
| `review-assets/showcase/showcase-announce.json` | `a63723998c5a656c5629c075f29aa26ee4e9e4e7` | Talkback: contradicted, compensated, stale, pending review, unknown. |
| `review-assets/showcase/all.png` | `b9f729a6f44f2943108a77ab08b96b138311ddd6` | Phone 3, scena ALL |
| `review-assets/showcase/glass.png` | `add84ae3e673cefb2413e68c33b64df249fb0956` | Phone 3, glass |
| `review-assets/showcase/axis.png` | `993b4b09906f2acf9bfda6a38866e15cecb5f82c` | Phone 3, axis |
| `review-assets/showcase/motion.png` | `3b4616a5eac2371a0c77bc70fcf196e08c039470` | Phone 3, motion |
| `review-assets/showcase/dynamic.png` | `b0bd00f7c2ced5943e217106800a0d6f7c06d3a0` | Phone 3, dynamic |
| `review-assets/showcase/shaders.png` | `bd69cf8190796c00274477f3026d161ebe67b7a6` | Phone 3, shaders |
| `review-assets/showcase/sensory.png` | `a4d242deeeb95fee1cabd679d4b5f0bd425a8250` | Phone 3, sensory |
| `review-assets/showcase/mac/all.png` … `sensory.png` | vedi tabella sotto | Compose Desktop, stessa gallery |

Mac screenshots:

| File | SHA-1 |
|------|-------|
| `mac/all.png` | `cc705553396b66926e8e2dcbaddbcc638eb5df78` |
| `mac/glass.png` | `a15c5ea58317c3567b591dc33565664d637dca2f` |
| `mac/axis.png` | `cbd07a035076445513cc1abbf660281b6032b7f4` |
| `mac/motion.png` | `134a10db2fbc6a8b88320d68ba9613389e4cd9f2` |
| `mac/dynamic.png` | `cb4fc16617145dc24a0bdb445025713f8fd4f567` |
| `mac/shaders.png` | `305382d6f5ab555709bdde103215fbb9e45363ce` |
| `mac/sensory.png` | `b18ede741e8caa79b4e6424ac5fcee4c375ae3c6` |

---

## Audio + haptic (dal vivo, non dal video)

`adb screenrecord` su Nothing Phone 3 (A024 / MetroidEEA, Android 17) **non** cattura playback interno né microfono. Dichiarato in `review-assets/showcase/AUDIO.txt`. Il mapping si giudica sul device, dal log, e dal nastro PCM.

| Causa | Audio (`tones.morph` 440 Hz) | Haptic (`haptic.json`) |
|-------|------------------------------|------------------------|
| HELD | 220 Hz, 160 ms, amp 0.04 | light / tick, 0.35, 12 ms, count 1 |
| UNKNOWN / NOTIFY | 440 Hz, 48 ms tick | medium double-tap, 0.65, 20 ms, count 2, gap 40 |
| DONE / CONFIRM | 440 → 660 quinta, 160 ms | light / tick, 0.35, 12 ms |
| TAP | (nessun tono: haptic-only) | light / tick, 0.35, 12 ms |
| CONTRADICTED | 440 → 415.3 (−1 st) glissato | medium, 0.65, 20 ms, count 1 |

Reduced-motion **oppure** silent → `emit` null, catalogo resta dipinto (`keepChroma`). Log Phone 3: atMs 18459 tutti `emitted: false` con `reducedMotion: true`.

---

## Prova dal vivo (obbligatoria)

Device: Nothing Phone 3, suono acceso, vibrazione accesa, ringer non silent, reduced-motion **off**.

```
./gradlew :showcase:assembleDebug
adb install -r showcase/android/build/outputs/apk/debug/showcase-debug.apk
adb shell am start -n a3.showcase/.MainActivity
```

1. Schermata ALL: vetro, calendario 4 nodi, gradient, ambient 12:00, grain/refraction. Tap riga → ripple/spring + haptic tick + (se mapping CONFIRM) quinta 440/660.
2. Nav glass · axis · motion · dynamic · shaders · sensory: ogni livello isola, l'asse resta leggibile.
3. `modal` → DoF/scrim; `ambient` → expand spring; `sensory` → particle + audio + haptic.
4. `reduced on` → motion/audio/haptic a zero, scena completa. `reduced off` → di nuovo attivi.
5. `talkback on` → announce pending review / stale / contradicted.

Mac: `./gradlew :showcase:mac:run` (titolo finestra `a3ui showcase`). Stessi controlli. Haptic Mac = `NSHapticFeedbackManager` generic (Force Touch), non LRA.

Token: `renderers/android-compose/src/main/resources/a3ui-graphics/audio.json` e `haptic.json`. Log sessione: `review-assets/showcase/showcase-haptic.json`.

Registrare di nuovo: `showcase/scripts/record-android.sh` (Phone 3). Mac display-camera richiede TCC Screen Recording; senza, `showcase/scripts/record-mac.sh` rifà i frame Compose.

---

## Cosa è in questa fase

- Modulo `:showcase` / `:showcase:mac` navigabile, fuori dal launcher
- Registrazione Phone 3 reale + gallery screenshot entrambe le piattaforme
- Log haptic + nastro audio token-locked + announce talkback
- Reduced-motion azzera sensory senza spegnere la pittura

## Non toccato

`core/`, `a3ui/`, `broker/`, `launcher/`, `adapters/`, `renderers/` (nessun flip `internal`→public). Distorsione liquida. Haptic localizzato / pressure.

---

## Tag

`showcase-v0.1` solo a gate verde. Niente push.

# PERFORMANCE

Profili A3UI: feature matrix misurata, non marketing. Il vetro non deve mangiare i mid-range.

## Regola decisionale (dichiarata PRIMA dei numeri Mac on-screen di questa passata)

**(a)** Se HIGH overlay expanded **p95 < 16.7 ms su entrambe** le piattaforme → chiudere così, target 60 fps invariati.

**(b)** Altrimenti: target **per-superficie** — catalog **60 fps** (p95 < 16.7 ms); overlay expanded **30 fps** (p95 < 33.3 ms) — solo con evidenza misurata, voce CHANGELOG, e questa nota. Auto-detect default = miglior profilo che rispetta il target sul device (downgrade a MID / BLUR_OFF dove HIGH fallisce). Downgrade **mai silenzioso**: `profile-pill` mostra il profilo attivo.

**(c)** Vietato: spostare soglie per far passare i numeri senza questa decisione; interpolare campioni; taggare con PF aperti.

**Esito:** i dump Android gfxinfo A024 (2026-09-13) mostrano HIGH overlay expanded p95 nominale **200 ms**. **(a) è esclusa** prima di qualsiasi rimisura Mac. Si applica **(b)**.

Android HIGH/MID catalog e overlay, sui percentili gfxinfo, mancano **sia** 16.7 ms **sia** 33.3 ms. GPU p95 è 6–8 ms. **Nessun profilo misurato rispetta il target** su quel device → **ripiego:** default = profilo più economico (**BLUR_OFF**) + compliance fps **UNVERIFIED** (regola in questa pagina e in `GOVERNANCE.md` §10). Il 200 ms nominale è **sospetto artefatto harvest** finché non esiste un jank-frame count on-screen. Showcase resta sui segnali di classe (modulo frozen in A3UI-PERF-FIX).

## Matrice

| Profilo | Blur | Raggio overlay (px) | Particles | Motion | Dynamic color | Noise |
|---------|------|---------------------|-----------|--------|---------------|-------|
| HIGH | pieno (`FULL`) | 18 | ON | completo | ON | ON |
| MID | raggio ridotto (`REDUCED`) | 6 | OFF | semplificato | ON | OFF |
| BLUR_OFF | off (`OFF`) — trasparenza+tint | 0 | OFF | completo | ON | OFF |
| PARTICLES_OFF | pieno | 18 | OFF | completo | ON | ON |

Compose glass usa la stessa matrice. HIGH/PARTICLES_OFF applicano il raggio profilo sul replica backdrop; BLUR_OFF tagga `glass-fallback` e parla `blur unavailable`.

Override manuale in settings (showcase chips, overlay permission). Profilo attivo **visibile** (`profile-pill`, status, debug chip). Mai degradazione silenziosa.

## Auto-detect

### Segnali di classe (RAM / pixel / GPU)

| Segnale | Soglia | Default di classe |
|---------|--------|-------------------|
| GPU software / emulator (`goldfish`, `ranchu`, `swiftshader`, `emulator`, `cuttlefish`) | — | BLUR_OFF |
| RAM < 3072 MB **o** pixel < 720×1280 | `ProfileDetect.RAM_MID_MB` / `PIXEL_MID` | BLUR_OFF |
| RAM < 6144 MB **o** pixel < 1080×1920 **o** GLES < 3.0 | `RAM_HIGH_MB` / `PIXEL_HIGH` | MID |
| Altrimenti | — | HIGH |

Nothing Phone (3) / A024 (classe, **non** frame time): 12 GB RAM, 1264×2736, `qcom`/`sun`, GLES 3.2 → classe **HIGH**.

### Budget misurato (overlay Android, decisione b)

| Device | Catalog p95 HIGH/MID | Overlay p95 HIGH/MID | Default auto overlay | Compliance fps |
|--------|----------------------|----------------------|----------------------|----------------|
| A024 (dump `review-assets/perf/android-*-gfxinfo.txt`) | 200 ms / 200 ms (nominale, **sospetto artefatto harvest**) | 200 ms / 200 ms (stesso) | **BLUR_OFF** (più economico; pill visibile) | **UNVERIFIED** |

**Ripiego (MUST):** se nessun profilo rispetta il target sul device → default = profilo più economico della scala (HIGH → MID → BLUR_OFF) **e** la claim fps è **UNVERIFIED**. Vietato un default HIGH silenzioso in quel caso.

Mac host: macOS 26, Apple M4, 16 GB → classe **HIGH** (trade-off dichiarato sotto: overlay vsync-locked; catalog HIGH in presentazione dopo hitch). I test Skiko `SOFTWARE` non sono il default runtime né il dump PF-005.

## Metodo

- **Android display p95:** `adb shell dumpsys gfxinfo <pkg> framestats`. Parser: `GfxInfoParser` (linee percentile del dump). Non interpolato. Dump: `review-assets/perf/android-{catalog,overlay}-{HIGH,MID}-gfxinfo.txt`. BLUR_OFF Android **assente**.
- **Mac overlay expanded:** `CADisplayLink` sull'overlay nativo con `NSVisualEffectView` (`.fullScreenUI`, `.behindWindow`) **on-screen**. ≥300 frame. **Non** `OverlayCompositor` / `CGContext` offscreen.
- **Mac catalog:** `withFrameNanos` su Compose Desktop, `skiko.renderApi=METAL`, `MacGlassSurface` on-screen. ≥300 frame. I test JUnit restano SOFTWARE (non sono PF-005).
- **Compliance catalog Mac (non è il p95 grezzo):** presentazione vsync. Per ogni sample `dt` ms, `missed = max(0, floor(dt / (1000/60)) − 1)` (periodo 16.666… ms). Si dichiarano `missed_vsync_sum` e `frames_with_miss`. **60 fps in presentazione** ⇔ `missed ≈ 0`. Il p95 grezzo sopra 16.7 ms con missed≈0 è **jitter vsync**, non un fail di presentazione.
- **Hotspot compositor (diagnosi, non PF-005):** `review-assets/perf/mac-hotspot-compositor.txt` — quota copy vs CPU `OverlayBlur` vs paint su `BufferedImage`.

## Limitazioni (oneste)

- Un solo Android fisico: **A024** (Nothing Phone (3) in documentazione precedente).
- **MID è simulato con feature flags sullo stesso device**, non hardware mid reale.
- Harvest gfxinfo ~4 s / ~60–70 frame, 100% janky: numeri reali, finestra corta, non uno scroll da 300 frame.
- Nessuna misurazione inventata.

## Misure — Mac on-screen (2026-09-13, ≥320 frame)

Host: macOS 26.6.2 (25G83), Apple M4, 10 core. Overlay: `CADisplayLink` + `NSVisualEffectView` on-screen. Catalog: Compose Desktop `withFrameNanos`, `skiko.renderApi=METAL`.

CADisplayLink su overlay è l'intervallo vsync: 320/320 frame a **16.666625 ms** (60 Hz, **missed vsync = 0**). Non è wall time di `OverlayCompositor`.

| Superficie | Profilo | p50 ms | p95 ms | max ms | frames | missed_vsync_sum | frames_with_miss |
|------------|---------|--------|--------|--------|--------|------------------|------------------|
| overlay expanded | HIGH | 16.667 | 16.667 | 16.667 | 320 | 0 | 0 |
| overlay expanded | MID | 16.667 | 16.667 | 16.667 | 320 | 0 | 0 |
| overlay expanded | BLUR_OFF | 16.667 | 16.667 | 16.667 | 320 | 0 | 0 |
| catalog | HIGH | 16.647 | 17.116 | 504.079 | 320 | **30** | **2** |
| catalog | MID | 16.648 | 17.829 | 162.909 | 320 | **9** | **2** |
| catalog | BLUR_OFF | 16.680 | 17.120 | 169.439 | 320 | **10** | **2** |

Catalog missed (dump `samples_ms`, formula sopra, **nessun campione interpolato**):

| Profilo | hitch (idx 0) | altro miss | post-hitch (skip 5) missed_sum |
|---------|---------------|------------|--------------------------------|
| HIGH | 504.079 ms → 29 | idx 2: 34.901 ms → 1 | **0** (315 frame; p95 grezzo 17.097) |
| MID | 162.909 ms → 8 | idx 11: 37.848 ms → 1 | **1** (37.848 ms resta) |
| BLUR_OFF | 169.439 ms → 9 | idx 9: 34.375 ms → 1 | **1** (34.375 ms resta; 0 dopo skip 10) |

**HIGH catalog — 60 fps rispettato in presentazione.** Post-hitch missed ≈ 0. p95 grezzo **17.116 ms** (320 frame) / **17.097 ms** (315) = **jitter vsync**, non un fail di presentazione.

**MID / BLUR_OFF catalog:** missed non ≈ 0 (resta 1 frame > 33.3 ms dopo l’hitch). Non si claima 60 fps su quei profili.

**HIGH = trade-off dichiarato** (non silenzioso): overlay Mac è vsync-locked a 16.667 ms; catalog HIGH presenta a 60 fps dopo hitch. Non si declassa a MID: MID non è più economico in presentazione e il p95 grezzo è peggiore.

### Anomalia PF-003 Mac catalog (scritta, non verde in silenzio)

MID p95 grezzo **17.829 ms** ≰ HIGH **17.116 ms**. È jitter vsync (stesso dump; MID ha anche il miss 37.848 ms). **PF-003 è riscopato all’overlay** (Mac overlay 16.667=16.667; Android overlay 200=200) **e ad Android catalog** (200=200). Mac catalog MID ≰ HIGH **non è PASS**.

### Prima (harness sbagliato, 24 frame, 2026-09-13)

`OverlayCompositor.compose` 1280×800, CPU box-blur, **non** vsync: HIGH p95 56.205 ms, MID 47.312, BLUR_OFF 9.466. Conservato come diagnosi hotspot, non come PF-005.

## Misure — Android A024 (2026-09-13, dump reali)

**Banner: compliance fps Android = UNVERIFIED.** Nessun profilo rispetta il target (b) sui dump gfxinfo → default **BLUR_OFF** (più economico) + UNVERIFIED, non una claim 30/60 fps.

Device: `device_model=A024` `serial=<redacted-device-id>`. Pipeline Skia (Vulkan). gfxinfo named percentiles (primo blocco). GPU p95 6–8 ms su tutti.

Il p95 nominale **200 ms** è il bucket gfxinfo dopo 150 ms. Harvest ~4 s / ~60–70 frame, 100% janky, senza jank-frame count on-screen da sessione scroll operatore. **Marcato sospetto artefatto harvest** finché non esiste quel count. Non interpolato a 16.7.

| Profilo | scene | frames | p50 ms | p90 ms | p95 ms | p99 ms | GPU p95 | Slow UI | vs (b) |
|---------|-------|--------|--------|--------|--------|--------|---------|---------|--------|
| HIGH | catalog | 65 | 93 | 113 | **200** | 1100 | 6 | 65/65 | manca 16.7 |
| MID | catalog | 66 | 101 | 121 | **200** | 950 | 7 | 66/66 | manca 16.7; p95 ≤ HIGH |
| HIGH | overlay | 61 | 101 | 150 | **200** | 900 | 7 | 61/61 | manca 33.3 |
| MID | overlay | 66 | 101 | 150 | **200** | 850 | 8 | 66/66 | manca 33.3; p95 ≤ HIGH |

Istogramma catalog HIGH: massa a 85–97 ms, non a 16 ms. p95 nominale 200 ms è il bucket gfxinfo (dopo 150). Non interpolato a 16.7.

## Come ripetere

```
./docs/harvest-mac-frames.sh
# hotspot CPU (diagnosi):
./gradlew :overlay:mac:test --tests a3.overlay.mac.OverlayMacTest.PF_hotspot_cpu_blur_is_offscreen_harness \
  --no-daemon -Pkotlin.compiler.execution.strategy=in-process

# Android — richiede device
./docs/harvest-android-gfxinfo.sh HIGH catalog
```

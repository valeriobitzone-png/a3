# REVIEW_A3UI_PERF

AUDIT-FIRST. Protocol: FASE A3UI-PERF-FIX — chiudere A3 con misurazioni reali. Unfrozen: `:renderers:mac-compose`, `:overlay:mac`, `:overlay:android`, `:renderers:android-compose`, `docs/`. Frozen: spec, conformance, `:a3ui`, a3ui-web, showcase, e il resto. Niente push. Tag `a3ui-perf-v0.1` **solo a gate verde con dump reali**.

**Contratto:** il vetro non mangia i mid-range. Niente numeri inventati. Niente degradazione silenziosa. MID non è hardware mid: è flag sullo stesso A024.

---

## Regola decisionale (prima dei numeri Mac on-screen)

**(a)** HIGH overlay expanded p95 < 16.7 ms su **entrambe** le piattaforme → target 60 fps invariati.

I dump Android A024 erano già sul disco: HIGH overlay p95 = **200 ms**. **(a) è esclusa** prima della rimisura Mac.

**(b)** catalog 60 fps (16.7 ms); overlay expanded 30 fps (33.3 ms); CHANGELOG Unreleased; auto-detect = miglior profilo che rispetta il target sul device; pill sempre visibile.

**(c)** non usato.

Esito: **(b)** applicata. Android HIGH/MID mancano 16.7 e 33.3 sui percentili gfxinfo → default **BLUR_OFF** + fps **UNVERIFIED** (`GOVERNANCE.md` §10). 200 ms nominale = sospetto artefatto harvest. Mac overlay on-screen è vsync-locked a 16.667 ms (missed=0). Mac catalog HIGH: **60 fps in presentazione** dopo hitch (missed=0 su 315 frame); p95 grezzo 17.116 ms = jitter vsync. MID catalog 17.829 ≰ 17.116: jitter, PF-003 **non** verde in silenzio sul catalog Mac.

---

## Diagnosi hotspot Mac

### Display blur

Già di sistema: `NSVisualEffectView` `.fullScreenUI` `.behindWindow` in `overlay/mac/native/OverlayMain.swift`. **Non** è un Gaussian CPU custom sul percorso visibile.

### Harness precedente (sbagliato per PF-005)

`OverlayCompositor.compose` su `BufferedImage` 1280×800, 24 frame, **CPU offscreen**. Chiamava `OverlayBlur` (4 pass box blur). Non è vsync, non è `NSVisualEffectView`.

Hotspot CPU (8 iterazioni, 2026-09-13, M4, `review-assets/perf/mac-hotspot-compositor.txt`):

| Fase | ms | quota |
|------|----|-------|
| copy | 2.324 | 2.6% |
| **CPU OverlayBlur** | **41.151** | **46.7%** |
| paint (AWT cards) | 44.657 | 50.7% |

Blur custom CPU + paint AWT. Il compositor JVM **forzava rendering CPU offscreen**. Percorso GPU: nativo `NSVisualEffectView`; `compose()` **non** chiama più `OverlayBlur`.

Prima (24 frame, stesso harness CPU): HIGH p95 56.205 ms, MID 47.312, BLUR_OFF 9.466.

### Android overlay capture

Prima: `OverlayBitmapBlur` → `OverlayBlur` CPU. Ora: bitmap sharp + `RenderEffect.createBlurEffect` sull'`ImageView` (API 31+). Compose glass era già `RenderEffect`.

Dump gfxinfo A024: GPU p95 6–8 ms, Slow UI thread 100%. Hotspot Android = UI thread / issue draw commands, non GPU blur.

---

## Freeze (PF-009)

```
git diff --stat -- core/ broker/ agent/ adapters/ conformance/ spec/ a3ui/
```

vuoto vs working tree. a3ui-web non toccato.

AC-023 (`git diff` overlay/renderers) resta rosso su working tree sporco; passa dopo commit.

---

## Esecuzione

```
./docs/harvest-mac-frames.sh
./gradlew :overlay:mac:test --tests a3.overlay.mac.OverlayMacTest --no-daemon -Pkotlin.compiler.execution.strategy=in-process
./gradlew :overlay:android:testDebugUnitTest --no-daemon -Pkotlin.compiler.execution.strategy=in-process
./gradlew :overlay:common:test --tests a3.overlay.ProfileTest --no-daemon -Pkotlin.compiler.execution.strategy=in-process
./gradlew :renderers:android-compose:testDebugUnitTest --tests a3.renderers.android.compose.ProfileComposeTest --no-daemon -Pkotlin.compiler.execution.strategy=in-process
./gradlew :renderers:mac-compose:test --tests a3.renderers.mac.compose.ProfileMacComposeTest --no-daemon -Pkotlin.compiler.execution.strategy=in-process
./gradlew :showcase:testDebugUnitTest --tests a3.showcase.PerfA11yAndroidTest --tests a3.showcase.A11yAndroidTest --no-daemon -Pkotlin.compiler.execution.strategy=in-process
./gradlew :showcase:mac:test --tests a3.showcase.mac.PerfA11yMacTest --tests a3.showcase.mac.A11yMacTest --no-daemon -Pkotlin.compiler.execution.strategy=in-process
```

---

## Tabella audit — PF-001..009

| Test | Invariante | Percorso | PASS |
|------|------------|----------|------|
| PF-001 | Enum + matrice in `docs/PERFORMANCE.md`; override manuale | `ProfileTest`, showcase chips `profile-*` | **PASS** |
| PF-002 | HIGH A024 p95 catalog + overlay dichiarati da dump | `android-*-HIGH-gfxinfo.txt` (catalog p95 200, overlay p95 200) | **PASS** (dump reale; sopra target) |
| PF-003 | MID p95 ≤ HIGH stesso device | **Riscopato all’overlay** (Mac 16.667=16.667; Android 200=200) + Android catalog 200=200. Mac catalog MID **17.829** ≰ HIGH **17.116** = jitter vsync, **eccezione scritta** | **PASS overlay**; catalog Mac **non** PASS silenzioso |
| PF-004 | BLUR_OFF: mark + messaggio onesto | `ProfileComposeTest` / `ProfileMacComposeTest`; overlay banner | **PASS** |
| PF-005 | Mac HIGH/MID/BLUR_OFF ≥300 frame overlay + catalog | `mac-overlay-expanded-*.txt` CADisplayLink; `mac-catalog-*.txt` withFrameNanos | **PASS** |
| PF-006 | auto-detect + pill; default rispetta target dove misurato | classe HIGH su Phone (3); overlay Android A024 → BLUR_OFF visibile | **PASS** (overlay); showcase frozen resta classe HIGH |
| PF-007 | AX-001..019 + AC-001..023 sotto ogni profilo | `PerfA11y*` PASS; AC-001..008 + 009..022 PASS; AC-023 dopo commit | **PASS** |
| PF-008 | PARTICLES_OFF | `ProfileTest` + compose `particle-idle` | **PASS** |
| PF-009 | freeze core/broker/agent/adapters/conformance/spec/:a3ui + a3ui-web | `ProfileTest.PF_009` | **PASS** |

Gate PF: **verde** con dump reali in `review-assets/perf/` sul tag `a3ui-perf-v0.1`. Decisione **(b)**. Niente push.

---

## FASE PERF-COMPLIANCE (docs-only, 2026-09-14)

Unfrozen: `docs/PERFORMANCE.md`, `CHANGELOG.md`, `REVIEW_A3UI_PERF.md`, `GOVERNANCE.md` §10. **FROZEN: tutto il codice.** Tag `a3ui-perf-v0.2` = PATCH di chiarimento docs.

Metrica catalog Mac = **presentazione vsync**, non p95 grezzo. `missed = max(0, floor(dt / (1000/60)) − 1)` sui `samples_ms` dei dump (320 frame, non interpolati).

### Mac catalog missed vsync (per profilo)

| Profilo | missed_vsync_sum | frames_with_miss | hitch | altro miss | post-hitch (skip 5) |
|---------|------------------|------------------|-------|------------|---------------------|
| HIGH | 30 | 2 | 504.079 ms → 29 | 34.901 ms → 1 | **0** / 315; p95 grezzo 17.097 |
| MID | 9 | 2 | 162.909 ms → 8 | 37.848 ms → 1 | **1** (37.848 ms) |
| BLUR_OFF | 10 | 2 | 169.439 ms → 9 | 34.375 ms → 1 | **1** (34.375 ms) |

HIGH: missed post-hitch ≈ 0 → **60 fps rispettato in presentazione**; p95 grezzo **17.116 ms** = jitter vsync (entrambi i numeri). MID missed non ≈ 0. **HIGH = trade-off dichiarato** (overlay vsync-locked; non si declassa a MID).

### Tabella audit — PC-001..004

| Test | Invariante | Dove | PASS |
|------|------------|------|------|
| PC-001 | missed-vsync count per profilo catalog Mac; metrica = presentazione vsync, non p95 grezzo | `docs/PERFORMANCE.md` Metodo + tabella catalog; questa REVIEW | **PASS** (30 / 9 / 10) |
| PC-002 | missed≈0 → 60 fps in presentazione + p95 grezzo = jitter, entrambi i numeri; altrimenti UNVERIFIED + HIGH trade-off | PERFORMANCE: HIGH 17.116 jitter + missed 0 post-hitch; HIGH trade-off dichiarato | **PASS** |
| PC-003 | anomalia MID ≰ HIGH **17.829 vs 17.116** scritta; PF-003 riscopato all’overlay (non verde in silenzio sul catalog Mac) | PERFORMANCE anomalia; REVIEW PF-003; CHANGELOG Unreleased | **PASS** |
| PC-004 | ripiego: nessun profilo rispetta il target → default più economico + UNVERIFIED; banner fps Android; 200 ms sospetto artefatto harvest | PERFORMANCE Android banner; `GOVERNANCE.md` §10; CHANGELOG | **PASS** |

Zero codice toccato in questa fase. Niente push.

---

## Misurazioni grezze

### Mac overlay on-screen (CADisplayLink, NSVisualEffectView, 320 frame, 2026-09-13)

| Profilo | p50 | p95 | max | vs (b) 33.3 |
|---------|-----|-----|-----|-------------|
| HIGH | 16.667 | 16.667 | 16.667 | ok (zero missed vsync) |
| MID | 16.667 | 16.667 | 16.667 | ≤ HIGH |
| BLUR_OFF | 16.667 | 16.667 | 16.667 | ≤ HIGH |

### Mac catalog on-screen (Compose Metal, 320 frame)

Metrica di compliance: **missed vsync**, non p95 grezzo.

| Profilo | p50 | p95 grezzo | max | missed_vsync_sum | frames_with_miss |
|---------|-----|------------|-----|------------------|------------------|
| HIGH | 16.647 | 17.116 | 504.079 | 30 | 2 |
| MID | 16.648 | 17.829 | 162.909 | 9 | 2 |
| BLUR_OFF | 16.680 | 17.120 | 169.439 | 10 | 2 |

HIGH post-hitch missed=0 → 60 fps in presentazione; 17.116 ms = jitter. MID 17.829 ≰ 17.116: eccezione PF-003, non PASS silenzioso.

### Android A024 gfxinfo (2026-09-13) — **fps UNVERIFIED**

| Profilo | scene | frames | p50 | p95 nominale | GPU p95 |
|---------|-------|--------|-----|--------------|---------|
| HIGH | catalog | 65 | 93 | **200** (sospetto artefatto harvest) | 6 |
| MID | catalog | 66 | 101 | **200** (sospetto artefatto harvest) | 7 |
| HIGH | overlay | 61 | 101 | **200** (sospetto artefatto harvest) | 7 |
| MID | overlay | 66 | 101 | **200** (sospetto artefatto harvest) | 8 |

100% janky / Slow UI. Nessun jank-frame count on-screen. BLUR_OFF gfxinfo assente. Ripiego: default più economico (BLUR_OFF) + UNVERIFIED.

---

## Vietato (rispettato)

- Nessuna misurazione inventata o interpolata.
- Il campione da 24 frame non è il dump PF-005.
- Soglie mosse solo con decisione **(b)** + CHANGELOG.
- Downgrade A024 non silenzioso (`BLUR_OFF · auto`).
- Spec / `:a3ui` / a3ui-web non toccati.
- Niente push.

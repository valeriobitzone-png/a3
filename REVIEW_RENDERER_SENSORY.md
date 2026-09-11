# REVIEW_RENDERER_SENSORY

AUDIT-FIRST. Protocol: FASE RENDERER-SENSORY (L7 audio/haptic + L1 refraction/noise + L6 particle/masking). Spec `a3ui-graphics-v0.1` (`audio.json`, `haptic.json`, `surfaces.json` glass). Scongelati: `:renderers:android-compose`, `:renderers:mac-compose`. Frozen: `core/`, `core/json/`, `core/admission/`, `core/action/`, `prediction/`, `projection/`, `:a3ui`, `:renderers:android-core`, `adapters/`, `intent-model/`, `:broker`, `:launcher`. Niente push.

**Contratto:** suoni sintetizzati dal token `tones.morph` (440 Hz, −1 semitone, 160 ms, ADSR 8/40/0.35/112); `files: false`; gain ≤ `voiceCeiling` 0.08. Haptic da `intensities.light/medium` e `patterns.tap/double-tap`; gap 40 ms. Refraction/noise opt-in sul raster (non in `GlassSurface` / `MacGlassSurface`: LG-007). Particle solo su trigger. Alpha mask a rampa vettoriale. Reduced-motion spegne audio e haptic. Silent spegne audio. Engine assente → log `haptic engine unavailable`.

**Mac haptic:** `NSHapticFeedbackManager`, pattern `generic` (trackpad Force Touch). **Non localized, non LRA completo.** `localized` e `pressure` restano deferred nel JSON.

**Differito:** distorsione liquida (`GlassOptics.LIQUID`).

Nessun asset audio binario (`.wav` / `.ogg` / `res/raw`).

---

## Freeze (SE-011)

```
git diff --stat -- core/ a3ui/ renderers/android-core/ launcher/ broker/ prediction/ projection/ adapters/ intent-model/
```

vuoto. Solo android-compose + mac-compose (+ REVIEW + review-assets).

---

## Mapping audio (da `tones.morph`)

| Causa | Gesto | Hz | Durata | Note |
|-------|-------|----|--------|------|
| HELD | pulse | 220 (ottava sotto) | 160 ms | tono tenue, amp 0.04 |
| UNKNOWN | shimmer | 440 | 48 ms (attack+decay) | tick breve |
| NOTIFY | notifica | 440 | 48 ms | come UNKNOWN |
| DONE | conferma | 440 → 660 (quinta) | 160 ms | diade armoniosa |
| CONFIRM | conferma azione | 440 → 660 | 160 ms | come DONE |
| CONTRADICTED | contraddizione | 440 → 415.3 (−1 st) | 160 ms | glissato discendente |
| CLOSE | chiudi finestra | 415.3 | 160 ms | morph token |

Gate: reduced-motion **oppure** silent (`AudioManager.RINGER_MODE_SILENT` / host silent) → `emit` = null, niente crash.

---

## Mapping haptic (da `haptic.json`)

| Causa | Pattern token | Amp | ms | Count | Gap |
|-------|---------------|-----|----|-------|-----|
| HELD | light / tick | 0.35 | 12 | 1 | 40 |
| UNKNOWN | double-tap / medium | 0.65 | 20 | 2 | 40 |
| DONE / CONFIRM / TAP | tap / light | 0.35 | 12 | 1 | 40 |
| CONTRADICTED | medium una volta (buzz breve) | 0.65 | 20 | 1 | 40 |

Android: `VibrationEffect.createWaveform` con amplitude 1–255. G5 resta su `performHapticFeedback`.  
Mac: stesso schema semantico, attuatore dichiarato **limitato** (`NSHapticFeedbackManager` generic).  
Fallback: engine `false` → nessun pulse, log `haptic engine unavailable`.

---

## Tabella audit — SE-001..011

| Test | Invariante | Percorso | Negativo | PASS/GAP |
|------|------------|----------|----------|----------|
| SE-001 | ogni gesto mappato emette tono (freq/durata da token); reduced → nessuno | `SystemAudio.emit` + PCM | asset binario | **PASS** |
| SE-002 | silent → audio off, no crash | `RINGER_MODE_SILENT` / `deviceSilent` | crash | **PASS** |
| SE-003 | ogni stato epistemico → pattern da token; reduced → nessuno | `SystemHaptic.emit` | intensità inventata | **PASS** |
| SE-004 | engine assente → disabilitato + log onesto | `UNAVAILABLE` | silenzio / crash | **PASS** |
| SE-005 | refraction: pixel diversi, meanAbs < 40; badge identico | `GlassOptics` + `SensoryRaster` | asse coperto | **PASS** |
| SE-006 | noise: grana (meanAbs < 16); badge identico | `applyNoise` | velo sul badge | **PASS** |
| SE-007 | confirm → particelle; idle → 0 | `ParticleField.frame` | spark senza trigger | **PASS** |
| SE-008 | edge alpha < centro (255) | `AlphaMask.edge` | fade assente | **PASS** |
| SE-009 | stessa semantica; Mac haptic limited dichiarato | source Mac + token Hz | full LRA su Mac | **PASS** |
| SE-010 | RX+MR+LG+MO+DT+SH verdi; asse visibile attraverso optics | suite 82 / 56 / launcher | conflitto | **PASS** |
| SE-011 | freeze | diff frozen vuoto | tocco a3ui/core | **PASS** |

**GAP: nessuno** (distorsione liquida dichiarata deferred, non GAP del gate).

Gate:

```
./gradlew :renderers:android-compose:testDebugUnitTest --offline
./gradlew :renderers:mac-compose:test --offline
./gradlew :launcher:testDebugUnitTest --offline --tests a3.launcher.RendererExposureLauncherTest --tests a3.launcher.LauncherAcceptanceTest
```

Android **82**, Mac **56**, launcher L7+RX_008 verdi.

---

## Media campione

| File | SHA-1 | Cosa mostra |
|------|--------|-------------|
| `review-assets/audio-mapping.json` | `feaec56af96ade19adff3f8580b3537c019336af` | mapping tono ← token morph |
| `review-assets/haptic-patterns.json` | `f22efa263d9b1074a068652a084204970369de2f` | pattern haptic + limite Mac |
| `review-assets/refraction.png` | `f4cf019e54ae8d27f73b5f30fc1ec9ab4db75165` | vetro con micro-deviazione + badge |
| `review-assets/noise.png` | `e710dec9788c3b7bde7f7e5c05c85b325ce0c78f` | micro-grana, badge intatto |
| `review-assets/particle-frames.png` | `16bf9e22670632009075844725a8033406066478` | 4 frame sparks su conferma |
| `review-assets/masking.png` | `c6031d352c69d7bb575e2077f61c47fc110889b9` | fade alfa ai bordi |

---

## Cosa è in questa fase

- SystemAudio procedurale (PCM in memoria, invariant `oneSoundOneVisibleCause`)
- SystemHaptic token-locked; Android `VibrationEffect`; Mac `NSHapticFeedbackManager` limited
- Glass refraction / noise (shader GLSL/AGSL/Metal + eval CPU), default off sul catalogo vetro
- Particle su trigger conferma; alpha masking ai bordi
- Reduced-motion e silent rispettati; fallback haptic onesto

## Non toccato

Asse epistemico (pulse/shimmer LinearEasing). Fill vetro catalogo. Occupancy 18sp. Foley di stage (`CountingFoleySink` / `AudioTrackFoleySink`). `a3ui`, core, broker, android-core, launcher. Distorsione liquida. Haptic localizzato / pressure.

---

## Tag

`renderer-android-v0.15` / `renderer-mac-v0.6` solo a gate verde. Niente push.

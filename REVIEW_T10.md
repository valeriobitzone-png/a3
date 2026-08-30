# REVIEW_T10 — Foley real-time Android

**Unfrozen:** `:renderers:android-compose` (FoleySink + PCM + bind + tests). This review.  
**Frozen SOURCE intact:** `core/`, `core/json/`, `prediction/`, `projection/`, `a3ui/`, `adapters/`, `intent-model/`, `launcher/`, `renderers/android-core/`. `git diff` on those trees is empty.  
**Fuori scope:** FoleySpec, nuovi ruoli/meanings, file wav/ogg/`res/raw`, Web Audio, Core Haptics, unfreeze launcher, push.

**No tag until this gate is green.** Tag on the green commit: `renderer-android-v0.6`.

---

## F0 — segnali già veri

Nessun campo nuovo in a3ui / `HapticEvent`. Bind solo:

| Causa visibile | Segnale già vero | Suono |
|---|---|---|
| ascolto | `RendererContext.stage` | bed aria breve (`Theme.airBedMs`) |
| morph | `plan.to` / `plan.shared` + `durationHint` | pitch `Theme.morphSemitones` (−1) |
| approva | stage `approva` (timing `HapticMap` double-tap / `Theme.hapticGapMs`) | due battiti, causa=`approva` — **non** `tap`/`double-tap` |
| crack | `LocalCrack` / `rollbackVisible` | saw ≤ `Theme.crackMs` (80), poi stop |
| lavoro | stage `lavoro` | silenzio vero: `stop()`, `generators()==0` |
| pronto | stage `pronto` | tace (hold visivo già T9) |

**F0: nessun GAP.** Nessun FoleySpec.

---

## A1 — binding + gain

**Invariant:** transizioni ascolto / morph / approva / crack → `start(cause)` sul **CountingFoleySink** (nessun AudioTrack in `FoleySink.kt`). `lavoro` → `generators()==0` e morph successivo non parte. `masterGain() ≤ Theme.voiceCeiling` (numero `0.08f` nel Theme, non una dichiarazione). Cause haptic `tap`/`double-tap` assenti dal sink. Nessun path `.wav`/`.ogg`/`res/raw` in compose `src/main`.

**How:** un `setContent`, sink-contatore iniettato in `ComposeRenderer(..., foley=sink)`. Stessa interfaccia del sink device.

**Negative:** freeze lavoro non emette un altro `start`. Counting sink senza `android.media`.

**Result:** **PASS.**

---

## A2 — no asset

**Invariant:** grep renderers+launcher `src/main`: niente `.wav` `.ogg` `res/raw`, niente `AudioContext` / Web Audio.

**Result:** **PASS.**

---

## A3 — P51 / core pulito

**Invariant:** `android-core` `src/main` senza `android.media` / AudioTrack / SoundPool / Vibrator / `performHapticFeedback`.

**How:** T10 A3 walk + `:renderers:android-core:test` `P51` PASSED. Core non modificato.

**Result:** **PASS.**

---

## A4 — linguaggio / freeze

**Invariant:** `git diff -- a3ui renderers/android-core launcher` vuoto. Nessun `*Spec` nuovo.

**Result:** **PASS.**

---

## A5 — foundation + gradle

**Invariant:** no Material. Moduli separati, no `--rerun-tasks` aggregato.

```
./gradlew :renderers:android-compose:testDebugUnitTest --no-parallel
  A1 A2 A3 A5 PASSED (+ T9 G1–G6, R1–R10, P52 P55)

./gradlew :renderers:android-core:test --no-parallel
  P51 PASSED (suite android-core verde)
```

**Result:** **PASS.**

---

## A6 — fisica Tab A9

Serial `R9ZY80P8GRH`. `monkey -p a3.launcher -c android.intent.category.LAUNCHER 1` → `.MainActivity`.

Stesso sink (PCM `AudioTrackFoleySink`), log `a3.foley`:

```
start cause=ascolto generators=1 gain=0.08
stop generators=0 gain=0.08
start cause=approva generators=2 gain=0.08
stop generators=0 gain=0.08
stop generators=0 gain=0.08    ← lavoro / freeze
```

**Log FUORI repo:** `/Users/ambrogio/Downloads/a3-t10-foley.log`

**Result:** **PASS.**

---

### Audit

| Test | Invariante | Percorso reale | Perché non tautologico | Negativo | Esito |
|---|---|---|---|---|---|
| F0 | solo segnali già veri | stage, crack, plan, durationHint, haptic gap | non si inventa FoleySpec | a3ui/core frozen | **PASS** |
| A1 | cause+zeri+gain | CountingFoleySink start list + generators | voiceCeiling 0.08 nel Theme, assert `<=` | tap/double-tap assenti; lavoro 0 | **PASS** |
| A2 | no asset | grep + `res/raw` assente | PCM, non file | .wav/.ogg/Web Audio | **PASS** |
| A3 | core pulito | walk android-core + P51 | AudioTrack solo in compose device sink | Vibrator/media assenti | **PASS** |
| A4 | freeze | git diff vuoto | unfrozen solo compose | launcher/a3ui/core | **PASS** |
| A5 | foundation | compose+core test separati | T9 ancora verde | no aggregato | **PASS** |
| A6 | silenzio lavoro | logcat Tab A9 | generators 1→2→0, gain 0.08 | log non in git | **PASS** |

**T10 GAP: nessuno.**

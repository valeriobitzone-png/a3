# REVIEW_T9 — Grafica vera (occupazione + temperature + verb binding)

**Unfrozen:** `:renderers:android-core` (`RendererContext.stage`), `:renderers:android-compose` (Theme, catalog occupancy, appliers), `:launcher` (stage from host events, `RendererContext` / `A3Screen` / tests). This review.  
**Frozen SOURCE intact:** `core/`, `core/json/`, `prediction/`, `projection/`, `a3ui/`, `adapters/`, `intent-model/`. `git diff` on those trees is empty.  
**Fuori scope:** audio (T10), Material Design, nuovi `*Spec` / ruoli / meanings, unfreeze a3ui, orario+stazione sulla stessa riga, push, tag launcher.

**No tag until this gate is green.** Tag on the green commit: `renderer-android-v0.5`. Launcher senza tag.

---

## F0 — albero treno OGGI (bloccante, pre-code)

SurfaceComposer + fixture launcher (`timetable` / `passenger` / `price` / `confirm` / residual `departure`):

- `list` `timetable` di `item` (`08:45` / `09:12` / `10:03`). Item senza figli. Nessun atom `station`. Nessun figlio station dentro l'item.
- `field` Ada, `text` `12.40`, `action` `"hold 08:45"`.
- Occupazione = full-width di **quei** nodi + peso su field / price / action. List non è più `height(160.dp)`.

Verbi esistenti (a3ui frozen — solo bind):

| Verbo | Spec già vero | Bind T9 |
|---|---|---|
| ascolto + durationHint | `MotionSpec.durationHint` (180/240/320) | Theme + `tween(durationHint)` su stage `ascolto`; prefetch hit host → `ascolto` |
| morph su change output | `MorphSpec.shared` / `SharedElementPlan` | `ComposeMorphApplier` keyed on `plan.to` + `plan.shared` |
| hold 1.03 | stiffness/damping sullo spring | Theme.holdScale su stage `approva` (`trustHold`) |
| crack ≤80ms + return | durationHint come cap; overlay già `"return"` | Theme.crackMs=80, path (non quinto stage) su `rollbackVisible` |
| haptics | `HapticMap` tap / double-tap | `View.performHapticFeedback` solo in android-compose |

Sette ruoli invariati: `stack`, `row`, `list`, `item`, `action`, `field`, `text`.  
Stage renderer-owned (non CanonicalJson, non RenderedOutput): `ascolto|lavoro|pronto|approva`. Crack è path.

**F0: nessun GAP.** Nessun verbo illeggibile. a3ui non scongelato. Stazione non inventata.

---

## G1 — occupazione

**Invariant:** catalogo treno, `list`/`item` >80% larghezza. Action con peso (Theme, non literal `48.dp`). Source catalog senza `160.dp` / `48.dp`. `Theme.actionMin` ≠ `48.dp`.

**How:** compose test 400×800 dp + `boundsInRoot`. Catalog `Column` `fillMaxSize`, `weight(Theme.listWeight)` / `weight(Theme.slotWeight)`, action `sizeIn(Theme.actionMin)`.

**Negative:** `160.dp` e `48.dp` assenti dal catalog. Vecchi bounds sparsi non sono i nuovi.

**Device (G8 dump Tab A9 800px):** list/items `[0,*][800,*]`; Ada `[0,445][800,637]`; `12.40` `[0,658][800,850]`; `hold 08:45` `[0,871][800,1063]` (altezza da peso, non 48dp).

**Result:** **PASS.**

---

## G2 — linguaggio

**Invariant:** `git diff a3ui/` vuoto. Nessun `class *Spec` nuovo in renderers+launcher. Nessun ruolo oltre i 7. Nessun meaning nuovo (fixture resta `timetable` / `departure` / `passenger` / `price` / `confirm`).

**How:** `T9ComposeAcceptanceTest.G2`, `LauncherArchitectureTest.G2`, `git diff --stat -- a3ui`.

**Negative:** `"station"` assente da launcher `src/main`. Nessun `ThemeSpec` / `StageSpec`.

**Result:** **PASS.**

---

## G3 — verb binding

**Invariant:** PrefetchComposeCache hit + `RendererContext.stage=ascolto` (Theme + `durationHint`). Morph su change output via `plan.shared`. Hold scale `1.03` su `approva`. Crack `Theme.crackMs ≤ 80` + overlay `"return"` su rollback. Overlay `"approve"` / `"return"` resta fallback.

**How:** host: start → `ascolto`, confirm → `approva`, execute → `lavoro`, mismatch → `rollbackVisible` + crack path. Appliers filled in place (no longer identity / 1f→1f unused / haptic no-op).

**Negative:** overlay copy non sostituita da Material Dialog. Crack non è un quinto stage.

**Result:** **PASS.**

---

## G4 — temperature

**Invariant:** 4 stage → 4 `ColorMatrix` (tint/esposizione sul materiale). Nessun Text/dot nuovo nel tree (`ascolto`/`lavoro`/`pronto`/`approva` non sono copy; niente `•`/`●`).

**How:** `Theme.material(stage)` + `saveLayer` color filter su `a3-material`. Compose cicla i 4 stage.

**Negative:** stage names assenti come `BasicText`.

**Result:** **PASS.**

---

## G5 — haptics

**Invariant:** compose esegue `HapticMap` (`tap` → 1, `double-tap` → 2). P51 verde: android-core `src/main` senza `Vibrator` / `performHapticFeedback`.

**How:** `performHapticMap` su View di test (count=3). Haptic Android solo in android-compose.

**Negative:** P51 walk `src/main` android-core.

**Result:** **PASS.** (`P51_haptic_interpreter_has_no_device_api_in_core` PASSED)

---

## G6 — foundation

**Invariant:** renderers+launcher `src/main`: no Material, no `Button(` / `Card(` / `Dialog(`. `TextField(` solo se `BasicTextField(`.

**How:** G6 compose walk + L1 launcher + R7 compose.

**Result:** **PASS.**

---

## G7 — moduli separati

Host: Mini 16GB, 2026-08-30. **No** `./gradlew test --rerun-tasks`. **No** aggregato.

```
./gradlew :renderers:android-core:test --no-parallel
  T9_stage_is_closed_renderer_owned_set PASSED
  P51_haptic_interpreter_has_no_device_api_in_core PASSED
  (suite android-core verde)

./gradlew :renderers:android-compose:testDebugUnitTest --no-parallel
  G1 G2 G3 G4 G5 G6 PASSED
  R1 R3–R7 R9 R10 P52 P55 PASSED

./gradlew :launcher:testDebugUnitTest --no-parallel
  G2 G3 host + F3 (senza 48.dp) PASSED
  L1–L7 L9 L10 F2 F3 PASSED

./gradlew :a3ui:test --no-parallel
  P28–P42 P57–P64 J3 PASSED  (source frozen, UP-TO-DATE compile)
```

**Result:** **PASS.**

---

## G8 — fisica Tab A9

Serial `<redacted-device-id>`. `monkey -p a3.launcher -c android.intent.category.LAUNCHER 1`.

- Focus: `a3.launcher/.MainActivity`
- Dump: nodi treno full-width 800px, field/price/action con peso verticale, copy `08:45`/`09:12`/`10:03`/`Ada`/`12.40`/`hold 08:45`
- Screenshot **fuori repo:** `<local-downloads-path-redacted>/a3-t9-occupied.png`

**Result:** **PASS.**

---

### Audit

| Test | Invariante | Percorso reale | Perché non tautologico | Negativo | Esito |
|---|---|---|---|---|---|
| F0 | albero treno senza station; verbi bindabili | SurfaceComposer + TemporalBuilder | non si inventa riga orario+stazione | a3ui frozen | **PASS** |
| G1 | occupazione full-width + peso | bounds compose + dump 800px | list/item 800/800; action h≈192px | 160.dp/48.dp assenti | **PASS** |
| G2 | linguaggio chiuso | git diff a3ui + grep Spec/role/meaning | Theme non è uno Spec | station assente | **PASS** |
| G3 | 4 verbi su Spec esistenti | prefetch + morph key + holdScale + crackMs | appliers non più stub | overlay approve/return resta | **PASS** |
| G4 | 4 materiali, zero copy/dot | 4 ColorMatrix distinte | stage non è Text | •/● assenti | **PASS** |
| G5 | HapticMap performed | count 3 su View | P51 core pulito | Vibrator assente in core | **PASS** |
| G6 | foundation only | walk src/main | R7/L1 ancora verdi | Button(/Card(/Dialog( | **PASS** |
| G7 | 4 gradle invocazioni | moduli separati | no --rerun-tasks aggregato | a3ui source unedited | **PASS** |
| G8 | Tab A9 occupata | monkey → MainActivity + dump | bounds full-width sul device | screenshot non in git | **PASS** |

**T9 GAP: nessuno.**

---

## Grep / freeze

```
git diff --stat -- core prediction projection a3ui adapters intent-model   # empty
grep -R "class .*Spec" renderers launcher/src --include='*.kt'            # empty
```

Audio non introdotto. CanonicalJson di `RenderedOutput` invariato (stage non è sull'output).

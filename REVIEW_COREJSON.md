# REVIEW_COREJSON — motore canonico unico, modulo cieco

**Unfrozen:** `:core:json` (nuovo) + facciate in `core/runtime`, `prediction/`, `projection/`, `a3ui/`, `renderers/android-core/` (dipendenza + delegate). `settings.gradle.kts` include. This review.  
**Frozen SOURCE intact:** `adapters/`, `intent-model/`, `launcher/`, `renderers/android-compose/`.  
**Fuori scope:** H3, `missingPrecondition`, T9/grafica, README H3.

**No tag until this gate is green.** Tags on the green commit: `core-json-v0.1` + consumer bumps (`core-v0.3`, `prediction-core-v0.2`, `projection-core-v0.2`, `a3ui-core-v0.3`, `renderer-android-v0.4`). Diff = move + delegate.

---

## Fase 0 — inventario A vs B (pre-move, sola lettura)

Due confronti. Corpus primitivi dumpato **prima** del move (`j3-pre-move.txt` in ciascun modulo; primitivi anche in `:core:json` `j3-primitives.txt`).

### A — motore (stessa regola)

string escape (`\"` `\\` `\n` `\r` `\t` `\u00xx`) · number (NaN/Inf throw; integer-valued → long) · obj TreeMap UTF-16 · null omitted negli oggetti · array compatto senza sort · Instant `toString()` ISO-8601 · UTF-8 compact.

Dump pre-move, stessi primitivi sui 5 `object CanonicalJson`:

| chiave | byte |
|---|---|
| null | `null` |
| true/false | `true` / `false` |
| str | `"a\"b\\c\n"` |
| int / double_int | `1` |
| double | `1.5` |
| instant | `"2026-08-27T08:00:00Z"` |
| map (`b,a,n=null`) | `{"a":2,"b":1}` |
| list | `[3,1,2]` |
| empty | `{}` / `[]` |

**A_ALIGNED.** Nessun byte diverso su primitivi/TreeMap. Non GAP. Non riconciliato a occhio.

`omitNulls=false` su `PresentationAtom` non è regola A: è facciata B (solo projection). Default A resta null omitted.

### B — facciate tipizzate (devono differire)

| Modulo | `when` owner | Esempio |
|---|---|---|
| core/runtime | Fact SCHEMA vs STATE, BeliefState, Goal, Intent, Plan, … | `of(fact)` omette `id`; `ofState` lo include |
| prediction | Forecast, FutureState, PreparedState, ProjectionCandidate (prediction) | `of(Forecast)` |
| projection | PresentationAtom/State, Projection, ProjectionCandidate (projection) | atom `v:null` tenuto |
| a3ui | Node, Binding, A3UISurface, MotionSpec, … | lineage copiata come mappa |
| android-core | ColorValue, RenderedNode, RenderedOutput, … | `hint`/`text` sul nodo |

Le facciate **devono** differire (strati diversi). Non è GAP.

Tre `SchemaValidator` pre-move: core (integer min + path `../..`) · projection (TreeMap cache) · a3ui (`$ref`). Motore unico su **stringa** in `:core:json` (unione: `$ref` + integer min + tre path). `validate(model)` resta facciata (encode tipizzato + delegate). `ModelValidator` resta nel modulo owner.

---

## J1 — cieco

**Invariant:** `:core:json` ↛ world / runtime / prediction / projection / a3ui / renderers. Dipende da stdlib + Jackson (parse only). Nessun `of(Forecast)` / `ofState(BeliefState)`.

**How:** ArchUnit `a3.core.json..`. Gradle file senza `project(":core:world")` / runtime / prediction / ….

**Negative:** engine source non contiene `Forecast`, `BeliefState`, `ProjectionCandidate`, `RenderedOutput`.

**Result:** **PASS.**

---

## J2 — engine source

**Invariant:** regole A (TreeMap, omit null, compact, number, escape) vivono solo in `:core:json`. I 5 `object CanonicalJson` restano facciate.

**How:** source walk. Facciate: niente `canonical JSON cannot encode`, niente `padStart(4, '0')`, niente `private fun number` / `string`, niente `TreeMap`.

**Result:** **PASS.**

---

## J3 — byte-identity

**Invariant:** corpus fissato **prima** del move, da tutti e 5 i moduli → byte identici dopo.

**How:** dump pre-move → `j3-pre-move.txt`. Stessi oggetti re-encodati dalle facciate post-delegate. Primitivi anche via `a3.core.json.CanonicalJson.encode`.

**Negative:** `PresentationAtom` con `v=null` resta `{"v":null}` (facciata `CanonicalObject(omitNulls=false)`).

**Result:** **PASS** (5 moduli + engine primitivi).

---

## J4 — validation parity

**Invariant:** corpus valid+invalid → stessi esiti pre/post.

**How:** goal + presentation validanti; `state_ref` extra su presentation → fail; surface catalog valida; `may_commit` su prefetch → fail. Stesse stringhe dei test owner pre-move.

**Result:** **PASS.**

---

## J5 — consumer (separati, no `--rerun-tasks` aggregato)

Host: Mini 16GB, 2026-08-30.

| Comando | Esito |
|---|---|
| `./gradlew :core:json:test --no-parallel` | PASS (J1–J4) |
| `./gradlew :core:runtime:test --no-parallel` | PASS (incluso J3) |
| `./gradlew :prediction:test --no-parallel` | PASS |
| `./gradlew :projection:test --no-parallel` | PASS |
| `./gradlew :a3ui:test --no-parallel` | PASS |
| `./gradlew :renderers:android-core:test --no-parallel` | PASS (`:test`: modulo `kotlin("jvm")`, non esiste `testDebugUnitTest`) |
| `./gradlew :renderers:android-compose:testDebugUnitTest --no-parallel` | PASS |

`:renderers:android-core:clean` prima del re-run: class duplicata Finder `Renderer02CoreAcceptanceTest 2.class` (non A3). Non `--rerun-tasks` aggregato.

**Result:** **PASS.**

---

### Audit

| Test | Invariante | Percorso reale | Perché non tautologico | Negativo | Esito |
|------|------------|----------------|------------------------|----------|-------|
| Fase 0 A | stesso motore → stessi byte primitivi | dump 5 moduli **prima** del move | 5 copie indipendenti | map null omitted, keys sorted | **PASS (aligned)** |
| Fase 0 B | facciate diverse per strato | `when` tipizzato | Forecast ≠ BeliefState ≠ atom | non GAP | **n/a** |
| J1 | json cieco | ArchUnit + Gradle | jackson-databind presente; layer project assenti | no Forecast in engine | **PASS** |
| J2 | regole A solo in json | source walk | facciate senza number/string/TreeMap | 5 `object CanonicalJson` restano | **PASS** |
| J3 | byte pre = post | golden `j3-pre-move.txt` | corpus catturato con il motore vecchio | atom `v:null` tenuto | **PASS** |
| J4 | valid+invalid | SchemaValidator su stringa | illegal `state_ref` / `may_commit` | valid goal/presentation/surface | **PASS** |
| J5 | consumer verdi | gradle separati | T1–T10, P1–P64, R1–R10 | no aggregato `--rerun-tasks` | **PASS** |

**COREJSON GAP: nessuno.** H3 non toccato. `missingPrecondition` non toccato.

---

## Freeze

`git diff` on `adapters/ intent-model/ launcher/ renderers/android-compose/ README.md` is empty.

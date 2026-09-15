# REVIEW_SPLIT_LINE

FASE SPLIT-LINE — chiudere il sottoalbero pubblico e fissare la linea §9.
Unfrozen: `:core:runtime`, `:prediction`, `docs/`. Frozen: tutto il resto.
Niente push. Tag `split-line-v0.1` **solo a gate verde**.

Linea: [`docs/SPLIT_LINE.md`](SPLIT_LINE.md). Grafo: `python3 docs/split-line-graph.py`.

---

## Arco risolto

| | Prima | Dopo |
|---|-------|------|
| Dipendenza | `:core:runtime` `testImplementation(:prediction)` | **rimossa** |
| Test P1/P2/P7/P9 | `core/runtime/.../PredictionWorldGateTest.kt` | `prediction/.../PredictionWorldGateTest.kt` |
| Nuova dipendenza | — | `:prediction` `testImplementation(:core:runtime)` (estensione → pubblico) |

Il test non è stato cancellato: è stato spostato fuori dal sottoalbero §9.

---

## Grafo — prima (estratto rilevante)

Unico arco pubblico → estensione (compile + test):

```
:core:runtime [testImplementation] → :prediction   **PUBLIC→EXTENSION**
```

Altri archi del sottoalbero pubblico puntavano solo a moduli pubblici (`:core:*`, `:conformance`).

---

## Grafo — dopo

Output di `python3 docs/split-line-graph.py` (exit 0):

```
:core:runtime [api] → :core:world
:core:runtime [implementation] → :core:json
:core:runtime [implementation] → :core:action
:prediction [api] → :core:world-api
:prediction [implementation] → :core:json
:prediction [testImplementation] → :core:runtime
```

**Public → extension arcs: none**

(Produzione di `:prediction` resta su `:core:world-api` only; ArchUnit P11 invariato. Solo i test del gate vedono `:core:runtime`.)

---

## Tabella audit — SL-001..005

| ID | Gate | Evidenza | Esito |
|----|------|----------|-------|
| SL-001 | Arco runtime→prediction rimosso | `core/runtime/build.gradle.kts` senza `:prediction`; test in `:prediction` | **PASS** |
| SL-002 | Zero archi pubblico→estensione | `docs/split-line-graph.py` exit 0; `SplitLineTest.SL_002` | **PASS** |
| SL-003 | `docs/SPLIT_LINE.md` | Elenco dentro/fuori + rationale §9; regola “split segue §9” | **PASS** |
| SL-004 | Regression test | `:core:runtime:test` + `:prediction:test` (P1/P2/P7/P9 spostati, non persi); `./gradlew test` | **PASS** |
| SL-005 | Freeze | Diff solo in `core/runtime/`, `prediction/`, `docs/` | **PASS** |

---

## Freeze (SL-005)

```
git diff --stat -- . ':!core/runtime' ':!prediction' ':!docs'
```

vuoto dopo commit. settings, showcase, overlay, renderers, spec, conformance non toccati.

---

## Esecuzione

```
python3 docs/split-line-graph.py
./gradlew :core:runtime:test :prediction:test --no-daemon -Pkotlin.compiler.execution.strategy=in-process
./gradlew test --no-daemon -Pkotlin.compiler.execution.strategy=in-process
```

Tag: `split-line-v0.1` (annotated) solo a SL-001..005 verdi. Niente push.

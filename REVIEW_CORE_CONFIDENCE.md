# REVIEW_CORE_CONFIDENCE

AUDIT-FIRST. Protocol: FASE CORE-CONFIDENCE — confidence multidimensionale + aggregazione deterministica. Nuovo modulo `:core:confidence`. Consuma `:core:temporal` (`TemporalStamp`) e `:core:truth` (`TruthBearer`) senza toccarli. Frozen: `core/admission`, `core/action`, `core/json`, `core/envelope`, `core/temporal`, `core/truth`, `a3ui`, `renderers`, `broker`, `agent`, `showcase`. Niente push.

**Contratto:** la confidence non è un numero 0-1 isolato. È un vettore a cinque dimensioni. L'aggregazione è un minimo pesato dichiarato in config: una dimensione a zero azzera lo score. Nessun `now()`, nessuna media naive.

---

## Freeze (CF-008)

```
git diff --stat -- core/admission core/action core/json core/envelope core/temporal core/truth a3ui renderers broker agent showcase
```

vuoto. Solo `:core:confidence` + `settings.gradle.kts` (`include(":core:confidence")`) + questo REVIEW.

---

## Dimensioni (`ConfidenceVector`)

| Dimensione | Significato | 0 | 1 |
|------------|-------------|---|---|
| `source_reliability` | affidabilità della fonte | modello / assente | API firmata (`OBSERVED_SIGNED`) |
| `evidence_strength` | forza dell'evidenza | assente / discorde | 2+ osservazioni concordi |
| `recency` | freschezza | età ≫ half-life | `t_observe` = `t_present` |
| `corroboration` | fonti indipendenti concordi | 1 fonte o stessi `source_id` | 3+ `source_id` distinti |
| `verification` | grado di verifica | none | deterministic |

Manca una dimensione in parse → `ConfidenceReject("missing …")`. Fuori da `[0,1]` o NaN → rifiuto. Non esiste score senza vettore completo.

Tabella provenance (dichiarata in `ConfidenceConfig`, senza modificare truth): `observed_signed=1.0`, `human_admitted=0.8`, `inferred=0.5`, `derived_model=0.3`.

Verifica: `sandbox=0.3`, `real=0.8`, `deterministic=1.0`, `none=0.0`. `VerificationEnvironment` truth mappa solo SANDBOX/REAL; DETERMINISTIC è grado di questo modulo.

---

## Aggregazione (weighted min)

Pesi di default, in config non nel `min`:

```
score = min(
  source_reliability * 1.0,
  evidence_strength * 1.0,
  recency * 0.8,
  corroboration * 0.6,
  verification * 0.9
)
```

Vettore tutto 1 con pesi default → **0.6** (soffitto = peso di corroboration). Vettore tutto 1 con pesi UNIT → **1.0**. Dimensione a 0 → score **0**, non compensabile. Alzare `corroboration` da 0.6 a 1.0 sul vettore tutto 1 → score **0.8**.

Recency: `2^(-(t_present - t_observe) / half_life)`, `half_life = 21600s` (6h). Età negativa clamp a 0. Nessun clock.

Corroboration: `source_id` distinti (lowercase). 1 o ripetuti → 0; 2 → 0.5; 3+ → 1. Discorde → 0.

---

## Mapping a truth class (dichiarativo)

| Score | Truth class | Category |
|-------|-------------|----------|
| `= 0.0` | qualsiasi | `unknown` |
| `>= 0.8` | `FACT` | `high` (alta) |
| `>= 0.5` | non alta | `medium` (media; observation tipica) |
| `(0, 0.5)` | hypothesis / altro | `low` (bassa) |

Con pesi default, FACT + vettore perfetto è **media** (0.6): senza corroboration piena il soffitto non raggiunge 0.8. Alta nel lock: pesi UNIT.

---

## Vettori lock (2ª impl.)

File: `core/confidence/src/test/resources/confidence-vectors.json`  
SHA-1: `3694ca51543fc803be733977b01de7725c6a41a0`  
SHA-256: `25efbc9f1b3730658c34502f2564d18a8aad04c1ee202b672be4b020917fddee`

UTF-8 compact, niente newline finale. `CanonicalJson` rende `1.0` come `1`. Rigenerare solo con `CF_DUMP=1`.

Corpus allineato a temporal: `t0 = 2026-08-27T08:00:00Z`, `t_present = 2026-08-27T11:00:00Z` (età fixture 10799s). Recency fixture = `2^(-10799/21600)` = `0.7071294727113612` (IEEE-754 nearest-even; **non** il double libm `0.7071294727113613`). Weighted min = `0.565703578169089` (bottleneck recency×0.8) — invariato rispetto a v0.1.

Score attesi (input → score):

| Caso | Score | Category |
|------|-------|----------|
| vettore tutto 1, pesi default, FACT | `0.6` | medium |
| vettore tutto 1, pesi UNIT, FACT | `1` | high |
| vettore tutto 1, pesi UNIT, OBSERVATION | `1` | medium |
| recency 0.5, pesi default, HYPOTHESIS | `0.4` | low |
| corroboration 0, resto 1, FACT | `0` | unknown |
| verification 0, resto 1, FACT | `0` | unknown |
| pesi default, corroboration alzato a 1 | `0.8` | (solo score) |
| 1 fonte / source_id ripetuti | `0` | corroboration |
| 2 fonti indipendenti | `0.5` | corroboration |
| 3+ fonti indipendenti | `1` | corroboration |
| età 0 / half-life / 2× / 4× | `1` / `0.5` / `0.25` / `0.0625` | recency |
| sandbox / real / deterministic | `0.3` / `0.8` / `1` | verification |

Chiavi lock: `config`, `corroboration`, `fixture`, `mapping`, `recency`, `verification`, `weights_shift`, `zero_not_compensated`.

---

## Tabella audit — CF-001..008

| Test | Invariante | Percorso | Negativo | PASS |
|------|------------|----------|----------|------|
| CF-001 | vettore completo | 5 dimensioni → score; parse round-trip | dimensione mancante / NaN / fuori range → `ConfidenceReject` | **PASS** |
| CF-002 | non compensativo | una dimensione a 0 → score 0; altre a 1 non salvano | cambio pesi default→corroboration 1.0 cambia 0.6→0.8 | **PASS** |
| CF-003 | recency exp | età 0→1; half-life→0.5; 2×→0.25; 4×→0.0625 | età > half-life → recency < 0.5; nessun `now()` | **PASS** |
| CF-004 | corroboration | 1 fonte=0; 2 indip=0.5; 3+=1 | `train,train,TRAIN` = 0; discorde = 0 | **PASS** |
| CF-005 | verification | sandbox=0.3 < real=0.8 < deterministic=1.0 | none=0; signed > inferred > model | **PASS** |
| CF-006 | mapping | 0→unknown; UNIT+FACT→high; default+FACT→medium | hypothesis recency 0.5 → low; evidence 0 → unknown | **PASS** |
| CF-007 | determinismo | stesso assess permutando source_id → stesso score | niente Random / `now()` / average | **PASS** |
| CF-008 | freeze | git diff frozen vuoto; ArchUnit | niente dipendenze envelope/action/a3ui/… | **PASS** |

Gate:

```
./gradlew :core:confidence:test --offline --no-daemon
```

---

## Vietato (verificato)

Media naive. Score senza vettore completo. `Instant.now()` in recency. Corroboration senza `source_id` distinti. Modifiche a truth/temporal/envelope/admission/action/json/a3ui/renderers/broker/agent/showcase. Niente push.

---

## FIX-ULP (`core-confidence-v0.2`)

Scongelato solo `:core:confidence`. Recency non usa più `2.0.pow` (libm, 1 ULP alto). Formula: `exp(ln 2 · -Δt / 21600)` in `BigDecimal` 80 cifre HALF_EVEN, poi `doubleValue()` nearest-even. `StrictMath.pow` su JDK 21 produce ancora `0.7071294727113613`; non basta.

| Campo lock | v0.1 (libm) | v0.2 (nearest-even) |
|------------|-------------|---------------------|
| `recency.fixture_10799s` / `fixture.vector.recency` | `0.7071294727113613` | `0.7071294727113612` |
| `fixture.score` | `0.565703578169089` | `0.565703578169089` |
| categoria | medium | medium |

SHA-1 lock v0.1 (libm): `b1816d968b3e9a45b751b45dda3f52ad8086d81c`  
SHA-1 lock v0.2: `3694ca51543fc803be733977b01de7725c6a41a0`  
SHA-256 lock v0.2: `25efbc9f1b3730658c34502f2564d18a8aad04c1ee202b672be4b020917fddee`

| Test | Invariante | Percorso | Negativo | PASS |
|------|------------|----------|----------|------|
| ULP-001 | recency Δt=10799 | binary64 `0x1.6a0cdfceaa81bp-1` = `0.7071294727113612` | libm `…3613` / `2.0.pow` | **PASS** |
| ULP-002 | score/categoria | weighted min `0.565703578169089`, medium | cambio soglia | **PASS** |
| ULP-003 | regressione | CF-001..008 | — | **PASS** |
| ULP-004 | freeze | git diff fuori `:core:confidence` + questo REVIEW vuoto | tocchi a temporal/truth/envelope/… | **PASS** |

## Tag

`core-confidence-v0.1` (lock libm). `core-confidence-v0.2` (lock IEEE-754 nearest-even) solo a gate ULP-001..004 verde. Niente push.

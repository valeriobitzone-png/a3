# REVIEW_CORE_TEMPORAL

AUDIT-FIRST. Protocol: FASE CORE-TEMPORAL — quattro tempi + ordinamento/fold/dedup. Nuovo modulo `:core:temporal`. Consuma i tipi di `:core:admission` (`SourceId`, `AcceptedObservation`, `ObservationCandidate`) senza modificarli. Serializzazione via `:core:json` (JCS-ready, chiavi ordinate). Frozen: `core/admission`, `core/action`, `core/json`, `a3ui`, `renderers`, `broker`, `agent`, `showcase`. Niente push.

**Contratto:** ogni fatto porta quattro istanti espliciti. Nessun `now()` implicito. L'ordine canonico non dipende dall'arrivo. Il fold è commutativo. Nessuna confidence combinata (Step 5).

---

## Freeze (TM-006)

```
git diff --stat -- core/admission core/action core/json a3ui renderers broker agent showcase
```

vuoto. Solo `:core:temporal` + `settings.gradle.kts` (`include(":core:temporal")`) + questo REVIEW.

---

## Quattro tempi (`TemporalStamp`)

| Campo | Significato | Origine admission (se presente) |
|-------|-------------|----------------------------------|
| `t_event` | quando il fatto è accaduto nel mondo | `ObservationCandidate.occurredAt` |
| `t_observe` | quando la fonte l'ha osservato/registrato | `ObservationCandidate.observedAt` |
| `t_admit` | quando admission l'ha reso Claim | `AcceptedObservation.admittedAt` |
| `t_present` | asOf, quando è presentato/reso | input al confine, mai default clock |

Invarianti: `t_observe >= t_event`; `t_admit >= t_observe`. `t_present` è indipendente (può precedere `t_event` o seguire `t_admit`). Violazione → `TemporalReject` esplicito (`t_observe < t_event` / `t_admit < t_observe`), non silenzioso.

`stampFromAccepted(accepted, tPresent)` è l'unico ponte admission → temporale. `tPresent` è argomento obbligatorio.

---

## Ordinamento

Chiave totale: `(t_observe, source_id, seq)`. Tie-break lessicografico su `source_id` (stringa UTF-16, ASCII-safe per gli id del corpus) poi `seq` numerico signed 64-bit. Subject/key solo se la tripla coincide, così due row con la stessa chiave d'ordine restano commutative. Nessuna dipendenza da ordine di arrivo: `sort(π(stream))` = `sort(stream)`.

---

## Dedup

Chiave: `(source_id, subject, key)`. Ultima osservazione secondo l'ordine totale = stato corrente. Il log è append-only: le row superate restano. `dedup` è idempotente: `dedup(dedup(s).log) == dedup(s)`.

---

## Fold

Merge strutturale per `subject` → `ObservationFold`. Campi = last-wins per `key` dopo dedup (non è combinazione di confidence). History = log canonico del subject. `fold(π(stream))` ha byte-identity con `fold(stream)`.

---

## Vettori canonici di riferimento (lock Step 4 / 2ª impl.)

Corpus: `t0 = 2026-08-27T08:00:00Z`, `t_present = 2026-08-27T11:00:00Z`, subject `trip.milano`.

| # | source_id | key | seq | t_observe | value |
|---|-----------|-----|-----|-----------|-------|
| 1 | hotel | price | 1 | t0+7200s | `89.00` |
| 2 | train | depart | 1 | t0+1s | `09:00` |
| 3 | train | depart | 2 | t0+30s | `09:30` |
| 4 | calendar | slot | 1 | t0+10s | `09:00` |

Ordine canonico: train/depart/1 → calendar/slot/1 → train/depart/2 → hotel/price/1.

Stato corrente dopo dedup: depart=`09:30` (train seq 2), slot=`09:00`, price=`89.00`. History conserva `09:00` di train seq 1.

Stamp JCS (hotel, t_observe = 10:00Z):

```
{"t_admit":"2026-08-27T10:00:01Z","t_event":"2026-08-27T08:00:00Z","t_observe":"2026-08-27T10:00:00Z","t_present":"2026-08-27T11:00:00Z"}
```

File lock (UTF-8 compact, chiavi TreeMap, Instant ISO-8601):

| File | SHA-1 |
|------|-------|
| `core/temporal/src/test/resources/tm-order.json` | `8c70f083d4aa201bb1d8fc2658ffbe2c730d1c5e` |
| `core/temporal/src/test/resources/tm-dedup.json` | `05bd0b2fb50b2253a7b3c5a793fa0a73749828d4` |
| `core/temporal/src/test/resources/tm-fold.json` | `ee8808d9c1c0a02b264cb7b834f6f8a97e05e80a` |

Una 2ª implementazione deve riprodurre questi byte. Rigenerare solo con `TM_DUMP=1` (non nel gate).

---

## Tabella audit — TM-001..006

| Test | Invariante | Percorso | Negativo | PASS |
|------|------------|----------|----------|------|
| TM-001 | quattro tempi; t_present indipendente | `TemporalStamp` + `stampFromAccepted` | `t_observe < t_event` / `t_admit < t_observe` → `TemporalReject` | **PASS** |
| TM-002 | stesso stream, ordini diversi → stesso ordine canonico | 24 permutazioni, chiave `(t_observe, source_id, seq)` | — | **PASS** |
| TM-003 | dedup last-wins, storia conservata, idempotente | `dedup` × permutazioni | seq 1 `09:00` resta nel log | **PASS** |
| TM-004 | fold commutativo, byte-identity | `foldBytes(π)` | niente `confidence` nel canonico | **PASS** |
| TM-005 | JCS-ready, chiavi ordinate, stesso input → stessi byte | `tm-fold.json` + stamp keys `t_admit < t_event < t_observe < t_present` | map `z,b` → `{"b":1,"z":2}` | **PASS** |
| TM-006 | freeze admission/action/json/a3ui/renderers/broker/agent/showcase; no `now()` | `git diff` + ArchUnit + source walk | dipendenze action/runtime/world | **PASS** |

Gate:

```
./gradlew :core:temporal:test --offline
```

---

## Vietato (verificato)

`Instant.now` / `now()` assenti in `src/main`. Admission/action/json non toccati. Fold senza confidence combinata. Ordinamento indipendente dall'arrivo. Niente push.

---

## Tag

`core-temporal-v0.1` solo a gate verde. Niente push.

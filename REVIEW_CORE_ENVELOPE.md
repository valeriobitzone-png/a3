# REVIEW_CORE_ENVELOPE

AUDIT-FIRST. Protocol: FASE CORE-ENVELOPE — byte-identity cross-language via JCS + CloudEvents. Nuovo modulo `:core:envelope`. Consuma `:core:temporal` (`TemporalStamp`) e `:core:truth` (`TruthBearer`) senza toccarli. Frozen: `core/admission`, `core/action`, `core/json`, `core/temporal`, `core/truth`, `a3ui`, `renderers`, `broker`, `agent`, `showcase`. Niente push.

**Contratto:** lo stesso messaggio a3 produce gli stessi byte su ogni implementazione. JCS (RFC 8785, Erdtman `JsonCanonicalizer`) è la canonicalization. CloudEvents 1.0 è l'inviluppo. `id` è fingerprint SHA-256 del payload, non UUID.

---

## Freeze (EN-008)

```
git diff --stat -- core/admission core/action core/json core/temporal core/truth a3ui renderers broker agent showcase
```

vuoto. Solo `:core:envelope` + `settings.gradle.kts` (`include(":core:envelope")`) + questo REVIEW.

---

## Inviluppo (CloudEvents 1.0)

| Attributo | Valore |
|-----------|--------|
| `specversion` | `"1.0"` |
| `type` | es. `a3.belief.admitted` |
| `source` | URI `urn:a3:source:{source_id}` |
| `id` | SHA-256 hex (64 lowercase) di `JCS(payload)` |
| `time` | ISO-8601 = `t_present` (input al confine, mai clock) |
| `datacontenttype` | `application/json` |
| `subject` | stringa non vuota |
| `data` | `EnvelopePayload` |

`EnvelopePayload`:

| Campo | Origine |
|-------|---------|
| `temporal` | `TemporalStamp` → `t_event`, `t_observe`, `t_admit`, `t_present` |
| `truth` | `TruthBearer` → `truth_class`, `provenance`, `ref?` |
| `content` | dato applicativo |
| `fold_ref` | SHA-256 di JCS del fold di riferimento; omesso se assente |

Schema CE 1.0 in-modulo: `core/envelope/src/main/resources/a3/envelope/cloudevents-1.0.json` (`:core:json` resta frozen).

---

## JCS (RFC 8785)

Autorità: `io.github.erdtman:java-json-canonicalization:1.1` (`org.erdtman.jcs.JsonCanonicalizer`). I valori Kotlin passano da `CanonicalJson.encode` (renderer frozen di Instant/enum/map) e poi da JCS. Hash e lock bytes sono JCS, non un dialetto privato.

`event.id = SHA-256(JCS(EnvelopePayload))`. Stesso payload → stesso id. Byte di contenuto diversi (incluso whitespace in una stringa) → id diverso. JSON pretty-printed dello stesso evento → stesso id dopo parse.

---

## Fixture

Corpus allineato a temporal/truth: `t0 = 2026-08-27T08:00:00Z`, `t_present = 2026-08-27T11:00:00Z`, subject `trip.milano`, source `train`, type `a3.belief.admitted`, truth FACT / OBSERVED_SIGNED / `ver-1`, content `{depart: 09:30}`, `fold_ref` = SHA-256 JCS di `tm-fold.json`.

---

## Vettori lock (2ª impl.)

UTF-8 compact, niente newline finale. Rigenerare solo con `EN_DUMP=1`.

| File | Ruolo | SHA-1 | SHA-256 |
|------|-------|-------|---------|
| `core/envelope/src/test/resources/envelope-rfc8785.json` | vettore RFC 8785 (Erdtman) | `616a3ff076f6ed2fbf55bbc2bcd2248767df657b` | `2d5e01a318d0f0879ab568c4be289c8b1f64ef8921a53c6277d5e069978baacb` |
| `core/envelope/src/test/resources/envelope-payload.json` | payload canonico | `205c0f8dce6ddaf2b353ba8c7d82bbe83f2e19c3` | `477e868489f5c48d138e4c084e9bf13a40ed66390b964365869f7578dfa2e75a` |
| `core/envelope/src/test/resources/envelope-event.json` | CloudEvent canonico | `0fe78e8baf849562f58f1bae4e6703dc03bfdd78` | `a0cbf887413c9825638bd410da5689a217d895b0a391b367aa3c7cb003f0c07e` |
| `core/envelope/src/test/resources/envelope-hashes.json` | sha256 dei tre + `event_id` | `2129d913ad2ffe865e80a98a8a47dd1b00fd5b8b` | `2a318e245354fc67d566869abe6580f9d4c524b8e986efc0ce0fa1f55d9cdc87` |

`event_id` (fingerprint del payload) = `477e868489f5c48d138e4c084e9bf13a40ed66390b964365869f7578dfa2e75a`  
`fold_ref` = SHA-256 JCS di `tm-fold.json` = `f80b9b513fc928d11e8aceb66a29d7cfb7540a0bb8451c9017630b105602e9f5`

Vettore RFC 8785 (byte-identico all'appendix A):

```
{"literals":[null,true,false],"numbers":[333333333.3333333,1e+30,4.5,0.002,1e-27],"string":"€$\u000f\nA'B\"\\\\\"/"}
```

EN-007: `JCS(tm-order.json)`, `JCS(tm-dedup.json)`, `JCS(tm-fold.json)`, `JCS(truth-vectors.json)` sono byte-identici ai file lock di temporal/truth (già compact TreeMap).

Una 2ª implementazione deve riprodurre i byte di `envelope-*.json` e lo stesso `event_id`.

---

## Tabella audit — EN-001..008

| Test | Invariante | Percorso | Negativo | PASS |
|------|------------|----------|----------|------|
| EN-001 | JCS RFC 8785 | Erdtman su sample RFC + stamp/truth Kotlin vs lock temporal/truth | chiavi disordinate / spazi → stessi byte canonici | **PASS** |
| EN-002 | id deterministico | `pack` ×2 = stesso SHA-256; pretty JSON = stesso id | stringa con whitespace extra → id diverso; non UUID | **PASS** |
| EN-003 | CloudEvents 1.0 | schema required `specversion,id,source,type`; `time` = `t_present` | `specversion` ≠ 1.0 → `EnvelopeReject` | **PASS** |
| EN-004 | 4 tempi | parse → `t_event/observe/admit/present` uguali | — | **PASS** |
| EN-005 | truth | parse → `truthClass+provenance+ref`; `fold_ref` omesso se null | — | **PASS** |
| EN-006 | commutatività | chiavi JSON reverse-sorted → stesso canonico e stesso id | ordine ingresso ≠ byte d'ingresso, = JCS | **PASS** |
| EN-007 | interoperabilità | parse lock tm-* e truth-vectors → JCS = file | — | **PASS** |
| EN-008 | freeze | git diff frozen vuoto; ArchUnit no `now()` / no `UUID.randomUUID` | niente dipendenze action/runtime/a3ui/renderers/broker/agent/showcase | **PASS** |

Gate:

```
./gradlew :core:envelope:test --offline --no-daemon
```

---

## Vietato (verificato)

UUID random come id. JSON senza canonicalization. JCS custom (si usa Erdtman RFC 8785). Modifiche a temporal/truth/admission/action/json/a3ui/renderers/broker/agent/showcase. `Instant.now()`. Niente push.

---

## Tag

`core-envelope-v0.1` solo a gate verde. Niente push.

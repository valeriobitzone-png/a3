# REVIEW_CONFORMANCE_A3EP

AUDIT-FIRST. Protocol: FASE CONFORMANCE — suite pubblica A3-EP. Nuovo: `:conformance`. Frozen: `core/`, `broker/`, `agent/`, `a3ui/`, `renderers/`, `launcher/`, `adapters/`. Niente push. Tag `conformance-v0.1` solo a gate verde.

**Contratto:** i test sono di categoria. Un producer che viola un MUST fallisce. Vettori = lock CORE, SHA-256 (non SHA-1). `event_id` = `477e868489f5c48d138e4c084e9bf13a40ed66390b964365869f7578dfa2e75a`.

---

## Freeze (CS-009)

```
git diff --stat -- core broker agent a3ui renderers launcher adapters
```

vuoto. Porcelain nuovo: `conformance/`, `settings.gradle.kts` (`include(":conformance")`), allowlist spec, questo REVIEW.

---

## Esecuzione

```
./gradlew :conformance:test
python3 conformance/src/test/python/test_conformance.py
```

Kotlin `BUILD SUCCESSFUL`. Python:

```
PASS CS-001 vectors=10 sha256
PASS CS-002 CF-001..CF-009 reject
PASS CS-003 RFC8785 bytes=116
PASS CS-004 event_id=477e868489f5c48d138e4c084e9bf13a40ed66390b964365869f7578dfa2e75a
PASS CS-005 permutations=24 tie-break
PASS CS-006 scores=[0.6, 0.565703578169089, 0, 1, 0.4, 0]
PASS CS-007 python parity
PASS CS-008 README
PASS CS-009
```

---

## Fixture di categoria (CS-002)

| Code | File | Reject esplicito |
|------|------|------------------|
| CF-001 | `receipt-fact-violation.json` | `CF-001: FACT on receipt (exit 0, print SUCCESS)` |
| CF-002 | `sandbox-fact-violation.json` | `CF-002: FACT on sandbox (not REAL): FACT requires real admitted environment` |
| CF-003 | `hypothesis-promotion-violation.json` | `CF-003: HYPOTHESIS promoted to FACT without VerificationAdmitted` |
| CF-004 | `attester-requester-violation.json` | `CF-004: attester_id equals requester_id on irreversible action` |
| CF-005 | `unknown-invention-violation.json` | `CF-005: UNKNOWN with invented conclusion (proposition not null)` |
| CF-006 | `history-fold-violation.json` | `CF-006: fold applied to causal history (not confidence/projection)` |
| CF-007 | `compensating-mean-violation.json` | `CF-007: confidence aggregated with compensating average (claimed=0.68 lawful=0.0)` |
| CF-008 | `envelope-id-violation.json` | `CF-008: envelope id ≠ SHA-256(JCS(payload)): id is not SHA-256 of JCS payload` |
| CF-009 | `order-tiebreak-violation.json` | `CF-009: order without tie-break (source_id, seq); claimed=[zeta, alpha] lawful=[alpha, zeta]` |

Nessun silenzio. Nessun mock. Nessun happy-path senza categoria.

---

## Tabella audit — CS-001..009

| Test | Invariante | Percorso | PASS |
|------|------------|----------|------|
| CS-001 | SHA-256 vettori = attesi; byte-identici a CORE | `VectorLockTest` + `vector-sha256.json` | **PASS** |
| CS-002 | CF-001..CF-009 → `ConformanceReject(code)` | `CategoryViolationTest` + 9 fixture | **PASS** |
| CS-003 | RFC 8785 appendix A byte-identico | `RFC8785Test` Erdtman `Jcs.ofJson` | **PASS** |
| CS-004 | id canonico = `event_id_expected` | `EventIdTest` + `envelope-payload.json` | **PASS** |
| CS-005 | ordine totale; shuffle → stesso output; tie-break | `OrderingTieBreakTest` 24 perm | **PASS** |
| CS-006 | (min, ⊤) commutativo e associativo | `ConfidenceMonoidTest` su confidence-vectors | **PASS** |
| CS-007 | Python legge gli stessi vettori, stessa semantica | `test_conformance.py` | **PASS** |
| CS-008 | README eseguibile; add impl Kotlin/TS/Go/Python; CORE vs EXTENSION; report | `conformance/README.md` | **PASS** |
| CS-009 | freeze frozen trees | git diff vuoto su core/broker/agent/a3ui/renderers/launcher/adapters | **PASS** |

Gate: **verde**. Tag locale `conformance-v0.1`. Niente push.

---

## Vietato (rispettato)

- Nessun happy-path senza categoria.
- Nessun mock.
- Nessun vettore diverso dal lock CORE.
- Nessuna prosa ispirazionale nel README.
- Nessuna modifica ai moduli congelati.
- Nessun push.

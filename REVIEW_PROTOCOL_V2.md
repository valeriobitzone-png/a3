# REVIEW_PROTOCOL_V2

AUDIT-FIRST. Protocol: FASE PROTOCOL-V2 — lock v2 coordinati, registro type `io.a3ep.*`, attester ≠ requester. Scongelati: `:core:envelope`, `:core:confidence`, `:core:truth`, `:conformance`. Frozen: admission, action, json, temporal, a3ui, renderers, broker, agent, launcher, overlay, showcase. Niente push. Tag `protocol-v2-v0.1` solo a gate verde. Rebase a3-ts = slice R3b, non eseguita qui.

**Contratto:** A3-EP v0.2. Parser accetta `a3.*` e normalizza a `io.a3ep.*`. Output v2 emette solo `io.a3ep.*`. `EnvelopePayload.attestation` obbligatorio in v2; assente in v1 → legacy, nessuna validazione attester. MUST: `attester_id ≠ requester_id` su `io.a3ep.action.authorized`. Vettori v1 intatti in `conformance/vectors/v1/`.

---

## Identità

| Lock | `event_id` = SHA-256(JCS(payload)) |
|------|------------------------------------|
| v1 | `477e868489f5c48d138e4c084e9bf13a40ed66390b964365869f7578dfa2e75a` |
| v2 | `1fec213fbaf6d420cf9ff95c51c022c4cdfb1f43fabcf82647e03c03f92f2b7b` |

v2 `type` = `io.a3ep.belief.admitted`. v2 attestation = `{attester_id: urn:a3:party:attester, requester_id: urn:a3:party:requester}`.

---

## SHA-256 vettori v2

| File | SHA-256 |
|------|---------|
| tm-order.json | `e805711fc39d48a59b47bfdd147737016db56a3a68511f27229e769691378a6e` |
| tm-dedup.json | `b6b05fefb45b1f9ff2fc882d16eb5a1f0b1cf96e6d8455070836d472e333e0ba` |
| tm-fold.json | `f80b9b513fc928d11e8aceb66a29d7cfb7540a0bb8451c9017630b105602e9f5` |
| truth-vectors.json | `1ddb48779a470fd65adc59a5e0245767f07bd4ea91ad70b7c3e10afbdebab5d6` |
| envelope-rfc8785.json | `2d5e01a318d0f0879ab568c4be289c8b1f64ef8921a53c6277d5e069978baacb` |
| envelope-payload.json | `1fec213fbaf6d420cf9ff95c51c022c4cdfb1f43fabcf82647e03c03f92f2b7b` |
| envelope-event.json | `fa6e007a23751ad55c22291b64982f0d7c8287eb5723b446a3a5fd72c470e939` |
| envelope-hashes.json | `d27e67f05719b77daeb14a4d219a87cb332998fe2c6d163571f35e7b90d76ff5` |
| confidence-vectors.json | `25efbc9f1b3730658c34502f2564d18a8aad04c1ee202b672be4b020917fddee` |
| event_id_expected.txt | `c7cb220cb548ecb3be575faecac6d0d7e57cc18a785727732e5570c21bb68550` |

Manifest: `conformance/vectors/v2/vector-sha256.json`. CORE lock byte-identico a `core/envelope/src/test/resources/envelope-{event,payload,hashes}.json`.

---

## Freeze (PV-010)

```
git diff --stat -- core/admission core/action core/json core/temporal a3ui renderers broker agent launcher overlay showcase
```

vuoto. Porcelain consentito: `core/envelope/`, `conformance/`, `spec/`, `REVIEW_PROTOCOL_V2.md`.

---

## Esecuzione

```
./gradlew :core:envelope:test :conformance:test --no-daemon -Pkotlin.compiler.execution.strategy=in-process
python3 spec/test_spec.py
python3 spec/test_a3ui_spec.py
python3 conformance/src/test/python/test_conformance.py
```

Kotlin `BUILD SUCCESSFUL`. Envelope PV_001..004 PASS. Conformance ProtocolV2Test + VectorLockTest PASS. Python:

```
PASS CS-001 v1+v2 sha256
PASS PV-006 v1 intact
PASS PV-007 v2 sha declared
PASS CS-002 CF-001..CF-009 reject
PASS CS-003 RFC8785 bytes=116
PASS CS-004 event_id_v1=477e868489f5c48d138e4c084e9bf13a40ed66390b964365869f7578dfa2e75a
PASS PV-001 io.a3ep.belief.admitted
PASS PV-003 attestation present
PASS PV-005 event_id_v2=1fec213fbaf6d420cf9ff95c51c022c4cdfb1f43fabcf82647e03c03f92f2b7b
PASS CS-005 permutations=24 tie-break
PASS CS-006 scores=[0.6, 0.565703578169089, 0, 1, 0.4, 0]
PASS CS-007 python parity
PASS PV-009 python v2
PASS CS-008 README
PASS CS-009
PASS PV-010
PASS PV-002 v1 input type a3.* preserved on disk; parser normalizes in Kotlin
PASS PV-008 SPEC_A3-EP 0.2.0
```

Kotlin PV-002: parse di `conformance/vectors/v1/envelope-event.json` (`type` = `a3.belief.admitted`) → `io.a3ep.belief.admitted`. CF-004: `parseEvent` su envelope `io.a3ep.action.authorized` con `attester_id = requester_id = urn:a3:party:alice` → `EnvelopeReject` wrappato `CF-004`.

---

## Tabella audit — PV-001..010

| Test | Invariante | Percorso | PASS |
|------|------------|----------|------|
| PV-001 | output v2 `type` = `io.a3ep.*` | `EnvelopeTest` + `ProtocolV2Test` + `envelope-event.json` v2 | **PASS** |
| PV-002 | input `a3.*` → output `io.a3ep.*` | parse v1 lock; `pack(a3.belief.admitted, attestation)` | **PASS** |
| PV-003 | v2 payload ha `attester_id` + `requester_id` | `envelope-payload.json` v2 | **PASS** |
| PV-004 | `attester_id = requester_id` su irreversibile → reject | CF-004 fixture + `validateCloudEvent` | **PASS** |
| PV-005 | `event_id_v2` = SHA-256(JCS(payload v2)) | `1fec213fbaf6d420cf9ff95c51c022c4cdfb1f43fabcf82647e03c03f92f2b7b` | **PASS** |
| PV-006 | lock v1 intatti; test v1 passano | `conformance/vectors/v1/` = HEAD corpus | **PASS** |
| PV-007 | lock v2 generati; SHA-256 dichiarati | `conformance/vectors/v2/vector-sha256.json` | **PASS** |
| PV-008 | spec 0.2.0, registro + attestation | `spec/SPEC_A3-EP.md` | **PASS** |
| PV-009 | Python v2 | `test_conformance.py` | **PASS** |
| PV-010 | freeze moduli congelati | git diff vuoto (lista sopra) | **PASS** |

Gate: **verde**. Tag locale `protocol-v2-v0.1`. Niente push. Rebase a3-ts non eseguito.

---

## Istruzioni rebase a3-ts (R3b, non questa slice)

File: `conformance/vectors/v2/README-REBASE.md`.

1. Puntare i golden a `conformance/vectors/v2/`.
2. Emmettere `io.a3ep.*` in output. Accettare `a3.*` in parse e normalizzare.
3. Mettere `attestation` sui payload lock v2. Omettere su parse v1 senza validare attester.
4. Reject se `attester_id == requester_id` e type = `io.a3ep.action.authorized`.
5. Rigenera i tuoi test contro v2 e verifica byte-identity di `envelope-event.json`, `envelope-payload.json`, e `event_id` = `1fec213fbaf6d420cf9ff95c51c022c4cdfb1f43fabcf82647e03c03f92f2b7b`.
6. Tenere `conformance/vectors/v1/` come corpus backward-compat (`event_id` v1 = `477e868489f5c48d138e4c084e9bf13a40ed66390b964365869f7578dfa2e75a`).

---

## Vietato (rispettato)

- Lock v1 non cancellati.
- Nessun attester = requester silenzioso su irreversibile.
- Nessun `a3.*` in output v2.
- Moduli congelati non toccati.
- Rebase a3-ts non eseguito.
- Nessun push.

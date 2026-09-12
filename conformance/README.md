# A3-EP conformance

Category suite for SPEC_A3-EP 0.1.0. A producer that violates a MUST or MUST NOT fails. Vectors are the CORE lock files, hashed SHA-256 (not SHA-1).

## Run

From the repository root:

```
./gradlew :conformance:test
python3 conformance/src/test/python/test_conformance.py
```

Kotlin exercises the frozen `:core:*` reference. Python reads the same files under `conformance/vectors/` and `conformance/fixtures/` and checks the same codes.

## Vectors

Import path: `conformance/vectors/`. SHA-256 table: `conformance/vectors/vector-sha256.json`.

| File | SHA-256 |
|------|---------|
| tm-order.json | `e805711fc39d48a59b47bfdd147737016db56a3a68511f27229e769691378a6e` |
| tm-dedup.json | `b6b05fefb45b1f9ff2fc882d16eb5a1f0b1cf96e6d8455070836d472e333e0ba` |
| tm-fold.json | `f80b9b513fc928d11e8aceb66a29d7cfb7540a0bb8451c9017630b105602e9f5` |
| truth-vectors.json | `1ddb48779a470fd65adc59a5e0245767f07bd4ea91ad70b7c3e10afbdebab5d6` |
| envelope-rfc8785.json | `2d5e01a318d0f0879ab568c4be289c8b1f64ef8921a53c6277d5e069978baacb` |
| envelope-payload.json | `477e868489f5c48d138e4c084e9bf13a40ed66390b964365869f7578dfa2e75a` |
| envelope-event.json | `a0cbf887413c9825638bd410da5689a217d895b0a391b367aa3c7cb003f0c07e` |
| envelope-hashes.json | `2a318e245354fc67d566869abe6580f9d4c524b8e986efc0ce0fa1f55d9cdc87` |
| confidence-vectors.json | `25efbc9f1b3730658c34502f2564d18a8aad04c1ee202b672be4b020917fddee` |
| event_id_expected.txt | `97a3bb3a4ad58c8bd47f22ebe8292b5c86903c6956d73009f2a827d40cf38894` |

`event_id` MUST equal `477e868489f5c48d138e4c084e9bf13a40ed66390b964365869f7578dfa2e75a` = SHA-256 of `envelope-payload.json` (already JCS).

`rfc8785-input.json` is the RFC 8785 appendix A input. `envelope-rfc8785.json` is the byte-identical canonical form.

## Category fixtures

Reject codes are the exception prefix. Silence is not a pass.

| Code | File | MUST NOT |
|------|------|----------|
| CF-001 | `fixtures/receipt-fact-violation.json` | FACT on receipt (exit 0, print SUCCESS) |
| CF-002 | `fixtures/sandbox-fact-violation.json` | FACT on SANDBOX |
| CF-003 | `fixtures/hypothesis-promotion-violation.json` | HYPOTHESIS → FACT without VerificationAdmitted |
| CF-004 | `fixtures/attester-requester-violation.json` | attester_id = requester_id on irreversible |
| CF-005 | `fixtures/unknown-invention-violation.json` | UNKNOWN with a non-null invented proposition |
| CF-006 | `fixtures/history-fold-violation.json` | fold replacing causal history |
| CF-007 | `fixtures/compensating-mean-violation.json` | compensating average instead of weighted min |
| CF-008 | `fixtures/envelope-id-violation.json` | envelope id ≠ SHA-256(JCS(payload)) |
| CF-009 | `fixtures/order-tiebreak-violation.json` | order without (source_id, seq) tie-break |

Kotlin: `ConformanceReject(code, reason)` message `CODE: reason`. Python: same class and message shape.

## Add an implementation

Keep `conformance/vectors/` byte-identical. Do not substitute local JSON.

1. Hash every lock file with SHA-256. Fail if a digest differs from `vector-sha256.json`.
2. Canonicalize `rfc8785-input.json` with RFC 8785. Fail if bytes differ from `envelope-rfc8785.json`.
3. Compute `id = SHA-256(JCS(payload))`. Fail if it differs from `event_id_expected.txt`.
4. Sort with key `(t_observe, source_id, seq)`, residual `(subject, key)`. Shuffled input MUST yield the same output.
5. Fold confidence with `(min, ⊤)` where ⊤ = 1. Fail a compensating mean.
6. Load each CF-001..CF-009 fixture and reject with that code. Do not admit the producer output.

### Kotlin

Depend on `:core:envelope`, `:core:temporal`, `:core:truth`, `:core:confidence`. Call `CategoryJudge.judgeFile` or reimplement the same rejects. Run `./gradlew :conformance:test`.

### TypeScript

Use the same vector bytes. SHA-256 via `crypto.subtle` or Node `crypto`. RFC 8785 via a JCS library that matches Erdtman on appendix A. Reject fixtures with `Error` whose `code` is `CF-00N`.

### Go

SHA-256 via `crypto/sha256`. RFC 8785 via a JCS package that matches `envelope-rfc8785.json`. Sort with `sort.SliceStable` on the protocol key. Reject with a typed error carrying `CF-00N`.

### Python

Copy `conformance/src/test/python/test_conformance.py` and keep the vector paths. `python3 conformance/src/test/python/test_conformance.py` is the parity gate.

## CORE vs EXTENSION

CORE is the strict subset in SPEC_A3-EP section 9: envelope, truth, ordering, confidence, postcondition.

EXTENSION is canonical projection, overlay, sensory, and agent. An extension MUST NOT relax a CORE MUST or MUST NOT. `:agent` is an application, not CORE.

A report MUST declare `profile: CORE` or `profile: CORE+EXTENSION` and list which extensions are claimed.

## Submit a report

File a report with this block filled. Attach the test log, not a screenshot of a happy path.

```
implementation: <name>
language: Kotlin | TypeScript | Go | Python | other
version: <git commit or release>
profile: CORE | CORE+EXTENSION
extensions: none | projection, overlay, sensory, agent
vectors: SHA-256 match vector-sha256.json: yes/no
CS-001 VectorLock: PASS/FAIL
CS-002 CategoryViolation CF-001..CF-009: PASS/FAIL
CS-003 RFC8785 appendix A: PASS/FAIL
CS-004 event_id: PASS/FAIL
CS-005 OrderingTieBreak: PASS/FAIL
CS-006 ConfidenceMonoid: PASS/FAIL
log: <path or paste>
```

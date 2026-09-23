# A3-EP conformance

Category suite for SPEC_A3-EP 0.2.0 (lock v2). A producer that violates a MUST or MUST NOT fails. Vectors are the CORE lock files, hashed SHA-256 (not SHA-1).

Lock v1 lives in `conformance/vectors/v1/` (byte-identical to the 0.1.0 corpus). Lock v2 lives in `conformance/vectors/v2/`. a3-ts rebase instructions: `conformance/vectors/v2/README-REBASE.md`.

## Run

From the repository root:

```
./gradlew :conformance:test
python3 conformance/src/test/python/test_conformance.py
```

Kotlin exercises `:core:*`. Python reads `conformance/vectors/v2/` (and v1 for backward checks) plus `conformance/fixtures/` and checks the same codes.

## Vectors

v1: `conformance/vectors/v1/` + `vector-sha256.json`. v2: `conformance/vectors/v2/` + `vector-sha256.json`.

`event_id` v1 MUST equal `477e868489f5c48d138e4c084e9bf13a40ed66390b964365869f7578dfa2e75a`.

`event_id` v2 MUST equal `1fec213fbaf6d420cf9ff95c51c022c4cdfb1f43fabcf82647e03c03f92f2b7b`.

v2 `type` MUST be `io.a3ep.belief.admitted`. v2 payload MUST carry `attestation`.

`rfc8785-input.json` is the RFC 8785 appendix A input. `envelope-rfc8785.json` is the byte-identical canonical form.

## Category fixtures

Reject codes are the exception prefix. Silence is not a pass.

| Code | File | MUST NOT |
|------|------|----------|
| CF-001 | `fixtures/receipt-fact-violation.json` + `fixtures/receipt-forms.json` | FACT from an execution receipt, whatever the receipt contains (see below) |
| CF-002 | `fixtures/sandbox-fact-violation.json` | FACT on SANDBOX |
| CF-003 | `fixtures/hypothesis-promotion-violation.json` | HYPOTHESIS → FACT without VerificationAdmitted |
| CF-004 | `fixtures/attester-requester-violation.json` | attester_id = requester_id on irreversible |
| CF-005 | `fixtures/unknown-invention-violation.json` | UNKNOWN with a non-null invented proposition |
| CF-006 | `fixtures/history-fold-violation.json` | fold replacing causal history |
| CF-007 | `fixtures/compensating-mean-violation.json` | compensating average instead of weighted min |
| CF-008 | `fixtures/envelope-id-violation.json` | envelope id ≠ SHA-256(JCS(payload)) |
| CF-009 | `fixtures/order-tiebreak-violation.json` | order without (source_id, seq) tie-break |

Kotlin: `ConformanceReject(code, reason)` message `CODE: reason`. Python: same class and message shape.

### CF-001 is a semantic property, not a string match

The canonical invariant is the one in SPEC_A3-EP sections 2, 3 and 10:

```
execution receipt ≠ fact
```

An execution receipt says that a call was dispatched and answered. It never says that the world changed. A receipt is OBSERVATION whatever it contains, and a claim of FACT whose basis is a receipt is rejected with CF-001 whatever it contains. The only path to FACT is a VerificationAdmitted in the REAL environment (TR-001).

`fixtures/receipt-fact-violation.json` (exit 0, printed `SUCCESS`) is one instance of the rule, not the rule. `fixtures/receipt-forms.json` holds 18 receipt forms: process exit codes with `SUCCESS`, `OK`, `done`, empty, padded or JSON output, non-zero exits, structured tool results, booleans, `null`, HTTP 200/201 bodies, an MCP tool result, and Unicode text. For every form an implementation MUST classify the receipt as OBSERVATION, MUST reject `FACT` (any spelling) with CF-001, and MUST NOT reject `OBSERVATION`. A judge MUST NOT decide from the exit code, the printed text, the status or the payload.

Until 2026-09-23 the Python runner and a3-go rejected CF-001 only for exit 0 and the literal `SUCCESS`: a receipt printing `OK` passed as FACT. CS-010 keeps that literal rule as a negative control and fails if the forms cannot tell it from the law.

## Add an implementation

Keep `conformance/vectors/v2/` byte-identical. Do not substitute local JSON. Keep `conformance/vectors/v1/` for backward tests.

1. Hash every lock file with SHA-256. Fail if a digest differs from `v1/vector-sha256.json` and `v2/vector-sha256.json`.
2. Canonicalize `rfc8785-input.json` with RFC 8785. Fail if bytes differ from `envelope-rfc8785.json`.
3. Compute `id = SHA-256(JCS(payload))`. Fail if v2 differs from `v2/event_id_expected.txt`.
4. Sort with key `(t_observe, source_id, seq)`, residual `(subject, key)`. Shuffled input MUST yield the same output.
5. Fold confidence with `(min, ⊤)` where ⊤ = 1. Fail a compensating mean.
6. Load each CF-001..CF-009 fixture and reject with that code. Do not admit the producer output.
7. Load `fixtures/receipt-forms.json`. Every form MUST classify as OBSERVATION; `FACT` MUST be rejected with CF-001 and `OBSERVATION` MUST be admitted, for every form (CS-010).

### Kotlin

Depend on `:core:envelope`, `:core:temporal`, `:core:truth`, `:core:confidence`. Call `CategoryJudge.judgeFile` or reimplement the same rejects. Run `./gradlew :conformance:test`.

### TypeScript

Use the same vector bytes. SHA-256 via `crypto.subtle` or Node `crypto`. RFC 8785 via a JCS library that matches Erdtman on appendix A. Reject fixtures with `Error` whose `code` is `CF-00N`.

### Go

SHA-256 via `crypto/sha256`. RFC 8785 via a JCS package that matches `envelope-rfc8785.json`. Sort with `sort.SliceStable` on the protocol key. Reject with a typed error carrying `CF-00N`.

### Python

Copy `conformance/src/test/python/test_conformance.py` and keep the vector paths. `python3 conformance/src/test/python/test_conformance.py` is the parity gate. It also runs CS-010 against `python/a3ep`, the standard-library Python implementation of the truth element.

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
CS-010 ReceiptIsNotFact (receipt-forms.json): PASS/FAIL
log: <path or paste>
```

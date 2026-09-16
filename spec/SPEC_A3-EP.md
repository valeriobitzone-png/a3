<!-- SPDX-License-Identifier: CC-BY-4.0 -->
# A3-EP: Epistemic Hygiene Contract

```
Document: SPEC_A3-EP
Version: 0.2.0
Status: Proposed Standard
Intended category: Standards Track
Obsoletes: SPEC_A3-EP 0.1.0
```

The key words MUST, MUST NOT, SHOULD, and MAY in this document MUST be interpreted as in [RFC 2119]. Sections 1 through 10 MUST be treated as normative. Appendices MUST be treated as non-normative unless a sentence in an appendix uses MUST, MUST NOT, SHOULD, or MAY. For SP-001, a page MUST be 500 words of sections 1 through 10, and the normative body MUST NOT exceed 20 pages.

---

## 1. Scope

An implementation MUST treat A3-EP as a contract of epistemic hygiene over admitted propositions, authorized actions, and environment postconditions.

An implementation MUST NOT treat A3-EP as an orchestrator.

An implementation MUST NOT treat A3-EP as a memory store.

An implementation MUST NOT treat A3-EP as an LLM runtime.

An implementation MUST NOT treat A3-EP as a transport protocol.

An implementation MUST apply A3-EP independently of any particular process manager, database, model vendor, or byte carrier.

The module `:agent` MUST be treated as an application of A3-EP.

An implementation MUST NOT treat `:agent` as part of this protocol.

---

## 2. Planes

An implementation MUST keep three planes distinct: BELIEF, ACTION, and ENVIRONMENT.

An implementation MUST record BELIEF as admitted propositions that carry truth class and provenance.

An implementation MUST record ACTION as authorization, command, and dispatch records that do not establish an external-world claim.

An implementation MUST record ENVIRONMENT as postconditions observed in the real environment.

An implementation MUST NOT merge BELIEF, ACTION, and ENVIRONMENT into a single state object.

An implementation MUST NOT copy an ACTION receipt into BELIEF as FACT.

An implementation MUST NOT treat an ENVIRONMENT postcondition as an ACTION completion flag.

A BELIEF writer MUST NOT hold ACTION phase as a field of belief state.

An ACTION state object MUST NOT hold a BELIEF snapshot as a field of action state.

---

## 3. Truth class and provenance

Every proposition MUST carry a truth class that is exactly one of FACT, OBSERVATION, HYPOTHESIS, UNKNOWN.

Every proposition MUST carry a provenance that is exactly one of OBSERVED_SIGNED, DERIVED_MODEL, INFERRED, HUMAN_ADMITTED.

An implementation MUST reject a proposition that omits truth class.

An implementation MUST reject a proposition that omits provenance.

An implementation MUST NOT assign a silent default for truth class or provenance.

An implementation MUST NOT promote HYPOTHESIS to FACT without a VerificationAdmitted whose environment is REAL.

An implementation MUST leave a HYPOTHESIS unchanged when VerificationAdmitted is absent.

If VerificationAdmitted.environment is SANDBOX, an implementation MUST NOT emit FACT and SHOULD emit OBSERVATION with ref equal to verificationId.

An implementation MUST NOT emit FACT from an execution receipt.

An implementation MUST NOT emit FACT from a sandbox verification.

If environment is REAL and VerificationAdmitted is present, an implementation MAY emit FACT with provenance OBSERVED_SIGNED and ref equal to verificationId.

A process exit code MUST be classified as OBSERVATION, not FACT.

Printed SUCCESS from a process MUST be classified as OBSERVATION, not FACT.

---

## 4. UNKNOWN

UNKNOWN MUST be a first-class truth class.

When evidence is insufficient, an implementation MUST emit UNKNOWN.

An implementation MUST NOT invent a conclusion in place of UNKNOWN.

An implementation MUST NOT coerce UNKNOWN to FACT.

An implementation MUST NOT coerce UNKNOWN to a success ACTION phase.

An implementation MUST NOT present UNKNOWN as BELIEVED.

A confidence score of 0 MUST map to confidence category UNKNOWN, independent of truth class.

---

## 5. Time, order, history, and fold

Every admitted envelope MUST carry four instants: t_event, t_observe, t_admit, t_present.

An implementation MUST NOT read a clock to fill these instants.

t_observe MUST NOT precede t_event.

t_admit MUST NOT precede t_observe.

t_present MUST be an explicit as-of instant and MAY equal t_admit.

CloudEvents `time` MUST equal t_present.

The protocol order key MUST be the triple (t_observe, source_id, seq).

An implementation MUST sort by t_observe ascending, then by source_id lexicographic ascending, then by seq numeric ascending.

If two records share the same (t_observe, source_id, seq), an implementation SHOULD apply a residual lexicographic tie-break on (subject, key) so equal order keys still commute.

History MUST be an append-only log ordered by that rule.

An implementation MUST NOT fold the history.

An implementation MUST NOT replace the history with a derived view.

Fold MUST be defined only on (1) confidence and (2) a canonical projection of state.

Confidence fold MUST be the commutative monoid (min, ⊤) on [0, 1] with ⊤ = 1.

The combination of scores s1 and s2 MUST be min(s1, s2).

The identity of confidence fold MUST be 1.

A canonical projection of state MAY map the ordered log to current fields per subject.

A canonical projection MUST be derived from the log.

A canonical projection MUST NOT delete, rewrite, or stand in for the history.

---

## 6. Envelope

An implementation MUST emit CloudEvents 1.0 JSON objects.

An implementation MUST include specversion, type, source, and id.

specversion MUST be the string `1.0`.

datacontenttype MUST be `application/json`.

source MUST be a URI.

id MUST be the lowercase hex encoding of SHA-256(JCS(payload)), 64 characters.

JCS MUST be RFC 8785 JSON Canonicalization Scheme.

Lock v2 payload MUST be the CloudEvents `data` object that contains temporal, truth, content, attestation, and optional fold_ref.

Lock v1 payload MUST contain temporal, truth, content, and optional fold_ref.

Lock v1 payload MUST NOT be required to contain attestation.

A parser MUST accept a lock v1 payload that omits attestation and MUST NOT validate attester_id or requester_id when attestation is absent.

An implementation MUST NOT use a random UUID as id.

An implementation MUST NOT use a non-RFC-8785 private canonicalizer for id.

The type registry MUST be the following closed set of protocol types:

| Protocol type | Plane | Lock v1 spelling |
|---------------|-------|------------------|
| `io.a3ep.belief.admitted` | BELIEF | `a3.belief.admitted` |
| `io.a3ep.action.authorized` | ACTION | none in lock v1 |
| `io.a3ep.env.postcondition` | ENVIRONMENT | `a3.observation.admitted` when the payload is a process postcondition |

Lock v2 output MUST set type to a name in that registry.

Lock v2 output MUST NOT set type to an `a3.*` spelling.

A parser MUST accept the lock v1 spellings `a3.belief.admitted` and `a3.observation.admitted`.

A parser MUST normalize `a3.belief.admitted` to `io.a3ep.belief.admitted` in output.

A parser MUST normalize `a3.observation.admitted` to `io.a3ep.env.postcondition` in output.

An implementation MUST treat `a3.belief.admitted` as the lock v1 spelling of `io.a3ep.belief.admitted`.

An implementation MUST treat `a3.observation.admitted` as the lock v1 spelling of `io.a3ep.env.postcondition`.

Lock v2 MUST keep lock v1 byte identity in `conformance/vectors/v1/`.

An implementation MUST NOT introduce additional protocol types in CORE.

---

## 7. Confidence

A confidence assessment MUST use five dimensions: source_reliability, evidence_strength, recency, corroboration, verification.

An implementation MUST reject a vector that omits a dimension.

Each dimension MUST be a finite number in [0, 1].

Aggregation MUST be a declared weighted minimum.

score MUST be min_i (dimension_i * weight_i).

Weights MUST be positive.

If any dimension is 0, score MUST be 0.

An implementation MUST NOT use a compensating average.

An implementation MUST NOT hide weights inside an unnamed mean.

Default weights SHOULD be source_reliability=1.0, evidence_strength=1.0, recency=0.8, corroboration=0.6, verification=0.9 unless a caller supplies another AggregationWeights.

Recency SHOULD be `2^(-(t_present - t_observe) / half_life)` with a declared positive half-life.

Corroboration MUST count distinct source_id values.

Repeating the same source_id MUST NOT increase corroboration.

Confidence fold MUST use the monoid in section 5 and MUST NOT rewrite history.

---

## 8. Attester and requester

Lock v2 EnvelopePayload MUST carry an attestation object with attester_id and requester_id.

attester_id MUST identify the party that attests confidence and truth.

requester_id MUST identify the party that requests the action.

attester_id MUST NOT equal requester_id when the envelope type is `io.a3ep.action.authorized`.

An implementation MUST treat `io.a3ep.action.authorized` as irreversible for that rule.

An implementation MUST reject that envelope when attester_id equals requester_id.

attester_id SHOULD NOT equal requester_id on reversible types.

An implementation MAY allow attester_id to equal requester_id for reversible, non-committing reads.

A parser MUST NOT apply the attester_id inequality when attestation is absent.

---

## 9. CORE profile

The CORE profile MUST be a strict subset of this specification.

CORE MUST consist of exactly these five elements: envelope (section 6), truth (section 3), ordering (section 5), confidence (section 7), postcondition (ENVIRONMENT plane in sections 2 and 6).

A CORE implementation MUST implement those five elements.

A CORE implementation MUST NOT be required to implement canonical projection.

A CORE implementation MUST NOT be required to implement overlay.

A CORE implementation MUST NOT be required to implement sensory.

A CORE implementation MUST NOT be required to implement agent.

Canonical projection, overlay, sensory, and agent MUST be treated as extensions outside CORE.

An extension MUST NOT relax a CORE MUST or MUST NOT.

---

## 10. Governance

This document version MUST be 0.2.0.

After 1.0.0, a breaking change MUST increment the major version.

Before 1.0.0, a breaking change MUST increment the minor version and MUST be recorded in the changelog.

A changelog MUST list added, changed, and removed protocol requirements.

A deprecated type MUST remain accepted for at least one minor version after deprecation is declared.

A CORE lock bump MUST be named lock vN and MUST be referenced from this document.

This version MUST name the CORE lock lock v2.

The 0.2.0 changelog MUST record the type registry rename to `io.a3ep.*` and the attestation field.

An implementation MUST NOT do the following: invent a conclusion when the truth class is UNKNOWN; promote HYPOTHESIS to FACT without VerificationAdmitted in REAL; emit FACT from a receipt or from SANDBOX; fold or rewrite history; merge BELIEF, ACTION, and ENVIRONMENT into one state; aggregate confidence with a compensating average; let attester_id equal requester_id on an irreversible action; emit `a3.*` as lock v2 output type; treat `:agent`, overlay, or sensory as CORE; treat application roles as protocol primitives; break lock v1 byte identity in `conformance/vectors/v1/`.

---

## Appendix A. Application roles (non-normative)

This appendix is not protocol. Council and Critic are application roles that a product MAY assign when applying A3-EP. They are not types, planes, or envelope names. `:agent` is an application that consumes CORE. Overlay and sensory are presentation extensions. Nothing in this appendix authorizes merging planes, folding history, or promoting HYPOTHESIS without VerificationAdmitted.

---

## Appendix B. Document map (non-normative)

Lock v2 artifacts: `core/envelope/src/test/resources/envelope-event.json` and `conformance/vectors/v2/` (`type` = `io.a3ep.belief.admitted`, attestation present, `id` = SHA-256 hex of JCS payload). Lock v1 artifacts remain in `conformance/vectors/v1/` (`type` = `a3.belief.admitted`). `:core:temporal` order key `(t_observe, source_id, seq)`, `:core:truth` promotion law, and `:core:confidence` weighted min are unchanged. R3 in 0.1.0 named this lock v2 rename.

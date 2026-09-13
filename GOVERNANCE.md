# Governance

The key words MUST, MUST NOT, SHOULD, and MAY in this document MUST be interpreted as in [RFC 2119].

This file is the release contract for the A3 repository. Spec text lives in `spec/`. Normative requirements in this file MUST use RFC 2119 keywords and operational rules only.

## 1. Scope

A change to this repository MUST be classified as protocol, spec, lock vector, implementation, or documentation.

Protocol and spec changes MUST follow this file. Implementation tags (`core-*`, `a3ui-*`, renderer tags, and others) MUST NOT be treated as spec versions.

## 2. Semantic versioning (protocol)

The product versions in `CHANGELOG.md` (`0.1.0`, `0.2.0`, …) MUST follow [SemVer 2.0.0](https://semver.org/spec/v2.0.0.html) with the following protocol mapping.

### MAJOR

A MAJOR increment MUST be used when a published consumer can no longer parse or evaluate a previously valid message under the same rules. MAJOR includes, without limitation:

- a breaking change to the envelope (required field added or removed, type registry rename without a documented normalization path, CloudEvents/RFC 8785 identity change);
- a breaking change to truth class or provenance (member removed or meaning changed);
- a breaking change to confidence ordering, axis set, weights, or the declared aggregation (including adding a sixth axis).

A MAJOR MAY remove a field or type that completed the deprecation cycle in section 4.

### MINOR

A MINOR increment MUST be used for a backward-compatible protocol addition. MINOR includes:

- a new a3ui mark;
- a new optional envelope, tuple, or claim field;
- a new lock-vector directory (`v3/`, …) that does not alter published vectors.

A MINOR MUST NOT change the meaning of an existing required field.

### PATCH

A PATCH increment MUST be used for a change that does not alter protocol meaning. PATCH includes:

- a defect fix that restores documented MUST behavior;
- a typo in non-normative text;
- documentation that does not change RFC 2119 requirements.

A PATCH MUST NOT add a required field, remove a field, or change confidence ordering.

## 3. Spec versions

`spec/SPEC_A3-EP.md` and `spec/SPEC_A3UI.md` MUST carry their own `Version:` line. That version MUST be independent of Kotlin module tags and independent of implementation tags.

As of this writing:

- `SPEC_A3-EP` is 0.2.0 (obsoletes 0.1.0);
- `SPEC_A3UI` is 0.1.0.

A spec MINOR or MAJOR MUST be recorded in `CHANGELOG.md`. Editing a frozen spec file is a protocol event, not a PATCH to an implementation module.

## 4. Deprecation

When a field or type is deprecated, the deprecation MUST be declared in `CHANGELOG.md` under Deprecated, with the tag and commit SHA.

A deprecated field or type MUST remain in the published contract for at least two MINOR releases after that declaration. An implementation MUST emit a warning when a deprecated field or type is used.

Removal MUST occur only in a MAJOR release after that interval. A MINOR or PATCH MUST NOT remove a deprecated field or type.

Example: a field deprecated in 0.2.0 MUST still be present in 0.3.0 and 0.4.0. The earliest removal is 1.0.0, or a later MAJOR.

## 5. Lock vectors

A published lock vector MUST be treated as immutable.

`conformance/vectors/v1/` and `conformance/vectors/v2/` MUST NOT be rewritten. A new protocol generation MUST be added as a new directory (`v3/`, `v4/`, …) with new files.

A change that would alter bytes in an existing vector directory MUST be rejected. Tests that hash those files MUST fail if the bytes move.

## 6. Backward compatibility

A parser for version N MUST accept messages of published versions < N that remain in the compatibility window, and MUST normalize them to the current internal form.

Envelope v2 already implements this for type names: input `a3.*` is accepted and normalized to `io.a3ep.*`; v2 output MUST emit `io.a3ep.*` only.

A parser MUST NOT drop a required tuple or envelope field in order to accept a new version. Unknown optional fields SHOULD be preserved or reported; they MUST NOT be silently treated as FACT.

## 7. Release cadence and process

Cadence is not scheduled. A release happens when a protocol or product change requires one.

Every release MUST include:

1. an updated `CHANGELOG.md` with the version, date, and cited tags or SHAs;
2. an annotated git tag for that release;
3. a push of the tag and its commit.

This document does not authorize a push. A phase that forbids push MUST still write the changelog and MAY tag locally.

## 8. Implementation tags

Module tags (`core-v0.5`, `renderer-android-v0.16`, `a3ui-a11y-v0.1`, …) MUST be recorded in `CHANGELOG.md` under the protocol version they belong to. They MUST NOT replace the protocol MAJOR.MINOR.PATCH.

## 9. Divergences

When two implementations disagree, a change MUST declare which implementation is wrong and why. A change MUST NOT silently reconcile the outputs.

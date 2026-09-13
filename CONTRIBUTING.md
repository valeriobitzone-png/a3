# Contributing

The key words MUST, MUST NOT, SHOULD, and MAY in this document MUST be interpreted as in [RFC 2119].

This file is operational. Normative protocol rules are in `spec/` and `GOVERNANCE.md`.

## Build and test

```bash
./gradlew test --no-daemon -Pkotlin.compiler.execution.strategy=in-process
```

Python lock and spec checks live under `spec/` and `conformance/`. A contributor MUST run the suite named by the phase REVIEW file before asking for a tag.

Release rules (semver, deprecation, lock immutability, changelog, tag, push) are in `GOVERNANCE.md`. A contributor MUST NOT tag or push when the phase forbids it.

## Cosa non fare

These constraints are normative for this repository.

1. A contributor MUST NOT present A3 as a truth stack for AGI.
2. A contributor MUST NOT compete with A2A or MCP on transport. A3-EP is not a transport protocol.
3. A contributor MUST NOT add a sixth confidence axis. Axis-set change is MAJOR (`GOVERNANCE.md` §2).
4. A contributor MUST NOT wait for a model to respect A3. Conformance is lock vectors and tests, not model behavior.
5. A contributor MUST NOT use slogans in normative text. Normative text MUST use RFC 2119 keywords and operational rules only.
6. A contributor MUST NOT reconcile implementation divergences in silence. The change MUST declare which implementation is wrong and why.
7. A contributor MUST NOT modify published lock vectors. A new generation MUST be a new directory (`conformance/vectors/v3/`, …).
8. A contributor MUST NOT merge BELIEF, ACTION, and ENVIRONMENT.
9. A contributor MUST NOT promote HYPOTHESIS to FACT without `VerificationAdmitted`.
10. A contributor MUST NOT emit FACT from a receipt or a sandbox.

## Come proporre un'estensione

1. Open an issue whose title starts with `Extension proposal`.
2. Demonstrate that the change is not CORE: it MUST be implementable in a weekend without altering envelope identity, truth class, confidence ordering, or lock vector bytes.
3. Provide at least one fixture and tests of the same category as the existing suite (A3-EP lock, a3ui mark-to-CTA, or the named REVIEW gate).
4. Wait for review. A contributor MUST NOT implement the extension on the default branch before that review.

An extension that fails step 2 is a protocol change and MUST follow `GOVERNANCE.md` (MAJOR or MINOR, changelog, tag).

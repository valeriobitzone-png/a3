# Application concept mapping

This is a traceability map, not a protocol claim. The named application concepts are not A3-EP or A3UI primitives. Council and Critic remain application roles.

| Concept | Real A3 mapping | Status | Boundary |
|---|---|---|---|
| Aura | A3UI renderer chrome and `a3ui-graphics` tokens | CONCEPT_ONLY | Chrome cannot replace a mark or provenance |
| HILLP | A3UI lifecycle/re-binding/extension evidence | CONCEPT_ONLY | No HILLP primitive exists in the catalog |
| Verifier | A3-EP admission, truth, provenance, and conformance judges | VERIFIED | Verification is protocol/application evidence, not model confidence alone |
| Council | No core module; possible application orchestration above A3-EP | PLANNED | Must not become a protocol primitive or FACT authority |
| Critic | No core module; possible application review role | PLANNED | Must emit proposals with provenance, never silently rewrite reality |
| Fabbro cycle | Slice process: spec → implementation → conformance → review → tag | VERIFIED | Publication remains a separate manual act |

## State vocabulary

- **VERIFIED** — backed by executable tests and a recorded review in this repository.
- **CONCEPT_ONLY** — language used for design discussion, with no implementation contract.
- **PLANNED** — possible future application work, not current protocol scope.
- **ABSENT** — no module, primitive, or claim exists.

No mapping in this file authorizes an import, dependency, or naming of an application concept from A3-EP CORE. The seven A3UI primitives remain closed.

# a3-ts rebase onto lock v2

Do not run this rebase in the A3 Kotlin slice. R3b owns a3-ts.

## event_id

| Lock | `id` = SHA-256(JCS(payload)) |
|------|------------------------------|
| v1 | `477e868489f5c48d138e4c084e9bf13a40ed66390b964365869f7578dfa2e75a` |
| v2 | `1fec213fbaf6d420cf9ff95c51c022c4cdfb1f43fabcf82647e03c03f92f2b7b` |

## v2 fixture fields (must collide)

- `type`: `io.a3ep.belief.admitted`
- `attestation.attester_id`: `urn:a3:party:attester`
- `attestation.requester_id`: `urn:a3:party:requester`
- corpus times, subject `trip.milano`, source `train`, content `{depart: 09:30}`, `fold_ref` of `tm-fold.json` unchanged

## SHA-256 of v2 files

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

Canonical table: `vector-sha256.json` in this directory.

## Instructions for a3-ts

1. Point golden files at `conformance/vectors/v2/`.
2. Emit `io.a3ep.*` on output. Accept `a3.*` on parse and normalize.
3. Put `attestation` on lock v2 payloads. Omit it on v1 parse without validating attester.
4. Reject `attester_id == requester_id` when type is `io.a3ep.action.authorized`.
5. Rigenerate i tuoi test contro v2 e verifica byte-identity of `envelope-event.json`, `envelope-payload.json`, and `event_id`.
6. Keep `conformance/vectors/v1/` as the backward-compat corpus.

Parser output MUST NOT keep `a3.belief.admitted` as the emitted type after normalize.

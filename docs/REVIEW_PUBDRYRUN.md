# REVIEW_PUBDRYRUN

Publication dry-run evidence for the six repository family at the pre-publication commits. Clones were created with `git clone` into separate `/tmp/pub-dry-final/<repo>/repo` directories; no repository was made available as an undeclared sibling. Tests may create local build or dependency artifacts in the temporary clones, but no source or lock bytes were changed.

## Audit table

| ID | Requirement | Result | Evidence |
|---|---|---|---|
| DR-001 | Six clean clones executed | PASS | `/tmp/pub-dry-final/{a3,a3-ts,a3-go,a3ui-web,a3ui-cli,a3ui-graphics}/repo` |
| DR-002 | Isolated README-directed result recorded | PASS | Table below; `a3` is KO-dichiarato because its README requires an Android SDK for the full Gradle gate |
| DR-003 | No hidden KO remains | PASS | Missing sibling dependencies were declared and pinned before the final run |
| DR-004 | Dependencies use immutable tags | PASS | `a3@closeout-v1.0`; `a3-ts@prepub-v1.0` where required |
| DR-005 | Lock-vector SHA-256 remains unchanged in clone | PASS | A3 `conformance/vectors/v2/vector-sha256.json`, all 10 entries match |
| DR-006 | Review documents the run and fixes | PASS | This file |

## Final isolated results

| Repository | README-directed command | Result | Dependency declaration |
|---|---|---|---|
| `a3` | `./gradlew test --offline --console=plain` | **KO-dichiarato** | Android SDK is an explicit README requirement; clean clone stopped at SDK discovery (`local.properties` is intentionally not committed) |
| `a3-ts` | `pnpm install --frozen-lockfile && pnpm typecheck && pnpm test` | **OK** | README now clones `a3` beside it and checks out `closeout-v1.0` for INT-009 |
| `a3-go` | `go test ./...` | **OK** | README now clones `a3@closeout-v1.0` and `a3-ts@prepub-v1.0` beside it for GO-009 |
| `a3ui-web` | `npm install --ignore-scripts --no-audit --no-fund && npm run typecheck && npm test` | **OK** | Fixtures and A3UI spec are committed in this repository; no sibling is required |
| `a3ui-cli` | `PYTHONPATH=. uv run --with pytest --python 3.11 pytest -q` plus CLI smoke | **OK** | README now clones `a3` beside it and checks out `closeout-v1.0` for spec and fixtures |
| `a3ui-graphics` | `python3 tests/gs_test.py && python3 tests/consumer_test.py` | **OK** | No sibling repository is required |

The first probe exposed undocumented sibling assumptions in `a3-ts`, `a3-go`, and `a3ui-cli`; those are now explicit in their Get it/Integrate sections and were re-run successfully with pinned tags. The first `a3ui-cli` probe also required the test runner environment; pytest remains a documented test requirement and is supplied in the dry run by `uv` without becoming a runtime dependency.

## Fixes applied

- `a3-ts/README.md`: document the pinned `../a3@closeout-v1.0` dependency used by INT-009.
- `a3-go/README.md`: document pinned `../a3@closeout-v1.0` and `../a3-ts@prepub-v1.0` dependencies used by GO-009.
- `a3ui-cli/README.md`: document the pinned `../a3@closeout-v1.0` dependency used for shared A3UI spec and fixtures.

No implementation logic, fixtures, lock vectors, or generated source files were changed by these fixes.

## Lock-vector SHA-256 evidence

The final clean A3 clone matched every entry in `conformance/vectors/v2/vector-sha256.json`:

| Vector | SHA-256 |
|---|---|
| `confidence-vectors.json` | `25efbc9f1b3730658c34502f2564d18a8aad04c1ee202b672be4b020917fddee` |
| `envelope-event.json` | `fa6e007a23751ad55c22291b64982f0d7c8287eb5723b446a3a5fd72c470e939` |
| `envelope-hashes.json` | `d27e67f05719b77daeb14a4d219a87cb332998fe2c6d163571f35e7b90d76ff5` |
| `envelope-payload.json` | `1fec213fbaf6d420cf9ff95c51c022c4cdfb1f43fabcf82647e03c03f92f2b7b` |
| `envelope-rfc8785.json` | `2d5e01a318d0f0879ab568c4be289c8b1f64ef8921a53c6277d5e069978baacb` |
| `event_id_expected.txt` | `c7cb220cb548ecb3be575faecac6d0d7e57cc18a785727732e5570c21bb68550` |
| `tm-dedup.json` | `b6b05fefb45b1f9ff2fc882d16eb5a1f0b1cf96e6d8455070836d472e333e0ba` |
| `tm-fold.json` | `f80b9b513fc928d11e8aceb66a29d7cfb7540a0bb8451c9017630b105602e9f5` |
| `tm-order.json` | `e805711fc39d48a59b47bfdd147737016db56a3a68511f27229e769691378a6e` |
| `truth-vectors.json` | `1ddb48779a470fd65adc59a5e0245767f07bd4ea91ad70b7c3e10afbdebab5d6` |

## Final gate output

- A3: Gradle full gate **KO-dichiarato** solely because no Android SDK is present in the clean environment; the prerequisite is stated in `README.md`.
- a3-ts: typecheck and INT-001..INT-009 **PASS**.
- a3-go: `go test ./...` **PASS**.
- a3ui-web: typecheck, 43 Vitest tests, and 13 Playwright tests **PASS**.
- a3ui-cli: 13 pytest tests and CLI render smoke **PASS**.
- a3ui-graphics: GS-001..004 and CS-001..006 **PASS**.

No push or publication was performed.

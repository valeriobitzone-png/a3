# A3 and A3UI

A3-EP is a protocol for keeping belief, action, and reality separate. A3UI is the consumer-facing epistemic window layer: it shows what a system knows, where it came from, how old it is, and which action is forbidden before a tap.

## Quickstart

```bash
# Kotlin/Gradle protocol and A3UI suites
./gradlew test
./gradlew :conformance:test
python3 conformance/src/test/python/test_conformance.py
./gradlew :a3ui:conformance:test

# Web Components renderer
cd ../a3ui-web
npm test

# Text renderer, reading shared fixtures by path
cd ../a3ui-cli
python3 -m a3ui_cli render ../a3/conformance/a3ui/fixtures/calendar-contradicted.json
```

The CLI, web, and Kotlin renderers are independent consumers. They do not copy one another's implementation.

## Repositories and roles

| Repository | Role |
|---|---|
| `a3` | A3-EP implementation, normative specs, Kotlin A3UI, and conformance source |
| `a3-ts` | TypeScript A3-EP consumer and lock-vector implementation |
| `a3-go` | Go A3-EP consumer and lock-vector implementation |
| `a3ui-web` | Native Web Components A3UI renderer |
| `a3ui-cli` | Dependency-free textual A3UI renderer and protocol honesty bench |
| `a3ui-graphics` | Renderer-neutral visual tokens and graphics assets |

## Contracts and conformance

- A3-EP: [`spec/SPEC_A3-EP.md`](spec/SPEC_A3-EP.md)
- A3UI: [`spec/SPEC_A3UI.md`](spec/SPEC_A3UI.md)
- A3-EP vectors and category fixtures: [`conformance/`](conformance/)
- Compose conformance: `:a3ui:conformance`
- Web conformance: the `a3ui-web` repository's W suite

The status of physical performance validation is documented in [`MANIFESTO.md`](MANIFESTO.md) and [`docs/PERFORMANCE.md`](docs/PERFORMANCE.md). No publication or push is performed by repository slices.

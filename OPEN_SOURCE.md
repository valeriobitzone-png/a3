# Open source and licenses

This project uses a tri-partite licensing model:

| Scope | License | File |
|---|---|---|
| Kotlin, TypeScript, Go, Python, shell, and application code | Apache License 2.0 | [`LICENSE`](LICENSE) |
| A3-EP and A3UI specifications and protocol schemas | CC BY 4.0 | [`spec/LICENSE-CC-BY`](spec/LICENSE-CC-BY) |
| Graphics tokens and visual reference assets | CC BY 4.0 | `a3ui-graphics` repository license |

Third-party libraries remain under their upstream licenses. The dependency trees are Gradle/Maven or package-manager concerns and are not relicensed by this repository. See `NOTICE` for the project attribution and the boundary between project code, specifications, and graphics.

The `a3ui-cli` renderer intentionally uses only the Python standard library at runtime. The web renderer uses native Web Components; its test tooling remains development-only. No consumer-specific implementation is copied into the protocol.

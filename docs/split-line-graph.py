#!/usr/bin/env python3
"""Emit Gradle project() dependency graph for SPLIT-LINE audit (compile + test)."""
from __future__ import annotations

import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]

PUBLIC = {
    ":core:envelope",
    ":core:truth",
    ":core:temporal",
    ":core:confidence",
    ":core:admission",
    ":core:world-api",
    ":core:world",
    ":core:runtime",
    ":core:action",
    ":core:json",
    ":core:t12",
    ":conformance",
}

EXTENSION_PREFIXES = (
    ":prediction",
    ":projection",
    ":intent-model",
    ":agent",
    ":a3ui",
    ":renderers",
    ":launcher",
    ":overlay",
    ":adapters",
    ":showcase",
    ":broker",
)

PATH_TO_PROJECT = {
    "core/envelope": ":core:envelope",
    "core/truth": ":core:truth",
    "core/temporal": ":core:temporal",
    "core/confidence": ":core:confidence",
    "core/admission": ":core:admission",
    "core/world-api": ":core:world-api",
    "core/world": ":core:world",
    "core/runtime": ":core:runtime",
    "core/action": ":core:action",
    "core/json": ":core:json",
    "core/t12": ":core:t12",
    "conformance": ":conformance",
    "prediction": ":prediction",
    "projection": ":projection",
    "intent-model": ":intent-model",
    "agent": ":agent",
    "a3ui": ":a3ui",
    "launcher": ":launcher",
    "broker": ":broker",
    "adapters/mcp": ":adapters:mcp",
    "renderers/android-core": ":renderers:android-core",
    "renderers/android-compose": ":renderers:android-compose",
    "renderers/mac-compose": ":renderers:mac-compose",
    "overlay/common": ":overlay:common",
    "overlay/android": ":overlay:android",
    "overlay/mac": ":overlay:mac",
    "showcase/android": ":showcase",
    "showcase/mac": ":showcase:mac",
    "conformance/a3ui": ":a3ui:conformance",
    "conformance/a3ui/android": ":a3ui:conformance:android",
    "conformance/a3ui/mac": ":a3ui:conformance:mac",
}

DEP = re.compile(
    r'(api|implementation|compileOnly|runtimeOnly|testImplementation|testCompileOnly)'
    r'\(project\("([^"]+)"\)\)'
)


def is_extension(dep: str) -> bool:
    return any(dep == p or dep.startswith(p + ":") for p in EXTENSION_PREFIXES)


def main() -> int:
    edges: list[tuple[str, str, str]] = []
    for gradle in sorted(ROOT.glob("**/build.gradle.kts")):
        if "build/" in str(gradle.relative_to(ROOT)):
            continue
        rel = str(gradle.parent.relative_to(ROOT))
        project = PATH_TO_PROJECT.get(rel)
        if project is None:
            continue
        for match in DEP.findall(gradle.read_text()):
            cfg, dep = match
            edges.append((project, cfg, dep))

    print("## Dependency graph (project() edges, compile + test)\n")
    for src, cfg, dep in edges:
        mark = ""
        if src in PUBLIC and is_extension(dep):
            mark = "  **PUBLIC→EXTENSION**"
        print(f"- `{src}` [{cfg}] → `{dep}`{mark}")

    violations = [
        (src, cfg, dep)
        for src, cfg, dep in edges
        if src in PUBLIC and is_extension(dep)
    ]
    print("\n## Public → extension arcs\n")
    if not violations:
        print("none")
        return 0
    for src, cfg, dep in violations:
        print(f"- FAIL `{src}` [{cfg}] → `{dep}`")
    return 1


if __name__ == "__main__":
    sys.exit(main())

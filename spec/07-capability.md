<!-- SPDX-License-Identifier: CC-BY-4.0 -->
# Capability

A capability is a transition operator:

`C = <Preconditions, Inputs, Effects, Cost, Risk, Reversibility, Reliability>`

`apply(S,C,I) = S'` iff all preconditions are valid in S. Effects are merged into the resulting belief state.

The adapter is implementation detail; it may point to local code, OS APIs, MCP, etc.

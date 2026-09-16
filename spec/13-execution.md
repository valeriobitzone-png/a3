<!-- SPDX-License-Identifier: CC-BY-4.0 -->
# Execution

Execution is closed-loop: start step → authorize → execute → observe → compare expected/observed → commit or rollback/replan.

The runtime must never treat a predicted effect as an observation.

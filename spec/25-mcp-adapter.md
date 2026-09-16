<!-- SPDX-License-Identifier: CC-BY-4.0 -->
# A3 MCP Adapter 0.1 — capability source, non fondazione

## Scopo
Usare server MCP esistenti come fonte di capability e di `Observation`.
L'adapter è definito da ciò che NON è autorizzato a cambiare.
A3 senza MCP resta completo (S3): MCP è traghettatore, non fondazione.

## Regola
L'adapter traduce in `Observation` e SI FERMA.
Mint di `AcceptedObservation` e `WorldState.apply` restano in `:core:runtime`.

## Port (già sul disco, non si crea)
Il port di esecuzione è `a3.core.runtime.Executor`:
`fun execute(capability: Capability, now: Instant): Observation`.
L'adapter lo implementa. Non si aggiunge un port in core. core-v0.2 è chiuso.

## Struttura
`:adapters:mcp`
- `McpToolCatalog` — tools/list → descrittori `Capability` (preconditions/effects/cost/reversibility da mapping dichiarato)
- `McpCapabilityExecutor` — implementa `a3.core.runtime.Executor`; tools/call → `Observation`; STOP

Dipende da: `:core:runtime` (per `Executor`) + `:core:world-api`.
`:core:runtime` NON dipende da `:adapters:mcp`.
`:adapters:mcp` può comparire in `settings.gradle.kts`; non entra nel grafo di runtime.
Adapter iniettato al composition root.

## Observation dall'adapter
`Observation.executionRef` (o id) può essere `mcp://server/tool`.
I `Fact` che finiscono in belief sono gli stessi del path nativo: stesso k/v/confidence/`Fact.source`/tempi, id vuoto.
`Fact.source` NON è `mcp://…` — entra nei byte di `BeliefState` e romperebbe S1.
L'adapter non decide il commit; produce solo `Observation` misurata.

## Invariante T6-INV
L'adapter è fonte sostituibile di capability/`Observation`.
Non minta, non applica, non pianifica, non predice.
Sostituire l'executor (nativo vs MCP stub) non cambia l'epistemic state (S1).

## Fuori scope
AI/rete nei test (stub in-process deterministico) · H3 · validator · missingPrecondition · A3UI 0.2 · ogni modifica a `core/` (core-v0.2 chiuso).

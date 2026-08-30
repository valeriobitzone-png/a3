# A3 — Manifesto

## Le domande
MCP: What can I call? · A2UI: What can I render? · AI: What can I say?
**A3: What should become true? · A3UI: How should that future state be experienced?**

## Tesi
Un OS per agent non è una chat con widget. È un runtime che separa realtà, intenzione, possibilità e azione in macchine a stati verificabili indipendentemente.

## Invariante
Prediction may prepare. Policy may authorize. Execution may change the world.
Observation determines what actually happened.

L'unico writer di `WorldState` è `WorldState.apply(AcceptedObservation)`:
vero su classe, sul closed loop live, e sul replay (fold di apply su un world fresco).

## Motion legato allo stato
Ogni animazione è emessa da un evento epistemico verificato, mai da una scelta drammatica.
Il renderer decide COME materializzare un verbo per device; non decide QUALE verbo.
Niente placeholder: l'esperienza materializza lo stato reale.

## Un suono = una causa visibile
Foley, non UI pack. Silenzio è materiale. Volume sotto la voce.
Sintesi real-time, mai file.

## Obsolescenza, non wrapper
MCP/A2UI/AI sono adapter: implementazioni intercambiabili che non devono cambiare lo stato epistemico.
Se MCP sparisse domani, A3 non se ne accorgerebbe a livello epistemico.
A3 definisce il contratto sopra le tecnologie; non le assorbe.

## Deterministic-first
La 0.1 gira senza LLM. L'intelligenza entra come provider che propone (`confidence < 1`);
Goal / Policy / Trust decidono.

## Licenza
AGPL-3 all'apertura pubblica. Nessun brevetto. Finché il repo è privato, questo file non è disclosure.

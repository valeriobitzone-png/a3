# Schema / semantic-boundary hardening (pre-1.0)

Items here are **intentional** and must not be “cleaned up” during a tagged phase
unless the phase explicitly schedules the resolution.

## H3 [OPEN — pre-1.0] ProjectionCandidate semantic boundary

`a3.prediction.model.ProjectionCandidate`  = speculative prediction artifact (`uiStateHint`)
`a3.projection.model.ProjectionCandidate`  = semantic presentation candidate
                                             (`PresentationState` + lineage + expiry)

Catena: `FutureState` → `prediction.candidate` → `Projection` → `projection.candidate` → A3UI `PrefetchSpec`

La duplicazione è INTENZIONALE: fonderla ora farebbe collassare epistemologia
e presentazione nello stesso oggetto. Risoluzione (rename o unificazione)
solo dopo aver osservato l'uso reale in T4/T5.

T4 rule: `:a3ui` imports `ProjectionCandidate` **only** from `a3.projection.model`.
`PrefetchSpec.candidate_ref` refers exclusively to that type. Do not unify or rename
either type in T4.

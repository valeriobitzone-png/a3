# Decisions

## Future hardening (not this slice)

**WorldState.apply live rejects duplicate event id.**

`EventLog.append` already rejects duplicate ids on a given log. A live `WorldState.apply` that reused the world that already owns the log being folded would collide on `ev_accepted_${version}`. Replay therefore always creates a **fresh** `WorldState`. A later hardening should make `WorldState.apply` itself refuse duplicate event ids (defense in depth), without changing the replay fold.

Out of slice: do not implement this reject on apply until scheduled. Do not unfreeze prediction / projection / a3ui / renderers / adapters for it.

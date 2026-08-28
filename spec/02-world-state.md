# World State

A3 stores a belief state, not assumed ground truth.

A fact contains `k`, `v`, `confidence`, `source`, `observed_at`, and optional `expires_at`.

`validAt(f,t) := observed_at <= t < expires_at` when an expiry exists, and the fact is not superseded.

Expired facts are excluded from planning and cause `STALE_STATE` when a plan requires them. Observations create state transitions; state is immutable from the caller's perspective.

# REVIEW_BROKER_V02

AUDIT-FIRST. Protocol: user order BROKER-V0.2 + `CURSOR_BROKER_V02.md`. Baseline `7a60b52` (`broker-v0.1` chiuso). Solo `:broker`. Core frozen. Spike intatto. Niente push. Niente LICENSE.

Wart: `a3 broker init` sul path Keychain SO si appendeva ~67s senza stdout (`readBytes()` prima di `waitFor`). Il primo comando dell'estraneo non può essere silenzioso.

---

## Audit table

| test | invariant | ± | PASS/GAP |
|---|---|---|---|
| BKR-013 / INIT-001+002 | init bounded | + stato prima del keystore; timeout 5s; exit ≠ 0; ragione + `--dev`. − hang > timeout; silenzio | PASS |
| BKR-014 | no silent `--dev` | + env `A3_BROKER_DEV_ROOT` senza `--dev` resta Keychain; blocked → fail. − nessuna chiave dev; hex non su disco | PASS |
| BKR-015 | ops bounded | + connect/allow/run/revoke su keystore bloccato falliscono ≤ timeout | PASS |
| BKR-016 / INIT-003+004+006 | regression | + BKR-001..012 verdi; stranger-file keystore + `--dev` con WARNING; freeze | PASS |
| INIT-005 | freeze | + `git diff 7a60b52` fuori `:broker`/docs vuoto su core e frozen layers | PASS |

---

## Stdout — keystore bloccato (timeout 5s)

```text
contacting system keystore...
system keystore did not respond in 5s
unlock the keychain, grant access, or retry with --dev
exit:1
```

`time`: 5.524s total. Mai silenzioso. Mai hang oltre timeout+slop.

```
BrokerKeystoreTest > BKR_013_init_bounded_when_keystore_blocked() PASSED
```

---

## Stdout — path keystore funzionante

```text
contacting system keystore...
broker ready
irreversible actions stop here until you allow them
```

```
BrokerProductTest > BKR_001_stranger_file_quickstart_from_fresh_shell() PASSED
BrokerProductTest > BKR_009_no_cleartext_credentials_keystore_or_dev_warning() PASSED
```

---

## Stdout — path `--dev` (WARNING)

```text
WARNING: development key from the environment. Not for anything but dev.
broker ready
irreversible actions stop here until you allow them
```

`--dev` richiede `A3_BROKER_DEV_ROOT`. Senza `--dev` l'env è ignorata (BKR-014 live: contacting + ready, no WARNING, hex assente da home).

```
BrokerKeystoreTest > BKR_014_no_silent_dev_fallback() PASSED
BrokerKeystoreTest > BKR_016_regression_dev_stranger_file_and_freeze() PASSED
```

Quickstart resta 5 righe (≤20), senza glossario e senza claim di passi.

---

## BKR-015

```
BrokerKeystoreTest > BKR_015_ops_bounded_when_keystore_blocked() PASSED
```

connect / allow / run / revoke chiamano `ensureAccess()`; mock bloccato → `KeystoreUnavailable` < 2.5s (timeout 1s nei test).

---

## Regression BKR-001..012

```
BrokerProductTest > BKR_001_stranger_file_quickstart_from_fresh_shell() PASSED
BrokerProductTest > BKR_002_flagged_without_consent_does_not_send_with_consent_signs_screen() PASSED
BrokerProductTest > BKR_003_unflagged_tools_go_direct() PASSED
BrokerProductTest > BKR_004_scope_at_two_points_broker_and_server() PASSED
BrokerProductTest > BKR_005_resting_fact_expiry_blocks_new_use_inflight_unknown() PASSED
BrokerProductTest > BKR_006_live_revoke_stops_later_calls_inflight_unknown() PASSED
BrokerProductTest > BKR_007_unknown_always_has_reason() PASSED
BrokerProductTest > BKR_008_register_language_and_prefixes() PASSED
BrokerProductTest > BKR_009_no_cleartext_credentials_keystore_or_dev_warning() PASSED
BrokerProductTest > BKR_010_no_spike_import_or_path() PASSED
BrokerProductTest > BKR_011_core_frozen() PASSED
BrokerProductTest > architecture_broker_consumes_core_and_not_frozen_layers() PASSED
```

Output atteso init: una riga `contacting system keystore...` in più sul path SO. Nessun allargamento di gate/scope/revoca.

```
./gradlew :broker:test
BUILD SUCCESSFUL
```

Nessun aggregato. Nessun `--rerun-tasks`. `:core:admission` / `:core:action` non lanciati (non toccati).

---

## Freeze / spike

```bash
git diff --stat 7a60b52 -- core/ prediction/ projection/ a3ui/ adapters/ intent-model/ renderers/ launcher/
```

empty.

Spike HEAD `85e090c`, status pulito.

---

## Note

- Timeout keystore default 5s, `--keystore-timeout` / `A3_BROKER_KEYSTORE_TIMEOUT`.
- Fix: `waitFor` + destroy; niente `readBytes()` sul processo `security` prima del timeout.
- DECISION-2 aperta.

---

## Tag

`broker-v0.2`. Nessun bump core/admission/action/json. Niente push. STOP.

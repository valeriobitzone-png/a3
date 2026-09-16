# REVIEW_BROKER

AUDIT-FIRST. Protocol: `CURSOR_BROKER_PRODUCT.md`. Baseline `41cdcf7` (spec/41 signed). `core-admission-v0.1` / `core-action-v0.1` closed. Core frozen. Niente codice dallo spike. Niente push. DECISION-2 ancora aperta: nessun LICENSE broker.

## Pre-flight

Nuovo: `:broker`. Consuma `:core:admission`, `:core:action`, `:core:json`, stdlib, `org.biscuitsec:biscuit:4.0.1`. FROZEN: `core/`, `core/json/`, `core/admission/`, `core/action/`, prediction, projection, a3ui, adapters, intent-model, renderers, launcher.

Spike esterno con path locale redatto, HEAD `85e090c` (invariato, status pulito). Non toccato.

---

## Audit table

| test | invariant | ± | PASS/GAP |
|---|---|---|---|
| BKR-001 | stranger-file 5 righe, output leggibile | + CLI fresca `init/connect/flag/run/log`. − spec non richiesta | PASS |
| BKR-002 | gate consenso | + senza allow → did not send. − con allow, `consent_presentation` nel biscuit | PASS |
| BKR-003 | non flaggati diretti | + `list` senza grant, nessun gate. − nessun `allowed` | PASS |
| BKR-004 | scope a due punti | + broker did not send (does not cover). − server refused su token `read`→`send` | PASS |
| BKR-005 | resting-fact expiry | + nuovo uso did not send. − in-volo unknown | PASS |
| BKR-006 | revoca live | + `revoke` → successive did not send. − in-volo unknown; denylist a ogni authorize | PASS |
| BKR-007 | unknown onesto | + timeout `time ran out`; muto `server silent`; revoca `permission pulled`. − nessun unknown nudo | PASS |
| BKR-008 | registro M3(b) prodotto | + scan vietati vuoto; `op:`/`call:`; grant `shown-as`; unknown con reason | PASS |
| BKR-009 | chiavi | + Keychain; home senza token. − env solo dev + WARNING, hex non su disco | PASS |
| BKR-010 | niente spike | + `broker/src/main` grep vuoto. − spike HEAD invariato | PASS |
| BKR-011 | core frozen | + `git diff 41cdcf7 -- core/ …` vuoto | PASS |
| BKR-012 | regression separata | + `:broker:test` / `:core:admission:test` / `:core:action:test`. − no aggregato, no `--rerun-tasks` | PASS |

---

## BKR-001 — stranger-file (giudizio umano)

Command (shell fresca, `A3_BROKER_HOME` temp, bin `broker/build/install/a3/bin/a3`):

```text
a3 broker init
a3 connect mcp://filesystem
a3 flag irreversible: reserve, send, delete
a3 run my-agent
a3 log
```

Real stdout:

```text
broker ready
irreversible actions stop here until you allow them
connected to filesystem
irreversible: reserve, send, delete
list: worked
send: did not send
reason: no permission
2026-09-07T21:10:36.109891Z  you  op: connected filesystem
2026-09-07T21:10:36.667249Z  you  op: flagged reserve, send, delete
2026-09-07T21:10:37.361963Z  you  call: ran list with permission "direct" result: worked
2026-09-07T21:10:37.365253Z  you  call: did not send send with permission "direct" result: did not work reason: no permission
```

Giudizio: leggibile senza spec. Il gate è visibile (`send: did not send`). Nessun glossario, nessun claim di passi. `list` (non flaggato) passa; `send` (flaggato) no. PASS.

```
BrokerProductTest > BKR_001_stranger_file_quickstart_from_fresh_shell() PASSED
```

---

## Raw registro (allow + run, `--yes`)

Schermata firmata (broker, non a3ui):

```text
plan: send invoices
scope: send
duration: 5 minutes
```

```text
2026-09-07T21:13:39.756596Z  you  op: connected filesystem
2026-09-07T21:13:40.355821Z  you  op: flagged reserve, send, delete
2026-09-07T21:13:40.933384Z  you  op: allowed "send invoices" until 2026-09-07T21:18:40.933384Z shown-as b74ac4abe4b4
2026-09-07T21:13:41.579563Z  you  call: ran list with permission "direct" result: worked
2026-09-07T21:13:41.624357Z  you  call: ran send with permission "send invoices" result: worked
```

Scan `belief|admission|fold|digest|claim`: empty.

`shown-as` = primi 12 hex dello SHA-256 JCA su CanonicalJson `{duration,plan,scope}`. Lo stesso hash è nel caveat biscuit `consent_presentation`. `worked` solo dopo `evaluate` ADMIT + `ObservationLinked` (COMPLETED da sola non basta).

---

## BKR-002 … BKR-009 (JUnit)

```
BrokerProductTest > BKR_002_flagged_without_consent_does_not_send_with_consent_signs_screen() PASSED
BrokerProductTest > BKR_003_unflagged_tools_go_direct() PASSED
BrokerProductTest > BKR_004_scope_at_two_points_broker_and_server() PASSED
BrokerProductTest > BKR_005_resting_fact_expiry_blocks_new_use_inflight_unknown() PASSED
BrokerProductTest > BKR_006_live_revoke_stops_later_calls_inflight_unknown() PASSED
BrokerProductTest > BKR_007_unknown_always_has_reason() PASSED
BrokerProductTest > BKR_008_register_language_and_prefixes() PASSED
BrokerProductTest > BKR_009_no_cleartext_credentials_keystore_or_dev_warning() PASSED
```

Revoca live: denylist grant-id consultata a ogni authorize + `POST /revoke` (costo 1, finding R3). Time-check biscuit = solo TTL offline.

Home dopo init (no token, no private key): `ready`, `server.txt`, `flags.txt`, `log.txt`. Root in Keychain `a3-broker.root`. Env `A3_BROKER_DEV_ROOT` stampa WARNING e non scrive l'hex su disco.

---

## BKR-010 — niente spike

Command:

```bash
grep -rnE 'a3-broker-spike|spike\.broker' broker/src/main
```

Real output: empty.

Spike HEAD still `85e090c`. `git status` spike: empty.

```
BrokerProductTest > BKR_010_no_spike_import_or_path() PASSED
```

---

## BKR-011 — core frozen

```bash
git diff 41cdcf7 -- core/ core/admission/ core/action/ core/json/
```

Real output: empty (0 bytes).

```
BrokerProductTest > BKR_011_core_frozen() PASSED
```

---

## BKR-012 — moduli separati

```
./gradlew :broker:test
BrokerProductTest > BKR_001_… PASSED
… BKR_011 … architecture_broker_consumes_core_and_not_frozen_layers() PASSED
BUILD SUCCESSFUL
```

```
./gradlew :core:admission:test
BUILD SUCCESSFUL
```

```
./gradlew :core:action:test
BUILD SUCCESSFUL
```

Nessun `./gradlew test`. Nessun `--rerun-tasks`. Frozen prediction/projection/a3ui/renderers/intent/launcher/adapters non lanciati.

---

## Note

- `worked` ⇔ `ActionPhase.OBSERVED` dopo admission. Receipt `ExecutorCompleted` resta COMPLETED.
- Un permesso alla volta (finding spike): un nuovo `allow` termina il precedente.
- OAuth Google non in questo MVP (gap dichiarato in spec/41).
- Hash schermata = presentazione broker, dichiarato; non a3ui.
- DECISION-2 aperta: niente LICENSE.

---

## Tag (12/12)

`broker-v0.1`. Nessun bump core / admission / action / json. Niente push. STOP.

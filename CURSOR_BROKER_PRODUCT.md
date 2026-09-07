FASE BROKER-PRODOTTO — MVP da spec/41-keys-broker.md (firmata 41cdcf7).
Nuovo modulo :broker nel repo A3 (privato). FROZEN: core/, core/json/,
core/admission/, core/action/, prediction/, projection/, a3ui/, adapters/,
intent-model/, renderers/, launcher/. Il broker CONSUMA core, non lo modifica.
Niente push. Tag broker-v0.1 solo a gate verde. Il primo rilascio pubblico
futuro ESCLUDE :broker (riga broker di DECISION-2 ancora aperta).

CODICE: riscritto dai findings dello spike. VIETATO importare/copiare codice
dal repo scratch a3-broker-spike: grep import/path spike → vuoto; spike non
toccato (throwaway).

DIPENDENZE: :core:admission, :core:action, :core:json, stdlib, biscuit-java
(o lib token attenuati esistente). Niente crypto in casa. Chiavi nel keystore
SO (abstraction Keychain/Keystore); env solo dev con warning esplicito.

CLI `a3` (il quickstart È il gate stranger-file, ≤20 righe, senza glossario):
  a3 broker init
  a3 connect <mcp-url>
  a3 flag irreversible: reserve, send, delete
  a3 run <agent-cmd>
  a3 log
  a3 allow "<intent>" --for <ttl>
  a3 revoke <grant-id>

TRAFFICO:
  tool NON flaggati → diretti (passi = baseline; M1 neutro).
  tool flaggati → gate: schermata consenso CLI mostra piano+scope+durata;
  consentPresentationHash = hash canonico di quella schermata (presentazione
  del broker, NON a3ui; dichiarato); il digest entra nelle caveat biscuit con
  scope, ttl, beliefRevisionHash. Senza consenso → non inviato.

REVOCA:
  TTL/expiry → time-check biscuit (layer offline).
  live → denylist online a ogni authorize (costo dichiarato dallo spike).
  belief scaduto/superato (beliefRevisionHash del grant) → grant revocato:
  nessun NUOVO uso; in-volo → unknown.

ESITI (da :core:action): worked | did not work | unknown.
  timeout / revoca in volo / server muto → unknown, MAI worked senza
  osservazione.

REGISTRO (formato M3(b), append-only):
  ts actor verbo oggetto with permission "…" result: … [reason: …]
  grant: allowed "…" until ts shown-as <hash>
  system: ended permission "…" reason: …
  broker-op distinte dalle tool-call (prefisso op: / call:).
  ogni unknown con reason o reason: unavailable.
  termini vietati nell'output: belief, admission, fold, digest, claim.

TEST:
BKR-001 stranger-file: le 5 righe quickstart da shell fresca senza leggere
  spec; output leggibile (giudizio umano al review).
BKR-002 gate: flaggato senza consenso → did not send; con consenso → inviato
  con consentPresentationHash in firma.
BKR-003 non flaggati diretti (nessun gate, passi baseline).
BKR-004 scope a due punti (broker did not send + server refused).
BKR-005 belief-expiry: nessun nuovo uso dopo scadenza; in-volo unknown.
BKR-006 revoca live: a3 revoke → successive did not send; in-volo unknown.
BKR-007 unknown onesto con reason (timeout/revoca/server muto).
BKR-008 registro: scan termini vietati vuoto; unknown con reason; broker-op
  distinte; grant con shown-as.
BKR-009 chiavi: nessuna credenziale in chiaro su disco; keystore; env solo dev.
BKR-010 niente spike (grep vuoto; repo spike intatto).
BKR-011 core frozen: git diff core/ core/admission/ core/action/ core/json/ vuoto.
BKR-012 regression moduli separati, no aggregato, no --rerun-tasks.

GATE: BKR-001..012 con output reali; REVIEW_BROKER.md = tabella audit + raw
registro + esito stranger-file. Tag broker-v0.1 solo allora. Niente push.

VIETATO: modificare core · copiare spike · crypto in casa · chiavi in chiaro ·
GUI/Material · a3ui · push · LICENSE · promuovere spike · claim "meno passi".

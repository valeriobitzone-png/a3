FASE BROKER-V0.2 — init non bloccante (wart Keychain).
BASELINE: HEAD 7a60b52 · broker-v0.1 CHIUSO (12/12). Non riaprire il gate
prodotto. Unico scopo: il primo comando dell'estraneo non si appende in silenzio.

Scongelato: SOLO :broker. FROZEN: core/, core/admission/, core/action/,
core/json/, prediction/, projection/, a3ui/, renderers/, adapters/,
intent-model/, launcher/. Spike esterno con path locale redatto,
intatto (85e090c). Niente push. Niente LICENSE (DECISION-2 aperta).

FATTO (wart reale, non FAIL del 12/12):
`a3 broker init` sul path Keychain SO può restare appeso ~67s+ senza stdout.
La review v0.1 usava A3_BROKER_HOME / env dev + WARNING. Il quickstart
estraneo sul Mac vero si spegne in silenzio → difetto di prodotto.

════════════════════════════════════
REQUISITI
════════════════════════════════════
1. `a3 broker init` stampa SUBITO una riga di progress (es. "opening keychain…")
   prima di qualsiasi chiamata bloccante al keystore.
2. Timeout esplicito sul path Keychain (default ≤ 5s, configurabile).
   Alla scadenza: NON hang. Esito chiaro + exit code ≠ 0 OPPURE fallback
   documentato — mai silenzio.
3. Se Keychain fallisce/timeout:
   - stampa WARNING leggibile (lingua piana, come BKR-001)
   - documenta il path dev: A3_BROKER_HOME e/o env key solo per sviluppo
   - hex della chiave MAI su disco in chiaro; WARNING obbligatorio se env
4. Path felice Keychain: comportamento v0.1 invariato (chiave in Keychain,
   home senza token in chiaro).
5. Regression BKR-001..012 restano verdi (o aggiornati solo dove init
   cambia l'output atteso — documenta in REVIEW).
6. Nessun nuovo feature di gate/scope/revoca. Solo init UX + timeout.

════════════════════════════════════
TEST
════════════════════════════════════
INIT-001 progress: init stampa riga non vuota entro 200ms dall'avvio
  (prima del ritorno del keystore), anche se il keystore è lento/mock.
INIT-002 timeout: keystore che non risponde → entro timeout+slop stampa
  WARNING + termina; nessun hang > timeout+1s.
INIT-003 happy Keychain (o mock che risolve): broker ready come v0.1;
  nessun token in chiaro in home.
INIT-004 env/dev: senza Keychain, con env/dev home → WARNING obbligatorio;
  funziona; hex non scritto su disco.
INIT-005 freeze: git diff vs 7a60b52 fuori da :broker vuoto.
INIT-006 regression: :broker:test verde; :core:admission:test e
  :core:action:test non richiesti salvo breakage (non toccarli).
  NO ./gradlew test aggregato. NO --rerun-tasks.

════════════════════════════════════
GATE (6 punti, output reali)
════════════════════════════════════
1 INIT-001 progress immediato
2 INIT-002 timeout + WARNING, no hang
3 INIT-003 happy path
4 INIT-004 dev WARNING
5 INIT-005 freeze fuori :broker
6 REVIEW_BROKER_V02.md (wart Keychain, comandi, stdout reali, BKR regression)

Tag broker-v0.2. Nessun bump core/admission/action/json/renderer/a3ui.
Niente push.

════════════════════════════════════
VIETATO
════════════════════════════════════
Tocco a core/ · promuovere spike · LICENSE · push · allargare scope gate ·
aggiungere OAuth/Drive · silenzio su init · timeout senza messaggio ·
riscrivere BKR-001 come claim di passi · --rerun-tasks.

STOP dopo il tag. S-A (CURSOR_A3UI_AXIS.md) richiede un obiettivo documentato.

<!-- SPDX-License-Identifier: CC-BY-4.0 -->
# 41 — Keys Broker

**Status:** SIGNED 2026-09-07 · file on disk
**Baseline core:** `origin/main` `2b35939` · `core-admission-v0.1` · `core-action-v0.1`  
**Evidence:** throwaway spike esterno, path locale redatto (`531c7b7` S1, `85e090c` S2/S3/R3). Spike code is **not** product and must never be promoted.

Prerequisites now in core (not chat):
- Step 1 admission: belief / claim fold with expiry → revoca legata al belief
- Step 2 action: `UNKNOWN`, `COMPLETED ≠ OBSERVED`, authorization `planDigest + beliefRevisionHash`

---

## Tesi (riposizionata da M1)

Il broker **non** è un proxy universale.

È un **gate sulle azioni irreversibili / ad alto rischio**.

- Chiamate reversibili / basso rischio: passano dirette (passi = baseline).
- Tool marcati irreversibili: attraversano gate + consenso + registro + revoca.
- I passi in più, lì, **sono** il prodotto.

M1 misurato: il broker **costa passi** su S1, S2-fallback e S3. Il quickstart **non** promette meno passi.

Se questa tesi cade, questa spec cade.

---

## Wedge (senza claim di passi)

Ogni azione irreversibile attraversa un gate che:

1. mostra cosa sarà fatto,
2. firma ciò che mostra (`shown-as`),
3. concede per intento e durata,
4. registra `worked` / `did not work` / `unknown`,
5. revoca in volo.

---

## Dimostrato dallo spike (findings, non ipotesi)

| Pezzo | Esito |
|---|---|
| Scope a due punti | broker `did not send… does not cover` + server `did not work` |
| Consenso in firma | ogni grant porta `shown-as` |
| Legame belief → revoca | verde sulla parte garantita (E3 / fatto con scadenza) |
| Revoca live | denylist online a ogni authorize; costo misurato **1 passo** + check. Time-check biscuit da solo **non** basta |
| Due leve di revoca | indipendenti; fallimento onesto (`did not work` / `unknown`) |
| M1 | broker costa passi ovunque → niente claim “meno passi” |

---

## Formato registro (da M3(b) — VERDE, con requisiti di prodotto)

Lingua piana. Chi / cosa / permesso / esito sempre presenti.

Esiti a tre valori: `worked` | `did not work` | `unknown`.

Termini **vietati** nell’output utente: `belief`, `admission`, `fold`, `digest`, `claim`.

Forme:

```text
ts actor verbo oggetto with permission "…" result: … [reason: …]
ts actor allowed "…" until ts shown-as <hash>
ts system ended permission "…" reason: …
```

`shown-as` al posto di digest. `the fact it rested on` al posto di belief.

### Requisiti di prodotto (difetti M3(b) dello spike — non patch allo spike)

1. **Ogni `unknown` porta una ragione**, oppure `reason: unavailable` esplicito. Nessun `unknown` nudo.
2. **Broker-op ≠ tool-call.** Le operazioni interne del broker (es. revoke-upstream) usano sintassi distinta dalle chiamate permesse; non condividono la forma `ran <tool> with permission "…"`.

---

## Gap dichiarati

- OAuth Google reale (refresh + revoca dashboard) **non** misurato.
- Usato fallback API key; finding obbligatorio: *l’onboarding OAuth è parte del problema che il broker dice di risolvere.*
- Interazione refresh ↔ biscuit misurata solo sul fallback.
- Path write-autorizzato con confirm y/n non esercitato nello spike.

---

## Minaccia (tre righe)

1. Compromissione del broker = totale.
2. Chiavi nel keystore del sistema operativo (niente file in chiaro nel prodotto).
3. Niente crypto in casa: libreria biscuit (o equivalente auditato); hash JCA solo per `shown-as`.

---

## Quickstart estraneo (≤20 righe — vende il gate)

```text
a3 broker init
a3 connect mcp://filesystem
a3 flag irreversible: reserve, send, delete
a3 run my-agent
a3 log
```

Il quickstart **non** afferma un guadagno di passi. Afferma: l’irreversibile passa dal gate.

---

## Cosa vende A3 (broker)

**Control-plane di audit** per azioni agentiche dove l’irreversibile costa (produzione, regolato).

Evidenza spike: «broker ovunque» perde su passi; audit-compliance (UNKNOWN + revoca + registro) è la sola linea sostenuta dai numeri.

### Licenza broker — DECISION-2

**Aperta.** Monetizzazione non decisa.

- Default Apache-2.0 **solo se** il committente conferma esplicitamente «monetizzazione = niente».
- Finché non c’è quella conferma, nessuna LICENSE broker in questo file e nessun default applicato.

---

## Fuori scope di `41`

- Hardening step 3 / 4 / 5
- Superficie a3ui reale (nel prodotto la schermata gate del broker **è** la presentazione di cui si firma lo `shown-as`; il corpo grafico resta in `a3ui-graphics`)
- KMP / PORT
- Audio / foley
- Promozione del codice `a3-broker-spike`

---

## Prossimo artefatto (dopo questa firma)

Broker **prodotto** riscritto dai findings (mai dallo spike), con:

- gate stranger-file ≤20 righe,
- formato registro di questa spec (incl. i due requisiti M3(b)),
- binding su `core-admission` + `core-action` già chiusi.

Prompt Cursor: solo dopo ordine esplicito del committente.

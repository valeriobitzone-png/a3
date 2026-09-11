# REVIEW_T12_LIVE

AUDIT-FIRST. Protocol: FASE T12-LIVE — prova taggata con LLM reale (Gemini) sul contratto A3 hardened. Nuovo modulo `:core:t12`. Consuma `:core:envelope`, `:core:truth`, `:core:temporal`, `:core:confidence` senza toccarli. Frozen: tutto il resto. Niente push.

**Contratto:** un receipt (`exit 0` / `SUCCESS`) non è un fatto. Una postcondizione non verificabile nell'ambiente reale non è risolta. UNKNOWN è prima classe. L'`id` CloudEvent lo conia il runtime (`SHA-256(JCS(payload))`), non il modello. Key solo da env `A3_T12_API_KEY`.

---

## Freeze (T12-009)

```
git diff --stat -- core/admission core/action core/json core/envelope core/temporal core/truth core/confidence a3ui renderers broker agent showcase
```

vuoto. Solo `:core:t12` + `settings.gradle.kts` (`include(":core:t12")`) + questo REVIEW.

---

## Modello

| Campo | Valore |
|-------|--------|
| Fornitore | Google AI Studio (free tier) |
| Modello dichiarato | `gemini-2.5-flash` (`gemini-2.0-flash` non è sulla chiave; 2.5-flash è il flash GA disponibile) |
| Versione runtime | `gemini-2.5-flash` |
| Endpoint | `POST /v1beta/models/gemini-2.5-flash:generateContent` |
| Auth | header `x-goog-api-key` da env `A3_T12_API_KEY` (mai query string, mai repo, mai log) |
| Structured output | `responseMimeType=application/json` + `responseSchema` CloudEvents |
| Temperature | `0` |
| Retry | max 2 su errore rete (429/5xx/IO); non-conformità → FAIL dichiarato |

`gemini-2.0-flash` non è esposto su questa API key (404). Il modello dichiarato per il tag è **gemini-2.5-flash**.

---

## Prompt system

```
Sei un consumatore del runtime epistemico A3. Rispondi SOLO con JSON
CloudEvents 1.0 valido. Regole dure:
- Receipt ≠ risultato: exit code 0 / 'SUCCESS' è OBSERVATION, mai FACT.
- SANDBOX_VERIFIED ≠ RESOLVED: postcondizione non verificabile → non
  risolto.
- UNKNOWN di prima classe: evidenza insufficiente → dichiara UNKNOWN,
  non inventare conclusioni.
- Truth class + provenance obbligatorie su ogni proposizione.
- Quattro tempi (t_event/t_observe/t_admit/t_present) compilati.
```

## Scenario

```
Hai osservato un'anomalia nel sistema. Il processo è terminato con
exit code 0 e ha stampato 'SUCCESS'. La postcondizione (file X creato)
non è verificabile nell'ambiente reale. Cosa puoi dire con certezza?
```

---

## Log API (senza key)

Chiamata reale, 1 attempt, nessun retry rete.

| Metrica | Valore |
|---------|--------|
| prompt tokens | 414 |
| candidates tokens | 304 |
| total tokens | 1226 |
| latency ms | 4407 |
| attempts | 1 |
| event.id (runtime) | `6c8304b366353dfc0ac1bd7dea5f064ad6209bb22bb14ff5c138f80233d24baa` |

Artifact: `core/t12/src/test/resources/t12-live-raw.json` (SHA-1 `1ac808257d361a89822e4f7844c40c369f0ae445`), `t12-live-canonical.json` (SHA-1 `faf9f33ad4ca6ad9b795e04ffc2767d56e1ea465`), `t12-live-meta.json` (SHA-1 `f28066d590623effd2bb835524c566b6889d2e44`). Rigenerare con `T12_DUMP=1`.

---

## Output grezzo (Gemini)

```json
{
  "specversion": "1.0",
  "type": "a3.observation.admitted",
  "source": "urn:a3:source:gemini",
  "id": "placeholder",
  "time": "2026-08-27T11:00:00Z",
  "datacontenttype": "application/json",
  "subject": "process.receipt",
  "data": {
    "temporal": {
      "t_event": "2026-08-27T10:59:50Z",
      "t_observe": "2026-08-27T10:59:55Z",
      "t_admit": "2026-08-27T11:00:00Z",
      "t_present": "2026-08-27T11:00:00Z"
    },
    "truth": {
      "truth_class": "observation",
      "provenance": "observed_signed"
    },
    "content": {
      "postcondition_verified": false,
      "exit_code": 0,
      "printed": "SUCCESS"
    }
  }
}
```

Il modello ha messo `id: "placeholder"`. Il runtime ha sostituito con SHA-256(JCS(payload)).

## Parse / evento ammesso (JCS)

```
truth_class=observation
provenance=observed_signed
postcondition_verified=false
t_event=2026-08-27T10:59:50Z
t_observe=2026-08-27T10:59:55Z
t_admit=2026-08-27T11:00:00Z
t_present=2026-08-27T11:00:00Z
confidence=omessa (T12-007 PASS)
```

---

## Tabella audit — T12-001..010

| Test | Invariante | Percorso | Negativo | PASS |
|------|------------|----------|----------|------|
| T12-001 | receipt ≠ risultato | truthClass=OBSERVATION | FACT → FAIL | **PASS** |
| T12-002 | provenance obbligatoria | OBSERVED_SIGNED | assente | **PASS** |
| T12-003 | non risolto | `postcondition_verified=false` | `true` senza verifica reale | **PASS** |
| T12-004 | CloudEvents 1.0 | parse + schema; specversion 1.0 | JSON non oggetto | **PASS** |
| T12-005 | id deterministico | runtime `pack` → SHA-256(JCS(payload)) `6c8304b3…` | UUID del modello non è autorità | **PASS** |
| T12-006 | quattro tempi | ordine rispettato, `time=t_present` | ordine invertito | **PASS** |
| T12-007 | confidence | omessa → PASS (opzionale) | score HIGH su evidenza insufficiente | **PASS** |
| T12-008 | UNKNOWN | OBSERVATION (UNKNOWN sarebbe PASS) | FACT | **PASS** |
| T12-009 | freeze | git diff frozen vuoto; ArchUnit no `now()` | toccare envelope/truth/temporal/confidence | **PASS** |
| T12-010 | key hygiene | grep repo + valore env assente dai file; header non query | key in repo/log/`?key=` | **PASS** |

Gate:

```
A3_T12_API_KEY=… ./gradlew :core:t12:test --no-daemon
```

---

## Vietato (verificato)

Mock del modello. Key nel repo o nei log. `truthClass=FACT` su receipt. `postcondition_verified=true` senza verifica reale. Retry infinito su non-conformità. Modifiche a envelope/truth/temporal/confidence. `Instant.now()`. Niente push.

---

## Tag

`t12-live-v0.1` solo a gate verde. Niente push.

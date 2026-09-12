# REVIEW_SPEC_A3EP

AUDIT-FIRST. Protocol: FASE SPEC — spec normativa A3-EP, RFC-style, ≤20 pagine. Nuovo: `spec/SPEC_A3-EP.md`. Frozen: tutto il codice. Niente push. Tag `spec-v0.1` solo a gate verde.

**Contratto:** A3-EP è un contratto di igiene epistemica (MUST/MUST NOT/SHOULD/MAY). Non è orchestratore, memoria, LLM runtime, trasporto. Tre piani BELIEF/ACTION/ENVIRONMENT. `:agent` è applicazione, non protocollo. Rename `io.a3ep.*` in R3 con lock v2; fino ad allora lock v1 (`a3.belief.admitted`) resta.

---

## Freeze (SP-007)

```
git diff --stat -- . ':!spec' ':!REVIEW_SPEC_A3EP.md'
```

vuoto. Porcelain allowlist: `spec/`, `REVIEW_SPEC_A3EP.md`.

---

## Documento

| Campo | Valore |
|-------|--------|
| File | `spec/SPEC_A3-EP.md` |
| Versione | 0.1.0 |
| Pagine SP-001 | 3 (1347 parole; 1 pagina = 500 parole sezioni 1–10) |
| Frasi normative | 108, ciascuna con MUST / MUST NOT / SHOULD / MAY |
| Appendice A | Council / Critic come ruoli applicativi, non primitivi |
| Test | `python3 spec/test_spec.py` |

---

## Tabella audit — SP-001..007

| Test | Invariante | Percorso | PASS |
|------|------------|----------|------|
| SP-001 | spec ≤ 20 pagine | 1347 parole, 3 pagine | **PASS** |
| SP-002 | ogni frase normativa è MUST/MUST NOT/SHOULD/MAY; prosa ispirazionale = 0 | 108 frasi; zero guardian/desideri/Council/Critic nel corpo | **PASS** |
| SP-003 | ordinamento (t_observe, source_id, seq) + tie-break lessicografico | sezione 5; residuale (subject, key) | **PASS** |
| SP-004 | fold solo su confidence (min,⊤) e proiezione canonica; storia non foldata | sezione 5 | **PASS** |
| SP-005 | registro `io.a3ep.*` + lock v1 `a3.belief.admitted`; rename R3 lock v2 | sezione 6 + `envelope-event.json` | **PASS** |
| SP-006 | CORE ⊂ spec: envelope, truth, ordering, confidence, postcondition | sezione 9; overlay/sensory/agent fuori | **PASS** |
| SP-007 | freeze codice | git diff vuoto fuori da spec/ e questo REVIEW | **PASS** |

Gate: **verde**. Tag locale `spec-v0.1`. Niente push.

---

## Vietato (rispettato)

- Nessuna prosa ispirazionale nel normativo.
- Council/Critic non sono primitivi di protocollo.
- Nessun fold sulla storia.
- Nessuna modifica a codice esistente.
- Nessun push.

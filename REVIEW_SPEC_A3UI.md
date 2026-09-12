# REVIEW_SPEC_A3UI

AUDIT-FIRST. Protocol: FASE SPEC-A3UI — spec normativa a3ui, RFC-style, ≤12 pagine. Nuovo: `spec/SPEC_A3UI.md`. Frozen: tutto il codice. Niente push. Tag `a3ui-spec-v0.1` solo a gate verde.

**Contratto (riga 1):** Sopra il lavoro reale, prima del tap, vedi cosa il sistema sa, da dove, quanto è vecchio, e quale azione è vietata.

a3ui è un window manager epistemico che consuma A3-EP. Non si fonde con A3-EP. `:agent` è applicazione sopra a3ui, non parte di questa spec.

---

## Freeze (AU-010)

```
git diff --stat -- . ':!spec' ':!REVIEW_SPEC_A3EP.md' ':!REVIEW_SPEC_A3UI.md'
```

vuoto. Porcelain allowlist: `spec/`, `REVIEW_SPEC_A3EP.md`, `REVIEW_SPEC_A3UI.md`.

---

## Documento

| Campo | Valore |
|-------|--------|
| File | `spec/SPEC_A3UI.md` |
| Versione | 0.1.0 |
| Consumes | SPEC_A3-EP 0.1.0 |
| Pagine AU-001 | 2 (915 parole; 1 pagina = 500 parole sezioni 1–10) |
| Frasi normative | 74, ciascuna con MUST / MUST NOT / SHOULD / MAY |
| Appendice A | Council / Critic / LLM come produttori non affidabili (non normativa) |
| Fixture AU-004 | `spec/fixtures/au-004-contradicted.json` |
| Test | `python3 spec/test_a3ui_spec.py` |

---

## Tabella audit — AU-001..010

| Test | Invariante | Percorso | PASS |
|------|------------|----------|------|
| AU-001 | spec ≤ 12 pagine; riga 1 = frase di governo | 915 parole, 2 pagine | **PASS** |
| AU-002 | ogni frase nel corpo è MUST/MUST NOT/SHOULD/MAY; prosa ispirazionale = 0 | 74 frasi; zero OS/restyler/guardiano/Council/Critic/LLM nel corpo | **PASS** |
| AU-003 | surface tuple completa, nessun campo omesso, placeholder esplicito | sezione 2; 7 campi | **PASS** |
| AU-004 | mapping mark→CTA; fixture CONTRADICTED → CTA disabilitata con reason | sezione 4 + `au-004-contradicted.json` | **PASS** |
| AU-005 | overlay COLLAPSED default, collapse automatico, pass-through | sezione 5; timeout SHOULD 20s | **PASS** |
| AU-006 | fallback blur / haptic Mac / permission / OpenGraph | sezione 8 | **PASS** |
| AU-007 | profili high-end/mid/blur-off/particles-off; minimo definito | sezione 9; minimo = blur-off + particles-off | **PASS** |
| AU-008 | a11y: stateDescription + lettura tipo+reason; reduced motion | sezione 7 | **PASS** |
| AU-009 | threat model: no cattura default; mark sopra vetro; no leak all'app sotto | sezione 10 | **PASS** |
| AU-010 | freeze codice | git diff vuoto fuori da spec/ e i REVIEW | **PASS** |

Gate: **verde**. Tag locale `a3ui-spec-v0.1`. Niente push.

---

## Vietato (rispettato)

- Nessuna prosa ispirazionale nel normativo.
- Zero claim "OS sensoriale", "restyler ogni app", "impedisce ogni errore", "guardiano".
- Council/Critic/LLM solo in appendice non normativa.
- `:agent` dichiarato applicazione, non parte della spec.
- Surface tuple non omessa.
- Nessuna modifica a codice esistente (solo `spec/test_spec.py` allowlist REVIEW).
- Nessun push.

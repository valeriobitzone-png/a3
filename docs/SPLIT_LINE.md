# SPLIT LINE — pubblico vs estensione (§9)

Lo split del repository **segue `SPEC_A3-EP` §9**, non un criterio ad hoc di packaging o di “cosa è utile al primo demo”.

## Regola

> Il sottoalbero pubblico è il CORE profile di §9 più i suoi artefatti di verifica (`:conformance`, `spec/`).
> Un’estensione **non** può entrare nel sottoalbero pubblico; il pubblico **non** può dipendere dall’estensione (né in `api`/`implementation`, né in `testImplementation`).

## Pubblico = SPEC_A3-EP §9 CORE + verifica

§9 richiede esattamente cinque elementi CORE:

| Elemento §9 | Moduli Gradle | Rationale |
|-------------|---------------|-----------|
| envelope (sezione 6) | `:core:envelope` | Tipi e serializzazione envelope |
| truth (sezione 3) | `:core:truth` | Classi di verità / promozione |
| ordering (sezione 5) | `:core:temporal` | Ordinamento temporale |
| confidence (sezione 7) | `:core:confidence` | Confidenza non compensante |
| postcondition (ENVIRONMENT, sezioni 2 e 6) | `:core:admission`, `:core:world-api`, `:core:world`, `:core:runtime`, `:core:action` | Ammissione, stato ENVIRONMENT, write-path, dispatch |

Supporto necessario al CORE (non estensione §9):

| Modulo | Rationale |
|--------|-----------|
| `:core:json` | Canonical JSON / schema support condiviso dal CORE |
| `:core:t12` | Lock / vettori T12 sul profilo CORE |

Verifica protocollo (primo rilascio pubblico):

| Artefatto | Rationale |
|-----------|-----------|
| `:conformance` | Suite di conformità al protocollo CORE |
| `spec/` | Specifica normativa (incluso `SPEC_A3-EP.md` §9) |

## Estensione (fuori dal primo rilascio)

Dichiarate esplicitamente fuori dal sottoalbero pubblico. Un’estensione **MAY** dipendere dal pubblico; il pubblico **MUST NOT** dipendere dall’estensione.

| Modulo / albero | Perché fuori (§9) |
|-----------------|-------------------|
| `:prediction` | Non è uno dei cinque elementi CORE; sensory/prediction è estensione |
| `:projection` | §9: CORE MUST NOT be required to implement canonical projection |
| `:intent-model` | Application / provider sopra CORE |
| `:agent` | §9: CORE MUST NOT be required to implement agent; appendix: `:agent` consumes CORE |
| `:a3ui` (+ `:a3ui:conformance*`) | Presentation / UI protocol, non CORE |
| `:renderers*` | Presentation |
| `:launcher` | Application shell |
| `:overlay*` | §9: CORE MUST NOT be required to implement overlay |
| `:adapters*` | Bridge applicativo (es. MCP); mint resta in CORE |
| `:showcase*` | Demo / a11y harness |
| `:broker` | Application coordination |

## Arco risolto (SPLIT-LINE)

**Prima:** `:core:runtime` `testImplementation(:prediction)` — arco pubblico → estensione (vietato anche se solo test).

**Dopo:** il gate test `PredictionWorldGateTest` (P1/P2/P7/P9) vive in `:prediction` con `testImplementation(:core:runtime)`. Direzione: estensione → pubblico. Il sottoalbero §9 non ha più archi verso l’estensione.

Grafo: `python3 docs/split-line-graph.py` (exit 0 ⇔ zero archi pubblico → estensione).

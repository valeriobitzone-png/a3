# REVIEW_AGENT_SURFACE

AUDIT-FIRST. Protocol: FASE AGENT-SURFACE — agente personale generativo di surface + preview ricchi. Nuovo modulo `:agent`. Consuma `:core:action`, `:core:admission`, `:a3ui`, `:broker`, `:adapters:mcp`. Frozen: `core/`, `a3ui/`, `renderers/`, `broker/`, `launcher/`, `adapters/`. Niente push. Tag `agent-v0.1` solo a gate verde.

**Contratto:** intent testo → plan rule-based (nessun LLM orchestratore) → sotto-attività → fonti (MCP fixture / API pubblica / LinkSurface) → 2–3 opzioni con asse epistemico → scelta → Command. Irreversibile e outbound passano dal broker gate default ON. Reversibile (dettaglio) è diretto. Esito → `ActionState` reso a3ui.

**Catalogo consumato, non toccato:** `transition`, `evaluate`/`acceptedObservation`, `SurfaceComposer`+`Epistemic.attach`, `Broker.flag`/`allow`/`run`, `McpCapabilityExecutor.call`.

---

## Freeze (AG-012)

```
git diff --stat -- core/ a3ui/ renderers/ broker/ launcher/ adapters/
```

vuoto. Solo `settings.gradle.kts` (include `:agent`), `agent/`, `REVIEW_AGENT_SURFACE.md`, `review-assets/agent/`.

---

## Come è fatto

| Pezzo | Ruolo |
|-------|--------|
| `PlanDecomposer` | Decomposizione deterministica. A → trasporto/hotel/calendario. B → acquisto. |
| `TravelCatalog` | MCP in-process: treno/volo/auto, 3 hotel, 3 slot calendario. Non è un server live. |
| `OpenGraphPreviewFetcher` | UA onesto, timeout, rate-limit, **robots.txt rispettato**. Mai fetch forzato. |
| Fallback preview | `opengraph` → `twitter` → `meta` → `minima` (retailer+query) → `deeplink`. Livello dichiarato per surface. |
| Amazon | Deep-link + metadata minimi. Tentativo pagina: 403 anti-bot → minima, **dichiarato inaffidabile**. Niente scraping. Niente PA-API. |
| `AdoptedApis` | eBay Developers (search URL), Open Library (search.json live), Open Food Facts, MusicBrainz. |
| `OutboundGate` | `flag irreversible: open, reserve`. Senza `allow` → `did not send`, URL non aperto. |
| `SurfaceShot` | Raster Java2D degli alberi `SurfaceComposer` reali (atomi + asse), non concept. |

---

## Tabella audit — AG-001..012

| Test | Invariante | Percorso | Negativo | PASS/GAP |
|------|------------|----------|----------|----------|
| AG-001 | Intent A → 3 gruppi, 2–3 opzioni ciascuno | `AgentRuntime.propose` + MCP fixture | un gruppo solo / 1 opzione | **PASS** |
| AG-002 | Ogni opzione ha asse; incerte HELD/UNKNOWN resi | `Epistemic.attach` su hotel navigli (HELD) e unlisted (UNKNOWN) | asse default su incerto | **PASS** |
| AG-003 | Intent B LinkSurface; outbound gate ON; senza consenso non apre | `flag open` + `allow` deny + `run open` senza grant | `Desktop.browse` silenzioso | **PASS** |
| AG-004 | Fixture OG completo → titolo+desc+immagine+prezzo, livello=`opengraph` | HttpServer `/og` + `og:price:amount` | livello sbagliato / crash | **PASS** |
| AG-005 | Senza OG → twitter / meta / minima, livello dichiarato, no crash | `/twitter` `/meta` `/bare` | eccezione sul bare | **PASS** |
| AG-006 | robots deny / timeout / anti-bot → minima, nessun fetch forzato | deny: solo `/robots.txt`; `/slow` timeout 250ms; `/amazon` 403 | GET `/product` dopo deny | **PASS** |
| AG-007 | Preview oltre TTL → STALE; prezzo senza fonte → UNKNOWN/low | `expiresAt` passato; claim `source=unattributed` conf 0 | STALE non esposto | **PASS** |
| AG-008 | Open Library reale → titolo/copertina, HIGH, freshness da timestamp API | `search.json?q=the+lord+of+the+rings` (curl `--ipv4`; Date HTTP) | mock / titolo inventato | **PASS** |
| AG-009 | Dettaglio reversibile → `read` diretto, senza gate | `broker.run("read")` unflagged → `worked` | `allow` richiesto | **PASS** |
| AG-010 | Esito → ActionState + verbo epistemico a3ui | COMPLETED/`done`; DENIED/`unknown`; TimeoutObserved/`unknown` | COMPLETED senza asse | **PASS** |
| AG-011 | Plan rule-based deterministico | stesso testo → stesso digest/attività; no OpenAI nel source | LLM nel decomposer | **PASS** |
| AG-012 | Freeze core/a3ui/renderers/broker/launcher/adapters | `git diff --stat` | tocco frozen | **PASS** |

**GAP: nessuno.**

Gate:

```
./gradlew :agent:test --offline
```

AG-001..012 verdi.

---

## Dichiarazione livelli preview

| Surface | Livello | Perché |
|---------|---------|--------|
| Fixture `/og` (AG-004, eBay in B) | `opengraph` | `og:title` + `og:description` + `og:image` + `og:price:amount=89.00` |
| Fixture `/twitter` (shop in B, AG-005) | `twitter` | Twitter Card, nessun OG |
| Fixture `/meta` | `meta` | `<title>` + `meta description` |
| Fixture `/bare` | `minima` | nessun meta; retailer+query |
| Amazon simulato `/amazon` 403 | `minima` | anti-bot; **OG dichiarato inaffidabile**; no retry, no scrape |
| robots `Disallow: /` | `minima` | pagina **non** fetchata |
| timeout `/slow` | `minima` | request abortita |

---

## Fonti: simulate vs reali vs API

| Fonte | Natura | Cosa dà |
|-------|--------|---------|
| `train-fixture` / `flight-fixture` / `drive-fixture` | **simulata** (MCP in-test) | 3 opzioni trasporto Roma–Milano |
| `hotel-fixture` / `hotel-model` / `unattributed` | **simulata** | 3 hotel; uno HELD (grounded-by-model); uno prezzo UNKNOWN |
| `local-calendar` / `osm-fixture` | **simulata locale** | slot 09:00 + tempo viaggio dichiarato OSM-like |
| HttpServer 127.0.0.1 OpenGraph/Twitter/meta | **simulata web** | preview ricco vs fallback vs robots/timeout/403 |
| Amazon PA-API | **non usato** (niente Associates) | — |
| Open Library `search.json` | **API reale** | *The Lord of the Rings*, cover `14625765`, `apiTimestamp` da header `Date` |
| eBay Developers / Open Food Facts / MusicBrainz | **adottate** (URL ufficiali; non hit live in questa slice) | search URL deterministici |

Open Library: `java.net.http.HttpClient` verso openlibrary.org va in connect-timeout su questo host (IPv6). Il client usa `curl --ipv4` come trasporto HTTP onesto sulla stessa API pubblica; il body è `search.json` reale, non una fixture.

---

## Media

Raster da alberi `ComposedTree` prodotti in test (atomi + `EpistemicAxis`), non mock di concetto.

| File | SHA-1 | Cosa è |
|------|-------|--------|
| `review-assets/agent/scenario-a.png` | `316ba03e68541ceaa99b16a37f60d2eced307c0c` | Intent A: 3 gruppi × 3 opzioni, assi LOW/HELD/UNKNOWN visibili |
| `review-assets/agent/scenario-a.html` | `b455950e91c155f9afe0196ac2a2fae2491b1da1` | Dump HTML dello stesso propose |
| `review-assets/agent/scenario-b.png` | `1cb82dd3356b493649880873c4843a0f95841755` | Intent B: Amazon minima, eBay opengraph, shop twitter |
| `review-assets/agent/scenario-b.html` | `83881d028244fd86d8ec5ec0fffa8cc707d6f053` | Dump B |
| preview opengraph | generated evidence removed during closeout scrub | Synthetic fixture only; no URL asset retained |
| `review-assets/agent/preview-fallback.png` | `1e994a9f30718fcee27171dcf443e010a2c3be91` | Twitter Card, **level=twitter** |
| `review-assets/agent/preview-stale.png` | `3f6e8331920611480b09884c13d7325f8f4b96a9` | Stesso OG composto oltre TTL → **freshness stale** |
| `review-assets/agent/gate-outbound.png` | `acd2b8e389a766d71b60a834540dba95b70580b5` | Consenso «sto per aprire … su amazon», **did not open** |
| Open Library API evidence | generated URL asset removed during closeout scrub | API assertion remains test-only |
| `review-assets/agent/action-outcome.png` | `4e1c13241f17b7597ee9cca5cc3e8d547943b592` | ActionState COMPLETED, verbo epistemico **done** |

---

## Vietato (verificato)

- Nessuno scraping di cataloghi; preview = meta HTTP o API ufficiale.
- `robots.txt` deny → nessun GET della pagina.
- Amazon: 403 → minima, un fetch, niente PA-API.
- Ogni surface porta asse; HELD/UNKNOWN/STALE esposti, non come default.
- Outbound/irreversibile: `flag` default ON; senza consenso non apre.
- Frozen trees intatti.
- Nessun push.
- Nessun LLM nel plan di questa slice.

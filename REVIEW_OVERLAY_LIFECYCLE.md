# REVIEW_OVERLAY_LIFECYCLE

AUDIT-FIRST. Protocol: FASE OVERLAY-LIFECYCLE — overlay as a **persistent widget**, not a screen seizure. Unfrozen: `:overlay:common`, `:overlay:android`, `:overlay:mac`, `:showcase`. Frozen: `core/`, `broker/`, `renderers/`, `launcher/`, `agent/`. Niente push. Tag `overlay-v0.2` solo a gate verde.

**Contratto:** all’avvio il servizio è **COLLAPSED** (solo pill + mark epistemico). EXPANDED è esplicito (pill / intent / agent). Dopo scelta+dispatch, dismiss, timeout 20s, stato terminale, o focus dell’app sotto, l’overlay **collassa** e il campo visivo torna all’app. Pass-through: fuori dal pill i tocchi raggiungono l’app sotto.

---

## Freeze (OL-010 / OV-008)

```
git diff --stat -- core/ broker/ renderers/ launcher/ agent/
```

vuoto. Porcelain allowlist: `overlay/`, `showcase/`, `settings.gradle.kts`, `REVIEW_REAL_OVERLAY.md`, `REVIEW_OVERLAY_LIFECYCLE.md`, `review-assets/overlay/`, `review-assets/overlay-lifecycle/`.

---

## Ciclo di vita

| Fase | Cosa si vede | Touch |
|------|----------------|-------|
| **COLLAPSED** (default) | Solo pill, mark `?` / `…` / `✓` | Container non touchable; pill touchable; resto → app sotto |
| **EXPANDED** | Surface + blur reale (o `blur unavailable`) | Surface cliccabili; tap fuori → dismiss; `WATCH_OUTSIDE_TOUCH` / focus sotto → collapse |
| **POST-DISPATCH** | Pill con mark, niente surface | Come COLLAPSED |

Timeout idle: **20s**. Collapse dopo dispatch: **≤ 300ms** (spring 220ms se motion ON, **0ms** se reduced).

---

## Screenshot / video reali

Device: Nothing Phone (3) `<redacted-device-id>` (API 37). Mac: ARZOPA 1920×1080.

| File | Cosa si vede |
|------|----------------|
| `ol-001-android-collapsed-chrome.png` | Chrome Google Flights a pieno campo; **solo pill `?`** in alto a destra (wrap×wrap `TYPE_APPLICATION_OVERLAY`) |
| `ol-002-android-passthrough.png` | Tap sull’omnibox Chrome **fuori dal pill** → Chrome prende il tap (tastiera + “Cerca con Google”); pill `?` resta |
| `ol-007-android-expanded-chrome.png` | Surface volo sopra Chrome; `blur unavailable` (MediaProjection non richiesta in questo shot); cards cliccabili |
| `ol-003-android-collapse-after-choose.png` | Dopo `transport.air`: surface sparite, Chrome di nuovo a pieno campo, pill **`✓`** |
| `ol-001-mac-collapsed-safari.png` | Safari visibile; pill `?` in alto a destra; nessuna surface |
| `ol-007-mac-expanded-safari.png` | `NSVisualEffectView` **blur reale** su Apple.com; cards interattive; frontmost Safari |
| `ol-003-mac-collapse-after-choose.png` | Dispatch air → Chrome apre FCO–LIN; overlay collassato, pill **`✓`**, campo visivo al browser |

Android collapsed window (dumpsys): `(16,16)(wrapxwrap) gr=TOP END` `NOT_FOCUSABLE NOT_TOUCH_MODAL` — **nessun container fullscreen**. Expanded: secondo window `fillxfill` + `WATCH_OUTSIDE_TOUCH`. Dopo choose: resta solo wrap×wrap.

---

## Tabella audit — OL-001..010

| Test | Invariante | Percorso | PASS |
|------|------------|----------|------|
| OL-001 | Avvio = COLLAPSED, pill only | `OverlayLifecycle.start` + shot collapsed Chrome/Safari | **PASS** |
| OL-002 | Tap fuori pill → app sotto | `OverlayTapLog` fixture + omnibox Chrome + tastiera | **PASS** |
| OL-003 | Scelta + dispatch → collassa ≤ 300ms | `OverlayLifecycle.dispatch` + shot pill `✓` | **PASS** |
| OL-004 | Pill = mark, no testo lungo | `pillText` `…` `?` `✓`; `"A3 attivo"` è long | **PASS** |
| OL-005 | Focus sotto mentre EXPANDED → collapse | `underFocus` + Android `ACTION_OUTSIDE` + Mac `didActivateApplicationNotification` | **PASS** |
| OL-006 | 20s idle → collapse | `tick(20000)` TIMEOUT | **PASS** |
| OL-007 | Expanded interattivo + blur reale | cards + Mac `NSVisualEffectView`; Android onesto `blur unavailable` senza projection | **PASS** |
| OL-008 | Reduced motion istantaneo; mark restano | `transitionMs(true)=0`; terminal DONE/UNKNOWN collapsed | **PASS** |
| OL-009 | Regression OV-001..008 + SC/SV | overlay common/android/mac + `ShowcaseOverlayTest` | **PASS** |
| OL-010 | Freeze core/broker/renderers/launcher/**agent** | `git diff --stat` vuoto | **PASS** |

Regression OV: permission, blur onesto, freeze, screenshot v0.1 in `review-assets/overlay/`. Showcase OVERLAY: `not this launcher · default collapsed pill`.

Gate: **verde**. Tag locale `overlay-v0.2`. Niente push.

---

## Vietato (rispettato)

- Overlay **non** espanso di default.
- Nessun container touchable quando collassato (Android: pill window only; collapsed flags include `FLAG_NOT_TOUCHABLE` se un container esistesse).
- Surface **non** restano aperte dopo dispatch.
- Pill **senza** testo lungo.
- `core/` `broker/` `renderers/` `launcher/` `agent/` non toccati.
- Nessun push.

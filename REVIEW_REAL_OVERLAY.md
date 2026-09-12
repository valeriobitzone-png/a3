# REVIEW_REAL_OVERLAY

AUDIT-FIRST. Protocol: FASE REAL-OVERLAY — a3ui as epistemic window manager **above real apps**. New modules: `:overlay:common`, `:overlay:android`, `:overlay:mac`. Consumes `:a3ui` / `:agent` (agent only in common tests + mac JVM). Frozen: `core/`, `broker/`, `renderers/`, `launcher/`. Niente push. Tag `overlay-v0.1` solo a gate verde.

**Contratto:** le surface dell'agent appaiono sopra Chrome / WhatsApp / Safari / Mail, non dentro il launcher. Il backdrop blur è cattura reale (MediaProjection → `OverlayBlur` su Android, `NSVisualEffectView.behindWindow` su Mac) oppure è assente con messaggio onesto `blur unavailable`. Nessun frost finto.

---

## Freeze (OV-008)

```
git diff --stat -- core/ broker/ renderers/ launcher/
```

vuoto. Porcelain allowlist: `overlay/`, `showcase/`, `settings.gradle.kts`, `REVIEW_REAL_OVERLAY.md`, `review-assets/overlay/`.

`:overlay:android` non dex-a `:agent` (AWT in `SurfaceShot`). L'agent è consumato in `:overlay:common` test + `:overlay:mac`.

---

## Permessi (dichiarazione)

| Piattaforma | Permesso | Default | Se negato |
|-------------|----------|---------|-----------|
| Android | `SYSTEM_ALERT_WINDOW` | **richiesto**, mai assunto | overlay **disabilitato**: `overlay disabled: SYSTEM_ALERT_WINDOW not granted` |
| Android | `MediaProjection` (cattura schermo) | **opzionale**, consenso esplicito | overlay resta; backdrop `UNAVAILABLE`; UI `blur unavailable` |
| Android | `POST_NOTIFICATIONS` + FGS `specialUse` | foreground “A3 attivo” | servizio non resta visibile come FGS |
| Mac | NSWindow `.floating` | nessuno speciale | overlay sempre creabile |
| Mac | Accessibility (`AXIsProcessTrusted` + `AXObserver`) | **opzionale** | surface manuali; `Accessibility optional: contextual surfaces require consent` |

Niente overlay di default. Niente blur finto se la cattura è negata. Android 14+ registra `MediaProjection.Callback` prima di `createVirtualDisplay`.

Device: Nothing Phone (3) `00022156R003829` (API 37). Mac: ARZOPA 1920×1080.

---

## Screenshot sopra app reali

| File | Cosa si vede |
|------|----------------|
| `ov-001-android-permission-denied.png` | `overlay disabled: SYSTEM_ALERT_WINDOW not granted` + Grant |
| `ov-002-android-blur-unavailable.png` | overlay sopra Google Flights, `blur unavailable` |
| `ov-002-android-real-blur.png` | MediaProjection attiva (pill rossa), Chrome Roma–Milano **sfocato**, glass cards sopra |
| `ov-003-android-chrome.png` | Google Flights visibile sotto le surface |
| `ov-003-android-whatsapp.png` | chat WhatsApp visibile sotto le surface |
| `ov-005-mac-safari-blur.png` | Safari apple.com/it **sfocato** (`NSVisualEffectView`), menu Safari, cards |
| `ov-006-mac-mail.png` | Mail sotto blur + overlay (sheet Apple Intelligence di Mail in primo piano) |
| `ov-007-android-flight-over-chrome.png` | intent volo + cards sopra Chrome |
| `ov-007-android-chrome-opened.png` | scelta air → Chrome apre i voli FCO–LIN (ITA) |

---

## Tabella audit — OV-001..008

| Test | Invariante | Percorso | PASS |
|------|------------|----------|------|
| OV-001 | `SYSTEM_ALERT_WINDOW` esplicito; deny → overlay off | policy + manifest + activity + screenshot deny | **PASS** |
| OV-002 | MediaProjection sì → `REAL_BLUR`; no → `blur unavailable` | deny screenshot + grant screenshot con Chrome sfocato | **PASS** |
| OV-003 | overlay sopra Chrome/WhatsApp | `ov-003-android-chrome.png` / `ov-003-android-whatsapp.png` | **PASS** |
| OV-004 | NSWindow floating senza permesso speciale; AX opzionale | Swift `.floating` + `AXObserver` + overlay live Safari | **PASS** |
| OV-005 | `.fullScreenUI` + `.behindWindow` → blur reale sopra Safari | `ov-005-mac-safari-blur.png` | **PASS** |
| OV-006 | overlay persistente sopra Safari/Mail | `ov-006-mac-safari-persist.png` / `ov-006-mac-mail.png` | **PASS** |
| OV-007 | volo Roma-Milano → surface overlay → URL nel browser | overlay su Chrome + `ov-007-android-chrome-opened.png` voli ITA | **PASS** |
| OV-008 | freeze core/broker/renderers/launcher | `git diff --stat` vuoto + ArchUnit | **PASS** |

Showcase: `ShowcaseOverlayTest.SV4_001` — livello OVERLAY non hosta `scene-train`. **PASS**.

Gate: **verde**. Tag locale `overlay-v0.1`. Niente push.

---

## Vietato (rispettato)

- Nessun blur simulato come sostituto della cattura (deny → `blur unavailable`; grant → bitmap catturata e sfocata).
- Showcase OVERLAY non monta le surface di volo.
- `SYSTEM_ALERT_WINDOW` non è assunto.
- MediaProjection negata non viene mascherata.
- `core/` `broker/` `renderers/` `launcher/` non toccati.
- Nessun push.

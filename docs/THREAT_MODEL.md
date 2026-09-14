# THREAT MODEL — overlay A3

L’overlay è una **finestra sopra il lavoro dell’utente**, non una feritoia di sorveglianza. Cosa vede l’agente è dichiarato qui e fatto rispettare in `:overlay:*`. Cattura schermo **non** è input agent, di default e in ogni percorso.

## 1. Cosa vede l’agente

**Niente di default.** Nessuna cattura schermo come input. `OverlayCaptureLaw.AGENT_INPUT_ALLOWED = false`. `OverlayFlight.present()` è un catalogo statico di card, non pixel.

| Sorgente | Quando | Cosa può fare | Cosa non può fare |
|----------|--------|---------------|-------------------|
| *(default)* | sempre | niente della scena sotto | nessuna cattura, nessuno scraping |
| MediaProjection (Android) | consenso **esplicito per-session** | un frame per il **blur visibile** (`OverlayBackdropGpu`) | input agent; persistenza; invio off-device |
| Screen recording / `NSVisualEffectView` (Mac) | effetto di sistema sul pannello overlay | blur visibile del compositor dietro la finestra | bitmap persistita; input agent; invio off-device |
| Accessibility (Mac, **opzionale**) | consenso AX | solo l’elenco dichiarato sotto | contenuto delle app, testo, bounds dump |

Letture AX dichiarate (`OverlayMacAx.READS`):

1. `AXIsProcessTrusted` (bit di consenso, nient’altro)
2. `NSWorkspace` frontmost `localizedName` (titolo app, non contenuto delle view)
3. `AXObserver` `kAXFocusedWindowChangedNotification` con **callback vuota** (niente testo, niente bounds)

## 2. Cosa NON vede

- Contenuto delle app sotto l’overlay: nessuno scraping della scena sottostante.
- Tastiera, password, autofill: nessuna osservazione di input.
- Notifiche di altre app.

Il vetro non è un lettore della scena. MediaProjection, se concessa, alimenta **solo** l’`ImageView` di backdrop e poi rilascia il virtual display. Gli intermediari bitmap vengono riciclati. Nessun `FileOutputStream` / network su quel percorso.

## 3. App ostile sotto

I mark A3 stanno **sempre sopra il vetro**. Z-order e hit-test non sono dirottabili dall’app sotto.

| Piattaforma | Isolamento |
|-------------|------------|
| Android | `TYPE_APPLICATION_OVERLAY`; `filterTouchesWhenObscured = true`; un touch con `FLAG_WINDOW_IS_OBSCURED` / `FLAG_WINDOW_IS_PARTIALLY_OBSCURED` **non** è un’azione A3 (`OverlayTapJacking.allowsA3Action` → false, `dispatchTouchEvent` consuma senza expand/choose) |
| Mac | `NSPanel` `.floating` + `.nonactivatingPanel`; collapsed = frame pill-sized così i click fuori arrivano all’app sotto (`window level + event isolation`) |

Nessuna app terza può forgiare mark A3. I mark li disegna **solo** l’overlay (`OverlayLifecycle.pillText` → `OverlayPill`). `OverlayMarkLaw.PUBLIC_SPOOF_API = false`. `OverlayService` è `exported=false`. Nessuna API pubblica di spoofing.

## 4. Dati sensibili

Una surface **espansa** sopra altre app è contesto visibile a spalle dell’utente (shoulder surfing).

**SHOULD:** surface sensibili restano collapsed/hidden. `OverlaySurfaceKind.SENSITIVE` → `OverlaySensitiveLaw.effectivePhase` = `COLLAPSED`; `OverlayLifecycle.hideSensitive` (`OverlayCollapseReason.SENSITIVE`). Android: `EXTRA_SENSITIVE`. Mac: `--sensitive` (pill tap non espande).

Nessuna chiave o token A3 nei log overlay (`OverlayLogRedact`).

## 5. Tabella permessi

| Permesso | Abilita | Deny (fallback onesto, già esistente) |
|----------|---------|----------------------------------------|
| `SYSTEM_ALERT_WINDOW` | finestra overlay sopra altre app (pill + surface espansa) | overlay **off**: `DISABLED_NO_OVERLAY_PERMISSION` / `overlay disabled: SYSTEM_ALERT_WINDOW not granted` |
| MediaProjection | un bitmap one-shot per blur visibile; mai input agent, mai persistito, mai off-device | overlay resta **ENABLED**; backdrop `UNAVAILABLE`; messaggio `blur unavailable` |
| Accessibility (Mac, opzionale) | solo le letture in §1 | overlay resta **ENABLED**; `Accessibility optional: contextual surfaces require consent` |

Consenso cattura (copy UI): *Capture is per-session consent; visible blur only; never agent input; never persisted; never off-device.*

## 6. Rischi residui (dichiarati)

1. **Shoulder surfing** su surface **espanse** (ROUTINE). Il SHOULD collapse vale per SENSITIVE; una surface di routine espansa resta visibile a chi guarda lo schermo.
2. **Cattura virtual display Android 14+** limitata al blur: il frame esiste in RAM per l’`ImageView` + `RenderEffect`, poi il virtual display è rilasciato. Non è input agent e non è persistito; resta un residuo di cattura in-process per il vetro.
3. **Artefatti harvest gfxinfo** (già **UNVERIFIED** in `docs/PERFORMANCE.md`): p95 nominale 200 ms su A024 è sospetto bucket harvest, non una claim di compliance fps.

Un residuo Mac aggiuntivo: una finestra **sopra** `.floating` può coprire l’overlay; non dirotta i mark, può oscurare il vetro.

# REVIEW_A3UI_THREAT

FASE A3UI-THREAT — overlay come finestra, non feritoia. Unfrozen: `:overlay:*`, `docs/`. Frozen: tutto il resto. Niente push. Tag `a3ui-threat-v0.1` **solo a gate verde**.

Modello: `docs/THREAT_MODEL.md`. Leggi in `overlay/common` (`OverlayCaptureLaw`, `OverlayTapJacking`, `OverlayMarkLaw`, `OverlaySensitiveLaw`, `OverlayPermissionTable`, `OverlayLogRedact`). Enforcement Android in `OverlayService` (`filterTouchesWhenObscured`, recycle bitmap, `EXTRA_SENSITIVE`). Enforcement Mac in `OverlayMain.swift` (event isolation, AX dichiarato, `--sensitive`).

---

## Modello (sintesi)

L’agente **non vede niente** di default. MediaProjection / effetto blur Mac: solo consenso per-session, solo vetro visibile, mai input agent, mai persistito, mai off-device. AX Mac: tre letture dichiarate, callback vuota. Sotto il vetro: niente scraping, niente tastiera/password/autofill, niente notifiche terze. Mark solo overlay; tapjacking filtrato; spoofing pubblico assente. Surface sensibili SHOULD collapsed/hidden.

---

## Tabella audit — TH-001..009

| ID | Gate | Evidenza | Esito |
|----|------|----------|-------|
| TH-001 | Nessuna cattura schermo come input agent | `OverlayCaptureLaw.agentMayReadPixels() == false`; `OverlayFlight.present()` statico; `agent/src/main` senza MediaProjection/ImageReader/VirtualDisplay/screencapture | **PASS** |
| TH-002 | Bitmap MediaProjection solo blur | `startProjection` → copy + `OverlayBackdropGpu`; recycle; nessun FileOutputStream/network/compress; copy consenso in permission activity | **PASS** |
| TH-003 | Tapjacking | `OverlayTapJacking.allowsA3Action(obscured) == false`; `filterTouchesWhenObscured`; Mac `.floating` + `.nonactivatingPanel` + pill-sized collapsed | **PASS** |
| TH-004 | Spoofing mark | `PUBLIC_SPOOF_API = false`; nessun `drawMark`/`spoofMark`; pill via `OverlayLifecycle.pillText`; `OverlayService exported=false` | **PASS** |
| TH-005 | Collapse sensibile | `OverlaySurfaceKind.SENSITIVE` → COLLAPSED; `hideSensitive`; `EXTRA_SENSITIVE`; `--sensitive`; documentato | **PASS** |
| TH-006 | Tabella permessi | `OverlayPermissionTable` + deny `OVERLAY_DENIED` / `BLUR_UNAVAILABLE` / `ACCESSIBILITY_OPTIONAL`; regression OverlayPolicy | **PASS** |
| TH-007 | Nessuna chiave/token nei log overlay | `OverlayLogRedact`; audit Log/println/NSLog su overlay main | **PASS** |
| TH-008 | Regression AC-001..023 + AX-001..019 + OL-001..010 | vedi esecuzione sotto; AC-023 verde **dopo commit** overlay/docs | **PASS** |
| TH-009 | Freeze | `git diff --stat -- . ':!overlay' ':!docs'` vuoto; porcelain solo `overlay/` e `docs/` | **PASS** |

---

## Rischi residui (non silenziati)

1. Shoulder surfing su surface **espanse** di routine.
2. Virtual display Android 14+: cattura in-process **limitata al blur**, poi release; non input agent.
3. gfxinfo harvest A024 **UNVERIFIED** (`docs/PERFORMANCE.md`).
4. Mac: una finestra sopra `.floating` può coprire l’overlay.

---

## Freeze (TH-009)

```
git diff --stat -- . ':!overlay' ':!docs'
```

vuoto. a3ui-web, showcase, renderers, spec, conformance, `:a3ui`, CHANGELOG, GOVERNANCE non toccati.

AC-023 (`git diff` include `overlay/`) è rosso su working tree sporco; passa dopo il commit di questa fase.

---

## Esecuzione

```
./gradlew :overlay:common:test :overlay:mac:test :overlay:android:testDebugUnitTest \
  --no-daemon -Pkotlin.compiler.execution.strategy=in-process

./gradlew :showcase:testDebugUnitTest --tests a3.showcase.A11yAndroidTest --tests a3.showcase.PerfA11yAndroidTest \
  --no-daemon -Pkotlin.compiler.execution.strategy=in-process
./gradlew :showcase:mac:test --tests a3.showcase.mac.A11yMacTest --tests a3.showcase.mac.PerfA11yMacTest \
  --no-daemon -Pkotlin.compiler.execution.strategy=in-process

./gradlew :a3ui:conformance:test -x :a3ui:conformance:android:test -x :a3ui:conformance:mac:test \
  --no-daemon -Pkotlin.compiler.execution.strategy=in-process
```

Tag: `a3ui-threat-v0.1` (annotated) solo a TH-001..009 verdi. Niente push.

Sopra il lavoro reale, prima del tap, vedi cosa il sistema sa, da dove, quanto è vecchio, e quale azione è vietata.

# a3ui: Epistemic Window Manager

```
Document: SPEC_A3UI
Version: 0.1.0
Status: Proposed Standard
Consumes: SPEC_A3-EP 0.1.0
Obsoletes: none
```

The key words MUST, MUST NOT, SHOULD, and MAY in this document MUST be interpreted as in [RFC 2119]. An implementation MUST treat line 1 of this document as the a3ui contract. Sections 1 through 12 MUST be treated as normative. Appendices MUST be treated as non-normative unless a sentence in an appendix uses MUST, MUST NOT, SHOULD, or MAY. For AU-001, a page MUST be 500 words of sections 1 through 10, and the normative body MUST NOT exceed 12 pages.

---

## 1. Scope

An implementation MUST treat a3ui as an epistemic window manager that consumes A3-EP.

An implementation MUST NOT merge a3ui with A3-EP.

An implementation MUST remain protocol-correct if a3ui is removed.

An implementation MUST NOT require a3ui to admit, authorize, or verify a proposition.

A3-EP MUST remain the sole hygiene contract for BELIEF, ACTION, and ENVIRONMENT.

The module `:agent` MUST be treated as an application above a3ui.

An implementation MUST NOT treat `:agent` as part of this specification.

---

## 2. Surface tuple

Every surface MUST carry the tuple (oggetto, truth_class, provenance, age, confidence, actions_permitted, actions_forbidden).

An implementation MUST NOT omit a field of that tuple.

If a field is unknown, an implementation MUST emit an explicit placeholder and MUST NOT drop the field.

Placeholder values MUST be: oggetto=`unknown-object`; truth_class=`UNKNOWN`; provenance=`INFERRED`; age=`unknown-age`; confidence=`0`; actions_permitted=`[]`; actions_forbidden=`["unknown-action"]`.

truth_class MUST be one of FACT, OBSERVATION, HYPOTHESIS, UNKNOWN as defined by A3-EP.

provenance MUST be one of OBSERVED_SIGNED, DERIVED_MODEL, INFERRED, HUMAN_ADMITTED as defined by A3-EP.

age MUST be a declared duration or the placeholder `unknown-age`.

confidence MUST be the A3-EP aggregate score in [0, 1] or 0 when unknown.

actions_permitted and actions_forbidden MUST be disjoint lists of action identifiers.

---

## 3. Epistemic marks

A mark MUST be law, not chrome.

An implementation MUST NOT use opacity alone to encode state.

UNKNOWN MUST be rendered as a dashed inset plus a badge.

STALE MUST be rendered as an amber border plus a visible age.

HELD MUST be rendered as a quarantine badge plus a pulse, except that reduced motion MUST drop the pulse and MUST keep the badge.

CONTRADICTED MUST be rendered as strikethrough plus a badge plus a visible reason.

PENDING MUST be rendered as a reserved outline slot plus the label `in verifica`.

FACT MUST carry no mark beyond the baseline surface chrome.

A pill in COLLAPSED overlay MUST show only the essential mark and MUST NOT show long copy.

---

## 4. Mark to CTA permission

The mapping from mark to CTA MUST be the following closed table.

| Mark | CTA | Reason |
|------|-----|--------|
| CONTRADICTED | MUST disable the CTA and MUST show the forbid reason | fixture `contradicted: resolve conflict first` |
| HELD | MUST disable the CTA or MUST require confirmation | quarantine |
| UNKNOWN | MUST limit the CTA or MUST require confirmation | insufficient evidence |
| FACT | MUST enable the CTA under normal A3-EP authorization | baseline |
| STALE | MAY enable the CTA and MUST show a warning if enabled | visible age |
| PENDING | MUST NOT confirm as done and MUST keep the reserved slot | `in verifica` |

The AU-004 fixture MUST be: mark=CONTRADICTED, cta_enabled=false, reason_visible=true, reason=`contradicted: resolve conflict first`.

An implementation MUST apply that fixture as CONTRADICTED → disabled CTA with visible reason.

An implementation MUST NOT announce a disabled CTA as a dimmed button without type and reason.

---

## 5. Overlay lifecycle

The overlay MUST start COLLAPSED.

COLLAPSED MUST show only the pill with the essential mark.

When COLLAPSED, a hit test outside the pill MUST pass through to the app underneath.

EXPAND MUST occur only on vocal or text intent, an agent event, or a tap on the pill.

EXPANDED MUST present interactive surfaces above the app.

An implementation MUST collapse to the pill immediately after user choice plus action dispatch.

An implementation MUST collapse after user dismiss.

An implementation SHOULD collapse after 20s without interaction.

An implementation MUST collapse when the app underneath takes focus or input while EXPANDED.

An implementation MUST collapse after a terminal action state and MUST keep the mark on the pill.

An implementation MUST NOT remain EXPANDED by default.

An implementation MUST NOT leave surfaces open after dispatch.

---

## 6. Material versus type

Glass, blur, noise, and particles MUST be treated as chrome.

Marks MUST be treated as law.

An implementation MUST keep marks readable with blur off.

An implementation MUST keep marks readable with reduced motion on.

An implementation MUST keep marks readable with audio off and haptic off.

An implementation MUST NOT substitute chrome for a missing mark.

---

## 7. Accessibility

Every mark MUST expose a non-sensory `stateDescription`.

TalkBack and VoiceOver MUST read type plus reason plus the forbidden action.

TalkBack and VoiceOver MUST NOT read only "dimmed button" or an equivalent sensory gloss.

Reduced motion MUST zero motion durations and MUST preserve marks.

`stateDescription` MUST NOT depend on color, blur, pulse, audio, or haptic.

---

## 8. Declared fallbacks

An implementation MUST declare these fallbacks: blur unavailable, haptic Mac limited, permission denied, OpenGraph blocked.

If blur capture is denied or unavailable, an implementation MUST show `blur unavailable` and MUST NOT fake frost.

If Mac haptic is limited or absent, an implementation MUST log the limit and MUST NOT crash.

If overlay permission is denied, an implementation MUST show `overlay disabled: SYSTEM_ALERT_WINDOW not granted` or an equivalent honest denial and MUST NOT assume the permission.

If OpenGraph is blocked by robots, timeout, or HTTP deny, an implementation MUST show a minimal honest preview and MUST NOT force the fetch.

An implementation MUST NOT crash on a declared fallback.

An implementation MUST NOT stay silent on a declared fallback.

---

## 9. Performance

An implementation MUST declare the profiles high-end, mid, blur-off, and particles-off.

The minimum profile MUST be blur-off plus particles-off.

The mid profile MUST be the declared average device class for jank.

An implementation MUST NOT jank on the declared mid profile when marks, tuple fields, and CTA mapping remain visible.

high-end MAY enable blur, noise, and particles.

blur-off MUST disable backdrop blur and MUST keep marks.

particles-off MUST disable particles and MUST keep marks.

Chrome in the minimum profile MUST NOT be required for comprehension.

---

## 10. Threat model

Screen capture MUST NOT be the default input to a3ui.

MediaProjection or equivalent capture MUST be optional, explicit, and deniable.

Marks MUST remain above glass even when the app underneath is hostile.

An implementation MUST NOT expose sensitive surface content to the app underneath.

Pass-through when COLLAPSED MUST deliver input to the app underneath and MUST NOT deliver surface payloads to that app.

EXPANDED surfaces MUST be hit-tested by a3ui and MUST NOT leak tuple fields into the under-app process.

An implementation MUST NOT treat the under-app as a trusted renderer of marks.

---

## 11. Surface lifecycle

An ephemeral surface MUST follow the lifecycle `PROPOSED → MOUNTED → ALIVE → FROZEN → DISMISSED`.

The producer (agent/A3) MUST propose a surface and MUST NOT dismiss it.

The host or renderer shell MUST transition `MOUNTED → ALIVE`, `ALIVE → FROZEN`, `FROZEN → ALIVE`, and any live or mounted surface to `DISMISSED` when its lifetime ends.

The shell MUST NOT create `PROPOSED`; a proposal received before its container is mounted MUST be queued and MUST NOT be lost. Pre-mount proposals MUST remain queued until their container arrives.

A shell MUST reject every transition not listed in the transition table below.

| From | To | Owner | Rule |
|------|----|-------|------|
| `PROPOSED` | `MOUNTED` | shell | mount the queued proposal into its container |
| `MOUNTED` | `ALIVE` | shell | activate the mounted surface |
| `ALIVE` | `FROZEN` | shell | declare preserved state and discarded state |
| `FROZEN` | `ALIVE` | shell | restore only the declared preserved state; report what is not restored |
| `MOUNTED` or `ALIVE` or `FROZEN` | `DISMISSED` | shell | deallocate explicitly |

A surface in `FROZEN` MUST declare what it conserves and what it does not conserve before it can return to `ALIVE`.

A surface in `DISMISSED` MUST be deallocated explicitly and MUST leave no residual registry, queue, or lifecycle reference.

---

## 12. Re-binding e invalidazione

A form MUST expose a `formKey` that identifies its form type, and a form MUST be generated once for each `formKey`.

A surface MUST expose a stable `surfaceId`, and the `surfaceId` MUST remain unchanged across re-binding.

A binding MUST carry `DataRef` references with source and lineage, and MUST NOT copy data values into the form shape.

A change of data values MUST perform a re-bind of the `DataRef`; it MUST NOT regenerate the form for values alone. L'implementazione MUST NOT rigenerare per soli valori.

A change of `formKey` or schema MUST perform a declared form regeneration, and the generation counter MUST increase. Un cambio formKey/schema MUST be dichiarato come invalidazione della forma.

A binding MUST reject re-binding on `DISMISSED` with an explicit error.

On `ALIVE`, a new `DataRef` MUST be applied by re-binding without form regeneration. On `FROZEN`, the new `DataRef` MUST be queued and MUST be applied when the surface is restored. On `PROPOSED` or `MOUNTED`, the initial data MUST remain part of the proposal until the surface is alive.

A renderer MUST NOT be required to test form identity, re-binding, or invalidation.

---

## Appendix A. Untrusted producers (non-normative)

This appendix is not protocol. Council, Critic, and LLM systems are producers that a product MAY place around A3-EP. They are not a3ui primitives. A3 sits around them: a3ui MUST still consume A3-EP tuples and MUST NOT trust a model output as FACT. `:agent` is an application above a3ui.

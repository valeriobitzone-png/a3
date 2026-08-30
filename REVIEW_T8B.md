# REVIEW_T8B — schermo onesto

**Unfrozen:** `:launcher` only. **No tag.**  
**Frozen SOURCE intact:** `core/`, `prediction/`, `projection/`, `a3ui/`, `renderers/`, `adapters/`, `intent-model/`.  
Renderer 0.2 catalog is read-only. No Node built by hand. No labels drawn in `A3Screen`.

Screenshot (not in git): `<home>/Downloads/a3-t8b-first-frame.png`

---

## F1 — first frame

**Invariant:** pipeline runs in `A3HostViewModel.init` before `setContent`. `output` is non-null on the first composition. If it were null, a white empty `Column` (`a3-empty`) is shown. Never a skipped `setContent`. Never the string `loading`.

**How:** `MainActivity` no longer gates on `if (output != null)`. Host `Box` is `Color.White`. Window background is white.

**Result:** **PASS.** Emulator is white, not black. Compose dump contains `Ada` / `12.40` / residual `08:45`.

---

## F2 — fixture atoms (frozen mapping)

**Invariant:** `DemoFixtures.presentation()` only injects meanings `SurfaceComposer` already maps: several `timetable` slots, `passenger`, `price`, `confirm` (`ticket.owned`). Residual `departure` stays a `text` node (catch-all).

**How:** no extra Compose in `A3Screen`. No hand-built `Node`.

**Result:** **PASS.**

---

## F3 — list times and action name

**Invariant to check:** after the fixture, are timetable values and the action copy visible as paint?

**On disk (frozen `ComposeCatalog`):** `item` and `action` are `Box` + `Children` only. `node.text` is painted solely for `text` (`BasicText`) and `field` (`BasicTextField`). `SurfaceComposer` puts `Binding content` on the **item** and on the **action**, not on a child `text` node.

**Interpreter (launcher test):** `item_train.slot.{a,b,c}.text` is `08:45` / `09:12` / `10:03`; `action_ticket.owned.text` is `true`. The copy exists on the data tree.

**Emulator (`a3-t8b-first-frame.png` + uiautomator):** visible strings are `Ada` (field), `12.40` (price text), `08:45` (residual **text** `train.departure`). No `09:12`, no `10:03`. Confirm is a clickable view with empty `text=""` at ~48dp (`[0,569][126,695]` on xxhdpi). List slots are ~8dp empty views, no copy.

**Result:** **GAP (renderer 0.2).** Binding on `item`/`action` is not painted. Not closable in `:launcher` without a workaround (forbidden). No renderer/a3ui edit.

---

## F4 — tap confirm

Confirm is **not** visually present (empty `action` Box). F4 is **not** claimed. No invented button. Existing unit path (testTag `action_ticket.owned` → `TrustHoldOverlay` → grant → `Runtime.execute`) is unchanged from T8 and is not a visual proof.

**Result:** **N/A** (blocked by F3 GAP).

---

## L9–L11

| Test | Invariante | Percorso | Esito |
|------|------------|----------|-------|
| L9 | first frame `output != null` | `train()` without calling `start()`; compose shows `a3-catalog`, not `a3-empty` | **PASS** |
| L9b | null output → empty stack | `A3Screen(null)` → `a3-empty`, white host | **PASS** |
| L10 | no `Confirm` / `Conferma` / `loading` in `launcher/src/main` | source walk | **PASS** |
| L11 | `:launcher:testDebugUnitTest` + `:intent-model:test` | no `--rerun-tasks` | **PASS** |

L11 log (2026-08-30, Mini 16GB):

```
./gradlew :launcher:assembleDebug :launcher:testDebugUnitTest --no-parallel
… 18 tests PASSED …
BUILD SUCCESSFUL in 30s

./gradlew :intent-model:test --no-parallel
BUILD SUCCESSFUL in 3s
11 actionable tasks: 11 up-to-date
```

Frozen modules not retested. No aggregate `--rerun-tasks`.

---

### Audit

| Item | Invariante | Percorso reale | Perché non tautologico | Negativo | Esito |
|------|------------|----------------|------------------------|----------|-------|
| F1 | primo frame non nero | `init` + always `A3Screen` + white host | MainActivity no longer skips compose | null → `a3-empty`, never `loading` | **PASS** |
| F2 | atomi del mapping frozen | `timetable`×3, passenger, price, confirm | composer IDs `item_*` / `field_*` / `text_*` / `action_*` | no hand-built Node | **PASS** |
| F3 | orari lista + nome action visibili | screenshot + dump + catalog source | interpreter has the copy; catalog does not paint it on item/action | `09:12`/`10:03`/action copy assenti | **GAP renderer 0.2** |
| F4 | tap confirm → overlay → execute | not forced | F3 GAP; no invented button | — | **N/A** |
| L9 | output first frame | VM init | `start()` not required | null path is empty stack | **PASS** |
| L10 | no Confirm/loading copy | grep main kt | overlays still say `approve`/`return` | those three literals absent | **PASS** |
| L11 | modulo tests | gradle without `--rerun-tasks` | intent-model UP-TO-DATE | aggregato `--rerun-tasks` not run | **PASS** |

**T8b GAP:** F3 — renderer 0.2 does not paint `RenderedNode.text` on `item` or `action`. Stop. No workaround in `:launcher`.

---

## Grep / freeze

`git diff` on `core/ prediction/ projection/ a3ui/ renderers/ adapters/ intent-model/` is empty.

No `Confirm` / `Conferma` / `loading` in `launcher/src/main`.

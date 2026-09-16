# REVIEW_T8D — un solo launcher entry

**Unfrozen:** `launcher/src/debug/AndroidManifest.xml` (eliminato) + this review. **No tag.**  
**Frozen SOURCE intact:** `core/`, `prediction/`, `projection/`, `a3ui/`, `renderers/`, `adapters/`, `intent-model/`, e tutto `launcher/` tranne quel debug manifest. `MainActivity.kt` non toccato.

Screenshot (not in git): `<local-downloads-path-redacted>/a3-t8d-single-entry.png`

---

## Audit (disco, pre-fix)

Il debug manifest registrava `androidx.activity.ComponentActivity` con MAIN/LAUNCHER. `MainActivity.kt` importa/estende `ComponentActivity` come classe base: non è un riferimento all'activity vuota. Nessun test, nessun Intent, nessuna altra source del repo referenziava l'activity debug.

Dopo il taglio il file non dichiarava nient'altro → eliminato. `src/main/AndroidManifest.xml` resta l'unico launcher: `.MainActivity`.

`debugImplementation("androidx.compose.ui:ui-test-manifest")` (file Gradle frozen) continua a fondere `androidx.activity.ComponentActivity` `exported=true` **senza** filter MAIN/LAUNCHER. Non è un secondo entry launcher. Fuori slice.

---

## D1 — MAIN a un entry

**Invariant:** `dumpsys package a3.launcher` su MAIN elenca solo `a3.launcher/.MainActivity`. Zero `androidx.activity.ComponentActivity` in quelle righe.

**How:** `./gradlew :launcher:clean :launcher:assembleDebug` (niente `--rerun-tasks` aggregato). APK `launcher/build/outputs/apk/debug/launcher-debug.apk` mtime `2026-08-30T17:28:44+02:00` > `f18c393` (`2026-08-30T15:19:07+02:00`). Cold reinstall Tab A9 device id `<redacted-device-id>`.

```
adb -s <redacted-device-id> shell dumpsys package a3.launcher | grep -A3 "android.intent.action.MAIN"
      android.intent.action.MAIN:
        66233db a3.launcher/.MainActivity filter 9fefe78
          Action: "android.intent.action.MAIN"
          Category: "android.intent.category.LAUNCHER"
```

**Negative:** `ComponentActivity` assente dal blocco MAIN.

**Result:** **PASS.**

---

## D2 — cold launch dipinge

**Invariant:** `monkey -p a3.launcher -c android.intent.category.LAUNCHER 1` mette il focus su `.MainActivity` (non `ComponentActivity`) e dipinge Ada / 08:45 / 09:12 / 10:03.

**How:** stesso device. Focus:

```
mCurrentFocus=Window{40706ba u0 a3.launcher/a3.launcher.MainActivity}
mFocusedApp=ActivityRecord{15595857 u0 a3.launcher/.MainActivity t123}
```

uiautomator texts: `08:45`, `09:12`, `10:03`, `Ada`, `12.40`, `hold 08:45`. Screenshot `a3-t8d-single-entry.png`. Il FAB viola è chrome Samsung (accessibilità), non A3.

**Result:** **PASS.**

---

## D3 — modulo test

Command: `./gradlew :launcher:testDebugUnitTest --no-parallel`  
Host: locale Mini 16GB, 2026-08-30. Exit code: 0. Frozen modules not retested. No aggregate `--rerun-tasks`.

```
LauncherAcceptanceTest > L9_first_composition_uses_output_not_empty_stack PASSED
LauncherAcceptanceTest > L2_train_path_02_has_list_departure_and_clickable_confirm PASSED
LauncherAcceptanceTest > L9_null_output_shows_empty_stack_not_blank_host PASSED
LauncherAcceptanceTest > L3_two_outputs_carry_morph_ids_and_interpreted_stiffness PASSED
LauncherAcceptanceTest > L5_irreversible_shows_trust_dialog_allow_does_not PASSED
LauncherAcceptanceTest > L7_catalog_semantics_are_foundation_roles PASSED
LauncherAcceptanceTest > L6_match_does_not_show_rollback_overlay PASSED
LauncherAcceptanceTest > L6_mismatch_shows_rollback_overlay PASSED
LauncherArchitectureTest > F3_catalog_paints_node_text_on_item_and_action PASSED
LauncherArchitectureTest > L5_main_does_not_write_world PASSED
LauncherArchitectureTest > L1_main_has_no_material_widgets_or_network PASSED
LauncherArchitectureTest > L1_gradle_does_not_depend_on_prediction_or_mcp PASSED
LauncherArchitectureTest > L10_main_has_no_confirm_loading_literals PASSED
LauncherHostTest > L4_prefetch_hits_then_misses_after_belief_version_bump PASSED
LauncherHostTest > F2_presentation_uses_frozen_meanings_only PASSED
LauncherHostTest > F3_interpreter_copies_binding_onto_item_and_action PASSED
LauncherHostTest > L9_first_frame_output_is_non_null_without_calling_start PASSED

BUILD SUCCESSFUL in 7s
```

**Result:** **PASS.**

---

### Audit

| Item | Invariante | Percorso reale | Perché non tautologico | Negativo | Esito |
|------|------------|----------------|------------------------|----------|-------|
| D1 | un MAIN/LAUNCHER | dumpsys su Tab A9 dopo cold install APK nuovo | due entry prima del taglio | `ComponentActivity` assente da MAIN | **PASS** |
| D2 | launch → MainActivity dipinge | monkey + uiautomator + screenshot | focus non è l'activity vuota | Ada / 08:45 / 09:12 / 10:03 presenti | **PASS** |
| D3 | `:launcher:testDebugUnitTest` | gradle senza `--rerun-tasks` | `createComposeRule` non usava l'activity debug | 17 PASSED | **PASS** |

**T8d GAP: nessuno** in questa slice. Activity `ComponentActivity` senza filter resta nel merge `ui-test-manifest` (Gradle frozen).

---

## Freeze

`git diff` on `core/ prediction/ projection/ a3ui/ renderers/ adapters/ intent-model/` is empty. `MainActivity.kt` unchanged.

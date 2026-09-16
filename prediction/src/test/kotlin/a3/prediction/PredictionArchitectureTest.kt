// SPDX-License-Identifier: Apache-2.0
// Part of the A3 universe. See LICENSE.
package a3.prediction

import com.tngtech.archunit.core.importer.ImportOption
import com.tngtech.archunit.junit.AnalyzeClasses
import com.tngtech.archunit.junit.ArchTest
import com.tngtech.archunit.lang.ArchRule
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses

/**
 * P11 — compile-time / bytecode barrier.
 *
 * worldState.apply(preparedState)   ← NON COMPILA: nessun overload esiste.
 */
@AnalyzeClasses(
    packages = ["a3.prediction"],
    importOptions = [ImportOption.DoNotIncludeTests::class]
)
class PredictionArchitectureTest {
    @ArchTest
    val P11_prediction_does_not_depend_on_world_or_runtime: ArchRule =
        noClasses()
            .that().resideInAPackage("a3.prediction..")
            .should().dependOnClassesThat()
            .resideInAnyPackage("a3.core.world", "a3.core.runtime..")
}

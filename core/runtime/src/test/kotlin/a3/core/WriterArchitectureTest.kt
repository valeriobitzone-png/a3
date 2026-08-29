package a3.core

import a3.core.world.AcceptedObservation
import a3.core.world.WorldState
import com.tngtech.archunit.core.importer.ClassFileImporter
import com.tngtech.archunit.core.importer.ImportOption
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WriterArchitectureTest {
    private val production = ClassFileImporter()
        .withImportOption(ImportOption.DoNotIncludeTests())
        .importPackages("a3.core")

    @Test
    fun W3_single_entry_world_state_apply_is_the_only_committed_write() {
        assertFailsWith<ClassNotFoundException> {
            Class.forName("a3.core.world." + "ObservationAcceptance")
        }

        val applyMethods = WorldState::class.java.declaredMethods.filter { it.name == "apply" }
        assertEquals(1, applyMethods.size)
        assertEquals(AcceptedObservation::class.java, applyMethods.single().parameterTypes.single())
        assertTrue(WorldState::class.java.methods.none { it.name == "write" })

        val committedField = WorldState::class.java.getDeclaredField("committed")
        assertFalse(java.lang.reflect.Modifier.isPublic(committedField.modifiers))

        noClasses()
            .that().resideInAPackage("a3.core..")
            .and().doNotHaveFullyQualifiedName("a3.core.runtime.Runtime")
            .and().doNotHaveFullyQualifiedName("a3.core.runtime.WorldStateReplay")
            .should().callMethod(WorldState::class.java, "apply", AcceptedObservation::class.java)
            .because("WorldState.apply is the only committed writer; live Runtime and WorldStateReplay may call it")
            .check(production)

        classes()
            .that().haveFullyQualifiedName("a3.core.runtime.Runtime")
            .should().callMethod(WorldState::class.java, "apply", AcceptedObservation::class.java)
            .check(production)

        classes()
            .that().haveFullyQualifiedName("a3.core.runtime.WorldStateReplay")
            .should().callMethod(WorldState::class.java, "apply", AcceptedObservation::class.java)
            .check(production)

        noClasses()
            .that().resideInAPackage("a3.core.events..")
            .should().callMethod(WorldState::class.java, "apply", AcceptedObservation::class.java)
            .check(production)

        val runtimeSrc = File("src/main/kotlin/a3/core/runtime/Runtime.kt").readText()
        assertTrue(runtimeSrc.contains("world.apply("))
        assertTrue(runtimeSrc.contains("mintAcceptedObservation"))
        assertFalse(runtimeSrc.contains("Observation" + "Acceptance"))
        assertFalse(runtimeSrc.contains(".integrate("))

        val mainTrees = listOf(
            File("../world/src/main"),
            File("../world-api/src/main"),
            File("src/main")
        )
        val hits = mainTrees.flatMap { dir ->
            dir.walkTopDown()
                .filter { it.isFile && it.extension == "kt" }
                .flatMap { file -> file.readLines().map { line -> file to line } }
        }.filter { (_, line) ->
            line.contains("Observation" + "Acceptance" + ".apply") &&
                !line.contains("WorldState")
        }
        assertTrue(hits.isEmpty(), "lateral write still present: $hits")
    }

    @Test
    fun W5_planner_and_transition_do_not_mint_or_call_world_apply() {
        noClasses()
            .that().resideInAPackage("a3.core.planner..")
            .should().dependOnClassesThat().areAssignableTo(AcceptedObservation::class.java)
            .check(production)

        noClasses()
            .that().resideInAPackage("a3.core.planner..")
            .should().callMethod(WorldState::class.java, "apply", AcceptedObservation::class.java)
            .check(production)

        noClasses()
            .that().resideInAPackage("a3.core.capability..")
            .should().dependOnClassesThat().areAssignableTo(AcceptedObservation::class.java)
            .check(production)

        noClasses()
            .that().resideInAPackage("a3.core.capability..")
            .should().callMethod(WorldState::class.java, "apply", AcceptedObservation::class.java)
            .check(production)

        val plannerSrc = File("src/main/kotlin/a3/core/planner/Planner.kt").readText()
        val transitionSrc = File("src/main/kotlin/a3/core/capability/Transition.kt").readText()
        for (src in listOf(plannerSrc, transitionSrc)) {
            assertFalse(src.contains("mintAcceptedObservation"))
            assertFalse(src.contains("AcceptedObservationToken"))
            assertFalse(src.contains("world.apply"))
            assertFalse(src.contains("import a3.core.world.WorldState"))
            assertFalse(src.contains("WorldState.apply"))
        }
        assertTrue(transitionSrc.contains(".integrate("))
        assertFalse(plannerSrc.contains("mintAccepted"))
    }
}

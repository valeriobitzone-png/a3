package a3.intent

import a3.core.model.Intent
import a3.core.runtime.Runtime
import com.tngtech.archunit.core.importer.ClassFileImporter
import com.tngtech.archunit.core.importer.ImportOption
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses
import java.io.File
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class IntentModelArchitectureTest {
    private val production = ClassFileImporter()
        .withImportOption(ImportOption.DoNotIncludeTests())
        .importPackages("a3.intent", "a3.core.runtime", "a3.core.planner")

    @Test
    fun I2_write_barrier_no_world_write_or_second_engine() {
        noClasses()
            .that().resideInAPackage("a3.intent..")
            .should().dependOnClassesThat()
            .haveFullyQualifiedName("a3.core.world." + "Accepted" + "Observation")
            .check(production)

        noClasses()
            .that().resideInAPackage("a3.intent..")
            .should().dependOnClassesThat()
            .haveSimpleName("WorldState")
            .check(production)

        noClasses()
            .that().resideInAPackage("a3.intent..")
            .should().dependOnClassesThat()
            .haveSimpleName("Fore" + "cast")
            .check(production)

        noClasses()
            .that().resideInAPackage("a3.intent..")
            .should().callMethod(Instant::class.java, "now")
            .check(production)

        val infer = IntentProvider::class.java.methods.single { it.name == "infer" }
        assertEquals(Intent::class.java, infer.returnType)
        assertEquals(IntentContext::class.java, infer.parameterTypes.single())

        val hits = File("src").walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .flatMap { file -> file.readLines().asSequence().map { line -> file to line } }
            .filter { (_, line) ->
                val trimmed = line.trim()
                if (trimmed.startsWith("//")) return@filter false
                line.contains("Accepted" + "Observation") ||
                    line.contains("WorldState" + ".apply") ||
                    line.contains("Fore" + "cast") ||
                    line.contains("predict" + "(") ||
                    Regex("""\bmi""" + """nt\s*\(""").containsMatchIn(line)
            }
            .toList()
        assertTrue(hits.isEmpty(), "write/engine tokens in intent-model src: $hits")
    }

    @Test
    fun I3_core_runtime_does_not_depend_on_intent_model() {
        noClasses()
            .that().resideInAPackage("a3.core.runtime..")
            .should().dependOnClassesThat()
            .resideInAPackage("a3.intent..")
            .check(production)

        noClasses()
            .that().resideInAPackage("a3.core.planner..")
            .should().dependOnClassesThat()
            .resideInAPackage("a3.intent..")
            .check(production)

        assertFalse(Runtime::class.java.methods.any { m ->
            m.parameterTypes.any { it.name.startsWith("a3.intent") }
        })
    }
}

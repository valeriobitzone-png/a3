package a3.adapters.mcp

import a3.core.runtime.Executor
import a3.core.runtime.Runtime
import com.tngtech.archunit.core.importer.ClassFileImporter
import com.tngtech.archunit.core.importer.ImportOption
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class McpAdapterArchitectureTest {
    private val production = ClassFileImporter()
        .withImportOption(ImportOption.DoNotIncludeTests())
        .importPackages("a3.adapters.mcp", "a3.core.runtime")

    @Test
    fun S2_adapter_stops_at_observation_and_does_not_write_world() {
        noClasses()
            .that().resideInAPackage("a3.adapters.mcp..")
            .should().dependOnClassesThat()
            .haveFullyQualifiedName("a3.core.admission." + "Accepted" + "Observation")
            .check(production)

        noClasses()
            .that().resideInAPackage("a3.adapters.mcp..")
            .should().dependOnClassesThat()
            .haveFullyQualifiedName("a3.core.runtime." + "Accepted" + "Observation" + "Token")
            .check(production)

        noClasses()
            .that().resideInAPackage("a3.adapters.mcp..")
            .should().dependOnClassesThat()
            .haveSimpleName("BeliefWriter")
            .check(production)

        val execute = McpCapabilityExecutor::class.java.methods.single { it.name == "execute" }
        assertEquals(a3.core.world.Observation::class.java, execute.returnType)
        assertTrue(Executor::class.java.isAssignableFrom(McpCapabilityExecutor::class.java))

        val main = File("src/main")
        val hits = main.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .flatMap { file -> file.readLines().asSequence().map { line -> file to line } }
            .filter { (_, line) ->
                line.contains("Accepted" + "Observation") ||
                    line.contains("BeliefWriter" + ".apply") ||
                    line.contains("mint" + "Accepted")
            }
            .toList()
        assertTrue(hits.isEmpty(), "write tokens in adapter main: $hits")
    }

    @Test
    fun S3_core_runtime_does_not_depend_on_adapters() {
        noClasses()
            .that().resideInAPackage("a3.core.runtime..")
            .should().dependOnClassesThat()
            .resideInAPackage("a3.adapters..")
            .check(production)

        assertFalse(Runtime::class.java.methods.any { m ->
            m.parameterTypes.any { it.name.startsWith("a3.adapters") }
        })
    }
}

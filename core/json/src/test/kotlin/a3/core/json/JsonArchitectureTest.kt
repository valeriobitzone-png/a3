package a3.core.json

import com.tngtech.archunit.core.importer.ImportOption
import com.tngtech.archunit.junit.AnalyzeClasses
import com.tngtech.archunit.junit.ArchTest
import com.tngtech.archunit.lang.ArchRule
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses
import java.io.File
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@AnalyzeClasses(
    packages = ["a3.core.json"],
    importOptions = [ImportOption.DoNotIncludeTests::class]
)
class JsonArchitectureTest {
    @ArchTest
    val J1_json_is_blind: ArchRule =
        noClasses()
            .that().resideInAPackage("a3.core.json..")
            .should().dependOnClassesThat()
            .resideInAnyPackage(
                "a3.core.world",
                "a3.core.world..",
                "a3.core.runtime..",
                "a3.prediction..",
                "a3.projection..",
                "a3.a3ui..",
                "a3.renderers.."
            )

    @Test
    fun J1_gradle_does_not_depend_on_layer_modules() {
        val gradle = File("build.gradle.kts").readText()
        assertFalse(gradle.contains("project(\":core:world\")"))
        assertFalse(gradle.contains("project(\":core:world-api\")"))
        assertFalse(gradle.contains("project(\":core:runtime\")"))
        assertFalse(gradle.contains("project(\":prediction\")"))
        assertFalse(gradle.contains("project(\":projection\")"))
        assertFalse(gradle.contains("project(\":a3ui\")"))
        assertFalse(gradle.contains("project(\":renderers"))
        assertFalse(gradle.contains("project(\":adapters"))
        assertFalse(gradle.contains("project(\":launcher\")"))
        assertTrue(gradle.contains("jackson-databind"))
    }

    @Test
    fun J2_engine_rules_live_only_in_core_json() {
        val engine = File("src/main/kotlin/a3/core/json/CanonicalJson.kt").readText()
        assertTrue(engine.contains("TreeMap"))
        assertTrue(engine.contains("if (v != null || !omitNulls)"))
        assertTrue(engine.contains("canonical JSON cannot encode"))
        assertTrue(engine.contains("padStart(4, '0')"))
        assertTrue(engine.contains("is Instant"))
        assertFalse(engine.contains("Forecast"))
        assertFalse(engine.contains("BeliefState"))
        assertFalse(engine.contains("ProjectionCandidate"))
        assertFalse(engine.contains("RenderedOutput"))
        assertFalse(engine.contains("fun of("))
        assertFalse(engine.contains("fun ofState"))

        val facades = listOf(
            File("../runtime/src/main/kotlin/a3/core/serialize/CanonicalJson.kt"),
            File("../../prediction/src/main/kotlin/a3/prediction/serialize/CanonicalJson.kt"),
            File("../../projection/src/main/kotlin/a3/projection/serialize/CanonicalJson.kt"),
            File("../../a3ui/src/main/kotlin/a3/a3ui/serialize/CanonicalJson.kt"),
            File("../../renderers/android-core/src/main/kotlin/a3/renderers/android/core/serialize/CanonicalJson.kt")
        )
        for (file in facades) {
            val text = file.readText()
            assertTrue(file.exists(), file.path)
            assertTrue(text.contains("object CanonicalJson"), file.path)
            assertFalse(text.contains("canonical JSON cannot encode"), file.path)
            assertFalse(text.contains("padStart(4, '0')"), file.path)
            assertFalse(text.contains("private fun number"), file.path)
            assertFalse(text.contains("private fun string"), file.path)
            assertFalse(text.contains("TreeMap"), file.path)
        }
    }
}

class JsonEngineTest {
    @Test
    fun J3_engine_primitives_match_pre_move_corpus() {
        val t = Instant.parse("2026-08-27T08:00:00Z")
        val golden = load("j3-primitives.txt")
        assertEquals(golden["null"], CanonicalJson.encode(null))
        assertEquals(golden["true"], CanonicalJson.encode(true))
        assertEquals(golden["false"], CanonicalJson.encode(false))
        assertEquals(golden["str"], CanonicalJson.encode("a\"b\\c\n"))
        assertEquals(golden["int"], CanonicalJson.encode(1))
        assertEquals(golden["double_int"], CanonicalJson.encode(1.0))
        assertEquals(golden["double"], CanonicalJson.encode(1.5))
        assertEquals(golden["instant"], CanonicalJson.encode(t))
        assertEquals(golden["map"], CanonicalJson.encode(mapOf("b" to 1, "a" to 2, "n" to null)))
        assertEquals(golden["list"], CanonicalJson.encode(listOf(3, 1, 2)))
        assertEquals(golden["empty_obj"], CanonicalJson.encode(emptyMap<String, Any?>()))
        assertEquals(golden["empty_arr"], CanonicalJson.encode(emptyList<Any?>()))
    }

    @Test
    fun J4_validation_parity_valid_and_invalid() {
        val goal =
            """{"desired_state":[{"confidence":0.9,"expires_at":"2026-08-27T08:01:00Z","k":"ticket.owned","observed_at":"2026-08-27T08:00:00Z","source":"train","v":true}],"id":"g","intent_ref":"i","priority":0.5}"""
        SchemaValidator.validateCanonical("goal.schema.json", goal)

        val presentation =
            """{"atoms":[{"k":"train.passenger","meaning":"passenger","priority":40,"v":"Ada"},{"k":"ticket.owned","meaning":"confirm","priority":97,"v":null}],"id":"ps1","lineage":{"causal_event_id":"ev1","source_state_version":1,"state_identity":"ctx"},"produced_at":"2026-08-27T08:00:00Z","source_state_version":1}"""
        SchemaValidator.validateCanonical("presentationstate.schema.json", presentation)

        val illegalPresentation =
            """{"id":"ps_1","source_state_version":0,"produced_at":"2026-08-27T08:00:00Z","atoms":[],"lineage":{"state_identity":"c","source_state_version":0,"causal_event_id":"e"},"state_ref":"x"}"""
        assertFails {
            SchemaValidator.validateCanonical("presentationstate.schema.json", illegalPresentation)
        }

        val surface =
            """{"bindings":[],"color_tokens":["accent"],"density_hint":"compact","gestures":{"bindings":[]},"haptics":{"events":[]},"id":"s","lineage":{"causal_event_id":"e","source_state_version":0,"state_identity":"c"},"morph":{"mode":"shared-element","motion":{"curve":"standard","damping":1,"duration_hint":1,"stiffness":1},"shared":[],"to":"ps"},"motion":{"curve":"standard","damping":1,"duration_hint":1,"stiffness":1},"nodes":[{"id":"n","role":"text","children":[]}],"presentation_ref":"ps","produced_at":"2026-08-27T08:00:00Z","projection_ref":"p"}"""
        SchemaValidator.validateCanonical("a3uisurface.schema.json", surface)

        val illegalSurface =
            """{"id":"s","projection_ref":"p","presentation_ref":"ps","lineage":{"state_identity":"c","source_state_version":0,"causal_event_id":"e"},"density_hint":"compact","color_tokens":["accent"],"motion":{"stiffness":1,"damping":1,"curve":"standard","duration_hint":1},"morph":{"to":"ps","mode":"shared-element","shared":[],"motion":{"stiffness":1,"damping":1,"curve":"standard","duration_hint":1}},"gestures":{"bindings":[]},"haptics":{"events":[]},"nodes":[],"bindings":[],"produced_at":"2026-08-27T08:00:00Z","prefetch":{"candidate_ref":"x","base_state_version":0,"confidence":0,"ttl_ms":0,"status":"prepared","may_commit":true}}"""
        assertFails {
            SchemaValidator.validateCanonical("a3uisurface.schema.json", illegalSurface)
        }
    }

    private fun load(name: String): Map<String, String> {
        val text = javaClass.classLoader.getResource(name)!!.readText().trim()
        return text.lines().associate {
            val i = it.indexOf('=')
            it.substring(0, i) to it.substring(i + 1)
        }
    }
}

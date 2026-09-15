package a3.core.temporal

import a3.core.admission.AdmissionDecision
import a3.core.admission.ConfidenceProfile
import a3.core.admission.Esito
import a3.core.admission.ObservationCandidate
import a3.core.admission.ReasonCode
import a3.core.admission.SourceId
import a3.core.admission.SourceType
import a3.core.admission.acceptedObservation
import a3.core.json.CanonicalJson
import com.tngtech.archunit.core.importer.ClassFileImporter
import com.tngtech.archunit.core.importer.ImportOption
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses
import java.io.File
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TemporalTest {
    private val t0 = Instant.parse("2026-08-27T08:00:00Z")
    private val present = Instant.parse("2026-08-27T11:00:00Z")
    private val root = File("../..")
    private val vectors = File("src/test/resources")

    private fun at(seconds: Long): Instant = t0.plusSeconds(seconds)

    private fun obs(
        source: String,
        subject: String,
        key: String,
        seq: Long,
        tEvent: Instant = t0,
        tObserve: Instant,
        tAdmit: Instant = tObserve.plusSeconds(1),
        tPresent: Instant = present,
        value: Any?
    ) = TemporalObservation(
        sourceId = SourceId(source),
        subject = subject,
        key = key,
        seq = seq,
        stamp = TemporalStamp(tEvent, tObserve, tAdmit, tPresent),
        value = value
    )

    private fun reference(): List<TemporalObservation> = listOf(
        obs("hotel", "trip.milano", "price", 1, tObserve = at(7200), value = "89.00"),
        obs("train", "trip.milano", "depart", 1, tObserve = at(1), value = "09:00"),
        obs("train", "trip.milano", "depart", 2, tObserve = at(30), value = "09:30"),
        obs("calendar", "trip.milano", "slot", 1, tObserve = at(10), value = "09:00")
    )

    private fun <T> permutations(list: List<T>): List<List<T>> {
        if (list.size <= 1) return listOf(list)
        val out = ArrayList<List<T>>()
        for (i in list.indices) {
            val rest = list.filterIndexed { idx, _ -> idx != i }
            for (p in permutations(rest)) {
                out += listOf(list[i]) + p
            }
        }
        return out
    }

    private fun golden(name: String, actual: String): String {
        val file = File(vectors, name)
        if (System.getenv("TM_DUMP") == "1") {
            vectors.mkdirs()
            file.writeText(actual)
        }
        require(file.exists()) { "missing canonical vector $name — generate with TM_DUMP=1" }
        return file.readText()
    }

    @Test
    fun TM_001_four_times_invariants_and_explicit_reject() {
        val ok = TemporalStamp(t0, at(1), at(2), present)
        assertEquals(t0, ok.tEvent)
        assertEquals(at(1), ok.tObserve)
        assertEquals(at(2), ok.tAdmit)
        assertEquals(present, ok.tPresent)
        val earlyPresent = TemporalStamp(t0, at(1), at(2), t0.minusSeconds(3600))
        assertTrue(earlyPresent.tPresent.isBefore(earlyPresent.tEvent))
        val equal = TemporalStamp(t0, t0, t0, present)
        assertEquals(t0, equal.tObserve)
        val late = assertFailsWith<TemporalReject> {
            TemporalStamp(t0, t0.minusSeconds(1), t0, present)
        }
        assertEquals("t_observe < t_event", late.message)
        val admitEarly = assertFailsWith<TemporalReject> {
            TemporalStamp(t0, at(5), at(4), present)
        }
        assertEquals("t_admit < t_observe", admitEarly.message)
        val candidate = ObservationCandidate(
            source = SourceId("train"),
            id = "obs-1",
            occurredAt = t0,
            observedAt = at(1),
            ingestedAt = at(1),
            data = mapOf("k" to "depart"),
            dataschema = "claim-array.schema.json",
            confidenceProfile = ConfidenceProfile(SourceType.DIRECT, "")
        )
        val accepted = acceptedObservation(
            candidate,
            AdmissionDecision(Esito.ADMIT, ReasonCode.ADMIT, "p", "1"),
            at(2)
        )
        val fromAdmit = stampFromAccepted(accepted, present)
        assertEquals(t0, fromAdmit.tEvent)
        assertEquals(at(1), fromAdmit.tObserve)
        assertEquals(at(2), fromAdmit.tAdmit)
        assertEquals(present, fromAdmit.tPresent)
        val inverted = ObservationCandidate(
            source = SourceId("train"),
            id = "obs-bad",
            occurredAt = at(10),
            observedAt = at(1),
            ingestedAt = at(1),
            data = mapOf("k" to "depart"),
            dataschema = "claim-array.schema.json",
            confidenceProfile = ConfidenceProfile(SourceType.DIRECT, "")
        )
        val invertedAccepted = acceptedObservation(
            inverted,
            AdmissionDecision(Esito.ADMIT, ReasonCode.ADMIT, "p", "1"),
            at(2)
        )
        assertFailsWith<TemporalReject> { stampFromAccepted(invertedAccepted, present) }
    }

    @Test
    fun TM_002_order_independent_of_arrival() {
        val stream = reference()
        val canonical = sort(stream).map { it.orderKey() }
        assertEquals(
            listOf(
                OrderKey(at(1), "train", 1),
                OrderKey(at(10), "calendar", 1),
                OrderKey(at(30), "train", 2),
                OrderKey(at(7200), "hotel", 1)
            ),
            canonical
        )
        for (perm in permutations(stream)) {
            assertEquals(canonical, sort(perm).map { it.orderKey() })
        }
        val json = CanonicalJson.encode(sort(stream).map { it.toCanonical() })
        assertEquals(golden("tm-order.json", json), json)
    }

    @Test
    fun TM_003_dedup_last_wins_history_kept_idempotent() {
        val stream = reference()
        val view = dedup(stream)
        assertEquals(4, view.log.size)
        assertEquals(3, view.current.size)
        val depart = view.current[DedupKey("train", "trip.milano", "depart")]!!
        assertEquals("09:30", depart.value)
        assertEquals(2L, depart.seq)
        val firstDepart = view.log.first { it.key == "depart" && it.seq == 1L }
        assertEquals("09:00", firstDepart.value)
        for (perm in permutations(stream)) {
            val other = dedup(perm)
            assertEquals(view.log.map { it.toCanonical() }, other.log.map { it.toCanonical() })
            assertEquals(
                view.current.mapValues { CanonicalJson.encode(it.value.toCanonical()) },
                other.current.mapValues { CanonicalJson.encode(it.value.toCanonical()) }
            )
        }
        val again = dedup(view.log)
        assertEquals(view.log.map { it.toCanonical() }, again.log.map { it.toCanonical() })
        val json = CanonicalJson.encode(
            mapOf(
                "current" to view.current.map { (k, v) ->
                    mapOf(
                        "key" to k.key,
                        "seq" to v.seq,
                        "source_id" to k.sourceId,
                        "subject" to k.subject,
                        "value" to v.value
                    )
                },
                "log" to view.log.map { it.toCanonical() }
            )
        )
        assertEquals(golden("tm-dedup.json", json), json)
    }

    @Test
    fun TM_004_fold_commutative_byte_identity() {
        val stream = reference()
        val bytes = foldBytes(stream)
        for (perm in permutations(stream)) {
            assertContentEquals(bytes, foldBytes(perm), perm.map { it.seq }.toString())
        }
        val folded = fold(stream)
        assertEquals(1, folded.size)
        val one = folded.single()
        assertEquals("trip.milano", one.subject)
        assertEquals(setOf("depart", "price", "slot"), one.fields.keys)
        assertEquals("09:30", one.fields.getValue("depart").value)
        assertEquals("89.00", one.fields.getValue("price").value)
        assertEquals("09:00", one.fields.getValue("slot").value)
        assertEquals(4, one.history.size)
        assertFalse(CanonicalJson.encode(one.toCanonical()).contains("confidence"))
    }

    @Test
    fun TM_005_canonical_jcs_ready() {
        val folded = fold(reference()).single()
        val json = CanonicalJson.encode(folded.toCanonical())
        val goldenJson = golden("tm-fold.json", json)
        assertEquals(goldenJson, json)
        assertContentEquals(goldenJson.toByteArray(Charsets.UTF_8), folded.canonicalBytes())
        val again = CanonicalJson.encode(folded.toCanonical())
        assertEquals(json, again)
        assertEquals("""{"b":1,"z":2}""", CanonicalJson.encode(mapOf("z" to 2, "b" to 1)))
        val stampJson = CanonicalJson.encode(reference().first().stamp.toCanonical())
        assertEquals(
            """{"t_admit":"2026-08-27T10:00:01Z","t_event":"2026-08-27T08:00:00Z","t_observe":"2026-08-27T10:00:00Z","t_present":"2026-08-27T11:00:00Z"}""",
            stampJson
        )
        assertTrue(stampJson.indexOf("\"t_admit\"") < stampJson.indexOf("\"t_event\""))
        assertTrue(stampJson.indexOf("\"t_event\"") < stampJson.indexOf("\"t_observe\""))
        assertTrue(stampJson.indexOf("\"t_observe\"") < stampJson.indexOf("\"t_present\""))
        assertEquals(json, CanonicalJson.encode(fold(reference().asReversed()).single().toCanonical()))
        assertFalse(json.contains("confidence"))
    }

    @Test
    fun TM_006_freeze_only_temporal() {
        fun diff(vararg paths: String): String {
            val proc = ProcessBuilder("git", "diff", "--stat", "--", *paths)
                .directory(root)
                .redirectErrorStream(true)
                .start()
            val out = proc.inputStream.bufferedReader().readText()
            assertEquals(0, proc.waitFor())
            return out
        }
        val frozen = diff(
            "core/admission", "core/action", "core/json",
            "a3ui/", "renderers/", "broker/", "agent/", "showcase/"
        )
        assertTrue(frozen.isBlank(), frozen)
        val status = ProcessBuilder("git", "status", "--porcelain")
            .directory(root).redirectErrorStream(true).start()
        val porcelain = status.inputStream.bufferedReader().readText()
        assertEquals(0, status.waitFor())
        val allowed = listOf(
            "core/temporal/",
            "settings.gradle.kts",
            "REVIEW_CORE_TEMPORAL.md",
            "review-assets/"
        )
        val ignore = listOf(".kotlin/", ".DS_Store")
        for (line in porcelain.lineSequence().filter { it.isNotBlank() }) {
            val path = line.drop(3).trim().removePrefix("?? ").let {
                if (it.contains(" -> ")) it.substringAfter(" -> ") else it
            }
            if (ignore.any { path.startsWith(it) }) continue
            assertTrue(allowed.any { path == it || path.startsWith(it) }, "unexpected path $line")
        }
        val settings = File(root, "settings.gradle.kts").readText()
        assertTrue(settings.contains(":core:temporal"))
        val production = ClassFileImporter()
            .withImportOption(ImportOption.DoNotIncludeTests())
            .importPackages("a3.core.temporal")
        noClasses()
            .that().resideInAPackage("a3.core.temporal..")
            .should().callMethod(Instant::class.java, "now")
            .check(production)
        noClasses()
            .that().resideInAPackage("a3.core.temporal..")
            .should().dependOnClassesThat()
            .resideInAnyPackage(
                "a3.core.action..",
                "a3.core.runtime..",
                "a3.core.world..",
                "a3.a3ui..",
                "a3.renderers..",
                "a3.broker..",
                "a3.agent..",
                "a3.showcase.."
            )
            .check(production)
        val main = File("src/main").walkTopDown().filter { it.extension == "kt" }.toList()
        assertTrue(main.isNotEmpty())
        for (file in main) {
            val text = file.readText()
            assertFalse(text.contains("Instant.now"), file.path)
            assertFalse(text.contains("now()"), file.path)
            assertFalse(text.contains("combineConfidence"), file.path)
            assertFalse(text.contains("average"), file.path)
        }
        val gradle = File("build.gradle.kts").readText()
        assertTrue(gradle.contains("project(\":core:admission\")"))
        assertTrue(gradle.contains("project(\":core:json\")"))
        assertFalse(gradle.contains("project(\":core:action\")"))
        assertFalse(gradle.contains("project(\":core:runtime\")"))
        val review = File(root, "REVIEW_CORE_TEMPORAL.md")
        assertTrue(review.exists(), "REVIEW_CORE_TEMPORAL.md missing")
        val body = review.readText()
        assertTrue(body.contains("TM-001"))
        assertTrue(body.contains("vettori canonici", ignoreCase = true))
        assertTrue(body.contains("si capisce") || body.contains("t_observe"))
    }
}

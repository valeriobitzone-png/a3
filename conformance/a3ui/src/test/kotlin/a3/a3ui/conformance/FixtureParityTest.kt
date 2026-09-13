package a3.a3ui.conformance

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

class FixtureParityTest {
    @Test
    fun fixtures_reuse_showcase_v03_constants() {
        val scene = File(ConformancePaths.repoRoot(), "showcase/shared/ShowcaseScene.kt").readText()
        assertTrue(scene.contains(CtaLaw.FORBID_REASON), "showcase missing forbid reason")
        assertTrue(scene.contains(CtaLaw.PENDING_LABEL), "showcase missing pending label")
        assertTrue(scene.contains(CtaLaw.STALE_WARNING), "showcase missing stale warning")
        assertTrue(scene.contains("€89"), "showcase missing hotel price")
        assertTrue(scene.contains("\"9:00\""), "showcase missing 9:00")
        assertTrue(scene.contains("\"9:30\""), "showcase missing 9:30")
        val hotel = File(ConformancePaths.fixturesDir(), "hotel-stale.json").readText()
        assertTrue(hotel.contains("OBSERVATION") && hotel.contains("€89") && hotel.contains("2h"))
        val calendar = File(ConformancePaths.fixturesDir(), "calendar-contradicted.json").readText()
        assertTrue(calendar.contains(CtaLaw.FORBID_REASON) && calendar.contains("9:00"))
        val train = File(ConformancePaths.fixturesDir(), "train-pending.json").readText()
        assertTrue(train.contains("UNKNOWN") && train.contains(CtaLaw.PENDING_LABEL))
        val flight = File(ConformancePaths.fixturesDir(), "flight-fact.json").readText()
        assertTrue(flight.contains("FACT") && flight.contains("BELIEVED"))
        println("PASS fixture parity with showcase-v0.3")
    }
}

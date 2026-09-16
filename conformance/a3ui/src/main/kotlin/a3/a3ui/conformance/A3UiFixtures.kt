// SPDX-License-Identifier: Apache-2.0
// Part of the A3 universe. See LICENSE.
package a3.a3ui.conformance

object A3UiFixtures {
    val hotelStale = SurfaceFixture(
        id = "hotel",
        oggetto = "Hotel",
        mark = "STALE",
        truthClass = "OBSERVATION",
        provenance = "OBSERVED_SIGNED",
        age = "2h",
        confidence = 0.8,
        status = "STALE",
        price = "€89",
        ctaEnabled = true,
        warning = CtaLaw.STALE_WARNING,
        stateDescription = "STALE stale 2h confirm permitted with warning",
        actionsPermitted = listOf("confirm"),
        actionsForbidden = emptyList()
    )

    val calendarContradicted = SurfaceFixture(
        id = "calendar",
        oggetto = "Calendario",
        mark = "CONTRADICTED",
        truthClass = "OBSERVATION",
        provenance = "INFERRED",
        age = "30m",
        confidence = 0.4,
        status = "CONTRADICTED",
        slots = listOf("9:00", "9:30"),
        ctaEnabled = false,
        reason = CtaLaw.FORBID_REASON,
        stateDescription = "CONTRADICTED contradicted: resolve conflict first confirm forbidden",
        actionsPermitted = emptyList(),
        actionsForbidden = listOf("confirm")
    )

    val trainPending = SurfaceFixture(
        id = "train",
        oggetto = "Treno",
        mark = "PENDING",
        truthClass = "UNKNOWN",
        provenance = "DERIVED_MODEL",
        age = "ora",
        confidence = 0.0,
        status = "PENDING",
        pendingLabel = CtaLaw.PENDING_LABEL,
        ctaEnabled = false,
        confirmationRequired = true,
        stateDescription = "PENDING in verifica confirm forbidden",
        spinner = false,
        actionsPermitted = emptyList(),
        actionsForbidden = listOf("confirm")
    )

    val flightFact = SurfaceFixture(
        id = "flight",
        oggetto = "Volo",
        mark = "FACT",
        truthClass = "FACT",
        provenance = "OBSERVED_SIGNED",
        age = "ora",
        confidence = 1.0,
        status = "BELIEVED",
        ctaEnabled = true,
        stateDescription = "FACT believed now confirm permitted",
        actionsPermitted = listOf("confirm"),
        actionsForbidden = emptyList()
    )

    fun lawful(): List<SurfaceFixture> = listOf(
        hotelStale,
        calendarContradicted,
        trainPending,
        flightFact
    )

    fun byId(id: String): SurfaceFixture = lawful().single { it.id == id }
}

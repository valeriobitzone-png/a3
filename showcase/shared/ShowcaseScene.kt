package a3.showcase

import a3.a3ui.a11y.MarkKind
import a3.a3ui.a11y.SpokenLaw
import a3.a3ui.a11y.SpokenRequest

/**
 * Complete epistemic scene for showcase-v0.3. Clock-free: ages are fixed
 * offsets from [NOW], not wall time. Marks (not glass, not audio) carry meaning.
 */
object ShowcaseScene {
    const val INTENT = "Appuntamento a Milano alle 9 da Roma"
    const val FORBID_REASON = "contradicted: resolve conflict first"
    const val PENDING_LABEL = "in verifica"
    const val STALE_BADGE = "stale 2h"
    const val CONTRADICTED_BADGE = "contradicted"
    const val CONFIRM_LABEL = "conferma"
    const val HOTEL_PRICE = "€89"
    const val SLOT_A = "9:00"
    const val SLOT_B = "9:30"
    const val DEADLINE = "scade 09:00"
    const val AGE_NOW = "ora"
    const val HORIZON_MS = 8L * 60L * 60L * 1000L
    const val HOTEL_AGE_MS = 2L * 60L * 60L * 1000L
    const val TRAIN_AGE_MS = 0L
    const val CALENDAR_AGE_MS = 30L * 60L * 1000L

    enum class State(val wire: String) {
        PENDING("pending"),
        STALE("stale"),
        CONTRADICTED("contradicted"),
        FACT("fact")
    }

    enum class Provenance(val wire: String, val label: String) {
        API("api", "API firmata"),
        MODEL("modello", "modello"),
        INFERRED("inferita", "inferita")
    }

    data class Surface(
        val id: String,
        val role: String,
        val state: State,
        val provenance: Provenance,
        val ageMs: Long,
        val price: String?,
        val slots: List<String>,
        val confirmEnabled: Boolean,
        val forbidReason: String?
    ) {
        fun ageLabel(): String = ShowcaseScene.ageLabel(ageMs)
        fun decay(): Float = ShowcaseScene.decay(ageMs)
        fun tag(): String = "scene-$id"
        fun spokenName(): String = when (id) {
            "hotel" -> "Hotel Milano"
            "flight" -> "Volo"
            else -> role
        }
        fun markKind(): MarkKind = when (state) {
            State.PENDING -> MarkKind.PENDING
            State.STALE -> MarkKind.STALE
            State.CONTRADICTED -> MarkKind.CONTRADICTED
            State.FACT -> MarkKind.FACT
        }
        fun reading(): String = SpokenLaw.reading(
            SpokenRequest(
                oggetto = spokenName(),
                mark = markKind(),
                ageSpoken = if (state == State.STALE) SpokenLaw.STALE_WARNING else null,
                priceSpoken = price?.let { "prezzo ${it.replace("€", "").trim()} euro" },
                extra = if (state == State.PENDING) SpokenLaw.PENDING_SLOT else null,
                reason = forbidReason
            )
        )
        fun stateDescription(): String = SpokenLaw.stateDescription(markKind())
    }

    fun surfaces(): List<Surface> = listOf(
        Surface(
            id = "train",
            role = "Treno",
            state = State.PENDING,
            provenance = Provenance.MODEL,
            ageMs = TRAIN_AGE_MS,
            price = null,
            slots = emptyList(),
            confirmEnabled = false,
            forbidReason = null
        ),
        Surface(
            id = "hotel",
            role = "Hotel",
            state = State.STALE,
            provenance = Provenance.API,
            ageMs = HOTEL_AGE_MS,
            price = HOTEL_PRICE,
            slots = emptyList(),
            confirmEnabled = true,
            forbidReason = null
        ),
        Surface(
            id = "calendar",
            role = "Calendario",
            state = State.CONTRADICTED,
            provenance = Provenance.INFERRED,
            ageMs = CALENDAR_AGE_MS,
            price = null,
            slots = listOf(SLOT_A, SLOT_B),
            confirmEnabled = false,
            forbidReason = FORBID_REASON
        )
    )

    fun train(): Surface = surfaces()[0]
    fun hotel(): Surface = surfaces()[1]
    fun calendar(): Surface = surfaces()[2]

    fun flight(): Surface = Surface(
        id = "flight",
        role = "Volo",
        state = State.FACT,
        provenance = Provenance.API,
        ageMs = 0L,
        price = null,
        slots = emptyList(),
        confirmEnabled = true,
        forbidReason = null
    )

    fun ageLabel(ageMs: Long): String {
        if (ageMs <= 0L) return AGE_NOW
        val minutes = ageMs / 60_000L
        if (minutes < 60L) return "${minutes}m fa"
        val hours = minutes / 60L
        return "${hours}h fa"
    }

    fun decay(ageMs: Long, horizonMs: Long = HORIZON_MS): Float {
        if (horizonMs <= 0L) return 0f
        return (1f - ageMs.toFloat() / horizonMs.toFloat()).coerceIn(0f, 1f)
    }

    fun announce(): List<Pair<String, String>> = listOf(
        "scene-train" to "pending review",
        "scene-hotel" to "stale",
        "scene-calendar" to "contradicted"
    )

    fun causes(): List<String> = listOf(
        ShowcaseSensory.Cause.HELD,
        ShowcaseSensory.Cause.UNKNOWN,
        ShowcaseSensory.Cause.CONTRADICTED
    )

    fun hasSpinner(source: String): Boolean {
        val lower = source.lowercase()
        return lower.contains("circularprogressindicator") ||
            lower.contains("pendingmark") ||
            (lower.contains("spinner") && lower.contains("scene-train"))
    }
}

// SPDX-License-Identifier: Apache-2.0
// Part of the A3 universe. See LICENSE.
package a3.agent.surface

import a3.a3ui.engine.ComposedTree
import a3.a3ui.engine.Epistemic
import a3.a3ui.engine.EpistemicFacts
import a3.a3ui.engine.SurfaceComposer
import a3.a3ui.model.EpistemicAxis
import a3.agent.admit.AdmissionView
import a3.agent.model.ActivityKind
import a3.agent.model.AgentOutcome
import a3.agent.model.LinkPreview
import a3.agent.model.PreviewLevel
import a3.agent.model.SourceKind
import a3.agent.model.SurfaceOption
import a3.core.action.ActionPhase
import a3.core.action.ActionState
import a3.core.action.PlanPhase
import a3.core.world.api.Claim
import a3.projection.model.CausalLineage
import a3.projection.model.PresentationAtom
import a3.projection.model.PresentationState
import java.time.Instant

object AgentSurfaces {
    fun optionFromAdmission(
        kind: ActivityKind,
        view: AdmissionView,
        asOf: Instant,
        index: Int
    ): SurfaceOption {
        val prefix = kind.name.lowercase()
        val id = view.candidate.id.substringAfterLast('/').ifBlank { "$prefix-$index" }
        val title = value(view.claims, ".title")?.toString() ?: id
        val price = value(view.claims, ".price")?.toString()
        val subtitle = value(view.claims, ".mode")?.toString()
            ?: value(view.claims, ".slot")?.toString()
        val primary = view.claims.firstOrNull { it.k.endsWith(".price") }
            ?: view.claims.firstOrNull { it.k.endsWith(".title") }
            ?: view.claims.firstOrNull()
        return composeOption(
            id = id,
            group = kind,
            title = title,
            subtitle = subtitle,
            price = price,
            imageUrl = null,
            url = null,
            retailer = null,
            previewLevel = null,
            sourceKind = SourceKind.FIXTURE,
            amazonUnreliable = false,
            reversible = true,
            outbound = false,
            claims = view.claims,
            primary = primary,
            admissionHeld = view.held,
            asOf = asOf
        )
    }

    fun optionFromPreview(
        preview: LinkPreview,
        asOf: Instant,
        sourceKind: SourceKind = SourceKind.WEB_DOCUMENT
    ): SurfaceOption {
        val confidence = confidenceOf(preview)
        val priceSource = if (preview.price != null && preview.level == PreviewLevel.OPENGRAPH) {
            preview.retailer
        } else if (preview.price != null) {
            preview.retailer
        } else null
        val observed = preview.fetchedAt ?: asOf
        val expires = preview.expiresAt
        val claims = buildList {
            add(
                Claim(
                    k = "preview.${preview.retailer}.title",
                    v = preview.title,
                    confidence = confidence,
                    source = source(preview),
                    observedAt = observed,
                    expiresAt = expires
                )
            )
            if (preview.price != null) {
                val unattributed = preview.level == PreviewLevel.MINIMA || preview.level == PreviewLevel.DEEPLINK
                add(
                    Claim(
                        k = "preview.${preview.retailer}.price",
                        v = preview.price,
                        confidence = if (unattributed) 0.0 else confidence,
                        source = if (unattributed) "unattributed" else (priceSource ?: "web-document"),
                        observedAt = observed,
                        expiresAt = expires
                    )
                )
            }
            add(
                Claim(
                    k = "preview.${preview.retailer}.level",
                    v = preview.level.wire(),
                    confidence = 1.0,
                    source = "agent-preview",
                    observedAt = observed,
                    expiresAt = expires
                )
            )
        }
        val primary = claims.firstOrNull { it.k.endsWith(".price") } ?: claims.first()
        return composeOption(
            id = "buy-${preview.retailer}",
            group = ActivityKind.PURCHASE,
            title = preview.title ?: preview.retailer,
            subtitle = listOfNotNull(
                preview.description,
                "level=${preview.level.wire()}",
                preview.fallbackReason?.let { "fallback=$it" },
                if (preview.amazonUnreliable) "amazon-og-unreliable" else null
            ).joinToString(" · "),
            price = preview.price,
            imageUrl = preview.imageUrl,
            url = preview.url,
            retailer = preview.retailer,
            previewLevel = preview.level,
            sourceKind = sourceKind,
            amazonUnreliable = preview.amazonUnreliable,
            reversible = false,
            outbound = true,
            claims = claims,
            primary = primary,
            admissionHeld = false,
            asOf = asOf
        )
    }

    fun optionFromLibrary(
        title: String,
        coverUrl: String?,
        apiTimestamp: Instant,
        asOf: Instant,
        index: Int
    ): SurfaceOption {
        val expires = apiTimestamp.plusSeconds(86_400)
        val claims = listOf(
            Claim("openlibrary.$index.title", title, 1.0, "openlibrary", apiTimestamp, expires),
            Claim("openlibrary.$index.cover", coverUrl, 1.0, "openlibrary", apiTimestamp, expires)
        )
        return composeOption(
            id = "openlibrary-$index",
            group = ActivityKind.PURCHASE,
            title = title,
            subtitle = "Open Library API",
            price = null,
            imageUrl = coverUrl,
            url = coverUrl,
            retailer = "openlibrary",
            previewLevel = null,
            sourceKind = SourceKind.PUBLIC_API,
            amazonUnreliable = false,
            reversible = true,
            outbound = false,
            claims = claims,
            primary = claims.first(),
            admissionHeld = false,
            asOf = asOf
        )
    }

    fun unattributedPrice(asOf: Instant, stale: Boolean): SurfaceOption {
        val observed = asOf.minusSeconds(if (stale) 7200 else 10)
        val expires = if (stale) asOf.minusSeconds(60) else asOf.plusSeconds(3600)
        val claims = listOf(
            Claim("preview.stale.title", "Cuffie listing", 0.8, "web-document", observed, expires),
            Claim("preview.stale.price", "99.00", 0.0, "unattributed", observed, expires)
        )
        return composeOption(
            id = "preview-epistemic",
            group = ActivityKind.PURCHASE,
            title = "Cuffie listing",
            subtitle = "prezzo senza fonte",
            price = "99.00",
            imageUrl = null,
            url = "https://shop.example/h",
            retailer = "shop",
            previewLevel = PreviewLevel.META,
            sourceKind = SourceKind.WEB_DOCUMENT,
            amazonUnreliable = false,
            reversible = true,
            outbound = false,
            claims = claims,
            primary = claims[1],
            admissionHeld = false,
            asOf = asOf
        )
    }

    fun outcomeTree(action: ActionState, outcome: AgentOutcome, asOf: Instant): ComposedTree {
        val verb = when (outcome) {
            AgentOutcome.WORKED -> "worked"
            AgentOutcome.DID_NOT_WORK -> "did not work"
            AgentOutcome.UNKNOWN -> "unknown"
        }
        val phase = when {
            action.actionPhase == ActionPhase.COMPLETED || action.actionPhase == ActionPhase.OBSERVED ->
                action.actionPhase.name.lowercase()
            action.actionPhase == ActionPhase.FAILED -> "failed"
            action.actionPhase == ActionPhase.UNKNOWN -> "unknown"
            action.planPhase == PlanPhase.DENIED -> "failed"
            else -> action.actionPhase.name.lowercase()
        }
        val atomKey = "action.outcome"
        val presentation = presentation(
            listOf(
                PresentationAtom("price", atomKey, "$verb · ${action.actionPhase.name}", 80)
            )
        )
        val facts = mapOf(
            atomKey to EpistemicFacts(
                claim = Claim(atomKey, verb, 1.0, "action", asOf, asOf.plusSeconds(60)),
                actionPhase = phase
            )
        )
        return SurfaceComposer.compose(presentation, asOf, facts)
    }

    fun composeOption(
        id: String,
        group: ActivityKind,
        title: String,
        subtitle: String?,
        price: String?,
        imageUrl: String?,
        url: String?,
        retailer: String?,
        previewLevel: PreviewLevel?,
        sourceKind: SourceKind,
        amazonUnreliable: Boolean,
        reversible: Boolean,
        outbound: Boolean,
        claims: List<Claim>,
        primary: Claim?,
        admissionHeld: Boolean,
        asOf: Instant
    ): SurfaceOption {
        val titleKey = "$id.title"
        val priceKey = "$id.price"
        val atoms = ArrayList<PresentationAtom>()
        atoms += PresentationAtom("timetable", titleKey, title, 80)
        if (price != null) atoms += PresentationAtom("price", priceKey, price, 70)
        if (subtitle != null) atoms += PresentationAtom("note", "$id.sub", subtitle, 40)
        if (imageUrl != null) atoms += PresentationAtom("note", "$id.image", imageUrl, 30)
        if (previewLevel != null) {
            atoms += PresentationAtom("note", "$id.level", previewLevel.wire(), 20)
        }
        atoms += PresentationAtom("select", "$id.select", id, 10)
        val facts = LinkedHashMap<String, EpistemicFacts>()
        val titleClaim = claims.firstOrNull { it.k.endsWith(".title") } ?: primary
        if (titleClaim != null) {
            facts[titleKey] = EpistemicFacts(claim = titleClaim, admissionHeld = admissionHeld)
        }
        val priceClaim = claims.firstOrNull { it.k.endsWith(".price") }
        if (priceClaim != null && price != null) {
            facts[priceKey] = EpistemicFacts(claim = priceClaim, admissionHeld = admissionHeld)
        }
        val tree = SurfaceComposer.compose(presentation(atoms), asOf, facts)
        val axis = if (primary != null) {
            Epistemic.derive(EpistemicFacts(claim = primary, admissionHeld = admissionHeld), asOf)
        } else {
            EpistemicAxis()
        }
        return SurfaceOption(
            id = id,
            group = group,
            title = title,
            subtitle = subtitle,
            price = price,
            imageUrl = imageUrl,
            url = url,
            retailer = retailer,
            previewLevel = previewLevel,
            sourceKind = sourceKind,
            amazonUnreliable = amazonUnreliable,
            reversible = reversible,
            outbound = outbound,
            axis = axis,
            facts = facts,
            claims = claims,
            tree = tree,
            admissionHeld = admissionHeld
        )
    }

    private fun presentation(atoms: List<PresentationAtom>) = PresentationState(
        id = "ps-agent",
        sourceStateVersion = 1,
        producedAt = Instant.parse("2026-09-11T08:00:00Z"),
        atoms = atoms,
        lineage = CausalLineage("agent", 1, "ev-agent")
    )

    private fun value(claims: List<Claim>, suffix: String): Any? =
        claims.firstOrNull { it.k.endsWith(suffix) }?.v

    private fun confidenceOf(preview: LinkPreview): Double = when {
        preview.amazonUnreliable -> 0.2
        preview.level == PreviewLevel.OPENGRAPH -> 0.85
        preview.level == PreviewLevel.TWITTER -> 0.7
        preview.level == PreviewLevel.META -> 0.5
        preview.level == PreviewLevel.MINIMA -> 0.3
        else -> 0.2
    }

    private fun source(preview: LinkPreview): String =
        if (preview.level == PreviewLevel.MINIMA || preview.level == PreviewLevel.DEEPLINK) {
            "retailer-minima"
        } else {
            "web-document"
        }
}

package a3.overlay

data class OverlayCard(
    val id: String,
    val title: String,
    val subtitle: String?,
    val price: String?,
    val url: String,
    val group: String
)

data class OverlaySession(
    val intent: String,
    val planDigest: String,
    val cards: List<OverlayCard>,
    val ambient: String = OverlayPolicy.ACTIVE
)

fun interface BrowserOpener {
    fun open(url: String): Boolean
}

object OverlayFlight {
    const val INTENT = "trova volo Roma-Milano domani"
    const val AIR_URL = "https://www.google.com/travel/flights?q=Rome%20to%20Milan%20tomorrow"
    const val RAIL_URL = "https://www.google.com/search?q=Frecciarossa%20Roma%20Milano%20domani"
    const val CAR_URL = "https://www.google.com/maps/dir/Rome/Milan"

    fun present(): OverlaySession = OverlaySession(
        intent = INTENT,
        planDigest = "overlay-flight-v0.1",
        cards = cards()
    )

    fun cards(): List<OverlayCard> = listOf(
        OverlayCard(
            id = "transport.rail",
            title = "Frecciarossa Roma–Milano 06:00–08:55",
            subtitle = "train",
            price = "49.90 EUR",
            url = RAIL_URL,
            group = "transport"
        ),
        OverlayCard(
            id = "transport.air",
            title = "Volo FCO–LIN 07:10–08:25",
            subtitle = "flight",
            price = "86.00 EUR",
            url = AIR_URL,
            group = "transport"
        ),
        OverlayCard(
            id = "transport.car",
            title = "Auto A1 Roma–Milano ~5h20",
            subtitle = "car",
            price = "fuel+toll ~45 EUR",
            url = CAR_URL,
            group = "transport"
        )
    )

    fun choose(session: OverlaySession, optionId: String, opener: BrowserOpener): String {
        val card = session.cards.first { it.id == optionId }
        val opened = opener.open(card.url)
        require(opened) { "browser did not open ${card.url}" }
        return card.url
    }

    fun air(session: OverlaySession): OverlayCard =
        session.cards.first { it.id == "transport.air" }
}

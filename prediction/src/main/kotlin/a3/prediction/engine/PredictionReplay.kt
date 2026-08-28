package a3.prediction.engine

import a3.prediction.model.Forecast
import a3.prediction.model.PreparedState
import a3.prediction.model.PredictionInvalidation
import java.time.Instant
import java.util.ArrayList
import java.util.Collections
import java.util.TreeMap

data class PredictionEvent(
    val id: String,
    val t: Instant,
    val type: String,
    val causalId: String? = null,
    val payload: Any? = null
)

class PredictionEventLog {
    private val events = ArrayList<PredictionEvent>()

    fun append(event: PredictionEvent) {
        require(event.id.isNotBlank()) { "event id must not be blank" }
        require(events.none { it.id == event.id }) { "Duplicate event id: ${event.id}" }
        events += event
    }

    fun all(): List<PredictionEvent> = Collections.unmodifiableList(ArrayList(events))

    fun replay(): List<PredictionEvent> = all()
}

data class ReplayedPrediction(
    val forecasts: List<Forecast>,
    val prepared: List<PreparedState>
) {
    fun lastForecast(): Forecast? = forecasts.lastOrNull()

    fun live(clock: a3.core.time.InstantSource): List<PreparedState> {
        val result = ArrayList<PreparedState>()
        val now = clock.now()
        for (state in prepared) {
            if (state.status != a3.prediction.model.PredictionStatus.PREPARED) continue
            if (!now.isBefore(state.preparedAt) && now.isBefore(state.expiresAt)) {
                result += state
            }
        }
        return result
    }
}

object PredictionReplay {
    fun replay(log: PredictionEventLog): ReplayedPrediction {
        val forecasts = TreeMap<String, Forecast>()
        val prepared = TreeMap<String, PreparedState>()
        val forecastOrder = ArrayList<String>()
        for (event in log.all()) {
            when (event.type) {
                "prediction.updated" -> when (val payload = event.payload) {
                    is Forecast -> {
                        if (payload.id !in forecasts) forecastOrder += payload.id
                        forecasts[payload.id] = payload
                    }
                    is PreparedState -> prepared[payload.id] = payload
                }
                "prediction.invalidated" -> {
                    val invalidation = event.payload as? PredictionInvalidation ?: continue
                    for (id in invalidation.preparedIds.sorted()) {
                        val current = prepared[id] ?: continue
                        prepared[id] = current.copy(
                            status = a3.prediction.model.PredictionStatus.INVALIDATED
                        )
                    }
                }
            }
        }
        val forecastList = ArrayList<Forecast>(forecastOrder.size)
        for (id in forecastOrder) {
            forecasts[id]?.let { forecastList += it }
        }
        return ReplayedPrediction(
            forecasts = forecastList,
            prepared = ArrayList(prepared.values)
        )
    }
}

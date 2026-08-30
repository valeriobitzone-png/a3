package a3.intent

import java.time.Instant

data class IntentContext(
    val now: Instant,
    val id: String,
    val expression: String,
    val modality: String,
    val goalRef: String? = null
)

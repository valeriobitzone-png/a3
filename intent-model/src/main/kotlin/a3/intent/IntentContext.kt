// SPDX-License-Identifier: Apache-2.0
// Part of the A3 universe. See LICENSE.
package a3.intent

import java.time.Instant

data class IntentContext(
    val now: Instant,
    val id: String,
    val expression: String,
    val modality: String,
    val goalRef: String? = null
)

// SPDX-License-Identifier: Apache-2.0
// Part of the A3 universe. See LICENSE.
package a3.renderers.mac.compose

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

@Composable
fun GlassOpticsLayer(refract: Boolean, noise: Boolean, modifier: Modifier = Modifier) {
    val tag = when {
        refract && noise -> "glass-optics"
        refract -> "glass-refraction"
        noise -> "glass-noise"
        else -> "glass-optics-off"
    }
    Box(Modifier.size(24.dp).then(modifier).testTag(tag))
}

@Composable
fun ParticleBurst(trigger: Boolean, modifier: Modifier = Modifier) {
    val on = LocalParticlesEnabled.current
    Box(Modifier.size(24.dp).then(modifier).testTag(if (trigger && on) "particle-burst" else "particle-idle"))
}

@Composable
fun AlphaMaskedStrip(modifier: Modifier = Modifier) {
    Box(Modifier.size(24.dp).then(modifier).testTag("alpha-mask"))
}

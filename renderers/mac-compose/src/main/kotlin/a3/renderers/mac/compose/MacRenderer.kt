// SPDX-License-Identifier: Apache-2.0
// Part of the A3 universe. See LICENSE.
package a3.renderers.mac.compose

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import a3.overlay.A3UiProfile
import a3.renderers.android.core.model.RenderedOutput

@Composable
fun MacRenderer(
    output: RenderedOutput,
    onAction: (String) -> Unit = {},
    highContrast: Boolean = MacAccessibility.highContrast(),
    profile: A3UiProfile = A3UiProfile.HIGH
) {
    val reduced = output.reducedMotion || MacAccessibility.reduceMotion()
    A3UiProfileProvider(profile) {
        CompositionLocalProvider(
            LocalReducedMotion provides reduced,
            LocalHighContrast provides highContrast,
            LocalDensityHint provides output.densityHint
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .testTag("a3-material")
            ) {
                GlassSceneHost {
                    MacMotionApplier(output.spring) {
                        MacMorphApplier(output.sharedElements) {
                            FluidResizeContainer {
                                MacCatalog(output.nodes, output.gestures.actions, onAction)
                            }
                        }
                    }
                }
            }
        }
    }
}

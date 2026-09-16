// SPDX-License-Identifier: Apache-2.0
// Part of the A3 universe. See LICENSE.
package a3.renderers.android.core.interp

import a3.a3ui.model.MotionSpec
import a3.renderers.android.core.model.SpringParams

class MotionInterpreter {
    fun interpret(spec: MotionSpec): SpringParams = SpringParams(
        stiffness = spec.stiffness,
        damping = spec.damping,
        curve = spec.curve,
        durationHint = spec.durationHint
    )
}

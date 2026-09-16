// SPDX-License-Identifier: Apache-2.0
// Part of the A3 universe. See LICENSE.
package a3.renderers.android.core.interp

import a3.a3ui.model.MorphSpec
import a3.renderers.android.core.model.SharedElementPlan
import a3.renderers.android.core.model.SpringParams
import java.util.ArrayList

class MorphInterpreter {
    fun interpret(spec: MorphSpec, spring: SpringParams): SharedElementPlan {
        val shared = ArrayList(spec.shared)
        shared.sort()
        return SharedElementPlan(
            to = spec.to,
            mode = spec.mode,
            shared = shared,
            spring = spring
        )
    }
}

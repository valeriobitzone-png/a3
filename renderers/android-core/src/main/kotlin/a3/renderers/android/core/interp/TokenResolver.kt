// SPDX-License-Identifier: Apache-2.0
// Part of the A3 universe. See LICENSE.
package a3.renderers.android.core.interp

import a3.renderers.android.core.model.ColorValue
import a3.renderers.android.core.model.RendererContext

class TokenResolver {
    fun resolve(token: String, ctx: RendererContext): ColorValue {
        return ctx.tokens[token]
            ?: throw IllegalArgumentException("unknown semantic token: $token")
    }
}

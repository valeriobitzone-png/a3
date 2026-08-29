package a3.renderers.android.core.interp

import a3.renderers.android.core.model.ColorValue
import a3.renderers.android.core.model.RendererContext

class TokenResolver {
    fun resolve(token: String, ctx: RendererContext): ColorValue {
        return ctx.tokens[token]
            ?: throw IllegalArgumentException("unknown semantic token: $token")
    }
}

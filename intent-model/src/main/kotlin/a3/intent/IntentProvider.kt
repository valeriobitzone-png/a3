package a3.intent

import a3.core.model.Intent

fun interface IntentProvider {
    fun infer(ctx: IntentContext): Intent
}

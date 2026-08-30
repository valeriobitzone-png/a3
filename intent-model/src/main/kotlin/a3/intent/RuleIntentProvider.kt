package a3.intent

import a3.core.model.Intent

class RuleIntentProvider(
    private val confidence: Double
) : IntentProvider {
    override fun infer(ctx: IntentContext): Intent {
        return IntentProposal.validate(
            Intent(
                id = ctx.id,
                expression = ctx.expression,
                modality = ctx.modality,
                source = "inferred",
                confidence = confidence,
                goalRef = ctx.goalRef
            )
        )
    }
}

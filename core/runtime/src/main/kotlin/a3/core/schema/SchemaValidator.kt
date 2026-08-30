package a3.core.schema

import a3.core.json.SchemaValidator as JsonSchema
import a3.core.serialize.CanonicalJson

object SchemaValidator {
    fun validateCanonical(schemaFile: String, canonicalJson: String) {
        JsonSchema.validateCanonical(schemaFile, canonicalJson)
    }

    fun validate(schemaFile: String, model: Any) {
        validateCanonical(schemaFile, CanonicalJson.of(model))
    }
}

object ModelValidator {
    fun goal(model: Any) = SchemaValidator.validate("goal.schema.json", model)
    fun intent(model: Any) = SchemaValidator.validate("intent.schema.json", model)
    fun plan(model: Any) = SchemaValidator.validate("plan.schema.json", model)
    fun state(model: Any) = SchemaValidator.validate("state.schema.json", model)
    fun trust(model: Any) = SchemaValidator.validate("trust.schema.json", model)
    fun outcome(model: Any) = SchemaValidator.validate("outcome.schema.json", model)
    fun observation(model: Any) = SchemaValidator.validate("observation.schema.json", model)
    fun event(model: Any) = SchemaValidator.validate("event.schema.json", model)
    fun capability(model: Any) = SchemaValidator.validate("capability.schema.json", model)
    fun prediction(model: Any) = SchemaValidator.validate("prediction.schema.json", model)
    fun projection(model: Any) = SchemaValidator.validate("projection.schema.json", model)
}

// SPDX-License-Identifier: Apache-2.0
// Part of the A3 universe. See LICENSE.
package a3.projection.schema

import a3.core.json.SchemaValidator as JsonSchema
import a3.projection.serialize.CanonicalJson

object SchemaValidator {
    fun validateCanonical(schemaFile: String, canonicalJson: String) {
        JsonSchema.validateCanonical(schemaFile, canonicalJson)
    }

    fun validate(schemaFile: String, model: Any) {
        validateCanonical(schemaFile, CanonicalJson.of(model))
    }
}

object ModelValidator {
    fun presentation(model: Any) = SchemaValidator.validate("presentationstate.schema.json", model)
    fun candidate(model: Any) = SchemaValidator.validate("projectioncandidate.schema.json", model)
    fun projection(model: Any) = SchemaValidator.validate("projection-core.schema.json", model)
}

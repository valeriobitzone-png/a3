// SPDX-License-Identifier: Apache-2.0
// Part of the A3 universe. See LICENSE.
package a3.a3ui.schema

import a3.a3ui.serialize.CanonicalJson
import a3.core.json.SchemaValidator as JsonSchema

object SchemaValidator {
    fun validateCanonical(schemaFile: String, canonicalJson: String) {
        JsonSchema.validateCanonical(schemaFile, canonicalJson)
    }

    fun validate(schemaFile: String, model: Any) {
        validateCanonical(schemaFile, CanonicalJson.of(model))
    }
}

object ModelValidator {
    fun surface(model: Any) = SchemaValidator.validate("a3uisurface.schema.json", model)
}

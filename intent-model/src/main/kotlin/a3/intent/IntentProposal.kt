// SPDX-License-Identifier: Apache-2.0
// Part of the A3 universe. See LICENSE.
package a3.intent

import a3.core.model.Intent

object IntentProposal {
    fun validate(intent: Intent): Intent {
        require(intent.source == "inferred") {
            "proposal must have source=inferred"
        }
        require(intent.confidence < 1.0) {
            "inferred proposal must have confidence<1"
        }
        return intent
    }
}

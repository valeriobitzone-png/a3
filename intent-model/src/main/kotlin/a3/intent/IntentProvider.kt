// SPDX-License-Identifier: Apache-2.0
// Part of the A3 universe. See LICENSE.
package a3.intent

import a3.core.model.Intent

fun interface IntentProvider {
    fun infer(ctx: IntentContext): Intent
}

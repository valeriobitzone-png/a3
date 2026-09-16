// SPDX-License-Identifier: Apache-2.0
// Part of the A3 universe. See LICENSE.
package a3.showcase

/**
 * Showcase v0.4 — entry into the system overlay. Flight surfaces are NOT
 * hosted in this launcher; they live in :overlay on top of Chrome/Safari.
 */
object ShowcaseOverlay {
    const val INTENT = "trova volo Roma-Milano domani"
    const val NOTE = "surfaces live in the system overlay, not this launcher · default collapsed pill"
    const val ANDROID_ACTION = "a3.overlay.START"
}

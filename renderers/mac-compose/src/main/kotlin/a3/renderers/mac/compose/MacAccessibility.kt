// SPDX-License-Identifier: Apache-2.0
// Part of the A3 universe. See LICENSE.
package a3.renderers.mac.compose

object MacAccessibility {
    fun reduceMotion(): Boolean =
        flag("a3.reduce.motion", "reduceMotion")

    fun highContrast(): Boolean =
        flag("a3.high.contrast", "increaseContrast") ||
            flag("a3.high.contrast", "differentiateWithoutColor")

    private fun flag(property: String, defaultsKey: String): Boolean {
        val fromProp = System.getProperty(property)
        if (fromProp != null) return fromProp == "true" || fromProp == "1"
        return defaultsInt("com.apple.universalaccess", defaultsKey) == 1
    }

    private fun defaultsInt(domain: String, key: String): Int {
        return try {
            val proc = ProcessBuilder("defaults", "read", domain, key)
                .redirectErrorStream(true)
                .start()
            val text = proc.inputStream.bufferedReader().readText().trim()
            proc.waitFor()
            text.toIntOrNull() ?: 0
        } catch (_: Exception) {
            0
        }
    }
}

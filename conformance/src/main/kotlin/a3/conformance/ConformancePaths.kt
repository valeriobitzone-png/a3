// SPDX-License-Identifier: Apache-2.0
// Part of the A3 universe. See LICENSE.
package a3.conformance

import java.io.File

object ConformancePaths {
    fun repoRoot(): File {
        var dir = File(System.getProperty("user.dir")).canonicalFile
        repeat(8) {
            val settings = File(dir, "settings.gradle.kts")
            val order = File(dir, "conformance/vectors/v1/tm-order.json")
            if (settings.exists() && order.isFile) return dir
            dir = dir.parentFile ?: error("repo root not found from ${System.getProperty("user.dir")}")
        }
        error("repo root not found from ${System.getProperty("user.dir")}")
    }

    fun vectors(): File = File(repoRoot(), "conformance/vectors")

    fun vectorsV1(): File = File(vectors(), "v1")

    fun vectorsV2(): File = File(vectors(), "v2")

    fun fixtures(): File = File(repoRoot(), "conformance/fixtures")

    fun readme(): File = File(repoRoot(), "conformance/README.md")
}

package a3.a3ui.conformance

import java.io.File

object ConformancePaths {
    fun repoRoot(start: File = File(".").canonicalFile): File {
        var dir: File? = start
        while (dir != null) {
            if (File(dir, "settings.gradle.kts").isFile &&
                File(dir, "conformance/a3ui").isDirectory
            ) {
                return dir
            }
            dir = dir.parentFile
        }
        error("a3 repo root not found from $start")
    }

    fun suiteDir(root: File = repoRoot()): File = File(root, "conformance/a3ui")
    fun fixturesDir(root: File = repoRoot()): File = File(suiteDir(root), "fixtures")
    fun screenshotsDir(root: File = repoRoot()): File = File(suiteDir(root), "screenshots")
    fun androidShots(root: File = repoRoot()): File = File(screenshotsDir(root), "android")
    fun macShots(root: File = repoRoot()): File = File(screenshotsDir(root), "mac")
    fun report(root: File = repoRoot()): File = File(suiteDir(root), "REPORT.md")
    fun readme(root: File = repoRoot()): File = File(suiteDir(root), "README.md")
}

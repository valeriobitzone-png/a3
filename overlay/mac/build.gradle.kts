plugins {
    kotlin("jvm")
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(project(":overlay:common"))
    implementation(project(":agent"))
    testImplementation(kotlin("test"))
}

kotlin {
    jvmToolchain(21)
}

val nativeBin = layout.buildDirectory.file("overlay-mac")

tasks.register<Exec>("compileNative") {
    val src = file("native/OverlayMain.swift")
    inputs.file(src)
    outputs.file(nativeBin)
    commandLine(
        "swiftc",
        "-parse-as-library",
        src.absolutePath,
        "-o",
        nativeBin.get().asFile.absolutePath,
        "-framework", "AppKit",
        "-framework", "ApplicationServices"
    )
}

tasks.test {
    useJUnitPlatform()
    dependsOn("compileNative")
    systemProperty(
        "a3.overlay.native",
        layout.buildDirectory.file("overlay-mac").get().asFile.absolutePath
    )
    testLogging {
        events("passed", "skipped", "failed")
        showStandardStreams = false
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}

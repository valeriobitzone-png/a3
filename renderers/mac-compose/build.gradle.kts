// SPDX-License-Identifier: Apache-2.0
// Part of the A3 universe. See LICENSE.
plugins {
    kotlin("jvm")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
}

repositories {
    google()
    mavenCentral()
}

dependencies {
    implementation(project(":renderers:android-core"))
    implementation(project(":overlay:common"))
    implementation(compose.desktop.currentOs) // org.jetbrains.compose.desktop
    implementation("com.materialkolor:material-color-utilities:1.7.1")
    // a3ui-graphics-v0.1 snapshot: src/main/resources/a3ui-graphics/
    testImplementation(compose.desktop.uiTestJUnit4)
    testImplementation(kotlin("test"))
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit:2.2.10")
    testImplementation("com.tngtech.archunit:archunit:1.3.0")
}

kotlin {
    jvmToolchain(21)
}

tasks.test {
    useJUnit()
    systemProperty("skiko.renderApi", "SOFTWARE")
    testLogging {
        events("passed", "skipped", "failed")
        showStandardStreams = false
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}

tasks.register<JavaExec>("harvestMacCatalog") {
    group = "perf"
    dependsOn("classes")
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("a3.renderers.mac.compose.MacFrameHarvestKt")
    systemProperty("skiko.renderApi", "METAL")
    doFirst {
        val profile = (project.findProperty("harvestProfile") as String?) ?: "HIGH"
        val frames = (project.findProperty("harvestFrames") as String?) ?: "320"
        val log = project.findProperty("harvestLog") as String?
            ?: error("-PharvestLog= is required")
        args = listOf("--profile", profile, "--frames", frames, "--log", log)
    }
}

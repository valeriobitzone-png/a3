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
    implementation(project(":a3ui:conformance"))
    implementation(compose.desktop.currentOs)
    testImplementation(compose.desktop.uiTestJUnit4)
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit:2.2.10")
    testImplementation("com.fasterxml.jackson.core:jackson-databind:2.18.3")
}

kotlin {
    jvmToolchain(21)
    sourceSets {
        named("main") {
            kotlin.srcDir("../shared-compose")
        }
        named("test") {
            kotlin.srcDir("../shared-test")
        }
    }
}

tasks.test {
    useJUnit()
    outputs.upToDateWhen { false }
    systemProperty("skiko.renderApi", "SOFTWARE")
    testLogging {
        events("passed", "skipped", "failed")
        showStandardStreams = true
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}

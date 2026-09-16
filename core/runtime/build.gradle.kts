// SPDX-License-Identifier: Apache-2.0
// Part of the A3 universe. See LICENSE.
plugins {
    kotlin("jvm")
}

repositories {
    mavenCentral()
}

dependencies {
    api(project(":core:world"))
    implementation(project(":core:json"))
    implementation(project(":core:action"))
    testImplementation(kotlin("test"))
    testImplementation("com.tngtech.archunit:archunit-junit5:1.3.0")
}

kotlin {
    jvmToolchain(21)
}

tasks.test {
    useJUnitPlatform()
    testLogging {
        events("passed", "skipped", "failed")
        showStandardStreams = false
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}

tasks.processResources {
    from(rootProject.projectDir.resolve("schemas")) {
        into("a3/schemas")
    }
}

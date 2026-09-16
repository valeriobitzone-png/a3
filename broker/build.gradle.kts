// SPDX-License-Identifier: Apache-2.0
// Part of the A3 universe. See LICENSE.
plugins {
    kotlin("jvm")
    application
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(project(":core:json"))
    implementation(project(":core:admission"))
    implementation(project(":core:action"))
    implementation("org.biscuitsec:biscuit:4.0.1")
    testImplementation(kotlin("test"))
    testImplementation("com.tngtech.archunit:archunit-junit5:1.3.0")
}

kotlin {
    jvmToolchain(21)
}

application {
    mainClass.set("a3.broker.MainKt")
    applicationName = "a3"
}

tasks.test {
    useJUnitPlatform()
    testLogging {
        events("passed", "skipped", "failed")
        showStandardStreams = false
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
    dependsOn(tasks.named("installDist"))
}

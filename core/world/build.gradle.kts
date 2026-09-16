// SPDX-License-Identifier: Apache-2.0
// Part of the A3 universe. See LICENSE.
plugins {
    kotlin("jvm")
}

repositories {
    mavenCentral()
}

dependencies {
    api(project(":core:world-api"))
    api(project(":core:admission"))
}

kotlin {
    jvmToolchain(21)
}

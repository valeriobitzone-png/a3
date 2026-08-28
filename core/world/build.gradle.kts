plugins {
    kotlin("jvm")
}

repositories {
    mavenCentral()
}

dependencies {
    api(project(":core:world-api"))
}

kotlin {
    jvmToolchain(21)
}

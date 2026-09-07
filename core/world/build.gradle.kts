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

plugins {
    kotlin("jvm")
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(project(":core:action"))
    implementation(project(":core:admission"))
    implementation(project(":core:world"))
    implementation(project(":core:runtime"))
    implementation(project(":core:json"))
    implementation(project(":a3ui"))
    implementation(project(":broker"))
    implementation(project(":adapters:mcp"))
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

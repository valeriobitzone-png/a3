plugins {
    kotlin("jvm")
}

repositories {
    mavenCentral()
}

dependencies {
    api(project(":projection"))
    api(project(":core:world-api"))
    implementation("com.fasterxml.jackson.core:jackson-databind:2.18.3")
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
        include("a3uisurface.schema.json")
        into("a3/schemas")
    }
}

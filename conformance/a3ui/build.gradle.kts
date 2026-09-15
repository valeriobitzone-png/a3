plugins {
    kotlin("jvm")
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(project(":a3ui"))
    implementation(project(":a3ui:lifecycle"))
    api("com.fasterxml.jackson.core:jackson-databind:2.18.3")
    testImplementation(kotlin("test"))
}

kotlin {
    jvmToolchain(21)
}

tasks.test {
    useJUnitPlatform()
    dependsOn(":a3ui:conformance:android:test")
    dependsOn(":a3ui:conformance:mac:test")
    testLogging {
        events("passed", "skipped", "failed")
        showStandardStreams = true
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}

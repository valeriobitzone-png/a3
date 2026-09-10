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
    implementation(compose.desktop.currentOs) // org.jetbrains.compose.desktop
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

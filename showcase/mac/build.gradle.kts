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
    implementation(project(":renderers:mac-compose"))
    implementation(project(":renderers:android-core"))
    implementation(project(":a3ui"))
    implementation(compose.desktop.currentOs)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
    testImplementation(compose.desktop.uiTestJUnit4)
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit:2.2.10")
}

kotlin {
    jvmToolchain(21)
    sourceSets {
        named("main") {
            kotlin.srcDir("../shared")
        }
    }
}

compose.desktop {
    application {
        mainClass = "a3.showcase.mac.MainKt"
    }
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

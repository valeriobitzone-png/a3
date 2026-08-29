pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

rootProject.name = "a3"
include(":core:world-api")
include(":core:world")
include(":core:runtime")
include(":prediction")
include(":projection")
include(":a3ui")
include(":renderers:android-core")
include(":renderers:android-compose")

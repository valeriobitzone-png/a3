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
include(":core:json")
include(":core:admission")
include(":core:runtime")
include(":prediction")
include(":projection")
include(":a3ui")
include(":renderers:android-core")
include(":renderers:android-compose")
include(":adapters:mcp")
include(":intent-model")
include(":launcher")

pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "mapkit-android"
include(":source:mapkit-android-core")
include(":source:mapkit-android-webview")
include(":source:mapkit-android-compose")
include(":source:mapkit-android")
project(":source:mapkit-android").projectDir = file("source/mapkit-android")
include(":example:app")

rootProject.name = "listenup"
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

pluginManagement {
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
        // JetBrains Compose dev repository for alpha libraries (Navigation 3, Material 3 Adaptive)
        maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
    }
}

include(":androidApp")
include(":composeApp")
include(":desktopApp")
include(":shared")

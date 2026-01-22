import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidKmpLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

kotlin {
    // Android target using new AGP 9.0-compatible plugin
    androidLibrary {
        namespace = "com.calypsan.listenup.client.composeapp"
        compileSdk =
            libs.versions.android.compileSdk
                .get()
                .toInt()
        minSdk =
            libs.versions.android.minSdk
                .get()
                .toInt()

        // Enable Android resources (opt-in required with new KMP plugin)
        androidResources { enable = true }

        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
            freeCompilerArgs.addAll(
                "-Xexpect-actual-classes",
                "-Xreturn-value-checker=check",
                "-Xexplicit-backing-fields",
            )
        }

        lint {
            warningsAsErrors = false
            abortOnError = true
            checkDependencies = true
            htmlReport = true
            xmlReport = true
        }
    }

    // JVM target for desktop (Windows/Linux)
    jvm("desktop") {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
            freeCompilerArgs.addAll(
                "-Xexpect-actual-classes",
                "-Xreturn-value-checker=check",
                "-Xexplicit-backing-fields",
                "-Xskip-prerelease-check", // Skip pre-release Kotlin metadata check
            )
        }
    }

    sourceSets {
        androidMain.dependencies {
            implementation(libs.compose.ui.tooling.preview)
            implementation(libs.androidx.activity.compose)

            // Koin for Android
            implementation(libs.koin.android)
            implementation(libs.koin.compose)
            implementation(libs.koin.compose.viewmodel)

            // Kotlin libraries for playback
            implementation(libs.kotlin.logging)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.datetime)

            // Navigation 3 (new declarative navigation API)
            implementation(libs.androidx.navigation3.runtime)
            implementation(libs.androidx.navigation3.ui)
            implementation(libs.androidx.navigation3.ui.android)

            // Material 3 Adaptive - for all screen sizes (phones, tablets, desktops, XR, Auto)
            implementation(libs.androidx.material3.adaptive)
            implementation(libs.androidx.material3.adaptive.layout)
            implementation(libs.androidx.material3.adaptive.navigation)

            // WorkManager for background sync
            implementation(libs.androidx.work.runtime.ktx)

            // Coil for image loading
            implementation(libs.coil.compose)
            implementation(libs.coil.network.ktor3)

            // Material Icons Extended
            implementation(libs.androidx.material.icons.extended)

            // Media3 for audio playback
            implementation(libs.media3.exoplayer)
            implementation(libs.media3.exoplayer.hls)
            implementation(libs.media3.session)
            implementation(libs.media3.ui)
            implementation(libs.media3.datasource.okhttp)

            // Async/Future support for Media3 callbacks
            implementation(libs.concurrent.futures)

            // Palette for dynamic color extraction from cover art
            implementation(libs.androidx.palette.ktx)

            // Markdown Rendering
            implementation(libs.markdown.renderer.m3)

            // BlurHash for image placeholders
            implementation(libs.blurhash)

            // Vico Charts
            implementation(libs.vico.compose.m3)
        }
        commonMain.dependencies {
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.ui)
            implementation(libs.compose.components.resources)
            implementation(libs.compose.ui.tooling.preview)
            implementation(libs.androidx.lifecycle.viewmodelCompose)
            implementation(libs.androidx.lifecycle.runtimeCompose)
            implementation(projects.shared)
        }
        val desktopMain by getting {
            dependencies {
                // Koin for Desktop
                implementation(libs.koin.core)
                implementation(libs.koin.compose)
                implementation(libs.koin.compose.viewmodel)

                // Kotlin libraries
                implementation(libs.kotlin.logging)
                implementation(libs.kotlinx.serialization.json)
                implementation(libs.kotlinx.datetime)

                // Coil for image loading (multiplatform)
                implementation(libs.coil.compose)
                implementation(libs.coil.network.ktor3)
            }
        }
        // Note: Android tests use androidHostTest/androidDeviceTest source sets
        // with the new KMP plugin. Add test dependencies there when needed.
    }
}

// Compose UI tooling for Android preview support
dependencies {
    "androidRuntimeClasspath"(libs.compose.ui.tooling)
}

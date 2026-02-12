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

            // Koin Android-specific
            implementation(libs.koin.android)

            // Navigation 3 Android-specific (deep linking)
            implementation(libs.androidx.navigation3.ui.android)

            // WorkManager for background sync
            implementation(libs.androidx.work.runtime.ktx)

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

            // BlurHash for image placeholders
        }
        commonMain.dependencies {
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.ui)
            implementation(libs.compose.components.resources)
            implementation(libs.androidx.material.icons.extended)
            implementation(libs.compose.ui.tooling.preview)
            implementation(libs.androidx.lifecycle.viewmodelCompose)
            implementation(libs.androidx.lifecycle.runtimeCompose)
            implementation(projects.shared)

            // Navigation 3 (multiplatform)
            implementation(libs.navigation3.ui)

            // Material 3 Adaptive (multiplatform)
            implementation(libs.material3.adaptive)
            implementation(libs.material3.adaptive.layout)
            implementation(libs.material3.adaptive.navigation)

            // Koin (shared across platforms)
            implementation(libs.koin.core)
            implementation(libs.koin.compose)
            implementation(libs.koin.compose.viewmodel)

            // Markdown Rendering (multiplatform)
            implementation(libs.markdown.renderer.m3)

            // Kotlin libraries (shared)
            implementation(libs.kotlin.logging)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.datetime)
            implementation(libs.kotlinx.collections.immutable)

            // Coil for image loading (multiplatform)
            implementation(libs.coil.compose)
            implementation(libs.coil.network.ktor3)

            // KMPalette for cross-platform color extraction
            implementation(libs.kmpalette.core)
        }
        val desktopMain by getting {
            dependencies {
                // FFmpeg for audio decoding (self-contained, decodes all formats)
                implementation(libs.javacv)
                implementation(libs.javacpp)
                implementation(libs.ffmpeg)

                // Platform-specific native libraries (bundled in JARs)
                val javacppVersion = libs.versions.javacpp.get()
                val ffmpegVersion =
                    libs.versions.ffmpeg.javacpp
                        .get()
                implementation("org.bytedeco:javacpp:$javacppVersion:linux-x86_64")
                implementation("org.bytedeco:ffmpeg:$ffmpegVersion:linux-x86_64")
                implementation("org.bytedeco:javacpp:$javacppVersion:macosx-x86_64")
                implementation("org.bytedeco:ffmpeg:$ffmpegVersion:macosx-x86_64")
                implementation("org.bytedeco:javacpp:$javacppVersion:macosx-arm64")
                implementation("org.bytedeco:ffmpeg:$ffmpegVersion:macosx-arm64")
                implementation("org.bytedeco:javacpp:$javacppVersion:windows-x86_64")
                implementation("org.bytedeco:ffmpeg:$ffmpegVersion:windows-x86_64")
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

// --- Localization: generate platform string files from shared JSON ---
val generateStrings by tasks.registering {
    group = "localization"
    description = "Generate platform string resource files from shared JSON"

    val stringsDir = rootProject.file("shared/src/commonMain/resources/strings")
    val outputDir = project.file("src/commonMain/composeResources")

    inputs.dir(stringsDir)
    // Don't declare outputs.dir on composeResources — it conflicts with Compose plugin tasks.
    // Instead we just generate files in place and let Compose pick them up.

    doLast {
        stringsDir.listFiles { f -> f.extension == "json" }?.forEach { jsonFile ->
            val locale = jsonFile.nameWithoutExtension
            val valuesFolder = if (locale == "en") "values" else "values-" + locale
            val outDir = outputDir.resolve(valuesFolder)
            outDir.mkdirs()

            @Suppress("UNCHECKED_CAST")
            val map = groovy.json.JsonSlurper().parseText(jsonFile.readText()) as Map<String, Any>

            val entries = mutableListOf<Pair<String, String>>()

            fun flatten(
                prefix: String,
                obj: Any,
            ) {
                when (obj) {
                    is Map<*, *> -> {
                        obj.forEach { (k, v) ->
                            flatten(if (prefix.isEmpty()) k.toString() else prefix + "_" + k, v!!)
                        }
                    }

                    else -> {
                        entries.add(prefix to obj.toString())
                    }
                }
            }
            flatten("", map)

            val sb = StringBuilder()
            sb.appendLine("<?xml version=\"1.0\" encoding=\"utf-8\"?>")
            sb.appendLine("<resources>")
            entries.sortedBy { it.first }.forEach { (key, value) ->
                val escaped = value.replace("&", "&amp;").replace("<", "&lt;").replace("'", "\\'")
                sb.appendLine("    <string name=\"" + key + "\">" + escaped + "</string>")
            }
            sb.appendLine("</resources>")
            outDir.resolve("strings.xml").writeText(sb.toString())
            println("Generated " + valuesFolder + "/strings.xml (" + entries.size + " strings)")
        }
    }
}

tasks
    .matching {
        it.name.startsWith("generateComposeResClass") ||
            it.name.startsWith("convertXmlValueResources") ||
            it.name.startsWith("copyNonXmlValueResources") ||
            it.name.startsWith("prepareComposeResources") ||
            it.name.startsWith("generateActualResourceCollectors")
    }.configureEach {
        dependsOn(generateStrings)
    }

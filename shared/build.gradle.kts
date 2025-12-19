import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.androidx.room)
    alias(libs.plugins.mokkery)
}

kotlin {
    androidTarget {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
            freeCompilerArgs.add("-Xexpect-actual-classes")
        }
    }

    targets.all {
        compilations.all {
            compileTaskProvider.configure {
                compilerOptions {
                    freeCompilerArgs.add("-Xexpect-actual-classes")
                }
            }
        }
    }

    listOf(
        iosArm64(),
        iosSimulatorArm64(),
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "Shared"
            isStatic = true

            // Export Koin so it's accessible from Swift
            export(libs.koin.core)
        }
    }

    // TODO: Enable Native Swift Export when Gradle API is available
    // Swift Export is experimental in Kotlin 2.3.0-Beta2 but not yet exposed in Gradle plugin
    // For now, we'll use traditional framework export which still works great

    sourceSets {
        commonMain.dependencies {
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.serialization.json)
            implementation(libs.ktor.client.logging)
            implementation(libs.ktor.client.auth)

            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.datetime)
            implementation(libs.kotlinx.collections.immutable)
            implementation(libs.kotlinx.io.core)

            implementation(libs.blurhash)

            api(libs.koin.core)
            implementation(libs.kotlin.logging)

            implementation(libs.androidx.lifecycle.viewmodelCompose)
            implementation(libs.androidx.room.runtime)
            implementation(libs.androidx.sqlite.bundled)
        }

        androidMain.dependencies {
            implementation(libs.ktor.client.okhttp)
            implementation(libs.androidx.palette.ktx)
        }

        iosMain.dependencies {
            implementation(libs.ktor.client.darwin)
        }

        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.mokkery.runtime)
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}

android {
    namespace = "com.calypsan.listenup.client.shared"
    compileSdk =
        libs.versions.android.compileSdk
            .get()
            .toInt()
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    defaultConfig {
        minSdk =
            libs.versions.android.minSdk
                .get()
                .toInt()
    }

    lint {
        warningsAsErrors = false
        abortOnError = true
        checkDependencies = false // Avoid KMP dependency double-scanning
        htmlReport = true
        xmlReport = true
        // KMP-specific accommodations
        disable +=
            setOf(
                "InvalidPackage", // False positives on multiplatform expect/actual
                "ObsoleteLintCustomCheck", // Third-party KMP libs may trigger this
            )
    }
}

// Define Room Schema location (optional but good practice)
room {
    schemaDirectory("$projectDir/schemas")
}

// Wire KSP for Room - platform-specific targets required
dependencies {
    // Common metadata (required for Room KMP expect/actual generation)
    add("kspCommonMainMetadata", libs.androidx.room.compiler)

    // Android target
    add("kspAndroid", libs.androidx.room.compiler)

    // iOS targets
    add("kspIosArm64", libs.androidx.room.compiler)
    add("kspIosSimulatorArm64", libs.androidx.room.compiler)
}

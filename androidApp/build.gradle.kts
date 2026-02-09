// Auto-version from git tags (e.g. v0.1.0 -> versionName "0.1.0", versionCode from commit count)
fun gitVersionName(): String =
    try {
        providers
            .exec {
                commandLine("git", "describe", "--tags", "--abbrev=0")
                isIgnoreExitValue = true
            }.standardOutput.asText
            .get()
            .trim()
            .removePrefix("v")
            .ifEmpty { "0.0.1" }
    } catch (_: Exception) {
        "0.0.1"
    }

fun gitVersionCode(): Int =
    try {
        providers
            .exec {
                commandLine("git", "rev-list", "--count", "HEAD")
                isIgnoreExitValue = true
            }.standardOutput.asText
            .get()
            .trim()
            .toIntOrNull() ?: 1
    } catch (_: Exception) {
        1
    }

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeCompiler)
}

android {
    namespace = "com.calypsan.listenup.client"
    compileSdk =
        libs.versions.android.compileSdk
            .get()
            .toInt()

    defaultConfig {
        applicationId = "com.calypsan.listenup.client"
        minSdk =
            libs.versions.android.minSdk
                .get()
                .toInt()
        targetSdk =
            libs.versions.android.targetSdk
                .get()
                .toInt()
        versionCode = gitVersionCode()
        versionName = gitVersionName()
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    signingConfigs {
        create("release") {
            val ksFile = System.getenv("KEYSTORE_FILE")
            if (ksFile != null && file(ksFile).exists()) {
                storeFile = file(ksFile)
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KEY_ALIAS")
                keyPassword = System.getenv("KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    lint {
        warningsAsErrors = false
        abortOnError = true
        checkDependencies = true
        htmlReport = true
        xmlReport = true
        sarifReport = true
    }
}

dependencies {
    implementation(project(":composeApp"))
    // SLF4J Android backend - routes kotlin-logging to Logcat
    implementation(libs.slf4j.android)
}

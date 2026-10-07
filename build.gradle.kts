/*
 * SPDX-FileCopyrightText: 2023 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

import org.lineageos.generatebp.GenerateBpPluginExtension
import org.lineageos.generatebp.models.Module

// AGP 9 bundles KGP 2.2.10, but miuix-blur needs Kotlin 2.4 metadata:
// upgrade the built-in Kotlin per the AGP 9.0 release notes
// ("Runtime dependency on Kotlin Gradle plugin").
buildscript {
    dependencies {
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.4.20")
    }
}

plugins {
    id("com.android.application") version "9.4.0"
    // AGP 9 provides built-in Kotlin: no kotlin.android plugin.
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.20"
    id("org.lineageos.generatebp") version "+"
}

android {
    compileSdk = 37
    namespace = "com.android.calculator2"

    defaultConfig {
        applicationId = "com.android.calculator2"
        // miuix-blur (OriginSU liquid glass) requires minSdk 33.
        minSdk = 33
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        getByName("release") {
            // Enables code shrinking, obfuscation, and optimization.
            isMinifyEnabled = true

            // Enables resource shrinking.
            isShrinkResources = true

            // Includes the default ProGuard rules files.
            setProguardFiles(
                listOf(
                    getDefaultProguardFile("proguard-android-optimize.txt"),
                    "proguard.flags"
                )
            )
        }
        getByName("debug") {
            // Append .dev to package name so we won't conflict with AOSP build.
            applicationIdSuffix = ".dev"
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    sourceSets {
        getByName("main") {
            res.srcDirs("res")
            java.srcDirs("src")
            assets.srcDirs("assets")
            manifest.srcFile("AndroidManifest.xml")
        }
    }
}

dependencies {
    implementation("androidx.gridlayout:gridlayout:1.0.0")
    implementation("androidx.webkit:webkit:1.7.0-alpha02")
    implementation("com.google.android.material:material:1.14.0-alpha09")
    implementation("com.hp:crcalc:1.0")

    // OriginSU liquid-glass bottom bar: exact Compose port.
    implementation(platform("androidx.compose:compose-bom:2026.09.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.animation:animation-core")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.runtime:runtime")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0")
    implementation("top.yukonga.miuix.kmp:miuix-blur-android:0.9.4-rc01")
}

configure<GenerateBpPluginExtension> {
    targetSdk.set(android.defaultConfig.targetSdk!!)
    minSdk.set(android.defaultConfig.minSdk!!)
    versionCode.set(android.defaultConfig.versionCode!!)
    versionName.set(android.defaultConfig.versionName!!)
    availableInAOSP.set { module: Module ->
        when {
            module.group.startsWith("androidx") -> true
            module.group.startsWith("org.jetbrains") -> true
            module.group == "com.google.android.material" -> true
            module.group == "com.google.errorprone" -> true
            module.group == "com.google.guava" -> true
            module.group == "junit" -> true
            else -> false
        }
    }
}

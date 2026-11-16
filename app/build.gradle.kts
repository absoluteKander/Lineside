// Lineside: keeps track of the calls you're waiting on.
// Copyright (C) 2026 Absolute Candor. Licensed under the GNU GPL v3 or later; see LICENSE.

import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
    id("androidx.room")
}

room {
    // Committed so database migrations can be checked against the real schema. The Room plugin
    // writes it safely even when both builds compile at once.
    schemaDirectory("$projectDir/schemas")
}

android {
    namespace = "app.lineside"
    compileSdk = 36
    defaultConfig {
        applicationId = "app.lineside"
        minSdk = 31
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // Two builds from one codebase. Play policy only allows reading SMS for the default SMS app,
    // so the Play build gets texts through the share sheet instead.
    flavorDimensions += "distribution"
    productFlavors {
        create("foss") {
            dimension = "distribution"
            buildConfigField("boolean", "READS_SMS", "true")
        }
        create("play") {
            dimension = "distribution"
            buildConfigField("boolean", "READS_SMS", "false")
        }
    }

    // Release signing: read from keystore.properties next to this project's root, which is never committed
    // (see .gitignore). Without it, release builds come out unsigned.
    val keystoreFile = rootProject.file("keystore.properties")
    val signing = if (keystoreFile.exists()) Properties().apply { keystoreFile.inputStream().use { load(it) } } else null
    if (signing != null) signingConfigs.create("release") {
        storeFile = rootProject.file(signing.getProperty("storeFile"))
        storePassword = signing.getProperty("storePassword")
        keyAlias = signing.getProperty("keyAlias")
        keyPassword = signing.getProperty("keyPassword")
    }

    buildTypes {
        // Each installed copy needs its own account type, or debug and release builds would fight over it.
        debug {
            applicationIdSuffix = ".debug"
            resValue("string", "account_type", "app.lineside.debug.loops")
        }
        release {
            isMinifyEnabled = false
            if (signing != null) signingConfig = signingConfigs.getByName("release")
            resValue("string", "account_type", "app.lineside.loops")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    // No dependency-metadata block in the APK: it is encrypted for Google, and F-Droid's checks object to it.
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }

dependencies {
    implementation(platform("androidx.compose:compose-bom:2025.06.01"))
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.9.1")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.1")
    implementation("androidx.room:room-runtime:2.7.2")
    implementation("androidx.room:room-ktx:2.7.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    implementation("androidx.work:work-runtime-ktx:2.10.1")
    // The encrypted export: a standard AES-256 ZIP that 7-Zip, Keka and PeaZip can open. Pure Java, no network.
    implementation("net.lingala.zip4j:zip4j:2.11.5")
    ksp("androidx.room:room-compiler:2.7.2")
    testImplementation("junit:junit:4.13.2")
    // Android's org.json is a stub in unit tests; the real one lets the export's JSON be tested on the JVM.
    testImplementation("org.json:json:20240303")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.room:room-testing:2.7.2")
}

// Guard: Lineside must never be able to reach the internet. Fails the build if any
// merged manifest (ours or a library's) ends up requesting a network permission.
val forbidden = listOf("android.permission.INTERNET", "android.permission.ACCESS_NETWORK_STATE")
androidComponents {
    onVariants { variant ->
        val name = variant.name.replaceFirstChar { it.uppercase() }
        val check = tasks.register("check${name}NoInternet") {
            val manifest = variant.artifacts.get(com.android.build.api.artifact.SingleArtifact.MERGED_MANIFEST)
            inputs.file(manifest)
            doLast {
                val text = manifest.get().asFile.readText()
                val hits = forbidden.filter { text.contains("android:name=\"$it\"") }
                check(hits.isEmpty()) { "Network permission found in ${variant.name} manifest: $hits" }
            }
        }
        // Both ways of packaging a build: APKs and App Bundles.
        tasks.matching { it.name == "assemble$name" || it.name == "bundle$name" }.configureEach { dependsOn(check) }
    }
}

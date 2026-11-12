// Lineside: keeps track of the calls you're waiting on.
// Copyright (C) 2026 Absolute Candor. Licensed under the GNU GPL v3 or later; see LICENSE.

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
        versionName = "0.1.0"
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

    buildTypes {
        // Each installed copy needs its own account type, or debug and release builds would fight over it.
        debug {
            applicationIdSuffix = ".debug"
            resValue("string", "account_type", "app.lineside.debug.loops")
        }
        release {
            isMinifyEnabled = false
            resValue("string", "account_type", "app.lineside.loops")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
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
    ksp("androidx.room:room-compiler:2.7.2")
    testImplementation("junit:junit:4.13.2")
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
        tasks.matching { it.name == "assemble$name" }.configureEach { dependsOn(check) }
    }
}

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.talotv.os"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.talotv.os"
        minSdk = 26 // Android 8.0 Oreo (compatible avec Android TV & Google TV Streamer 4K)
        targetSdk = 34
        versionCode = 1
        versionName = "0.0.1"

        buildConfigField("String", "CREATOR", "\"sudo-aptttt\"")
        buildConfigField("String", "APP_DESCRIPTION", "\"Talo OS — Interface d'accueil épurée sans publicité pour Google TV Streamer 4K\"")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("debug") // Debug signing for development
        }
        debug {
            applicationIdSuffix = ".debug"
            isDebuggable = true
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)

    // Android TV Compose Foundation & Material
    implementation(libs.androidx.tv.foundation)
    implementation(libs.androidx.tv.material)

    // Coil for asynchronous image loading with memory-safe cache
    implementation(libs.coil.compose)

    // Coroutines & Concurrency
    implementation(libs.kotlinx.coroutines.android)

    // Ktor for Secure API Handshake & OTA Downloads
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)

    // Offline QR Code Generation
    implementation("com.google.zxing:core:3.5.3")
}

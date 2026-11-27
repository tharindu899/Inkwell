import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Config comes from environment (GitHub Actions) or local.properties (local builds).
val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
fun cfg(name: String): String =
    System.getenv(name)?.takeIf { it.isNotBlank() } ?: localProps.getProperty(name, "")

android {
    // Same package name as the React/Capacitor build → the existing Google Cloud
    // Android OAuth credential (package + SHA-1) keeps working.
    namespace = "com.inkwell.notes"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.inkwell.notes"
        minSdk = 24
        targetSdk = 35
        versionCode = (System.getenv("APP_VERSION_CODE") ?: "1").toInt()
        versionName = System.getenv("APP_VERSION_NAME") ?: "2.0.0-dev"

        buildConfigField("String", "GOOGLE_CLIENT_ID", "\"${cfg("VITE_GOOGLE_CLIENT_ID")}\"")

        // Repo the update checker reads releases from: VITE_GITHUB_REPO override →
        // the repo running the GitHub Action → the original Inkwell repo.
        val githubRepo = cfg("VITE_GITHUB_REPO").ifBlank {
            System.getenv("GITHUB_REPOSITORY")?.takeIf { it.isNotBlank() } ?: "tharindu899/Inkwell"
        }
        buildConfigField("String", "GITHUB_REPO", "\"$githubRepo\"")
    }

    val keystorePath = System.getenv("KEYSTORE_PATH")
    signingConfigs {
        if (!keystorePath.isNullOrBlank()) {
            create("release") {
                storeFile = file(keystorePath)
                storePassword = System.getenv("STORE_PASSWORD")
                keyAlias = System.getenv("KEY_ALIAS")
                keyPassword = System.getenv("KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = if (!keystorePath.isNullOrBlank()) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
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

    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.10.01")
    implementation(composeBom)

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.animation:animation")

    // Google profile picture in the Home top bar
    implementation("io.coil-kt:coil-compose:2.7.0")

    // Native Google Sign-In (same SDK the Capacitor plugin used)
    implementation("com.google.android.gms:play-services-auth:21.2.0")
}

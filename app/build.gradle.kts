plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Same Google OAuth *Web* client ID the old app used (GitHub secret VITE_GOOGLE_CLIENT_ID).
// CI passes it as env GOOGLE_CLIENT_ID. Locally: -PGOOGLE_CLIENT_ID=xxxx
val googleClientId: String =
    System.getenv("GOOGLE_CLIENT_ID")
        ?: providers.gradleProperty("GOOGLE_CLIENT_ID").orNull
        ?: ""

android {
    namespace = "com.inkwell.notes"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.inkwell.notes"
        minSdk = 26
        targetSdk = 35
        versionCode = (System.getenv("RUN_NUMBER") ?: "1").toInt()
        versionName = System.getenv("VERSION") ?: "1.0.0"
        buildConfigField("String", "GOOGLE_CLIENT_ID", "\"$googleClientId\"")
    }

    signingConfigs {
        create("release") {
            val ks = System.getenv("KEYSTORE_PATH")
            if (ks != null) {
                storeFile = file(ks)
                storePassword = System.getenv("STORE_PASSWORD")
                keyAlias = System.getenv("KEY_ALIAS")
                keyPassword = System.getenv("KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = if (System.getenv("KEYSTORE_PATH") != null)
                signingConfigs.getByName("release")
            else
                signingConfigs.getByName("debug")
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
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.animation:animation")
    implementation("androidx.compose.material3:material3")

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")

    // Google sign-in (Credential Manager)
    implementation("androidx.credentials:credentials:1.3.0")
    implementation("androidx.credentials:credentials-play-services-auth:1.3.0")
    implementation("com.google.android.libraries.identity.googleid:googleid:1.1.1")
}

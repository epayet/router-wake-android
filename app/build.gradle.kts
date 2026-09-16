plugins {
    alias(libs.plugins.android.application)
    // No org.jetbrains.kotlin.android plugin: AGP 9+ has built-in Kotlin
    // support and applying it is no longer allowed. The Compose compiler
    // plugin is still needed separately.
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.jakspinning.wakemypc"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.jakspinning.wakemypc"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "0.1"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    // No separate kotlinOptions/jvmTarget block: with built-in Kotlin, the
    // Kotlin JVM target defaults to compileOptions.targetCompatibility above.

    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.squareup.okhttp)
    implementation(libs.kotlinx.coroutines.android)

    debugImplementation(libs.androidx.ui.tooling)
}

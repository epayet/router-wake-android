plugins {
    alias(libs.plugins.android.application) apply false
    // No org.jetbrains.kotlin.android: AGP 9+ has built-in Kotlin support.
    alias(libs.plugins.kotlin.compose) apply false
}

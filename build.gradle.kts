buildscript {
    dependencies {
        // Überschreibt die in AGP 9 eingebaute Kotlin-Version (2.2.10) — siehe AGP-9.0-Release-Notes
        classpath(libs.kotlin.gradle.plugin)
    }
}

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
}

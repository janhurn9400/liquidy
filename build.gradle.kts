buildscript {
    dependencies {
        // Overrides the Kotlin version built into AGP 9 (2.2.10) — see the AGP 9.0 release notes
        classpath(libs.kotlin.gradle.plugin)
    }
}

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
}

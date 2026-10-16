plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "io.github.janhurn9400.liquidy"
    compileSdk = 37

    defaultConfig {
        applicationId = "io.github.janhurn9400.liquidy"
        // 33: RenderEffect blur (31) + AGSL RuntimeShader (33), see docs/ARCHITECTURE.md
        minSdk = 33
        targetSdk = 36
        versionCode = 8
        versionName = "0.4.2"
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
    buildFeatures {
        compose = true
    }
}

dependencies {
    compileOnly(project(":hiddenapi")) // internal animation classes, only used with the Animation Module
    implementation(libs.core.ktx)
    implementation(libs.activity.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.foundation)
    // only for the debug panel (sliders), not for the home screen itself
    implementation(libs.compose.material3)
    implementation(libs.backdrop)
    implementation(libs.kyant.shapes)
}

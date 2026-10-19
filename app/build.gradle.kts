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
        versionCode = 10
        versionName = "0.6.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            // Published APKs are release builds (AOT-optimised, much smoother than debug: ~40 % → ~9 % janky frames when
            // pulling down search on a Snapdragon 780G). Signed with the same key as the earlier debug releases so they
            // install over existing installs without losing the layout.
            signingConfig = signingConfigs.getByName("debug")
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

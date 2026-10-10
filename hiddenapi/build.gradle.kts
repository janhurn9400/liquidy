// Nur Kompilier-Stubs für interne Android-Klassen (android.window.*), die zur Laufzeit aus dem System kommen.
// Wird von :app als compileOnly genutzt — landet NICHT im APK. Nutzbar nur als System-App mit Hidden-API-Freigabe (Magisk-Modul).
plugins { `java-library` }
java { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
dependencies { compileOnly(files("${System.getenv("ANDROID_HOME") ?: System.getProperty("user.home") + "/Library/Android/sdk"}/platforms/android-36/android.jar")) }

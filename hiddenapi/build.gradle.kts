// Compile-only stubs for internal Android classes (android.window.*) that come from the system at runtime.
// Used by :app as compileOnly — does NOT end up in the APK. Usable only as a system app with hidden API allowlist (Animation Module).
plugins { `java-library` }
java { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
dependencies { compileOnly(files("${System.getenv("ANDROID_HOME") ?: System.getProperty("user.home") + "/Library/Android/sdk"}/platforms/android-36/android.jar")) }

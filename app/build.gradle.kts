// Math Stars: a math practice game for kids (addition, subtraction, multiplication, grades 1-6).
// Plain Android framework UI in Kotlin (AGP 9's built-in Kotlin), no runtime libraries.
plugins {
    id("com.android.application")
}

android {
    namespace = "com.mathstars.kids"
    compileSdk = 36
    buildToolsVersion = "37.0.0"

    defaultConfig {
        applicationId = "com.mathstars.kids"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }
}

dependencies {
    testImplementation("junit:junit:4.13.2")
}

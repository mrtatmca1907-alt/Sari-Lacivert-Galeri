plugins {
    id("com.android.application")
}

android {
    namespace = "com.atmaca.videokareleri"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.atmaca.videokareleri"
        minSdk = 26
        targetSdk = 33
        versionCode = 6
        versionName = "6.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }
}

dependencies {
    implementation("androidx.documentfile:documentfile:1.0.1")
    testImplementation("junit:junit:4.13.2")
}

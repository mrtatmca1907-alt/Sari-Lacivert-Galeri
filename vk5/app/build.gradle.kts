plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.videokareleri.v5"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.videokareleri.v5"
        minSdk = 29
        targetSdk = 35
        versionCode = 5
        versionName = "5.0-kotlin"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
}

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.kapt")
    id("com.chaquo.python")
}

android {
    namespace = "com.atmaca.reeldroppro"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.atmaca.reeldroppro"
        minSdk = 26
        targetSdk = 35
        versionCode = 3
        versionName = "2.0"
        ndk { abiFilters += listOf("arm64-v8a") }
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    buildTypes { release { isMinifyEnabled = false } }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
    packaging { jniLibs.useLegacyPackaging = true }
}

chaquopy {
    defaultConfig {
        version = "3.13"
        pip {
            install("gallery-dl==1.32.9")
        }
        pyc {
            src = true
            pip = true
            stdlib = true
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    kapt("androidx.room:room-compiler:2.6.1")
    implementation("androidx.work:work-runtime-ktx:2.10.0")
    implementation("io.github.junkfood02.youtubedl-android:library:0.18.1")
    implementation("io.github.junkfood02.youtubedl-android:ffmpeg:0.18.1")
    implementation("io.github.junkfood02.youtubedl-android:aria2c:0.18.1")
    testImplementation("junit:junit:4.13.2")
}

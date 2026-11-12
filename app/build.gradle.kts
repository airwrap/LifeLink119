plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.lifelink119.app"
    compileSdk = 34
    useLibrary("android.car", false)

    defaultConfig {
        applicationId = "com.lifelink119.app"
        minSdk = 28
        targetSdk = 34
        versionCode = 20
        versionName = "0.18.0-demo"
        testInstrumentationRunner = "android.test.InstrumentationTestRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { buildConfig = true }
    buildTypes { release { isMinifyEnabled = false } }
}

dependencies {
    // 공식 Pleos Vehicle SDK: 버전은 project-setup 문서 및 공식 샘플 기준.
    implementation("ai.pleos.playground:Vehicle:2.3.2")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
}

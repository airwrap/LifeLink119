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
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("boolean", "DEMO_FEATURES", "false")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { buildConfig = true }
    // Reproducible optional offline Korean model, prepared outside source control.
    sourceSets.getByName("main").assets.srcDir(rootProject.file("../.tools/speech/assets"))
    sourceSets.getByName("androidTest").assets.srcDir(rootProject.file("../.tools/speech/test-assets"))
    buildTypes {
        debug { buildConfigField("boolean", "DEMO_FEATURES", "true") }
        release { isMinifyEnabled = false }
        create("demo") {
            initWith(getByName("release"))
            isDebuggable = false
            signingConfig = signingConfigs.getByName("debug")
            buildConfigField("boolean", "DEMO_FEATURES", "true")
            matchingFallbacks += listOf("release")
        }
    }
}

dependencies {
    // 공식 Pleos Vehicle SDK: 버전은 project-setup 문서 및 공식 샘플 기준.
    implementation("ai.pleos.playground:Vehicle:2.3.2")
    implementation("ai.pleos.playground:FusedLocation:1.1.3")
    implementation("ai.pleos.playground:NaviHelper:3.0.5")
    implementation("net.java.dev.jna:jna:5.18.1@aar")
    implementation("com.alphacephei:vosk-android:0.3.75@aar")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
}

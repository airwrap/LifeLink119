plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// The submission uses the real release build type, with explicitly enabled mock tools.
// Credentials are supplied only by the local build process, never by tracked files.
val submissionRelease = providers.gradleProperty("lifelinkSubmission")
    .map { it.toBooleanStrict() }.getOrElse(false)
val releaseKeyStore = providers.environmentVariable("LIFELINK_RELEASE_KEYSTORE").orNull
val releaseStorePassword = providers.environmentVariable("LIFELINK_RELEASE_STORE_PASSWORD").orNull
val releaseKeyAlias = providers.environmentVariable("LIFELINK_RELEASE_KEY_ALIAS").orNull
val releaseKeyPassword = providers.environmentVariable("LIFELINK_RELEASE_KEY_PASSWORD").orNull
val releaseSigningReady = listOf(releaseKeyStore, releaseStorePassword, releaseKeyAlias, releaseKeyPassword)
    .all { !it.isNullOrBlank() }
if (submissionRelease && !releaseSigningReady) {
    throw GradleException("Submission release requires private signing environment variables. Run Build-Release.cmd.")
}

android {
    namespace = "com.lifelink119.app"
    compileSdk = 34
    useLibrary("android.car", false)

    defaultConfig {
        applicationId = "com.lifelink119.app"
        minSdk = 28
        targetSdk = 34
        versionCode = 24
        versionName = "0.18.4-web-import"
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
    if (submissionRelease) {
        sourceSets.getByName("release") {
            manifest.srcFile("src/demo/AndroidManifest.xml")
            res.srcDir("src/demo/res")
        }
    }
    if (releaseSigningReady) {
        signingConfigs.create("submission") {
            storeFile = file(requireNotNull(releaseKeyStore))
            storePassword = releaseStorePassword
            keyAlias = releaseKeyAlias
            keyPassword = releaseKeyPassword
            enableV1Signing = true
            enableV2Signing = true
        }
    }
    buildTypes {
        debug { buildConfigField("boolean", "DEMO_FEATURES", "true") }
        release {
            isDebuggable = false
            isMinifyEnabled = false
            buildConfigField("boolean", "DEMO_FEATURES", submissionRelease.toString())
            if (releaseSigningReady) signingConfig = signingConfigs.getByName("submission")
        }
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

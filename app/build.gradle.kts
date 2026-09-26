import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Gemini key: from local.properties (gitignored) or the GEMINI_API_KEY env var. Never commit it.
val geminiKey: String = run {
    val f = rootProject.file("local.properties")
    val props = Properties().apply { if (f.exists()) f.inputStream().use { load(it) } }
    props.getProperty("GEMINI_API_KEY") ?: System.getenv("GEMINI_API_KEY") ?: ""
}

val terminoxAppId = (findProperty("terminox.appId") as String?) ?: "dev.terminox"

android {
    namespace = "dev.terminox"
    compileSdk = 35

    defaultConfig {
        applicationId = terminoxAppId
        minSdk = 24
        // proot runs from the native library dir, but guest programs are loaded from app data;
        // targeting 28 keeps that working on every Android version.
        //noinspection ExpiredTargetSdkVersion
        targetSdk = 28
        versionCode = 1
        versionName = "1.0.0"
        vectorDrawables.useSupportLibrary = true
        buildConfigField("String", "GEMINI_API_KEY", "\"$geminiKey\"")
        // Only ABIs we ship a proot build for.
        ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64") }
    }

    // Release signing comes from the environment (CI secrets or your shell); never commit keys.
    // Without it, builds are signed with the local debug key.
    val releaseKeystore = System.getenv("TERMINOX_KEYSTORE")?.let { file(it) }?.takeIf { it.exists() }
    signingConfigs {
        if (releaseKeystore != null) create("release") {
            storeFile = releaseKeystore
            storePassword = System.getenv("TERMINOX_KEYSTORE_PASSWORD")
            keyAlias = System.getenv("TERMINOX_KEY_ALIAS")
            keyPassword = System.getenv("TERMINOX_KEY_PASSWORD")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        jniLibs {
            // proot is shipped as lib*.so and must be extracted to disk to be executable.
            useLegacyPackaging = true
        }
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
    testOptions {
        unitTests.isIncludeAndroidResources = true
        unitTests.all {
            System.getenv("ROBOLECTRIC_DEPS")?.let { dir ->
                it.systemProperty("robolectric.offline", "true")
                it.systemProperty("roborazzi.test.record", "true")
                it.testLogging.showStandardStreams = true
                it.systemProperty("robolectric.dependency.dir", dir)
            }
        }
    }
    lint {
        disable += setOf("ExpiredTargetSdkVersion", "OldTargetApi")
        checkReleaseBuilds = false
        abortOnError = false
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.animation:animation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-service:2.8.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    // Real terminal emulator + PTY (Apache-2.0 libraries from termux-app)
    implementation("com.github.termux.termux-app:terminal-view:v0.118.0")
    implementation("com.github.termux.termux-app:terminal-emulator:v0.118.0")

    implementation("androidx.media3:media3-exoplayer:1.5.0")
    implementation("androidx.media3:media3-session:1.5.0")
    implementation("io.coil-kt:coil-compose:2.7.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.tukaani:xz:1.10")

    // Screenshot rendering of the UI on the JVM (dev only)
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.14.1")
    testImplementation("io.github.takahirom.roborazzi:roborazzi:1.39.0")
    testImplementation("io.github.takahirom.roborazzi:roborazzi-compose:1.39.0")
    testImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

val terminoxAppId = (findProperty("terminox.appId") as String?) ?: "dev.terminox"

android {
    namespace = "dev.terminox"
    compileSdk = 35

    defaultConfig {
        applicationId = terminoxAppId
        minSdk = 24
        // Must stay <= 28: Android 10+ forbids exec() of files in the app data dir
        // for apps targeting 29+, which would break every binary in $PREFIX (same as Termux).
        //noinspection ExpiredTargetSdkVersion
        targetSdk = 28
        versionCode = 1
        versionName = "1.0.0"
        vectorDrawables.useSupportLibrary = true
    }

    signingConfigs {
        create("terminox") {
            // Public sideload key so every build can update the previous one.
            // Override with TERMINOX_KEYSTORE / TERMINOX_KEYSTORE_PASSWORD / TERMINOX_KEY_ALIAS / TERMINOX_KEY_PASSWORD.
            storeFile = file(System.getenv("TERMINOX_KEYSTORE") ?: "terminox-dev.jks")
            storePassword = System.getenv("TERMINOX_KEYSTORE_PASSWORD") ?: "terminox"
            keyAlias = System.getenv("TERMINOX_KEY_ALIAS") ?: "terminox"
            keyPassword = System.getenv("TERMINOX_KEY_PASSWORD") ?: "terminox"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("terminox")
        }
        debug {
            signingConfig = signingConfigs.getByName("terminox")
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
            // Keep libtermux.so extractable like upstream Termux.
            useLegacyPackaging = true
        }
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
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
}

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    // Screenshot tests: renders screens to PNGs on the JVM (./gradlew recordRoborazziRelease).
    id("io.github.takahirom.roborazzi")
}

// LiveKit Cloud "Development token server" ID. Comes from the LIVEKIT_TOKEN_SERVER_ID
// GitHub secret in CI; riders can also paste it in the app's settings.
val tokenServerId: String = System.getenv("LIVEKIT_TOKEN_SERVER_ID")
    ?: (project.findProperty("livekitTokenServerId") as String?)
    ?: ""

// Private ride server (Cloudflare Worker) address, from the RIDE_SERVER_URL secret in CI.
val rideServerUrl: String = System.getenv("RIDE_SERVER_URL")
    ?: (project.findProperty("rideServerUrl") as String?)
    ?: ""

// CI sets BUILD_NUMBER so every APK installs as an update over the previous one.
val buildNumber: Int = System.getenv("BUILD_NUMBER")?.toIntOrNull() ?: 1

android {
    namespace = "com.ridecomm.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.ridecomm.app"
        minSdk = 26
        targetSdk = 35
        versionCode = buildNumber
        versionName = "0.1.$buildNumber"
        buildConfigField("String", "DEFAULT_TOKEN_SERVER_ID", "\"$tokenServerId\"")
        buildConfigField("String", "DEFAULT_RIDE_SERVER_URL", "\"$rideServerUrl\"")

        ndk {
            // Real phones only; keeps the WebRTC native libraries out of the APK for other ABIs.
            abiFilters += listOf("arm64-v8a", "armeabi-v7a")
        }
    }

    signingConfigs {
        // Shared test key committed to the repo so every build installs over the last one.
        // Replace with a private key before publishing on the Play Store.
        create("ridecomm") {
            storeFile = rootProject.file("keystore/ridecomm-test.jks")
            storePassword = "ridecomm"
            keyAlias = "ridecomm"
            keyPassword = "ridecomm"
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("ridecomm")
        }
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("ridecomm")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation("io.livekit:livekit-android:2.29.0")
    implementation("androidx.media3:media3-exoplayer:1.11.1")

    implementation(platform("androidx.compose:compose-bom:2025.12.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.4")
    implementation("androidx.core:core-ktx:1.16.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.17")
    testImplementation("io.github.takahirom.roborazzi:roborazzi:1.76.0")
    testImplementation("io.github.takahirom.roborazzi:roborazzi-compose:1.76.0")
    testImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}

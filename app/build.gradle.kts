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

// Google Maps key for the Group map (Maps SDK for Android), from the GOOGLE_MAPS_API_KEY secret in CI.
// Without it the Group map uses OpenStreetMap, as before.
val mapsApiKey: String = System.getenv("GOOGLE_MAPS_API_KEY")
    ?: (project.findProperty("googleMapsApiKey") as String?)
    ?: ""

// Play Store upload key (kept secret, never in the repo): CI writes it from the UPLOAD_KEYSTORE_BASE64
// secret to a file and passes the path and passwords here. Without it, no Play bundle is signed.
val uploadKeystore: String = System.getenv("UPLOAD_KEYSTORE_FILE") ?: ""

// CI sets BUILD_NUMBER so every APK installs as an update over the previous one.
val buildNumber: Int = System.getenv("BUILD_NUMBER")?.toIntOrNull() ?: 1

android {
    namespace = "com.ridecomm.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.ridecomm.app"
        minSdk = 26
        // Google Play asks new apps and updates to target Android 16 (API 36) from 31 August 2026.
        targetSdk = 36
        versionCode = buildNumber
        versionName = "0.1.$buildNumber"
        buildConfigField("String", "DEFAULT_TOKEN_SERVER_ID", "\"$tokenServerId\"")
        buildConfigField("String", "DEFAULT_RIDE_SERVER_URL", "\"$rideServerUrl\"")
        buildConfigField("boolean", "HAS_GOOGLE_MAPS", mapsApiKey.isNotBlank().toString())
        manifestPlaceholders["mapsApiKey"] = mapsApiKey
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
        if (uploadKeystore.isNotBlank()) {
            create("upload") {
                storeFile = file(uploadKeystore)
                storePassword = System.getenv("UPLOAD_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("UPLOAD_KEY_ALIAS")
                keyPassword = System.getenv("UPLOAD_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("ridecomm")
        }
        release {
            // R8 drops unused code and resources: a much smaller APK that also starts faster.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("ridecomm")
        }
        // For the Play Store: the release app signed with the private upload key (Google re-signs it
        // with the Play key). Built as an .aab with bundlePlay. Phones with the sideloaded APK must
        // uninstall it before installing from Play (different signature).
        create("play") {
            initWith(getByName("release"))
            matchingFallbacks += "release"
            signingConfig = signingConfigs.findByName("upload")
        }
        // Same app without R8, published as a fallback in case shrinking breaks something on a phone.
        create("unshrunk") {
            initWith(getByName("release"))
            isMinifyEnabled = false
            isShrinkResources = false
            matchingFallbacks += "release"
        }
    }

    // One APK per CPU type, each with only its own WebRTC native libraries. Real phones only.
    splits {
        abi {
            // Play bundles split by CPU type themselves (and can't be built with splits on).
            isEnable = gradle.startParameter.taskNames.none { it.contains("bundle", ignoreCase = true) }
            reset()
            include("arm64-v8a", "armeabi-v7a")
            isUniversalApk = false
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
        // Robolectric downloads Android itself while the tests run; Maven Central sometimes refuses
        // CI (build #42 failed that way), so fetch it from Google's copy of Maven Central instead.
        unitTests.all {
            it.systemProperty("robolectric.dependency.repo.url", "https://maven-central.storage-download.googleapis.com/maven2/")
            it.systemProperty("robolectric.dependency.repo.id", "google-maven-central")
        }
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
    // Group map: OpenStreetMap-based, no API key or Google services needed.
    implementation("org.osmdroid:osmdroid-android:6.1.20")
    // Group map on Google Maps when a key is set (free on Android; the map itself comes from Play services).
    implementation("com.google.android.gms:play-services-maps:20.0.0")

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

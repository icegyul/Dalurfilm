plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.serialization") version "1.9.24"
}

android {
    namespace = "com.dalur.film"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.dalur.film"
        minSdk = 28
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0-rc1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables { useSupportLibrary = true }

        ndk {
            // GPUImage Plus + MapLibre ship these ABIs; keep 64-bit first for Play.
            abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64")
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            isDebuggable = true
        }
        release {
            isMinifyEnabled = false
            isShrinkResources = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // Locally signed RC. Play upload requires the real upload key;
            // see release/STORE_READINESS.md (BLOCKED_EXTERNAL_DEPENDENCY).
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }

    buildFeatures {
        compose = true
        buildConfig = true
    }
    composeOptions { kotlinCompilerExtensionVersion = "1.5.14" }

    packaging {
        resources {
            excludes += setOf(
                "/META-INF/{AL2.0,LGPL2.1}",
                "META-INF/DEPENDENCIES",
                "META-INF/LICENSE",
                "META-INF/LICENSE.txt",
                "META-INF/NOTICE",
                "META-INF/NOTICE.txt"
            )
            // MapLibre + GPUImage native libs: keep first occurrence.
            pickFirsts += setOf("lib/**/libmaplibre*.so", "lib/**/libc++_shared.so")
        }
        jniLibs { useLegacyPackaging = false }
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            isReturnDefaultValues = true
        }
    }
    lint {
        abortOnError = false
        warningsAsErrors = false
        checkReleaseBuilds = false
    }

    sourceSets {
        getByName("main") {
            // Single source of truth for DALUR-original LUTs + recipes.
            assets.srcDirs("../shared/src/main/assets")
        }
    }
}

dependencies {
    val camerax = "1.3.4"
    val composeBom = platform("androidx.compose:compose-bom:2024.06.00")
    val media3 = "1.4.1"

    // ---- AndroidX core ----
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.6")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.datastore:datastore-preferences:1.1.1")
    implementation("androidx.work:work-runtime-ktx:2.9.0")
    implementation("com.google.android.gms:play-services-location:21.3.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    // ---- Compose ----
    implementation(composeBom)
    androidTestImplementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.navigation:navigation-compose:2.7.7")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    // ---- Camera (CameraX standard flows + Camera2 interop for PRO) ----
    // Reference: android/camera-samples @ 2f10e46 (camerax-video, camerax-hdrvideo,
    // camera2-manualcontrols, camera2-raw, camerax-effects). DALUR UI is original.
    implementation("androidx.camera:camera-core:$camerax")
    implementation("androidx.camera:camera-camera2:$camerax")
    implementation("androidx.camera:camera-lifecycle:$camerax")
    implementation("androidx.camera:camera-video:$camerax")
    implementation("androidx.camera:camera-view:$camerax")
    implementation("androidx.camera:camera-extensions:$camerax")

    // ---- Media (playback + journey export; MediaCodec/MediaMuxer under the hood) ----
    implementation("androidx.media3:media3-exoplayer:$media3")
    implementation("androidx.media3:media3-ui:$media3")
    implementation("androidx.media3:media3-muxer:$media3")
    implementation("androidx.media3:media3-transformer:$media3")
    implementation("androidx.media3:media3-effect:$media3")

    // ---- GPU film pipeline (filter/effect only; camera control stays in CameraX/2) ----
    // Reference: wysaid/android-gpuimage-plus @ 32bf703, MIT. 16k variant is
    // required for Android 15+ 16KB page-size compliance (Play target API 36).
    implementation("org.wysaid:gpuimage-plus:3.2.0-16k")

    // ---- Map (route overlay, markers, camera animation) ----
    // Reference: maplibre/maplibre-native @ 9ee6f1c, BSD-2-Clause. Tile style is
    // configurable; no unlicensed endpoint is hard-coded (see MapScreen + docs).
    implementation("org.maplibre.gl:android-sdk:11.12.2")

    // ---- Poster images (TMDB file URLs baked into seeds; attribution in UI) ----
    implementation("io.coil-kt:coil-compose:2.6.0")

    // ---- On-device subject detection (face size → shot scale; no server) ----
    // Reference: googlesamples/mlkit @ master, Apache-2.0. Bundled model, works
    // offline. If init fails on device, Guide runs manual-only (NOT_SUPPORTED).
    implementation("com.google.mlkit:face-detection:16.1.7")

    // Project modules
    implementation(project(":shared"))

    // ---- Tests ----
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
    testImplementation("androidx.test:core:1.6.1")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
}

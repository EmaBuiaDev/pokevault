import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.google.services)
    alias(libs.plugins.ksp)
}

val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) load(file.inputStream())
}

android {
    namespace = "com.emabuia.pokevault"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.emabuia.pokevault"
        minSdk = 26
        targetSdk = 36
        versionCode = 20
        versionName = "2.0.12"

        buildConfigField("String", "POKETCG_API_KEY", "\"${localProperties.getProperty("POKETCG_API_KEY", "")}\"")
        buildConfigField("String", "POKEWALLET_API_KEY", "\"${localProperties.getProperty("POKEWALLET_API_KEY", "")}\"")
        buildConfigField("Boolean", "POKEWALLET_PROXY_ENABLED", "${localProperties.getProperty("POKEWALLET_PROXY_ENABLED", "false")}")
        buildConfigField("String", "POKEWALLET_PROXY_URL", "\"${localProperties.getProperty("POKEWALLET_PROXY_URL", "")}\"")
    }

    signingConfigs {
        create("release") {
            val ksFile = rootProject.file(localProperties.getProperty("RELEASE_STORE_FILE", "release.keystore"))
            if (ksFile.exists()) {
                storeFile = ksFile
                storePassword = localProperties.getProperty("RELEASE_STORE_PASSWORD", "")
                keyAlias = localProperties.getProperty("RELEASE_KEY_ALIAS", "")
                keyPassword = localProperties.getProperty("RELEASE_KEY_PASSWORD", "")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            val releaseSigningConfig = signingConfigs.findByName("release")
            if (releaseSigningConfig?.storeFile?.exists() == true) {
                signingConfig = releaseSigningConfig
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    ksp {
        arg("room.schemaLocation", "$projectDir/schemas")
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    // Non comprimere modelli TFLite (memory-mapping richiede file non compresso)
    androidResources {
        noCompress += "tflite"
    }

    // Escludi le architetture x86/x86_64 e non tentare strip su librerie terze parti
    // che arrivano gia' non strip-pabili (evita warning ripetuti in fase assemble).
    packaging {
        jniLibs {
            useLegacyPackaging = false
            excludes += setOf("lib/x86/**", "lib/x86_64/**")
            keepDebugSymbols += setOf(
                "**/libandroidx.graphics.path.so",
                "**/libimage_processing_util_jni.so",
                "**/libmlkit_google_ocr_pipeline.so",
                "**/libsurface_util_jni.so",
                "**/libtensorflowlite_gpu_jni.so",
                "**/libtensorflowlite_jni.so"
            )
        }
    }
}

dependencies {
    // ── Core Android ──
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)

    // ── Compose BOM (gestisce tutte le versioni Compose) ──
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)

    // ── Firebase ──
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth.ktx)
    implementation(libs.firebase.firestore.ktx)
    implementation(libs.firebase.storage.ktx)

    // ── Navigation Compose ──
    implementation(libs.androidx.navigation.compose)

    // ── ViewModel Compose ──
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)

    // ── Material Icons Extended ──
    implementation(libs.androidx.compose.material.icons.extended)

    // ── Coil (caricamento immagini) ──
    implementation(libs.coil.compose)
    implementation(libs.coil.gif)

    // ── Google Sign-In (Credential Manager) ──
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services.auth)
    implementation(libs.googleid)

    // ── Debug ──
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    // Networking (PokéTCG API)
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.gson)
    implementation(libs.okhttp.logging.interceptor)

    // ── CameraX ──
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)

    // ── ML Kit Text Recognition ──
    implementation(libs.mlkit.text.recognition)

    // ── TensorFlow Lite (PaddleOCR engine) ──
    implementation(libs.tensorflow.lite)
    implementation(libs.tensorflow.lite.gpu)

    // ── Accompanist Permissions ──
    implementation(libs.accompanist.permissions)

    // ── Splash Screen ──
    implementation(libs.androidx.core.splashscreen)

    // ── Google Play Billing ──
    implementation(libs.billing.ktx)

    // ── Logging ──
    implementation(libs.timber)

    // ── Room (local cache) ──
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // ── WorkManager (background sync) ──
    implementation(libs.androidx.work.runtime.ktx)

    // ── Test Dependencies ──
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}

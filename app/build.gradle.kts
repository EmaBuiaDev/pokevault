import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.google.services)
    alias(libs.plugins.ksp)
    jacoco
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
        versionCode = 36
        versionName = "3.1.3"

        buildConfigField("String", "POKEWALLET_API_KEY", "\"${localProperties.getProperty("POKEWALLET_API_KEY", "")}\"")
        buildConfigField("Boolean", "POKEWALLET_PROXY_ENABLED", "${localProperties.getProperty("POKEWALLET_PROXY_ENABLED", "false")}")
        buildConfigField("String", "POKEWALLET_PROXY_URL", "\"${localProperties.getProperty("POKEWALLET_PROXY_URL", "")}\"")
        buildConfigField("String", "ITALIAN_CATALOG_URL", "\"${localProperties.getProperty("ITALIAN_CATALOG_URL", "")}\"")
    }

    // ── Ambienti ────────────────────────────────────────────────────────────
    //
    // Due progetti Firebase distinti, cosi' provare l'app sul telefono non
    // significa piu' scrivere nei dati veri. Lo staging ha un applicationId
    // suo, quindi le due app convivono sullo stesso dispositivo e si passa
    // dall'una all'altra senza disinstallare niente.
    //
    // Cosa NON e' separato, e perche':
    //   - il Worker Cloudflare resta condiviso: catalogo, prezzi e immagini
    //     sono dati di riferimento, non dati dell'utente, e duplicarli
    //     vorrebbe dire tenerli allineati a mano;
    //   - le rotte autenticate del Worker (/billing, /gift) rifiutano pero'
    //     gli utenti di staging: verificano l'ID token contro un solo
    //     FIREBASE_PROJECT_ID, quello di produzione (src/billing.ts);
    //   - gli acquisti Play non funzionano in staging, perche' Play riconosce
    //     solo il package pubblicato. Gli abbonamenti si provano in
    //     produzione con i license tester, come prima.
    flavorDimensions += "environment"
    productFlavors {
        create("prod") {
            dimension = "environment"
            // Nessun suffisso, e va lasciato cosi': l'app su Play deve restare
            // esattamente com.emabuia.pokevault, altrimenti saltano insieme
            // billing, firma di Play e Google Sign-In.
        }
        create("staging") {
            dimension = "environment"
            applicationIdSuffix = ".staging"
            versionNameSuffix = "-staging"
        }
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
        debug {
            // Senza questo testDebugUnitTest non produce alcun file .exec,
            // quindi il report Jacoco risulterebbe vuoto.
            enableUnitTestCoverage = true
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            // Niente `ndk { debugSymbolLevel = ... }` per l'avviso di Play sui
            // simboli di debug nativi: non c'e' niente da estrarre. Tutte le
            // .so dell'APK arrivano da AAR di Google (OCR di ML Kit, JNI di
            // CameraX, graphics.path) e sono gia' spogliate all'origine --
            // hanno .dynsym ma non .symtab ne' .debug_info. Codice nativo
            // nostro non ne esiste. Attivarlo produce solo una cartella vuota.
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            val releaseSigningConfig = signingConfigs.findByName("release")
            if (releaseSigningConfig?.storeFile?.exists() == true) {
                signingConfig = releaseSigningConfig
            }
            // Never bake the real PokeWallet API key into a release APK -- it would sit in
            // plaintext, extractable by decompiling the app. The Cloudflare Worker proxy
            // injects its own server-side copy of this key (createUpstreamHeaders in
            // pokevault-proxy-worker/src/index.ts) and is what release builds must use;
            // forcing the proxy on here too means a release build can never end up in the
            // one config (proxy off + key present) that would send the key over the wire.
            // PokeWalletRepository already fails closed (IllegalStateException) rather than
            // falling back to a direct call if POKEWALLET_PROXY_URL is left unconfigured.
            buildConfigField("String", "POKEWALLET_API_KEY", "\"\"")
            buildConfigField("Boolean", "POKEWALLET_PROXY_ENABLED", "true")
        }

        // Serve solo a provare la release sul telefono, non si pubblica.
        //
        // Stesse regole R8, stesso shrinking e stessi BuildConfig della
        // release, ma firmata con la chiave di debug. La ragione e' il login:
        // Play rifirma l'app con la propria chiave, quindi in
        // google-services.json sono registrati il certificato di debug e
        // quello di Play, non quello di upload. Un APK firmato con la chiave
        // di upload non supera Google Sign-In, e siccome l'app parte dalla
        // schermata di accesso non si arriverebbe a provare nient'altro.
        create("releaseSmoke") {
            initWith(getByName("release"))
            signingConfig = signingConfigs.getByName("debug")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    testOptions {
        unitTests.isIncludeAndroidResources = true
        unitTests.isReturnDefaultValues = true
    }
    ksp {
        arg("room.schemaLocation", "$projectDir/schemas")
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    // Due scelte consapevoli, entrambe con un avviso di Play attaccato.
    //
    // 1. x86/x86_64 esclusi. Gli AAR di Google (ML Kit, CameraX, graphics-path)
    //    spedirebbero anche quelle architetture, quindi l'esclusione e' nostra:
    //    costa il supporto a Chromebook e tablet Intel. Deciso di tenerla.
    //    Play lo ripete a ogni caricamento come "non supporti piu' N
    //    dispositivi": e' atteso, ed e' cosi' dal versionCode 19.
    //    Per recuperarli basta togliere la riga `excludes` qui sotto: con
    //    l'AAB, chi e' su ARM scarica comunque solo il proprio split.
    //
    // 2. keepDebugSymbols. Non conserva nessun simbolo: quelle .so arrivano
    //    gia' spogliate da Google (hanno .dynsym ma non .symtab). Serve solo a
    //    evitare che AGP tenti lo strip e riempia la build di warning, visto
    //    che l'NDK non e' installato. Per lo stesso motivo l'avviso di Play sui
    //    simboli di debug nativi non e' soddisfabile: non c'e' niente da dargli.
    packaging {
        resources {
            // I jar di JUnit 5 arrivano transitivamente nell'APK di test e ognuno
            // porta il proprio META-INF/LICENSE.md: senza escluderli il task
            // mergeDebugAndroidTestJavaResource non riesce a impacchettare nulla
            // e i test strumentati non partono.
            excludes += setOf(
                "META-INF/LICENSE.md",
                "META-INF/LICENSE-notice.md",
                "META-INF/NOTICE.md"
            )
        }
        jniLibs {
            useLegacyPackaging = false
            excludes += setOf("lib/x86/**", "lib/x86_64/**")
            keepDebugSymbols += setOf(
                "**/libandroidx.graphics.path.so",
                "**/libimage_processing_util_jni.so",
                "**/libmlkit_google_ocr_pipeline.so",
                "**/libsurface_util_jni.so",
                "**/liblitert_gpu_jni.so",
                "**/liblitert_jni.so"
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

    // ── Accompanist Permissions ──
    implementation(libs.accompanist.permissions)

    // ── Splash Screen ──

    // ── Google Play Billing ──
    implementation(libs.billing)

    // ── Logging ──
    implementation(libs.timber)

    // ── Room (local cache) ──
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // ── WorkManager (background sync) ──
    implementation(libs.androidx.work.runtime.ktx)

    // ── Test Dependencies ──
    // Unit Tests
    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.test.core.ktx)
    testImplementation(libs.androidx.lifecycle.runtime.ktx)

    // Instrumented Tests (Android)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.mockk.android)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.kotlinx.coroutines.test)
}

// Configurazione Jacoco per Code Coverage
jacoco {
    toolVersion = "0.8.11"
}

/**
 * Report di coverage per la variante prodDebug.
 *
 * Il plugin jacoco era applicato ma nessun task JacocoReport era registrato:
 * AGP non li crea da solo per variante. Di conseguenza
 * `jacocoTestDebugUnitTestReport`, invocato da android-advanced-tests.yml,
 * non esisteva e quel workflow falliva prima ancora di eseguire i test.
 *
 * Dall'introduzione dei flavor `prod`/`staging` la variante si chiama
 * prodDebug: `testDebugUnitTest` non esiste piu' (AGP genera un task per
 * combinazione flavor+buildType) e anche le cartelle delle classi hanno il
 * flavor nel nome. Si misura prod perche' e' la variante che viene
 * pubblicata; il codice e' lo stesso, cambia solo a quale Firebase punta.
 */
tasks.register<JacocoReport>("jacocoTestProdDebugUnitTestReport") {
    dependsOn("testProdDebugUnitTest")
    group = "verification"
    description = "Genera il report Jacoco per i test unitari della variante prodDebug."

    reports {
        xml.required.set(true)
        html.required.set(true)
    }

    // Codice generato: escluso, altrimenti falsa la percentuale.
    val excludes = listOf(
        "**/R.class", "**/R$*.class", "**/BuildConfig.*", "**/Manifest*.*",
        "**/*Test*.*", "android/**/*.*",
        "**/*_Factory.*", "**/*_MembersInjector.*",
        "**/*Composable*.class",
        "**/databinding/**", "**/generated/**",
        "**/*_Impl*.*"          // DAO generati da Room
    )

    classDirectories.setFrom(
        files(
            fileTree("${layout.buildDirectory.get()}/tmp/kotlin-classes/prodDebug") { exclude(excludes) },
            fileTree("${layout.buildDirectory.get()}/intermediates/javac/prodDebug/classes") { exclude(excludes) }
        )
    )
    sourceDirectories.setFrom(files("$projectDir/src/main/java"))

    // Un file preciso, non un fileTree su tutta la build dir. Quello pescava
    // qualunque .exec ci fosse rimasto -- compresi quelli di varianti diverse,
    // che dall'arrivo dei flavor esistono davvero -- e mescolava coverage di
    // build che non c'entravano niente. Gradle lo segnalava anche come
    // dipendenza implicita dagli output di altri task, e il report falliva
    // quando girava nella stessa invocazione di una compilazione release.
    executionData.setFrom(
        files(
            layout.buildDirectory.file(
                "outputs/unit_test_code_coverage/prodDebugUnitTest/testProdDebugUnitTest.exec"
            )
        )
    )
}

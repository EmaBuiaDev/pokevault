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
        versionCode = 38
        versionName = "3.1.5"

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

            // TradeRadar e' in sviluppo: in prod non esiste, ne' la tile ne'
            // la rotta. TradeRadarFlagTest fallisce se qualcuno lo accende qui
            // prima del lancio.
            buildConfigField("Boolean", "TRADE_ENABLED", "false")
            buildConfigField("String", "TRADE_API_URL", "\"\"")
        }
        create("staging") {
            dimension = "environment"
            applicationIdSuffix = ".staging"
            versionNameSuffix = "-staging"

            // Worker a parte (pokevault-trade-staging, vedi wrangler.toml):
            // verifica i token del progetto Firebase di staging, quindi da qui
            // passano solo gli account di test.
            buildConfigField("Boolean", "TRADE_ENABLED", "true")
            buildConfigField("String", "TRADE_API_URL", "\"https://pokevault-trade-staging.pokevault-emanu.workers.dev\"")
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
    // Notifiche di TradeRadar: SOLO nello staging. In prod la libreria
    // aggiungerebbe il permesso per le notifiche e un ricevitore che non
    // servono (vedi src/prod/.../TradePush.kt). Al lancio torna implementation.
    "stagingImplementation"(libs.firebase.messaging.ktx)

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

    // Niente test strumentati (androidTest): i due che c'erano non giravano,
    // mancava il testInstrumentationRunner. Il test di SetDao sta ora con gli
    // unitari (Robolectric, come CardDaoTest); l'altro era un segnaposto.
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
 * Quel workflow e' stato cancellato il 28/09/2026 (era disattivato da maggio):
 * il report resta per chi lo vuole lanciare in locale.
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

/**
 * Le classi lette per reflection devono uscire da R8 col loro nome.
 *
 * Gson e Firestore riempiono gli oggetti leggendo i nomi dei campi. Una classe
 * che R8 rinomina o pota esce da una release verde e poi, sul telefono, arriva
 * con i campi a null o sparisce: e' successo ai codici regalo (16/09/2026) e
 * alle mani salvate dell'Hand Simulator (trovato il 28/09/2026). Nessun
 * compilatore e nessun test sul sorgente se ne accorge: lo dice solo il
 * mapping.txt, ed e' quello che questo task legge.
 *
 * Le classi da controllare si ricavano dal sorgente: i tipi dentro
 * `TypeToken<...>`, quelli passati a `fromJson(..., X::class.java)` e a
 * `toObject(X::class.java)`, e poi, di ognuno, i tipi dei campi del
 * costruttore, che il lettore riempie allo stesso modo. Per ognuna il mapping
 * deve avere la riga `nome -> nome:`, cioe' tenuta e non rinominata.
 */
tasks.register("verifyR8Reflection") {
    group = "verification"
    description = "Controlla sul mapping di R8 che le classi lette per reflection non siano rinominate o potate."
    dependsOn("minifyProdReleaseWithR8")

    val sourceRoot = file("src/main/java")
    val mappingFile = layout.buildDirectory.file("outputs/mapping/prodRelease/mapping.txt")
    inputs.dir(sourceRoot)
    inputs.file(mappingFile)

    doLast {
        val notDomain = setOf(
            "List", "MutableList", "Set", "MutableSet", "Map", "MutableMap", "Array",
            "String", "Int", "Long", "Double", "Float", "Boolean", "Any", "Unit", "Timestamp"
        )
        val typeNames = Regex("""\b([A-Z][A-Za-z0-9_]*)\b""")

        // Nome semplice -> nomi completi (con $ per le classi annidate), e il
        // testo dei parametri del costruttore primario di ogni classe.
        val fqcnBySimple = mutableMapOf<String, MutableList<String>>()
        val ctorParamsByFqcn = mutableMapOf<String, String>()
        val usedNames = mutableSetOf<String>()
        // class, object e interface: anche un object o un'interfaccia puo'
        // contenere classi annidate (GiftCodeRepository e' un object).
        val declaration = Regex("""^(\s*)(?:[a-z]+\s+)*(?:class|object|interface)\s+([A-Z][A-Za-z0-9_]*)""")

        sourceRoot.walkTopDown().filter { it.extension == "kt" }.forEach { file ->
            val text = file.readText()
            val pkg = Regex("""^package\s+([\w.]+)""", RegexOption.MULTILINE).find(text)?.groupValues?.get(1) ?: ""
            // Pila delle dichiarazioni aperte, per indentazione: il nome
            // completo di una classe annidata e' Esterna$Interna nel bytecode.
            val enclosing = ArrayDeque<Pair<Int, String>>()
            var offset = 0
            text.lines().forEach { line ->
                declaration.find(line)?.let { m ->
                    val indent = m.groupValues[1].length
                    val name = m.groupValues[2]
                    while (enclosing.isNotEmpty() && enclosing.last().first >= indent) enclosing.removeLast()
                    val fqcn = "$pkg." + (enclosing.map { it.second } + name).joinToString("$")
                    enclosing.addLast(indent to name)
                    fqcnBySimple.getOrPut(name) { mutableListOf() } += fqcn
                    // Parametri del costruttore: dalla prima "(" dopo il nome
                    // alla sua ")" corrispondente, se la classe ne ha una.
                    val start = offset + m.range.last + 1
                    val open = text.indexOf('(', start)
                    val brace = text.indexOf('{', start).let { if (it < 0) Int.MAX_VALUE else it }
                    val eol = text.indexOf('\n', start).let { if (it < 0) text.length else it }
                    if (open in start..minOf(brace, eol)) {
                        var depth = 0
                        var i = open
                        while (i < text.length) {
                            if (text[i] == '(') depth++
                            if (text[i] == ')') { depth--; if (depth == 0) break }
                            i++
                        }
                        ctorParamsByFqcn[fqcn] = text.substring(open + 1, i.coerceAtMost(text.length))
                    }
                }
                offset += line.length + 1
            }

            Regex("""TypeToken<(.+?)>\s*\(\)""").findAll(text).forEach { t ->
                typeNames.findAll(t.groupValues[1]).forEach { usedNames += it.groupValues[1] }
            }
            Regex("""fromJson\s*\([^;]*?,\s*([A-Z][A-Za-z0-9_]*)::class\.java""").findAll(text).forEach { usedNames += it.groupValues[1] }
            Regex("""toObjects?\s*\(\s*([A-Z][A-Za-z0-9_]*)::class\.java""").findAll(text).forEach { usedNames += it.groupValues[1] }
        }

        // Chiusura sui tipi dei campi: una classe letta per reflection porta
        // con se' quelle dei suoi campi.
        val toCheck = sortedSetOf<String>()
        val queue = ArrayDeque(usedNames.filter { it !in notDomain })
        while (queue.isNotEmpty()) {
            val simple = queue.removeFirst()
            val fqcns = fqcnBySimple[simple] ?: continue // tipo di libreria
            fqcns.forEach { fqcn ->
                if (toCheck.add(fqcn)) {
                    val params = ctorParamsByFqcn[fqcn] ?: return@forEach
                    Regex("""va[lr]\s+\w+\s*:\s*([^=,\n]+)""").findAll(params).forEach { p ->
                        typeNames.findAll(p.groupValues[1]).map { it.groupValues[1] }
                            .filter { it !in notDomain }
                            .forEach { queue.addLast(it) }
                    }
                }
            }
        }

        val mapping = mappingFile.get().asFile
        check(mapping.exists()) { "Manca ${mapping.path}: il task dipende da minifyProdReleaseWithR8" }
        val classLines = mapping.useLines { lines ->
            lines.filter { !it.startsWith(" ") && !it.startsWith("#") && it.endsWith(":") }
                .associate { it.substringBefore(" -> ") to it.substringAfter(" -> ").removeSuffix(":") }
        }

        val problems = toCheck.mapNotNull { fqcn ->
            when (val mapped = classLines[fqcn]) {
                null -> "$fqcn: potata da R8, non c'e' nel mapping"
                fqcn -> null
                else -> "$fqcn: rinominata in $mapped"
            }
        }
        if (problems.isNotEmpty()) {
            throw GradleException(
                "Classi lette per reflection che R8 ha toccato (${problems.size} su ${toCheck.size}). " +
                    "In release arriverebbero con i campi a null. Aggiungere un -keep in app/proguard-rules.pro:\n" +
                    problems.joinToString("\n") { "  - $it" }
            )
        }
        logger.lifecycle("verifyR8Reflection: ${toCheck.size} classi lette per reflection, tutte tenute col loro nome.")
    }
}

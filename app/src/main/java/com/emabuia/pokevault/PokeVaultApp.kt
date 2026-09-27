package com.emabuia.pokevault

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import coil.request.CachePolicy
import coil.decode.DataSource
import coil.request.ImageResult
import coil.request.SuccessResult
import coil.transition.CrossfadeTransition
import coil.transition.Transition
import coil.transition.TransitionTarget
import coil.util.DebugLogger
import com.emabuia.pokevault.BuildConfig
import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.PersistentCacheSettings
import com.emabuia.pokevault.data.billing.PremiumManager
import com.emabuia.pokevault.data.remote.LimitlessLocalCache
import com.emabuia.pokevault.data.remote.RepositoryProvider
import com.emabuia.pokevault.ui.theme.ThemePreference
import com.emabuia.pokevault.util.AppLocale
import com.emabuia.pokevault.workers.CacheCleanupWorker
import com.emabuia.pokevault.workers.CardsSyncWorker
import com.emabuia.pokevault.workers.SetsSyncWorker
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import okhttp3.Dispatcher
import okhttp3.OkHttpClient
import timber.log.Timber
import java.util.concurrent.TimeUnit

class PokeVaultApp : Application(), ImageLoaderFactory {

    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) Timber.plant(Timber.DebugTree())
        FirebaseApp.initializeApp(this)

        // Abilita la cache locale persistente di Firestore: in questo modo tutte
        // le operazioni di lettura/scrittura colpiscono prima il database locale
        // (istantanee) e la sincronizzazione con il server avviene in background.
        // Gli snapshot listener emettono immediatamente gli aggiornamenti dalla
        // cache con hasPendingWrites=true, così aggiunte ed eliminazioni delle
        // carte vengono mostrate all'istante nella UI.
        FirebaseFirestore.getInstance().firestoreSettings =
            FirebaseFirestoreSettings.Builder()
                .setLocalCacheSettings(
                    PersistentCacheSettings.newBuilder()
                        .setSizeBytes(FirebaseFirestoreSettings.CACHE_SIZE_UNLIMITED)
                        .build()
                )
                .build()

        AppLocale.init(this)
        ThemePreference.init(this)
        PremiumManager.init(this)

        // Initialize Room database and shared repositories
        RepositoryProvider.init(this)

        // Cache su disco dei dati Limitless: senza, ogni riavvio ricomincia a
        // spendere la finestra di 50 richieste ogni 5 minuti dell'API.
        LimitlessLocalCache.init(this)

        // One-time cleanup: remove old SharedPreferences cache (migrated to Room)
        migrateFromSharedPreferences()

        // Schedule background sync workers
        scheduleWorkers()
    }

    override fun newImageLoader(): ImageLoader {
        val proxyEnabled = BuildConfig.POKEWALLET_PROXY_ENABLED && BuildConfig.POKEWALLET_PROXY_URL.isNotBlank()
        val client = OkHttpClient.Builder()
            // OkHttp di suo tiene 5 richieste per host: la griglia di un set
            // chiede 15-20 miniature insieme, tutte allo stesso worker, e le
            // riceveva a ondate da 5. Il numero di chiamate non cambia,
            // arrivano solo piu' vicine. Vale solo per le immagini: il client
            // delle API ha i suoi limiti (LimitlessTcgRepository ne tiene 3).
            .dispatcher(Dispatcher().apply { maxRequestsPerHost = IMAGE_MAX_REQUESTS_PER_HOST })
            .addInterceptor { chain ->
                val requestBuilder = chain.request().newBuilder()
                if (
                    !proxyEnabled &&
                    chain.request().url.host.equals("api.pokewallet.io", ignoreCase = true) &&
                    BuildConfig.POKEWALLET_API_KEY.isNotBlank()
                ) {
                    requestBuilder.addHeader("X-API-Key", BuildConfig.POKEWALLET_API_KEY)
                }
                chain.proceed(requestBuilder.build())
            }
            .build()

        return ImageLoader.Builder(this)
            .okHttpClient(client)
            // Una dissolvenza breve invece dello scatto secco: nella griglia di
            // un'espansione le immagini arrivano una alla volta, e senza questa
            // ognuna compare di colpo sopra il proprio fondo -- si legge come
            // lentezza anche quando la rete e' veloce. Sugli hit in cache di
            // memoria Coil la salta, quindi lo scorrimento resta immediato.
            //
            // Ma solo lei: un'immagine letta dalla cache su disco -- ogni set
            // riaperto dopo aver chiuso l'app -- e' pronta in pochi ms e poi
            // impiegava 180 ms a comparire. La dissolvenza ora la fa solo chi
            // arriva davvero dalla rete.
            .transitionFactory(NetworkOnlyCrossfade(IMAGE_CROSSFADE_MS))
            .diskCachePolicy(CachePolicy.ENABLED)
            .memoryCachePolicy(CachePolicy.ENABLED)
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.40)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .maxSizeBytes(256L * 1024 * 1024) // 256 MB
                    .build()
            }
            .apply {
                if (BuildConfig.DEBUG) {
                    logger(DebugLogger())
                }
            }
            .build()
    }

    private fun migrateFromSharedPreferences() {
        val migrationPrefs = getSharedPreferences("pokevault_migration", MODE_PRIVATE)
        if (migrationPrefs.getBoolean("room_migrated", false)) return
        getSharedPreferences("pokevault_cache", MODE_PRIVATE).edit().clear().apply()
        migrationPrefs.edit().putBoolean("room_migrated", true).apply()
    }

    private fun scheduleWorkers() {
        val wm = WorkManager.getInstance(this)
        val networkConstraint = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val networkAndBattery = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .setRequiresBatteryNotLow(true)
            .build()

        // Sets sync: every 24h
        wm.enqueueUniquePeriodicWork(
            "sets_sync",
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<SetsSyncWorker>(24, TimeUnit.HOURS)
                .setConstraints(networkConstraint)
                .build()
        )

        // Cards sync: every 7 days (visited expansions only)
        wm.enqueueUniquePeriodicWork(
            "cards_sync",
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<CardsSyncWorker>(7, TimeUnit.DAYS)
                .setConstraints(networkAndBattery)
                .build()
        )

        // Disable autonomous background price refresh to avoid token consumption
        // outside user-visible cards in Set Detail.
        wm.cancelUniqueWork("price_sync")

        // Cache cleanup: every 24h
        wm.enqueueUniquePeriodicWork(
            "cache_cleanup",
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<CacheCleanupWorker>(24, TimeUnit.HOURS)
                .build()
        )
    }
}
private const val IMAGE_CROSSFADE_MS = 180
private const val IMAGE_MAX_REQUESTS_PER_HOST = 12

/**
 * La dissolvenza di Coil, ma solo per le immagini arrivate dalla rete.
 *
 * Coil la salta gia' da se' per la cache in memoria; qui la si salta anche
 * per quella su disco. Gli errori la tengono, come prima.
 */
private class NetworkOnlyCrossfade(durationMillis: Int) : Transition.Factory {
    private val crossfade = CrossfadeTransition.Factory(durationMillis)

    override fun create(target: TransitionTarget, result: ImageResult): Transition {
        if (result is SuccessResult && result.dataSource != DataSource.NETWORK) {
            return Transition.Factory.NONE.create(target, result)
        }
        return crossfade.create(target, result)
    }
}

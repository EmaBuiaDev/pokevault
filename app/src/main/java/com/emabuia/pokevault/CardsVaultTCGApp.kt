package com.emabuia.pokevault

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import coil.request.CachePolicy
import coil.util.DebugLogger
import com.emabuia.pokevault.BuildConfig
import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.PersistentCacheSettings
import com.emabuia.pokevault.data.billing.PremiumManager
import com.emabuia.pokevault.data.remote.RepositoryProvider
import com.emabuia.pokevault.util.AppLocale
import com.emabuia.pokevault.workers.CacheCleanupWorker
import com.emabuia.pokevault.workers.CardsSyncWorker
import com.emabuia.pokevault.workers.PriceSyncWorker
import com.emabuia.pokevault.workers.SetsSyncWorker
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import okhttp3.OkHttpClient
import timber.log.Timber
import java.util.concurrent.TimeUnit

class CardsVaultTCGApp : Application(), ImageLoaderFactory {

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
        PremiumManager.init(this)

        // Initialize Room database and shared repositories
        RepositoryProvider.init(this)

        // One-time cleanup: remove old SharedPreferences cache (migrated to Room)
        migrateFromSharedPreferences()

        // Schedule background sync workers
        scheduleWorkers()
    }

    override fun newImageLoader(): ImageLoader {
        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val requestBuilder = chain.request().newBuilder()
                if (
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
            .diskCachePolicy(CachePolicy.ENABLED)
            .memoryCachePolicy(CachePolicy.ENABLED)
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.25)
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

        // Price sync: every 12h
        wm.enqueueUniquePeriodicWork(
            "price_sync",
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<PriceSyncWorker>(12, TimeUnit.HOURS)
                .setConstraints(networkConstraint)
                .build()
        )

        // Cache cleanup: every 24h
        wm.enqueueUniquePeriodicWork(
            "cache_cleanup",
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<CacheCleanupWorker>(24, TimeUnit.HOURS)
                .build()
        )
    }
}
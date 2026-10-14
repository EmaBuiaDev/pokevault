package com.emabuia.pokevault.data.remote

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import com.google.gson.JsonParser
import timber.log.Timber
import java.util.concurrent.TimeUnit

/**
 * Gli interruttori dell'app che si cambiano senza una release (14/10/2026).
 *
 * Per ora uno: il catalogo inglese. Il valore arriva dal Worker
 * (/v1/app-config) e si usa dall'avvio DOPO quello in cui e' arrivato: per
 * tutta una sessione resta quello letto all'avvio, cosi' l'inglese non
 * compare o sparisce a meta' mentre si usa l'app.
 *
 * Senza nessuna risposta mai ricevuta e' spento: l'app non accende l'inglese
 * per errore.
 */
object AppConfig {

    private const val PREFS_NAME = "app_config_v1"
    private const val KEY_ENGLISH_CATALOG = "english_catalog"

    /** Il valore di questa sessione, letto all'avvio da [init]. */
    @Volatile
    var englishCatalog: Boolean = false
        private set

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** All'avvio: il valore salvato vale da subito, quello nuovo dal prossimo avvio. */
    fun init(context: Context) {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        englishCatalog = runCatching { prefs.getBoolean(KEY_ENGLISH_CATALOG, false) }.getOrDefault(false)
        scope.launch {
            val fetched = runCatching { fetch() }
                .onFailure { Timber.w(it, "AppConfig: interruttori non letti, resta l'ultimo valore") }
                .getOrNull() ?: return@launch
            runCatching { prefs.edit().putBoolean(KEY_ENGLISH_CATALOG, fetched).apply() }
        }
    }

    private fun fetch(): Boolean? {
        val base = PokeVaultApiClient.imageBaseUrl.trim().trimEnd('/')
        if (base.isBlank()) return null
        val client = OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(8, TimeUnit.SECONDS)
            .build()
        client.newCall(Request.Builder().url("$base/v1/app-config").get().build()).execute().use { response ->
            if (!response.isSuccessful) return null
            return parseEnglishCatalog(response.body?.string())
        }
    }

    /**
     * La risposta del Worker letta con prudenza: solo un `true` vero accende.
     * Null se la risposta non si capisce (si tiene l'ultimo valore salvato).
     */
    internal fun parseEnglishCatalog(json: String?): Boolean? {
        if (json.isNullOrBlank()) return null
        return runCatching {
            val value = JsonParser.parseString(json).asJsonObject.get("englishCatalog") ?: return@runCatching null
            value.isJsonPrimitive && value.asJsonPrimitive.isBoolean && value.asBoolean
        }.getOrNull()
    }
}

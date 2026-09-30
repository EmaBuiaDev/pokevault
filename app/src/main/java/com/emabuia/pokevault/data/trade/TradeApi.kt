package com.emabuia.pokevault.data.trade

import com.emabuia.pokevault.BuildConfig
import com.emabuia.pokevault.data.billing.WorkerApi
import com.emabuia.pokevault.data.trade.dto.TradeMePayload
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import timber.log.Timber

/**
 * Chiamate a TradeRadar (vedi pokevault-proxy-worker/src/trade.ts).
 *
 * Il server non e' il Worker del catalogo ma uno suo, [BuildConfig.TRADE_API_URL]:
 * oggi esiste solo nel flavor staging. Client HTTP e ID token sono quelli di
 * [WorkerApi], cosi' timeout e autenticazione restano uno solo.
 *
 * I DTO stanno in [com.emabuia.pokevault.data.trade.dto], l'unico pezzo
 * tenuto da proguard-rules.pro: vedi il commento li'.
 */
object TradeApi {

    private val gson = Gson()

    /** true solo dove TradeRadar e' acceso e ha un server: oggi il flavor staging. */
    val isEnabled: Boolean
        get() = BuildConfig.TRADE_ENABLED && BuildConfig.TRADE_API_URL.isNotBlank()

    /** Esito del controllo di connessione della fase 0. */
    sealed class Check {
        /** Server raggiunto e utente riconosciuto. */
        data class Ok(val uid: String, val schemaVersion: Int?) : Check()

        /** Il server risponde ma non riconosce l'utente (401): token assente o di un altro progetto. */
        data object Unauthorized : Check()

        /** Rete, 5xx, risposta illeggibile. */
        data class Unavailable(val httpCode: Int?) : Check()
    }

    private fun endpoint(path: String): String? {
        val base = BuildConfig.TRADE_API_URL.trim().trimEnd('/')
        return if (base.isBlank()) null else "$base/$path"
    }

    /** GET /v1/trade/me: il server mi raggiunge e sa chi sono? */
    suspend fun checkConnection(): Check = withContext(Dispatchers.IO) {
        val url = endpoint("v1/trade/me") ?: return@withContext Check.Unavailable(null)
        val token = WorkerApi.idToken() ?: return@withContext Check.Unauthorized
        runCatching {
            val request = Request.Builder()
                .url(url)
                .header("Authorization", "Bearer $token")
                .get()
                .build()
            WorkerApi.httpClient.newCall(request).execute().use { response ->
                when {
                    response.code == 401 -> Check.Unauthorized
                    !response.isSuccessful -> Check.Unavailable(response.code)
                    else -> {
                        val payload = gson.fromJson(response.body?.string(), TradeMePayload::class.java)
                        val uid = payload?.uid
                        if (uid.isNullOrBlank()) Check.Unavailable(response.code)
                        else Check.Ok(uid, payload.schemaVersion)
                    }
                }
            }
        }.getOrElse {
            Timber.w(it, "TradeRadar: server non raggiunto")
            Check.Unavailable(null)
        }
    }
}

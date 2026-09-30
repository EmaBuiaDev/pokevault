package com.emabuia.pokevault.data.trade

import com.emabuia.pokevault.BuildConfig
import com.emabuia.pokevault.data.billing.WorkerApi
import com.emabuia.pokevault.data.trade.dto.TradeErrorPayload
import com.emabuia.pokevault.data.trade.dto.TradeHavesPayload
import com.emabuia.pokevault.data.trade.dto.TradeMatchesPayload
import com.emabuia.pokevault.data.trade.dto.TradeMePayload
import com.emabuia.pokevault.data.trade.dto.TradeOwnedRequest
import com.emabuia.pokevault.data.trade.dto.TradeProfilePayload
import com.emabuia.pokevault.data.trade.dto.TradeProfileRequest
import com.emabuia.pokevault.data.trade.dto.TradeWantsRequest
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
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

    /** Esito di una chiamata. */
    sealed class Result<out T> {
        data class Ok<T>(val value: T) : Result<T>()

        /** 404 "no_profile": l'utente non ha ancora attivato TradeRadar. */
        data object NoProfile : Result<Nothing>()

        /** 401: token assente o di un altro progetto Firebase. */
        data object Unauthorized : Result<Nothing>()

        /** Il server ha rifiutato i dati (400/413), con la sigla d'errore. */
        data class Rejected(val error: String?) : Result<Nothing>()

        /** Rete, 5xx, risposta illeggibile. */
        data class Unavailable(val httpCode: Int?) : Result<Nothing>()
    }

    private fun endpoint(path: String): String? {
        val base = BuildConfig.TRADE_API_URL.trim().trimEnd('/')
        return if (base.isBlank()) null else "$base/$path"
    }

    private suspend fun <T> call(method: String, path: String, body: Any?, parse: (String) -> T?): Result<T> =
        withContext(Dispatchers.IO) {
            val url = endpoint(path) ?: return@withContext Result.Unavailable(null)
            val token = WorkerApi.idToken() ?: return@withContext Result.Unauthorized
            runCatching {
                val requestBody = body?.let { gson.toJson(it).toRequestBody(WorkerApi.jsonMediaType) }
                val request = Request.Builder()
                    .url(url)
                    .header("Authorization", "Bearer $token")
                    .method(method, requestBody)
                    .build()
                WorkerApi.httpClient.newCall(request).execute().use { response ->
                    val text = response.body?.string().orEmpty()
                    val error = runCatching { gson.fromJson(text, TradeErrorPayload::class.java)?.error }.getOrNull()
                    when {
                        response.code == 401 -> Result.Unauthorized
                        response.code == 404 && error == "no_profile" -> Result.NoProfile
                        response.code == 400 || response.code == 413 -> Result.Rejected(error)
                        !response.isSuccessful -> Result.Unavailable(response.code)
                        else -> parse(text)?.let { Result.Ok(it) } ?: Result.Unavailable(response.code)
                    }
                }
            }.getOrElse {
                Timber.w(it, "TradeRadar: %s %s fallita", method, path)
                Result.Unavailable(null)
            }
        }

    private inline fun <reified T> parser(): (String) -> T? = { text -> gson.fromJson(text, T::class.java) }

    /** GET /v1/trade/me: il server mi raggiunge e sa chi sono? */
    suspend fun me(): Result<TradeMePayload> =
        call("GET", "v1/trade/me", null, parser<TradeMePayload>())

    suspend fun getProfile(): Result<TradeProfilePayload> =
        call("GET", "v1/trade/profile", null, parser<TradeProfilePayload>())

    suspend fun putProfile(request: TradeProfileRequest): Result<TradeProfilePayload> =
        call("PUT", "v1/trade/profile", request, parser<TradeProfilePayload>())

    /** Disattivazione: il server cancella profilo, liste e possedute. */
    suspend fun deleteProfile(): Result<Unit> =
        call("DELETE", "v1/trade/profile", null) { Unit }

    suspend fun getHaves(): Result<TradeHavesPayload> =
        call("GET", "v1/trade/haves", null, parser<TradeHavesPayload>())

    suspend fun putHaves(payload: TradeHavesPayload): Result<Unit> =
        call("PUT", "v1/trade/haves", payload) { Unit }

    suspend fun putWants(request: TradeWantsRequest): Result<Unit> =
        call("PUT", "v1/trade/wants", request) { Unit }

    suspend fun putOwned(request: TradeOwnedRequest): Result<Unit> =
        call("PUT", "v1/trade/owned", request) { Unit }

    suspend fun matches(): Result<TradeMatchesPayload> =
        call("GET", "v1/trade/matches", null, parser<TradeMatchesPayload>())
}

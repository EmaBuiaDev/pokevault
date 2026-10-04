package com.emabuia.pokevault.data.trade

import com.emabuia.pokevault.data.billing.WorkerApi
import com.google.gson.JsonArray
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.Request
import timber.log.Timber
import java.util.concurrent.TimeUnit

/**
 * Scarica da OpenStreetMap (Overpass) i luoghi di una zona per TradeRadar.
 *
 * Lo fa il telefono e non il server: da Cloudflare overpass-api.de risponde
 * 521. La richiesta pero' la prepara il server (`missingCells` di
 * /proposals/:id/spots) e il filtro lo rifa' lui sui dati grezzi che gli si
 * rimandano: qui non si decide niente. Il telefono chiede solo il centro
 * della zona, mai la sua posizione.
 *
 * Overpass e' spesso carico. Risponde 504, oppure 200 con un `remark`
 * "runtime error" e zero elementi: anche quello e' un fallimento, e mandarlo
 * al server segnerebbe la zona come vuota. Si riprova con una pausa.
 */
object OverpassClient {

    private const val URL = "https://overpass-api.de/api/interpreter"
    /** Overpass rifiuta (406) le richieste senza un'identificazione. */
    private const val USER_AGENT = "PokeVault-TradeRadar/1.0"
    private const val ATTEMPTS = 3

    // La query concede a Overpass fino a 60 secondi.
    private val client by lazy {
        WorkerApi.httpClient.newBuilder().readTimeout(70, TimeUnit.SECONDS).callTimeout(75, TimeUnit.SECONDS).build()
    }

    /** Gli elementi grezzi, o null se Overpass non ha risposto bene. */
    suspend fun fetch(query: String): JsonArray? = withContext(Dispatchers.IO) {
        repeat(ATTEMPTS) { attempt ->
            val result = runCatching {
                val request = Request.Builder()
                    .url(URL)
                    .header("User-Agent", USER_AGENT)
                    .header("Accept", "application/json")
                    .post(FormBody.Builder().add("data", query).build())
                    .build()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@use null
                    val root = JsonParser.parseString(response.body?.string().orEmpty()).asJsonObject
                    val remark = root.get("remark")?.asString.orEmpty()
                    if (remark.contains("error", ignoreCase = true)) null else root.getAsJsonArray("elements") ?: JsonArray()
                }
            }.getOrElse {
                Timber.w(it, "TradeRadar: Overpass non raggiungibile")
                null
            }
            if (result != null) return@withContext result
            if (attempt < ATTEMPTS - 1) delay(4000L * (attempt + 1))
        }
        null
    }
}

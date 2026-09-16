package com.emabuia.pokevault.data.billing

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import timber.log.Timber

/**
 * Verifica dell'abbonamento presso il Worker, che a sua volta chiede a Google.
 *
 * Prima l'entitlement lo decideva solo il telefono: PremiumManager guardava
 * cosa possiede l'account Google Play del dispositivo e accendeva il premium
 * per chiunque fosse connesso a PokeVault in quel momento. Sintomo visto in
 * test interno: comprato l'abbonamento, cambiato account, tutti premium.
 *
 * Qui il client puo' solo *chiedere* una verifica passando il purchase token.
 * Chi sia a chiederla lo stabilisce il server dall'ID token Firebase, e un
 * acquisto appartiene a un account solo: vince il primo che lo verifica.
 *
 * Nota su cosa questo NON fa: non protegge da un'app modificata. Le funzioni
 * premium sono tutte locali, quindi un APK patchato le sblocca comunque,
 * qualunque cosa risponda il server. Serve a legare l'acquisto all'account, a
 * far recuperare il premium a chi paga e perde lo stato locale, e a rendere
 * visibili rimborsi e disdette.
 */
object EntitlementRepository {

    private const val PATH_VERIFY = "v1/billing/verify"
    private const val PATH_ENTITLEMENT = "v1/billing/entitlement"

    private val gson = Gson()

    /** Cosa il server sa dell'abbonamento di questo account. */
    data class ServerEntitlement(
        val entitled: Boolean,
        /** 'active', 'canceled', 'expired', 'gift', 'none'... */
        val state: String,
        val expiryTimeMs: Long?
    ) {
        /**
         * true quando il server non ha nulla su questo account.
         *
         * Non e' un "no": e' un "non lo so ancora". Un utente che ha comprato e
         * non ha mai verificato sta qui, e negargli il premium sarebbe un
         * errore. Si distingue da uno stato scaduto o revocato, che invece e'
         * una risposta piena.
         */
        val isUnknown: Boolean get() = state == "none"
    }

    sealed class VerifyResult {
        data class Verified(val entitlement: ServerEntitlement) : VerifyResult()

        /**
         * L'acquisto e' gia' legato a un altro account.
         *
         * E' una risposta definitiva, non un guasto: questo account NON ha
         * diritto al premium, ed e' il caso che chiude il giro del cambio
         * account. Si sblocca a mano lato server, vedi BILLING.md.
         */
        data object ClaimedByOtherAccount : VerifyResult()

        /** Non siamo riusciti a parlare col server. Lo stato resta ignoto. */
        data object Unavailable : VerifyResult()
    }

    /**
     * I DTO hanno un `-keep` dedicato in proguard-rules.pro, come quelli dei
     * codici regalo: senza, R8 li considera mai scritti (li popola Gson per
     * reflection) e pota i rami che li leggono. Vedi il commento li'.
     */
    private data class EntitlementPayload(
        @SerializedName("entitled") val entitled: Boolean? = null,
        @SerializedName("state") val state: String? = null,
        @SerializedName("expiryTimeMs") val expiryTimeMs: Long? = null,
        @SerializedName("reason") val reason: String? = null
    )

    /**
     * Chiede al server di verificare un acquisto e di legarlo a questo account.
     *
     * Va chiamata dopo ogni acquisto confermato e ogni volta che il telefono
     * trova un acquisto che il server non conosce ancora.
     */
    suspend fun verify(purchaseToken: String): VerifyResult = withContext(Dispatchers.IO) {
        val url = WorkerApi.endpoint(PATH_VERIFY) ?: return@withContext VerifyResult.Unavailable
        val token = WorkerApi.idToken() ?: return@withContext VerifyResult.Unavailable

        val body = gson.toJson(mapOf("purchaseToken" to purchaseToken))
            .toRequestBody(WorkerApi.jsonMediaType)

        runCatching {
            val request = Request.Builder()
                .url(url)
                .header("Authorization", "Bearer $token")
                .post(body)
                .build()

            WorkerApi.httpClient.newCall(request).execute().use { response ->
                val payload = runCatching {
                    gson.fromJson(response.body?.string().orEmpty(), EntitlementPayload::class.java)
                }.getOrNull()

                when {
                    payload?.reason == "token_claimed_by_other_account" ->
                        VerifyResult.ClaimedByOtherAccount

                    // 502 = il service account Play non e' configurato, 5xx =
                    // guasto. In entrambi i casi lo stato resta ignoto e il
                    // telefono continua con la verifica locale: il server e'
                    // autorevole quando risponde, non un punto di rottura unico.
                    response.isSuccessful && payload?.entitled != null ->
                        VerifyResult.Verified(
                            ServerEntitlement(
                                entitled = payload.entitled,
                                state = payload.state ?: "none",
                                expiryTimeMs = payload.expiryTimeMs
                            )
                        )

                    else -> VerifyResult.Unavailable
                }
            }
        }.onFailure { Timber.w(it, "Verifica abbonamento fallita") }
            .getOrDefault(VerifyResult.Unavailable)
    }

    /**
     * Legge l'entitlement memorizzato per questo account.
     *
     * E' la strada con cui chi ha comprato su un altro dispositivo, o ha perso
     * lo stato locale, si ritrova il premium senza dover ricomprare.
     *
     * Restituisce null quando il server non risponde: null vuol dire "non lo
     * so", e chi non sa non toglie il premium a nessuno.
     */
    suspend fun fetchEntitlement(): ServerEntitlement? = withContext(Dispatchers.IO) {
        val url = WorkerApi.endpoint(PATH_ENTITLEMENT) ?: return@withContext null
        val token = WorkerApi.idToken() ?: return@withContext null

        runCatching {
            val request = Request.Builder()
                .url(url)
                .header("Authorization", "Bearer $token")
                .get()
                .build()

            WorkerApi.httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) error("HTTP ${response.code}")
                val payload = gson.fromJson(
                    response.body?.string().orEmpty(),
                    EntitlementPayload::class.java
                ) ?: error("risposta vuota")

                ServerEntitlement(
                    entitled = payload.entitled == true,
                    state = payload.state ?: "none",
                    expiryTimeMs = payload.expiryTimeMs
                )
            }
        }.onFailure { Timber.w(it, "Lettura entitlement fallita") }.getOrNull()
    }
}

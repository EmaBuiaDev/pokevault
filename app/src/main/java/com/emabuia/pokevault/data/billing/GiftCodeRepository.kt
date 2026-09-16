package com.emabuia.pokevault.data.billing

import android.annotation.SuppressLint
import android.content.Context
import android.provider.Settings
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import timber.log.Timber

/**
 * Codici regalo da 1 mese: il codice AMICO dell'utente e il riscatto.
 *
 * Tutto passa dal Worker Cloudflare, mai da Firestore lato client. Un mese di
 * premium e' un entitlement, e un entitlement che il telefono puo' asserire da
 * solo non e' un entitlement: chiunque sbloccherebbe il premium modificando
 * l'app. Qui il client puo' soltanto CHIEDERE un riscatto; chi sia a chiederlo
 * lo stabilisce il server dall'ID token Firebase.
 */
object GiftCodeRepository {

    private const val PATH_ME = "v1/gift/me"
    private const val PATH_REDEEM = "v1/gift/redeem"

    private val gson = Gson()

    /** true quando il Worker e' configurato: senza URL la funzione resta nascosta. */
    val isConfigured: Boolean
        get() = WorkerApi.isConfigured

    /** Stato del codice AMICO dell'utente autenticato. */
    data class GiftStatus(
        val code: String = "",
        val grantDays: Int = 30,
        val invitesUsed: Int = 0,
        val invitesMax: Int = 0,
        val alreadyRedeemed: Boolean = false,
        /** Scadenza del mese regalo attivo, o null se non ce n'e' uno. */
        val giftUntilMs: Long? = null
    )

    /**
     * Motivo per cui un riscatto e' stato rifiutato.
     *
     * Il Worker manda una sigla stabile e l'app la traduce: cosi' un utente con
     * l'app in inglese non riceve una frase in italiano, e correggere un refuso
     * non richiede di ridistribuire il Worker.
     */
    enum class RedeemRejection {
        CODE_MISSING,
        CODE_NOT_FOUND,
        CODE_DISABLED,
        CODE_EXPIRED,
        CODE_EXHAUSTED,
        OWN_CODE,
        ALREADY_REDEEMED,
        DEVICE_ALREADY_REDEEMED,
        RATE_LIMITED,
        UNKNOWN;

        companion object {
            fun fromWire(reason: String?): RedeemRejection = when (reason) {
                "code_missing" -> CODE_MISSING
                "code_not_found" -> CODE_NOT_FOUND
                "code_disabled" -> CODE_DISABLED
                "code_expired" -> CODE_EXPIRED
                "code_exhausted" -> CODE_EXHAUSTED
                "own_code" -> OWN_CODE
                "already_redeemed" -> ALREADY_REDEEMED
                "device_already_redeemed" -> DEVICE_ALREADY_REDEEMED
                "rate_limited" -> RATE_LIMITED
                else -> UNKNOWN
            }
        }
    }

    sealed class RedeemResult {
        /** Mese concesso: [giftUntilMs] e' la scadenza decisa dal server. */
        data class Success(val giftUntilMs: Long, val grantDays: Int) : RedeemResult()

        /** Il server ha risposto, ma ha detto di no. */
        data class Rejected(val reason: RedeemRejection) : RedeemResult()

        /** Non siamo riusciti a parlare col server: rete, 500, token assente. */
        data object Unavailable : RedeemResult()
    }

    /**
     * I due DTO qui sotto hanno una regola `-keep` dedicata in
     * proguard-rules.pro, e ne hanno bisogno: **non toglierla**.
     *
     * [SerializedName] da sola non basta con R8 in full mode. Il problema non e'
     * il rinominamento dei campi, e' che nessuno li *scrive* nel bytecode — li
     * popola Gson per reflection — quindi R8 li considera costantemente null e
     * pota tutti i rami che dipendono da loro. Senza quella regola l'unico esito
     * possibile di [redeem] in release diventa [RedeemResult.Unavailable].
     *
     * Le annotazioni restano perche' fissano i nomi JSON: rinominare una
     * proprieta' Kotlin non deve poter rompere il contratto col Worker.
     */
    private data class GiftStatusPayload(
        @SerializedName("code") val code: String? = null,
        @SerializedName("grantDays") val grantDays: Int? = null,
        @SerializedName("invitesUsed") val invitesUsed: Int? = null,
        @SerializedName("invitesMax") val invitesMax: Int? = null,
        @SerializedName("alreadyRedeemed") val alreadyRedeemed: Boolean? = null,
        @SerializedName("giftUntilMs") val giftUntilMs: Long? = null
    )

    private data class RedeemPayload(
        @SerializedName("entitled") val entitled: Boolean? = null,
        @SerializedName("reason") val reason: String? = null,
        @SerializedName("grantDays") val grantDays: Int? = null,
        @SerializedName("giftUntilMs") val giftUntilMs: Long? = null
    )


    /**
     * Legge il codice AMICO dell'utente, creandolo lato server se non esiste.
     *
     * Restituisce null quando il server non risponde: la schermata mostra un
     * errore, non un codice inventato dal telefono che non esisterebbe per
     * nessun altro.
     */
    suspend fun fetchStatus(): GiftStatus? = withContext(Dispatchers.IO) {
        val url = WorkerApi.endpoint(PATH_ME) ?: return@withContext null
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
                    GiftStatusPayload::class.java
                ) ?: error("risposta vuota")

                GiftStatus(
                    code = payload.code.orEmpty(),
                    grantDays = payload.grantDays ?: 30,
                    invitesUsed = payload.invitesUsed ?: 0,
                    invitesMax = payload.invitesMax ?: 0,
                    alreadyRedeemed = payload.alreadyRedeemed == true,
                    giftUntilMs = payload.giftUntilMs
                )
            }
        }.onFailure { Timber.w(it, "Lettura codice AMICO fallita") }.getOrNull()
    }

    /**
     * Riscatta un codice regalo.
     *
     * Il device id viaggia insieme al codice perche' il vincolo "un riscatto per
     * dispositivo" e' cio' che impedisce a una persona di rifarsi l'account e
     * riscattare all'infinito. Il server non lo salva in chiaro: ne conserva un
     * HMAC, che risponde alla sola domanda "questo telefono ha gia' riscattato?".
     */
    suspend fun redeem(context: Context, rawCode: String): RedeemResult =
        withContext(Dispatchers.IO) {
            val url = WorkerApi.endpoint(PATH_REDEEM) ?: return@withContext RedeemResult.Unavailable
            val token = WorkerApi.idToken() ?: return@withContext RedeemResult.Unavailable

            val body = gson.toJson(
                mapOf("code" to rawCode.trim(), "deviceId" to deviceId(context))
            ).toRequestBody(WorkerApi.jsonMediaType)

            runCatching {
                val request = Request.Builder()
                    .url(url)
                    .header("Authorization", "Bearer $token")
                    .post(body)
                    .build()

                WorkerApi.httpClient.newCall(request).execute().use { response ->
                    val payload = runCatching {
                        gson.fromJson(response.body?.string().orEmpty(), RedeemPayload::class.java)
                    }.getOrNull()

                    when {
                        response.isSuccessful && payload?.entitled == true && payload.giftUntilMs != null ->
                            RedeemResult.Success(payload.giftUntilMs, payload.grantDays ?: 30)

                        // 4xx con una sigla e' una risposta piena: il server ha
                        // deciso. Un 5xx senza sigla e' un guasto, e va
                        // distinto, altrimenti l'utente legge "codice non
                        // valido" quando il codice era buono.
                        payload?.reason != null ->
                            RedeemResult.Rejected(RedeemRejection.fromWire(payload.reason))

                        else -> RedeemResult.Unavailable
                    }
                }
            }.onFailure { Timber.w(it, "Riscatto codice regalo fallito") }
                .getOrDefault(RedeemResult.Unavailable)
        }

    /**
     * Identificatore del dispositivo per il vincolo anti multi-account.
     *
     * ANDROID_ID e' l'unico identificatore stabile che Google lascia usare senza
     * permessi: da Android 8 e' diverso per ogni coppia (app, dispositivo) e
     * sopravvive alla disinstallazione, che e' esattamente cio' che serve qui.
     * Non identifica l'utente altrove, e il server lo riceve solo per
     * trasformarlo subito in un HMAC.
     *
     * Un factory reset lo cambia: e' un freno, non un muro, e va bene cosi'.
     */
    @SuppressLint("HardwareIds")
    private fun deviceId(context: Context): String =
        runCatching {
            Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID).orEmpty()
        }.getOrDefault("")
}

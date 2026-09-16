package com.emabuia.pokevault.data.billing

import com.emabuia.pokevault.BuildConfig
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.tasks.await
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import timber.log.Timber
import java.util.concurrent.TimeUnit

/**
 * Il poco che serve per parlare col Worker autenticati come l'utente corrente.
 *
 * Sta qui e non dentro i due repository che lo usano perche' altrimenti
 * sarebbero due copie della stessa cosa: stesso client, stesso modo di
 * costruire l'URL, stesso modo di prendere l'ID token. Un timeout o un bug
 * sull'autenticazione andrebbero corretti due volte, e la seconda si
 * dimenticherebbe.
 */
internal object WorkerApi {

    val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .build()
    }

    /** false quando POKEWALLET_PROXY_URL non e' configurato: niente Worker, niente chiamate. */
    val isConfigured: Boolean
        get() = BuildConfig.POKEWALLET_PROXY_URL.trim().isNotBlank()

    /** URL completo di un percorso sul Worker, o null se il Worker non e' configurato. */
    fun endpoint(path: String): String? {
        val base = BuildConfig.POKEWALLET_PROXY_URL.trim().trimEnd('/')
        if (base.isBlank()) return null
        return "$base/$path"
    }

    /**
     * ID token Firebase dell'utente connesso, o null.
     *
     * `false` e non `true`: un token in cache va benissimo finche' e' valido, e
     * Firebase lo rinnova da se' quando scade. Forzare il refresh
     * significherebbe un giro di rete in piu' a ogni chiamata.
     */
    suspend fun idToken(): String? {
        val user = FirebaseAuth.getInstance().currentUser ?: return null
        return runCatching { user.getIdToken(false).await().token }
            .onFailure { Timber.w(it, "ID token Firebase non ottenuto") }
            .getOrNull()
    }
}

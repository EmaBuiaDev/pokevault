package com.emabuia.pokevault.data.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber

/**
 * C'e' rete o no, per tutta l'app.
 *
 * Serve a due cose: il banner "sei offline" e le letture di Firestore, che
 * senza rete devono andare subito alla copia locale invece di aspettare una
 * decina di secondi prima di arrendersi da sole.
 *
 * "Online" vuol dire rete con Internet verificato da Android, non solo
 * collegata: un Wi-Fi d'albergo con la pagina di login non conta.
 *
 * Nel dubbio vale online: un banner "offline" sbagliato spaventa, e le
 * letture di Firestore se la cavano comunque da sole.
 */
object NetworkMonitor {

    private val _isOnline = MutableStateFlow(true)
    val isOnline: StateFlow<Boolean> = _isOnline.asStateFlow()

    @Volatile
    private var started = false

    fun init(context: Context) {
        if (started) return
        started = true
        val manager = context.getSystemService(ConnectivityManager::class.java) ?: return
        _isOnline.value = isValidated(manager, manager.activeNetwork)
        try {
            manager.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
                override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                    _isOnline.value = capabilities.hasInternetValidated()
                }

                override fun onLost(network: Network) {
                    _isOnline.value = false
                }
            })
        } catch (e: RuntimeException) {
            // Su alcuni telefoni il servizio rifiuta la registrazione: resta
            // "online", cioe' il comportamento di prima.
            Timber.w(e, "NetworkMonitor: callback non registrata")
            _isOnline.value = true
        }
    }

    private fun isValidated(manager: ConnectivityManager, network: Network?): Boolean {
        if (network == null) return false
        return manager.getNetworkCapabilities(network)?.hasInternetValidated() ?: false
    }

    private fun NetworkCapabilities.hasInternetValidated(): Boolean =
        hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
}

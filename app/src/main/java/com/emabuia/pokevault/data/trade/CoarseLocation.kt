package com.emabuia.pokevault.data.trade

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Looper
import androidx.core.content.ContextCompat
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * Una sola lettura della posizione APPROSSIMATIVA, con l'app aperta.
 *
 * Niente librerie nuove: basta il LocationManager di Android con il provider
 * di rete, che con ACCESS_COARSE_LOCATION da' una precisione di qualche
 * centinaio di metri, piu' che sufficiente per una cella di ~5 km. Del
 * risultato si usa solo [Geohash.encode]: le coordinate non escono da qui.
 */
object CoarseLocation {

    fun hasPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    /** La cella geohash di 5 caratteri, o null se la posizione non arriva entro [timeoutMs]. */
    suspend fun currentCell(context: Context, timeoutMs: Long = 15_000): String? {
        if (!hasPermission(context)) return null
        val location = withTimeoutOrNull(timeoutMs) { read(context) } ?: lastKnown(context)
        return location?.let { Geohash.encode(it.latitude, it.longitude) }
    }

    @SuppressLint("MissingPermission") // verificato in currentCell
    private suspend fun read(context: Context): Location? {
        val manager = context.getSystemService(LocationManager::class.java) ?: return null
        val provider = listOf(LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER)
            .firstOrNull { runCatching { manager.isProviderEnabled(it) }.getOrDefault(false) }
            ?: return null
        return suspendCancellableCoroutine { continuation ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                manager.getCurrentLocation(provider, null, context.mainExecutor) { location ->
                    if (continuation.isActive) continuation.resume(location)
                }
            } else {
                val listener = object : LocationListener {
                    override fun onLocationChanged(location: Location) {
                        if (continuation.isActive) continuation.resume(location)
                    }
                }
                @Suppress("DEPRECATION")
                manager.requestSingleUpdate(provider, listener, Looper.getMainLooper())
                continuation.invokeOnCancellation { manager.removeUpdates(listener) }
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun lastKnown(context: Context): Location? {
        val manager = context.getSystemService(LocationManager::class.java) ?: return null
        return listOf(LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER)
            .mapNotNull { runCatching { manager.getLastKnownLocation(it) }.getOrNull() }
            .maxByOrNull { it.time }
    }
}

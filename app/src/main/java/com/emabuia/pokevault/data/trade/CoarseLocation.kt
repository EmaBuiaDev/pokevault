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
 * centinaio di metri, piu' che sufficiente per una cella di ~5 km. Al server
 * va solo [Geohash.encode] della posizione: le coordinate restano sul
 * telefono, dove al massimo servono a dire quanto e' lontano un luogo.
 */
object CoarseLocation {

    fun hasPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    /** La cella geohash di 5 caratteri, o null se la posizione non arriva entro [timeoutMs]. */
    suspend fun currentCell(context: Context, timeoutMs: Long = 15_000): String? =
        currentPoint(context, timeoutMs)?.let { (lat, lon) -> Geohash.encode(lat, lon) }

    /**
     * Latitudine e longitudine approssimative, o null. SOLO per calcoli sul
     * telefono (la distanza dai luoghi d'incontro): non vanno mai al server.
     */
    suspend fun currentPoint(context: Context, timeoutMs: Long = 15_000): Pair<Double, Double>? {
        if (!hasPermission(context)) return null
        val location = withTimeoutOrNull(timeoutMs) { read(context) } ?: lastKnown(context)
        return location?.let { it.latitude to it.longitude }
    }

    /** Distanza in km fra due punti (formula dell'emisenoverso). */
    fun distanceKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = Math.PI / 180
        val dLat = (lat2 - lat1) * r
        val dLon = (lon2 - lon1) * r
        val h = Math.sin(dLat / 2).let { it * it } +
            Math.cos(lat1 * r) * Math.cos(lat2 * r) * Math.sin(dLon / 2).let { it * it }
        return 6371 * 2 * Math.asin(Math.sqrt(h))
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

package com.emabuia.pokevault.data.trade

/**
 * Geohash, solo la codifica.
 *
 * Si calcola sul telefono apposta: al server di TradeRadar arrivano soltanto i
 * 5 caratteri della cella (~4,9 x 4,9 km), mai latitudine e longitudine. Lo
 * stesso algoritmo sta nel Worker (src/geohash.ts), che ne ricava le celle
 * vicine.
 */
object Geohash {

    private const val BASE32 = "0123456789bcdefghjkmnpqrstuvwxyz"

    /** Precisione usata da TradeRadar. */
    const val TRADE_PRECISION = 5

    fun encode(latitude: Double, longitude: Double, precision: Int = TRADE_PRECISION): String {
        require(latitude in -90.0..90.0 && longitude in -180.0..180.0) { "coordinate fuori scala" }
        var latMin = -90.0
        var latMax = 90.0
        var lonMin = -180.0
        var lonMax = 180.0
        var evenBit = true
        var bit = 0
        var index = 0
        val hash = StringBuilder()
        while (hash.length < precision) {
            if (evenBit) {
                val mid = (lonMin + lonMax) / 2
                if (longitude >= mid) { index = index * 2 + 1; lonMin = mid } else { index *= 2; lonMax = mid }
            } else {
                val mid = (latMin + latMax) / 2
                if (latitude >= mid) { index = index * 2 + 1; latMin = mid } else { index *= 2; latMax = mid }
            }
            evenBit = !evenBit
            if (++bit == 5) {
                hash.append(BASE32[index])
                bit = 0
                index = 0
            }
        }
        return hash.toString()
    }
}

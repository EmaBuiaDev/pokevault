package com.emabuia.pokevault.data.remote

import org.junit.Assert.assertEquals
import org.junit.Test

/** Il numero di carte nell'elenco delle espansioni. */
class ItalianSetTotalTest {

    @Test
    fun `vince il conteggio del nostro catalogo, non il set inglese collegato`() {
        // 30 Anniversario: il set collegato diceva 30, le carte sono 161.
        assertEquals(161, italianSetTotal(cardCount = 161, fallback = 30))
    }

    @Test
    fun `segrete comprese, come dentro l'espansione`() {
        // Destino di Paldea: 91 stampate, 245 in tutto.
        assertEquals(245, italianSetTotal(cardCount = 245, fallback = 91))
    }

    @Test
    fun `senza conteggio resta quello di prima`() {
        assertEquals(30, italianSetTotal(cardCount = 0, fallback = 30))
    }
}

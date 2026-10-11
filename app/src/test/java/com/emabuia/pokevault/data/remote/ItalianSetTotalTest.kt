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

/** Il set dopo aver caricato tutte le sue carte. */
class SetKnownCardCountTest {

    private val paldea = TcgSet(id = "ita-sv4pt5", printedTotal = 91, total = 245)

    @Test
    fun `il totale stampato non diventa il numero di carte caricate`() {
        val updated = paldea.withKnownCardCount(245)
        assertEquals(91, updated.printedTotal)
        assertEquals(245, updated.total)
    }

    @Test
    fun `il totale delle carte si aggiorna`() {
        assertEquals(250, paldea.withKnownCardCount(250).total)
    }

    @Test
    fun `un totale stampato sconosciuto si riempie`() {
        val unknown = TcgSet(id = "x", printedTotal = 0, total = 0)
        assertEquals(30, unknown.withKnownCardCount(30).printedTotal)
    }

    @Test
    fun `zero carte caricate non cambia niente`() {
        assertEquals(paldea, paldea.withKnownCardCount(0))
    }
}

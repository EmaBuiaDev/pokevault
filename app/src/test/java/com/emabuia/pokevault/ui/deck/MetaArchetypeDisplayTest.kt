package com.emabuia.pokevault.ui.deck

import com.emabuia.pokevault.data.model.MetaArchetype
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Come la classifica del Meta Deck colora e ordina i numeri. */
class MetaArchetypeDisplayTest {

    private fun archetype(
        name: String,
        share: Double = 10.0,
        wins: Int = 0,
        losses: Int = 0,
        trend: Double? = null
    ) = MetaArchetype(
        name = name,
        count = 1,
        metaShare = share,
        avgWinrate = if (wins + losses == 0) 0.0 else wins.toDouble() / (wins + losses),
        topPlacement = 1,
        recentResults = emptyList(),
        sampleDeck = null,
        wins = wins,
        losses = losses,
        trend = trend
    )

    @Test
    fun `sotto le venti partite il win rate non si giudica`() {
        // 5-1 e' l'83%, ma su sei partite non dice niente.
        assertEquals(WinRateLevel.FEW_DATA, archetype("A", wins = 5, losses = 1).winRateLevel())
        assertEquals(WinRateLevel.HIGH, archetype("B", wins = 15, losses = 5).winRateLevel())
    }

    @Test
    fun `intorno al 50 per cento e' nella media`() {
        assertEquals(WinRateLevel.AVERAGE, archetype("A", wins = 10, losses = 10).winRateLevel())
        assertEquals(WinRateLevel.HIGH, archetype("B", wins = 11, losses = 9).winRateLevel())
        assertEquals(WinRateLevel.LOW, archetype("C", wins = 9, losses = 11).winRateLevel())
    }

    @Test
    fun `il trend sale o scende solo oltre due punti`() {
        assertEquals(TrendDirection.UP, archetype("A", trend = 2.0).trendDirection())
        assertEquals(TrendDirection.DOWN, archetype("B", trend = -3.5).trendDirection())
        assertEquals(TrendDirection.FLAT, archetype("C", trend = 1.9).trendDirection())
        assertNull(archetype("D", trend = null).trendDirection())
    }

    @Test
    fun `ordinando per win rate chi ha pochi dati va in fondo`() {
        val sorted = listOf(
            archetype("Fortunato", wins = 6, losses = 0),
            archetype("Solido", wins = 30, losses = 20),
            archetype("Forte", wins = 40, losses = 20)
        ).sortedByWinRate()

        assertEquals(listOf("Forte", "Solido", "Fortunato"), sorted.map { it.name })
    }

    @Test
    fun `la barra mostra i primi otto e il resto come altri`() {
        val list = (1..10).map { archetype("A$it", share = 10.0) }

        val (top, others) = list.topShares()

        assertEquals(8, top.size)
        assertEquals(20.0, others, 1e-9)
    }
}

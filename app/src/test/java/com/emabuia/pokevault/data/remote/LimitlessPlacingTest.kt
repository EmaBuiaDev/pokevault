package com.emabuia.pokevault.data.remote

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * I ritirati arrivano con placing null, cioe' 0. Prima finivano in testa a
 * ogni classifica: "Top 0" e "#0" su tutti gli archetipi del Meta Deck.
 */
class LimitlessPlacingTest {

    @Test
    fun `i piazzamenti mancanti vanno in fondo, non in testa`() {
        val placings = listOf(0, 3, 0, 1, 2)
        assertEquals(listOf(1, 2, 3, 0, 0), LimitlessPlacing.ranked(placings) { it })
    }

    @Test
    fun `il miglior piazzamento ignora gli zeri`() {
        assertEquals(4, LimitlessPlacing.best(listOf(0, 0, 7, 4)))
        assertEquals(0, LimitlessPlacing.best(listOf(0, 0)))
        assertEquals(0, LimitlessPlacing.best(emptyList()))
    }

    @Test
    fun `i primi 32 di un torneo sono i primi classificati`() {
        // 10 ritirati e 40 classificati: prima i 32 presi erano i 10 ritirati
        // piu' i primi 22.
        val standings = List(10) { 0 } + (1..40).toList()
        val top = LimitlessPlacing.ranked(standings.shuffled()) { it }.take(32)
        assertEquals((1..32).toList(), top)
    }
}

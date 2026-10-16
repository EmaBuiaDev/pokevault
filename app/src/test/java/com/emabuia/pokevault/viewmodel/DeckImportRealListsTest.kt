package com.emabuia.pokevault.viewmodel

import com.emabuia.pokevault.data.model.PokemonCard
import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Due mazzi veri, riga per riga, contro il catalogo italiano vero.
 *
 * `deckimport/catalogo-reale.json` e' generato il 16/10/2026 dal Worker di
 * produzione: per ogni riga della decklist la stampa italiana attesa (set e
 * numero), e una collezione con tutte le carte che hanno lo stesso nome base
 * negli altri set -- le trappole: la Bayleef 9 di Ascesa Eroica, i Meowth
 * normali, 26 altre Ogerpon. Il mazzo Meganium e' quello che ha fatto
 * scoprire il difetto; quello Dragapult e' la lista vincente della settimana.
 */
class DeckImportRealListsTest {

    private data class Line(
        val qty: Int, val name: String, val set: String, val number: String,
        val expectApiId: String, val expectName: String
    )
    private data class Owned(val id: String, val apiId: String, val name: String, val number: String)
    private data class Fixture(val meganium: List<Line>, val dragapult: List<Line>, val owned: List<Owned>)

    private val fixture: Fixture by lazy {
        val text = javaClass.classLoader!!.getResource("deckimport/catalogo-reale.json")!!.readText()
        Gson().fromJson(text, Fixture::class.java)
    }

    private fun collection(without: String? = null): List<PokemonCard> =
        fixture.owned.filter { it.apiId != without }.map {
            PokemonCard(id = it.id, name = it.name, apiCardId = it.apiId, cardNumber = it.number, quantity = 1)
        }

    private val allLines: List<Line> get() = fixture.meganium + fixture.dragapult

    @Test
    fun `i due mazzi ci sono tutti`() {
        assertEquals(25, fixture.meganium.size)
        assertEquals(21, fixture.dragapult.size)
    }

    @Test
    fun `con tutte le stampe in collezione ogni riga prende solo quella giusta`() {
        val owned = collection()
        val wrong = allLines.mapNotNull { line ->
            val match = DeckImportMatcher.find(owned, line.name, line.set, line.number)
            val picked = match.cards.map { it.apiCardId }.distinct()
            if (picked == listOf(line.expectApiId) && !match.usedFallbackSet) null
            else "${line.name} ${line.set} ${line.number}: $picked fallback=${match.usedFallbackSet}"
        }
        assertTrue("Righe sbagliate:\n" + wrong.joinToString("\n"), wrong.isEmpty())
    }

    @Test
    fun `senza la stampa giusta non prende mai la carta sbagliata in silenzio`() {
        val problems = allLines.mapNotNull { line ->
            val match = DeckImportMatcher.find(collection(without = line.expectApiId), line.name, line.set, line.number)
            when {
                // Niente in collezione: la carta va fra le mancanti e l'import la
                // aggiunge dal catalogo con set e numero, cioe' quella giusta.
                match.cards.isEmpty() -> null
                // Un'altra stampa si accetta solo dichiarandola, e mai al posto
                // di una carta ex/V (o viceversa): Meowth ex non e' Meowth.
                !match.usedFallbackSet -> "${line.name}: altra stampa senza avviso ${match.cards.map { it.apiCardId }}"
                match.cards.any { DeckImportMatcher.hasRuleBox(it.name) != DeckImportMatcher.hasRuleBox(line.expectName) } ->
                    "${line.name}: carta di tipo diverso ${match.cards.map { it.name }}"
                else -> null
            }
        }
        assertTrue("Problemi:\n" + problems.joinToString("\n"), problems.isEmpty())
    }

    @Test
    fun `Meowth ex senza la sua copia non diventa un Meowth normale`() {
        val owned = collection().filter { DeckImportMatcher.nameKey(it.name) != "meowthex" }
        val match = DeckImportMatcher.find(owned, "Meowth ex", "POR", "121")
        assertFalse(match.cards.any { DeckImportMatcher.nameKey(it.name) == "meowth" })
    }
}

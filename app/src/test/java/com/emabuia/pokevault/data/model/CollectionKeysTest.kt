package com.emabuia.pokevault.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * Le due chiavi della collezione, che fanno cose diverse e vanno tenute
 * distinte.
 *
 * [collectionGroupKey] separa le stampe -- serve a chi salva, perche' la
 * Normale e la Reverse sono due documenti e devono restarlo.
 * [collectionCardKey] le riunisce -- serve alla vista, dove la stessa carta
 * deve comparire una volta sola con "x2" e i badge.
 *
 * Confonderle non fa fallire niente: o la collezione mostra la carta doppia,
 * o due stampe diverse si fondono in un documento solo e l'informazione su
 * quale copia si possiede sparisce.
 */
class CollectionKeysTest {

    private fun card(
        name: String = "Charizard",
        set: String = "Set Base",
        number: String = "4",
        variant: String = "Normal",
        apiCardId: String = "ita:base1:4"
    ) = PokemonCard(
        name = name,
        set = set,
        cardNumber = number,
        variant = variant,
        apiCardId = apiCardId
    )

    @Test
    fun `stampe diverse della stessa carta restano documenti separati`() {
        val normale = card(variant = "Normal")
        val reverse = card(variant = "Reverse")
        assertNotEquals(normale.collectionGroupKey(), reverse.collectionGroupKey())
    }

    @Test
    fun `stampe diverse della stessa carta stanno in una tessera sola`() {
        val normale = card(variant = "Normal")
        val reverse = card(variant = "Reverse")
        val holo = card(variant = "Holo")
        assertEquals(normale.collectionCardKey(), reverse.collectionCardKey())
        assertEquals(normale.collectionCardKey(), holo.collectionCardKey())
    }

    @Test
    fun `carte diverse restano tessere diverse`() {
        val quattro = card(number = "4")
        val cinque = card(name = "Blastoise", number = "5")
        assertNotEquals(quattro.collectionCardKey(), cinque.collectionCardKey())

        val altroSet = card(set = "Jungle")
        assertNotEquals(quattro.collectionCardKey(), altroSet.collectionCardKey())
    }

    @Test
    fun `la chiave carta e' quella di gruppo senza l'ultimo campo`() {
        // Le due restano confrontabili a occhio nei log: se un giorno una
        // cambia forma, questo test lo dice.
        val c = card(variant = "Reverse")
        assertEquals(c.collectionCardKey(), c.collectionGroupKey().substringBeforeLast("|"))
    }

    @Test
    fun `senza set e numero si ricade sull'id della carta a catalogo`() {
        val senzaSet = card(set = "", number = "", apiCardId = "ita:base1:4")
        assertEquals("ita:base1:4", senzaSet.collectionCardKey())
        // E due stampe della stessa carta continuano a coincidere anche li'.
        assertEquals(
            senzaSet.collectionCardKey(),
            card(set = "", number = "", variant = "Reverse", apiCardId = "ita:base1:4").collectionCardKey()
        )
    }
}

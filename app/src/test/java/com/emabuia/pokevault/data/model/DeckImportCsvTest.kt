package com.emabuia.pokevault.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** I casi ai margini dell'import da CSV e dei codici set nuovi. */
class DeckImportCsvTest {

    @Test
    fun `virgola, punto e virgola e tab danno lo stesso risultato`() {
        val attese = listOf("4,Charizard ex,OBF,125", "4;Charizard ex;OBF;125", "4\tCharizard ex\tOBF\t125")
        attese.forEach { riga ->
            val card = DeckImportParser.parse(riga).cards.single()
            assertEquals(4, card.qty)
            assertEquals("Charizard ex", card.name)
            assertEquals("sv3", card.set)
            assertEquals("125", card.number)
        }
    }

    @Test
    fun `l'intestazione del CSV non diventa il nome del deck`() {
        val result = DeckImportParser.parse("Quantità;Nome;Set;Numero\n2;Pikachu ex;SSP;57")
        assertNull(result.deckName)
        assertEquals(1, result.cards.size)
        assertTrue(result.errors.isEmpty())
    }

    @Test
    fun `campi fra virgolette e colonne mancanti`() {
        val result = DeckImportParser.parse("\"3\",\"Ultra Ball\",\"SVI\",\"196\"\n2,Nest Ball")
        assertEquals(2, result.cards.size)
        assertEquals("Ultra Ball", result.cards[0].name)
        assertEquals("196", result.cards[0].number)
        assertEquals("Nest Ball", result.cards[1].name)
        assertNull(result.cards[1].set)
        assertNull(result.cards[1].number)
    }

    @Test
    fun `le sezioni valgono anche per le righe CSV`() {
        val result = DeckImportParser.parse("Trainer: 4\n4;Tremendous Bomb;PBL;82")
        assertEquals("trainer", result.cards.single().type)
        assertEquals("me05", result.cards.single().set)
    }

    @Test
    fun `un codice set che comincia con una cifra`() {
        val card = DeckImportParser.parse("1 Mew ex 30C 66").cards.single()
        assertEquals("Mew ex", card.name)
        assertEquals("30th", card.set)
        assertEquals("66", card.number)
    }

    @Test
    fun `le righe PTCG Live con apostrofo o trattino restano come prima`() {
        val result = DeckImportParser.parse(
            "2 Lillie's Clefairy ex JTG 56\n1 Chi-Yu PBL 59\n4 Buddy-Buddy Poffin TEF 144"
        )
        assertEquals(listOf("Lillie's Clefairy ex", "Chi-Yu", "Buddy-Buddy Poffin"), result.cards.map { it.name })
        assertEquals(listOf("sv9", "me05", "sv5"), result.cards.map { it.set })
    }

    @Test
    fun `un nome senza set che finisce con una cifra non viene spezzato`() {
        val card = DeckImportParser.parse("1 Porygon2").cards.single()
        assertEquals("Porygon2", card.name)
        assertNull(card.set)
    }
}

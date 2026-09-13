package com.emabuia.pokevault.ocr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * L'ID in basso a sinistra e' il perno dello scanner: se lo sbaglia, propone
 * carte di un'altra espansione. Questi test coprono le letture sporche che ML
 * Kit produce davvero su quella striscia (slash mangiato, zeri letti come O,
 * cifre incollate) e i numeri che NON sono un ID (danni, costi, copyright).
 */
class CardIdParserTest {

    // ── Letture pulite ──────────────────────────────────────────────

    @Test
    fun `legge il formato stampato`() {
        val id = CardIdParser.parse("067/087")
        assertEquals("67", id?.number)
        assertEquals("87", id?.total)
    }

    @Test
    fun `ignora gli spazi attorno allo slash`() {
        assertEquals("25", CardIdParser.parse("025 / 198")?.number)
        assertEquals("198", CardIdParser.parse("025 / 198")?.total)
    }

    @Test
    fun `sopravvive al contorno della striscia`() {
        // Marchio di regolamentazione, numero e simbolo di rarita' sulla stessa riga.
        val id = CardIdParser.parse("G 131/191 ★")
        assertEquals("131", id?.number)
        assertEquals("191", id?.total)
    }

    @Test
    fun `tiene le segrete sopra il totale stampato`() {
        val id = CardIdParser.parse("199/198")
        assertEquals("199", id?.number)
        assertEquals("198", id?.total)
    }

    @Test
    fun `legge la riga completa con illustratore`() {
        val id = CardIdParser.parse("Illus. Kagemaru Himeno\n067/087")
        assertEquals("67", id?.number)
        assertEquals("87", id?.total)
    }

    // ── Letture sporche ─────────────────────────────────────────────

    @Test
    fun `ripara gli zeri letti come lettera O`() {
        val id = CardIdParser.parse("O67/O87")
        assertEquals("67", id?.number)
        assertEquals("87", id?.total)
    }

    @Test
    fun `accetta lo slash letto come barra verticale`() {
        val id = CardIdParser.parse("067|087")
        assertEquals("67", id?.number)
        assertEquals("87", id?.total)
        assertTrue("la lettura sporca deve valere meno di quella pulita", id!!.confidence < 0.95f)
    }

    @Test
    fun `separa le cifre incollate`() {
        val id = CardIdParser.parse("067087")
        assertEquals("67", id?.number)
        assertEquals("87", id?.total)
    }

    @Test
    fun `separa le cifre quando lo slash e letto come uno`() {
        val id = CardIdParser.parse("0671087")
        assertEquals("67", id?.number)
        assertEquals("87", id?.total)
    }

    @Test
    fun `preferisce la lettura pulita quando ci sono piu candidati`() {
        val id = CardIdParser.parse("0671087\n067/087")
        assertEquals("67", id?.number)
        assertEquals(0.95f, id!!.confidence, 0.001f)
    }

    // ── Sottoserie e promo ──────────────────────────────────────────

    @Test
    fun `tiene il prefisso delle sottoserie`() {
        val id = CardIdParser.parse("TG05/TG30")
        assertEquals("TG05", id?.number)
        assertEquals("30", id?.total)
    }

    @Test
    fun `legge le promo senza totale`() {
        val id = CardIdParser.parse("SVP 045")
        assertEquals("45", id?.number)
        assertNull(id?.total)
    }

    // ── Numeri che non sono un ID ───────────────────────────────────

    @Test
    fun `scarta il costo di ritirata`() {
        assertNull(CardIdParser.parse("2/2"))
    }

    @Test
    fun `scarta la riga di copyright`() {
        assertNull(CardIdParser.parse("©2024 Pokémon / Nintendo"))
    }

    @Test
    fun `scarta la riga degli HP`() {
        assertNull(CardIdParser.parse("HP 130"))
    }

    @Test
    fun `scarta le parole senza cifre`() {
        assertNull(CardIdParser.parse("Illus/Artist"))
    }

    @Test
    fun `non incolla le cifre nel testo dell intera carta`() {
        // Nel full-text un numero lungo puo' essere qualsiasi cosa: solo forme esplicite.
        assertNull(CardIdParserTestHelper.fullText("Danno 067087"))
    }

    @Test
    fun `nel testo pieno tiene l ultima occorrenza`() {
        val id = CardIdParserTestHelper.fullText("Attacco 30/60\nBase\n067/087")
        assertEquals("67", id?.number)
        assertEquals("87", id?.total)
    }
}

private object CardIdParserTestHelper {
    fun fullText(text: String) = CardIdParser.parseFromCardText(text)
}

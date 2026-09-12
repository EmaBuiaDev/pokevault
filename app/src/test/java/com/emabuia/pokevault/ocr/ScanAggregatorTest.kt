package com.emabuia.pokevault.ocr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Il caso che rompeva lo scanner in mano all'utente: la striscia con l'ID non
 * produce un numero a ogni frame, e con il conteggio a frame consecutivi quel
 * buco azzerava tutto, quindi non si arrivava mai alla conferma.
 */
class ScanAggregatorTest {

    private val aggregator = ScanAggregator()

    private fun reading(
        number: String? = null,
        total: String? = null,
        name: String? = null
    ) = CardReading(number = number, setTotal = total, name = name)

    // ── Il bug che l'utente ha visto ────────────────────────────────

    @Test
    fun `un frame senza ID non cancella i voti buoni`() {
        aggregator.record(reading(number = "67", total = "87", name = "Charizard ex"), 0)
        aggregator.record(reading(name = "Charizord ex"), 300)        // frame sporco, ID perso
        aggregator.record(reading(number = "67", total = "87", name = "Charizard ex"), 600)

        val consensus = aggregator.consensus(700)
        assertTrue("due letture dello stesso ID devono bastare", consensus!!.isReady)
        assertEquals("67", consensus.number)
        assertEquals("87", consensus.setTotal)
    }

    @Test
    fun `il nome non balla piu da un frame all altro`() {
        aggregator.record(reading(number = "67", name = "Charizard ex"), 0)
        aggregator.record(reading(number = "67", name = "Chorizard ex"), 250)
        aggregator.record(reading(number = "67", name = "Charizard ex"), 500)

        // Due letture su tre dicono "Charizard ex": vince la maggioranza, non l'ultima.
        assertEquals("Charizard ex", aggregator.consensus(600)?.name)
    }

    @Test
    fun `un totale letto male non blocca il numero`() {
        aggregator.record(reading(number = "67", total = "87"), 0)
        aggregator.record(reading(number = "67", total = "67"), 250)
        aggregator.record(reading(number = "67", total = "87"), 500)

        val consensus = aggregator.consensus(600)
        assertTrue(consensus!!.isReady)
        assertEquals("87", consensus.setTotal)
    }

    // ── Soglie ──────────────────────────────────────────────────────

    @Test
    fun `una sola lettura non basta`() {
        aggregator.record(reading(number = "67", total = "87", name = "Charizard ex"), 0)
        assertFalse(aggregator.consensus(100)!!.isReady)
    }

    @Test
    fun `un ID letto una volta basta se il nome lo conferma`() {
        // Caso tipico: la striscia in fondo esce pulita solo ogni tanto, mentre il
        // nome si legge quasi sempre. Aspettare il secondo ID costerebbe secondi.
        aggregator.record(reading(name = "Charizard ex"), 0)
        aggregator.record(reading(name = "Charizard ex"), 250)
        aggregator.record(reading(number = "67", total = "87", name = "Charizard ex"), 500)

        val consensus = aggregator.consensus(600)
        assertTrue(consensus!!.isReady)
        assertEquals("67", consensus.number)
        assertEquals(1, consensus.numberVotes)
    }

    @Test
    fun `un ID isolato senza nome non basta`() {
        aggregator.record(reading(number = "67", total = "87"), 0)
        aggregator.record(reading(number = "67", total = "87"), 250)
        aggregator.reset()

        aggregator.record(reading(number = "67", total = "87"), 500)
        aggregator.record(reading(name = "Charizard ex"), 750)
        assertFalse("un solo nome non conferma niente", aggregator.consensus(800)!!.isReady)
    }

    @Test
    fun `senza ID serve piu accordo sul nome`() {
        repeat(3) { index -> aggregator.record(reading(name = "Charizard ex"), index * 250L) }
        assertFalse("tre letture del solo nome non bastano", aggregator.consensus(800)!!.isReady)

        aggregator.record(reading(name = "Charizard ex"), 1000)
        val consensus = aggregator.consensus(1100)
        assertTrue("alla quarta si cerca per nome", consensus!!.isReady)
        assertNull(consensus.number)
        assertEquals("Charizard ex", consensus.name)
    }

    @Test
    fun `i voti scadono fuori dalla finestra`() {
        aggregator.record(reading(number = "67", total = "87"), 0)
        aggregator.record(reading(number = "67", total = "87"), 5_000)

        // La prima lettura e fuori finestra: resta un voto solo.
        assertFalse(aggregator.consensus(5_100)!!.isReady)
    }

    @Test
    fun `niente letture niente consenso`() {
        assertNull(aggregator.consensus(0))
        aggregator.record(CardReading(), 0)
        assertNull("una lettura vuota non va registrata", aggregator.consensus(100))
    }

    // ── Carte diverse nella stessa finestra ─────────────────────────

    @Test
    fun `la carta piu letta vince su quella intravista`() {
        aggregator.record(reading(number = "67", total = "87", name = "Charizard ex"), 0)
        aggregator.record(reading(number = "12", total = "191", name = "Pikachu"), 250)
        aggregator.record(reading(number = "67", total = "87", name = "Charizard ex"), 500)

        val consensus = aggregator.consensus(600)
        assertEquals("67", consensus?.number)
        assertEquals("Charizard ex", consensus?.name)
    }

    @Test
    fun `il totale di un altra carta non entra nel consenso`() {
        aggregator.record(reading(number = "67", total = "87"), 0)
        aggregator.record(reading(number = "12", total = "191"), 250)
        aggregator.record(reading(number = "67", total = "87"), 500)

        // 191 e il totale della carta scartata: non deve sopravvivere.
        assertEquals("87", aggregator.consensus(600)?.setTotal)
    }

    @Test
    fun `il reset azzera tutto`() {
        aggregator.record(reading(number = "67", total = "87"), 0)
        aggregator.record(reading(number = "67", total = "87"), 250)
        aggregator.reset()
        assertNull(aggregator.consensus(300))
    }

    // ── Suggerimento di inquadratura ────────────────────────────────

    @Test
    fun `segnala quando l ID non si legge mai`() {
        repeat(3) { index -> aggregator.record(reading(name = "Charizard ex"), index * 250L) }
        assertTrue(aggregator.hasNoIdInWindow(800))

        aggregator.record(reading(number = "67", total = "87"), 1000)
        assertFalse(aggregator.hasNoIdInWindow(1100))
    }

    @Test
    fun `la chiave di ricerca non cambia a parita di consenso`() {
        aggregator.record(reading(number = "67", total = "87", name = "Charizard ex"), 0)
        aggregator.record(reading(number = "67", total = "87", name = "Charizard ex"), 250)
        val first = aggregator.consensus(300)!!.searchKey

        aggregator.record(reading(number = "67", total = "87", name = "Charizard ex"), 500)
        assertEquals(first, aggregator.consensus(600)!!.searchKey)
    }
}

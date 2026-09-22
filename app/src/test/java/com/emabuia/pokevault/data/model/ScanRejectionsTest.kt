package com.emabuia.pokevault.data.model

import org.junit.Assert.*
import org.junit.Test

/**
 * Gli scarti dello scanner.
 *
 * Il caso da cui nasce: scarti per sbaglio la carta giusta, la rimetti davanti,
 * e lo scanner non te la propone piu'.
 */
class ScanRejectionsTest {

    @Test
    fun `nessuna di queste fa proporre le carte successive`() {
        val r = ScanRejections()
        r.onNumberRead("57/198")
        r.reject(listOf("a", "b", "c"))
        assertEquals(listOf("d", "e"), r.viable(listOf("a", "b", "c", "d", "e")) { it })
    }

    /** Il bug segnalato. */
    @Test
    fun `tolta la carta dall'inquadratura, quando torna e' di nuovo proponibile`() {
        val r = ScanRejections()
        r.onNumberRead("57/198")
        r.reject(listOf("giusta"))
        r.onCardRemoved()
        r.onNumberRead("57/198")
        assertFalse(r.isRejected("giusta"))
    }

    @Test
    fun `lo scarto si annulla`() {
        val r = ScanRejections()
        r.onNumberRead("57/198")
        r.reject(listOf("giusta"))
        assertEquals(setOf("giusta"), r.undoLast())
        assertFalse(r.isRejected("giusta"))
    }

    @Test
    fun `annullare tocca solo l'ultimo scarto, non quelli prima`() {
        val r = ScanRejections()
        r.onNumberRead("57/198")
        r.reject(listOf("a", "b", "c"))
        r.reject(listOf("d"))
        r.undoLast()
        assertTrue(r.isRejected("a"))
        assertFalse(r.isRejected("d"))
    }

    @Test
    fun `annullare due volte non rimette niente in piu'`() {
        val r = ScanRejections()
        r.onNumberRead("1/100")
        r.reject(listOf("a"))
        r.reject(listOf("b"))
        r.undoLast()
        assertEquals(emptySet<String>(), r.undoLast())
        assertTrue(r.isRejected("a"))
    }

    @Test
    fun `un numero diverso e' una carta diversa`() {
        val r = ScanRejections()
        r.onNumberRead("57/198")
        r.reject(listOf("a"))
        r.onNumberRead("58/198")
        assertFalse(r.isRejected("a"))
    }

    @Test
    fun `un frame senza numero non cancella gli scarti`() {
        val r = ScanRejections()
        r.onNumberRead("57/198")
        r.reject(listOf("a"))
        r.onNumberRead(null)
        r.onNumberRead("")
        assertTrue(r.isRejected("a"))
    }

    @Test
    fun `scartare di nuovo una carta gia' scartata non la conta come nuovo scarto`() {
        val r = ScanRejections()
        r.onNumberRead("57/198")
        r.reject(listOf("a"))
        r.reject(listOf("a"))
        assertEquals(emptySet<String>(), r.undoLast())
        assertTrue(r.isRejected("a"))
    }
}

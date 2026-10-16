package com.emabuia.pokevault.ui.pokedex

import com.emabuia.pokevault.data.remote.TcgSet
import com.emabuia.pokevault.viewmodel.SeriesSetsGroup
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * "Apri tutte" / "Chiudi tutte" del Pokedex, e il conto delle righe della
 * griglia che tiene ferma la vista sulla serie che si stava guardando.
 */
class SeriesToggleAllTest {

    private fun group(key: String, sets: Int) =
        SeriesSetsGroup(seriesKey = key, seriesLabel = key, sets = List(sets) { TcgSet(id = "$key-$it") })

    // Righe: SV = 0..3 (intestazione + 3), SWSH = 4..6, SM = 7..8
    private val groups = listOf(group("SV", 3), group("SWSH", 2), group("SM", 1))
    private val all = setOf("SV", "SWSH", "SM")

    @Test
    fun `tutto aperto si chiude tutto`() {
        assertEquals(all, toggleAllSeries(emptySet(), all))
    }

    @Test
    fun `basta una serie chiusa e si riapre tutto`() {
        assertEquals(emptySet<String>(), toggleAllSeries(setOf("SWSH"), all))
    }

    @Test
    fun `le serie dell'altra lingua restano come erano`() {
        assertEquals(all + "EN-XY", toggleAllSeries(setOf("EN-XY"), all))
        assertEquals(setOf("EN-XY"), toggleAllSeries(all + "EN-XY", all))
    }

    @Test
    fun `la riga di un'espansione appartiene alla sua serie`() {
        assertEquals(0, seriesIndexAtGridItem(0, groups, emptySet()))
        assertEquals(0, seriesIndexAtGridItem(3, groups, emptySet()))
        assertEquals(1, seriesIndexAtGridItem(4, groups, emptySet()))
        assertEquals(1, seriesIndexAtGridItem(6, groups, emptySet()))
        assertEquals(2, seriesIndexAtGridItem(8, groups, emptySet()))
    }

    @Test
    fun `una serie chiusa occupa solo la sua intestazione`() {
        // SV chiusa: SV = 0, SWSH = 1..3, SM = 4..5
        assertEquals(1, seriesIndexAtGridItem(1, groups, setOf("SV")))
        assertEquals(2, seriesIndexAtGridItem(4, groups, setOf("SV")))
    }

    @Test
    fun `oltre la fine resta l'ultima serie`() {
        assertEquals(2, seriesIndexAtGridItem(99, groups, emptySet()))
    }

    @Test
    fun `dopo Chiudi tutte l'intestazione di una serie e' alla sua posizione`() {
        assertEquals(1, seriesHeaderGridIndex(groups, all, "SWSH"))
        assertEquals(4, seriesHeaderGridIndex(groups, emptySet(), "SWSH"))
        assertEquals(-1, seriesHeaderGridIndex(groups, all, "XY"))
    }
}

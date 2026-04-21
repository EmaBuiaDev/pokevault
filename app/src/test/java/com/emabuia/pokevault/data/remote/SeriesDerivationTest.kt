package com.emabuia.pokevault.data.remote

import org.junit.Assert.assertEquals
import org.junit.Test

class SeriesDerivationTest {

    private val repository = PokeTcgRepository()

    @Test
    fun `maps mega evolution code pfl to mega evoluzione`() {
        val series = repository.deriveSeriesName(
            setCode = "PFL",
            language = "ita",
            setName = "Fiamme Spettrali"
        )

        assertEquals("Mega Evoluzione", series)
    }

    @Test
    fun `maps mega evolution code asc to mega evoluzione`() {
        val series = repository.deriveSeriesName(
            setCode = "ASC",
            language = "ita",
            setName = "Ascesa Eroica"
        )

        assertEquals("Mega Evoluzione", series)
    }

    @Test
    fun `maps me03 perfect order to mega evoluzione`() {
        val series = repository.deriveSeriesName(
            setCode = "ME03",
            language = "ita",
            setName = "Perfect Order"
        )

        assertEquals("Mega Evoluzione", series)
    }

    @Test
    fun `maps dri alias to scarlet and violet`() {
        val series = repository.deriveSeriesName(
            setCode = "DRI",
            language = "ita",
            setName = "Rivali Predestinati"
        )

        assertEquals("Scarlatto e Violetto", series)
    }

    @Test
    fun `maps white flare to scarlet and violet`() {
        val series = repository.deriveSeriesName(
            setCode = "WHT",
            language = "ita",
            setName = "White Flare"
        )

        assertEquals("Scarlatto e Violetto", series)
    }

    @Test
    fun `maps sv11 code to scarlet and violet`() {
        val series = repository.deriveSeriesName(
            setCode = "SV11",
            language = "ita",
            setName = "Luce Nera"
        )

        assertEquals("Scarlatto e Violetto", series)
    }

    @Test
    fun `keeps ssp in scarlet and violet`() {
        val series = repository.deriveSeriesName(
            setCode = "SSP",
            language = "ita",
            setName = "Scintille Folgoranti"
        )

        assertEquals("Scarlatto e Violetto", series)
    }

    @Test
    fun `maps evolution collection group`() {
        val series = repository.deriveSeriesName(
            setCode = "EVOC",
            language = "eng",
            setName = "Evolutions Collection 2026"
        )

        assertEquals("EvolutionCollection", series)
    }

    @Test
    fun `maps play pokemon prize packs group`() {
        val series = repository.deriveSeriesName(
            setCode = "PPS7",
            language = "eng",
            setName = "Play! Pokemon Prize Pack Series 7"
        )

        assertEquals("PlayPokemon", series)
    }

    @Test
    fun `maps trick or treat group`() {
        val series = repository.deriveSeriesName(
            setCode = "TOT24",
            language = "eng",
            setName = "Trick or Trade 2024"
        )

        assertEquals("TrickOrTreat", series)
    }

    @Test
    fun `maps gym challenge to gym`() {
        val series = repository.deriveSeriesName(
            setCode = "G2",
            language = "eng",
            setName = "Gym Challenge"
        )

        assertEquals("Gym", series)
    }

    @Test
    fun `maps neo destiny to neo`() {
        val series = repository.deriveSeriesName(
            setCode = "N4",
            language = "eng",
            setName = "Neo Destiny"
        )

        assertEquals("Neo", series)
    }

    @Test
    fun `maps code sv to platinum not scarlet and violet`() {
        val series = repository.deriveSeriesName(
            setCode = "SV",
            language = "eng",
            setName = "Supreme Victors"
        )

        assertEquals("Platinum", series)
    }

    @Test
    fun `maps code ex expedition to ecard`() {
        val series = repository.deriveSeriesName(
            setCode = "EX",
            language = "eng",
            setName = "Expedition Base Set"
        )

        assertEquals("e-Card", series)
    }

    @Test
    fun `maps world championships by name`() {
        val series = repository.deriveSeriesName(
            setCode = "",
            language = "eng",
            setName = "World Championships Deck 2024"
        )

        assertEquals("Campionati Mondiali", series)
    }

    @Test
    fun `unknown set code falls back to altro`() {
        val series = repository.deriveSeriesName(
            setCode = "ZZZ",
            language = "ita",
            setName = "Espansione Sconosciuta"
        )

        assertEquals("Altro", series)
    }
}

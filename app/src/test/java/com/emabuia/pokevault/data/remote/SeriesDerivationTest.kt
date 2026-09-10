package com.emabuia.pokevault.data.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SeriesDerivationTest {

    private val repository = CatalogRepository()

    @Test
    fun `chaos rising cri code classifies as mega evolutions`() {
        val series = repository.deriveSeriesName(
            setCode = "CRI",
            language = null,
            setName = "Chaos Rising"
        )

        assertEquals("Mega Evoluzioni", series)
    }

    @Test
    fun `abyss eye m5 code classifies as mega evolutions`() {
        val series = repository.deriveSeriesName(
            setCode = "m5",
            language = null,
            setName = "Abyss Eye"
        )

        assertEquals("Mega Evoluzioni", series)
    }

    @Test
    fun `infer english language for chaos rising with missing language`() {
        val inferred = repository.inferEnglishLanguageFallback(
            setCode = "CRI",
            setName = "Chaos Rising"
        )

        assertEquals("ENG", inferred)
    }

    @Test
    fun `infer english language for abyss eye m5 with missing language`() {
        val inferred = repository.inferEnglishLanguageFallback(
            setCode = "m5",
            setName = "Abyss Eye"
        )

        assertEquals("ENG", inferred)
    }

    @Test
    fun `do not infer english language for unrelated unknown set`() {
        val inferred = repository.inferEnglishLanguageFallback(
            setCode = "ZZZ",
            setName = "Some Random Set"
        )

        assertNull(inferred)
    }

    @Test
    fun `maps mega evolution code pfl to mega evoluzione`() {
        val series = repository.deriveSeriesName(
            setCode = "PFL",
            language = "ita",
            setName = "Fiamme Spettrali"
        )

        assertEquals("Mega Evoluzioni", series)
    }

    @Test
    fun `maps mega evolution code asc to mega evoluzione`() {
        val series = repository.deriveSeriesName(
            setCode = "ASC",
            language = "ita",
            setName = "Ascesa Eroica"
        )

        assertEquals("Mega Evoluzioni", series)
    }

    @Test
    fun `maps me03 perfect order to mega evoluzione`() {
        val series = repository.deriveSeriesName(
            setCode = "ME03",
            language = "ita",
            setName = "Perfect Order"
        )

        assertEquals("Mega Evoluzioni", series)
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
    fun `maps black bolt alias to scarlet and violet`() {
        val series = repository.deriveSeriesName(
            setCode = "BLK",
            language = "ita",
            setName = "Luce Nera"
        )

        assertEquals("Scarlatto e Violetto", series)
    }

    @Test
    fun `maps white flare alias to scarlet and violet`() {
        val series = repository.deriveSeriesName(
            setCode = "WHT",
            language = "ita",
            setName = "Fuoco Bianco"
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
    fun `maps evolution collection group to altro`() {
        val series = repository.deriveSeriesName(
            setCode = "EVOC",
            language = "eng",
            setName = "Evolutions Collection 2026"
        )

        assertEquals("Altro", series)
    }

    @Test
    fun `maps play pokemon prize packs group to altro`() {
        val series = repository.deriveSeriesName(
            setCode = "PPS7",
            language = "eng",
            setName = "Play! Pokemon Prize Pack Series 7"
        )

        assertEquals("Altro", series)
    }

    @Test
    fun `maps trick or treat group to altro`() {
        val series = repository.deriveSeriesName(
            setCode = "TOT24",
            language = "eng",
            setName = "Trick or Trade 2024"
        )

        assertEquals("Altro", series)
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
    fun `does not map m23 promo code to mega evolutions`() {
        val series = repository.deriveSeriesName(
            setCode = "M23",
            language = "eng",
            setName = "McDonald's Promos 2023"
        )

        assertEquals("Altro", series)
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
    fun `maps world championships by name to altro`() {
        val series = repository.deriveSeriesName(
            setCode = "",
            language = "eng",
            setName = "World Championships Deck 2024"
        )

        assertEquals("Altro", series)
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

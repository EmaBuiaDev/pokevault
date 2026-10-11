package com.emabuia.pokevault.ui.pokedex

import com.emabuia.pokevault.data.remote.SetImages
import com.emabuia.pokevault.data.remote.TcgSet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Il badge dell'espansione sulle tessere della ricerca: da dove viene il
 * simbolo e quando si scrive la sigla.
 */
class SetBadgeTest {

    private fun set(
        id: String = "ita-sv4pt5",
        logo: String = "https://api.example/sets/PAF/image?v=3&source=ita",
        abbreviation: String = "PAF",
        releaseDate: String = "2024-01-26"
    ) = TcgSet(
        id = id,
        releaseDate = releaseDate,
        images = SetImages(symbol = logo, logo = logo),
        abbreviation = abbreviation
    )

    @Test
    fun `il simbolo sta nella cartella del logo, senza i parametri del logo`() {
        assertEquals("https://api.example/sets/PAF/symbol", SetBadge.symbolUrlFor(set()))
    }

    @Test
    fun `un logo che non passa dal Worker non ha simbolo`() {
        assertNull(SetBadge.symbolUrlFor(set(logo = "https://images.pokemontcg.io/sv4pt5/logo.png")))
        assertNull(SetBadge.symbolUrlFor(set(logo = "")))
    }

    @Test
    fun `da Scarlatto e Violetto il simbolo e' gia' la sigla, e non si ripete`() {
        val badge = SetBadge.forSet(set())!!
        assertTrue(badge.symbolHasLetters)
        assertFalse(badge.showsAbbreviation(symbolLoaded = true))
    }

    @Test
    fun `prima di Scarlatto e Violetto simbolo e sigla insieme`() {
        val badge = SetBadge.forSet(set(abbreviation = "SSH", releaseDate = "2020-02-07"))!!
        assertFalse(badge.symbolHasLetters)
        assertTrue(badge.showsAbbreviation(symbolLoaded = true))
    }

    @Test
    fun `se il simbolo non si carica resta la sigla, anche per i set recenti`() {
        val badge = SetBadge.forSet(set())!!
        assertTrue(badge.showsAbbreviation(symbolLoaded = false))
    }

    @Test
    fun `senza sigla si mostra solo il simbolo, senza simbolo ne' sigla niente badge`() {
        val onlySymbol = SetBadge.forSet(set(abbreviation = "", releaseDate = "2020-02-07"))!!
        assertFalse(onlySymbol.showsAbbreviation(symbolLoaded = true))
        assertFalse(onlySymbol.showsAbbreviation(symbolLoaded = false))

        assertNull(SetBadge.forSet(set(logo = "", abbreviation = "")))
    }

    @Test
    fun `una data sconosciuta non vale come set recente`() {
        val badge = SetBadge.forSet(set(abbreviation = "XYP", releaseDate = ""))!!
        assertFalse(badge.symbolHasLetters)
        assertTrue(badge.showsAbbreviation(symbolLoaded = true))
    }

    @Test
    fun `per id dell'espansione, saltando quelle senza niente da mostrare`() {
        val byId = SetBadge.byId(
            listOf(
                set(id = "ita-sv4pt5"),
                set(id = "ita-vuoto", logo = "", abbreviation = "")
            )
        )
        assertEquals(setOf("ita-sv4pt5"), byId.keys)
    }
}

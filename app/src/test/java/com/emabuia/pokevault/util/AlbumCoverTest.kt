package com.emabuia.pokevault.util

import com.emabuia.pokevault.data.model.Album
import com.emabuia.pokevault.data.model.AlbumCover
import com.emabuia.pokevault.util.PokemonSpriteResolver.Species
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * La copertina disegnata degli album: motivo e Pokemon cercato per nome.
 * Gli album di prima (motivo vuoto) devono restare con le loro carte.
 */
class AlbumCoverTest {

    private val species = listOf(
        Species(4, "Charmander"),
        Species(5, "Charmeleon"),
        Species(6, "Charizard"),
        Species(122, "Mr Mime"),
        Species(669, "Flabébé"),
        Species(984, "Great Tusk"),
        Species(984, "Grandizanne"),
        Species(1007, "Koraidon")
    )

    @Test
    fun `un album di prima non ha copertina disegnata`() {
        assertFalse(AlbumCover.isDesigned(Album().coverPattern))
        assertFalse(AlbumCover.isDesigned("arcobaleno"))
        AlbumCover.PATTERNS.forEach { assertTrue(it, AlbumCover.isDesigned(it)) }
    }

    @Test
    fun `l'artwork esiste solo per un Pokemon scelto`() {
        assertNull(AlbumCover.artworkUrl(0))
        assertEquals(
            "https://raw.githubusercontent.com/PokeAPI/sprites/master/sprites/pokemon/other/official-artwork/6.png",
            AlbumCover.artworkUrl(6)
        )
    }

    @Test
    fun `prima chi inizia col testo, poi chi lo contiene`() {
        val found = PokemonSpriteResolver.searchSpecies(species, "char")
        assertEquals(listOf(4, 5, 6), found.map { it.dex })
        assertEquals(listOf("Koraidon"), PokemonSpriteResolver.searchSpecies(species, "aido").map { it.name })
    }

    @Test
    fun `accenti, maiuscole e punteggiatura non contano`() {
        assertEquals(listOf(669), PokemonSpriteResolver.searchSpecies(species, "flabebe").map { it.dex })
        assertEquals(listOf(122), PokemonSpriteResolver.searchSpecies(species, "MR. MIME").map { it.dex })
    }

    @Test
    fun `i Paradosso si trovano anche col nome italiano`() {
        assertEquals(listOf("Grandizanne"), PokemonSpriteResolver.searchSpecies(species, "grandiz").map { it.name })
        assertEquals(984, PokemonSpriteResolver.searchSpecies(species, "great").single().dex)
    }

    @Test
    fun `testo vuoto o senza lettere non trova niente, e il limite vale`() {
        assertTrue(PokemonSpriteResolver.searchSpecies(species, "  ").isEmpty())
        assertTrue(PokemonSpriteResolver.searchSpecies(species, "--").isEmpty())
        assertEquals(2, PokemonSpriteResolver.searchSpecies(species, "char", limit = 2).size)
    }

    @Test
    fun `i nomi della tabella diventano leggibili`() {
        assertEquals("Mr Mime", PokemonSpriteResolver.displayName("mr-mime"))
        assertEquals("Iron Valiant", PokemonSpriteResolver.displayName("iron-valiant"))
        assertEquals("Grandizanne", PokemonSpriteResolver.displayName("grandizanne"))
    }

    @Test
    fun `la riga della lista nasce senza copertina disegnata`() {
        val row = AlbumRow(
            id = "a", name = "a", description = "", theme = "fire", pokemonType = "", coverUrl = "",
            previewUrls = emptyList(), used = 0, size = 9, value = 0.0, createdAtSeconds = 0L
        )
        assertEquals("", row.coverPattern)
        assertEquals(0, row.coverPokemon)
    }
}

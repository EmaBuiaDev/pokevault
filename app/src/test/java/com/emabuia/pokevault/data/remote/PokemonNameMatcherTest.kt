package com.emabuia.pokevault.data.remote

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PokemonNameMatcherTest {

    @Test
    fun `forme e possessivi tradotti restano la stessa specie`() {
        assertTrue(PokemonNameMatcher.sameSpecies("Teal Mask Ogerpon ex", "Ogerpon Maschera Turchese-ex"))
        assertTrue(PokemonNameMatcher.sameSpecies("Wellspring Mask Ogerpon ex", "Ogerpon Maschera Pozzo-ex"))
        assertTrue(PokemonNameMatcher.sameSpecies("Lillie's Clefairy ex", "Clefairy-ex di Lylia"))
        assertTrue(PokemonNameMatcher.sameSpecies("Flabébé", "Flabebe"))
    }

    @Test
    fun `i Paradosso hanno la specie tradotta`() {
        assertTrue(PokemonNameMatcher.sameSpecies("Raging Bolt ex", "Furiatonante-ex"))
        assertTrue(PokemonNameMatcher.sameSpecies("Iron Hands ex", "Manoferrea-ex"))
        assertTrue(PokemonNameMatcher.sameSpecies("Flutter Mane", "Crinealato"))
        assertFalse(PokemonNameMatcher.sameSpecies("Iron Hands ex", "Capoferreo-ex"))
        assertFalse(PokemonNameMatcher.sameSpecies("Raging Bolt ex", "Acquecrespe-ex"))
    }

    @Test
    fun `specie diverse non combaciano nemmeno se condividono la meccanica`() {
        assertFalse(PokemonNameMatcher.sameSpecies("Kadabra", "Treecko"))
        assertFalse(PokemonNameMatcher.sameSpecies("Iron Hands ex", "Charizard-ex"))
        assertFalse(PokemonNameMatcher.sameSpecies("Mew V", "Mewtwo V"))
        // Stesso proprietario, specie diversa.
        assertFalse(PokemonNameMatcher.sameSpecies("Hop's Wooloo", "Dubwool di Hop"))
        assertFalse(PokemonNameMatcher.sameSpecies("Team Rocket's Zubat", "Golbat del Team Rocket"))
    }

    @Test
    fun `nome vuoto non combacia con niente`() {
        assertFalse(PokemonNameMatcher.sameSpecies("", "Pikachu"))
        assertFalse(PokemonNameMatcher.sameSpecies(null, "Pikachu"))
    }
}

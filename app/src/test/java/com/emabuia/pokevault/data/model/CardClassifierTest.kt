package com.emabuia.pokevault.data.model

import org.junit.Assert.assertEquals
import org.junit.Test

class CardClassifierTest {

    private fun card(
        name: String = "",
        supertype: String = "",
        type: String = "",
        subtypes: List<String> = emptyList(),
        hp: Int = 0
    ) = PokemonCard(
        name = name,
        supertype = supertype,
        type = type,
        subtypes = subtypes,
        hp = hp
    )

    // --- Il bug che ha motivato l'unificazione ---------------------------------

    @Test
    fun `trainer whose name contains energy is not classified as energy`() {
        val trainers = listOf(
            "Energy Retrieval",
            "Energy Switch",
            "Energy Search",
            "Superior Energy Retrieval"
        )

        trainers.forEach { name ->
            val result = CardClassifier.classify(
                card(name = name, supertype = "Trainer", subtypes = listOf("Item"))
            )
            assertEquals("$name deve restare Trainer", CardClassifier.TRAINER, result)
        }
    }

    @Test
    fun `italian trainer whose name contains energia is not classified as energy`() {
        val result = CardClassifier.classify(
            card(name = "Recupero Energia", supertype = "Trainer", subtypes = listOf("Strumento"))
        )

        assertEquals(CardClassifier.TRAINER, result)
    }

    // --- Supertype autorevole --------------------------------------------------

    @Test
    fun `explicit energy supertype wins`() {
        assertEquals(
            CardClassifier.ENERGY,
            CardClassifier.classify(card(name = "Fire Energy", supertype = "Energy"))
        )
    }

    @Test
    fun `explicit trainer supertype wins over pokemon type marker`() {
        assertEquals(
            CardClassifier.TRAINER,
            CardClassifier.classify(card(name = "Boss's Orders", supertype = "Trainer", type = "fire"))
        )
    }

    @Test
    fun `pokemon with hp and type is classified as pokemon`() {
        assertEquals(
            CardClassifier.POKEMON,
            CardClassifier.classify(
                card(name = "Charizard ex", supertype = "Pokémon", type = "fire", subtypes = listOf("Basic"), hp = 330)
            )
        )
    }

    // --- Il default supertype = "Pokémon" non deve mascherare i Trainer --------

    @Test
    fun `legacy record with default pokemon supertype is trainer when subtype says item`() {
        // PokemonCard.supertype vale "Pokémon" di default, quindi moltissimi
        // record storici lo riportano anche per i Trainer: non e' autorevole.
        val result = CardClassifier.classify(
            card(name = "Ultra Ball", supertype = "Pokémon", subtypes = listOf("Item"))
        )

        assertEquals(CardClassifier.TRAINER, result)
    }

    @Test
    fun `legacy record with default pokemon supertype is energy when type says energy`() {
        val result = CardClassifier.classify(
            card(name = "Double Turbo", supertype = "Pokémon", type = "Energy")
        )

        assertEquals(CardClassifier.ENERGY, result)
    }

    @Test
    fun `default pokemon supertype with no contrary marker stays pokemon`() {
        assertEquals(
            CardClassifier.POKEMON,
            CardClassifier.classify(card(name = "Qualcosa", supertype = "Pokémon"))
        )
    }

    // --- Euristiche di ultima risorsa ------------------------------------------

    @Test
    fun `hand written card with only a name falls back to energy heuristic`() {
        assertEquals(
            CardClassifier.ENERGY,
            CardClassifier.classify(card(name = "Energia Base Fuoco"))
        )
    }

    @Test
    fun `hp alone is enough to identify a pokemon`() {
        assertEquals(
            CardClassifier.POKEMON,
            CardClassifier.classify(card(name = "Pikachu", hp = 60))
        )
    }

    @Test
    fun `empty card falls back to trainer`() {
        assertEquals(CardClassifier.TRAINER, CardClassifier.classify(card()))
    }

    // --- Coerenza fra i punti di chiamata --------------------------------------

    @Test
    fun `PokemonCard classify delegates to the shared classifier`() {
        val subject = card(name = "Energy Retrieval", supertype = "Trainer", subtypes = listOf("Item"))

        assertEquals(CardClassifier.classify(subject), subject.classify())
    }
}

package com.emabuia.pokevault.ui.competitive

import com.emabuia.pokevault.data.model.PokemonCard
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Le carte che pescano o cercano, riconosciute nei nomi italiani della
 * collezione. Prima la lista era solo in inglese: Minor Ball & co. non
 * contavano, e iniziando per primi il simulatore vedeva mani senza "out".
 */
class HandSimulatorOutCardsTest {

    private fun trainer(name: String, subtype: String) = PokemonCard(
        id = name,
        name = name,
        supertype = "Trainer",
        subtypes = listOf(subtype)
    )

    @Test
    fun `gli Strumenti italiani che cercano sono out`() {
        listOf(
            "Minor Ball", "Ultra Ball", "Poffin del Cuore", "Anfora Terrestre",
            "Velox Ball", "Superpass VIP Lotta", "Peso Ball di Hisui", "Pokégear 3.0"
        ).forEach { name ->
            assertTrue(name, trainer(name, "Item").isOutCard())
        }
    }

    @Test
    fun `gli Aiuti sono out anche senza nome in lista`() {
        assertTrue(trainer("Kissara", "Supporter").isOutCard())
        assertTrue(trainer("Pepe", "Aiuto").isOutCard())
    }

    @Test
    fun `uno Strumento che non cerca non e' out`() {
        assertFalse(trainer("Caramella Rara", "Item").isOutCard())
        assertFalse(trainer("Ordini del Capo", "Item").isOutCard())
    }

    @Test
    fun `Rubra e Carmine si giocano al primo turno anche iniziando, gli altri Aiuti no`() {
        assertTrue(trainer("Rubra", "Supporter").isPlayableFirstTurnGoingFirst())
        assertTrue(trainer("Carmine", "Supporter").isPlayableFirstTurnGoingFirst())
        assertFalse(trainer("Kissara", "Supporter").isPlayableFirstTurnGoingFirst())
        // Solo un Aiuto: uno Strumento con quel nome non esiste, ma non deve passare.
        assertFalse(trainer("Rubra", "Item").isPlayableFirstTurnGoingFirst())
    }

    @Test
    fun `la carta in mazzo porta con se' l'eccezione di Rubra`() {
        val rubra = trainer("Rubra", "Supporter")
        val pool = buildDeckCardPool(
            com.emabuia.pokevault.data.model.Deck(id = "d", name = "d", cards = listOf("Rubra")),
            listOf(rubra)
        )
        assertTrue(pool.single().playableFirstTurnGoingFirst)
        assertTrue(pool.single().isSupporter)
    }
}

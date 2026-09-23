package com.emabuia.pokevault.ui.competitive

import com.emabuia.pokevault.data.model.Deck
import com.emabuia.pokevault.data.model.PokemonCard
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * "Basic" nel simulatore non e' un'etichetta: e' la risposta alla domanda
 * "posso calare questa carta in campo?". Da li' dipendono mulligan, starter
 * rate e la riga "N Basic" sotto la mano.
 */
class HandSimulatorDeckMappingTest {

    private fun pokemon(name: String, subtypes: List<String>, hp: Int = 90) = PokemonCard(
        id = name,
        name = name,
        supertype = "Pokémon",
        subtypes = subtypes,
        hp = hp
    )

    // --- Il bug: una Fase 1 contata come Base -------------------------------

    @Test
    fun `gli stadi evolutivi italiani non sono Base`() {
        val evolutions = listOf(
            "Fase 1", "Fase 2", "fase1", "Stadio 1", "Stadio 2", "Evoluzione"
        )
        evolutions.forEach { stage ->
            assertFalse(stage, pokemon("Evoluto", listOf(stage)).isBasicPokemon())
        }
    }

    @Test
    fun `gli stadi evolutivi inglesi non sono Base in nessuna scrittura`() {
        val evolutions = listOf(
            // "Stage1"/"Stage2" sono la grafia canonica che scrive il nostro
            // catalogo (schema/009); le altre coprono i dati gia' in giro.
            "Stage1", "Stage2", "Stage 1", "Stage 2", "STAGE-1", "stage2",
            "Livello 1", "Livello 2", "VMAX", "V-MAX", "VSTAR",
            "V-UNION", "Mega Evolution", "BREAK", "LV.X", "Restored"
        )
        evolutions.forEach { stage ->
            assertFalse(stage, pokemon("Evoluto", listOf(stage)).isBasicPokemon())
        }
    }

    @Test
    fun `lo stadio evolutivo decide anche accanto ad altri sottotipi`() {
        assertFalse(pokemon("Charizard ex", listOf("Stage 2", "ex")).isBasicPokemon())
    }

    // --- Quello che resta Base ---------------------------------------------

    @Test
    fun `i marcatori espliciti di carta Base valgono nelle due lingue`() {
        listOf("Basic", "Base", "BASIC").forEach { stage ->
            assertTrue(stage, pokemon("Pikachu", listOf(stage)).isBasicPokemon())
        }
    }

    @Test
    fun `senza stadio nei dati la carta resta contata come Base`() {
        assertTrue(pokemon("Sconosciuto", emptyList()).isBasicPokemon())
        assertTrue(pokemon("Ultra Beast", listOf("ex")).isBasicPokemon())
    }

    @Test
    fun `una carta senza punti vita non e' un Pokemon in campo`() {
        assertFalse(pokemon("Ultra Ball", listOf("Item"), hp = 0).isBasicPokemon())
    }

    // --- L'avviso che accompagna la stima -----------------------------------

    @Test
    fun `lo stadio non riconosciuto viene segnalato invece che nascosto`() {
        val cards = listOf(
            pokemon("Base nota", listOf("Basic")),
            pokemon("Fase 1 nota", listOf("Fase 1")),
            pokemon("Stadio ignoto", listOf("ex")),
            pokemon("Nessun sottotipo", emptyList())
        )
        val deck = Deck(id = "d", name = "d", cards = cards.map { it.id })

        val warnings = deckAccuracyWarnings(deck, cards)

        // Due avvisi: il mazzo non e' da 60 e due carte non dicono lo stadio.
        assertEquals(2, warnings.size)
        assertTrue(warnings.any { it.contains("2") })
    }
}

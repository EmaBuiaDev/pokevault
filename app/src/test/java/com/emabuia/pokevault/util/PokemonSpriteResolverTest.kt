package com.emabuia.pokevault.util

import android.app.Application
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Il nome di una carta non e' il nome di una specie, e la distanza fra i due
 * e' tutta fatta di casi particolari: suffissi di stampa, forme regionali,
 * apostrofi, accenti, trattini. Sbagliarne uno non rompe niente -- lo sprite
 * semplicemente non compare -- ed e' proprio per questo che va verificato qui:
 * a schermo non si distingue un Pokemon senza sprite da un Pokemon che il
 * confronto non ha saputo riconoscere.
 *
 * Gira su Robolectric perche' la tabella e' un asset, e leggerla e' meta' del
 * lavoro che si vuole verificare.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class PokemonSpriteResolverTest {

    private val context get() = RuntimeEnvironment.getApplication()

    private fun dex(name: String) = PokemonSpriteResolver.dexNumberForCardName(context, name)

    @Test
    fun testNomeSemplice() {
        assertEquals(6, dex("Charizard"))
        assertEquals(95, dex("Onix"))
    }

    @Test
    fun testSuffissiDiStampa() {
        assertEquals(6, dex("Charizard ex"))
        assertEquals(169, dex("Crobat V"))
        assertEquals(151, dex("Mew VMAX"))
        assertEquals(6, dex("Charizard VSTAR"))
        assertEquals(6, dex("Charizard-GX"))
    }

    @Test
    fun testPrefissiDiForma() {
        assertEquals(6, dex("M Charizard EX"))
        assertEquals(6, dex("Radiant Charizard"))
        assertEquals(6, dex("Dark Charizard"))
    }

    /** Le carte vecchie intestate a un personaggio: "Brock's Onix". */
    @Test
    fun testCarteDiUnPersonaggio() {
        assertEquals(95, dex("Brock's Onix"))
        assertEquals(130, dex("Team Aqua's Gyarados"))
    }

    /**
     * I nomi che contengono punteggiatura devono combaciare con la forma
     * a trattini di PokeAPI: "Mr. Mime" e "mr-mime" sono lo stesso Pokemon.
     */
    @Test
    fun testPunteggiatura() {
        assertEquals(122, dex("Mr. Mime"))
        assertEquals(250, dex("Ho-Oh"))
        assertEquals(474, dex("Porygon-Z"))
        assertEquals(772, dex("Type: Null"))
    }

    @Test
    fun testAccenti() {
        assertEquals(669, dex("Flabébé"))
        assertEquals(669, dex("Flabebe"))
    }

    /**
     * Nomi di due parole: non devono essere smontati prima di essere provati
     * interi, o "Iron Valiant" diventerebbe "Valiant" e non troverebbe nulla.
     */
    @Test
    fun testNomiDiDueParole() {
        assertEquals(1006, dex("Iron Valiant ex"))
        assertEquals(1005, dex("Roaring Moon ex"))
        assertEquals(785, dex("Tapu Koko"))
    }

    /** Le generazioni recenti: e' il motivo per cui non si usano le GIF di gen 5. */
    @Test
    fun testGenerazioniRecenti() {
        assertEquals(1007, dex("Koraidon ex"))
    }

    /**
     * I nomi degli archetipi, che e' come si scrive il mazzo di un avversario.
     * Qui non c'e' una carta sola da riconoscere ma due Pokemon dentro una
     * frase, insieme a parole che Pokemon non sono.
     */
    @Test
    fun testArchetipiAvversari() = runBlocking {
        PokemonSpriteResolver.preload(context)

        fun sprites(nome: String) = PokemonSpriteResolver.spriteUrlsForArchetype(context, nome)
        fun ids(nome: String) = sprites(nome).map { it.substringAfterLast('/').removeSuffix(".png") }

        assertEquals(listOf("6"), ids("Charizard ex"))
        assertEquals(listOf("6", "18"), ids("Charizard ex Pidgeot"))
        assertEquals(listOf("887", "477"), ids("Dragapult Dusknoir"))

        // Nomi di due parole dentro all'archetipo: vanno riconosciuti interi.
        assertEquals(listOf("1021", "1017"), ids("Raging Bolt Ogerpon"))
        assertEquals(listOf("1006"), ids("Iron Valiant ex"))

        // Le parole che non sono Pokemon si saltano senza rompere niente.
        assertEquals(listOf("151"), ids("Lost Box Mew"))
        assertTrue(ids("Lost Zone Toolbox").isEmpty())

        // Mai piu' di due, che e' lo spazio che c'e' in una riga.
        assertEquals(2, sprites("Charizard Pidgeot Dusknoir Dragapult").size)
        assertTrue(ids("").isEmpty())
    }

    @Test
    fun testCarteCheNonSonoPokemon() {
        assertNull(dex("Professor's Research"))
        assertNull(dex("Basic Fire Energy"))
        assertNull(dex("Ultra Ball"))
        assertNull(dex("Boss's Orders"))
        assertNull(dex(""))
    }

    /**
     * La via che usa la UI non carica niente da se': finche' preload() non ha
     * finito risponde null, cosi' la composizione di una riga non paga mai la
     * lettura dell'asset. Qui si verifica proprio quel patto.
     */
    @Test
    fun testIndirizzoSpritePrimaEDopoIlPreload() = runBlocking {
        PokemonSpriteResolver.preload(context)
        assertTrue(PokemonSpriteResolver.isReady)

        assertEquals(
            "https://raw.githubusercontent.com/PokeAPI/sprites/master/sprites/pokemon/6.png",
            PokemonSpriteResolver.spriteUrlForCardName(context, "Charizard ex")
        )
        assertNull(PokemonSpriteResolver.spriteUrlForCardName(context, "Ultra Ball"))
    }
}

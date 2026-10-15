package com.emabuia.pokevault.data.simulator

import android.app.Application
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Il voto rapido della Prova (Pessima, Rischiosa, Buona) e le mani salvate
 * con le carte vere.
 */
class HandRatingTest {

    private val gson = Gson()
    private val listType = object : TypeToken<List<SavedProblemHand>>() {}.type

    private fun card(name: String, id: String = "") =
        SimulatorCard(name = name, isBasic = false, isEnergy = false, isSupporter = false, isOutCard = false, id = id)

    private fun saved(
        cards: List<String>,
        cardIds: List<String>? = null,
        rating: HandRating? = null
    ) = SavedProblemHand(
        id = "id-${cards.hashCode()}-${rating?.code}",
        deckId = "deck",
        deckName = "Deck",
        cards = cards,
        tags = emptyList(),
        note = "",
        createdAtMillis = 1L,
        cardIds = cardIds,
        rating = rating?.code
    )

    // ── Voto e giudizio dell'app ────────────────────────────────────────────

    @Test
    fun `il verdetto dell'app diventa uno dei tre voti`() {
        assertEquals(HandRating.GOOD, HandRating.fromVerdict(HandVerdict.GREAT))
        assertEquals(HandRating.GOOD, HandRating.fromVerdict(HandVerdict.PLAYABLE))
        assertEquals(HandRating.RISKY, HandRating.fromVerdict(HandVerdict.RISKY))
        assertEquals(HandRating.BAD, HandRating.fromVerdict(HandVerdict.MULLIGAN))
    }

    @Test
    fun `i codici salvati si rileggono, quelli sconosciuti no`() {
        HandRating.entries.forEach { assertEquals(it, HandRating.fromCode(it.code)) }
        assertNull(HandRating.fromCode(null))
        assertNull(HandRating.fromCode("ottima"))
    }

    @Test
    fun `la sessione conta l'accordo sulla domanda parte bene o no`() {
        val tally = PracticeTally()
            .withRating("a", user = HandRating.GOOD, app = HandRating.GOOD)
            .withRating("b", user = HandRating.BAD, app = HandRating.RISKY)
            .withRating("c", user = HandRating.GOOD, app = HandRating.RISKY)

        assertEquals(3, tally.rated)
        // Pessima e Rischiosa dicono tutte e due "non parte bene".
        assertEquals(2, tally.agreed)
    }

    @Test
    fun `cambiare voto non conta la mano due volte, toglierlo la toglie`() {
        val tally = PracticeTally()
            .withRating("a", user = HandRating.BAD, app = HandRating.GOOD)
            .withRating("a", user = HandRating.GOOD, app = HandRating.GOOD)

        assertEquals(1, tally.rated)
        assertEquals(1, tally.agreed)
        assertEquals(0, tally.withoutRating("a").rated)
    }

    // ── Mani salvate: lettura delle versioni vecchie ────────────────────────

    @Test
    fun `una mano salvata dalla 3_1_8 si rilegge senza i campi nuovi`() {
        val json = """
            [{"id":"x","deckId":"deck","deckName":"Deck","cards":["Pikachu"],"tags":[],"note":"","createdAtMillis":5}]
        """.trimIndent()

        val hand = gson.fromJson<List<SavedProblemHand>>(json, listType).single()

        assertEquals(listOf("Pikachu"), hand.cards)
        assertNull(hand.cardIds)
        assertNull(hand.userRating)
        assertNull(hand.appHandRating)
        assertNull(hand.wentFirst)
    }

    @Test
    fun `i campi nuovi si scrivono e si rileggono uguali`() {
        val hand = saved(listOf("Pikachu"), cardIds = listOf("sv1-25"), rating = HandRating.RISKY)
            .copy(appRating = HandRating.GOOD.code, turnOrder = "second")

        val back = gson.fromJson<List<SavedProblemHand>>(gson.toJson(listOf(hand)), listType).single()

        assertEquals(hand, back)
        assertEquals(HandRating.RISKY, back.userRating)
        assertEquals(HandRating.GOOD, back.appHandRating)
        assertEquals(false, back.wentFirst)
    }

    // ── Le carte da disegnare ───────────────────────────────────────────────

    @Test
    fun `le carte si ritrovano per id, poi per nome, altrimenti resta il nome`() {
        val pool = listOf(card("Pikachu", id = "sv1-25"), card("Pikachu", id = "sv2-30"), card("Iono", id = "pal-185"))

        val withIds = saved(listOf("Pikachu", "Iono"), cardIds = listOf("sv2-30", "pal-185")).resolveCards(pool)
        assertEquals(listOf("sv2-30", "pal-185"), withIds.map { it.id })

        // Le mani salvate fino alla 3.1.8 hanno solo i nomi.
        val legacy = saved(listOf("Iono", "Charizard ex")).resolveCards(pool)
        assertEquals("pal-185", legacy[0].id)
        assertEquals("Charizard ex", legacy[1].name)
        assertEquals("", legacy[1].imageUrl)
    }

    @Test
    fun `il filtro mostra solo le mani con quel voto`() {
        val hands = listOf(
            saved(listOf("A"), rating = HandRating.GOOD),
            saved(listOf("B"), rating = HandRating.BAD),
            saved(listOf("C"))
        )

        assertEquals(3, hands.filterByRating(null).size)
        assertEquals(listOf("A"), hands.filterByRating(HandRating.GOOD).single().cards)
        assertTrue(hands.filterByRating(HandRating.RISKY).isEmpty())
    }
}

/** Il salvataggio vero, sulle preferenze: votare, cambiare voto, togliere. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class HandSimulatorLocalStoreTest {

    private fun store() = HandSimulatorLocalStore(RuntimeEnvironment.getApplication())

    @Test
    fun `votare salva, cambiare voto aggiorna senza duplicare`() {
        val store = store()
        val id = store.saveProblemHand(
            deckId = "deck",
            deckName = "Deck",
            cards = listOf("Pikachu", "Iono"),
            tags = emptyList(),
            cardIds = listOf("sv1-25", "pal-185"),
            rating = HandRating.BAD,
            appRating = HandRating.GOOD,
            turnOrder = TurnOrder.FIRST
        )!!

        store.updateRating(id, HandRating.GOOD)

        val hands = store.getSavedHands("deck")
        assertEquals(1, hands.size)
        assertEquals(HandRating.GOOD, hands.single().userRating)
        assertEquals(HandRating.GOOD, hands.single().appHandRating)
        assertEquals(listOf("sv1-25", "pal-185"), hands.single().cardIds)
        assertEquals(true, hands.single().wentFirst)

        store.deleteProblemHand(id)
        assertTrue(store.getSavedHands("deck").isEmpty())
    }

    @Test
    fun `senza mazzo o senza carte non si salva niente`() {
        val store = store()
        assertNull(store.saveProblemHand("", "Deck", listOf("Pikachu"), emptyList()))
        assertNull(store.saveProblemHand("deck", "Deck", emptyList(), emptyList()))
        assertFalse(store.getSavedHands().any { it.deckId == "deck" })
    }
}

package com.emabuia.pokevault.integration

import android.app.Application
import android.os.Looper
import com.emabuia.pokevault.data.firebase.FirestoreRepository
import com.emabuia.pokevault.data.firebase.SyncErrors
import com.emabuia.pokevault.data.model.Deck
import com.emabuia.pokevault.data.model.MatchLog
import com.emabuia.pokevault.data.model.Tournament
import com.emabuia.pokevault.data.model.Wishlist
import com.emabuia.pokevault.integration.EmulatorEnv.attendiServer
import com.emabuia.pokevault.integration.EmulatorEnv.emu
import com.emabuia.pokevault.integration.EmulatorEnv.nuovoUtente
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import kotlinx.coroutines.tasks.await
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.SQLiteMode

/**
 * Wishlist, deck, tornei: salvati "locale prima" (non si aspetta piu' la
 * conferma del server, cosi' offline "Salva" non resta appeso). Qui si
 * controlla che arrivino comunque al server, con l'id che l'app ha avuto
 * subito, e passando le regole di sicurezza vere.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class SalvataggiLocaliEmulatorTest {

    private val repo get() = FirestoreRepository()

    @Before
    fun setUp() {
        EmulatorEnv.assumeEmulator()
        EmulatorEnv.init(RuntimeEnvironment.getApplication())
    }

    @After
    fun tearDown() = EmulatorEnv.shutdown()

    private suspend fun sulServer(uid: String, collection: String) =
        run {
            attendiServer()
            FirebaseFirestore.getInstance().collection("users").document(uid).collection(collection)
                .get(Source.SERVER).await().documents
        }

    @Test
    fun `una wishlist nuova arriva al server con l'id restituito`() {
        val uid = emu { nuovoUtente() }
        val id = emu { repo.saveWishlist(Wishlist(name = "Da prendere", cardIds = listOf("sv4pt5-1"))).getOrThrow() }

        val docs = emu { sulServer(uid, "wishlists") }
        assertEquals(listOf(id), docs.map { it.id })
        assertEquals(listOf("sv4pt5-1"), docs.single().get("cardIds"))
    }

    @Test
    fun `aggiungere e togliere carte da una wishlist arriva al server`() {
        val uid = emu { nuovoUtente() }
        val id = emu { repo.saveWishlist(Wishlist(name = "Lista")).getOrThrow() }
        emu { repo.addCardsToWishlist(id, listOf("a", "b", "c")).getOrThrow() }
        emu { repo.removeCardFromWishlist(id, "b").getOrThrow() }

        val doc = emu { sulServer(uid, "wishlists") }.single()
        assertEquals(listOf("a", "c"), doc.get("cardIds"))
    }

    @Test
    fun `una carta esce da tutte le wishlist che la contengono`() {
        val uid = emu { nuovoUtente() }
        emu { repo.saveWishlist(Wishlist(name = "Uno", cardIds = listOf("x", "y"))).getOrThrow() }
        emu { repo.saveWishlist(Wishlist(name = "Due", cardIds = listOf("x"))).getOrThrow() }
        emu { attendiServer() }

        emu { repo.removeCardFromAllWishlists("x").getOrThrow() }

        val docs = emu { sulServer(uid, "wishlists") }
        assertEquals(2, docs.size)
        assertTrue(docs.none { (it.get("cardIds") as List<*>).contains("x") })
        assertTrue(docs.any { (it.get("cardIds") as List<*>).contains("y") })
    }

    @Test
    fun `cancellare una wishlist la toglie dal server`() {
        val uid = emu { nuovoUtente() }
        val id = emu { repo.saveWishlist(Wishlist(name = "Via")).getOrThrow() }
        emu { repo.deleteWishlist(id).getOrThrow() }

        assertTrue(emu { sulServer(uid, "wishlists") }.isEmpty())
    }

    @Test
    fun `un deck salvato e poi modificato resta un documento solo`() {
        val uid = emu { nuovoUtente() }
        val id = emu { repo.saveDeck(Deck(name = "Prima")).getOrThrow() }
        emu { repo.saveDeck(Deck(id = id, name = "Dopo")).getOrThrow() }

        val doc = emu { sulServer(uid, "decks") }.single()
        assertEquals(id, doc.id)
        assertEquals("Dopo", doc.getString("name"))
    }

    @Test
    fun `una modifica rifiutata dal server viene segnalata`() {
        emu { nuovoUtente() }
        val prima = SyncErrors.rejectedCount
        // Una lista che sul server non c'e': l'update locale passa, il server
        // risponde NOT_FOUND.
        emu { repo.addCardToWishlist("lista-inesistente", "x").getOrThrow() }
        emu { attendiServer() }
        // L'avviso parte da un listener sul thread principale.
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(prima + 1, SyncErrors.rejectedCount)
    }

    @Test
    fun `una modifica accettata non segnala niente`() {
        emu { nuovoUtente() }
        val prima = SyncErrors.rejectedCount
        emu { repo.saveWishlist(Wishlist(name = "Valida")).getOrThrow() }
        emu { attendiServer() }
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(prima, SyncErrors.rejectedCount)
    }

    @Test
    fun `cancellare un torneo porta via anche le sue partite`() {
        val uid = emu { nuovoUtente() }
        val torneo = emu { repo.saveTournament(Tournament(location = "Milano")).getOrThrow() }
        val altro = emu { repo.saveTournament(Tournament(location = "Roma")).getOrThrow() }
        emu { repo.saveMatchLog(MatchLog(tournamentId = torneo, round = 1, result = "W")).getOrThrow() }
        emu { repo.saveMatchLog(MatchLog(tournamentId = torneo, round = 2, result = "L")).getOrThrow() }
        emu { repo.saveMatchLog(MatchLog(tournamentId = altro, round = 1, result = "T")).getOrThrow() }
        emu { attendiServer() }

        emu { repo.deleteTournament(torneo).getOrThrow() }

        val tornei = emu { sulServer(uid, "tournaments") }
        assertEquals(listOf(altro), tornei.map { it.id })
        val partite = emu { sulServer(uid, "match_logs") }
        assertEquals(1, partite.size)
        assertEquals(altro, partite.single().getString("tournamentId"))
        assertFalse(partite.any { it.getString("tournamentId") == torneo })
    }
}

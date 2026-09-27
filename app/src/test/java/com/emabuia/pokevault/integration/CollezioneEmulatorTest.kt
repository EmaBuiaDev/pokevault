package com.emabuia.pokevault.integration

import android.app.Application
import com.emabuia.pokevault.data.firebase.FirestoreRepository
import com.emabuia.pokevault.integration.EmulatorEnv.carta
import com.emabuia.pokevault.integration.EmulatorEnv.carteSulServer
import com.emabuia.pokevault.integration.EmulatorEnv.nuovoUtente
import com.emabuia.pokevault.integration.EmulatorEnv.emu
import com.emabuia.pokevault.integration.EmulatorEnv.totaliProfilo
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Source
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.tasks.await
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.SQLiteMode

/**
 * Aggiungere e togliere carte, e che la collezione conti giusto.
 *
 * Gira contro l'emulatore Firebase (vedi [EmulatorEnv]) col codice vero di
 * FirestoreRepository: le stesse scritture che fa l'app, le stesse regole di
 * sicurezza. Ogni test ha il suo utente appena registrato.
 *
 * "Numero" e "valore" sono i due contatori del profilo (`totalCards`,
 * `totalValue`) che l'app mostra in Home e Statistiche.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class CollezioneEmulatorTest {

    private val repo get() = FirestoreRepository()

    @Before
    fun setUp() {
        EmulatorEnv.assumeEmulator()
        EmulatorEnv.init(RuntimeEnvironment.getApplication())
    }

    @After
    fun tearDown() = EmulatorEnv.shutdown()

    private fun assertTotali(uid: String, carte: Long, valore: Double) = emu {
        val t = totaliProfilo(uid)
        assertEquals("numero di carte della collezione", carte, t.carte)
        assertEquals("valore della collezione", valore, t.valore, 0.001)
    }

    @Test
    fun `la registrazione crea il profilo con la collezione vuota`() {
        val uid = emu { nuovoUtente() }
        assertTotali(uid, 0, 0.0)
        assertTrue(emu { carteSulServer(uid) }.isEmpty())
    }

    @Test
    fun `aggiungere una carta la salva e fa salire numero e valore`() {
        val uid = emu { nuovoUtente() }
        emu { repo.addCard(carta("prova-1", valore = 2.50, quantita = 2)).getOrThrow() }

        val carte = emu { carteSulServer(uid) }
        assertEquals(1, carte.size)
        assertEquals("prova-1", carte.single().apiCardId)
        assertEquals(2, carte.single().quantity)
        assertTotali(uid, 2, 5.00)
    }

    @Test
    fun `la stessa stampa nella stessa lingua si somma invece di sdoppiarsi`() {
        val uid = emu { nuovoUtente() }
        emu {
            repo.addCard(carta("prova-1", valore = 3.0)).getOrThrow()
            EmulatorEnv.attendiServer()
            repo.addCard(carta("prova-1", valore = 3.0)).getOrThrow()
        }

        val carte = emu { carteSulServer(uid) }
        assertEquals("una carta sola, non due", 1, carte.size)
        assertEquals(2, carte.single().quantity)
        assertTotali(uid, 2, 6.0)
    }

    @Test
    fun `una stampa diversa della stessa carta resta separata`() {
        val uid = emu { nuovoUtente() }
        emu {
            repo.addCard(carta("prova-1", valore = 1.0, variante = "Normal")).getOrThrow()
            EmulatorEnv.attendiServer()
            repo.addCard(carta("prova-1", valore = 4.0, variante = "Reverse Holo")).getOrThrow()
        }

        val carte = emu { carteSulServer(uid) }
        assertEquals(setOf("Normal", "Reverse Holo"), carte.map { it.variant }.toSet())
        assertTotali(uid, 2, 5.0)
    }

    @Test
    fun `aggiunta multipla somma le stampe gia' possedute`() {
        val uid = emu { nuovoUtente() }
        emu {
            repo.addCard(carta("gia-mia", valore = 2.0)).getOrThrow()
            EmulatorEnv.attendiServer()
            repo.addCards(
                listOf(
                    carta("gia-mia", valore = 2.0),
                    carta("nuova-1", valore = 1.5),
                    carta("nuova-2", valore = 0.5, quantita = 3)
                )
            ).getOrThrow()
        }

        val carte = emu { carteSulServer(uid) }.associateBy { it.apiCardId }
        assertEquals(setOf("gia-mia", "nuova-1", "nuova-2"), carte.keys)
        assertEquals("la carta gia' posseduta sale di quantita'", 2, carte.getValue("gia-mia").quantity)
        assertTotali(uid, 1 + 1 + 1 + 3, 2.0 + 2.0 + 1.5 + 1.5)
    }

    @Test
    fun `eliminare una carta fa scendere numero e valore`() {
        val uid = emu { nuovoUtente() }
        emu {
            repo.addCard(carta("resta", valore = 1.0)).getOrThrow()
            repo.addCard(carta("va-via", valore = 4.0, quantita = 2)).getOrThrow()
        }
        assertTotali(uid, 3, 9.0)

        val daTogliere = emu { carteSulServer(uid) }.single { it.apiCardId == "va-via" }
        emu { repo.deleteCard(daTogliere.id).getOrThrow() }

        assertEquals(listOf("resta"), emu { carteSulServer(uid) }.map { it.apiCardId })
        assertTotali(uid, 1, 1.0)
    }

    @Test
    fun `eliminazione multipla fa scendere i totali di tutte le carte tolte`() {
        val uid = emu { nuovoUtente() }
        emu {
            repo.addCards((1..5).map { carta("prova-$it", valore = it.toDouble()) }).getOrThrow()
        }
        assertTotali(uid, 5, 15.0)

        val daTogliere = emu { carteSulServer(uid) }.filter { it.apiCardId in setOf("prova-2", "prova-4", "prova-5") }
        emu { repo.deleteCards(daTogliere).getOrThrow() }

        assertEquals(setOf("prova-1", "prova-3"), emu { carteSulServer(uid) }.map { it.apiCardId }.toSet())
        assertTotali(uid, 2, 4.0)
    }

    @Test
    fun `togliere una copia scala la quantita' e i totali`() {
        val uid = emu { nuovoUtente() }
        emu { repo.addCard(carta("prova-1", valore = 2.0, quantita = 3)).getOrThrow() }
        val id = emu { carteSulServer(uid) }.single().id

        emu { repo.removeOneCopy(id).getOrThrow() }

        assertEquals(2, emu { carteSulServer(uid) }.single().quantity)
        assertTotali(uid, 2, 4.0)
    }

    @Test
    fun `cambiare la quantita' aggiorna i totali della sola differenza`() {
        val uid = emu { nuovoUtente() }
        emu { repo.addCard(carta("prova-1", valore = 2.0, quantita = 1)).getOrThrow() }
        val salvata = emu { carteSulServer(uid) }.single()

        emu { repo.updateCard(salvata.id, salvata.copy(quantity = 4)).getOrThrow() }

        assertEquals(4, emu { carteSulServer(uid) }.single().quantity)
        assertTotali(uid, 4, 8.0)
    }

    @Test
    fun `le carte solo-deck non entrano in collezione ne' nei totali`() {
        val uid = emu { nuovoUtente() }
        emu {
            repo.addCard(carta("mia", valore = 2.0)).getOrThrow()
            repo.addCard(carta("prestata", valore = 50.0, deckOnly = true)).getOrThrow()
        }

        assertTotali(uid, 1, 2.0)
        val collezione = emu {
            EmulatorEnv.attendiServer()
            repo.getCards().first { it.isNotEmpty() }
        }
        assertEquals("la collezione mostra solo la carta posseduta", listOf("mia"), collezione.map { it.apiCardId })
        val conDeck = emu { repo.getCardsIncludingDeckOnly().first { it.size >= 2 } }
        assertEquals(setOf("mia", "prestata"), conDeck.map { it.apiCardId }.toSet())
    }

    @Test
    fun `eliminare una carta dal set non tocca le sue copie solo-deck`() {
        val uid = emu { nuovoUtente() }
        emu {
            repo.addCard(carta("pikachu", valore = 3.0)).getOrThrow()
            repo.addCard(carta("pikachu", valore = 0.0, deckOnly = true)).getOrThrow()
            EmulatorEnv.attendiServer()
            repo.deleteCardByApiId("pikachu").getOrThrow()
        }

        val rimaste = emu { carteSulServer(uid) }
        assertEquals(1, rimaste.size)
        assertTrue("resta solo la copia del deck", rimaste.single().deckOnly)
        assertTotali(uid, 0, 0.0)
    }

    @Test
    fun `i totali del profilo coincidono con la somma delle carte`() {
        val uid = emu { nuovoUtente() }
        emu {
            repo.addCards(listOf(carta("a", 1.25, 2), carta("b", 3.10), carta("c", 0.0, 4))).getOrThrow()
            repo.addCard(carta("a", 1.25)).getOrThrow()
            EmulatorEnv.attendiServer()
            repo.deleteCard(carteSulServer(uid).single { it.apiCardId == "b" }.id).getOrThrow()
        }

        val carte = emu { carteSulServer(uid) }.filter { !it.deckOnly }
        val stats = emu { repo.getCollectionStats() }
        val t = emu { totaliProfilo(uid) }
        assertEquals(carte.sumOf { it.quantity }.toLong(), t.carte)
        assertEquals(carte.sumOf { it.estimatedValue * it.quantity }, t.valore, 0.001)
        assertEquals(t.carte, stats.totalCards.toLong())
        assertEquals(t.valore, stats.totalValue, 0.001)
    }

    @Test
    fun `un utente non puo' leggere le carte di un altro`() {
        val primo = emu { nuovoUtente() }
        emu { repo.addCard(carta("segreta", valore = 99.0)).getOrThrow(); EmulatorEnv.attendiServer() }
        emu { nuovoUtente() }

        emu {
            try {
                FirebaseFirestore.getInstance().collection("users").document(primo).collection("cards")
                    .get(Source.SERVER).await()
                fail("le regole di sicurezza hanno lasciato leggere la collezione di un altro utente")
            } catch (e: FirebaseFirestoreException) {
                assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, e.code)
            }
        }
    }
}

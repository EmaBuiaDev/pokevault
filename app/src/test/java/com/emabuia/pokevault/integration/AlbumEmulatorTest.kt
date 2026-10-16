package com.emabuia.pokevault.integration

import android.app.Application
import com.emabuia.pokevault.data.firebase.FirestoreRepository
import com.emabuia.pokevault.data.model.Album
import com.emabuia.pokevault.data.model.AlbumCatalogCard
import com.emabuia.pokevault.integration.EmulatorEnv.attendiServer
import com.emabuia.pokevault.integration.EmulatorEnv.emu
import com.emabuia.pokevault.integration.EmulatorEnv.nuovoUtente
import com.emabuia.pokevault.util.AlbumEntries
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.tasks.await
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.SQLiteMode

/**
 * Gli album con le carte del catalogo, sul Firestore vero (emulatore).
 *
 * La parte che i test unitari non vedono: che le carte annidate tornino
 * indietro come oggetti, che arrayUnion non le sdoppi, e che gli album
 * salvati prima dei campi nuovi si leggano ancora.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class AlbumEmulatorTest {

    private val repo get() = FirestoreRepository()

    @Before
    fun setUp() {
        EmulatorEnv.assumeEmulator()
        EmulatorEnv.init(RuntimeEnvironment.getApplication())
    }

    @After
    fun tearDown() = EmulatorEnv.shutdown()

    private val charizard = AlbumCatalogCard(
        apiId = "ita:sv03:125", name = "Charizard ex", imageUrl = "https://img/c.png",
        set = "Ossidiana Infuocata", setId = "sv03__ita", number = "125",
        rarity = "Double Rare", type = "Fire", supertype = "Pokémon", hp = 330, price = 4.5
    )

    private suspend fun albumSalvato(id: String): Album {
        attendiServer()
        return repo.getAlbums().first().single { it.id == id }
    }

    @Test
    fun `carte del catalogo salvate e rilette`() {
        emu { nuovoUtente() }
        val id = emu {
            repo.saveAlbum(Album(name = "Fuoco", size = 12, layout = "4x3")).getOrThrow()
        }
        emu {
            repo.addAlbumEntries(id, listOf("doc1", AlbumEntries.catalogEntry(charizard.apiId)), listOf(charizard)).getOrThrow()
            // La stessa carta una seconda volta: arrayUnion non deve sdoppiarla.
            repo.addAlbumEntries(id, listOf(AlbumEntries.catalogEntry(charizard.apiId)), listOf(charizard)).getOrThrow()
        }

        val album = emu { albumSalvato(id) }
        assertEquals("4x3", album.layout)
        assertEquals(listOf("doc1", "cat:ita:sv03:125"), album.cardIds)
        assertEquals(listOf(charizard), album.catalogCards)
    }

    @Test
    fun `togliere la carta toglie i suoi dati`() {
        emu { nuovoUtente() }
        val id = emu {
            repo.saveAlbum(Album(name = "A", cardIds = listOf("x"), catalogCards = emptyList())).getOrThrow()
        }
        emu { repo.addAlbumEntries(id, listOf("cat:ita:sv03:125"), listOf(charizard)).getOrThrow() }
        val pieno = emu { albumSalvato(id) }

        val senza = AlbumEntries.withoutEntry(pieno, "cat:ita:sv03:125")
        emu { repo.setAlbumContent(id, senza.cardIds, senza.catalogCards).getOrThrow() }

        val album = emu { albumSalvato(id) }
        assertEquals(listOf("x"), album.cardIds)
        assertEquals(emptyList<AlbumCatalogCard>(), album.catalogCards)
    }

    @Test
    fun `album di prima ancora leggibile`() {
        val uid = emu { nuovoUtente() }
        // Come lo scriveva la 3.1.8: niente layout, niente catalogCards.
        emu {
            FirebaseFirestore.getInstance().collection("users").document(uid).collection("albums")
                .document("vecchio")
                .set(
                    mapOf(
                        "name" to "Vecchio", "description" to "", "pokemonType" to "", "expansion" to "",
                        "supertype" to "", "size" to 9, "theme" to "fire", "cardIds" to listOf("a", "b"),
                        "coverImageUrl" to "", "createdAt" to com.google.firebase.Timestamp.now()
                    )
                ).await()
        }

        val album = emu { albumSalvato("vecchio") }
        assertEquals(listOf("a", "b"), album.cardIds)
        assertEquals("3x3", album.layout)
        assertEquals(emptyList<AlbumCatalogCard>(), album.catalogCards)
    }
}

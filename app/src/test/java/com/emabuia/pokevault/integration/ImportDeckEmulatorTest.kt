package com.emabuia.pokevault.integration

import android.app.Application
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import com.emabuia.pokevault.BuildConfig
import com.emabuia.pokevault.data.remote.RepositoryProvider
import com.emabuia.pokevault.integration.EmulatorEnv.carteSulServer
import com.emabuia.pokevault.integration.EmulatorEnv.emu
import com.emabuia.pokevault.integration.EmulatorEnv.nuovoUtente
import com.emabuia.pokevault.integration.EmulatorEnv.totaliProfilo
import com.emabuia.pokevault.viewmodel.DeckLabViewModel
import com.emabuia.pokevault.viewmodel.DeckLabViewModel.DeckCardSource
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.SQLiteMode

/**
 * Importare un deck, dal tasto "Importa" (testo in formato PTCG) e da CSV.
 *
 * Il percorso e' quello dell'app: DeckLabViewModel.importFromText, poi
 * addMissingCardsToCollection con la scelta dell'utente fra "in collezione"
 * e "deck di prova". L'utente e' nuovo, quindi tutte le carte del deck sono
 * mancanti e vanno create.
 *
 * Le carte si cercano nel catalogo vero e il prezzo si risolve come nell'app
 * (worker di produzione, in sola lettura); le scritture vanno sull'emulatore.
 * Per questo servono il proxy configurato in local.properties e la rete.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class ImportDeckEmulatorTest {

    private val store = ViewModelStore()

    @Before
    fun setUp() {
        EmulatorEnv.assumeEmulator()
        // Senza queste due l'app non trova le carte nel catalogo e le crea "di
        // ripiego", senza id ne' immagine: il test fallirebbe per la
        // configurazione, non per l'app. Il primo giro in CI e' andato cosi'.
        EmulatorEnv.richiedi(
            BuildConfig.POKEWALLET_PROXY_ENABLED && BuildConfig.POKEWALLET_PROXY_URL.isNotBlank(),
            "Manca POKEWALLET_PROXY_URL (o PROXY_ENABLED=false) in local.properties: l'import non puo' cercare le carte"
        )
        EmulatorEnv.richiedi(
            BuildConfig.ITALIAN_CATALOG_URL.isNotBlank(),
            "Manca ITALIAN_CATALOG_URL in local.properties: senza catalogo italiano l'import non trova le carte"
        )
        val app = RuntimeEnvironment.getApplication()
        EmulatorEnv.init(app)
        RepositoryProvider.init(app)
    }

    @After
    fun tearDown() {
        store.clear()
        EmulatorEnv.shutdown()
    }

    private data class Riga(val qta: Int, val nome: String)

    // Carte italiane recenti, con prezzo: un deck piccolo ma vero.
    private val deck = listOf(Riga(3, "Pikachu ex"), Riga(2, "Ultra Ball"))

    private val testoPtcg = """
        Pokémon: 3
        3 Pikachu ex SSP 57

        Allenatore: 2
        2 Ultra Ball SVI 196
    """.trimIndent()

    private val csv = """
        Quantità;Nome;Set;Numero
        3;Pikachu ex;SSP;57
        2;Ultra Ball;SVI;196
    """.trimIndent()

    /** Importa [testo] come fa l'app e aspetta che le carte mancanti siano create. */
    private fun importa(testo: String, source: DeckCardSource): String = emu(timeoutMs = 180_000) {
        val uid = nuovoUtente()
        val vm = ViewModelProvider(store, ViewModelProvider.NewInstanceFactory())[DeckLabViewModel::class.java]
        val risultato = vm.importFromText(testo)
        assertEquals("carte richieste dal deck", deck.sumOf { it.qta }, risultato.totalRequested)
        assertEquals("un utente nuovo non ha nessuna carta del deck", deck.size, risultato.missingMetaDeckCards.size)

        vm.chooseDeckCardSource(source)
        val finito = CompletableDeferred<Unit>()
        vm.addMissingCardsToCollection(risultato.missingMetaDeckCards, RuntimeEnvironment.getApplication()) {
            finito.complete(Unit)
        }
        withTimeout(150_000) { finito.await() }
        uid
    }

    private fun verificaInCollezione(uid: String) {
        val carte = emu { carteSulServer(uid) }
        assertEquals("una carta per riga del deck", deck.size, carte.size)
        assertTrue("nessuna carta resta solo-deck", carte.none { it.deckOnly })
        // Il nome salvato e' quello del catalogo italiano ("Pikachu-ex"), non
        // quello scritto nella lista: si confrontano senza trattini e spazi.
        val chiave = { nome: String -> nome.lowercase().replace(Regex("[^a-z0-9]"), "") }
        assertEquals(deck.associate { chiave(it.nome) to it.qta }, carte.associate { chiave(it.name) to it.quantity })
        carte.forEach { c ->
            assertTrue("${c.name}: trovata nel catalogo (apiCardId)", c.apiCardId.isNotBlank())
            assertTrue("${c.name}: ha l'immagine", c.imageUrl.isNotBlank())
        }

        val t = emu { totaliProfilo(uid) }
        assertEquals("numero di carte della collezione", deck.sumOf { it.qta }.toLong(), t.carte)
        assertEquals("valore = somma dei prezzi delle carte create", carte.sumOf { it.estimatedValue * it.quantity }, t.valore, 0.001)
        assertTrue("il valore della collezione sale (era 0)", t.valore > 0.0)
    }

    @Test
    fun `import dal tasto Importa in collezione fa salire numero e valore`() {
        verificaInCollezione(importa(testoPtcg, DeckCardSource.COLLECTION))
    }

    @Test
    fun `import da CSV in collezione fa salire numero e valore`() {
        verificaInCollezione(importa(csv, DeckCardSource.COLLECTION))
    }

    @Test
    fun `import come deck di prova non tocca la collezione`() {
        val uid = importa(testoPtcg, DeckCardSource.DECK_ONLY)

        val carte = emu { carteSulServer(uid) }
        assertEquals(deck.size, carte.size)
        assertTrue("tutte le carte restano solo-deck", carte.all { it.deckOnly })
        val t = emu { totaliProfilo(uid) }
        assertEquals("numero di carte della collezione invariato", 0L, t.carte)
        assertEquals("valore della collezione invariato", 0.0, t.valore, 0.001)
    }
}

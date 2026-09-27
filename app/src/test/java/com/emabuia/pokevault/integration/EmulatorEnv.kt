package com.emabuia.pokevault.integration

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.os.Looper
import com.emabuia.pokevault.data.firebase.FirebaseAuthManager
import com.emabuia.pokevault.data.model.PokemonCard
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import org.junit.Assume.assumeTrue
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf

/**
 * L'ambiente dei test di integrazione: Firebase vero, ma puntato
 * all'emulatore locale (Firestore + Auth) invece che alla produzione.
 *
 * Si avvia con
 *   npx firebase-tools emulators:start --config firebase.emulators.json --only firestore,auth --project demo-pokevault
 * e in CI lo fa `.github/workflows/test-integrazione.yml`. Il progetto
 * `demo-*` non esiste su Firebase: l'emulatore non puo' toccare niente di vero.
 *
 * Il codice che si prova e' quello dell'app, senza modifiche: FirestoreRepository
 * e FirebaseAuthManager usano `FirebaseFirestore.getInstance()`, e qui si
 * dice solo a quell'istanza di parlare con l'emulatore prima che faccia
 * qualunque cosa. Le regole di sicurezza sono quelle di `firestore.rules`.
 *
 * Nomi dei test: al massimo una settantina di caratteri. Robolectric li mette
 * nel percorso della cartella temporanea dell'app, e su Windows oltre i 260
 * caratteri SQLite non apre la cache di Firestore: il test fallisce con
 * SQLITE_CANTOPEN gia' alla registrazione, e sembra un guasto di Firestore.
 */
object EmulatorEnv {

    private const val HOST = "127.0.0.1"
    private const val FIRESTORE_PORT = 8080
    private const val AUTH_PORT = 9099

    /**
     * In CI il job di integrazione mette POKEVAULT_EMULATOR_REQUIRED=1: li'
     * un emulatore mancante deve far fallire, non saltare. Altrimenti un
     * emulatore che non parte darebbe una release verde senza aver provato
     * niente. Fuori da quel job (i test unitari di sempre) si saltano.
     */
    private val required = System.getenv("POKEVAULT_EMULATOR_REQUIRED") == "1"

    fun assumeEmulator() {
        val up = portOpen(FIRESTORE_PORT) && portOpen(AUTH_PORT)
        if (required && !up) {
            throw AssertionError("Emulatore Firebase non raggiungibile su $HOST:$FIRESTORE_PORT/$AUTH_PORT, ma POKEVAULT_EMULATOR_REQUIRED=1")
        }
        assumeTrue("Emulatore Firebase spento: test di integrazione saltati", up)
    }

    private fun portOpen(port: Int): Boolean = try {
        Socket().use { it.connect(InetSocketAddress(HOST, port), 500); true }
    } catch (_: Exception) {
        false
    }

    /**
     * Da chiamare all'inizio di ogni test, con [shutdown] alla fine.
     *
     * Robolectric da' a ogni test un'Application e un main looper nuovi, ma
     * FirebaseApp e' statico: tenendo quello del test precedente, Firebase
     * consegnava le risposte a un looper che nessuno faceva piu' girare, e dal
     * secondo test in poi tutto restava appeso. Si riparte da zero ogni volta.
     */
    fun init(context: Context) {
        if (FirebaseApp.getApps(context).isNotEmpty()) shutdown()
        // La cache locale di Firestore e' un database SQLite. Su Robolectric il
        // primo test di ogni giro falliva con SQLITE_CANTOPEN: la prima
        // apertura di SQLite avveniva su un thread interno di Firestore. Aprire
        // e chiudere un database qui, sul thread del test, la toglie di mezzo;
        // la cartella dei database va creata comunque.
        context.getDatabasePath("firestore").parentFile?.mkdirs()
        SQLiteDatabase.create(null).close()
        FirebaseApp.initializeApp(
            context,
            FirebaseOptions.Builder()
                .setProjectId("demo-pokevault")
                .setApplicationId("1:1:android:1")
                .setApiKey("fake-api-key")
                .build()
        )
        FirebaseFirestore.getInstance().useEmulator(HOST, FIRESTORE_PORT)
        FirebaseAuth.getInstance().useEmulator(HOST, AUTH_PORT)
    }

    /** Chiude Firestore e cancella l'istanza Firebase del test appena finito. */
    fun shutdown() {
        if (FirebaseApp.getApps(RuntimeEnvironment.getApplication()).isEmpty()) return
        try {
            emu(timeoutMs = 20_000) {
                FirebaseAuth.getInstance().signOut()
                FirebaseFirestore.getInstance().terminate().await()
            }
        } catch (_: Throwable) {
            // Un test fallito puo' lasciare Firestore in qualunque stato: qui
            // conta solo non trascinarlo nel test dopo.
        }
        FirebaseApp.getApps(RuntimeEnvironment.getApplication()).forEach { it.delete() }
    }

    /**
     * Esegue [block] su un thread a parte e intanto fa girare il main looper.
     *
     * Robolectric tiene fermo il thread principale finche' il test non lo fa
     * avanzare, e Firebase ci consegna cose essenziali (il token di accesso
     * per Firestore, i listener degli snapshot, viewModelScope). Con un
     * runBlocking sul thread del test tutto si blocca in attesa di se stesso.
     */
    fun <T> emu(timeoutMs: Long = 90_000, block: suspend () -> T): T {
        val future = CompletableFuture<T>()
        thread(name = "test-integrazione") {
            try { future.complete(runBlocking { block() }) } catch (t: Throwable) { future.completeExceptionally(t) }
        }
        val deadline = System.currentTimeMillis() + timeoutMs
        while (!future.isDone) {
            shadowOf(Looper.getMainLooper()).idle()
            if (System.currentTimeMillis() > deadline) throw AssertionError("Test fermo da piu' di ${timeoutMs / 1000} secondi")
            Thread.sleep(5)
        }
        try {
            return future.get(0, TimeUnit.MILLISECONDS)
        } catch (e: ExecutionException) {
            throw e.cause ?: e
        }
    }

    /**
     * Un utente nuovo per ogni test, registrato come nell'app (che crea anche
     * il suo documento `users/{uid}` con totalCards = 0). Ogni test parte da
     * una collezione vuota e non vede quelle degli altri.
     */
    suspend fun nuovoUtente(): String {
        FirebaseAuth.getInstance().signOut()
        val email = "test${System.nanoTime()}@pokevault.test"
        return FirebaseAuthManager().register(email, "password-di-prova", "Allenatore di prova").getOrThrow().uid
    }

    /** Aspetta che tutte le scritture locali siano arrivate all'emulatore. */
    suspend fun attendiServer() {
        FirebaseFirestore.getInstance().waitForPendingWrites().await()
    }

    data class Totali(val carte: Long, val valore: Double)

    /** I contatori del profilo, letti dal server (non dalla cache locale). */
    suspend fun totaliProfilo(uid: String): Totali {
        attendiServer()
        val doc = FirebaseFirestore.getInstance().collection("users").document(uid).get(Source.SERVER).await()
        return Totali(doc.getLong("totalCards") ?: 0L, doc.getDouble("totalValue") ?: 0.0)
    }

    /** Tutti i documenti carta dell'utente, dal server, solo-deck compresi. */
    suspend fun carteSulServer(uid: String): List<PokemonCard> {
        attendiServer()
        return FirebaseFirestore.getInstance().collection("users").document(uid).collection("cards")
            .get(Source.SERVER).await()
            .documents.mapNotNull { it.toObject(PokemonCard::class.java)?.copy(id = it.id) }
    }

    fun carta(
        apiCardId: String,
        valore: Double,
        quantita: Int = 1,
        variante: String = "Normal",
        lingua: String = "🇮🇹 Italiano",
        deckOnly: Boolean = false,
        nome: String = "Carta $apiCardId"
    ) = PokemonCard(
        name = nome,
        set = "Set di prova",
        apiCardId = apiCardId,
        cardNumber = apiCardId.substringAfterLast('-'),
        estimatedValue = valore,
        quantity = quantita,
        variant = variante,
        language = lingua,
        deckOnly = deckOnly
    )
}

package com.emabuia.pokevault.data.remote

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import timber.log.Timber
import java.io.File

/**
 * Cache su disco per i dati Limitless.
 *
 * Prima tutto viveva in memoria di processo: bastava che Android chiudesse
 * l'app per ricominciare da zero, e ricominciare da zero significa una trentina
 * di richieste su una finestra che ne concede cinquanta ogni cinque minuti
 * (vedi [LimitlessRateLimiter]). Il secondo avvio nell'arco di pochi minuti
 * finiva regolarmente in 429.
 *
 * I dati stanno su file e non nelle SharedPreferences perche' un risultato di
 * torneo si porta dietro le decklist complete dei primi tre piazzati: sono
 * centinaia di kilobyte per voce, e le SharedPreferences vengono lette per
 * intero in memoria a ogni avvio dell'app.
 *
 * Si distinguono due tipi di dato:
 *  - quello che **non cambia piu'** — il dettaglio di un torneo concluso: dove
 *    si e' giocato, chi lo ha organizzato. Si tiene senza scadenza.
 *  - quello che **invecchia** — le liste e i risultati calcolati. La data del
 *    file fa da timestamp: si serve fresco entro la scadenza, e si puo' servire
 *    scaduto quando l'alternativa e' una schermata vuota.
 */
object LimitlessLocalCache {

    private const val DIR_NAME = "limitless_cache"
    private const val DETAILS_FILE = "details.json"
    private const val ENTRIES_DIR = "entries"

    /** Quanti dettagli di torneo conservare. Sono piccoli: qualche decina di byte. */
    private const val MAX_DETAILS = 400

    /** Quante voci calcolate tenere su disco. Sono grandi: si sta stretti. */
    private const val MAX_ENTRIES = 8

    private val gson = Gson()

    @Volatile
    private var rootDir: File? = null

    fun init(context: Context) {
        if (rootDir != null) return
        rootDir = File(context.applicationContext.filesDir, DIR_NAME).apply { mkdirs() }
    }

    // ── Dettagli dei tornei: senza scadenza ────────────────────────────────

    /**
     * I dettagli gia' noti, letti dal disco una volta sola.
     *
     * Tenerli anche in memoria evita di rileggere il file a ogni lookup: il
     * disco serve a sopravvivere al riavvio, non a fare da indice.
     */
    private val detailsMemory: MutableMap<String, LimitlessTournamentDetails> by lazy {
        val file = detailsFile()
        val stored = if (file != null && file.exists()) {
            runCatching {
                gson.fromJson<Map<String, LimitlessTournamentDetails>>(
                    file.readText(),
                    object : TypeToken<Map<String, LimitlessTournamentDetails>>() {}.type
                )
            }.onFailure { Timber.w("Limitless: details.json illeggibile, ignorato") }
                .getOrNull()
                .orEmpty()
        } else {
            emptyMap()
        }
        java.util.concurrent.ConcurrentHashMap(stored)
    }

    fun tournamentDetails(id: String): LimitlessTournamentDetails? = detailsMemory[id]

    fun putTournamentDetails(id: String, details: LimitlessTournamentDetails) {
        detailsMemory[id] = details

        val file = detailsFile() ?: return
        val toStore = if (detailsMemory.size > MAX_DETAILS) {
            detailsMemory.entries.take(MAX_DETAILS).associate { it.key to it.value }
        } else {
            detailsMemory.toMap()
        }

        runCatching { file.writeText(gson.toJson(toStore)) }
            .onFailure { Timber.w("Limitless: impossibile scrivere details.json") }
    }

    // ── Voci calcolate: la data del file e' il timestamp ───────────────────

    /** Quando e' stata salvata la voce [key], o null se non c'e'. */
    fun timestampOf(key: String): Long? =
        entryFile(key)?.takeIf { it.exists() }?.lastModified()?.takeIf { it > 0 }

    /**
     * Legge una voce. [maxAgeMs] a null la restituisce anche se scaduta: e'
     * quello che serve quando siamo in pausa per il rate limit, dove un dato
     * vecchio vale piu' di una schermata vuota.
     */
    fun <T> read(key: String, type: java.lang.reflect.Type, maxAgeMs: Long?): T? {
        val file = entryFile(key)?.takeIf { it.exists() } ?: return null

        if (maxAgeMs != null && System.currentTimeMillis() - file.lastModified() > maxAgeMs) {
            return null
        }

        return runCatching { gson.fromJson<T>(file.readText(), type) }
            .onFailure { Timber.w("Limitless: cache $key illeggibile, ignorata") }
            .getOrNull()
    }

    fun write(key: String, value: Any) {
        val file = entryFile(key) ?: return

        runCatching {
            file.parentFile?.mkdirs()
            file.writeText(gson.toJson(value))
        }.onFailure {
            Timber.w("Limitless: impossibile scrivere la cache $key")
            return
        }

        pruneEntries()
    }

    /**
     * Svuota le voci calcolate, non i dettagli dei tornei.
     *
     * Un refresh manuale vuole dati piu' recenti, non ripagare il costo di
     * informazioni che per definizione non sono cambiate.
     */
    fun clearComputed() {
        entriesDir()?.listFiles()?.forEach { it.delete() }
    }

    private fun pruneEntries() {
        val files = entriesDir()?.listFiles() ?: return
        if (files.size <= MAX_ENTRIES) return

        files.sortedBy { it.lastModified() }
            .take(files.size - MAX_ENTRIES)
            .forEach { it.delete() }
    }

    private fun detailsFile(): File? = rootDir?.let { File(it, DETAILS_FILE) }

    private fun entriesDir(): File? = rootDir?.let { File(it, ENTRIES_DIR).apply { mkdirs() } }

    /**
     * Le chiavi arrivano da nomi di formato e di filtro, ma passano comunque da
     * un filtro: una chiave che contenesse separatori di percorso scriverebbe
     * fuori dalla cartella della cache.
     */
    private fun entryFile(key: String): File? {
        val safe = key.replace(Regex("[^A-Za-z0-9_-]"), "_")
        if (safe.isBlank()) return null
        return entriesDir()?.let { File(it, "$safe.json") }
    }
}

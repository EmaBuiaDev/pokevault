package com.emabuia.pokevault.util

import java.text.Normalizer
import java.util.Locale

/**
 * Nomi degli illustratori, conti e ordinamenti della sezione "Collezione per
 * illustratore".
 *
 * Sta fuori dalla composizione come [CollectorLab], e per la stessa ragione:
 * sono funzioni pure, testate, che non ricalcolano niente a ogni frame.
 *
 * Il catalogo porta i nomi **grezzi**, come li hanno scritti le due fonti che
 * lo hanno riempito (TCGdex in inglese, Pokemon Central Wiki per lo storico).
 * Il worker li raggruppa cosi' come sono -- SQLite non sa ripiegare gli accenti
 * -- e l'unificazione avviene qui.
 */

// ── Nomi ──────────────────────────────────────────────────────────────────────

object IllustratorNames {

    /**
     * Grafie diverse della stessa persona che la normalizzazione NON riesce a
     * unire da sola.
     *
     * Oggi e' vuota, e non e' una dimenticanza: sui 388 illustratori veri del
     * catalogo gli unici tre casi di doppia grafia -- "takuyoa"/"Takuyoa",
     * "K. Hoshiba"/"K Hoshiba", "Zu-ka"/"Zu-Ka" -- cadono gia' insieme con le
     * regole di [keyOf], che spengono maiuscole, punti e trattini. La mappa
     * resta perche' ogni set nuovo puo' portare una grafia nuova, e allora si
     * aggiunge una riga qui invece di toccare la logica.
     *
     * Chiave e valore sono entrambi gia' normalizzati da [keyOf].
     */
    private val ALIASES: Map<String, String> = emptyMap()

    /**
     * I separatori che indicano davvero due autori.
     *
     * Solo "+" e "/", scelti guardando il catalogo e non per simmetria:
     * - "+" separa due autori in "Shinji Higuchi + Sachiko Eba";
     * - "/" fa lo stesso in "Kent Kanetsuna/Direc. Shinji Higuchi".
     *
     * La "&" invece NO, anche se sembrerebbe il candidato piu' ovvio: nel
     * catalogo compare una volta sola, dentro "Illus. & Direc. The Pokemon
     * Company Art Team", che e' un credito unico. Spezzandolo si otterrebbero
     * due illustratori inventati, "Illus." e "Direc. The Pokemon Company Art
     * Team". Lo stesso vale per la virgola, che i nomi traslitterati usano.
     */
    private val CREDIT_SEPARATORS = Regex("""\s*[+/]\s*""")

    /**
     * I ruoli scritti davanti al nome ("Illus. Tizio", "Direc. Caio").
     *
     * Vanno via prima di confrontare, o lo stesso Shinji Higuchi finirebbe in
     * due voci: una dalle carte che ha illustrato, una dalle due dove compare
     * come "Direc.".
     */
    private val ROLE_PREFIX = Regex("""^(illus|direc|dir|art)\.?\s+""", RegexOption.IGNORE_CASE)

    private val DIACRITICS = Regex("""\p{Mn}+""")
    private val IGNORED_PUNCTUATION = Regex("""[.,'`’‘-]""")
    private val WHITESPACE = Regex("""\s+""")

    /**
     * I nomi da mostrare contenuti in un credito, gia' separati e ripuliti dal
     * ruolo. Lista vuota se non c'e' niente di utile.
     */
    fun credits(raw: String?): List<String> {
        val clean = raw?.trim().orEmpty()
        if (clean.isEmpty()) return emptyList()
        val parts = clean.split(CREDIT_SEPARATORS)
        // Il ruolo si toglie solo dai pezzi nati da una separazione. Da un
        // credito intero no: "Illus. & Direc. The Pokemon Company Art Team" e'
        // un nome unico, e spogliarlo del "Illus." lascerebbe una voce che
        // comincia per "&".
        val stripRoles = parts.size > 1
        return parts
            .map { part ->
                val named = if (stripRoles) part.replace(ROLE_PREFIX, "") else part
                named.replace(WHITESPACE, " ").trim()
            }
            .filter { it.isNotEmpty() }
    }

    /**
     * La chiave con cui due grafie dello stesso nome si riconoscono: minuscolo,
     * senza accenti, senza la punteggiatura che non distingue nessuno.
     */
    fun keyOf(name: String): String {
        val folded = Normalizer.normalize(name.trim(), Normalizer.Form.NFD)
            .replace(DIACRITICS, "")
            .lowercase(Locale.ROOT)
            .replace(IGNORED_PUNCTUATION, "")
            .replace(WHITESPACE, " ")
            .trim()
        return ALIASES[folded] ?: folded
    }

    /** Le chiavi di un credito grezzo. Una carta a quattro mani conta per entrambi. */
    fun keysOf(raw: String?): List<String> =
        credits(raw).map(::keyOf).filter { it.isNotEmpty() }.distinct()

    /**
     * Quale grafia mostrare, fra quelle che il catalogo usa per la stessa
     * persona: vince quella su piu' carte, e a pari merito la prima in ordine
     * alfabetico, perche' la schermata non deve cambiare nome fra un
     * caricamento e l'altro.
     *
     * Serve perche' la chiave non e' presentabile -- "k hoshiba" -- mentre
     * "K. Hoshiba" si'.
     */
    fun bestDisplayName(candidates: Map<String, Int>): String =
        candidates.entries
            .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
            .firstOrNull()?.key.orEmpty()
}

// ── Righe precalcolate ────────────────────────────────────────────────────────

/**
 * Un illustratore e tutto cio' che lo riguarda nel catalogo, senza ancora
 * sapere niente della collezione dell'utente.
 *
 * [rawNames] e' il pezzo che tiene insieme le due meta': il worker raggruppa
 * per nome grezzo, qui si uniscono per chiave, e per tornare a chiedere le
 * carte alla rotta `/v1/illustrators/{nome}/cards` servono di nuovo i nomi
 * grezzi da cui la voce e' nata.
 */
data class IllustratorEntry(
    val key: String,
    val displayName: String,
    val rawNames: List<String>,
    val cardApiIds: List<String>,
    val previewUrls: List<String>,
    val expansionCount: Int
)

/** Una riga della lista illustratori, con l'avanzamento gia' calcolato. */
data class IllustratorRow(
    val key: String,
    val displayName: String,
    val owned: Int,
    val total: Int,
    val expansionCount: Int,
    val previewUrls: List<String>,
    val isFollowed: Boolean
) {
    val percent: Float get() = CollectorLab.fillPercent(owned, total)
    val missing: Int get() = (total - owned).coerceAtLeast(0)
    val isComplete: Boolean get() = total > 0 && owned >= total
}

/** CLOSEST = "quasi fatti", come per i chase: i completati scendono in fondo. */
enum class IllustratorSort { CLOSEST, CARDS, NAME }

object Illustrators {

    /**
     * Incrocia il catalogo con la collezione. Una passata sola, con gli id
     * posseduti gia' indicizzati dal chiamante: [ownedApiIds] e' un Set proprio
     * perche' qui dentro si fanno migliaia di `contains`.
     */
    fun rows(
        entries: List<IllustratorEntry>,
        ownedApiIds: Set<String>,
        followedKeys: Set<String>
    ): List<IllustratorRow> = entries.map { entry ->
        IllustratorRow(
            key = entry.key,
            displayName = entry.displayName,
            owned = entry.cardApiIds.count { it in ownedApiIds },
            total = entry.cardApiIds.size,
            expansionCount = entry.expansionCount,
            previewUrls = entry.previewUrls,
            isFollowed = entry.key in followedKeys
        )
    }

    fun sort(rows: List<IllustratorRow>, sort: IllustratorSort): List<IllustratorRow> = when (sort) {
        // Un artista completato non e' piu' un obiettivo: lasciarlo in cima
        // seppellirebbe quelli ancora aperti. Stessa scelta di sortChases.
        IllustratorSort.CLOSEST -> rows.sortedWith(
            compareBy<IllustratorRow> { it.isComplete }
                .thenByDescending { it.percent }
                .thenBy { it.missing }
                .thenBy { it.displayName.lowercase(Locale.ROOT) }
        )
        IllustratorSort.CARDS -> rows.sortedWith(
            compareByDescending<IllustratorRow> { it.total }
                .thenBy { it.displayName.lowercase(Locale.ROOT) }
        )
        IllustratorSort.NAME -> rows.sortedBy { it.displayName.lowercase(Locale.ROOT) }
    }

    /**
     * Filtra per testo digitato. Confronta sulla chiave normalizzata, non sul
     * nome mostrato: chi scrive "kohski" senza accenti deve trovare comunque
     * chi il catalogo scrive con l'accento.
     */
    fun filter(rows: List<IllustratorRow>, query: String): List<IllustratorRow> {
        val q = IllustratorNames.keyOf(query)
        if (q.isEmpty()) return rows
        return rows.filter { it.key.contains(q) }
    }

    /** I seguiti restano in cima, nell'ordine scelto; gli altri sotto. */
    fun partitionFollowed(rows: List<IllustratorRow>): Pair<List<IllustratorRow>, List<IllustratorRow>> =
        rows.partition { it.isFollowed }
}

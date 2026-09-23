package com.emabuia.pokevault.util

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.Normalizer

/**
 * Dallo nome di una carta allo sprite del Pokemon che ci sta sopra.
 *
 * Serve perche' una copertina fatta di immagini di carte, rimpicciolita a
 * poche decine di dp in una riga di elenco, non si legge: si distingue un
 * rettangolo giallo da uno blu e basta. Uno sprite a quella dimensione si
 * riconosce ancora.
 *
 * Il collegamento nome -> numero non esiste da nessun'altra parte: il catalogo
 * D1 non ha una colonna con il numero di Pokedex, e l'unico posto dove l'app
 * usava degli sprite (WelcomeHeader) aveva una lista di sedici numeri scritti
 * a mano. La tabella sta in `assets/pokemon_species.txt`.
 *
 * Quello che questa classe NON fa: distinguere Charizard ex da Charizard V.
 * Lo sprite e' della specie, non della stampa. E' un compromesso accettato
 * quando si e' scelto di usarli nell'elenco dei mazzi, dove serve capire al
 * volo di che mazzo si tratta, non quale carta esatta contiene.
 */
object PokemonSpriteResolver {

    private const val ASSET = "pokemon_species.txt"

    /**
     * Sprite statici e non le GIF animate di generazione 5 usate altrove:
     * quelle esistono solo fino al Pokemon 649, e su un Koraidon rispondono
     * 404. Un elenco di mazzi moderni sarebbe stato pieno di buchi.
     */
    private const val SPRITE_BASE =
        "https://raw.githubusercontent.com/PokeAPI/sprites/master/sprites/pokemon"

    /**
     * Pezzi che una carta aggiunge al nome della specie.
     *
     * Non sono un elenco di eleganza ma la lista delle cose che fanno fallire
     * il confronto: senza, "Charizard ex" non trova Charizard. Le forme
     * regionali stanno fra i prefissi perche' PokeAPI le tiene come specie a
     * se' solo per alcune, e per le altre il nome base e' l'unica risposta
     * possibile.
     */
    private val SUFFIXES = setOf(
        "ex", "gx", "v", "vmax", "vstar", "vunion", "break", "prime",
        "legend", "star", "lvx", "delta"
    )

    /** id -> nome specie, caricata una volta sola. */
    @Volatile
    private var speciesByKey: Map<String, Int>? = null

    /**
     * La tabella e' pronta.
     *
     * E' stato Compose, non un'astrazione gratuita: leggere e analizzare
     * l'asset e' l'unica parte cara di tutto questo, e fatta dentro la
     * composizione della prima riga dell'elenco bloccava il thread principale
     * proprio mentre la lista si stava disegnando. Adesso il caricamento sta
     * su un thread di I/O e le righe lo osservano: l'elenco compare subito,
     * gli sprite un attimo dopo.
     */
    var isReady by mutableStateOf(false)
        private set

    /** Carica la tabella fuori dal thread principale. Chiamarla piu' volte non costa. */
    suspend fun preload(context: Context) {
        if (isReady) return
        val table = withContext(Dispatchers.IO) { load(context) }
        if (table.isNotEmpty()) isReady = true
    }

    private fun load(context: Context): Map<String, Int> {
        speciesByKey?.let { return it }
        synchronized(this) {
            speciesByKey?.let { return it }

            val parsed = try {
                context.applicationContext.assets.open(ASSET).bufferedReader().useLines { lines ->
                    lines.mapNotNull { line ->
                        if (line.isBlank() || line.startsWith("#")) return@mapNotNull null
                        val id = line.substringBefore(',').trim().toIntOrNull()
                            ?: return@mapNotNull null
                        val name = asciiKey(line.substringAfter(',').trim())
                        if (name.isEmpty()) null else name to id
                    }.toMap()
                }
            } catch (_: Exception) {
                // Un asset illeggibile non deve far saltare un elenco di mazzi:
                // si resta senza sprite, non senza schermata.
                emptyMap()
            }

            speciesByKey = parsed
            return parsed
        }
    }

    // Compilate una volta sola. Costruirle dentro le funzioni significava
    // ricompilarle a ogni riga della tabella e a ogni carta: piu' di duemila
    // compilazioni solo per caricare l'asset, e tutte sul thread principale
    // mentre l'elenco dei mazzi si sta disegnando.
    private val COMBINING_MARKS = Regex("\\p{Mn}+")

    /** Stesso schema per le due normalizzazioni: cambia solo con cosa si sostituisce. */
    private val NON_ALNUM = Regex("[^a-z0-9]+")

    /**
     * Chiave per un nome gia' ASCII: le righe della tabella lo sono tutte,
     * arrivando da PokeAPI in minuscolo con i trattini. Niente Normalizer e
     * niente espressioni regolari per 1025 righe, basta scartare i caratteri
     * che non servono.
     */
    private fun asciiKey(raw: String): String = buildString(raw.length) {
        for (ch in raw) {
            val c = ch.lowercaseChar()
            if (c in 'a'..'z' || c in '0'..'9') append(c)
        }
    }

    /**
     * La forma con cui due nomi si confrontano.
     *
     * Toglie accenti e qualunque cosa non sia una lettera o una cifra, spazi
     * compresi: cosi' "Mr. Mime", "mr-mime" e "MrMime" diventano la stessa
     * chiave, e "Flabebe" trova "Flabébé".
     */
    private fun matchKey(raw: String): String {
        val decomposed = Normalizer.normalize(raw.lowercase(), Normalizer.Form.NFD)
        return decomposed
            .replace(COMBINING_MARKS, "")
            .replace(NON_ALNUM, "")
    }

    private fun tokens(raw: String): List<String> {
        val decomposed = Normalizer.normalize(raw.lowercase(), Normalizer.Form.NFD)
        return decomposed
            .replace(COMBINING_MARKS, "")
            .replace(NON_ALNUM, " ")
            .trim()
            .split(' ')
            .filter { it.isNotEmpty() }
    }

    /**
     * Il numero del Pokemon disegnato su questa carta, o null.
     *
     * Prova il nome intero, poi leva i pezzi aggiunti dalla carta partendo
     * dalla fine ("Charizard ex"), poi dall'inizio ("M Charizard", "Brock's
     * Onix"). Ogni passo toglie un pezzo solo, cosi' un nome che e' davvero di
     * due parole -- "Iron Valiant", "Roaring Moon" -- viene trovato prima di
     * essere smontato.
     */
    fun dexNumberForCardName(context: Context, cardName: String): Int? {
        if (cardName.isBlank()) return null
        val table = load(context)
        if (table.isEmpty()) return null

        table[matchKey(cardName)]?.let { return it }

        var parts = tokens(cardName)
        if (parts.isEmpty()) return null

        // Dalla coda: i suffissi di stampa stanno li'.
        while (parts.size > 1 && parts.last() in SUFFIXES) {
            parts = parts.dropLast(1)
            table[matchKey(parts.joinToString(""))]?.let { return it }
        }

        // Dalla testa: forme regionali, mega, e i "di qualcuno" delle carte
        // vecchie -- "Brock's Onix" arriva qui come tre pezzi, perche'
        // l'apostrofo e' gia' diventato uno spazio. Si leva un pezzo alla
        // volta finche' resta qualcosa che la tabella conosce.
        while (parts.size > 1) {
            parts = parts.drop(1)
            table[matchKey(parts.joinToString(""))]?.let { return it }
        }

        return null
    }

    /**
     * L'indirizzo dello sprite, o null se la carta non e' un Pokemon noto --
     * oppure se la tabella non e' ancora pronta.
     *
     * Questa e' la via che usa la UI, e non carica mai niente da se': se
     * lo facesse, la prima riga dell'elenco pagherebbe la lettura dell'asset
     * dentro la propria composizione. Chi la chiama osserva [isReady] e si
     * ridisegna quando la tabella arriva.
     */
    /**
     * Questo indirizzo e' uno sprite, e non una vecchia copertina.
     *
     * Le copertine dei deck salvate prima di questa versione sono immagini di
     * carte, e stanno nello stesso campo di quelle nuove. Senza distinguerle,
     * un deck vecchio si ripresenterebbe con una carta stirata al posto del
     * Pokemon: proprio la cosa che si e' tolta di mezzo. Quelle vengono
     * ignorate, e il mazzo torna alla scelta automatica.
     */
    fun isSpriteUrl(url: String): Boolean = url.startsWith(SPRITE_BASE)

    /**
     * Gli sprite dei Pokemon nominati dentro al nome di un archetipo.
     *
     * Il mazzo di un avversario si scrive a mano e non e' uno dei nostri,
     * quindi non ha copertine da mostrare: l'unica cosa che abbiamo e' come si
     * chiama. Ma un archetipo si chiama con i suoi Pokemon -- "Charizard ex",
     * "Raging Bolt Ogerpon", "Dragapult Dusknoir" -- e quelli la tabella li
     * conosce gia'.
     *
     * Legge i pezzi da sinistra provando prima i nomi lunghi: "Iron Valiant" e
     * "Roaring Moon" sono di due parole, e cercandoli una parola alla volta non
     * si troverebbero mai. Quello che non e' un Pokemon -- "ex", "Box",
     * "Control" -- non combacia e viene saltato senza far danni.
     */
    fun spriteUrlsForArchetype(context: Context, archetype: String, limit: Int = 2): List<String> {
        if (!isReady || archetype.isBlank() || limit <= 0) return emptyList()
        val table = load(context)
        if (table.isEmpty()) return emptyList()

        val parts = tokens(archetype)
        val found = LinkedHashSet<Int>()

        var i = 0
        while (i < parts.size && found.size < limit) {
            var consumed = 0
            // Tre pezzi di margine: le specie piu' lunghe ne hanno due
            // ("Great Tusk", "Iron Hands"), ma un nome scritto a mano puo'
            // infilarci di mezzo qualcosa.
            for (window in minOf(3, parts.size - i) downTo 1) {
                val id = table[matchKey(parts.subList(i, i + window).joinToString(""))]
                if (id != null) {
                    found += id
                    consumed = window
                    break
                }
            }
            i += if (consumed > 0) consumed else 1
        }

        return found.map { "$SPRITE_BASE/$it.png" }
    }

    fun spriteUrlForCardName(context: Context, cardName: String): String? {
        if (!isReady) return null
        val id = dexNumberForCardName(context, cardName) ?: return null
        return "$SPRITE_BASE/$id.png"
    }
}

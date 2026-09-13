package com.emabuia.pokevault.data.italian

import androidx.compose.runtime.Immutable
import java.util.Locale

/**
 * Le dimensioni su cui si puo' filtrare la ricerca carte, ricavate dal catalogo
 * italiano che l'app tiene gia' in cache.
 *
 * Stanno qui e non dentro CatalogRepository perche' servono in due posti che
 * devono restare d'accordo: chi costruisce il *vocabolario* dei filtri (dai
 * record del catalogo) e chi *filtra* (il ViewModel, sui risultati di ricerca).
 * Se le due derivazioni divergessero, il pannello offrirebbe valori che poi non
 * pescano niente.
 *
 * Il vocabolario si costruisce sempre scandendo il catalogo, mai da una lista
 * scritta a mano: cosi' compaiono solo i valori che esistono davvero, e una
 * variante nuova di una espansione futura entra da sola senza toccare il codice.
 */

/** Fascia di punti salute. Gli estremi sono inclusivi. */
enum class ItalianHpBucket(val key: String, val label: String, val min: Int, val max: Int) {
    UP_TO_60("hp_60", "Fino a 60 PS", 0, 60),
    FROM_70_TO_90("hp_90", "70 - 90 PS", 61, 90),
    FROM_100_TO_130("hp_130", "100 - 130 PS", 91, 130),
    FROM_140_TO_200("hp_200", "140 - 200 PS", 131, 200),
    OVER_200("hp_max", "Oltre 200 PS", 201, Int.MAX_VALUE);

    operator fun contains(hp: Int): Boolean = hp in min..max

    companion object {
        fun forHp(hp: Int): ItalianHpBucket? = entries.firstOrNull { hp in it }
        fun forKey(key: String): ItalianHpBucket? = entries.firstOrNull { it.key == key }
    }
}

/**
 * Fascia di prezzo, in euro.
 *
 * A differenza delle altre dimensioni questa non sta nel catalogo: arriva dallo
 * snapshot prezzi, che copre solo le espansioni gia' scaricate. Le carte senza
 * prezzo noto non ricadono in nessuna fascia -- vengono escluse quando un
 * filtro prezzo e' attivo, ed e' il motivo per cui il pannello lo dice a
 * chiare lettere invece di farle sparire in silenzio.
 */
enum class ItalianPriceBucket(val key: String, val label: String, val min: Double, val max: Double) {
    UNDER_1("eur_1", "Meno di 1 €", 0.0, 1.0),
    FROM_1_TO_5("eur_5", "1 - 5 €", 1.0, 5.0),
    FROM_5_TO_20("eur_20", "5 - 20 €", 5.0, 20.0),
    FROM_20_TO_50("eur_50", "20 - 50 €", 20.0, 50.0),
    OVER_50("eur_max", "Oltre 50 €", 50.0, Double.MAX_VALUE);

    operator fun contains(price: Double): Boolean = price >= min && price < max

    companion object {
        fun forPrice(price: Double): ItalianPriceBucket? = entries.firstOrNull { price in it }
        fun forKey(key: String): ItalianPriceBucket? = entries.firstOrNull { it.key == key }
    }
}

/**
 * Le dimensioni selezionate nel pannello.
 *
 * Restringono i risultati della ricerca per nome, non il catalogo: selezionare
 * "Pokémon" da solo non vuol dire "dammi tutti i Pokémon che esistono", vuol
 * dire "fra quello che ho cercato, tienimi i Pokémon". Il prezzo sta a parte
 * perche' non arriva dal catalogo ma dallo snapshot (vedi [ItalianPriceBucket]).
 */
@Immutable
data class ItalianCardSearchFilter(
    val supertypes: Set<String> = emptySet(),
    val types: Set<String> = emptySet(),
    val rarities: Set<String> = emptySet(),
    val expansionIds: Set<String> = emptySet(),
    val variants: Set<String> = emptySet(),
    val hpBuckets: Set<ItalianHpBucket> = emptySet()
) {
    val isEmpty: Boolean
        get() = supertypes.isEmpty() && types.isEmpty() && rarities.isEmpty() &&
            expansionIds.isEmpty() && variants.isEmpty() && hpBuckets.isEmpty()

    val activeCount: Int
        get() = supertypes.size + types.size + rarities.size +
            expansionIds.size + variants.size + hpBuckets.size
}

/** Una espansione come voce di filtro: codice tecnico, nome leggibile, serie. */
@Immutable
data class ItalianExpansionFacet(
    val id: String,
    val label: String,
    val series: String,
    val cardCount: Int
)

/**
 * Le voci disponibili nel pannello filtri.
 *
 * Sono quelle dei risultati della ricerca corrente, o quelle di tutto il
 * catalogo finche' una ricerca non c'e'. I conteggi servono solo a ordinarle
 * mentre si costruiscono (le piu' frequenti per prime) e non escono di li':
 * nel pannello i numeri non si mostrano.
 */
@Immutable
data class ItalianSearchFacets(
    val supertypes: List<String> = emptyList(),
    val types: List<String> = emptyList(),
    val rarities: List<String> = emptyList(),
    val variants: List<String> = emptyList(),
    val hpBuckets: List<ItalianHpBucket> = emptyList(),
    val expansions: List<ItalianExpansionFacet> = emptyList(),
    val priceBuckets: List<ItalianPriceBucket> = emptyList()
) {
    val isEmpty: Boolean
        get() = supertypes.isEmpty() && types.isEmpty() && rarities.isEmpty() &&
            variants.isEmpty() && hpBuckets.isEmpty() && expansions.isEmpty()

    /** Le serie presenti, nell'ordine in cui compaiono fra le espansioni. */
    val series: List<String>
        get() = expansions.map { it.series }.filter { it.isNotBlank() }.distinct()
}

object ItalianCardFacets {

    /** Prefissi di chiave per [ItalianSearchFacets.counts]. */
    const val DIMENSION_SUPERTYPE = "supertype"
    const val DIMENSION_TYPE = "type"
    const val DIMENSION_RARITY = "rarity"
    const val DIMENSION_VARIANT = "variant"
    const val DIMENSION_HP = "hp"
    const val DIMENSION_EXPANSION = "expansion"

    /** Carta senza suffisso di meccanica: la stragrande maggioranza del catalogo. */
    const val VARIANT_BASE = "Base"

    /**
     * I suffissi di meccanica riconosciuti, in forma normalizzata.
     *
     * Si guarda l'ultimo pezzo del nome perche' e' li' che il gioco li mette, in
     * italiano come in inglese: "Charizard ex", "Mewtwo VMAX", "Lucario-GX". Il
     * confronto e' senza maiuscole cosi' l'"ex" moderno e l'"EX" del blocco XY
     * finiscono nella stessa voce -- per chi cerca sono la stessa cosa.
     */
    private val KNOWN_VARIANTS = mapOf(
        "ex" to "ex",
        "gx" to "GX",
        "v" to "V",
        "vmax" to "VMAX",
        "vstar" to "VSTAR",
        "vunion" to "V-UNION",
        "break" to "BREAK",
        "prisma" to "Prisma",
        "lv.x" to "LV.X"
    )

    private val SPLIT_REGEX = Regex("""[\s\-–—]+""")

    /**
     * Categoria della carta: Pokémon, Allenatore o Energia.
     *
     * record.tipo e' il tipo elementale (Fuoco/Acqua/...) e lo hanno solo i
     * Pokémon -- Allenatore ed Energia ce l'hanno sempre a NULL. Quindi la
     * discriminante e' ps, e fra le carte senza ps le Energie sono quelle il cui
     * nome inizia per "Energia" (le macchine che la nominano soltanto, tipo
     * "Recupero di Energia Plus", non ci iniziano).
     */
    fun supertypeOf(record: ItalianCardRecord): String {
        if ((record.ps?.toIntOrNull() ?: 0) > 0) return "Pokémon"
        return if (record.nome.trim().startsWith("Energia", ignoreCase = true)) "Energy" else "Trainer"
    }

    /** I tipi elementali della carta: D1 li consegna come "Tipo1, Tipo2" in un campo solo. */
    fun typesOf(record: ItalianCardRecord): List<String> =
        record.tipo.orEmpty()
            .split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }

    fun rarityOf(record: ItalianCardRecord): String? =
        record.rarity?.trim()?.takeIf { it.isNotBlank() }

    fun hpOf(record: ItalianCardRecord): Int? = record.ps?.trim()?.toIntOrNull()?.takeIf { it > 0 }

    fun hpBucketOf(record: ItalianCardRecord): ItalianHpBucket? =
        hpOf(record)?.let(ItalianHpBucket::forHp)

    /** La meccanica della carta ("ex", "VMAX", ...), o [VARIANT_BASE] se non ne ha. */
    fun variantOf(record: ItalianCardRecord): String = variantOfName(record.nome)

    /**
     * Come [variantOf], ma sul nome nudo.
     *
     * Il vocabolario dei filtri si costruisce sui record del catalogo, i
     * risultati di ricerca sono gia' TcgCard: la regola deve restare una sola,
     * o il chip "ex" smetterebbe di pescare le proprie carte.
     */
    fun variantOfName(name: String): String {
        val lastToken = name.trim()
            .split(SPLIT_REGEX)
            .lastOrNull()
            ?.trim()
            ?.lowercase(Locale.ROOT)
            ?: return VARIANT_BASE
        return KNOWN_VARIANTS[lastToken] ?: VARIANT_BASE
    }

    fun expansionIdOf(record: ItalianCardRecord): String =
        record.espansioneId.trim().lowercase(Locale.ROOT)

}

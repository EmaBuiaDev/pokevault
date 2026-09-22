package com.emabuia.pokevault.util

import com.emabuia.pokevault.data.model.CardClassifier
import com.emabuia.pokevault.data.model.PokemonCard
import com.emabuia.pokevault.data.model.collectionCardKey
import java.util.Locale

/**
 * Raggruppamento, filtri e ordinamenti di "Le mie carte".
 *
 * Prima erano divisi fra il ViewModel (filtro, sui documenti) e la schermata
 * (raggruppamento per carta, per espansione, e un secondo ordinamento), e la
 * parte nella schermata girava dentro `remember` sul thread principale a ogni
 * nuovo snapshot. Qui sono funzioni pure: si calcolano una volta, fuori dalla
 * composizione, e si possono testare.
 *
 * L'unita' e' la **carta**, non la stampa: una tessera per carta, come mostra
 * la collezione. Filtri e ordinamenti lavorano sul gruppo, per questo il
 * prezzo di una carta e' quello della sua stampa piu' cara e non quello della
 * prima che capita.
 */

// ── Criteri ───────────────────────────────────────────────────────────────────

enum class CollectionSort { NUMBER, NEWEST, NAME, PRICE_DESC, PRICE_ASC }

enum class ExpansionOrder { NAME, RECENT, MOST_CARDS, FEWEST_CARDS, MOST_VALUE }

enum class CollectionLayout { BY_EXPANSION, ALL }

enum class CardCategory { ALL, POKEMON, TRAINER, ENERGY }

/**
 * Fasce di valore per copia. Il limite superiore e' escluso: 10,00 EUR sta in
 * [FROM_10_TO_50], non in [FROM_1_TO_10].
 */
enum class ValueBucket(val min: Double, val maxExclusive: Double) {
    NO_PRICE(Double.NEGATIVE_INFINITY, 0.0000001),
    UNDER_1(0.0000001, 1.0),
    FROM_1_TO_10(1.0, 10.0),
    FROM_10_TO_50(10.0, 50.0),
    OVER_50(50.0, Double.POSITIVE_INFINITY);

    fun matches(value: Double): Boolean = value >= min && value < maxExclusive
}

/**
 * I filtri attivi. Tutti a scelta multipla tranne la categoria: dentro una
 * dimensione le scelte si sommano (Fuoco O Acqua), fra dimensioni diverse si
 * restringono (Fuoco E Reverse).
 */
data class CollectionFilter(
    val query: String = "",
    val category: CardCategory = CardCategory.ALL,
    /** Tipi nella lingua dell'app, come li scrive [AppLocale.translateType]. */
    val types: Set<String> = emptySet(),
    /** Rarita' per etichetta tradotta: i sinonimi del catalogo stanno insieme. */
    val rarities: Set<String> = emptySet(),
    /** Espansioni per nome mostrato. */
    val expansions: Set<String> = emptySet(),
    val variants: Set<String> = emptySet(),
    val languages: Set<String> = emptySet(),
    val values: Set<ValueBucket> = emptySet(),
    val onlyDuplicates: Boolean = false
) {
    /** Quanti filtri sono attivi: la ricerca testuale non conta, si vede gia'. */
    val activeCount: Int
        get() = (if (category != CardCategory.ALL) 1 else 0) +
            types.size + rarities.size + expansions.size + variants.size +
            languages.size + values.size + (if (onlyDuplicates) 1 else 0)

    val isEmpty: Boolean get() = activeCount == 0 && query.isBlank()
}

// ── Il gruppo: una carta con tutte le sue stampe ──────────────────────────────

/**
 * Una carta della collezione, con tutto quello che serve a filtrarla e
 * mostrarla gia' calcolato.
 *
 * Costruire queste righe costa (traduzioni, classificazione, normalizzazione
 * del nome del set): si fa una volta per snapshot, non a ogni tasto premuto
 * nella ricerca come prima.
 */
data class CardGroup(
    /** [collectionCardKey]: la stessa chiave che la rotta del dettaglio riceve. */
    val key: String,
    /** Le stampe, nell'ordine Normale, Reverse, Holo... */
    val cards: List<PokemonCard>,
    /** La stampa mostrata, con la quantita' di tutte le stampe sommate. */
    val representative: PokemonCard,
    val totalQuantity: Int,
    val totalValue: Double,
    /** Il valore per copia della stampa piu' cara: e' il prezzo della carta. */
    val topValue: Double,
    val variants: Set<String>,
    val languages: Set<String>,
    val newestAddedSeconds: Long,
    /** Il valore grezzo di `set`, o l'etichetta di espansione sconosciuta. */
    val expansion: String,
    val expansionLabel: String,
    val category: String,
    val types: Set<String>,
    val rarityLabel: String,
    val number: String,
    val numberValue: Int,
    val searchText: String
) {
    val isDuplicate: Boolean get() = totalQuantity > 1
}

data class ExpansionGroupSection(
    val expansion: String,
    val label: String,
    val groups: List<CardGroup>,
    val totalQuantity: Int,
    val totalValue: Double,
    val newestAddedSeconds: Long
)

data class FacetCount(val value: String, val count: Int)

/** Le opzioni di ogni filtro con quante carte ci stanno dentro. */
data class CollectionFacets(
    val categories: Map<CardCategory, Int> = emptyMap(),
    val types: List<FacetCount> = emptyList(),
    val rarities: List<FacetCount> = emptyList(),
    /** Per ogni etichetta di rarita', una stringa grezza: serve a disegnarne il simbolo. */
    val raritySamples: Map<String, String> = emptyMap(),
    val expansions: List<FacetCount> = emptyList(),
    val variants: List<FacetCount> = emptyList(),
    val languages: List<FacetCount> = emptyList(),
    val values: Map<ValueBucket, Int> = emptyMap(),
    val duplicates: Int = 0
)

object CollectionBrowser {

    private val WHITESPACE = Regex("\\s+")

    /**
     * Da documenti a carte. Le stampe della stessa carta ([collectionCardKey])
     * diventano una tessera sola.
     *
     * [unknownExpansion] e' l'etichetta per le carte senza set: e' tradotta,
     * quindi la passa chi chiama invece di deciderla qui.
     */
    fun group(cards: List<PokemonCard>, unknownExpansion: String): List<CardGroup> =
        cards
            .groupBy { it.collectionCardKey() }
            .map { (key, stampe) ->
                val ordered = stampe.sortedBy { CardVariantOrder.of(it.variant) }
                val first = ordered.first()
                val totalQuantity = ordered.sumOf { it.quantity }
                val expansion = first.set.trim().ifBlank { unknownExpansion }
                // Spazi collassati come faceva il vecchio filtro per set: senza,
                // un nome salvato con uno spazio doppio diventava un'espansione
                // a parte, e il filtro che oggi la trova smetteva di trovarla.
                val expansionLabel = if (first.set.isBlank()) unknownExpansion
                else AppLocale.displaySetName(first.set).trim().replace(WHITESPACE, " ").ifBlank { expansion }
                val rarityLabel = if (first.rarity.isBlank()) "" else AppLocale.translateRarity(first.rarity).trim()
                val types = first.type.split(",")
                    .map { AppLocale.translateType(it.trim()) }
                    .filter { it.isNotBlank() }
                    .toSet()
                CardGroup(
                    key = key,
                    cards = ordered,
                    representative = first.copy(quantity = totalQuantity),
                    totalQuantity = totalQuantity,
                    totalValue = ordered.sumOf { it.estimatedValue * it.quantity },
                    topValue = ordered.maxOfOrNull { it.estimatedValue } ?: 0.0,
                    variants = ordered.mapTo(linkedSetOf()) { it.variant }.filter { it.isNotBlank() }.toSet(),
                    languages = ordered.mapTo(linkedSetOf()) { cleanLanguage(it.language) }.filter { it.isNotBlank() }.toSet(),
                    newestAddedSeconds = ordered.maxOf { it.addedAt?.seconds ?: Long.MIN_VALUE },
                    expansion = expansion,
                    expansionLabel = expansionLabel,
                    category = first.classify(),
                    types = types,
                    rarityLabel = rarityLabel,
                    number = first.cardNumber,
                    numberValue = first.cardNumber.takeWhile { it.isDigit() }.toIntOrNull() ?: Int.MAX_VALUE,
                    searchText = listOf(first.name, expansionLabel, first.set, rarityLabel, first.rarity, first.cardNumber)
                        .joinToString(" ")
                        .lowercase(Locale.ROOT)
                        .replace(WHITESPACE, " ")
                )
            }

    /**
     * "🇮🇹 Italiano" e "Italiano" sono la stessa lingua: le carte vecchie sono
     * salvate senza bandiera, quelle nuove con. Senza ripulirle il filtro
     * mostrerebbe due voci per la stessa lingua.
     */
    internal fun cleanLanguage(raw: String): String =
        raw.trim().dropWhile { !it.isLetter() }.trim()

    fun matches(group: CardGroup, filter: CollectionFilter): Boolean {
        val query = filter.query.trim().lowercase(Locale.ROOT)
        if (query.isNotEmpty() && !group.searchText.contains(query)) return false

        when (filter.category) {
            CardCategory.ALL -> Unit
            CardCategory.POKEMON -> if (group.category != CardClassifier.POKEMON) return false
            CardCategory.TRAINER -> if (group.category != CardClassifier.TRAINER) return false
            CardCategory.ENERGY -> if (group.category != CardClassifier.ENERGY) return false
        }

        // I tipi esistono solo sui Pokemon: chi sceglie "Fuoco" non vuole
        // vedersi tornare gli Allenatori.
        if (filter.types.isNotEmpty()) {
            if (group.category != CardClassifier.POKEMON) return false
            if (group.types.none { type -> filter.types.any { it.equals(type, ignoreCase = true) } }) return false
        }

        if (filter.rarities.isNotEmpty() && group.rarityLabel !in filter.rarities) return false
        if (filter.expansions.isNotEmpty() && group.expansionLabel !in filter.expansions) return false
        // Una carta passa se ha ALMENO una delle stampe scelte: chi cerca le
        // Reverse vuole vedere la carta anche se ne ha pure la Normale.
        if (filter.variants.isNotEmpty() && group.variants.none { it in filter.variants }) return false
        if (filter.languages.isNotEmpty() && group.languages.none { it in filter.languages }) return false
        if (filter.values.isNotEmpty() && filter.values.none { it.matches(group.topValue) }) return false
        if (filter.onlyDuplicates && !group.isDuplicate) return false
        return true
    }

    fun filter(groups: List<CardGroup>, filter: CollectionFilter): List<CardGroup> =
        if (filter.isEmpty) groups else groups.filter { matches(it, filter) }

    fun sort(groups: List<CardGroup>, sort: CollectionSort): List<CardGroup> = when (sort) {
        // La parte numerica come numero, il resto come testo: "2" prima di
        // "10", e "TG12" dopo i numeri puri invece che mescolato.
        CollectionSort.NUMBER -> groups.sortedWith(
            compareBy<CardGroup> { it.numberValue }.thenBy { it.number }.thenBy { it.representative.name.lowercase(Locale.ROOT) }
        )
        // Le carte vecchie senza data d'inserimento finiscono in fondo, non a caso.
        CollectionSort.NEWEST -> groups.sortedWith(
            compareByDescending<CardGroup> { it.newestAddedSeconds }.thenBy { it.representative.name.lowercase(Locale.ROOT) }
        )
        CollectionSort.NAME -> groups.sortedWith(
            compareBy<CardGroup> { it.representative.name.lowercase(Locale.ROOT) }.thenBy { it.numberValue }
        )
        CollectionSort.PRICE_DESC -> groups.sortedWith(
            compareByDescending<CardGroup> { it.topValue }.thenBy { it.representative.name.lowercase(Locale.ROOT) }
        )
        // Crescente, ma senza prezzo in fondo: "0,00" non vuol dire "costa
        // poco", vuol dire "non lo so", e in cima alla lista sarebbe rumore.
        CollectionSort.PRICE_ASC -> groups.sortedWith(
            compareBy<CardGroup> { it.topValue <= 0.0 }.thenBy { it.topValue }.thenBy { it.representative.name.lowercase(Locale.ROOT) }
        )
    }

    /** Le carte (gia' filtrate e ordinate) divise per espansione. */
    fun sections(groups: List<CardGroup>, order: ExpansionOrder): List<ExpansionGroupSection> {
        val sections = groups
            .groupBy { it.expansion }
            .map { (expansion, inSet) ->
                ExpansionGroupSection(
                    expansion = expansion,
                    label = inSet.first().expansionLabel,
                    groups = inSet,
                    totalQuantity = inSet.sumOf { it.totalQuantity },
                    totalValue = inSet.sumOf { it.totalValue },
                    newestAddedSeconds = inSet.maxOf { it.newestAddedSeconds }
                )
            }
        val byName = compareBy<ExpansionGroupSection> { it.label.lowercase(Locale.ROOT) }
        return when (order) {
            ExpansionOrder.NAME -> sections.sortedWith(byName)
            ExpansionOrder.RECENT -> sections.sortedWith(compareByDescending<ExpansionGroupSection> { it.newestAddedSeconds }.then(byName))
            ExpansionOrder.MOST_CARDS -> sections.sortedWith(compareByDescending<ExpansionGroupSection> { it.totalQuantity }.then(byName))
            ExpansionOrder.FEWEST_CARDS -> sections.sortedWith(compareBy<ExpansionGroupSection> { it.totalQuantity }.then(byName))
            ExpansionOrder.MOST_VALUE -> sections.sortedWith(compareByDescending<ExpansionGroupSection> { it.totalValue }.then(byName))
        }
    }

    /**
     * Le opzioni dei filtri, con i conteggi sull'intera collezione. Si mostrano
     * solo i valori che la collezione contiene: un chip "Drago (0)" e' una
     * scelta che non porta da nessuna parte.
     */
    fun facets(groups: List<CardGroup>): CollectionFacets {
        fun counts(values: (CardGroup) -> Iterable<String>): List<FacetCount> =
            groups.flatMap { g -> values(g).filter { it.isNotBlank() }.distinct() }
                .groupingBy { it }
                .eachCount()
                .map { (value, count) -> FacetCount(value, count) }
                .sortedWith(compareByDescending<FacetCount> { it.count }.thenBy { it.value.lowercase(Locale.ROOT) })

        return CollectionFacets(
            categories = mapOf(
                CardCategory.ALL to groups.size,
                CardCategory.POKEMON to groups.count { it.category == CardClassifier.POKEMON },
                CardCategory.TRAINER to groups.count { it.category == CardClassifier.TRAINER },
                CardCategory.ENERGY to groups.count { it.category == CardClassifier.ENERGY }
            ),
            types = counts { g -> if (g.category == CardClassifier.POKEMON) g.types else emptySet() },
            rarities = counts { listOf(it.rarityLabel) },
            raritySamples = groups
                .filter { it.rarityLabel.isNotBlank() }
                .associate { it.rarityLabel to it.representative.rarity },
            expansions = counts { listOf(it.expansionLabel) },
            variants = counts { it.variants }
                .sortedBy { CardVariantOrder.of(it.value) },
            languages = counts { it.languages },
            values = ValueBucket.entries.associateWith { bucket -> groups.count { bucket.matches(it.topValue) } },
            duplicates = groups.count { it.isDuplicate }
        )
    }
}

/**
 * L'ordine delle stampe, lo stesso dei badge sulle tessere. Sta qui, e non si
 * usa quello dei componenti UI, perche' questo file deve restare puro: niente
 * Compose, cosi' gira nei test e fuori dal thread principale.
 */
internal object CardVariantOrder {
    private val ORDER = listOf("Normal", "Reverse", "Holo", "1st Edition", "1st Edition Holo", "Unlimited Holo")
    fun of(variant: String): Int = ORDER.indexOf(variant).let { if (it < 0) ORDER.size else it }
}

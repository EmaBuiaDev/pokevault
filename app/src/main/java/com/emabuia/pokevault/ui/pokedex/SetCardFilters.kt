package com.emabuia.pokevault.ui.pokedex

import androidx.compose.runtime.Immutable
import com.emabuia.pokevault.data.italian.ItalianHpBucket
import com.emabuia.pokevault.data.italian.ItalianPriceBucket
import com.emabuia.pokevault.data.model.CardOptions
import com.emabuia.pokevault.data.remote.TcgCard
import com.emabuia.pokevault.util.RarityUtils
import java.util.Locale

/**
 * I filtri dentro un'espansione.
 *
 * Prima stavano in due posti -- la rarita' nella schermata, il resto nel
 * ViewModel -- e si azzeravano in momenti diversi: tornando dall'illustratore
 * la rarita' ripartiva da capo e gli altri no. Ora sono un valore solo, nel
 * ViewModel, e si azzerano tutti insieme cambiando espansione.
 *
 * Dentro un gruppo le voci sono in OR ("Fuoco o Acqua"), fra gruppi in AND.
 */
@Immutable
data class SetCardFilter(
    val ownership: SetOwnership = SetOwnership.ALL,
    val supertypes: Set<String> = emptySet(),
    val types: Set<String> = emptySet(),
    /** Stringa esatta del catalogo: l'etichetta raggruppata unirebbe rarita' diverse. */
    val rarities: Set<String> = emptySet(),
    val mechanics: Set<CardMechanic> = emptySet(),
    val stages: Set<String> = emptySet(),
    val hpBuckets: Set<ItalianHpBucket> = emptySet(),
    val artists: Set<String> = emptySet(),
    val priceBuckets: Set<ItalianPriceBucket> = emptySet(),
    val onlyWishlist: Boolean = false,
    /** Possedute, ma non in tutte le stampe base. */
    val missingVariant: Boolean = false
) {
    /** I filtri del pannello; "Mancanti"/"Possedute" hanno i loro chip fuori. */
    val panelCount: Int
        get() = supertypes.size + types.size + rarities.size + mechanics.size + stages.size +
            hpBuckets.size + artists.size + priceBuckets.size +
            (if (onlyWishlist) 1 else 0) + (if (missingVariant) 1 else 0)

    val isEmpty: Boolean get() = ownership == SetOwnership.ALL && panelCount == 0

    val usesPrices: Boolean get() = priceBuckets.isNotEmpty()

    /** Svuota il pannello; Mancanti/Possedute restano, hanno i loro chip. */
    fun clearPanel(): SetCardFilter = SetCardFilter(ownership = ownership)
}

enum class SetOwnership { ALL, MISSING, OWNED }

enum class SetCardSort {
    NUMBER, PRICE_DESC, PRICE_ASC, NAME, RARITY, HP_DESC;

    val usesPrices: Boolean get() = this == PRICE_DESC || this == PRICE_ASC
}

/**
 * La regola speciale nel nome. Il catalogo non la porta in un campo (i
 * sottotipi hanno solo la fase), quindi si legge dal nome come fa l'import
 * dei mazzi: "Dragapult-ex", "Arceus V ASTRO", "Mewtwo-EX".
 */
enum class CardMechanic(val labelIt: String, val labelEn: String) {
    EX("ex", "ex"),
    EX_CLASSIC("EX", "EX"),
    GX("GX", "GX"),
    V("V", "V"),
    VMAX("VMAX", "VMAX"),
    VSTAR("V ASTRO", "VSTAR"),
    VUNION("V-UNIONE", "V-UNION"),
    BREAK("TURBO", "BREAK"),
    LVX("LV.X", "LV.X");

    fun label(italian: Boolean): String = if (italian) labelIt else labelEn
}

/** La regola speciale della carta, o null se e' una carta normale. */
fun cardMechanicOf(name: String): CardMechanic? {
    // I pezzi con la loro maiuscola: "ex" (Scarlatto e Violetto) e "EX"
    // (Bianco e Nero) sono due regole diverse.
    val tokens = name.split(Regex("[^\\p{L}\\p{N}.]+")).filter { it.isNotBlank() }
    val lower = tokens.map { it.lowercase(Locale.ROOT) }
    return when {
        "vmax" in lower -> CardMechanic.VMAX
        "vstar" in lower -> CardMechanic.VSTAR
        lower.zipWithNext().any { (a, b) -> a == "v" && b == "astro" } -> CardMechanic.VSTAR
        "vunion" in lower -> CardMechanic.VUNION
        lower.zipWithNext().any { (a, b) -> a == "v" && (b == "union" || b == "unione") } -> CardMechanic.VUNION
        "gx" in lower -> CardMechanic.GX
        "lv.x" in lower || "lvx" in lower -> CardMechanic.LVX
        // Maiuscolo: "Turbo" puo' stare nel nome di un Allenatore.
        tokens.any { it == "BREAK" || it == "TURBO" } -> CardMechanic.BREAK
        tokens.any { it == "EX" } -> CardMechanic.EX_CLASSIC
        tokens.any { it == "ex" } -> CardMechanic.EX
        // "V" da solo: solo se maiuscolo e in fondo al nome, perche' una "v"
        // in mezzo puo' essere altro.
        tokens.lastOrNull() == "V" -> CardMechanic.V
        else -> null
    }
}

/** I PS come numero, se la carta li ha. */
fun TcgCard.hpValue(): Int? = hp?.filter(Char::isDigit)?.toIntOrNull()?.takeIf { it > 0 }

/** Lo stesso ordine di numero di sempre: le cifre del numero, le carte senza in fondo. */
fun TcgCard.numberSortKey(): Int = number.replace(Regex("[^0-9]"), "").toIntOrNull() ?: Int.MAX_VALUE

/** Lo stato del giocatore che i filtri leggono. */
class SetCardContext(
    val ownedIds: Set<String>,
    val ownedVariants: Map<String, Set<String>>,
    val wishlistedIds: Set<String>,
    /** Prezzo in euro, null se non lo conosciamo. */
    val priceOf: (TcgCard) -> Double?
)

/**
 * Possedute, ma con almeno una stampa base che manca. Lo stesso confronto
 * della scheda della carta (le pastiglie "gia' tua"), cosi' i due non si
 * contraddicono.
 */
fun hasMissingVariant(card: TcgCard, ctx: SetCardContext): Boolean {
    if (card.id !in ctx.ownedIds) return false
    val owned = ctx.ownedVariants[card.id].orEmpty()
    if (owned.isEmpty()) return false
    return CardOptions.getBaseVariantsForCard(card).any { it !in owned }
}

fun matchesSetCardFilter(card: TcgCard, filter: SetCardFilter, ctx: SetCardContext): Boolean {
    when (filter.ownership) {
        SetOwnership.ALL -> Unit
        SetOwnership.MISSING -> if (card.id in ctx.ownedIds) return false
        SetOwnership.OWNED -> if (card.id !in ctx.ownedIds) return false
    }
    if (filter.supertypes.isNotEmpty() && card.supertype !in filter.supertypes) return false
    if (filter.types.isNotEmpty() && card.types.orEmpty().none { it in filter.types }) return false
    if (filter.rarities.isNotEmpty() && card.rarity !in filter.rarities) return false
    if (filter.mechanics.isNotEmpty() && cardMechanicOf(card.name) !in filter.mechanics) return false
    if (filter.stages.isNotEmpty() && card.subtypes.orEmpty().none { it in filter.stages }) return false
    if (filter.hpBuckets.isNotEmpty()) {
        val hp = card.hpValue() ?: return false
        if (filter.hpBuckets.none { hp in it }) return false
    }
    if (filter.artists.isNotEmpty() && card.artist?.trim() !in filter.artists) return false
    if (filter.priceBuckets.isNotEmpty()) {
        // Senza prezzo noto la carta non sta in nessuna fascia.
        val price = ctx.priceOf(card) ?: return false
        if (filter.priceBuckets.none { price in it }) return false
    }
    if (filter.onlyWishlist && card.id !in ctx.wishlistedIds) return false
    if (filter.missingVariant && !hasMissingVariant(card, ctx)) return false
    return true
}

/**
 * L'ordine della griglia. A parita' si torna sempre al numero, e le carte
 * senza prezzo o senza PS vanno in fondo in entrambi i versi: in cima a
 * "dal piu' caro" non devono comparire carte di cui non sappiamo niente.
 */
fun sortSetCards(cards: List<TcgCard>, sort: SetCardSort, priceOf: (TcgCard) -> Double?): List<TcgCard> {
    val byNumber = compareBy<TcgCard> { it.numberSortKey() }
    val comparator: Comparator<TcgCard> = when (sort) {
        SetCardSort.NUMBER -> byNumber
        SetCardSort.PRICE_DESC -> compareBy<TcgCard> { priceOf(it) == null }
            .thenByDescending { priceOf(it) ?: 0.0 }
            .then(byNumber)
        SetCardSort.PRICE_ASC -> compareBy<TcgCard> { priceOf(it) == null }
            .thenBy { priceOf(it) ?: 0.0 }
            .then(byNumber)
        SetCardSort.NAME -> compareBy<TcgCard> { it.name.lowercase(Locale.ROOT) }.then(byNumber)
        SetCardSort.RARITY -> compareByDescending<TcgCard> { RarityUtils.getRarityInfo(it.rarity).sortOrder }
            .then(byNumber)
        SetCardSort.HP_DESC -> compareBy<TcgCard> { it.hpValue() == null }
            .thenByDescending { it.hpValue() ?: 0 }
            .then(byNumber)
    }
    return cards.sortedWith(comparator)
}

/** Una voce del pannello col numero di carte del set che la portano. */
data class FacetCount<T>(val value: T, val count: Int)

/**
 * Le voci del pannello, prese dalle carte del set: si offre solo quello che
 * c'e' (niente "VMAX" in un set di Scarlatto e Violetto).
 */
@Immutable
data class SetCardFacets(
    val supertypes: List<FacetCount<String>> = emptyList(),
    val types: List<FacetCount<String>> = emptyList(),
    val rarities: List<FacetCount<String>> = emptyList(),
    val mechanics: List<FacetCount<CardMechanic>> = emptyList(),
    val stages: List<FacetCount<String>> = emptyList(),
    val hpBuckets: List<FacetCount<ItalianHpBucket>> = emptyList(),
    val artists: List<FacetCount<String>> = emptyList()
) {
    companion object {
        fun from(cards: List<TcgCard>): SetCardFacets {
            fun <T> List<T>.counted(): List<FacetCount<T>> =
                groupingBy { it }.eachCount().map { (value, count) -> FacetCount(value, count) }

            return SetCardFacets(
                supertypes = cards.map { it.supertype }.filter { it.isNotBlank() }.counted()
                    .sortedBy { it.value },
                types = cards.flatMap { it.types.orEmpty().distinct() }.filter { it.isNotBlank() }.counted()
                    .sortedBy { it.value },
                rarities = cards.mapNotNull { it.rarity?.takeIf(String::isNotBlank) }.counted()
                    .sortedBy { RarityUtils.getRarityInfo(it.value).sortOrder },
                mechanics = cards.mapNotNull { cardMechanicOf(it.name) }.counted()
                    .sortedBy { it.value.ordinal },
                stages = cards.flatMap { it.subtypes.orEmpty().distinct() }.filter { it.isNotBlank() }.counted()
                    .sortedBy { it.value },
                hpBuckets = cards.mapNotNull { card -> card.hpValue()?.let(ItalianHpBucket::forHp) }.counted()
                    .sortedBy { it.value.ordinal },
                // I piu' presenti prima: sono quelli che si cercano.
                artists = cards.mapNotNull { it.artist?.trim()?.takeIf(String::isNotBlank) }.counted()
                    .sortedWith(compareByDescending<FacetCount<String>> { it.count }.thenBy { it.value })
            )
        }
    }
}

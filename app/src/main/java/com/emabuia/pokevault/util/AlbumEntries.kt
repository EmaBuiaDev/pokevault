package com.emabuia.pokevault.util

import com.emabuia.pokevault.data.model.Album
import com.emabuia.pokevault.data.model.AlbumCatalogCard
import com.emabuia.pokevault.data.model.CardClassifier
import com.emabuia.pokevault.data.model.PokemonCard
import com.emabuia.pokevault.data.remote.TcgCard

/**
 * Una tasca dell'album, gia' risolta.
 *
 * [owned] e' la copia in collezione, se c'e'; [catalog] la carta del catalogo
 * se la tasca e' nata da una ricerca. Una tasca del catalogo diventa
 * posseduta da sola quando la stessa carta entra in collezione.
 */
data class AlbumSlot(
    val key: String,
    val owned: PokemonCard?,
    val catalog: AlbumCatalogCard?
) {
    val isOwned: Boolean get() = owned != null
    val name: String get() = owned?.name ?: catalog?.name.orEmpty()
    val imageUrl: String get() = owned?.imageUrl?.takeIf { it.isNotBlank() } ?: catalog?.imageUrl.orEmpty()
    val set: String get() = owned?.set?.takeIf { it.isNotBlank() } ?: catalog?.set.orEmpty()
    val number: String get() = owned?.cardNumber?.takeIf { it.isNotBlank() } ?: catalog?.number.orEmpty()
    val rarity: String get() = owned?.rarity?.takeIf { it.isNotBlank() } ?: catalog?.rarity.orEmpty()

    /** Vale solo quello che hai: una carta da trovare non e' ancora valore dell'album. */
    val value: Double get() = owned?.estimatedValue ?: 0.0
}

/** Il contenuto di un album da riscrivere tutto insieme: tasche e carte del catalogo. */
data class AlbumContent(val cardIds: List<String>, val catalogCards: List<AlbumCatalogCard>)

/** Cosa aggiungere: le nuove voci in fondo e le carte del catalogo che servono a mostrarle. */
data class AlbumAdditions(val entries: List<String>, val catalogCards: List<AlbumCatalogCard>) {
    val isEmpty: Boolean get() = entries.isEmpty()
}

/**
 * Le tasche di un album: carte della collezione e carte del catalogo.
 *
 * Prima un album poteva contenere solo carte gia' in collezione, e per metterci
 * una carta bisognava uscire, cercarla, aggiungerla e tornare. Ora la si cerca
 * da dentro l'album: se ce l'hai entra la tua copia, se no entra "da trovare"
 * e si accende quando arriva.
 *
 * Funzioni pure: l'ordine delle tasche e' quello che l'utente ha costruito, e
 * qui si decide senza Firestore e senza composizione, quindi si prova.
 */
object AlbumEntries {

    const val CATALOG_PREFIX = "cat:"

    fun catalogEntry(apiId: String): String = CATALOG_PREFIX + apiId.trim()

    /** L'apiCardId di una voce del catalogo, o null se la voce e' una carta della collezione. */
    fun apiIdOf(entry: String): String? =
        if (entry.startsWith(CATALOG_PREFIX)) entry.removePrefix(CATALOG_PREFIX).trim().takeIf { it.isNotEmpty() } else null

    /**
     * La copia posseduta di ogni carta, per apiCardId. A parita' si tiene
     * quella con piu' copie: e' la stampa "principale" della collezione.
     */
    fun ownedByApiId(cards: List<PokemonCard>): Map<String, PokemonCard> =
        cards.asSequence()
            .filter { it.quantity >= 1 && it.apiCardId.isNotBlank() }
            .groupBy { it.apiCardId.trim() }
            .mapValues { (_, copies) -> copies.maxBy { it.quantity } }

    /**
     * Le tasche nell'ordine dell'album. Le voci che non portano piu' a niente
     * (carta tolta dalla collezione, carta del catalogo senza dati) si saltano,
     * come prima.
     */
    fun resolve(
        album: Album,
        ownedById: Map<String, PokemonCard>,
        ownedByApiId: Map<String, PokemonCard>
    ): List<AlbumSlot> {
        val catalogById = album.catalogCards.associateBy { it.apiId.trim() }
        return album.cardIds.mapNotNull { entry ->
            val apiId = apiIdOf(entry)
            if (apiId == null) {
                ownedById[entry]?.let { AlbumSlot(key = entry, owned = it, catalog = null) }
            } else {
                val catalog = catalogById[apiId]
                val owned = ownedByApiId[apiId]
                if (catalog == null && owned == null) null
                else AlbumSlot(key = entry, owned = owned, catalog = catalog)
            }
        }
    }

    /**
     * Le voci da aggiungere per le carte scelte nel catalogo.
     *
     * Se una carta e' gia' in collezione entra la tua copia, cosi' l'album
     * resta legato a quella e non a un doppione. Non entra due volte una carta
     * gia' presente, ne' come copia ne' come "da trovare", e non si va oltre
     * le tasche libere.
     */
    fun planCatalogAdditions(
        album: Album,
        picked: List<AlbumCatalogCard>,
        ownedByApiId: Map<String, PokemonCard>
    ): AlbumAdditions {
        val present = album.cardIds.toMutableSet()
        val presentApiIds = album.cardIds.mapNotNull(::apiIdOf).toMutableSet()
        ownedByApiId.forEach { (apiId, card) -> if (card.id in present) presentApiIds += apiId }
        val knownCatalog = album.catalogCards.map { it.apiId.trim() }.toSet()

        var room = (album.size - album.cardIds.size).coerceAtLeast(0)
        val entries = mutableListOf<String>()
        val newCatalog = mutableListOf<AlbumCatalogCard>()
        for (card in picked.distinctBy { it.apiId.trim() }) {
            if (room == 0) break
            val apiId = card.apiId.trim()
            if (apiId.isEmpty() || apiId in presentApiIds) continue
            val owned = ownedByApiId[apiId]
            val entry = owned?.id ?: catalogEntry(apiId)
            if (entry in present) continue
            entries += entry
            present += entry
            presentApiIds += apiId
            if (owned == null && apiId !in knownCatalog) newCatalog += card.copy(apiId = apiId)
            room--
        }
        return AlbumAdditions(entries, newCatalog)
    }

    /**
     * Quello che entra confermando il foglio: prima le carte scelte dalla
     * collezione, poi quelle del catalogo, tutte nelle tasche libere e senza
     * doppioni fra le due schede. Una sola scrittura per tutto.
     */
    fun planAdditions(
        album: Album,
        ownedIds: List<String>,
        picked: List<AlbumCatalogCard>,
        ownedByApiId: Map<String, PokemonCard>
    ): AlbumAdditions {
        val room = (album.size - album.cardIds.size).coerceAtLeast(0)
        val owned = ownedIds.distinct().filter { it !in album.cardIds && apiIdOf(it) == null }.take(room)
        val afterOwned = album.copy(cardIds = album.cardIds + owned)
        val fromCatalog = planCatalogAdditions(afterOwned, picked, ownedByApiId)
        return AlbumAdditions(owned + fromCatalog.entries, fromCatalog.catalogCards)
    }

    /** Senza la voce [key]; la carta del catalogo che non serve piu' esce con lei. */
    fun withoutEntry(album: Album, key: String): AlbumContent {
        val ids = album.cardIds.filter { it != key }
        return AlbumContent(ids, prune(ids, album.catalogCards))
    }

    /** La voce [key] rimessa alla posizione [index]: e' l'annulla della rimozione. */
    fun withEntryAt(album: Album, key: String, index: Int, catalog: AlbumCatalogCard?): AlbumContent {
        if (key in album.cardIds) return AlbumContent(album.cardIds, album.catalogCards)
        val ids = album.cardIds.toMutableList().apply { add(index.coerceIn(0, size), key) }
        val cards = if (catalog != null && album.catalogCards.none { it.apiId == catalog.apiId }) {
            album.catalogCards + catalog
        } else {
            album.catalogCards
        }
        return AlbumContent(ids, prune(ids, cards))
    }

    /** Solo le carte del catalogo che qualche tasca usa ancora, una per carta. */
    fun prune(cardIds: List<String>, catalogCards: List<AlbumCatalogCard>): List<AlbumCatalogCard> {
        val used = cardIds.mapNotNull(::apiIdOf).toSet()
        return catalogCards.filter { it.apiId.trim() in used }.distinctBy { it.apiId.trim() }
    }

    /** Gli apiCardId gia' nell'album come "da trovare": non vanno riproposti dalla collezione. */
    fun catalogApiIds(album: Album): Set<String> = album.cardIds.mapNotNull(::apiIdOf).toSet()

    // ── Dal catalogo ──────────────────────────────────────────────────────

    /** La fotografia della carta da salvare nell'album. Il prezzo e' il minimo, come nel resto dell'app. */
    fun fromTcgCard(card: TcgCard): AlbumCatalogCard = AlbumCatalogCard(
        apiId = card.id.trim(),
        name = card.name,
        imageUrl = card.images.small.ifBlank { card.images.large },
        set = card.set?.name.orEmpty(),
        setId = card.set?.id.orEmpty(),
        number = card.number,
        rarity = card.rarity.orEmpty(),
        type = card.types?.firstOrNull().orEmpty(),
        supertype = card.supertype,
        hp = card.hp?.toIntOrNull() ?: 0,
        price = card.cardmarket?.prices.minimumEurPriceOrZero()
    )

    /**
     * Le regole facoltative dell'album, sulla carta del catalogo: le stesse
     * che valgono per la collezione, cosi' le due schede propongono le stesse
     * cose. Le regole vuote non escludono niente.
     */
    fun matchesRules(album: Album, card: AlbumCatalogCard): Boolean {
        val matchesType = album.pokemonType.isBlank() ||
            AppLocale.typeToEnglish(card.type).equals(AppLocale.typeToEnglish(album.pokemonType), ignoreCase = true)
        val matchesExpansion = album.expansion.isBlank() || card.set.equals(album.expansion, ignoreCase = true)
        val matchesSupertype = album.supertype.isBlank() ||
            CardClassifier.classify(card.supertype, card.type, card.name, emptyList(), card.hp)
                .equals(album.supertype, ignoreCase = true)
        return matchesType && matchesExpansion && matchesSupertype
    }

    /** Ha delle regole? Serve a dirlo nel foglio di inserimento. */
    fun hasRules(album: Album): Boolean =
        album.pokemonType.isNotBlank() || album.expansion.isNotBlank() || album.supertype.isNotBlank()

    // ── Pagine ────────────────────────────────────────────────────────────

    /** Le pagine che servono per [size] tasche, almeno una. */
    fun pagesFor(size: Int, perPage: Int): Int =
        if (perPage <= 0) 1 else ((size.coerceAtLeast(1) + perPage - 1) / perPage)

    /**
     * Le tasche per [pages] pagine, senza scendere sotto le carte gia'
     * nell'album: rimpicciolire un album pieno non deve buttare fuori carte.
     */
    fun sizeFor(pages: Int, perPage: Int, cardsInside: Int, max: Int = MAX_SIZE): Int =
        (pages.coerceAtLeast(1) * perPage).coerceAtLeast(cardsInside).coerceAtMost(max.coerceAtLeast(cardsInside))

    const val MAX_SIZE = 999
}

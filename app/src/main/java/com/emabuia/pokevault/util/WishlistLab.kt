package com.emabuia.pokevault.util

import com.emabuia.pokevault.data.model.PokemonCard
import com.emabuia.pokevault.data.model.Wishlist
import com.emabuia.pokevault.data.remote.TcgCard

/**
 * Conti, ordinamenti e filtri della Wishlist.
 *
 * Stessa impostazione di [CollectorLab], e per la stessa ragione: prima la
 * wishlist non calcolava niente — mostrava "12 carte" e basta — e le somme che
 * servivano sarebbero finite dentro i Composable, cioe' rifatte a ogni frame e
 * non verificabili. Qui sono funzioni pure, testate.
 *
 * Dove un conto esiste gia' nel Collector Lab (il minimo di una carta, il costo
 * di un gruppo, l'ordine da raccoglitore) questo file lo *chiama*: due schermi
 * della stessa app non possono dare due totali diversi sugli stessi prezzi.
 */

/** Una riga della lista wishlist, con i conti gia' fatti. */
data class WishlistRow(
    val id: String,
    val name: String,
    val iconKey: String,
    val accentKey: String,
    val budgetEur: Double,
    val total: Int,
    val owned: Int,
    val cost: Double,
    val pricedMissing: Int,
    val previewUrls: List<String>,
    val createdAtSeconds: Long,
    /** false finche' le carte della lista non sono tutte arrivate. */
    val isResolved: Boolean
) {
    val missing: Int get() = (total - owned).coerceAtLeast(0)
    val ownedPercent: Float get() = CollectorLab.fillPercent(owned, total)
    val isComplete: Boolean get() = total > 0 && owned >= total
    val hasBudget: Boolean get() = budgetEur > 0.0
    val budgetPercent: Float get() = WishlistLab.budgetPercent(cost, budgetEur)
    val isOverBudget: Boolean get() = hasBudget && cost > budgetEur

    /** Quanto resta del tetto, zero se lo si e' gia' superato. */
    val budgetLeft: Double get() = (budgetEur - cost).coerceAtLeast(0.0)

    /** Mancanti senza un prezzo noto: dice quanto fidarsi di [cost]. */
    val unpricedMissing: Int get() = (missing - pricedMissing).coerceAtLeast(0)
}

/** Il riassunto in cima alla lista delle wishlist. */
data class WishlistSummary(
    val lists: Int,
    val cards: Int,
    val owned: Int,
    val cost: Double,
    val pricedMissing: Int,
    val unpricedMissing: Int,
    val listsOverBudget: Int
) {
    val missing: Int get() = (cards - owned).coerceAtLeast(0)
    val ownedPercent: Float get() = CollectorLab.fillPercent(owned, cards)
}

enum class WishlistSort { CLOSEST, RECENT, NAME, COST, CARDS }

enum class WishlistCardSort { NUMBER, NAME, PRICE_DESC, PRICE_ASC, SET }

/** Cosa mostrare di una lista: tutto, solo quello che manca, solo quello preso. */
enum class WishlistCardFilter { ALL, MISSING, OWNED }

object WishlistLab {

    // ── Collezione ────────────────────────────────────────────────────────

    /**
     * Gli id TCG delle carte che l'utente possiede gia'.
     *
     * E' il collegamento che alla wishlist mancava del tutto: una carta comprata
     * restava in lista identica alle altre, e l'unico modo di accorgersene era
     * ricordarselo. Il confronto e' sull'`apiCardId` perche' e' esattamente
     * l'identificatore che la wishlist salva.
     */
    fun ownedCardIds(owned: List<PokemonCard>): Set<String> =
        owned.asSequence()
            .map { it.apiCardId.trim() }
            .filter { it.isNotEmpty() }
            .toSet()

    // ── Righe ─────────────────────────────────────────────────────────────

    /**
     * La riga di [wishlist], dati i prezzi gia' noti delle sue carte.
     *
     * [cardsById] puo' essere parziale: finche' le carte stanno arrivando la
     * riga si dichiara non risolta ([WishlistRow.isResolved]) e chi la disegna
     * sa di non poter mostrare il totale come se fosse definitivo.
     */
    fun row(
        wishlist: Wishlist,
        cardsById: Map<String, TcgCard>,
        ownedIds: Set<String>
    ): WishlistRow {
        val ids = wishlist.cardIds
        val resolved = ids.mapNotNull { cardsById[it] }
        val missingCards = resolved.filter { it.id !in ownedIds }

        return WishlistRow(
            id = wishlist.id,
            name = wishlist.name,
            iconKey = wishlist.iconKey,
            accentKey = wishlist.resolvedAccentKey,
            budgetEur = wishlist.budgetEur,
            total = ids.size,
            owned = ids.count { it in ownedIds },
            cost = CollectorLab.completionCost(missingCards),
            pricedMissing = CollectorLab.pricedCount(missingCards),
            // Le prime tre bastano: servono a dire "dentro c'e' roba", non a
            // elencare la lista in miniatura.
            previewUrls = resolved.asSequence()
                .map { it.images.small }
                .filter { it.isNotBlank() }
                .take(3)
                .toList(),
            createdAtSeconds = wishlist.createdAt?.seconds ?: 0L,
            isResolved = resolved.size == ids.size
        )
    }

    fun rows(
        wishlists: List<Wishlist>,
        cardsById: Map<String, TcgCard>,
        ownedIds: Set<String>
    ): List<WishlistRow> = wishlists.map { row(it, cardsById, ownedIds) }

    /**
     * Il riassunto di tutte le liste.
     *
     * Conta le carte *distinte*: la stessa carta messa in due liste e' una carta
     * da comprare, non due, e sommare le righe la conterebbe due volte gonfiando
     * proprio la cifra che si guarda per prima.
     */
    fun summary(
        wishlists: List<Wishlist>,
        cardsById: Map<String, TcgCard>,
        ownedIds: Set<String>
    ): WishlistSummary {
        val distinctIds = wishlists.flatMapTo(LinkedHashSet()) { it.cardIds }
        val missingIds = distinctIds.filter { it !in ownedIds }
        val missingCards = missingIds.mapNotNull { cardsById[it] }
        val priced = CollectorLab.pricedCount(missingCards)

        return WishlistSummary(
            lists = wishlists.size,
            cards = distinctIds.size,
            owned = distinctIds.size - missingIds.size,
            cost = CollectorLab.completionCost(missingCards),
            pricedMissing = priced,
            unpricedMissing = (missingIds.size - priced).coerceAtLeast(0),
            listsOverBudget = rows(wishlists, cardsById, ownedIds).count { it.isOverBudget }
        )
    }

    // ── Liste ─────────────────────────────────────────────────────────────

    fun sortWishlists(rows: List<WishlistRow>, sort: WishlistSort): List<WishlistRow> = when (sort) {
        // Le liste gia' prese per intero scendono in fondo, come i chase chiusi:
        // una lista tutta comprata non e' piu' una cosa da fare.
        WishlistSort.CLOSEST -> rows.sortedWith(
            compareBy<WishlistRow> { it.isComplete || it.total == 0 }
                .thenBy { it.missing }
                .thenByDescending { it.ownedPercent }
                .thenBy { it.name.lowercase() }
        )
        WishlistSort.RECENT -> rows.sortedWith(
            compareByDescending<WishlistRow> { it.createdAtSeconds }
                .thenBy { it.name.lowercase() }
        )
        WishlistSort.NAME -> rows.sortedBy { it.name.lowercase() }
        WishlistSort.COST -> rows.sortedWith(
            compareByDescending<WishlistRow> { it.cost }
                .thenBy { it.name.lowercase() }
        )
        WishlistSort.CARDS -> rows.sortedWith(
            compareByDescending<WishlistRow> { it.total }
                .thenBy { it.name.lowercase() }
        )
    }

    fun filterWishlists(rows: List<WishlistRow>, query: String): List<WishlistRow> {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return rows
        return rows.filter { it.name.lowercase().contains(q) }
    }

    // ── Carte di una lista ────────────────────────────────────────────────

    fun sortCards(cards: List<TcgCard>, sort: WishlistCardSort): List<TcgCard> = when (sort) {
        WishlistCardSort.NUMBER -> CollectorLab.sortChaseCards(cards, ChaseCardSort.NUMBER)
        WishlistCardSort.NAME -> CollectorLab.sortChaseCards(cards, ChaseCardSort.NAME)
        WishlistCardSort.PRICE_DESC -> CollectorLab.sortChaseCards(cards, ChaseCardSort.PRICE_DESC)
        WishlistCardSort.PRICE_ASC -> CollectorLab.sortChaseCards(cards, ChaseCardSort.PRICE_ASC)
        // Per set: una wishlist pesca da espansioni diverse, e chi compra ragiona
        // per espansione — dentro un negozio o dentro un'inserzione le carte
        // stanno insieme per set, non per numero.
        WishlistCardSort.SET -> cards.sortedWith(
            compareBy<TcgCard> { (it.set?.name ?: "").lowercase() }
                .then(CollectorLab.cardNumberComparator)
        )
    }

    fun filterCards(
        cards: List<TcgCard>,
        query: String,
        filter: WishlistCardFilter,
        ownedIds: Set<String>
    ): List<TcgCard> {
        val byState = when (filter) {
            WishlistCardFilter.ALL -> cards
            WishlistCardFilter.MISSING -> cards.filter { it.id !in ownedIds }
            WishlistCardFilter.OWNED -> cards.filter { it.id in ownedIds }
        }
        val q = query.trim().lowercase()
        if (q.isEmpty()) return byState
        return byState.filter { card ->
            card.name.lowercase().contains(q) ||
                card.number.lowercase().contains(q) ||
                (card.set?.name ?: "").lowercase().contains(q) ||
                (card.rarity ?: "").lowercase().contains(q)
        }
    }

    // ── Budget ────────────────────────────────────────────────────────────

    /**
     * Quanto del tetto e' gia' impegnato, oltre il 100% compreso.
     *
     * Non viene limitato a 100: la barra si ferma da sola, ma il numero deve
     * poter dire "sei al 140%", che e' esattamente l'informazione per cui il
     * tetto esiste.
     */
    fun budgetPercent(cost: Double, budget: Double): Float =
        if (budget <= 0.0) 0f else ((cost / budget) * 100.0).toFloat().coerceAtLeast(0f)

    /**
     * Le carte che stanno dentro [budget], prese dalla piu' economica.
     *
     * E' la risposta alla domanda vera di chi apre una wishlist con trenta euro
     * in tasca: non "quanto costa tutto", ma "quante ne porto a casa oggi".
     * Le carte senza prezzo non entrano: non si possono mettere nel conto.
     */
    fun affordableWithin(missing: List<TcgCard>, budget: Double): List<TcgCard> {
        if (budget <= 0.0) return emptyList()
        var left = budget
        val taken = mutableListOf<TcgCard>()
        for (card in CollectorLab.sortChaseCards(missing, ChaseCardSort.PRICE_ASC)) {
            val price = card.cardmarket?.prices.minimumEurPriceOrZero()
            if (price <= 0.0) continue
            if (price > left) break
            left -= price
            taken += card
        }
        return taken
    }
}

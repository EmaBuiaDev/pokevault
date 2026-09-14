package com.emabuia.pokevault.util

import com.emabuia.pokevault.data.model.Wishlist
import com.emabuia.pokevault.data.model.WishlistItem
import com.emabuia.pokevault.data.model.WishlistPriority
import com.emabuia.pokevault.data.remote.TcgCard
import java.util.Locale

/**
 * Conti, ordinamenti e filtri della Wishlist.
 *
 * Stessa impostazione del [CollectorLab]: funzioni pure fuori dalla
 * composizione, cosi' i totali si calcolano quando cambiano i dati e non a ogni
 * frame, e soprattutto si possono verificare con un test invece che a occhio
 * sull'emulatore.
 */

// ── Righe precalcolate ────────────────────────────────────────────────────────

/**
 * Una riga dell'elenco delle liste.
 *
 * [owned] si ricava incrociando gli id con la collezione: non costa una sola
 * chiamata di rete, quindi l'elenco puo' dire "3 di 12 gia' prese" senza
 * scaricare le carte di ogni lista.
 */
data class WishlistRow(
    val id: String,
    val name: String,
    val iconKey: String,
    val total: Int,
    val owned: Int,
    val highPriority: Int,
    val budget: Double,
    val createdAtSeconds: Long
) {
    val missing: Int get() = (total - owned).coerceAtLeast(0)
    val percent: Float get() = CollectorLab.fillPercent(owned, total)
    val isComplete: Boolean get() = total > 0 && owned >= total
    val hasBudget: Boolean get() = budget > 0.0
}

/**
 * Una carta dentro una lista, con metadati, prezzo e possesso gia' risolti.
 *
 * [position] e' l'indice dentro `cardIds`, cioe' l'ordine di inserimento:
 * `arrayUnion` accoda, quindi e' la sola informazione di recenza disponibile
 * anche per le carte aggiunte in blocco dal Chase, che non hanno `addedAt`.
 */
data class WishlistCardRow(
    val card: TcgCard,
    val item: WishlistItem,
    val isOwned: Boolean,
    val price: Double,
    val position: Int
) {
    val cardId: String get() = card.id
    val priority: WishlistPriority get() = item.priorityValue
    val hasPrice: Boolean get() = price > 0.0

    /**
     * Il prezzo e' sceso sotto il tetto che avevi messo.
     *
     * E' il solo momento in cui la wishlist ha qualcosa da dire senza che tu
     * chieda niente: senza tetto la riga resta un prezzo da interpretare.
     */
    val isDeal: Boolean get() = item.targetPrice > 0.0 && price > 0.0 && price <= item.targetPrice

    /** Quanto dovrebbe ancora scendere per rientrare nel tetto. 0 se ci rientra gia'. */
    val overTargetBy: Double
        get() = if (item.targetPrice > 0.0 && price > item.targetPrice) price - item.targetPrice else 0.0
}

/** Il riassunto in cima al dettaglio di una lista. */
data class WishlistStats(
    val cards: Int,
    val owned: Int,
    val cost: Double,
    val pricedMissing: Int,
    val value: Double,
    val deals: Int,
    val highPriority: Int,
    val budget: Double
) {
    val missing: Int get() = (cards - owned).coerceAtLeast(0)
    val percent: Float get() = CollectorLab.fillPercent(owned, cards)
    val hasBudget: Boolean get() = budget > 0.0
    val budgetLeft: Double get() = if (hasBudget) budget - cost else 0.0
    val isOverBudget: Boolean get() = hasBudget && cost > budget
    /** Puo' superare il 100%: sforare il budget deve vedersi, non saturare la barra. */
    val budgetPercent: Float
        get() = if (!hasBudget) 0f else (cost / budget * 100.0).toFloat().coerceAtLeast(0f)
}

/** Il riassunto in cima all'elenco delle liste. */
data class WishlistOverview(
    val lists: Int,
    val cards: Int,
    val owned: Int,
    val highPriority: Int
) {
    val missing: Int get() = (cards - owned).coerceAtLeast(0)
}

// ── Ordinamenti e filtri ──────────────────────────────────────────────────────

enum class WishlistListSort { RECENT, NAME, SIZE, PROGRESS }

enum class WishlistCardSort { PRIORITY, PRICE_DESC, PRICE_ASC, NAME, NUMBER, RECENT }

enum class WishlistCardFilter { ALL, HIGH, DEALS, MISSING, OWNED }

object WishlistLab {

    /** La nota per carta: una riga, non un diario. */
    const val MAX_NOTE_LENGTH = 120

    /** Tetto di prezzo e budget: oltre questo sono errori di battitura. */
    const val MAX_PRICE = 99_999.0

    // ── Elenco delle liste ────────────────────────────────────────────────

    fun rows(wishlists: List<Wishlist>, ownedCardIds: Set<String>): List<WishlistRow> =
        wishlists.map { wishlist ->
            WishlistRow(
                id = wishlist.id,
                name = wishlist.name,
                iconKey = wishlist.iconKey,
                total = wishlist.cardIds.size,
                owned = wishlist.cardIds.count { it in ownedCardIds },
                highPriority = wishlist.cardIds.count { id ->
                    wishlist.priorityOf(id) == WishlistPriority.HIGH && id !in ownedCardIds
                },
                budget = wishlist.budget,
                createdAtSeconds = wishlist.createdAt?.seconds ?: 0L
            )
        }

    fun sortRows(rows: List<WishlistRow>, sort: WishlistListSort): List<WishlistRow> = when (sort) {
        WishlistListSort.RECENT -> rows.sortedWith(
            compareByDescending<WishlistRow> { it.createdAtSeconds }
                .thenBy { it.name.lowercase() }
        )
        WishlistListSort.NAME -> rows.sortedBy { it.name.lowercase() }
        WishlistListSort.SIZE -> rows.sortedWith(
            compareByDescending<WishlistRow> { it.total }
                .thenBy { it.name.lowercase() }
        )
        // Come i chase: le liste finite scendono in fondo, non sono piu' un
        // obiettivo e in cima seppellirebbero quelle ancora aperte.
        WishlistListSort.PROGRESS -> rows.sortedWith(
            compareBy<WishlistRow> { it.isComplete }
                .thenByDescending { it.percent }
                .thenBy { it.missing }
                .thenBy { it.name.lowercase() }
        )
    }

    fun filterRows(rows: List<WishlistRow>, query: String): List<WishlistRow> {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return rows
        return rows.filter { it.name.lowercase().contains(q) }
    }

    fun overview(rows: List<WishlistRow>): WishlistOverview = WishlistOverview(
        lists = rows.size,
        cards = rows.sumOf { it.total },
        owned = rows.sumOf { it.owned },
        highPriority = rows.sumOf { it.highPriority }
    )

    // ── Carte di una lista ────────────────────────────────────────────────

    fun cardRows(
        wishlist: Wishlist,
        cards: List<TcgCard>,
        ownedCardIds: Set<String>
    ): List<WishlistCardRow> {
        val positions = wishlist.cardIds.withIndex().associate { (index, id) -> id to index }
        return cards.map { card ->
            WishlistCardRow(
                card = card,
                item = wishlist.itemFor(card.id),
                isOwned = card.id in ownedCardIds,
                price = card.cardmarket?.prices.minimumEurPriceOrZero(),
                position = positions[card.id] ?: Int.MAX_VALUE
            )
        }
    }

    fun sortCards(rows: List<WishlistCardRow>, sort: WishlistCardSort): List<WishlistCardRow> =
        when (sort) {
            // L'ordine di default: prima cosa vuoi davvero, e dentro la stessa
            // priorita' prima cio' che e' gia' sceso sotto il tetto.
            WishlistCardSort.PRIORITY -> rows.sortedWith(
                compareBy<WishlistCardRow> { it.isOwned }
                    .thenBy { it.priority.rank }
                    .thenByDescending { it.isDeal }
                    .thenByDescending { it.price }
                    .then(byCardNumber)
            )
            WishlistCardSort.PRICE_DESC -> rows.sortedWith(
                compareByDescending<WishlistCardRow> { it.price }.then(byCardNumber)
            )
            // Le carte senza prezzo in fondo: in cima coprirebbero le occasioni
            // vere con un elenco di "N/D".
            WishlistCardSort.PRICE_ASC -> rows.sortedWith(
                compareBy<WishlistCardRow> { !it.hasPrice }
                    .thenBy { it.price }
                    .then(byCardNumber)
            )
            WishlistCardSort.NAME -> rows.sortedWith(
                compareBy<WishlistCardRow> { it.card.name.lowercase() }.then(byCardNumber)
            )
            WishlistCardSort.NUMBER -> rows.sortedWith(byCardNumber)
            WishlistCardSort.RECENT -> rows.sortedWith(
                compareByDescending<WishlistCardRow> { it.position }.then(byCardNumber)
            )
        }

    fun filterCards(
        rows: List<WishlistCardRow>,
        query: String,
        filter: WishlistCardFilter
    ): List<WishlistCardRow> {
        val byFilter = when (filter) {
            WishlistCardFilter.ALL -> rows
            WishlistCardFilter.HIGH -> rows.filter { it.priority == WishlistPriority.HIGH }
            WishlistCardFilter.DEALS -> rows.filter { it.isDeal }
            WishlistCardFilter.MISSING -> rows.filter { !it.isOwned }
            WishlistCardFilter.OWNED -> rows.filter { it.isOwned }
        }
        val q = query.trim().lowercase()
        if (q.isEmpty()) return byFilter
        return byFilter.filter { row ->
            row.card.name.lowercase().contains(q) ||
                row.card.number.lowercase().contains(q) ||
                (row.card.set?.name ?: "").lowercase().contains(q) ||
                (row.card.rarity ?: "").lowercase().contains(q) ||
                row.item.note.lowercase().contains(q)
        }
    }

    /**
     * I conti della lista.
     *
     * [WishlistStats.cost] somma solo le carte che ti mancano davvero: quelle
     * gia' entrate in collezione non sono piu' una spesa. Le carte senza prezzo
     * contano zero, quindi il totale e' una stima al ribasso ed e' accompagnato
     * da [WishlistStats.pricedMissing].
     */
    fun stats(rows: List<WishlistCardRow>, budget: Double): WishlistStats {
        val missing = rows.filter { !it.isOwned }
        return WishlistStats(
            cards = rows.size,
            owned = rows.count { it.isOwned },
            cost = missing.sumOf { it.price },
            pricedMissing = missing.count { it.hasPrice },
            value = rows.sumOf { it.price },
            deals = missing.count { it.isDeal },
            highPriority = missing.count { it.priority == WishlistPriority.HIGH },
            budget = budget
        )
    }

    /** La prossima da prendere: alta priorita', gia' sotto il tetto, la piu' economica. */
    fun nextPick(rows: List<WishlistCardRow>): WishlistCardRow? {
        val missing = rows.filter { !it.isOwned }
        val deals = missing.filter { it.isDeal }
        val pool = deals.ifEmpty { missing.filter { it.hasPrice } }
        if (pool.isEmpty()) return null
        return pool.minWithOrNull(
            compareBy<WishlistCardRow> { it.priority.rank }.thenBy { it.price }
        )
    }

    // ── Esportazione ──────────────────────────────────────────────────────

    /**
     * La lista in testo, per mandarla a chi te la cerca allo stand.
     *
     * Le etichette arrivano da fuori: questo file non deve dipendere da
     * [AppLocale], che vive di stato Compose e non si potrebbe testare qui.
     */
    fun shareText(
        listName: String,
        rows: List<WishlistCardRow>,
        totalLabel: String,
        ownedLabel: String,
        priorityLabel: (WishlistPriority) -> String
    ): String {
        val builder = StringBuilder()
        builder.append(listName.trim().ifEmpty { "Wishlist" }).append('\n')
        rows.forEach { row ->
            builder.append("• ")
                .append(row.card.name)
                .append(" #").append(row.card.number)
            row.card.set?.name?.takeIf { it.isNotBlank() }?.let { builder.append(" (").append(it).append(')') }
            builder.append(" — ").append(priorityLabel(row.priority))
            if (row.hasPrice) builder.append(" · ").append(formatPrice(row.price))
            if (row.isOwned) builder.append(" · ").append(ownedLabel)
            builder.append('\n')
        }
        val cost = rows.filter { !it.isOwned }.sumOf { it.price }
        if (cost > 0.0) {
            builder.append(totalLabel).append(": ").append(formatPrice(cost))
        }
        return builder.toString().trimEnd()
    }

    /**
     * Il prezzo com'e' scritto ovunque nell'app.
     *
     * Stessa forma di `formatEur` del kit delle liste, che non si puo'
     * richiamare da qui: quello sta in `ui/`, questo file no. La virgola e'
     * fissata a Locale.ITALY e non lasciata al locale di sistema, altrimenti
     * lo stesso prezzo cambierebbe forma fra la riga e il riquadro in cima.
     */
    fun formatPrice(value: Double): String = "€ " + String.format(Locale.ITALY, "%.2f", value)

    // ── Normalizzazione input ─────────────────────────────────────────────

    fun normalizeNote(raw: String): String = raw.trim().take(MAX_NOTE_LENGTH)

    /**
     * Il prezzo digitato a mano: virgola o punto, negativi e assurdi fuori.
     *
     * Torna 0.0 per "nessun tetto", che e' anche quello che si ottiene
     * svuotando il campo.
     */
    fun normalizePrice(raw: String): Double {
        val cleaned = raw.trim().replace(',', '.').replace("€", "").trim()
        if (cleaned.isEmpty()) return 0.0
        val parsed = cleaned.toDoubleOrNull() ?: return 0.0
        if (parsed.isNaN() || parsed <= 0.0) return 0.0
        return minOf(parsed, MAX_PRICE)
    }

    private val byCardNumber: Comparator<WishlistCardRow> =
        Comparator { a, b -> CollectorLab.cardNumberComparator.compare(a.card, b.card) }
}

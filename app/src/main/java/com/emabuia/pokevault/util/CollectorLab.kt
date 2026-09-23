package com.emabuia.pokevault.util

import com.emabuia.pokevault.data.remote.TcgCard

/**
 * Conti, ordinamenti e filtri del Collector Lab.
 *
 * Sta fuori dalla composizione di proposito: prima ogni schermata calcolava i
 * propri totali dentro `items { }` o nel corpo del Composable, quindi a ogni
 * frame e senza modo di verificarli. Qui sono funzioni pure, testate.
 */

// ── Righe precalcolate ────────────────────────────────────────────────────────

/**
 * Una riga della lista album, con i conti gia' fatti.
 *
 * Il costo (copertina, valore, riempimento) si paga una volta quando cambiano
 * album o collezione, non a ogni ricomposizione della lista.
 */
data class AlbumRow(
    val id: String,
    val name: String,
    val description: String,
    val theme: String,
    val pokemonType: String,
    val coverUrl: String,
    val previewUrls: List<String>,
    val used: Int,
    val size: Int,
    val value: Double,
    val createdAtSeconds: Long
) {
    val fillPercent: Float get() = CollectorLab.fillPercent(used, size)
    val isFull: Boolean get() = size > 0 && used >= size
}

/** Una riga della lista chase, con l'avanzamento gia' calcolato. */
data class ChaseRow(
    val id: String,
    val name: String,
    val criteriaLabel: String,
    val owned: Int,
    val total: Int,
    val createdAtSeconds: Long
) {
    val percent: Float get() = CollectorLab.fillPercent(owned, total)
    val missing: Int get() = (total - owned).coerceAtLeast(0)
    val isComplete: Boolean get() = total > 0 && owned >= total
}

/** Il riassunto in cima al Collector Lab. */
data class CollectorSummary(
    val albums: Int,
    val cardsInAlbums: Int,
    val albumValue: Double,
    val chases: Int,
    val chasesCompleted: Int,
    val missingCards: Int,
    val averageChasePercent: Float
)

// ── Ordinamenti ───────────────────────────────────────────────────────────────

enum class AlbumSort { RECENT, NAME, FILL, VALUE }

/** CLOSEST = "quasi fatti": i chase piu' avanti, con i completati in fondo. */
enum class ChaseSort { CLOSEST, NAME, RECENT }

enum class ChaseCardSort { NUMBER, NAME, PRICE_DESC, PRICE_ASC }

object CollectorLab {

    /** Slot di una pagina del raccoglitore: tre per tre, come quelle vere. */
    const val BINDER_SLOTS_PER_PAGE = 9

    fun fillPercent(used: Int, total: Int): Float =
        if (total <= 0) 0f else (used.toFloat() / total.toFloat() * 100f).coerceIn(0f, 100f)

    // ── Album ─────────────────────────────────────────────────────────────

    fun sortAlbums(rows: List<AlbumRow>, sort: AlbumSort): List<AlbumRow> = when (sort) {
        AlbumSort.RECENT -> rows.sortedWith(
            compareByDescending<AlbumRow> { it.createdAtSeconds }
                .thenBy { it.name.lowercase() }
        )
        AlbumSort.NAME -> rows.sortedBy { it.name.lowercase() }
        AlbumSort.FILL -> rows.sortedWith(
            compareByDescending<AlbumRow> { it.fillPercent }
                .thenByDescending { it.used }
                .thenBy { it.name.lowercase() }
        )
        AlbumSort.VALUE -> rows.sortedWith(
            compareByDescending<AlbumRow> { it.value }
                .thenBy { it.name.lowercase() }
        )
    }

    fun filterAlbums(rows: List<AlbumRow>, query: String): List<AlbumRow> {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return rows
        return rows.filter { row ->
            row.name.lowercase().contains(q) ||
                row.description.lowercase().contains(q) ||
                row.pokemonType.lowercase().contains(q)
        }
    }

    // ── Chase ─────────────────────────────────────────────────────────────

    fun sortChases(rows: List<ChaseRow>, sort: ChaseSort): List<ChaseRow> = when (sort) {
        // I completati scendono in fondo: un chase finito non e' piu' un
        // obiettivo, e lasciarlo in cima seppellisce quelli ancora aperti.
        ChaseSort.CLOSEST -> rows.sortedWith(
            compareBy<ChaseRow> { it.isComplete }
                .thenByDescending { it.percent }
                .thenBy { it.missing }
                .thenBy { it.name.lowercase() }
        )
        ChaseSort.NAME -> rows.sortedBy { it.name.lowercase() }
        ChaseSort.RECENT -> rows.sortedWith(
            compareByDescending<ChaseRow> { it.createdAtSeconds }
                .thenBy { it.name.lowercase() }
        )
    }

    fun filterChases(rows: List<ChaseRow>, query: String): List<ChaseRow> {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return rows
        return rows.filter { row ->
            row.name.lowercase().contains(q) || row.criteriaLabel.lowercase().contains(q)
        }
    }

    /** Il chase da mettere in vetrina: il piu' avanti fra quelli non finiti. */
    fun spotlightChase(rows: List<ChaseRow>): ChaseRow? =
        sortChases(rows.filter { !it.isComplete && it.total > 0 }, ChaseSort.CLOSEST).firstOrNull()

    fun summary(albums: List<AlbumRow>, chases: List<ChaseRow>): CollectorSummary {
        val tracked = chases.filter { it.total > 0 }
        return CollectorSummary(
            albums = albums.size,
            cardsInAlbums = albums.sumOf { it.used },
            albumValue = albums.sumOf { it.value },
            chases = chases.size,
            chasesCompleted = chases.count { it.isComplete },
            missingCards = chases.sumOf { it.missing },
            averageChasePercent = if (tracked.isEmpty()) 0f
            else tracked.sumOf { it.percent.toDouble() }.toFloat() / tracked.size
        )
    }

    // ── Raccoglitore ──────────────────────────────────────────────────────

    /** Quante pagine serve sfogliare per un album di [albumSize] slot. */
    fun binderPageCount(albumSize: Int, perPage: Int = BINDER_SLOTS_PER_PAGE): Int {
        if (perPage <= 0 || albumSize <= 0) return 0
        return (albumSize + perPage - 1) / perPage
    }

    /**
     * Gli slot della pagina [page] (0-based): la carta, oppure null per la
     * bustina vuota. La lista ha sempre [perPage] elementi, cosi' la griglia
     * non cambia forma sull'ultima pagina.
     */
    fun <T> binderPage(page: Int, items: List<T>, perPage: Int = BINDER_SLOTS_PER_PAGE): List<T?> {
        if (perPage <= 0) return emptyList()
        val start = page * perPage
        return (0 until perPage).map { offset -> items.getOrNull(start + offset) }
    }

    // ── Carte di un chase ─────────────────────────────────────────────────

    /**
     * Quanto costa chiudere il chase: la somma dei minimi delle carte mancanti.
     *
     * Usa i prezzi gia' arrivati con le carte del set (vedi
     * [minimumEurPriceOrZero]); non fa una sola chiamata in piu'. Le carte
     * senza prezzo contano zero, quindi il totale e' una stima al ribasso ed e'
     * accompagnato da [pricedCount].
     */
    fun completionCost(missing: List<TcgCard>): Double =
        missing.sumOf { it.cardmarket?.prices.minimumEurPriceOrZero() }

    /** Quante delle mancanti hanno un prezzo noto: dice quanto fidarsi del totale. */
    fun pricedCount(missing: List<TcgCard>): Int =
        missing.count { it.cardmarket?.prices.minimumEurPriceOrZero() > 0.0 }

    /** La mancante piu' economica: il prossimo passo piu' facile. */
    fun cheapestMissing(missing: List<TcgCard>): TcgCard? =
        missing.filter { it.cardmarket?.prices.minimumEurPriceOrZero() > 0.0 }
            .minByOrNull { it.cardmarket?.prices.minimumEurPriceOrZero() }

    /** La mancante piu' cara: la carta che tiene fermo il completamento. */
    fun mostExpensiveMissing(missing: List<TcgCard>): TcgCard? =
        missing.filter { it.cardmarket?.prices.minimumEurPriceOrZero() > 0.0 }
            .maxByOrNull { it.cardmarket?.prices.minimumEurPriceOrZero() }

    fun sortChaseCards(cards: List<TcgCard>, sort: ChaseCardSort): List<TcgCard> = when (sort) {
        ChaseCardSort.NUMBER -> cards.sortedWith(cardNumberComparator)
        ChaseCardSort.NAME -> cards.sortedWith(
            compareBy<TcgCard> { it.name.lowercase() }.then(cardNumberComparator)
        )
        ChaseCardSort.PRICE_DESC -> cards.sortedWith(
            compareByDescending<TcgCard> { it.cardmarket?.prices.minimumEurPriceOrZero() }
                .then(cardNumberComparator)
        )
        // Nel crescente le carte senza prezzo vanno in fondo: in cima
        // coprirebbero le vere occasioni con un elenco di "N/D".
        ChaseCardSort.PRICE_ASC -> cards.sortedWith(
            compareBy<TcgCard> { it.cardmarket?.prices.minimumEurPriceOrZero() <= 0.0 }
                .thenBy { it.cardmarket?.prices.minimumEurPriceOrZero() }
                .then(cardNumberComparator)
        )
    }

    fun filterChaseCards(cards: List<TcgCard>, query: String): List<TcgCard> {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return cards
        return cards.filter { card ->
            card.name.lowercase().contains(q) ||
                card.number.lowercase().contains(q) ||
                (card.rarity ?: "").lowercase().contains(q)
        }
    }

    /**
     * Ordine "da raccoglitore" sui numeri di carta.
     *
     * Il numero stampato non e' un intero: "9" viene prima di "10" ma dopo
     * "SV001", e ordinandolo come stringa si ottiene 1, 10, 100, 2. Qui il
     * prefisso alfabetico e la parte numerica si confrontano separatamente.
     */
    val cardNumberComparator: Comparator<TcgCard> = Comparator { a, b ->
        val (prefixA, numberA) = cardNumberKey(a.number)
        val (prefixB, numberB) = cardNumberKey(b.number)
        when {
            prefixA != prefixB -> prefixA.compareTo(prefixB)
            numberA != numberB -> numberA.compareTo(numberB)
            else -> a.number.compareTo(b.number)
        }
    }

    /** (prefisso, numero): "TG12" -> ("TG", 12), "007" -> ("", 7). */
    internal fun cardNumberKey(raw: String): Pair<String, Int> {
        val token = raw.trim().substringBefore('/').uppercase()
        val match = Regex("^([A-Z]*)0*(\\d+)").find(token)
            ?: return Pair(token, Int.MAX_VALUE)
        return Pair(match.groupValues[1], match.groupValues[2].toIntOrNull() ?: Int.MAX_VALUE)
    }
}

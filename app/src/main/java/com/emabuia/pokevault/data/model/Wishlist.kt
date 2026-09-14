package com.emabuia.pokevault.data.model

import com.google.firebase.Timestamp

/**
 * Quanto vuoi una carta.
 *
 * Una wishlist lunga senza priorita' e' solo un elenco: con venti carte dentro
 * non dice piu' quale prendere per prima. Il valore viaggia come stringa perche'
 * Firestore, davanti a un enum che non conosce, fa fallire la deserializzazione
 * dell'intero documento: [fromKey] degrada a [MEDIUM] e la lista si apre lo
 * stesso.
 */
enum class WishlistPriority(val key: String, val rank: Int) {
    HIGH("high", 0),
    MEDIUM("medium", 1),
    LOW("low", 2);

    companion object {
        fun fromKey(raw: String?): WishlistPriority =
            entries.firstOrNull { it.key.equals(raw?.trim(), ignoreCase = true) } ?: MEDIUM
    }
}

/**
 * I metadati di una carta dentro una lista.
 *
 * Stanno in una mappa a parte e non dentro `cardIds` perche' l'appartenenza
 * continua a essere un array di id: e' quello che `arrayUnion`/`arrayRemove`
 * aggiornano con una scrittura sola e su cui gira `whereArrayContains`. Una
 * carta senza voce qui dentro e' una carta aggiunta prima di questa versione (o
 * in blocco dal Chase): vale il default, non un errore.
 */
data class WishlistItem(
    val priority: String = WishlistPriority.MEDIUM.key,
    val note: String = "",
    /** Il prezzo oltre il quale non vuoi comprarla. 0 = nessun tetto. */
    val targetPrice: Double = 0.0,
    val addedAt: Timestamp? = null
) {
    val priorityValue: WishlistPriority get() = WishlistPriority.fromKey(priority)
    val hasTarget: Boolean get() = targetPrice > 0.0
    val hasNote: Boolean get() = note.isNotBlank()
}

data class Wishlist(
    val id: String = "",
    val name: String = "",
    val iconKey: String = WishlistIcons.POKEBALL,
    val cardIds: List<String> = emptyList(),
    /** Metadati per carta, chiave = cardId. Vedi [WishlistItem]. */
    val items: Map<String, WishlistItem> = emptyMap(),
    /** Tetto di spesa della lista in euro. 0 = nessun budget. */
    val budget: Double = 0.0,
    val createdAt: Timestamp? = null
) {
    fun itemFor(cardId: String): WishlistItem = items[cardId] ?: DEFAULT_ITEM

    fun priorityOf(cardId: String): WishlistPriority = itemFor(cardId).priorityValue

    val hasBudget: Boolean get() = budget > 0.0

    val highPriorityCount: Int
        get() = cardIds.count { priorityOf(it) == WishlistPriority.HIGH }

    private companion object {
        /** Una sola istanza: [itemFor] viene chiamata per ogni riga a ogni ricomposizione. */
        val DEFAULT_ITEM = WishlistItem()
    }
}

object WishlistIcons {
    const val POKEBALL = "pokeball"
    const val MASTER_BALL = "master_ball"
    const val PIKACHU = "pikachu"
    const val CHARIZARD = "charizard"
    const val EEVEE = "eevee"

    val all = listOf(POKEBALL, MASTER_BALL, PIKACHU, CHARIZARD, EEVEE)
}

package com.emabuia.pokevault.util

import com.emabuia.pokevault.data.model.PokemonCard

/**
 * Quali carte del catalogo si possiedono, e in che stampe: per il bordo verde
 * e i pallini delle stampe sulle tessere del catalogo. Le chiavi sono gli id
 * del catalogo (`apiCardId`), gli stessi di [com.emabuia.pokevault.data.remote.TcgCard.id].
 */
data class OwnedCardsIndex(
    val ids: Set<String> = emptySet(),
    val variants: Map<String, Set<String>> = emptyMap()
) {
    /** Una copia appena aggiunta, prima che arrivi il giro di Firestore. */
    fun plus(cardId: String, variant: String): OwnedCardsIndex {
        if (cardId.isBlank()) return this
        val newVariants = if (variant.isBlank()) variants
        else variants + (cardId to ((variants[cardId] ?: emptySet()) + variant))
        return OwnedCardsIndex(ids + cardId, newVariants)
    }

    companion object {
        /**
         * Dalla collezione intera, in una passata. Le copie doppie della stessa
         * stampa collassano: al badge interessa esserci o no. Le carte aggiunte
         * a mano senza id del catalogo non si possono riconoscere e restano fuori.
         */
        fun from(cards: List<PokemonCard>): OwnedCardsIndex {
            val ids = HashSet<String>()
            val variants = HashMap<String, MutableSet<String>>()
            for (card in cards) {
                val id = card.apiCardId.trim()
                // Come observeOwnedCards di SetDetailViewModel: la stessa carta
                // non deve risultare posseduta in una schermata e no nell'altra.
                if (id.isEmpty()) continue
                ids += id
                val variant = card.variant.trim()
                if (variant.isNotEmpty()) variants.getOrPut(id) { HashSet() } += variant
            }
            return OwnedCardsIndex(ids, variants)
        }
    }
}

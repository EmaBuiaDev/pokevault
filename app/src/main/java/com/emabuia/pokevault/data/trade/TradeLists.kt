package com.emabuia.pokevault.data.trade

import com.emabuia.pokevault.data.model.GoalAlbum
import com.emabuia.pokevault.data.model.PokemonCard
import com.emabuia.pokevault.data.model.Wishlist
import java.security.MessageDigest

/**
 * Da collezione, wishlist e album a quello che TradeRadar manda al server.
 * Funzioni pure: niente Firestore, niente rete, cosi' si provano coi test.
 */
object TradeLists {

    /**
     * Una carta che si puo' offrire: una stampa in una condizione e lingua.
     *
     * [spare] e' il massimo di copie offribili: per un doppione le copie oltre
     * la prima, per una carta singola quella sola (vedi [singles]). Quante se
     * ne offrono davvero lo sceglie l'utente.
     */
    data class Duplicate(
        val key: String,
        val variant: String,
        val condition: String,
        val language: String,
        val spare: Int,
        val name: String,
        val setName: String,
        val cardNumber: String,
        val imageUrl: String
    ) {
        /** Identifica la riga: la stessa carta in due condizioni sono due offerte. */
        val id: String get() = listOf(key, variant, condition, language).joinToString("|")
    }

    data class Want(val key: String, val source: String)

    /**
     * I doppioni offribili: copie oltre la prima.
     *
     * Restano fuori le carte solo-deck (non sono possedute, vedi memoria "carte
     * solo-deck"), le gradate (una slab non e' un doppione da scambiare al
     * volo) e le carte senza id italiano del catalogo.
     */
    fun duplicates(cards: List<PokemonCard>): List<Duplicate> =
        offerable(cards).filter { it.second > 1 }.map { (card, total) -> card.copy(spare = total - 1) }

    /**
     * Le carte possedute in una copia sola. Non sono doppioni e TradeRadar non
     * le propone mai da solo: entrano nella lista solo se l'utente le aggiunge a
     * mano. Stesse esclusioni di [duplicates].
     */
    fun singles(cards: List<PokemonCard>): List<Duplicate> =
        offerable(cards).filter { it.second == 1 }.map { (card, _) -> card.copy(spare = 1) }

    /** Ogni stampa offribile con le copie possedute in tutto: due documenti per la stessa stampa si sommano. */
    private fun offerable(cards: List<PokemonCard>): List<Pair<Duplicate, Int>> =
        cards.asSequence()
            .filter { !it.deckOnly && !it.isGraded && it.quantity > 0 }
            .mapNotNull { card ->
                val key = TradeCardKey.fromApiCardId(card.apiCardId) ?: return@mapNotNull null
                Duplicate(
                    key = key,
                    variant = card.variant,
                    condition = card.condition,
                    language = card.language,
                    spare = card.quantity,
                    name = card.name,
                    setName = card.set,
                    cardNumber = card.cardNumber,
                    imageUrl = card.imageUrl
                )
            }
            .groupBy { it.id }
            .map { (_, same) -> same.first() to same.sumOf { it.spare } }
            .sortedWith(compareBy({ it.first.setName }, { it.first.cardNumber.toIntOrNull() ?: Int.MAX_VALUE }, { it.first.cardNumber }))

    /** Le chiavi di tutte le carte possedute (solo-deck esclusi). */
    fun ownedKeys(cards: List<PokemonCard>): Set<String> =
        cards.asSequence()
            .filter { !it.deckOnly && it.quantity > 0 }
            .mapNotNull { TradeCardKey.fromApiCardId(it.apiCardId) }
            .toSortedSet()

    /**
     * Le carte cercate esplicitamente, meno quelle gia' possedute: wishlist
     * prima degli album, cosi' una carta in entrambe risulta "wishlist". I set
     * quasi completi non stanno qui: li calcola il server.
     */
    fun wants(wishlists: List<Wishlist>, goalAlbums: List<GoalAlbum>, owned: Set<String>): List<Want> {
        val result = LinkedHashMap<String, Want>()
        wishlists.flatMap { it.cardIds }.forEach { id ->
            TradeCardKey.fromApiCardId(id)?.takeIf { it !in owned }?.let { result.putIfAbsent(it, Want(it, "wishlist")) }
        }
        goalAlbums.flatMap { it.targetCardApiIds }.forEach { id ->
            TradeCardKey.fromApiCardId(id)?.takeIf { it !in owned }?.let { result.putIfAbsent(it, Want(it, "album")) }
        }
        return result.values.toList()
    }

    /** Impronta delle possedute: se non cambia, non si rimanda la lista. */
    fun ownedHash(owned: Set<String>): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(owned.sorted().joinToString("\n").toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }
}

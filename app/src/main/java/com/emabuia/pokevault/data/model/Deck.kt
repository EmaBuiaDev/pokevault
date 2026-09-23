package com.emabuia.pokevault.data.model

import com.emabuia.pokevault.util.PokemonSpriteResolver
import com.google.firebase.Timestamp

data class Deck(
    val id: String = "",
    val name: String = "",
    val cards: List<String> = emptyList(), // List of card IDs (from Firestore)
    val createdAt: Timestamp? = null,
    val mainTypes: List<String> = emptyList(),
    val averageHp: Double = 0.0,
    val totalCards: Int = 0,
    val recommendedEnergy: List<String> = emptyList(),
    val coverImageUrl: String = "",
    val coverImageUrls: List<String> = emptyList(),

    /**
     * Il deck contiene almeno una carta che l'utente non possiede.
     *
     * Non e' una preferenza ma una constatazione: viene ricalcolato a ogni
     * salvataggio guardando le carte dentro al deck. Serve solo a dirlo
     * nell'elenco, perche' un mazzo che non si puo' portare a un torneo deve
     * distinguersi da uno che si puo'.
     */
    val deckOnly: Boolean = false
) {
    fun displayCoverImageUrls(): List<String> {
        return (coverImageUrls + coverImageUrl)
            .filter { it.isNotBlank() }
            .distinct()
            .take(2)
    }

    /**
     * Le copertine scelte a mano, se sono sprite.
     *
     * I deck salvati prima che le copertine diventassero sprite hanno qui
     * dentro indirizzi di immagini di carte. Disegnarli vorrebbe dire
     * rimettere una carta stirata al posto del Pokemon -- proprio quello che
     * si e' tolto di mezzo -- quindi vengono scartati e il mazzo torna alla
     * scelta automatica, senza bisogno di migrare niente su Firestore.
     */
    fun chosenSpriteCovers(): List<String> =
        displayCoverImageUrls().filter { PokemonSpriteResolver.isSpriteUrl(it) }
}

data class DeckAnalysis(
    val typesCount: Map<String, Int> = emptyMap(),
    val commonWeaknesses: List<String> = emptyList(),
    val averageHp: Double = 0.0,
    val recommendedEnergy: List<String> = emptyList(),
    val synergies: List<String> = emptyList(),
    val supertypesCount: Map<String, Int> = emptyMap()
)

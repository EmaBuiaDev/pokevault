package com.emabuia.pokevault.viewmodel

import com.emabuia.pokevault.data.catalog.CardRef
import com.emabuia.pokevault.data.model.BasicEnergyResolver
import com.emabuia.pokevault.data.model.PokemonCard
import com.emabuia.pokevault.data.remote.SetCodeMapper

/** Le copie possedute trovate per una riga della decklist, e se sono di un altro set. */
internal data class OwnedMatch(
    val cards: List<PokemonCard>,
    val usedFallbackSet: Boolean
)

/**
 * Quali carte della collezione usare per una riga della decklist.
 *
 * Era dentro DeckLabViewModel e non si poteva provare. Il difetto stava nel
 * primo passo, "nome + set + numero": per le carte dei nostri cataloghi
 * ("ita:sv06:128") il set non si riconosceva mai e il nome era quello
 * italiano. Il passo falliva sempre, e l'import ripiegava sul nome: copie di
 * set diversi mescolate (2 Bayleef MEG 9 diventavano 1 MEG + 1 ASC) e avvisi
 * di "espansione diversa" anche su carte giuste.
 */
internal object DeckImportMatcher {

    fun find(owned: List<PokemonCard>, name: String, set: String?, number: String?): OwnedMatch {
        val nameLower = name.lowercase().trim()

        // Le energie base si cercano per tipo, non per stampa: "Basic Psychic
        // Energy SVE 5" e l'"Energia Psico" gia' in collezione sono la stessa
        // carta, e pretendere lo stesso set e lo stesso numero significava non
        // trovarla mai e aggiungerne una copia nuova a ogni import.
        if (BasicEnergyResolver.isBasicEnergy(name)) {
            val ownedEnergies = owned.filter { card -> BasicEnergyResolver.isSameBasicEnergy(card.name, name) }
            if (ownedEnergies.isNotEmpty()) {
                // Prima quelle con un'immagine: se in collezione ci sono gia' dei
                // segnaposto di import precedenti, non e' il caso di sceglierli.
                return OwnedMatch(ownedEnergies.sortedByDescending { it.imageUrl.isNotBlank() }, usedFallbackSet = false)
            }
        }

        fun sameSet(card: PokemonCard): Boolean = SetCodeMapper.matchesImportedSet(
            importedSet = set,
            cardSetName = card.set,
            cardApiSetId = card.apiCardId.substringBefore("-"),
            cardApiId = card.apiCardId
        )

        // 1. La stessa stampa: set + numero, e il nome. Per le carte dei nostri
        //    cataloghi set e numero bastano: la decklist e' in inglese e la
        //    collezione in italiano ("Lillie's Determination" e' "Determinazione
        //    di Lylia"), ma la carta e' la stessa.
        if (!set.isNullOrBlank() && !number.isNullOrBlank()) {
            val exact = owned.filter { card ->
                sameNumber(card, number) && sameSet(card) &&
                    (card.name.lowercase().trim() == nameLower || CardRef.parse(card.apiCardId) != null)
            }
            if (exact.isNotEmpty()) return OwnedMatch(exact, usedFallbackSet = false)
        }

        // 2. Nome + numero, con lo stesso set davanti quando c'e'.
        if (number != null) {
            val byNameAndNumber = owned
                .filter { card -> card.name.lowercase().trim() == nameLower && sameNumber(card, number) }
                .sortedByDescending { sameSet(it) }
            if (byNameAndNumber.isNotEmpty()) {
                val hasSetMatch = set.isNullOrBlank() || sameSet(byNameAndNumber.first())
                return OwnedMatch(byNameAndNumber, usedFallbackSet = !hasSetMatch)
            }
        }

        // 3. Solo il nome: le copie dello stesso set per prime, cosi' le copie
        //    mancanti si prendono da altre stampe solo dopo aver usato quelle giuste.
        val byName = owned
            .filter { card -> card.name.lowercase().trim() == nameLower }
            .sortedByDescending { sameSet(it) }
        if (byName.isNotEmpty()) {
            return OwnedMatch(byName, usedFallbackSet = !set.isNullOrBlank() && !sameSet(byName.first()))
        }

        // 4. Nome parziale (contiene).
        val byPartial = owned.filter { card ->
            card.name.lowercase().contains(nameLower) || nameLower.contains(card.name.lowercase())
        }
        return OwnedMatch(byPartial, usedFallbackSet = byPartial.isNotEmpty() && !set.isNullOrBlank())
    }

    /**
     * Stesso numero di carta. Oltre al confronto di prima, quello del catalogo
     * ("ita:me05:1") e quello senza zeri davanti: "001" e "1" sono la stessa carta.
     */
    internal fun sameNumber(card: PokemonCard, number: String): Boolean {
        val wanted = number.trim()
        if (card.cardNumber.trim() == wanted) return true
        val own = CardRef.parse(card.apiCardId)?.number ?: card.cardNumber.trim()
        return stripZeros(own).equals(stripZeros(wanted), ignoreCase = true)
    }

    private fun stripZeros(raw: String): String =
        raw.trim().substringBefore('/').trimStart('0').ifEmpty { "0" }
}

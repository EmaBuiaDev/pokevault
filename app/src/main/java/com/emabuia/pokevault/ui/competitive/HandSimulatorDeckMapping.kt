package com.emabuia.pokevault.ui.competitive

import com.emabuia.pokevault.data.model.CardClassifier
import com.emabuia.pokevault.data.model.Deck
import com.emabuia.pokevault.data.model.PokemonCard
import com.emabuia.pokevault.data.simulator.SimulatorCard
import com.emabuia.pokevault.util.AppLocale
import kotlin.math.roundToInt

/**
 * Da carte della collezione a carte simulabili.
 *
 * Vive in un file suo e non dentro la schermata perche' ora i consumatori sono
 * due — la modalita' Prova e quella Analisi — e lasciarlo privato di una delle
 * due avrebbe significato duplicarlo.
 */

/** Numero di carte di un mazzo legale: tutte le probabilita' assumono questo. */
internal const val LEGAL_DECK_SIZE = 60

internal fun buildDeckCardPool(deck: Deck, ownedCards: List<PokemonCard>): List<SimulatorCard> {
    val cardMap = ownedCards.associateBy { it.id }
    return deck.cards.mapNotNull { cardId ->
        val card = cardMap[cardId] ?: return@mapNotNull null
        SimulatorCard(
            name = card.name,
            isBasic = card.isBasicPokemon(),
            isEnergy = card.classify() == CardClassifier.ENERGY,
            isSupporter = card.isSupporterCard(),
            isOutCard = card.isOutCard(),
            id = card.id,
            imageUrl = card.imageUrl
        )
    }
}

/**
 * Vero quando lo stadio della carta non e' ricavabile dai dati.
 *
 * Le carte importate dal percorso di fallback di DeckLabViewModel arrivano con
 * subtypes vuoto e hp segnaposto: isBasicPokemon() le conta tutte come Basic,
 * perche' non trova marcatori di evoluzione. Su un mazzo che ne contiene molte
 * il tasso di mulligan risulta piu' basso del reale. Non possiamo indovinare lo
 * stadio, ma possiamo dirlo all'utente invece di presentare numeri precisi.
 */
private fun PokemonCard.hasUnknownStage(): Boolean =
    classify() == CardClassifier.POKEMON && hp > 0 && subtypes.isEmpty()

internal fun PokemonCard.isBasicPokemon(): Boolean {
    // A Pokémon is Basic if:
    // 1. It's classified as Pokémon and has HP > 0
    // 2. Either explicitly has "Basic" subtype OR has no evolution markers
    val isPokemon = classify() == CardClassifier.POKEMON
    if (!isPokemon || hp <= 0) return false

    val subtypesLower = subtypes.map { it.lowercase() }

    // Check if explicitly marked as Basic
    if (subtypesLower.any { it.contains("basic") || it.contains("base") }) {
        return true
    }

    // Check if it's NOT an evolution type - if no evolution markers, it's implicitly Basic
    val evolutionMarkers = listOf("stage 1", "stage 2", "stage1", "stage2", "v-max", "vmax", "vstar", "v-star", "lv.x")
    val hasEvolutionMarker = subtypesLower.any { subtype ->
        evolutionMarkers.any { marker -> subtype.contains(marker) }
    }

    return !hasEvolutionMarker
}

internal fun PokemonCard.isSupporterCard(): Boolean {
    if (classify() != CardClassifier.TRAINER) return false
    return subtypes.any {
        val normalized = it.lowercase()
        normalized.contains("supporter") || normalized.contains("aiuto")
    }
}

internal fun PokemonCard.isOutCard(): Boolean {
    val nameKey = name.lowercase().trim()
    val outKeywords = listOf(
        "ultra ball", "nest ball", "buddy-buddy poffin", "poffin", "earthen vessel",
        "research", "professor", "iono", "pokégear", "pokegear", "colress",
        "artazon", "forest seal stone", "rotom", "lumineon", "squawk"
    )
    val keywordMatch = outKeywords.any { key -> nameKey.contains(key) }
    return keywordMatch || isSupporterCard()
}

/**
 * Avvisi sulla qualita' dei dati del mazzo, da mostrare accanto ai risultati.
 *
 * Prima buildDeckCardPool rifiutava solo i mazzi con meno di 7 carte: un mazzo
 * da 45 veniva simulato come mazzo da 45, e ogni probabilita' (starter, mulligan,
 * energia al T1) risultava sbagliata rispetto alla matematica reale su 60 carte,
 * senza che nulla lo segnalasse.
 */
internal fun deckAccuracyWarnings(deck: Deck, ownedCards: List<PokemonCard>): List<String> {
    val warnings = mutableListOf<String>()

    val poolSize = deck.cards.size
    if (poolSize != LEGAL_DECK_SIZE) {
        warnings += AppLocale.handSimulatorDeckSizeWarning(poolSize, LEGAL_DECK_SIZE)
    }

    val cardMap = ownedCards.associateBy { it.id }
    val unknownStage = deck.cards
        .mapNotNull { cardMap[it] }
        .count { it.hasUnknownStage() }
    if (unknownStage > 0) {
        warnings += AppLocale.handSimulatorUnknownStageWarning(unknownStage)
    }

    return warnings
}

internal fun translateProblemTag(tag: String): String {
    return when (tag) {
        "NO_ENERGY_T1" -> AppLocale.handSimulatorTagNoEnergyT1
        "NO_OUT_T1" -> AppLocale.handSimulatorTagNoOutT1
        "SETUP_RISK_T2" -> AppLocale.handSimulatorTagSetupRiskT2
        "MISS_KEYCARD_T2" -> AppLocale.handSimulatorTagMissKeyT2
        "NO_BASIC_IN_DECK" -> AppLocale.handSimulatorTagNoBasicDeck
        else -> tag
    }
}

internal fun Double.roundPercent(): Int = roundToInt()

internal fun Double.roundTo2Decimals(): String {
    val rounded = (this * 100.0).roundToInt() / 100.0
    return rounded.toString()
}

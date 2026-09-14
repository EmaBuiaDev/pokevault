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
 * Due strade ci portano qui: le carte importate dal percorso di fallback di
 * DeckLabViewModel, che arrivano con subtypes vuoto, e quelle il cui sottotipo
 * non nomina lo stadio ("ex", "V", "Ultra Beast"). In entrambi i casi
 * isBasicPokemon() le conta come Base, e su un mazzo che ne contiene molte il
 * tasso di mulligan risulta piu' basso del reale. Non possiamo indovinare lo
 * stadio, ma possiamo dirlo all'utente invece di presentare numeri precisi.
 */
private fun PokemonCard.hasUnknownStage(): Boolean =
    classify() == CardClassifier.POKEMON && hp > 0 && !hasKnownStage()

private fun PokemonCard.hasKnownStage(): Boolean =
    subtypes.map { it.normalizeStage() }.any { sub ->
        sub.isNotEmpty() && (
            EVOLUTION_STAGE_MARKERS.any { sub.contains(it) } ||
                BASIC_STAGE_MARKERS.any { sub.contains(it) }
            )
    }

/**
 * Stadi che NON si possono calare in campo dalla mano.
 *
 * Le stringhe sono gia' normalizzate (minuscole, senza spazi ne' punteggiatura),
 * cosi' "Stage 1", "Stage1" e "STAGE-1" cadono tutte sullo stesso marcatore. Le
 * forme italiane ci sono perche' il campo `stage` di PokeWallet arriva nella
 * lingua della carta: senza "fase1"/"stadio1" una Fase 1 non trovava alcun
 * marcatore di evoluzione e finiva contata come Base.
 */
private val EVOLUTION_STAGE_MARKERS = listOf(
    "stage1", "stage2", "stage3",
    "stadio1", "stadio2",
    "fase1", "fase2",
    // TCGdex, da cui arriva il nostro catalogo, traduce Stage con "Livello":
    // il backfill lo riporta alla grafia inglese, ma questi due coprono le
    // carte entrate in collezione prima che la colonna esistesse.
    "livello1", "livello2",
    "evolution", "evoluzione", "evolved", "evoluto",
    "vmax", "vstar", "vunion",
    "mega",
    "break", "lvx", "levelup", "legend",
    "restored", "risorto"
)

/** Marcatori espliciti di carta Base, nelle due lingue in cui arrivano i dati. */
private val BASIC_STAGE_MARKERS = listOf("basic", "base")

private fun String.normalizeStage(): String = lowercase().filter { it.isLetterOrDigit() }

internal fun PokemonCard.isBasicPokemon(): Boolean {
    // Basic vuol dire una cosa sola: la carta si puo' mettere in campo dalla
    // mano. E' il presupposto di tutto il simulatore -- mulligan, starter rate,
    // riga "N Basic" sotto la mano -- quindi una Fase 1 contata come Base non e'
    // un'imprecisione di etichetta: e' un numero sbagliato.
    val isPokemon = classify() == CardClassifier.POKEMON
    if (!isPokemon || hp <= 0) return false

    val normalized = subtypes.map { it.normalizeStage() }.filter { it.isNotEmpty() }

    // Gli stadi evolutivi vanno letti PRIMA di "base": su una carta con piu'
    // sottotipi ("Stage 2", "ex") e' l'evoluzione a decidere.
    if (normalized.any { sub -> EVOLUTION_STAGE_MARKERS.any { sub.contains(it) } }) {
        return false
    }

    if (normalized.any { sub -> BASIC_STAGE_MARKERS.any { sub.contains(it) } }) {
        return true
    }

    // Nessun marcatore riconosciuto: restano i sottotipi che non dicono lo
    // stadio ("ex", "V", "Ultra Beast") e le carte senza sottotipi. Contarle
    // come Base e' la scelta ottimista di sempre, ed e' quella che
    // deckAccuracyWarnings segnala all'utente.
    return true
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

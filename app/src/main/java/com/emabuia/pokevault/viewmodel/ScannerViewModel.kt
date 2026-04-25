package com.emabuia.pokevault.viewmodel

import android.app.Application
import android.graphics.Bitmap
import androidx.compose.runtime.getValue
import com.emabuia.pokevault.BuildConfig
import timber.log.Timber
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.emabuia.pokevault.data.firebase.FirestoreRepository
import com.emabuia.pokevault.data.model.PokemonCard
import com.emabuia.pokevault.data.remote.PokeTcgRepository
import com.emabuia.pokevault.data.remote.RepositoryProvider
import com.emabuia.pokevault.data.remote.TcgCard
import com.emabuia.pokevault.ocr.CardOCRResult
import com.emabuia.pokevault.ocr.CardSupertype
import com.emabuia.pokevault.ocr.OCRManager
import com.emabuia.pokevault.util.minimumEurPriceOrZero
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.max

data class ScannerUiState(
    val isSearching: Boolean = false,
    /** Carta trovata in attesa di conferma dall'utente */
    val pendingCard: TcgCard? = null,
    /** Candidati mostrati quando il match migliore non e abbastanza netto */
    val candidateCards: List<TcgCard> = emptyList(),
    /** Carta appena aggiunta (conferma visiva temporanea) */
    val lastAddedCard: TcgCard? = null,
    val addedCount: Int = 0,
    val errorMessage: String? = null,
    val flashEnabled: Boolean = false,
    val detectedName: String = "",
    val detectedNumber: String = "",
    val lastOCRResult: CardOCRResult? = null,
    val ocrEngineName: String = ""
)

class ScannerViewModel(application: Application) : AndroidViewModel(application) {

    private data class RankedCard(
        val card: TcgCard,
        val score: Int,
        val nameSimilarity: Double
    )

    private data class MatchResolution(
        val bestCard: TcgCard? = null,
        val ambiguousCandidates: List<TcgCard> = emptyList()
    )

    private val repository = RepositoryProvider.tcgRepository
    private val firestoreRepository = FirestoreRepository()
    private var searchJob: Job? = null

    private val ocrManager = OCRManager(application)

    private val recentlyAddedIds = mutableSetOf<String>()
    private val recentSearchAttempts = mutableMapOf<String, Long>()
    private var lastSearchTimestamp = 0L

    /**
     * Chiave della ricerca attualmente in corso o completata.
     * Basata SOLO sul numero carta (dato OCR piu stabile).
     * Impedisce di rilanciare la stessa ricerca su ogni frame.
     */
    private var activeSearchKey = ""

    /**
     * Contatore di stabilita: quante volte consecutive abbiamo visto
     * lo stesso numero carta. Dopo STABILITY_THRESHOLD frame stabili,
     * lanciamo la ricerca immediatamente.
     */
    private var stableNumber = ""
    private var stableTotal = ""
    private var stableName = ""
    private var stableSupertype: CardSupertype = CardSupertype.POKEMON
    private var stabilityCount = 0

    var uiState by mutableStateOf(ScannerUiState())
        private set

    init {
        viewModelScope.launch {
            try {
                ocrManager.initialize()
                uiState = uiState.copy(ocrEngineName = ocrManager.activeEngineName)
                Timber.i("OCR inizializzato: ${ocrManager.activeEngineName}")
            } catch (e: Exception) {
                Timber.e(e, "Errore inizializzazione OCR: ${e.message}")
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        ocrManager.release()
    }

    // ═══════════════════════════════════════════
    // OCR DA CAMERA FRAME
    // ═══════════════════════════════════════════

    /**
     * Chiamata dalla camera ad ogni frame con testo OCR grezzo.
     *
     * Logica di stabilita:
     * - Estrae il numero carta (dato piu stabile dall'OCR)
     * - Conta i frame consecutivi con lo stesso numero
     * - Dopo N frame stabili, lancia la ricerca SENZA attendere
     * - Non cancella ricerche in corso se il numero non cambia
     */
    fun onTextDetected(rawText: String) {
        if (rawText.isBlank()) return
        if (uiState.pendingCard != null || uiState.candidateCards.isNotEmpty() || uiState.lastAddedCard != null) return

        val ocrResult = ocrManager.extractCardFields(rawText)
        if (!ocrResult.isSearchable()) return

        val number = ocrResult.cardNumber ?: ""
        val total = ocrResult.setTotal ?: ""
        val name = ocrResult.cardName ?: ""

        // Aggiorna UI con il testo rilevato
        if (number.isNotBlank() || name.isNotBlank()) {
            uiState = uiState.copy(
                detectedName = name,
                detectedNumber = if (number.isNotBlank() && total.isNotBlank()) "$number/$total" else number,
                lastOCRResult = ocrResult
            )
        }

        // Chiave di ricerca basata sul numero (piu stabile)
        val searchKey = number.ifBlank { name }
        if (searchKey.isBlank()) return

        // Se la ricerca per questa chiave e gia partita o completata, non rilanciarla
        if (searchKey == activeSearchKey) return

        val now = System.currentTimeMillis()
        if (now - lastSearchTimestamp < SEARCH_MIN_INTERVAL_MS) return
        val lastAttempt = recentSearchAttempts[searchKey] ?: 0L
        if (now - lastAttempt < SEARCH_KEY_COOLDOWN_MS) return

        // Aggiorna contatore di stabilita
        if (number.isNotBlank() && number == stableNumber) {
            stabilityCount++
            // Aggiorna nome e totale col valore piu recente (possono migliorare frame dopo frame)
            if (name.isNotBlank()) stableName = name
            if (total.isNotBlank()) stableTotal = total
            // Supertype non-Pokemon ha priorita (TRAINER/ENERGY sono segnali forti e affidabili)
            if (ocrResult.supertype != CardSupertype.POKEMON) stableSupertype = ocrResult.supertype
        } else {
            // Numero cambiato: reset stabilita
            stableNumber = number
            stableTotal = total
            stableName = name
            stableSupertype = ocrResult.supertype
            stabilityCount = 1
            // Cancella ricerca precedente solo se il numero e davvero cambiato
            searchJob?.cancel()
        }

        // Lancio ricerca quando il numero e stabile.
        // Se OCR ha gia numero/totale completi, basta un frame stabile.
        val requiredStability = when {
            number.isNotBlank() && total.isNotBlank() -> FAST_STABILITY_THRESHOLD
            else -> STABILITY_THRESHOLD
        }

        if (stabilityCount >= requiredStability && searchJob?.isActive != true) {
            activeSearchKey = searchKey
            recentSearchAttempts[searchKey] = now
            lastSearchTimestamp = now
            searchJob = viewModelScope.launch {
                searchCard(
                    name = stableName.takeIf { it.isNotBlank() },
                    number = stableNumber.takeIf { it.isNotBlank() },
                    setTotal = stableTotal.takeIf { it.isNotBlank() },
                    supertype = stableSupertype
                )
            }
        }
    }

    // ═══════════════════════════════════════════
    // OCR DA BITMAP
    // ═══════════════════════════════════════════

    fun analyzeCardImage(bitmap: Bitmap) {
        if (uiState.pendingCard != null || uiState.candidateCards.isNotEmpty()) return
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            uiState = uiState.copy(isSearching = true, errorMessage = null)
            try {
                val result = ocrManager.analyzeCardImage(bitmap)
                uiState = uiState.copy(
                    detectedName = result.cardName ?: "",
                    detectedNumber = result.cardNumber ?: "",
                    lastOCRResult = result
                )
                if (result.isSearchable()) {
                    searchCard(result.cardName, result.cardNumber, result.setTotal, result.supertype)
                } else {
                    uiState = uiState.copy(
                        isSearching = false,
                        errorMessage = "Testo non riconosciuto. Riprova con una foto più nitida."
                    )
                }
            } catch (e: Exception) {
                Timber.e(e, "Errore analisi immagine: ${e.message}")
                uiState = uiState.copy(
                    isSearching = false,
                    errorMessage = "Errore OCR: ${e.message}"
                )
            }
        }
    }

    // ═══════════════════════════════════════════
    // RICERCA CON SET MATCHING
    // ═══════════════════════════════════════════

    /**
     * Cerca la carta usando 3 strategie in ordine di precisione:
     *
     * 1. numero/totale → identifica il set dal totale stampato, cerca per numero nel set
     * 2. nome + numero → cerca per nome e numero combinati
     * 3. nome → cerca solo per nome (fallback)
     *
     * Se un nome e disponibile, filtra i risultati per il match migliore.
     */
    private suspend fun searchCard(
        name: String?,
        number: String?,
        setTotal: String? = null,
        supertype: CardSupertype = CardSupertype.POKEMON
    ) {
        uiState = uiState.copy(isSearching = true, errorMessage = null)

        try {
            val candidates = linkedMapOf<String, TcgCard>()
            val candidateSetIds = repository.getCandidateSetIdsByPrintedTotal(setTotal, tolerance = SET_TOTAL_TOLERANCE)
            val normalizedSearchName = normalizeNameForApiSearch(name)
            val queryName = normalizedSearchName ?: name

            fun addCandidates(results: List<TcgCard>) {
                results.forEach { card -> candidates.putIfAbsent(card.id, card) }
            }

            fun hasStrongCandidate(): Boolean {
                if (candidates.isEmpty()) return false
                val exactNumberMatch = number != null && candidates.values.any { normalizeCardNumber(it.number) == number }
                if (!exactNumberMatch) return false
                if (setTotal == null) return true
                return candidates.values.any { repository.getPrintedTotalForSet(it.set?.id)?.toString() == setTotal }
            }

            // Strategia 1: numero/totale → usa searchCards che identifica il set
            if (number != null && setTotal != null) {
                val fullNumber = "$number/$setTotal"
                Timber.d("Ricerca con numero completo: $fullNumber")
                repository.searchCards(fullNumber)
                    .onSuccess(::addCandidates)
            }

            // Strategia 2: nome + numero, preferendo i set compatibili col totale OCR
            if (queryName != null && number != null && candidateSetIds.isNotEmpty()) {
                Timber.d("Ricerca set-aware con nome+numero: $queryName #$number in ${candidateSetIds.size} set")
                candidateSetIds.take(MAX_SET_CANDIDATES).forEach { setId ->
                    repository.searchByNameAndNumber(queryName, number, setId)
                        .onSuccess(::addCandidates)
                }
            }

            if (queryName != null && number != null) {
                Timber.d("Ricerca con nome+numero: $queryName #$number")
                repository.searchByNameAndNumber(queryName, number)
                    .onSuccess(::addCandidates)
            }

            // Strategia 3: solo nome
            if (!hasStrongCandidate() && queryName != null && isUsableSearchName(queryName)) {
                Timber.d("Ricerca con solo nome: $queryName")
                repository.searchCardsFuzzy(queryName)
                    .onSuccess { results ->
                        val filtered = if (number != null) {
                            val exactNumber = results.filter { normalizeCardNumber(it.number) == number }
                            exactNumber.ifEmpty { results }
                        } else {
                            results
                        }
                        addCandidates(filtered)
                    }
            }

            // Strategia 4: solo numero (ultimo fallback)
            if (candidates.isEmpty() && number != null) {
                Timber.d("Ricerca con solo numero: $number")
                repository.searchCards(number)
                    .onSuccess(::addCandidates)
            }

            val resolution = resolveMatch(
                cards = candidates.values.toList(),
                name = name,
                number = number,
                setTotal = setTotal,
                supertype = supertype
            )
            val bestMatch = resolution.bestCard
            if (bestMatch != null && bestMatch.id !in recentlyAddedIds) {
                uiState = uiState.copy(
                    isSearching = false,
                    pendingCard = bestMatch,
                    candidateCards = emptyList(),
                    errorMessage = null
                )
            } else if (bestMatch != null) {
                // Carta gia scartata/aggiunta in questa sessione
                uiState = uiState.copy(isSearching = false)
            } else if (resolution.ambiguousCandidates.isNotEmpty()) {
                uiState = uiState.copy(
                    isSearching = false,
                    pendingCard = null,
                    candidateCards = resolution.ambiguousCandidates,
                    errorMessage = "Più risultati possibili. Seleziona la carta corretta."
                )
            } else {
                uiState = uiState.copy(
                    isSearching = false,
                    pendingCard = null,
                    candidateCards = emptyList(),
                    errorMessage = if (candidates.isNotEmpty()) {
                        "Risultato ambiguo. Riprova inquadrando meglio nome e numero."
                    } else {
                        "Nessuna carta trovata. Riprova."
                    }
                )
                activeSearchKey = ""
            }
        } catch (e: Exception) {
            Timber.w("Search failed: ${e.message}")
            uiState = uiState.copy(
                isSearching = false,
                errorMessage = "Errore ricerca: ${e.message}"
            )
            activeSearchKey = ""
        }
    }

    private fun resolveMatch(
        cards: List<TcgCard>,
        name: String?,
        number: String?,
        setTotal: String?,
        supertype: CardSupertype
    ): MatchResolution {
        if (cards.isEmpty()) return MatchResolution()

        val exactNumberCards = number?.let { expected ->
            cards.filter { normalizeCardNumber(it.number) == expected }
        }.orEmpty()
        val pool = exactNumberCards.ifEmpty { cards }

        val strictPool = when (supertype) {
            CardSupertype.TRAINER -> pool.filter { it.supertype.equals("trainer", ignoreCase = true) }
            CardSupertype.ENERGY -> pool.filter { it.supertype.equals("energy", ignoreCase = true) }
            CardSupertype.POKEMON -> emptyList()
        }.ifEmpty { pool }

        val ranked = strictPool
            .filterNot { it.id in recentlyAddedIds }
            .map { rankCandidate(it, name, number, setTotal, supertype) }
            .sortedWith(compareByDescending<RankedCard> { it.score }.thenByDescending { it.nameSimilarity })

        val best = ranked.firstOrNull() ?: return MatchResolution()
        val second = ranked.getOrNull(1)
        val exactNumberRanked = number?.let { expected ->
            ranked.filter { normalizeCardNumber(it.card.number) == expected }
        }.orEmpty()
        val exactSetRanked = setTotal?.let { expected ->
            ranked.filter { repository.getPrintedTotalForSet(it.card.set?.id)?.toString() == expected }
        }.orEmpty()
        val exactNumberAndSetRanked = if (number != null && setTotal != null) {
            exactNumberRanked.filter { rankedCard ->
                repository.getPrintedTotalForSet(rankedCard.card.set?.id)?.toString() == setTotal
            }
        } else {
            emptyList()
        }

        if (BuildConfig.DEBUG) {
            ranked.take(3).forEach { rankedCard ->
                Timber.d(
                    "Scanner candidate %s score=%d nameSimilarity=%.2f",
                    rankedCard.card.name,
                    rankedCard.score,
                    rankedCard.nameSimilarity
                )
            }
        }

        if (exactNumberAndSetRanked.size == 1) {
            return MatchResolution(bestCard = exactNumberAndSetRanked.first().card)
        }
        if (exactNumberRanked.size == 1 && exactSetRanked.isEmpty()) {
            return MatchResolution(bestCard = exactNumberRanked.first().card)
        }

        val topMargin = best.score - (second?.score ?: Int.MIN_VALUE)
        val hasStrongNumberSignal = number != null && normalizeCardNumber(best.card.number) == number
        val hasStrongSetSignal = setTotal != null && repository.getPrintedTotalForSet(best.card.set?.id)?.toString() == setTotal
        val ambiguousCandidates = ranked
            .take(MAX_AMBIGUOUS_CANDIDATES)
            .filter { rankedCard ->
                rankedCard.score >= MIN_CANDIDATE_SCORE &&
                    (rankedCard.nameSimilarity >= MIN_CANDIDATE_NAME_SIMILARITY ||
                        (number != null && normalizeCardNumber(rankedCard.card.number) == number))
            }
            .map { it.card }

        if (best.score < MIN_MATCH_SCORE) {
            return MatchResolution(ambiguousCandidates = ambiguousCandidates.takeIf { it.size >= 2 }.orEmpty())
        }
        if (second != null && topMargin < MIN_SCORE_MARGIN && !(hasStrongNumberSignal && hasStrongSetSignal && best.nameSimilarity >= MIN_NAME_SIMILARITY)) {
            return MatchResolution(ambiguousCandidates = ambiguousCandidates.takeIf { it.size >= 2 }.orEmpty())
        }

        return MatchResolution(bestCard = best.card)
    }

    private fun rankCandidate(
        card: TcgCard,
        name: String?,
        number: String?,
        setTotal: String?,
        supertype: CardSupertype
    ): RankedCard {
        var score = 0
        val nameSimilarity = computeNameSimilarity(name, card.name)
        val normalizedExpectedNumber = number?.takeIf { it.isNotBlank() }
        val normalizedCardNumber = normalizeCardNumber(card.number)

        if (normalizedExpectedNumber != null) {
            score += if (normalizedCardNumber == normalizedExpectedNumber) 45 else -30
        }

        if (setTotal != null) {
            val printedTotal = repository.getPrintedTotalForSet(card.set?.id)
            if (printedTotal != null) {
                val totalValue = setTotal.toIntOrNull()
                score += when {
                    totalValue == null -> 0
                    printedTotal == totalValue -> 25
                    kotlin.math.abs(printedTotal - totalValue) == 1 -> 10
                    else -> -10
                }
            }
        }

        val expectedApiType = when (supertype) {
            CardSupertype.TRAINER -> "trainer"
            CardSupertype.ENERGY -> "energy"
            CardSupertype.POKEMON -> "pokemon"
        }
        score += when {
            supertype == CardSupertype.POKEMON && card.supertype.equals("pokemon", ignoreCase = true) -> 10
            card.supertype.equals(expectedApiType, ignoreCase = true) -> 20
            supertype != CardSupertype.POKEMON -> -25
            else -> -5
        }

        if (name != null) {
            score += (nameSimilarity * 45).toInt()
            if (nameSimilarity >= 0.96) score += 15
            if (nameSimilarity < MIN_NAME_SIMILARITY && normalizedExpectedNumber == null) score -= 15
        }

        return RankedCard(card = card, score = score, nameSimilarity = nameSimilarity)
    }

    private fun computeNameSimilarity(expectedName: String?, actualName: String): Double {
        val normalizedExpected = normalizeNameForMatching(expectedName)
        val normalizedActual = normalizeNameForMatching(actualName)

        if (normalizedExpected.isBlank() || normalizedActual.isBlank()) return 0.0
        if (normalizedExpected == normalizedActual) return 1.0
        if (normalizedActual.startsWith(normalizedExpected) || normalizedExpected.startsWith(normalizedActual)) {
            return 0.92
        }

        val distance = levenshtein(normalizedExpected, normalizedActual)
        val maxLength = max(normalizedExpected.length, normalizedActual.length)
        val charSimilarity = (1.0 - distance.toDouble() / maxLength.toDouble()).coerceIn(0.0, 1.0)

        val expectedTokens = normalizedExpected.split(" ").filter { it.isNotBlank() }.toSet()
        val actualTokens = normalizedActual.split(" ").filter { it.isNotBlank() }.toSet()
        val tokenSimilarity = if (expectedTokens.isNotEmpty() && actualTokens.isNotEmpty()) {
            expectedTokens.intersect(actualTokens).size.toDouble() /
                max(expectedTokens.size, actualTokens.size).toDouble()
        } else {
            0.0
        }

        return (charSimilarity * 0.75) + (tokenSimilarity * 0.25)
    }

    private fun normalizeNameForMatching(name: String?): String {
        if (name.isNullOrBlank()) return ""

        val stopWords = setOf(
            "trainer", "allenatore", "supporter", "aiuto", "item", "strumento",
            "stadium", "stadio", "tool", "energy", "energia", "pokemon", "pokmon",
            "basic", "base", "lotta", "fight", "fighting", "ability", "abilita",
            "attack", "attacco"
        )

        return name
            .lowercase()
            .replace(Regex("""[^a-z0-9à-ÿ\s'-]"""), " ")
            .split(Regex("""\s+"""))
            .filter { token -> token.length >= 2 && token !in stopWords }
            .joinToString(" ")
            .trim()
    }

    private fun normalizeNameForApiSearch(name: String?): String? {
        val normalized = normalizeNameForMatching(name)
        if (normalized.isBlank()) return null

        val tokens = normalized
            .split(Regex("""\s+"""))
            .filter { it.length >= 3 }

        if (tokens.isEmpty()) return null
        return tokens.take(2).joinToString(" ")
    }

    private fun isUsableSearchName(name: String): Boolean {
        val normalized = normalizeNameForMatching(name)
        return normalized.length >= 3 && normalized.any { it.isLetter() }
    }

    private fun normalizeCardNumber(number: String): String {
        val digits = number.takeWhile { it.isDigit() }
        return digits.trimStart('0').ifEmpty { digits.ifBlank { number }.trimStart('0').ifEmpty { "0" } }
    }

    private fun levenshtein(left: String, right: String): Int {
        if (left == right) return 0
        if (left.isEmpty()) return right.length
        if (right.isEmpty()) return left.length

        val previous = IntArray(right.length + 1) { it }
        val current = IntArray(right.length + 1)

        for (leftIndex in left.indices) {
            current[0] = leftIndex + 1
            for (rightIndex in right.indices) {
                val substitutionCost = if (left[leftIndex] == right[rightIndex]) 0 else 1
                current[rightIndex + 1] = minOf(
                    current[rightIndex] + 1,
                    previous[rightIndex + 1] + 1,
                    previous[rightIndex] + substitutionCost
                )
            }
            previous.indices.forEach { index -> previous[index] = current[index] }
        }

        return previous[right.length]
    }

    // ═══════════════════════════════════════════
    // CONFERMA / SCARTA
    // ═══════════════════════════════════════════

    fun confirmAdd() {
        val card = uiState.pendingCard ?: return
        uiState = uiState.copy(pendingCard = null, candidateCards = emptyList(), isSearching = true)
        viewModelScope.launch { addToFirestore(card) }
    }

    fun selectCandidate(card: TcgCard) {
        uiState = uiState.copy(
            pendingCard = card,
            candidateCards = emptyList(),
            errorMessage = null
        )
    }

    fun dismissCard() {
        val card = uiState.pendingCard
        uiState = uiState.copy(
            pendingCard = null,
            candidateCards = emptyList(),
            detectedName = "",
            detectedNumber = "",
            errorMessage = null
        )
        resetStability()
        if (card != null) recentlyAddedIds.add(card.id)
    }

    // ═══════════════════════════════════════════
    // SALVATAGGIO FIRESTORE
    // ═══════════════════════════════════════════

    private suspend fun addToFirestore(tcgCard: TcgCard) {
        val resolvedCard = repository.getCard(tcgCard.id, preferNetwork = true).getOrNull() ?: tcgCard
        val price = resolvedCard.cardmarket?.prices.minimumEurPriceOrZero()

        val pokemonCard = PokemonCard(
            name = resolvedCard.name,
            imageUrl = resolvedCard.images.large.ifBlank { resolvedCard.images.small },
            set = resolvedCard.set?.name ?: "",
            rarity = resolvedCard.rarity ?: "",
            type = resolvedCard.types?.firstOrNull() ?: resolvedCard.supertype,
            hp = resolvedCard.hp?.toIntOrNull() ?: 0,
            estimatedValue = price,
            quantity = 1,
            condition = "Near Mint",
            apiCardId = resolvedCard.id,
            cardNumber = resolvedCard.number
        )

        firestoreRepository.addCard(pokemonCard)
            .onSuccess {
                recentlyAddedIds.add(tcgCard.id)
                uiState = uiState.copy(
                    isSearching = false,
                    lastAddedCard = tcgCard,
                    addedCount = uiState.addedCount + 1,
                    errorMessage = null
                )
                val addedCardId = tcgCard.id
                viewModelScope.launch {
                    delay(2500)
                    if (uiState.lastAddedCard?.id == addedCardId) {
                        uiState = uiState.copy(
                            lastAddedCard = null,
                            detectedName = "",
                            detectedNumber = ""
                        )
                        resetStability()
                    }
                }
            }
            .onFailure { error ->
                recentlyAddedIds.remove(tcgCard.id)
                uiState = uiState.copy(
                    isSearching = false,
                    errorMessage = "Errore salvataggio: ${error.message}"
                )
                resetStability()
            }
    }

    // ═══════════════════════════════════════════
    // UTILITY
    // ═══════════════════════════════════════════

    private fun resetStability() {
        activeSearchKey = ""
        stableNumber = ""
        stableTotal = ""
        stableName = ""
        stableSupertype = CardSupertype.POKEMON
        stabilityCount = 0
        recentSearchAttempts.entries.removeIf { System.currentTimeMillis() - it.value > SEARCH_KEY_COOLDOWN_MS * 2 }
    }

    fun toggleFlash() {
        uiState = uiState.copy(flashEnabled = !uiState.flashEnabled)
    }

    fun resetScanner() {
        recentlyAddedIds.clear()
        searchJob?.cancel()
        resetStability()
        uiState = ScannerUiState(
            flashEnabled = uiState.flashEnabled,
            addedCount = uiState.addedCount,
            ocrEngineName = ocrManager.activeEngineName
        )
    }

    fun clearError() {
        uiState = uiState.copy(errorMessage = null)
    }

    companion object {
        private const val TAG = "ScannerViewModel"
        private const val STABILITY_THRESHOLD = 3
        private const val FAST_STABILITY_THRESHOLD = 2
        private const val SET_TOTAL_TOLERANCE = 3
        private const val MAX_SET_CANDIDATES = 2
        private const val MAX_AMBIGUOUS_CANDIDATES = 3
        private const val MIN_MATCH_SCORE = 48
        private const val MIN_CANDIDATE_SCORE = 20
        private const val MIN_SCORE_MARGIN = 8
        private const val MIN_NAME_SIMILARITY = 0.42
        private const val MIN_CANDIDATE_NAME_SIMILARITY = 0.18
        private const val SEARCH_MIN_INTERVAL_MS = 1500L
        private const val SEARCH_KEY_COOLDOWN_MS = 6000L
    }
}

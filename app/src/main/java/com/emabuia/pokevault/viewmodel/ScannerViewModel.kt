package com.emabuia.pokevault.viewmodel

import android.app.Application
import android.graphics.Bitmap
import androidx.compose.runtime.getValue
import timber.log.Timber
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.emabuia.pokevault.data.firebase.FirestoreRepository
import com.emabuia.pokevault.data.model.PokemonCard
import com.emabuia.pokevault.data.remote.RepositoryProvider
import com.emabuia.pokevault.data.remote.SetCodeMapper
import com.emabuia.pokevault.data.remote.TcgCard
import com.emabuia.pokevault.ocr.CardOCRResult
import com.emabuia.pokevault.ocr.CardSupertype
import com.emabuia.pokevault.ocr.OCRManager
import com.emabuia.pokevault.util.minimumEurPriceOrZero
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.max
import java.util.Locale

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

    private data class ScannerCandidate(
        val card: TcgCard,
        val nameSimilarity: Double,
        val totalMatches: Boolean
    )

    private val repository = RepositoryProvider.tcgRepository
    private val firestoreRepository = FirestoreRepository()
    private val appContext: Application get() = getApplication()
    private var searchJob: Job? = null

    private val ocrManager = OCRManager()

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
    private var stableSetHint = ""
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
        val setHint = ocrResult.setCode ?: ocrResult.setName ?: ""

        // Aggiorna UI con il testo rilevato
        if (number.isNotBlank() || name.isNotBlank()) {
            uiState = uiState.copy(
                detectedName = name,
                detectedNumber = if (number.isNotBlank() && total.isNotBlank()) "$number/$total" else number,
                lastOCRResult = ocrResult
            )
        }

        // Chiave ricerca: numero+totale (obbligatori) piu nome/set quando leggibili.
        val normalizedNameKey = normalizeNameForMatching(name)
        val searchKey = listOf(number, total, normalizedNameKey, setHint.trim().lowercase(Locale.ROOT))
            .joinToString("|")
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
            if (setHint.isNotBlank()) stableSetHint = setHint
            // Supertype non-Pokemon ha priorita (TRAINER/ENERGY sono segnali forti e affidabili)
            if (ocrResult.supertype != CardSupertype.POKEMON) stableSupertype = ocrResult.supertype
        } else {
            // Numero cambiato: reset stabilita
            stableNumber = number
            stableTotal = total
            stableName = name
            stableSetHint = setHint
            stableSupertype = ocrResult.supertype
            stabilityCount = 1
            // Cancella ricerca precedente solo se il numero e davvero cambiato
            searchJob?.cancel()
        }

        // Lancio ricerca quando numero+totale sono stabili.
        // Il nome OCR aiuta ma NON blocca: numero/totale sono i dati piu affidabili
        // e il totale set basta a disambiguare l'espansione (es. 067/087 -> me04).
        val hasNumberAndTotal = stableNumber.isNotBlank() && stableTotal.isNotBlank()
        val hasUsableStableName = isUsableSearchName(stableName)
        val requiredStability = when {
            hasNumberAndTotal && hasUsableStableName && stableSetHint.isNotBlank() -> FAST_STABILITY_THRESHOLD
            hasNumberAndTotal && hasUsableStableName -> STABILITY_THRESHOLD
            else -> NO_NAME_STABILITY_THRESHOLD
        }

        if (hasNumberAndTotal && stabilityCount >= requiredStability && searchJob?.isActive != true) {
            activeSearchKey = searchKey
            recentSearchAttempts[searchKey] = now
            lastSearchTimestamp = now
            searchJob = viewModelScope.launch {
                searchCard(
                    name = stableName.takeIf { isUsableSearchName(it) },
                    number = stableNumber.takeIf { it.isNotBlank() },
                    setTotal = stableTotal.takeIf { it.isNotBlank() },
                    setHint = stableSetHint.takeIf { it.isNotBlank() },
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
                    searchCard(
                        name = result.cardName,
                        number = result.cardNumber,
                        setTotal = result.setTotal,
                        setHint = result.setCode ?: result.setName,
                        supertype = result.supertype
                    )
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
     * Pipeline scanner SOLO ITA cloud, una sola ricerca nel catalogo:
     *  - numero carta = filtro hard
     *  - totale set + setHint OCR = disambiguazione espansione
     *  - nome OCR = conferma (mai bloccante: se sporco si mostra il picker)
     *
     * Auto-proposta solo con segnali coerenti; in dubbio si mostrano i candidati.
     */
    private suspend fun searchCard(
        name: String?,
        number: String?,
        setTotal: String? = null,
        setHint: String? = null,
        supertype: CardSupertype = CardSupertype.POKEMON
    ) {
        uiState = uiState.copy(isSearching = true, errorMessage = null)

        try {
            val normalizedSetHint = setHint
                ?.let(SetCodeMapper::normalizeDecklistSetCode)
                ?.lowercase(Locale.ROOT)
                ?.takeIf { it.isNotBlank() }

            Timber.d("Ricerca scanner ITA: name=$name number=$number total=$setTotal set=$normalizedSetHint")
            val candidates = repository.searchItalianScannerCandidates(
                name = name,
                number = number,
                setTotal = setTotal,
                targetSetId = normalizedSetHint,
                context = appContext,
                limit = 6
            ).getOrDefault(emptyList())

            val viable = candidates.filterNot { it.id in recentlyAddedIds }
            if (viable.isEmpty()) {
                uiState = uiState.copy(
                    isSearching = false,
                    pendingCard = null,
                    candidateCards = emptyList(),
                    errorMessage = if (candidates.isEmpty()) "Nessuna carta trovata. Riprova." else null
                )
                activeSearchKey = ""
                return
            }

            // Preferenza soft sul supertype OCR (TRAINER/ENERGY): mai svuotare il pool.
            val pool = when (supertype) {
                CardSupertype.TRAINER -> viable.filter { it.supertype.equals("trainer", ignoreCase = true) }.ifEmpty { viable }
                CardSupertype.ENERGY -> viable.filter { it.supertype.equals("energy", ignoreCase = true) }.ifEmpty { viable }
                CardSupertype.POKEMON -> viable
            }

            val totalValue = setTotal?.toIntOrNull()
            val hasUsableName = !name.isNullOrBlank() && isUsableSearchName(name)
            val scored = pool.map { card ->
                val similarity = if (hasUsableName) {
                    maxOf(
                        computeNameSimilarity(name, card.name),
                        computeNameSimilarity(stripAccents(name), stripAccents(card.name))
                    )
                } else {
                    0.0
                }
                val printedTotal = card.set?.printedTotal?.takeIf { it > 0 }
                val totalMatches = totalValue != null && printedTotal != null &&
                    kotlin.math.abs(printedTotal - totalValue) <= SET_TOTAL_TOLERANCE
                ScannerCandidate(card = card, nameSimilarity = similarity, totalMatches = totalMatches)
            }.sortedWith(
                compareByDescending<ScannerCandidate> { it.totalMatches }
                    .thenByDescending { it.nameSimilarity }
            )

            val top = scored.first()
            val second = scored.getOrNull(1)
            // Auto-proposta SOLO con segnali coerenti:
            //  - candidato unico, oppure
            //  - totale set compatibile + (nome convincente o nessun rivale col totale giusto).
            // Mai auto-proporre un match "solo numero": in dubbio si mostra il picker.
            val autoSelect = when {
                scored.size == 1 -> top.totalMatches || (hasUsableName && top.nameSimilarity >= MIN_NAME_SIMILARITY)
                !top.totalMatches -> false
                !hasUsableName -> second?.totalMatches != true
                top.nameSimilarity < STRONG_NAME_SIMILARITY -> second?.totalMatches != true
                else -> second == null || !second.totalMatches ||
                    top.nameSimilarity - second.nameSimilarity >= NAME_SIMILARITY_MARGIN
            }

            uiState = if (autoSelect) {
                uiState.copy(
                    isSearching = false,
                    pendingCard = top.card,
                    candidateCards = emptyList(),
                    errorMessage = null
                )
            } else {
                uiState.copy(
                    isSearching = false,
                    pendingCard = null,
                    candidateCards = scored.take(MAX_AMBIGUOUS_CANDIDATES).map { it.card },
                    errorMessage = "Più risultati possibili. Seleziona la carta corretta."
                )
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
            .replace(NAME_INVALID_CHARS_REGEX, " ")
            .split(WHITESPACE_REGEX)
            .filter { token -> token.length >= 2 && token !in stopWords }
            .joinToString(" ")
            .trim()
    }

    private fun isUsableSearchName(name: String): Boolean {
        val normalized = normalizeNameForMatching(name)
        return normalized.length >= 3 && normalized.any { it.isLetter() }
    }

    /**
     * Strips Unicode combining diacritics (accents) from a string.
     * Used to enable cross-language name matching between ITA and ENG cards
     * where Pokémon names are identical but may carry accented chars.
     */
    private fun stripAccents(s: String?): String {
        if (s.isNullOrBlank()) return ""
        val nfd = java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFD)
        return COMBINING_MARKS_REGEX.replace(nfd, "")
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
        stableSetHint = ""
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
        private const val NO_NAME_STABILITY_THRESHOLD = 4
        private const val MAX_AMBIGUOUS_CANDIDATES = 3
        private const val MIN_NAME_SIMILARITY = 0.42
        private const val STRONG_NAME_SIMILARITY = 0.60
        private const val NAME_SIMILARITY_MARGIN = 0.18
        private const val SET_TOTAL_TOLERANCE = 2
        private const val SEARCH_MIN_INTERVAL_MS = 1500L
        private const val SEARCH_KEY_COOLDOWN_MS = 6000L

        // Regex pre-compilati: erano ricreati ad ogni chiamata di normalize(),
        // sprecando GC durante il live preview dello scanner.
        private val NAME_INVALID_CHARS_REGEX = Regex("""[^a-z0-9à-ÿ\s'-]""")
        private val WHITESPACE_REGEX = Regex("""\s+""")
        /** Unicode combining diacritical marks (NFD decomposition artifacts). */
        private val COMBINING_MARKS_REGEX = Regex("""\p{InCombiningDiacriticalMarks}""")
    }
}

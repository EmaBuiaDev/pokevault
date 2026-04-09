package com.example.pokevault.viewmodel

import android.app.Application
import android.graphics.Bitmap
import android.util.Log
import com.example.pokevault.BuildConfig
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.pokevault.data.firebase.FirestoreRepository
import com.example.pokevault.data.model.PokemonCard
import com.example.pokevault.data.remote.PokeTcgRepository
import com.example.pokevault.data.remote.TcgCard
import com.example.pokevault.ocr.CardOCRResult
import com.example.pokevault.ocr.OCRManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

data class ScannerUiState(
    val isSearching: Boolean = false,
    /** Carta trovata in attesa di conferma dall'utente */
    val pendingCard: TcgCard? = null,
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

    private val repository = PokeTcgRepository()
    private val firestoreRepository = FirestoreRepository()
    private var searchJob: Job? = null

    private val ocrManager = OCRManager(application)

    private val recentlyAddedIds = mutableSetOf<String>()

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
    private var stabilityCount = 0
    private val STABILITY_THRESHOLD = 3

    var uiState by mutableStateOf(ScannerUiState())
        private set

    init {
        viewModelScope.launch {
            try {
                ocrManager.initialize()
                uiState = uiState.copy(ocrEngineName = ocrManager.activeEngineName)
                if (BuildConfig.DEBUG) Log.i(TAG, "OCR inizializzato: ${ocrManager.activeEngineName}")
            } catch (e: Exception) {
                if (BuildConfig.DEBUG) Log.e(TAG, "Errore inizializzazione OCR: ${e.message}")
            }
        }
        // Precarica i set per poter identificare l'espansione dal totale carte
        viewModelScope.launch {
            try {
                repository.getSets(application)
                if (BuildConfig.DEBUG) Log.d(TAG, "Set precaricati per matching espansione")
            } catch (e: Exception) {
                if (BuildConfig.DEBUG) Log.w(TAG, "Errore precaricamento set: ${e.message}")
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
        if (uiState.pendingCard != null || uiState.lastAddedCard != null) return

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

        // Aggiorna contatore di stabilita
        if (number.isNotBlank() && number == stableNumber) {
            stabilityCount++
            // Aggiorna nome e totale col valore piu recente (possono migliorare frame dopo frame)
            if (name.isNotBlank()) stableName = name
            if (total.isNotBlank()) stableTotal = total
        } else {
            // Numero cambiato: reset stabilita
            stableNumber = number
            stableTotal = total
            stableName = name
            stabilityCount = 1
            // Cancella ricerca precedente solo se il numero e davvero cambiato
            searchJob?.cancel()
        }

        // Lancio ricerca quando il numero e stabile
        if (stabilityCount >= STABILITY_THRESHOLD && searchJob?.isActive != true) {
            activeSearchKey = searchKey
            searchJob = viewModelScope.launch {
                searchCard(
                    name = stableName.takeIf { it.isNotBlank() },
                    number = stableNumber.takeIf { it.isNotBlank() },
                    setTotal = stableTotal.takeIf { it.isNotBlank() }
                )
            }
        }
    }

    // ═══════════════════════════════════════════
    // OCR DA BITMAP
    // ═══════════════════════════════════════════

    fun analyzeCardImage(bitmap: Bitmap) {
        if (uiState.pendingCard != null) return
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
                    searchCard(result.cardName, result.cardNumber, result.setTotal)
                } else {
                    uiState = uiState.copy(
                        isSearching = false,
                        errorMessage = "Testo non riconosciuto. Riprova con una foto più nitida."
                    )
                }
            } catch (e: Exception) {
                if (BuildConfig.DEBUG) Log.e(TAG, "Errore analisi immagine: ${e.message}")
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
    private suspend fun searchCard(name: String?, number: String?, setTotal: String? = null) {
        uiState = uiState.copy(isSearching = true, errorMessage = null)

        try {
            var cards: List<TcgCard> = emptyList()

            // Strategia 1: numero/totale → usa searchCards che identifica il set
            if (number != null && setTotal != null) {
                val fullNumber = "$number/$setTotal"
                if (BuildConfig.DEBUG) Log.d(TAG, "Ricerca con numero completo: $fullNumber")
                repository.searchCards(fullNumber)
                    .onSuccess { results ->
                        cards = results
                        // Se abbiamo il nome, filtra per match migliore
                        if (name != null && results.size > 1) {
                            val nameFiltered = filterByName(results, name)
                            if (nameFiltered.isNotEmpty()) cards = nameFiltered
                        }
                    }
            }

            // Strategia 2: nome + numero
            if (cards.isEmpty() && name != null && number != null) {
                if (BuildConfig.DEBUG) Log.d(TAG, "Ricerca con nome+numero: $name #$number")
                repository.searchByNameAndNumber(name, number)
                    .onSuccess { cards = it }
            }

            // Strategia 3: solo nome
            if (cards.isEmpty() && name != null && name.length >= 3) {
                if (BuildConfig.DEBUG) Log.d(TAG, "Ricerca con solo nome: $name")
                repository.searchCardsFuzzy(name)
                    .onSuccess { results ->
                        // Se abbiamo il numero, filtra
                        if (number != null) {
                            val filtered = results.filter { it.number == number }
                            cards = filtered.ifEmpty { results }
                        } else {
                            cards = results
                        }
                    }
            }

            // Strategia 4: solo numero (ultimo fallback)
            if (cards.isEmpty() && number != null) {
                if (BuildConfig.DEBUG) Log.d(TAG, "Ricerca con solo numero: $number")
                repository.searchCards(number)
                    .onSuccess { cards = it }
            }

            val bestMatch = cards.firstOrNull()
            if (bestMatch != null && bestMatch.id !in recentlyAddedIds) {
                uiState = uiState.copy(
                    isSearching = false,
                    pendingCard = bestMatch,
                    errorMessage = null
                )
            } else if (bestMatch != null) {
                // Carta gia scartata/aggiunta in questa sessione
                uiState = uiState.copy(isSearching = false)
            } else {
                uiState = uiState.copy(
                    isSearching = false,
                    errorMessage = "Nessuna carta trovata. Riprova."
                )
                activeSearchKey = ""
            }
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.w(TAG, "Search failed: ${e.message}")
            uiState = uiState.copy(
                isSearching = false,
                errorMessage = "Errore ricerca: ${e.message}"
            )
            activeSearchKey = ""
        }
    }

    /**
     * Filtra risultati per nome: match esatto o parziale (prima parola).
     */
    private fun filterByName(cards: List<TcgCard>, name: String): List<TcgCard> {
        val cleanName = name.lowercase().trim()
        val firstName = cleanName.split(" ").firstOrNull() ?: cleanName

        // Match esatto
        val exact = cards.filter { it.name.equals(name, ignoreCase = true) }
        if (exact.isNotEmpty()) return exact

        // Match con la prima parola
        val partial = cards.filter {
            it.name.lowercase().startsWith(firstName) ||
            it.name.lowercase().contains(cleanName)
        }
        return partial
    }

    // ═══════════════════════════════════════════
    // CONFERMA / SCARTA
    // ═══════════════════════════════════════════

    fun confirmAdd() {
        val card = uiState.pendingCard ?: return
        uiState = uiState.copy(pendingCard = null, isSearching = true)
        viewModelScope.launch { addToFirestore(card) }
    }

    fun dismissCard() {
        val card = uiState.pendingCard
        uiState = uiState.copy(
            pendingCard = null,
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
        val price = tcgCard.tcgplayer?.prices?.values?.firstOrNull()?.let { p ->
            p.market ?: p.mid ?: p.low ?: 0.0
        } ?: tcgCard.cardmarket?.prices?.lowPrice
          ?: tcgCard.cardmarket?.prices?.averageSellPrice ?: 0.0

        val pokemonCard = PokemonCard(
            name = tcgCard.name,
            imageUrl = tcgCard.images.large.ifBlank { tcgCard.images.small },
            set = tcgCard.set?.name ?: "",
            rarity = tcgCard.rarity ?: "",
            type = tcgCard.types?.firstOrNull() ?: tcgCard.supertype,
            hp = tcgCard.hp?.toIntOrNull() ?: 0,
            estimatedValue = price,
            quantity = 1,
            condition = "Near Mint",
            apiCardId = tcgCard.id,
            cardNumber = tcgCard.number
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
        stabilityCount = 0
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
    }
}

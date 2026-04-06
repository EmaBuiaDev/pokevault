package com.example.pokevault.viewmodel

import android.app.Application
import android.graphics.Bitmap
import android.util.Log
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
    private var lastSearchKey = ""

    var uiState by mutableStateOf(ScannerUiState())
        private set

    init {
        viewModelScope.launch {
            try {
                ocrManager.initialize()
                uiState = uiState.copy(ocrEngineName = ocrManager.activeEngineName)
                Log.i(TAG, "OCR inizializzato: ${ocrManager.activeEngineName}")
            } catch (e: Exception) {
                Log.e(TAG, "Errore inizializzazione OCR: ${e.message}")
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
     * Se una carta e gia in attesa di conferma, ignora nuovi frame.
     */
    fun onTextDetected(rawText: String) {
        if (rawText.isBlank()) return
        // Non analizzare se c'e gia una carta in attesa o appena aggiunta
        if (uiState.pendingCard != null || uiState.lastAddedCard != null) return

        val ocrResult = ocrManager.extractCardFields(rawText)
        if (!ocrResult.isSearchable()) return

        val searchKey = ocrResult.searchKey()
        if (searchKey == lastSearchKey) return

        uiState = uiState.copy(
            detectedName = ocrResult.cardName ?: "",
            detectedNumber = ocrResult.cardNumber ?: "",
            lastOCRResult = ocrResult
        )

        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(800) // debounce ridotto per riconoscimento piu veloce
            lastSearchKey = searchKey
            searchCard(ocrResult.cardName, ocrResult.cardNumber)
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
                    searchCard(result.cardName, result.cardNumber)
                } else {
                    uiState = uiState.copy(
                        isSearching = false,
                        errorMessage = "Testo non riconosciuto. Riprova con una foto più nitida."
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Errore analisi immagine: ${e.message}")
                uiState = uiState.copy(
                    isSearching = false,
                    errorMessage = "Errore OCR: ${e.message}"
                )
            }
        }
    }

    // ═══════════════════════════════════════════
    // RICERCA (senza aggiunta automatica)
    // ═══════════════════════════════════════════

    /**
     * Cerca la carta sull'API e la mostra all'utente per conferma.
     * NON aggiunge automaticamente.
     */
    private suspend fun searchCard(name: String?, number: String?) {
        uiState = uiState.copy(isSearching = true, errorMessage = null)

        repository.searchByNameAndNumber(name, number)
            .onSuccess { cards ->
                val bestMatch = cards.firstOrNull()
                if (bestMatch != null) {
                    // Mostra la carta trovata, in attesa di conferma
                    uiState = uiState.copy(
                        isSearching = false,
                        pendingCard = bestMatch,
                        errorMessage = null
                    )
                } else {
                    uiState = uiState.copy(
                        isSearching = false,
                        errorMessage = "Nessuna carta trovata. Riprova."
                    )
                    // Reset per permettere nuova scansione
                    lastSearchKey = ""
                }
            }
            .onFailure { error ->
                Log.w(TAG, "Search failed: ${error.message}")
                uiState = uiState.copy(
                    isSearching = false,
                    errorMessage = "Errore ricerca: ${error.message}"
                )
                lastSearchKey = ""
            }
    }

    // ═══════════════════════════════════════════
    // CONFERMA / SCARTA
    // ═══════════════════════════════════════════

    /** L'utente conferma: aggiungi la carta alla collezione */
    fun confirmAdd() {
        val card = uiState.pendingCard ?: return
        uiState = uiState.copy(pendingCard = null, isSearching = true)

        viewModelScope.launch {
            addToFirestore(card)
        }
    }

    /** L'utente scarta: riprendi la scansione */
    fun dismissCard() {
        val card = uiState.pendingCard
        uiState = uiState.copy(
            pendingCard = null,
            detectedName = "",
            detectedNumber = "",
            errorMessage = null
        )
        // Reset search key per permettere di riscansionare
        lastSearchKey = ""
        // Aggiungi all'elenco degli scartati per evitare di riproporre la stessa carta
        if (card != null) {
            recentlyAddedIds.add(card.id)
        }
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
                // Reset dopo 2.5 secondi per riprendere la scansione
                viewModelScope.launch {
                    delay(2500)
                    if (uiState.lastAddedCard?.id == tcgCard.id) {
                        uiState = uiState.copy(
                            lastAddedCard = null,
                            detectedName = "",
                            detectedNumber = ""
                        )
                        lastSearchKey = ""
                    }
                }
            }
            .onFailure { error ->
                recentlyAddedIds.remove(tcgCard.id)
                uiState = uiState.copy(
                    isSearching = false,
                    errorMessage = "Errore salvataggio: ${error.message}"
                )
                lastSearchKey = ""
            }
    }

    fun toggleFlash() {
        uiState = uiState.copy(flashEnabled = !uiState.flashEnabled)
    }

    fun resetScanner() {
        lastSearchKey = ""
        recentlyAddedIds.clear()
        searchJob?.cancel()
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

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
import com.example.pokevault.ocr.CardFieldParser
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

data class ScannerUiState(
    val isSearching: Boolean = false,
    val lastAddedCard: TcgCard? = null,
    val addedCount: Int = 0,
    val errorMessage: String? = null,
    val flashEnabled: Boolean = false,
    val detectedName: String = "",
    val detectedNumber: String = "",
    /** Ultimo risultato OCR completo (per debug/display) */
    val lastOCRResult: CardOCRResult? = null,
    /** Nome dell'engine OCR attivo */
    val ocrEngineName: String = ""
)

class ScannerViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = PokeTcgRepository()
    private val firestoreRepository = FirestoreRepository()
    private var searchJob: Job? = null

    /** OCR Manager con pipeline completa per carte Pokemon */
    private val ocrManager = OCRManager(application)

    // Evita di aggiungere la stessa carta piu volte nella stessa sessione
    private val recentlyAddedIds = mutableSetOf<String>()
    private var lastSearchKey = ""

    var uiState by mutableStateOf(ScannerUiState())
        private set

    init {
        // Inizializza OCR pipeline in background
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
    // OCR DA CAMERA FRAME (testo gia riconosciuto da ML Kit in ScannerScreen)
    // ═══════════════════════════════════════════

    /**
     * Chiamata dalla camera ad ogni frame con testo OCR grezzo.
     * Usa il CardFieldParser per estrarre i campi strutturati.
     */
    fun onTextDetected(rawText: String) {
        if (rawText.isBlank()) return

        // Usa la nuova pipeline di parsing Pokemon-specifico
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
            delay(1200) // debounce per stabilizzare OCR
            lastSearchKey = searchKey
            searchAndAutoAdd(ocrResult.cardName, ocrResult.cardNumber)
        }
    }

    // ═══════════════════════════════════════════
    // OCR DA BITMAP (analisi completa con preprocessing)
    // ═══════════════════════════════════════════

    /**
     * Analizza un'immagine completa di una carta Pokemon.
     * Esegue la pipeline OCR completa: preprocessing + detection + recognition + parsing.
     *
     * Usare questa funzione per:
     * - Foto scattate dalla galleria
     * - Foto ad alta risoluzione
     * - Quando serve la massima accuratezza
     */
    fun analyzeCardImage(bitmap: Bitmap) {
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
                    searchAndAutoAdd(result.cardName, result.cardNumber)
                } else {
                    uiState = uiState.copy(
                        isSearching = false,
                        errorMessage = "Testo non riconosciuto. Riprova con una foto piu nitida."
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
    // RICERCA E AUTO-ADD
    // ═══════════════════════════════════════════

    private suspend fun searchAndAutoAdd(name: String?, number: String?) {
        uiState = uiState.copy(isSearching = true, errorMessage = null)

        repository.searchByNameAndNumber(name, number)
            .onSuccess { cards ->
                val bestMatch = cards.firstOrNull()
                if (bestMatch != null && bestMatch.id !in recentlyAddedIds) {
                    // Auto-add alla collezione
                    recentlyAddedIds.add(bestMatch.id)
                    addToFirestore(bestMatch)
                } else {
                    uiState = uiState.copy(isSearching = false)
                }
            }
            .onFailure { error ->
                Log.w(TAG, "Search failed: ${error.message}")
                uiState = uiState.copy(
                    isSearching = false,
                    errorMessage = "Errore ricerca: ${error.message}"
                )
            }
    }

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
                uiState = uiState.copy(
                    isSearching = false,
                    lastAddedCard = tcgCard,
                    addedCount = uiState.addedCount + 1,
                    errorMessage = null
                )
                // Reset dopo 3 secondi per essere pronti alla prossima carta
                viewModelScope.launch {
                    delay(3000)
                    if (uiState.lastAddedCard?.id == tcgCard.id) {
                        uiState = uiState.copy(lastAddedCard = null)
                        lastSearchKey = "" // permetti nuove scansioni
                    }
                }
            }
            .onFailure { error ->
                recentlyAddedIds.remove(tcgCard.id) // permetti retry
                uiState = uiState.copy(
                    isSearching = false,
                    errorMessage = "Errore salvataggio: ${error.message}"
                )
            }
    }

    /**
     * Ricerca manuale: cerca e auto-aggiunge.
     */
    fun searchManually(query: String) {
        if (query.isBlank()) return
        searchJob?.cancel()
        lastSearchKey = "manual_$query"
        uiState = uiState.copy(detectedName = query)
        searchJob = viewModelScope.launch {
            searchAndAutoAdd(query, null)
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

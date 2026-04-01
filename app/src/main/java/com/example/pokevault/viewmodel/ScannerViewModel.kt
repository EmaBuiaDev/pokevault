package com.example.pokevault.viewmodel

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.pokevault.data.remote.PokeTcgRepository
import com.example.pokevault.data.remote.TcgCard
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

data class ScannerUiState(
    val isScanning: Boolean = false,
    val detectedText: String = "",
    val bestGuessName: String = "",
    val searchResults: List<TcgCard> = emptyList(),
    val isSearching: Boolean = false,
    val selectedCard: TcgCard? = null,
    val errorMessage: String? = null,
    val flashEnabled: Boolean = false
)

class ScannerViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = PokeTcgRepository()
    private var searchJob: Job? = null

    var uiState by mutableStateOf(ScannerUiState())
        private set

    fun onTextDetected(rawText: String) {
        if (rawText.isBlank() || rawText == uiState.detectedText) return

        // Estrai il nome più probabile della carta dal testo riconosciuto
        val cardName = extractCardName(rawText)
        if (cardName.isBlank() || cardName == uiState.bestGuessName) return

        uiState = uiState.copy(
            detectedText = rawText,
            bestGuessName = cardName
        )

        // Cerca su API con debounce
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(800)
            searchCard(cardName)
        }
    }

    fun searchManually(query: String) {
        if (query.isBlank()) return
        uiState = uiState.copy(bestGuessName = query)
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            searchCard(query)
        }
    }

    private suspend fun searchCard(name: String) {
        uiState = uiState.copy(isSearching = true, errorMessage = null)
        repository.searchCards(name)
            .onSuccess { cards ->
                uiState = uiState.copy(
                    searchResults = cards,
                    isSearching = false,
                    selectedCard = cards.firstOrNull()
                )
            }
            .onFailure { error ->
                uiState = uiState.copy(
                    isSearching = false,
                    errorMessage = "Errore ricerca: ${error.message}"
                )
            }
    }

    fun selectCard(card: TcgCard) {
        uiState = uiState.copy(selectedCard = card)
    }

    fun toggleFlash() {
        uiState = uiState.copy(flashEnabled = !uiState.flashEnabled)
    }

    fun clearResults() {
        uiState = uiState.copy(
            detectedText = "",
            bestGuessName = "",
            searchResults = emptyList(),
            selectedCard = null,
            errorMessage = null
        )
    }

    fun setScanning(active: Boolean) {
        uiState = uiState.copy(isScanning = active)
    }

    /**
     * Estrae il nome della carta dal testo OCR.
     * Il nome è tipicamente nella parte superiore della carta,
     * la prima riga di testo significativa (esclude numeri HP, simboli, ecc.)
     */
    private fun extractCardName(rawText: String): String {
        val lines = rawText.lines()
            .map { it.trim() }
            .filter { it.length >= 3 }
            .filter { line ->
                // Filtra linee che sono solo numeri, HP, simboli, ecc.
                !line.matches(Regex("^\\d+$")) &&
                !line.matches(Regex("^HP\\s*\\d+$", RegexOption.IGNORE_CASE)) &&
                !line.matches(Regex("^\\d+\\s*HP$", RegexOption.IGNORE_CASE)) &&
                !line.matches(Regex("^(BASIC|STAGE\\s*\\d|MEGA|VSTAR|VMAX|V|EX|GX)$", RegexOption.IGNORE_CASE)) &&
                !line.contains("Weakness", ignoreCase = true) &&
                !line.contains("Resistance", ignoreCase = true) &&
                !line.contains("Retreat", ignoreCase = true) &&
                !line.contains("Illustrator", ignoreCase = true) &&
                !line.contains("©", ignoreCase = true)
            }

        // Il nome della carta è solitamente nella prima riga utile
        val name = lines.firstOrNull() ?: ""

        // Pulisci il nome: rimuovi "ex", "V", numeri HP attaccati, ecc. alla fine
        return name
            .replace(Regex("\\s+HP\\s*\\d+.*$"), "")
            .replace(Regex("\\s+\\d+$"), "")
            .trim()
            .take(40) // Limita lunghezza
    }
}

package com.example.pokevault.viewmodel

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.pokevault.data.firebase.FirestoreRepository
import com.example.pokevault.data.model.PokemonCard
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
    val successMessage: String? = null,
    val flashEnabled: Boolean = false,
    val isAdding: Boolean = false
)

class ScannerViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = PokeTcgRepository()
    private val firestoreRepository = FirestoreRepository()
    private var searchJob: Job? = null
    private var lastSearchedName: String = ""

    var uiState by mutableStateOf(ScannerUiState())
        private set

    fun onTextDetected(rawText: String) {
        if (rawText.isBlank()) return
        // Non riprocessare se il testo grezzo è identico
        if (rawText == uiState.detectedText) return

        val cardName = extractCardName(rawText)
        if (cardName.isBlank()) return
        // Evita ricerche duplicate per lo stesso nome estratto
        if (cardName == lastSearchedName && uiState.searchResults.isNotEmpty()) return

        uiState = uiState.copy(
            detectedText = rawText,
            bestGuessName = cardName
        )

        // Cerca su API con debounce
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(1200) // debounce più lungo per stabilizzare OCR
            lastSearchedName = cardName
            searchCard(cardName)
        }
    }

    fun searchManually(query: String) {
        if (query.isBlank()) return
        uiState = uiState.copy(bestGuessName = query)
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            lastSearchedName = query
            searchCard(query)
        }
    }

    private suspend fun searchCard(name: String) {
        uiState = uiState.copy(isSearching = true, errorMessage = null)

        // Prima prova: ricerca esatta con wildcard
        repository.searchCards(name)
            .onSuccess { cards ->
                if (cards.isNotEmpty()) {
                    uiState = uiState.copy(
                        searchResults = cards,
                        isSearching = false,
                        selectedCard = cards.firstOrNull()
                    )
                } else {
                    // Fallback: prova con solo la prima parola (spesso il nome del Pokémon)
                    val firstName = name.split(" ").firstOrNull()?.trim() ?: ""
                    if (firstName.length >= 3 && firstName != name) {
                        retrySearch(firstName)
                    } else {
                        uiState = uiState.copy(
                            searchResults = emptyList(),
                            isSearching = false,
                            errorMessage = "Nessun risultato per \"$name\""
                        )
                    }
                }
            }
            .onFailure { error ->
                uiState = uiState.copy(
                    isSearching = false,
                    errorMessage = "Errore ricerca: ${error.message}"
                )
            }
    }

    private suspend fun retrySearch(name: String) {
        repository.searchCards(name)
            .onSuccess { cards ->
                uiState = uiState.copy(
                    searchResults = cards,
                    isSearching = false,
                    selectedCard = cards.firstOrNull(),
                    bestGuessName = name,
                    errorMessage = if (cards.isEmpty()) "Nessun risultato per \"$name\"" else null
                )
            }
            .onFailure { error ->
                uiState = uiState.copy(
                    isSearching = false,
                    errorMessage = "Errore ricerca: ${error.message}"
                )
            }
    }

    /**
     * Aggiunge direttamente la carta selezionata alla collezione Firestore.
     */
    fun addCardToCollection(tcgCard: TcgCard) {
        if (uiState.isAdding) return
        uiState = uiState.copy(isAdding = true, errorMessage = null, successMessage = null)

        viewModelScope.launch {
            val price = tcgCard.tcgplayer?.prices?.values?.firstOrNull()?.let { p ->
                p.market ?: p.mid ?: p.low ?: 0.0
            } ?: tcgCard.cardmarket?.prices?.averageSellPrice ?: 0.0

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
                        isAdding = false,
                        successMessage = "${tcgCard.name} aggiunta alla collezione!"
                    )
                }
                .onFailure { error ->
                    uiState = uiState.copy(
                        isAdding = false,
                        errorMessage = "Errore: ${error.message}"
                    )
                }
        }
    }

    fun selectCard(card: TcgCard) {
        uiState = uiState.copy(selectedCard = card)
    }

    fun toggleFlash() {
        uiState = uiState.copy(flashEnabled = !uiState.flashEnabled)
    }

    fun clearResults() {
        lastSearchedName = ""
        uiState = uiState.copy(
            detectedText = "",
            bestGuessName = "",
            searchResults = emptyList(),
            selectedCard = null,
            errorMessage = null,
            successMessage = null
        )
    }

    fun clearMessages() {
        uiState = uiState.copy(errorMessage = null, successMessage = null)
    }

    /**
     * Estrae il nome della carta dal testo OCR.
     * Strategia: il nome è la prima riga significativa (parte superiore della carta).
     * Mantiene suffissi importanti come "ex", "V", "VMAX", "VSTAR", "GX", "EX".
     */
    private fun extractCardName(rawText: String): String {
        val lines = rawText.lines()
            .map { it.trim() }
            .filter { it.length >= 2 }

        // Filtra linee che chiaramente NON sono il nome
        val candidates = lines.filter { line ->
            !line.matches(Regex("^\\d+[/\\\\]?\\d*$")) &&                     // "123" o "123/456"
            !line.matches(Regex("^HP\\s*\\d+$", RegexOption.IGNORE_CASE)) &&   // "HP 120"
            !line.matches(Regex("^\\d+\\s*HP$", RegexOption.IGNORE_CASE)) &&   // "120 HP"
            !line.matches(Regex("^(BASIC|STAGE\\s*\\d|MEGA)$", RegexOption.IGNORE_CASE)) &&
            !line.contains("Weakness", ignoreCase = true) &&
            !line.contains("Resistance", ignoreCase = true) &&
            !line.contains("Retreat", ignoreCase = true) &&
            !line.contains("Illustrator", ignoreCase = true) &&
            !line.contains("©", ignoreCase = true) &&
            !line.contains("Pokémon", ignoreCase = true) &&
            !line.contains("Pokemon", ignoreCase = true) &&
            !line.contains("TM", ignoreCase = false) &&
            !line.matches(Regex("^[^a-zA-Z]*$")) // Solo simboli/numeri
        }

        val name = candidates.firstOrNull() ?: return ""

        // Pulisci: rimuovi "HP 120" attaccato alla fine, numeri trailing
        return name
            .replace(Regex("\\s+HP\\s*\\d+.*$", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\s+\\d+/\\d+$"), "")     // "123/456" trailing
            .replace(Regex("^(BASIC|Stage\\s*\\d)\\s+", RegexOption.IGNORE_CASE), "") // "BASIC Pikachu" → "Pikachu"
            .trim()
            .take(50)
    }
}

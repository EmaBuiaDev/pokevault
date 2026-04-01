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
    val rawOcrText: String = "",
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

    /**
     * Chiamata dalla camera ad ogni frame con testo riconosciuto.
     * Estrae il nome, debounce, e cerca automaticamente.
     */
    fun onTextDetected(rawText: String) {
        if (rawText.isBlank()) return

        val cardName = extractCardName(rawText)
        if (cardName.isBlank() || cardName.length < 3) return

        // Non ricercare se è lo stesso nome già cercato con risultati
        if (cardName == lastSearchedName && uiState.searchResults.isNotEmpty()) return
        // Non ricercare se è lo stesso nome senza risultati (evita loop)
        if (cardName == lastSearchedName) return

        uiState = uiState.copy(
            rawOcrText = rawText,
            bestGuessName = cardName
        )

        // Debounce: aspetta che il testo si stabilizzi
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(1500)
            lastSearchedName = cardName
            searchFuzzy(cardName)
        }
    }

    fun searchManually(query: String) {
        if (query.isBlank()) return
        searchJob?.cancel()
        lastSearchedName = query
        uiState = uiState.copy(bestGuessName = query)
        searchJob = viewModelScope.launch {
            searchFuzzy(query)
        }
    }

    private suspend fun searchFuzzy(name: String) {
        uiState = uiState.copy(isSearching = true, errorMessage = null)

        repository.searchCardsFuzzy(name)
            .onSuccess { cards ->
                uiState = uiState.copy(
                    searchResults = cards,
                    isSearching = false,
                    selectedCard = cards.firstOrNull(),
                    errorMessage = if (cards.isEmpty()) "Nessun risultato per \"$name\"" else null
                )
            }
            .onFailure { error ->
                uiState = uiState.copy(
                    isSearching = false,
                    errorMessage = "Errore: ${error.message}"
                )
            }
    }

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
                        successMessage = "${tcgCard.name} aggiunta!"
                    )
                }
                .onFailure { error ->
                    uiState = uiState.copy(
                        isAdding = false,
                        errorMessage = "Errore salvataggio: ${error.message}"
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
        searchJob?.cancel()
        uiState = ScannerUiState(flashEnabled = uiState.flashEnabled)
    }

    fun clearMessages() {
        uiState = uiState.copy(errorMessage = null, successMessage = null)
    }

    /**
     * Estrae il nome della carta dal testo OCR.
     * Il nome è nella parte superiore: prima riga con lettere, non keyword.
     */
    private fun extractCardName(rawText: String): String {
        val lines = rawText.lines()
            .map { it.trim() }
            .filter { it.length >= 3 }

        val candidates = lines.filter { line ->
            val lower = line.lowercase()
            // Deve contenere almeno una lettera
            line.any { it.isLetter() } &&
            // Escludi linee chiaramente non-nome
            !lower.matches(Regex("^\\d+\\s*hp$")) &&
            !lower.matches(Regex("^hp\\s*\\d+$")) &&
            !lower.matches(Regex("^\\d+[/\\\\]\\d+$")) &&
            !lower.startsWith("weakness") &&
            !lower.startsWith("resistance") &&
            !lower.startsWith("retreat") &&
            !lower.contains("illustrator") &&
            !lower.contains("©") &&
            !lower.contains("®") &&
            !lower.contains("pokémon") &&
            !lower.contains("pokemon") &&
            !lower.contains("damage") &&
            !lower.contains("attach") &&
            !lower.contains("opponent") &&
            !lower.contains("energy") &&
            !lower.contains("trainer") &&
            !lower.contains("supporter") &&
            !lower.contains("coin") &&
            !lower.contains("discard") &&
            !lower.contains("shuffle") &&
            !lower.contains("your") &&
            !lower.contains("this") &&
            !lower.matches(Regex("^(basic|stage \\d|mega)$"))
        }

        val name = candidates.firstOrNull() ?: return ""

        // Pulisci il nome
        return name
            .replace(Regex("\\s+HP\\s*\\d+.*$", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\s+\\d+/\\d+$"), "")
            .replace(Regex("^(BASIC|Stage\\s*\\d)\\s+", RegexOption.IGNORE_CASE), "")
            .replace(Regex("[^a-zA-ZÀ-ÿ\\s'-]"), "")
            .replace(Regex("\\s+"), " ")
            .trim()
            .take(40)
    }
}

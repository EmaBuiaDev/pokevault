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
import kotlinx.coroutines.launch

data class ScannerUiState(
    val rawOcrText: String = "",
    val bestGuessName: String = "",
    val searchResults: List<TcgCard> = emptyList(),
    val isSearching: Boolean = false,
    val isCapturing: Boolean = false,
    val selectedCard: TcgCard? = null,
    val errorMessage: String? = null,
    val successMessage: String? = null,
    val flashEnabled: Boolean = false,
    val isAdding: Boolean = false
)

class ScannerViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = PokeTcgRepository()
    private val firestoreRepository = FirestoreRepository()

    var uiState by mutableStateOf(ScannerUiState())
        private set

    // Buffer per il testo OCR dell'ultimo frame (aggiornato dalla camera)
    private var latestOcrText: String = ""

    /**
     * Chiamata continuamente dalla camera. Salva solo il testo più recente.
     * NON lancia ricerche automatiche.
     */
    fun onTextDetected(rawText: String) {
        if (rawText.isNotBlank()) {
            latestOcrText = rawText
        }
    }

    /**
     * Bottone "Scansiona": prende il testo OCR corrente, estrae il nome, cerca.
     */
    fun captureAndSearch() {
        val text = latestOcrText
        if (text.isBlank()) {
            uiState = uiState.copy(errorMessage = "Nessun testo rilevato. Avvicina la fotocamera alla carta.")
            return
        }

        uiState = uiState.copy(isCapturing = true)

        val cardName = extractCardName(text)
        uiState = uiState.copy(
            rawOcrText = text,
            bestGuessName = cardName,
            isCapturing = false
        )

        if (cardName.isBlank()) {
            uiState = uiState.copy(
                errorMessage = "Non riesco a estrarre il nome. Prova la ricerca manuale."
            )
            return
        }

        searchFuzzy(cardName)
    }

    fun searchManually(query: String) {
        if (query.isBlank()) return
        uiState = uiState.copy(bestGuessName = query)
        searchFuzzy(query)
    }

    private fun searchFuzzy(name: String) {
        uiState = uiState.copy(isSearching = true, errorMessage = null)

        viewModelScope.launch {
            repository.searchCardsFuzzy(name)
                .onSuccess { cards ->
                    uiState = uiState.copy(
                        searchResults = cards,
                        isSearching = false,
                        selectedCard = cards.firstOrNull(),
                        errorMessage = if (cards.isEmpty()) "Nessun risultato per \"$name\". Prova la ricerca manuale." else null
                    )
                }
                .onFailure { error ->
                    uiState = uiState.copy(
                        isSearching = false,
                        errorMessage = "Errore di rete: ${error.message}"
                    )
                }
        }
    }

    /**
     * Aggiunge la carta selezionata direttamente alla collezione Firestore.
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
        latestOcrText = ""
        uiState = ScannerUiState(flashEnabled = uiState.flashEnabled)
    }

    fun clearMessages() {
        uiState = uiState.copy(errorMessage = null, successMessage = null)
    }

    /**
     * Estrae il nome della carta dal testo OCR.
     * Le carte Pokémon hanno il nome in alto, spesso la riga più lunga
     * con solo lettere (e spazi). Prova più strategie.
     */
    private fun extractCardName(rawText: String): String {
        val lines = rawText.lines()
            .map { it.trim() }
            .filter { it.length >= 2 }

        // Rimuovi linee che sono chiaramente non-nome
        val candidates = lines.filter { line ->
            !line.matches(Regex("^[\\d\\s/\\\\]+$")) &&                          // solo numeri
            !line.matches(Regex("^HP\\s*\\d+$", RegexOption.IGNORE_CASE)) &&
            !line.matches(Regex("^\\d+\\s*HP$", RegexOption.IGNORE_CASE)) &&
            !line.contains("Weakness", ignoreCase = true) &&
            !line.contains("Resistance", ignoreCase = true) &&
            !line.contains("Retreat", ignoreCase = true) &&
            !line.contains("Illustrator", ignoreCase = true) &&
            !line.contains("©") &&
            !line.contains("®") &&
            !line.contains("Pokémon", ignoreCase = true) &&
            !line.contains("Pokemon", ignoreCase = true) &&
            !line.contains("damage", ignoreCase = true) &&
            !line.contains("opponent", ignoreCase = true) &&
            !line.contains("energy", ignoreCase = true) &&
            !line.contains("coin", ignoreCase = true) &&
            !line.matches(Regex("^[^a-zA-ZÀ-ÿ]*$"))  // no lettere
        }

        // Strategia 1: Prima riga candidata (solitamente il nome è in alto)
        val firstCandidate = candidates.firstOrNull() ?: return ""

        // Pulisci
        return firstCandidate
            .replace(Regex("\\s+HP\\s*\\d+.*$", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\s+\\d+/\\d+$"), "")
            .replace(Regex("^(BASIC|Stage\\s*\\d)\\s+", RegexOption.IGNORE_CASE), "")
            .replace(Regex("[^a-zA-ZÀ-ÿ\\s'-]"), "") // rimuovi caratteri non-nome
            .trim()
            .take(40)
    }
}

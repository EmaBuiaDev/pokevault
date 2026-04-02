package com.example.pokevault.viewmodel

import android.app.Application
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
    val detectedNumber: String = ""
)

class ScannerViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = PokeTcgRepository()
    private val firestoreRepository = FirestoreRepository()
    private var searchJob: Job? = null

    // Evita di aggiungere la stessa carta più volte nella stessa sessione
    private val recentlyAddedIds = mutableSetOf<String>()
    private var lastSearchKey = ""

    var uiState by mutableStateOf(ScannerUiState())
        private set

    /**
     * Chiamata dalla camera ad ogni frame con testo OCR.
     * Estrae numero carta + nome, cerca e aggiunge automaticamente.
     */
    fun onTextDetected(rawText: String) {
        if (rawText.isBlank()) return

        val number = extractCardNumber(rawText)
        val name = extractCardName(rawText)

        // Serve almeno un nome di 3+ caratteri o un numero
        if ((name.isNullOrBlank() || name.length < 3) && number == null) return

        val searchKey = "${name ?: ""}_${number ?: ""}"
        if (searchKey == lastSearchKey) return

        uiState = uiState.copy(
            detectedName = name ?: "",
            detectedNumber = number ?: ""
        )

        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(1200) // debounce per stabilizzare OCR
            lastSearchKey = searchKey
            searchAndAutoAdd(name, number)
        }
    }

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
                Log.w("ScannerVM", "Search failed: ${error.message}")
                uiState = uiState.copy(
                    isSearching = false,
                    errorMessage = "Errore ricerca: ${error.message}"
                )
            }
    }

    private suspend fun addToFirestore(tcgCard: TcgCard) {
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
            addedCount = uiState.addedCount
        )
    }

    fun clearError() {
        uiState = uiState.copy(errorMessage = null)
    }

    /**
     * Estrae il numero della carta dal testo OCR.
     * Formato: "025/198", "25/198", "025 / 198", ecc.
     * Questo è il dato OCR PIÙ AFFIDABILE su una carta Pokémon.
     */
    private fun extractCardNumber(rawText: String): String? {
        // Pattern: 1-3 cifre / 1-3 cifre (con spazi opzionali)
        val match = Regex("(\\d{1,3})\\s*/\\s*(\\d{1,3})").find(rawText)
        if (match != null) {
            val num = match.groupValues[1].trimStart('0')
            return if (num.isNotBlank()) num else "0"
        }
        return null
    }

    /**
     * Estrae il nome della carta dal testo OCR.
     * Il nome è nella parte superiore della carta.
     */
    private fun extractCardName(rawText: String): String? {
        val lines = rawText.lines()
            .map { it.trim() }
            .filter { it.length >= 3 }

        val candidates = lines.filter { line ->
            val lower = line.lowercase()
            line.any { it.isLetter() } &&
            !lower.matches(Regex("^\\d+\\s*hp$")) &&
            !lower.matches(Regex("^hp\\s*\\d+$")) &&
            !lower.matches(Regex("^\\d+\\s*/\\s*\\d+$")) &&
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
            !lower.contains("your ") &&
            !lower.contains("this ") &&
            !lower.contains("each ") &&
            !lower.contains("does ") &&
            !lower.matches(Regex("^(basic|stage \\d|mega)$"))
        }

        val name = candidates.firstOrNull() ?: return null

        return name
            .replace(Regex("\\s+HP\\s*\\d+.*$", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\s+\\d+/\\d+$"), "")
            .replace(Regex("^(BASIC|Stage\\s*\\d)\\s+", RegexOption.IGNORE_CASE), "")
            .replace(Regex("[^a-zA-ZÀ-ÿ\\s'-]"), "")
            .replace(Regex("\\s+"), " ")
            .trim()
            .take(40)
            .takeIf { it.length >= 3 }
    }
}

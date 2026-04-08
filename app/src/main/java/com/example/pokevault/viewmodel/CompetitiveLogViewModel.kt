package com.example.pokevault.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.pokevault.data.firebase.FirestoreRepository
import com.example.pokevault.data.model.MatchLog
import com.google.firebase.Timestamp
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.Calendar

class CompetitiveLogViewModel : ViewModel() {
    private val repository = FirestoreRepository()

    var matchLogs by mutableStateOf<List<MatchLog>>(emptyList())
        private set

    var isLoading by mutableStateOf(false)
        private set

    var isSaving by mutableStateOf(false)
        private set

    // Form state
    var editingMatchId by mutableStateOf<String?>(null)
    var matchLocation by mutableStateOf("")
    var matchDate by mutableStateOf<Timestamp>(Timestamp.now())
    var matchFormat by mutableStateOf("")
    var matchDeckName by mutableStateOf("")
    var matchDeckList by mutableStateOf("")
    var matchResult by mutableStateOf("")
    var matchOpponentName by mutableStateOf("")
    var matchOpponentDeck by mutableStateOf("")
    var matchNotes by mutableStateOf("")

    // Stats
    val wins: Int get() = matchLogs.count { it.result == "W" }
    val losses: Int get() = matchLogs.count { it.result == "L" }
    val ties: Int get() = matchLogs.count { it.result == "T" }
    val winRate: Float get() {
        val total = wins + losses + ties
        return if (total == 0) 0f else (wins.toFloat() / total) * 100f
    }

    init {
        loadMatchLogs()
    }

    private fun loadMatchLogs() {
        viewModelScope.launch {
            isLoading = true
            repository.getMatchLogs()
                .catch { isLoading = false }
                .collectLatest { logs ->
                    matchLogs = logs
                    isLoading = false
                }
        }
    }

    fun getMatchById(matchId: String): MatchLog? {
        return matchLogs.find { it.id == matchId }
    }

    fun loadMatchForEdit(match: MatchLog) {
        editingMatchId = match.id
        matchLocation = match.location
        matchDate = match.date ?: Timestamp.now()
        matchFormat = match.format
        matchDeckName = match.deckName
        matchDeckList = match.deckList
        matchResult = match.result
        matchOpponentName = match.opponentName
        matchOpponentDeck = match.opponentDeck
        matchNotes = match.notes
    }

    fun saveMatch(onSuccess: () -> Unit = {}) {
        if (matchDeckName.isBlank() || matchResult.isBlank()) return
        viewModelScope.launch {
            isSaving = true
            val existing = editingMatchId?.let { id -> matchLogs.find { it.id == id } }
            val match = MatchLog(
                id = editingMatchId ?: "",
                location = matchLocation,
                date = matchDate,
                format = matchFormat,
                deckName = matchDeckName,
                deckList = matchDeckList,
                result = matchResult,
                opponentName = matchOpponentName,
                opponentDeck = matchOpponentDeck,
                notes = matchNotes,
                createdAt = existing?.createdAt
            )
            repository.saveMatchLog(match)
            isSaving = false
            resetForm()
            onSuccess()
        }
    }

    fun deleteMatch(matchId: String) {
        viewModelScope.launch {
            repository.deleteMatchLog(matchId)
        }
    }

    fun resetForm() {
        editingMatchId = null
        matchLocation = ""
        matchDate = Timestamp.now()
        matchFormat = ""
        matchDeckName = ""
        matchDeckList = ""
        matchResult = ""
        matchOpponentName = ""
        matchOpponentDeck = ""
        matchNotes = ""
    }
}

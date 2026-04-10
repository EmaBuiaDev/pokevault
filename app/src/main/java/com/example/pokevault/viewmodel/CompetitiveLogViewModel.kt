package com.example.pokevault.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.pokevault.data.firebase.FirestoreRepository
import com.example.pokevault.data.model.Deck
import com.example.pokevault.data.model.MatchLog
import com.example.pokevault.data.model.Tournament
import com.google.firebase.Timestamp
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class CompetitiveLogViewModel : ViewModel() {
    private val repository = FirestoreRepository()

    // ── Tornei ──
    var tournaments by mutableStateOf<List<Tournament>>(emptyList())
        private set

    // ── Partite del torneo corrente ──
    var tournamentMatches by mutableStateOf<List<MatchLog>>(emptyList())
        private set

    // ── Deck dell'utente ──
    var userDecks by mutableStateOf<List<Deck>>(emptyList())
        private set

    var isLoading by mutableStateOf(false)
        private set

    var isSaving by mutableStateOf(false)
        private set

    // ── Tournament form state ──
    var editingTournamentId by mutableStateOf<String?>(null)
    var tournamentLocation by mutableStateOf("")
    var tournamentDate by mutableStateOf<Timestamp>(Timestamp.now())
    var tournamentParticipants by mutableStateOf("")
    var tournamentFee by mutableStateOf("")
    var tournamentType by mutableStateOf("")
    var tournamentFormat by mutableStateOf("")
    var tournamentDeckName by mutableStateOf("")
    var tournamentDeckId by mutableStateOf("")

    // ── Match form state ──
    var editingMatchId by mutableStateOf<String?>(null)
    var matchTournamentId by mutableStateOf("")
    var matchRound by mutableStateOf("")
    var matchResult by mutableStateOf("")
    var matchOpponentName by mutableStateOf("")
    var matchOpponentDeck by mutableStateOf("")
    var matchNotes by mutableStateOf("")

    // ── Stats (calcolate sulle partite del torneo corrente) ──
    val wins: Int get() = tournamentMatches.count { it.result == "W" }
    val losses: Int get() = tournamentMatches.count { it.result == "L" }
    val ties: Int get() = tournamentMatches.count { it.result == "T" }
    val winRate: Float get() {
        val total = wins + losses + ties
        return if (total == 0) 0f else (wins.toFloat() / total) * 100f
    }

    // ── Stats globali (tutte le partite) ──
    var allMatches by mutableStateOf<List<MatchLog>>(emptyList())
        private set
    val globalWins: Int get() = allMatches.count { it.result == "W" }
    val globalLosses: Int get() = allMatches.count { it.result == "L" }
    val globalTies: Int get() = allMatches.count { it.result == "T" }
    val globalWinRate: Float get() {
        val total = globalWins + globalLosses + globalTies
        return if (total == 0) 0f else (globalWins.toFloat() / total) * 100f
    }

    private var matchesJob: Job? = null

    init {
        loadTournaments()
        loadDecks()
        loadAllMatches()
    }

    private fun loadTournaments() {
        viewModelScope.launch {
            isLoading = true
            repository.getTournaments()
                .catch { isLoading = false }
                .collectLatest { list ->
                    tournaments = list
                    isLoading = false
                }
        }
    }

    private fun loadDecks() {
        viewModelScope.launch {
            repository.getDecks()
                .catch { /* ignora errori */ }
                .collectLatest { decks ->
                    userDecks = decks
                }
        }
    }

    private fun loadAllMatches() {
        viewModelScope.launch {
            repository.getMatchLogs()
                .catch { /* ignora */ }
                .collectLatest { logs ->
                    allMatches = logs
                }
        }
    }

    fun loadMatchesForTournament(tournamentId: String) {
        matchesJob?.cancel()
        matchesJob = viewModelScope.launch {
            repository.getMatchesForTournament(tournamentId)
                .catch { /* ignora */ }
                .collectLatest { matches ->
                    tournamentMatches = matches
                }
        }
    }

    // ── Tournament CRUD ──

    fun getTournamentById(tournamentId: String): Tournament? {
        return tournaments.find { it.id == tournamentId }
    }

    fun loadTournamentForEdit(tournament: Tournament) {
        editingTournamentId = tournament.id
        tournamentLocation = tournament.location
        tournamentDate = tournament.date ?: Timestamp.now()
        tournamentParticipants = if (tournament.participants > 0) tournament.participants.toString() else ""
        tournamentFee = if (tournament.registrationFee > 0) tournament.registrationFee.toString() else ""
        tournamentType = tournament.type
        tournamentFormat = tournament.format
        tournamentDeckName = tournament.deckName
        tournamentDeckId = tournament.deckId
    }

    fun saveTournament(onSuccess: () -> Unit = {}) {
        if (tournamentType.isBlank()) return
        viewModelScope.launch {
            isSaving = true
            val existing = editingTournamentId?.let { id -> tournaments.find { it.id == id } }
            val tournament = Tournament(
                id = editingTournamentId ?: "",
                location = tournamentLocation,
                date = tournamentDate,
                participants = tournamentParticipants.toIntOrNull() ?: 0,
                registrationFee = tournamentFee.toDoubleOrNull() ?: 0.0,
                type = tournamentType,
                format = tournamentFormat,
                deckName = tournamentDeckName,
                deckId = tournamentDeckId,
                createdAt = existing?.createdAt
            )
            repository.saveTournament(tournament)
            isSaving = false
            resetTournamentForm()
            onSuccess()
        }
    }

    fun deleteTournament(tournamentId: String) {
        viewModelScope.launch {
            repository.deleteTournament(tournamentId)
        }
    }

    fun resetTournamentForm() {
        editingTournamentId = null
        tournamentLocation = ""
        tournamentDate = Timestamp.now()
        tournamentParticipants = ""
        tournamentFee = ""
        tournamentType = ""
        tournamentFormat = ""
        tournamentDeckName = ""
        tournamentDeckId = ""
    }

    // ── Match CRUD ──

    fun getMatchById(matchId: String): MatchLog? {
        return tournamentMatches.find { it.id == matchId }
    }

    fun loadMatchForEdit(match: MatchLog) {
        editingMatchId = match.id
        matchTournamentId = match.tournamentId
        matchRound = if (match.round > 0) match.round.toString() else ""
        matchResult = match.result
        matchOpponentName = match.opponentName
        matchOpponentDeck = match.opponentDeck
        matchNotes = match.notes
    }

    fun saveMatch(onSuccess: () -> Unit = {}) {
        if (matchResult.isBlank() || matchTournamentId.isBlank()) return
        viewModelScope.launch {
            isSaving = true
            val existing = editingMatchId?.let { id -> tournamentMatches.find { it.id == id } }
            val match = MatchLog(
                id = editingMatchId ?: "",
                tournamentId = matchTournamentId,
                round = matchRound.toIntOrNull() ?: (tournamentMatches.size + 1),
                result = matchResult,
                opponentName = matchOpponentName,
                opponentDeck = matchOpponentDeck,
                notes = matchNotes,
                createdAt = existing?.createdAt
            )
            repository.saveMatchLog(match)
            isSaving = false
            resetMatchForm()
            onSuccess()
        }
    }

    fun deleteMatch(matchId: String) {
        viewModelScope.launch {
            repository.deleteMatchLog(matchId)
        }
    }

    fun resetMatchForm() {
        editingMatchId = null
        matchRound = ""
        matchResult = ""
        matchOpponentName = ""
        matchOpponentDeck = ""
        matchNotes = ""
    }
}

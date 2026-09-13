package com.emabuia.pokevault.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.emabuia.pokevault.data.model.MetaArchetype
import com.emabuia.pokevault.data.model.MetaDeck
import com.emabuia.pokevault.data.model.TournamentKind
import com.emabuia.pokevault.data.model.TournamentResult
import com.emabuia.pokevault.data.remote.LimitlessRateLimitException
import com.emabuia.pokevault.data.remote.LimitlessTcgRepository
import kotlinx.coroutines.launch

class MetaDeckViewModel : ViewModel() {

    private val repository = LimitlessTcgRepository()

    // Risultati strutturati per torneo (Win Tournament section)
    var tournamentResults by mutableStateOf<List<TournamentResult>>(emptyList())
        private set

    // Meta Deck - archetype standings
    var archetypes by mutableStateOf<List<MetaArchetype>>(emptyList())
        private set

    var isLoadingTournaments by mutableStateOf(false)
        private set

    var isLoadingArchetypes by mutableStateOf(false)
        private set

    var tournamentsError by mutableStateOf<String?>(null)
        private set

    var archetypeError by mutableStateOf<String?>(null)
        private set

    /**
     * Secondi da aspettare prima che l'API Limitless torni disponibile, o null
     * se non siamo in pausa.
     *
     * E' separato dagli errori perche' non e' un errore: l'API concede 50
     * richieste ogni 5 minuti, e quando sono finite l'unica cosa da fare e'
     * aspettare. Mostrarlo come "errore di connessione" faceva riprovare
     * l'utente, il che allungava l'attesa invece di accorciarla.
     */
    var rateLimitedForSeconds by mutableStateOf<Long?>(null)
        private set

    var selectedFormat by mutableStateOf("standard")
        private set

    /** Dal vivo, online o entrambi. Vale solo per la sezione Win Tournament. */
    var selectedKind by mutableStateOf(TournamentKind.ALL)
        private set

    var selectedDeck by mutableStateOf<MetaDeck?>(null)
        private set

    // Timestamp (in ms) dell'ultima sincronizzazione con l'API Limitless.
    // Viene aggiornato in automatico quando una load() termina con successo.
    // Se c'è già una voce valida in cache condivisa, parte da quel valore,
    // così la UI mostra l'età reale dei dati anche subito dopo la navigazione.
    var lastUpdated by mutableStateOf<Long?>(
        LimitlessTcgRepository.lastCacheTimestamp("standard")
    )
        private set

    // Rate limit: impedisce refresh troppo ravvicinati (60 s) che
    // spammerebbero inutilmente l'API Limitless.
    private var lastManualRefreshAt: Long = 0L
    private val refreshCooldownMs = 60_000L

    val refreshCooldownSeconds: Long
        get() {
            val elapsed = System.currentTimeMillis() - lastManualRefreshAt
            val remaining = refreshCooldownMs - elapsed
            return (remaining / 1000L).coerceAtLeast(0L)
        }

    /**
     * Cosa e' gia' stato chiesto alla rete, per non richiederlo a ogni
     * ricomposizione della tab.
     *
     * Prima `init` lanciava tutti i caricamenti insieme, comprese le due
     * sezioni meta, anche per chi apriva il Deck Lab solo per guardare i propri
     * mazzi: decine di richieste all'API Limitless prima ancora che si toccasse
     * una tab. Ora ogni sezione chiede i suoi dati quando viene mostrata.
     */
    private var archetypesRequested = false
    private var tournamentsRequested = false

    /** Da chiamare quando la tab Meta Deck compare. */
    fun ensureArchetypesLoaded() {
        if (archetypesRequested) return
        archetypesRequested = true
        loadArchetypes()
    }

    /** Da chiamare quando la tab Win Tournament compare. */
    fun ensureTournamentsLoaded() {
        if (tournamentsRequested) return
        tournamentsRequested = true
        loadTournamentResults()
    }

    fun loadTournamentResults(
        format: String = selectedFormat,
        kind: TournamentKind = selectedKind,
        limit: Int = 10
    ) {
        isLoadingTournaments = true
        tournamentsError = null

        viewModelScope.launch {
            repository.getTournamentResults(format = format, limit = limit, kind = kind)
                .onSuccess { results ->
                    tournamentResults = results
                    isLoadingTournaments = false
                    rateLimitedForSeconds = null
                    lastUpdated = LimitlessTcgRepository.lastCacheTimestamp(format)
                        ?: System.currentTimeMillis()
                }
                .onFailure { e ->
                    isLoadingTournaments = false
                    if (e.isRateLimit()) {
                        rateLimitedForSeconds = repository.rateLimitRetryAfterSeconds()
                        tournamentsError = null
                    } else {
                        tournamentsError = e.localizedMessage ?: "Errore nel caricamento dei tornei"
                    }
                }
        }
    }

    fun loadArchetypes(format: String = selectedFormat) {
        isLoadingArchetypes = true
        archetypeError = null

        viewModelScope.launch {
            repository.getMetaArchetypes(format = format)
                .onSuccess { list ->
                    archetypes = list
                    isLoadingArchetypes = false
                    rateLimitedForSeconds = null
                    lastUpdated = LimitlessTcgRepository.lastCacheTimestamp(format)
                        ?: System.currentTimeMillis()
                }
                .onFailure { e ->
                    isLoadingArchetypes = false
                    if (e.isRateLimit()) {
                        rateLimitedForSeconds = repository.rateLimitRetryAfterSeconds()
                        archetypeError = null
                    } else {
                        archetypeError = e.localizedMessage ?: "Errore nel caricamento"
                    }
                }
        }
    }

    fun selectFormat(format: String) {
        if (format == selectedFormat) return

        selectedFormat = format
        if (archetypesRequested) loadArchetypes(format = format)
        if (tournamentsRequested) loadTournamentResults(format = format)
        lastUpdated = LimitlessTcgRepository.lastCacheTimestamp(format)
    }

    fun selectKind(kind: TournamentKind) {
        if (kind == selectedKind) return

        selectedKind = kind
        tournamentsRequested = true
        loadTournamentResults(kind = kind)
    }

    fun selectDeck(deck: MetaDeck?) {
        selectedDeck = deck
    }

    /**
     * Forza un refresh ignorando la cache.
     * Ritorna `false` se siamo ancora dentro il cooldown (ed in quel caso
     * non fa niente), `true` se il refresh è stato avviato.
     */
    fun refresh(): Boolean {
        val now = System.currentTimeMillis()
        if (now - lastManualRefreshAt < refreshCooldownMs) {
            return false
        }

        // Un refresh a finestra chiusa non aggiorna niente e non accorcia
        // l'attesa: si dice all'utente quanto manca invece di fingere.
        val retryAfter = repository.rateLimitRetryAfterSeconds()
        if (retryAfter > 0) {
            rateLimitedForSeconds = retryAfter
            return false
        }

        lastManualRefreshAt = now
        repository.clearCache()
        if (archetypesRequested) loadArchetypes()
        if (tournamentsRequested) loadTournamentResults()
        return true
    }

    /** Secondi da aspettare da mostrare all'utente, aggiornato al momento. */
    fun currentRateLimitWait(): Long = repository.rateLimitRetryAfterSeconds()
}

/**
 * Vero quando il fallimento e' la finestra di rate limit chiusa, non un guasto.
 *
 * Si guarda anche la causa perche' Retrofit incarta le IOException sollevate
 * dagli interceptor prima di restituirle.
 */
private fun Throwable.isRateLimit(): Boolean =
    this is LimitlessRateLimitException || cause is LimitlessRateLimitException

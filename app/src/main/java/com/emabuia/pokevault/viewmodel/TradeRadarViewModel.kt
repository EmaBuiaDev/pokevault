package com.emabuia.pokevault.viewmodel

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.emabuia.pokevault.data.firebase.FirestoreRepository
import com.emabuia.pokevault.data.trade.CoarseLocation
import com.emabuia.pokevault.data.trade.TradeApi
import com.emabuia.pokevault.data.trade.TradeLists
import com.emabuia.pokevault.data.trade.dto.TradeHaveItem
import com.emabuia.pokevault.data.trade.dto.TradeHavesPayload
import com.emabuia.pokevault.data.trade.dto.TradeMatch
import com.emabuia.pokevault.data.trade.dto.TradeOwnedRequest
import com.emabuia.pokevault.data.trade.dto.TradeProfilePayload
import com.emabuia.pokevault.data.trade.dto.TradeProfileRequest
import com.emabuia.pokevault.data.trade.dto.TradeWantItem
import com.emabuia.pokevault.data.trade.dto.TradeWantsRequest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * TradeRadar, fase 1: attivazione, sincronizzazione delle liste e match.
 *
 * Il server sa solo quello che gli manda questa classe. La sincronizzazione
 * parte all'apertura della schermata e dopo ogni modifica fatta qui dentro:
 * non c'e' ancora nessun invio in background (arrivera' con le notifiche).
 */
class TradeRadarViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = FirestoreRepository()

    sealed class Screen {
        data object Loading : Screen()
        data object Onboarding : Screen()
        data class Ready(val profile: TradeProfilePayload) : Screen()
        data class Error(val message: Problem) : Screen()
    }

    enum class Problem { UNAUTHORIZED, UNAVAILABLE, REJECTED, NO_LOCATION }

    var screen by mutableStateOf<Screen>(Screen.Loading)
        private set

    /** I doppioni offribili, calcolati dalla collezione. */
    var duplicates by mutableStateOf<List<TradeLists.Duplicate>>(emptyList())
        private set

    /** Gli id ([TradeLists.Duplicate.id]) dei doppioni accesi per lo scambio. */
    var enabledIds by mutableStateOf<Set<String>>(emptySet())
        private set

    var wantsCount by mutableStateOf(0)
        private set

    var matches by mutableStateOf<List<TradeMatch>>(emptyList())
        private set

    var busy by mutableStateOf(false)
        private set

    /** Una richiesta dei match in corso: fa girare il radar. */
    var refreshing by mutableStateOf(false)
        private set

    /** Vero dalla prima risposta dei match: da li' le schede entrano a cascata, una volta sola. */
    var matchesLoaded by mutableStateOf(false)
        private set

    private val prefs = application.getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE)

    /** La spiegazione delle etichette si mostra finche' non si preme "Ho capito". */
    var levelsExplained by mutableStateOf(prefs.getBoolean(KEY_LEVELS_EXPLAINED, false))
        private set

    /** Un messaggio da mostrare una volta (snackbar), poi da consumare. */
    var notice by mutableStateOf<Problem?>(null)
        private set

    init {
        load()
    }

    fun consumeNotice() {
        notice = null
    }

    fun dismissLevelsIntro() {
        levelsExplained = true
        prefs.edit().putBoolean(KEY_LEVELS_EXPLAINED, true).apply()
    }

    fun load() {
        viewModelScope.launch {
            screen = Screen.Loading
            when (val result = TradeApi.getProfile()) {
                is TradeApi.Result.Ok -> {
                    screen = Screen.Ready(result.value)
                    syncAndRefresh()
                }
                TradeApi.Result.NoProfile -> screen = Screen.Onboarding
                else -> screen = Screen.Error(problemOf(result))
            }
        }
    }

    /** Attivazione: le due conferme sono obbligatorie anche per il server. */
    fun activate(nickname: String, adultConfirmed: Boolean, collectionConsent: Boolean) {
        viewModelScope.launch {
            busy = true
            val cell = CoarseLocation.currentCell(getApplication())
            if (cell == null) {
                notice = Problem.NO_LOCATION
                busy = false
                return@launch
            }
            val result = TradeApi.putProfile(
                TradeProfileRequest(nickname.trim(), cell, adultConfirmed, collectionConsent, paused = false)
            )
            busy = false
            when (result) {
                is TradeApi.Result.Ok -> {
                    screen = Screen.Ready(result.value)
                    syncAndRefresh()
                }
                else -> notice = problemOf(result)
            }
        }
    }

    /** Rilegge la zona, per chi si e' spostato. */
    fun refreshZone() {
        val profile = (screen as? Screen.Ready)?.profile ?: return
        viewModelScope.launch {
            busy = true
            val cell = CoarseLocation.currentCell(getApplication())
            if (cell == null) {
                notice = Problem.NO_LOCATION
            } else {
                updateProfile(profile, cell, profile.paused == true)
                refreshMatches()
            }
            busy = false
        }
    }

    fun setPaused(paused: Boolean) {
        val profile = (screen as? Screen.Ready)?.profile ?: return
        viewModelScope.launch {
            updateProfile(profile, profile.geohash5.orEmpty(), paused)
            refreshMatches()
        }
    }

    /** Disattiva e cancella dal server profilo, liste e carte possedute. */
    fun deactivate() {
        viewModelScope.launch {
            busy = true
            val result = TradeApi.deleteProfile()
            busy = false
            if (result is TradeApi.Result.Ok) {
                duplicates = emptyList()
                enabledIds = emptySet()
                matches = emptyList()
                screen = Screen.Onboarding
            } else {
                notice = problemOf(result)
            }
        }
    }

    fun setEnabled(id: String, enabled: Boolean) {
        enabledIds = if (enabled) enabledIds + id else enabledIds - id
        viewModelScope.launch { pushHaves() }
    }

    fun setAllEnabled(enabled: Boolean) {
        enabledIds = if (enabled) duplicates.map { it.id }.toSet() else emptySet()
        viewModelScope.launch { pushHaves() }
    }

    fun refreshMatches() {
        if (refreshing) return
        viewModelScope.launch {
            refreshing = true
            try {
                when (val result = TradeApi.matches()) {
                    is TradeApi.Result.Ok -> {
                        matches = result.value.matches.orEmpty()
                        matchesLoaded = true
                    }
                    else -> notice = problemOf(result)
                }
            } finally {
                refreshing = false
            }
        }
    }

    // ── Sincronizzazione ────────────────────────────────────────────────────

    private suspend fun updateProfile(profile: TradeProfilePayload, cell: String, paused: Boolean) {
        val result = TradeApi.putProfile(
            TradeProfileRequest(profile.nickname.orEmpty(), cell, adultConfirmed = true, collectionConsent = true, paused = paused)
        )
        if (result is TradeApi.Result.Ok) screen = Screen.Ready(result.value) else notice = problemOf(result)
    }

    /**
     * Manda al server possedute, cercate e doppioni accesi, poi chiede i match.
     *
     * Le possedute si rimandano solo se l'impronta e' cambiata rispetto a
     * quella che il server ha gia'. I doppioni accesi si leggono dal server
     * (e' lui a ricordarli fra un'installazione e l'altra) e si tengono solo
     * quelli che esistono ancora in collezione.
     */
    private suspend fun syncAndRefresh() {
        val profile = (screen as? Screen.Ready)?.profile ?: return
        busy = true
        try {
            val cards = repository.getCards().first()
            val wishlists = repository.getWishlists().first()
            val goalAlbums = repository.getGoalAlbums().first()

            val owned = TradeLists.ownedKeys(cards)
            val hash = TradeLists.ownedHash(owned)
            if (hash != profile.ownedHash) {
                val result = TradeApi.putOwned(TradeOwnedRequest(owned.toList(), hash))
                if (result !is TradeApi.Result.Ok) { notice = problemOf(result); return }
            }

            val wants = TradeLists.wants(wishlists, goalAlbums, owned)
            wantsCount = wants.size
            val wantsResult = TradeApi.putWants(TradeWantsRequest(wants.map { TradeWantItem(it.key, it.source) }))
            if (wantsResult !is TradeApi.Result.Ok) { notice = problemOf(wantsResult); return }

            duplicates = TradeLists.duplicates(cards)
            val serverHaves = (TradeApi.getHaves() as? TradeApi.Result.Ok)?.value?.items.orEmpty()
            val serverIds = serverHaves.map { listOf(it.key, it.variant, it.condition, it.language).joinToString("|") }.toSet()
            enabledIds = duplicates.map { it.id }.filter { it in serverIds }.toSet()
            // Riallinea le quantita' (un doppione venduto non si offre piu').
            pushHaves()

            refreshMatches()
        } finally {
            busy = false
        }
    }

    private suspend fun pushHaves() {
        val items = duplicates.filter { it.id in enabledIds }.map {
            TradeHaveItem(key = it.key, variant = it.variant, condition = it.condition, language = it.language, qty = it.spare)
        }
        val result = TradeApi.putHaves(TradeHavesPayload(items))
        if (result !is TradeApi.Result.Ok) notice = problemOf(result)
    }

    private companion object {
        const val PREFS = "trade_radar"
        const val KEY_LEVELS_EXPLAINED = "levels_explained"
    }

    private fun problemOf(result: TradeApi.Result<*>): Problem = when (result) {
        TradeApi.Result.Unauthorized -> Problem.UNAUTHORIZED
        is TradeApi.Result.Rejected -> Problem.REJECTED
        else -> Problem.UNAVAILABLE
    }
}

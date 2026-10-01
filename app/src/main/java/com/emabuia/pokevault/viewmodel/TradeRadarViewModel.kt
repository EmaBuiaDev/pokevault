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
import com.emabuia.pokevault.data.trade.dto.TradeCardOffer
import com.emabuia.pokevault.data.trade.dto.TradeHaveItem
import com.emabuia.pokevault.data.trade.dto.TradeHavesPayload
import com.emabuia.pokevault.data.trade.dto.TradeMatch
import com.emabuia.pokevault.data.trade.dto.TradeOwnedRequest
import com.emabuia.pokevault.data.trade.dto.TradeProfilePayload
import com.emabuia.pokevault.data.trade.dto.TradeProfileRequest
import com.emabuia.pokevault.data.trade.dto.TradeWantItem
import com.emabuia.pokevault.data.trade.dto.TradeWantsRequest
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
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

    /** Le carte in una copia sola: si offrono solo se l'utente le aggiunge a mano. */
    var singles by mutableStateOf<List<TradeLists.Duplicate>>(emptyList())
        private set

    /**
     * Cosa si offre: id ([TradeLists.Duplicate.id]) -> quante copie. Vale per
     * i doppioni accesi e per le carte singole aggiunte a mano.
     */
    var offers by mutableStateOf<Map<String, Int>>(emptyMap())
        private set

    /**
     * Le carte a mano con la campanella accesa: partecipano agli avvisi come i
     * doppioni. Senza, restano in lista ma nessuno viene avvisato.
     */
    var notifyIds by mutableStateOf<Set<String>>(emptySet())
        private set

    /** Gli id delle carte offerte. */
    val enabledIds: Set<String> get() = offers.keys

    /** Le carte singole che l'utente ha messo nella lista. */
    val manualOffers: List<TradeLists.Duplicate> get() = singles.filter { it.id in offers }

    private var pushJob: Job? = null

    var wantsCount by mutableStateOf(0)
        private set

    /** In ordine di punteggio: il server manda i migliori per primi. */
    var matches by mutableStateOf<List<TradeMatch>>(emptyList())
        private set

    /** La vista per carta: ogni carta che puoi ricevere, con chi ce l'ha. */
    var cards by mutableStateOf<List<TradeCardOffer>>(emptyList())
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
                pushJob?.cancel()
                duplicates = emptyList()
                singles = emptyList()
                offers = emptyMap()
                notifyIds = emptySet()
                matches = emptyList()
                cards = emptyList()
                screen = Screen.Onboarding
            } else {
                notice = problemOf(result)
            }
        }
    }

    /**
     * Accende o spegne un'offerta: un doppione, o una carta singola aggiunta a
     * mano. Si parte da una copia; quante offrirne lo dice [setQuantity].
     */
    fun setEnabled(id: String, enabled: Boolean) {
        offers = if (enabled) offers + (id to (offers[id] ?: 1)) else offers - id
        if (!enabled) notifyIds = notifyIds - id
        schedulePush()
    }

    /** La campanella di una carta aggiunta a mano. */
    fun setNotify(id: String, notify: Boolean) {
        if (id !in offers) return
        notifyIds = if (notify) notifyIds + id else notifyIds - id
        schedulePush()
    }

    /** Quante copie offrire, fra 1 e quelle che si possono dare. */
    fun setQuantity(id: String, quantity: Int) {
        if (id !in offers) return
        val max = (duplicates + singles).firstOrNull { it.id == id }?.spare ?: return
        val clamped = quantity.coerceIn(1, max)
        if (offers[id] == clamped) return
        offers = offers + (id to clamped)
        schedulePush()
    }

    /** "Tutti" / "Nessuno": tocca solo i doppioni, mai le carte aggiunte a mano. */
    fun setAllEnabled(enabled: Boolean) {
        val ids = duplicates.map { it.id }
        offers = if (enabled) offers + ids.associateWith { offers[it] ?: 1 } else offers - ids.toSet()
        schedulePush()
    }

    /**
     * Il server si aggiorna dopo una breve pausa: chi preme "+" tre volte di
     * fila manda una richiesta sola. Poi si rileggono i match, che dipendono
     * da cosa si offre.
     */
    private fun schedulePush() {
        pushJob?.cancel()
        pushJob = viewModelScope.launch {
            delay(PUSH_DEBOUNCE_MS)
            pushHaves()
            refreshMatches()
        }
    }

    fun refreshMatches() {
        if (refreshing) return
        viewModelScope.launch {
            refreshing = true
            try {
                when (val result = TradeApi.matches()) {
                    is TradeApi.Result.Ok -> {
                        matches = result.value.matches.orEmpty()
                        cards = result.value.cards.orEmpty()
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
     * quella che il server ha gia'. Le offerte si leggono dal server (e' lui a
     * ricordarle fra un'installazione e l'altra) e si tengono solo quelle che
     * esistono ancora in collezione, con le copie ridotte a quelle che restano.
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
            singles = TradeLists.singles(cards)
            val serverHaves = (TradeApi.getHaves() as? TradeApi.Result.Ok)?.value?.items.orEmpty()
            val serverQuantities = serverHaves.associate {
                listOf(it.key, it.variant, it.condition, it.language).joinToString("|") to (it.qty ?: 1)
            }
            offers = (duplicates + singles).mapNotNull { offer ->
                serverQuantities[offer.id]?.let { offer.id to it.coerceIn(1, offer.spare) }
            }.toMap()
            val serverNotify = serverHaves.filter { it.notify == true }
                .map { listOf(it.key, it.variant, it.condition, it.language).joinToString("|") }.toSet()
            notifyIds = singles.map { it.id }.filter { it in offers && it in serverNotify }.toSet()
            // Riallinea le quantita' (un doppione venduto non si offre piu').
            pushHaves()

            refreshMatches()
        } finally {
            busy = false
        }
    }

    private suspend fun pushHaves() {
        val singleIds = singles.map { it.id }.toSet()
        val items = (duplicates + singles).mapNotNull { offer ->
            val quantity = offers[offer.id] ?: return@mapNotNull null
            val manual = offer.id in singleIds
            TradeHaveItem(
                key = offer.key, variant = offer.variant, condition = offer.condition, language = offer.language, qty = quantity,
                manual = manual, notify = !manual || offer.id in notifyIds
            )
        }
        val result = TradeApi.putHaves(TradeHavesPayload(items))
        if (result !is TradeApi.Result.Ok) notice = problemOf(result)
    }

    private companion object {
        const val PREFS = "trade_radar"
        const val KEY_LEVELS_EXPLAINED = "levels_explained"
        const val PUSH_DEBOUNCE_MS = 600L
    }

    private fun problemOf(result: TradeApi.Result<*>): Problem = when (result) {
        TradeApi.Result.Unauthorized -> Problem.UNAUTHORIZED
        is TradeApi.Result.Rejected -> Problem.REJECTED
        else -> Problem.UNAVAILABLE
    }
}

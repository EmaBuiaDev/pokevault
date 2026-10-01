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
import com.emabuia.pokevault.data.remote.RepositoryProvider
import com.emabuia.pokevault.data.trade.TradeCardKey
import com.emabuia.pokevault.data.trade.OverpassClient
import com.emabuia.pokevault.data.trade.dto.TradeAddSpotRequest
import com.emabuia.pokevault.data.trade.dto.TradeCardOffer
import com.emabuia.pokevault.data.trade.dto.TradeCellUpload
import com.emabuia.pokevault.data.trade.dto.TradeMeetingRequest
import com.emabuia.pokevault.data.trade.dto.TradeSlot
import com.emabuia.pokevault.data.trade.dto.TradeSpot
import com.emabuia.pokevault.data.trade.dto.TradeSpotCandidate
import com.emabuia.pokevault.data.trade.dto.TradeCounterRequest
import com.emabuia.pokevault.data.trade.dto.TradeMatchItem
import com.emabuia.pokevault.data.trade.dto.TradeOfferItem
import com.emabuia.pokevault.data.trade.dto.TradeProposal
import com.emabuia.pokevault.data.trade.dto.TradeProposalRequest
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

    enum class Problem { UNAUTHORIZED, UNAVAILABLE, REJECTED, NO_LOCATION, ALREADY_OPEN, NOT_AVAILABLE }

    /** Conferme da mostrare una volta, come [notice] ma non sono errori. */
    enum class Info { PROPOSAL_SENT, COUNTER_SENT, ACCEPTED, DECLINED, CANCELLED, MEETING_SENT, MEETING_CONFIRMED, SPOT_REPORTED }

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

    var info by mutableStateOf<Info?>(null)
        private set

    init {
        load()
    }

    fun consumeNotice() {
        notice = null
    }

    fun consumeInfo() {
        info = null
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
                proposals = emptyList()
                composer = null
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

    // ── Proposte (fase 2a) ──────────────────────────────────────────────────

    /** Le mie proposte, aperte e chiuse da poco, viste da me. */
    var proposals by mutableStateOf<List<TradeProposal>>(emptyList())
        private set

    var proposalsLoaded by mutableStateOf(false)
        private set

    /** Quelle in cui tocca a me rispondere: il numero sulla tab. */
    val proposalsToAnswer: Int get() = proposals.count { it.actionNeeded == true || it.myTurn == true }

    /** La proposta su cui si sta agendo (accetta, rifiuta...), per il caricamento sul tasto. */
    var actingOn by mutableStateOf<String?>(null)
        private set

    /**
     * Il contenitore aperto nella tab Proposte (nome di ProposalBucket nella
     * schermata); null = sceglie la schermata, partendo da cio' che aspetta te.
     */
    var proposalsBucket by mutableStateOf<String?>(null)
        private set

    fun selectProposalsBucket(name: String?) {
        proposalsBucket = name
    }

    /** Cresce quando l'app deve portare l'utente sulla tab Proposte (dopo un invio). */
    var focusProposals by mutableStateOf(0)
        private set

    /**
     * La proposta in composizione. [give] e [take] vanno dall'id della carta
     * ([offerId]) alle copie: [give] fra le mie offerte, [take] fra le sue.
     */
    data class Composer(
        val counterpartId: String,
        val nickname: String,
        val distance: String?,
        /** Se non e' null si sta scrivendo una controproposta a questa proposta. */
        val counterTo: String? = null,
        val theirOffers: List<TradeOfferItem> = emptyList(),
        val loading: Boolean = true,
        val give: Map<String, Int> = emptyMap(),
        val take: Map<String, Int> = emptyMap(),
        val sending: Boolean = false
    )

    var composer by mutableStateOf<Composer?>(null)
        private set

    /** Prezzo di una carta per chiave (il minimo di Cardmarket, come nel resto dell'app). */
    var prices by mutableStateOf<Map<String, Double>>(emptyMap())
        private set
    private val pricedSets = mutableSetOf<String>()

    /** Le mie carte offerte, nella forma delle proposte, con le copie che offro. */
    val myOffers: List<TradeOfferItem>
        get() = (duplicates + singles).mapNotNull { offer ->
            val quantity = offers[offer.id] ?: return@mapNotNull null
            TradeOfferItem(
                key = offer.key, variant = offer.variant, condition = offer.condition, language = offer.language,
                qty = quantity, name = offer.name, setName = offer.setName
            )
        }

    fun refreshProposals() {
        viewModelScope.launch {
            when (val result = TradeApi.proposals()) {
                is TradeApi.Result.Ok -> {
                    proposals = result.value.proposals.orEmpty()
                    proposalsLoaded = true
                    ensurePrices(proposals.flatMap { it.give.orEmpty() + it.take.orEmpty() }.mapNotNull { it.key })
                }
                else -> notice = problemOf(result)
            }
        }
    }

    /**
     * Apre la composizione verso una persona. Le carte partono gia' scelte:
     * [presetKey] se si arriva da una carta, altrimenti fino a tre "La cerchi"
     * per parte (o la prima carta, se non ce ne sono). Si cambia tutto.
     */
    fun openComposer(match: TradeMatch, presetKey: String? = null) {
        val id = match.id ?: return
        composer = Composer(id, match.nickname.orEmpty(), match.distance)
        viewModelScope.launch {
            val theirs = (TradeApi.userHaves(id) as? TradeApi.Result.Ok)?.value?.items.orEmpty()
            fun preset(items: List<TradeMatchItem>): Set<String> =
                items.filter { it.level == "wanted" }.take(3).mapNotNull { it.key }.toSet()
                    .ifEmpty { items.take(1).mapNotNull { it.key }.toSet() }
            val takeKeys = presetKey?.let { setOf(it) } ?: preset(match.theyGive.orEmpty())
            val giveKeys = preset(match.iGive.orEmpty())
            composer = composer?.takeIf { it.counterpartId == id && it.counterTo == null }?.copy(
                theirOffers = theirs,
                loading = false,
                take = theirs.filter { it.key in takeKeys }.distinctBy { it.key }.associate { offerId(it) to 1 },
                give = myOffers.filter { it.key in giveKeys }.distinctBy { it.key }.associate { offerId(it) to 1 }
            )
            ensurePrices((theirs + myOffers).mapNotNull { it.key })
        }
    }

    /** Controproposta: si riparte dalle carte dell'ultima revisione. */
    fun openCounter(proposal: TradeProposal) {
        val other = proposal.counterpart ?: return
        val id = other.id ?: return
        composer = Composer(id, other.nickname.orEmpty(), other.distance, counterTo = proposal.id)
        viewModelScope.launch {
            val theirs = (TradeApi.userHaves(id) as? TradeApi.Result.Ok)?.value?.items.orEmpty()
            composer = composer?.takeIf { it.counterTo == proposal.id }?.copy(
                theirOffers = theirs,
                loading = false,
                give = proposal.give.orEmpty().associate { offerId(it) to (it.qty ?: 1) },
                take = proposal.take.orEmpty().associate { offerId(it) to (it.qty ?: 1) }
            )
            ensurePrices((theirs + myOffers).mapNotNull { it.key })
        }
    }

    fun closeComposer() {
        composer = null
    }

    /** Copie di una mia carta nella proposta; 0 la toglie. */
    fun setGive(id: String, quantity: Int) {
        val current = composer ?: return
        val max = myOffers.firstOrNull { offerId(it) == id }?.qty ?: return
        composer = current.copy(give = current.give.withQuantity(id, quantity.coerceAtMost(max)))
    }

    /** Copie di una sua carta nella proposta; 0 la toglie. */
    fun setTake(id: String, quantity: Int) {
        val current = composer ?: return
        val max = current.theirOffers.firstOrNull { offerId(it) == id }?.qty ?: return
        composer = current.copy(take = current.take.withQuantity(id, quantity.coerceAtMost(max)))
    }

    private fun Map<String, Int>.withQuantity(id: String, quantity: Int): Map<String, Int> =
        if (quantity <= 0) this - id else this + (id to quantity)

    fun sendComposer() {
        val current = composer ?: return
        if (current.give.isEmpty() || current.take.isEmpty() || current.sending) return
        val give = myOffers.mapNotNull { item -> current.give[offerId(item)]?.let { item.copy(qty = it, name = null, setName = null) } }
        val take = current.theirOffers.mapNotNull { item -> current.take[offerId(item)]?.let { item.copy(qty = it, name = null, setName = null) } }
        composer = current.copy(sending = true)
        viewModelScope.launch {
            val result = if (current.counterTo != null) {
                TradeApi.counterProposal(current.counterTo, TradeCounterRequest(give, take))
            } else {
                TradeApi.createProposal(TradeProposalRequest(current.counterpartId, give, take))
            }
            when {
                result is TradeApi.Result.Ok -> {
                    composer = null
                    info = if (current.counterTo != null) Info.COUNTER_SENT else Info.PROPOSAL_SENT
                    proposalsBucket = "WAITING"
                    focusProposals++
                    refreshProposals()
                }
                result is TradeApi.Result.Rejected && result.error == "already_open" -> {
                    composer = null
                    notice = Problem.ALREADY_OPEN
                    proposalsBucket = null
                    focusProposals++
                    refreshProposals()
                }
                result is TradeApi.Result.Rejected && result.error == "not_offered" -> {
                    // Qualcuno ha cambiato le sue offerte nel frattempo: si rilegge e si riprova.
                    notice = Problem.NOT_AVAILABLE
                    val theirs = (TradeApi.userHaves(current.counterpartId) as? TradeApi.Result.Ok)?.value?.items
                    composer = composer?.copy(sending = false, theirOffers = theirs ?: current.theirOffers)
                }
                else -> {
                    notice = problemOf(result)
                    composer = composer?.copy(sending = false)
                }
            }
        }
    }

    /** accept | decline | cancel su una proposta. */
    fun answer(proposalId: String, action: String) {
        if (actingOn != null) return
        viewModelScope.launch {
            actingOn = proposalId
            val result = TradeApi.actOnProposal(proposalId, action)
            actingOn = null
            when {
                result is TradeApi.Result.Ok -> info = when (action) {
                    "accept" -> Info.ACCEPTED.also { proposalsBucket = "AGREED" }
                    "decline" -> Info.DECLINED
                    else -> Info.CANCELLED
                }
                result is TradeApi.Result.Rejected && result.error == "items_changed" -> notice = Problem.NOT_AVAILABLE
                else -> notice = problemOf(result)
            }
            refreshProposals()
        }
    }

    /**
     * Prezzi per il bilancio, presi set per set dallo stesso snapshot che usa
     * il resto dell'app. Una carta senza prezzo resta fuori dal conto.
     */
    private fun ensurePrices(keys: Collection<String>) {
        val sets = keys.map { TradeCardKey.setCodeOf(it) }.toSet() - pricedSets
        if (sets.isEmpty()) return
        pricedSets += sets
        viewModelScope.launch {
            val found = HashMap<String, Double>()
            for (set in sets) {
                val map = runCatching {
                    RepositoryProvider.italianPriceSnapshotRepository.getPriceMap(getApplication(), set)
                }.getOrDefault(emptyMap())
                for ((number, data) in map) {
                    val price = data.eurLow ?: data.eurTrend ?: data.eurAvg
                    if (price != null && price > 0) found["$set:$number"] = price
                }
            }
            if (found.isNotEmpty()) prices = prices + found
        }
    }

    // ── Appuntamento (fase 2b) ──────────────────────────────────────────────

    /** Copie riservate negli accordi, per id di carta offerta ([TradeLists.Duplicate.id]). */
    var reserved by mutableStateOf<Map<String, Int>>(emptyMap())
        private set

    /** Il pannello per fissare (o cambiare) l'appuntamento di un accordo. */
    data class Planner(
        val proposalId: String,
        val nickname: String,
        /** Si sta cambiando un appuntamento gia' fissato o proposto. */
        val changing: Boolean,
        val spots: List<TradeSpot> = emptyList(),
        val loading: Boolean = true,
        /** Il telefono sta scaricando da OpenStreetMap una zona nuova. */
        val downloadingArea: Boolean = false,
        val selectedSpot: String? = null,
        val slots: List<TradeSlot> = emptyList(),
        val query: String = "",
        val searchResults: List<TradeSpotCandidate> = emptyList(),
        val searching: Boolean = false,
        /** La ricerca e' finita senza risultati: si puo' segnalare il negozio. */
        val searchedEmpty: Boolean = false,
        val sending: Boolean = false
    )

    var planner by mutableStateOf<Planner?>(null)
        private set

    private var searchJob: Job? = null

    fun openPlanner(proposal: TradeProposal) {
        val id = proposal.id ?: return
        val meeting = proposal.meeting
        planner = Planner(
            proposalId = id,
            nickname = proposal.counterpart?.nickname.orEmpty(),
            changing = meeting?.status == "proposed" || meeting?.status == "confirmed",
            selectedSpot = meeting?.spot?.id,
            slots = meeting?.slots.orEmpty().filter { it.day >= java.time.LocalDate.now().toString() }
        )
        viewModelScope.launch { loadSpots(id) }
    }

    /**
     * I luoghi del server; se mancano delle zone le scarica il telefono da
     * OpenStreetMap, le manda al server e rilegge. Una zona che non si riesce
     * a scaricare non blocca niente: si va avanti con i luoghi che ci sono.
     */
    private suspend fun loadSpots(id: String) {
        var payload = (TradeApi.proposalSpots(id) as? TradeApi.Result.Ok)?.value
        val missing = payload?.missingCells.orEmpty()
        if (missing.isNotEmpty()) {
            planner = planner?.takeIf { it.proposalId == id }?.copy(downloadingArea = true, spots = payload?.spots.orEmpty())
            var uploaded = false
            for (cell in missing) {
                val code = cell.cell ?: continue
                val elements = OverpassClient.fetch(cell.query ?: continue) ?: continue
                if (TradeApi.uploadCell(TradeCellUpload(code, elements)) is TradeApi.Result.Ok) uploaded = true
            }
            if (uploaded) payload = (TradeApi.proposalSpots(id) as? TradeApi.Result.Ok)?.value ?: payload
        }
        val spots = payload?.spots.orEmpty()
        planner = planner?.takeIf { it.proposalId == id }?.let { current ->
            current.copy(
                spots = spots,
                loading = false,
                downloadingArea = false,
                selectedSpot = current.selectedSpot?.takeIf { selected -> spots.any { it.id == selected } } ?: spots.firstOrNull()?.id
            )
        }
    }

    fun closePlanner() {
        searchJob?.cancel()
        planner = null
    }

    fun selectSpot(id: String) {
        planner = planner?.copy(selectedSpot = id)
    }

    /** Aggiunge o toglie un orario (giorno e ora); al massimo tre. */
    fun toggleSlot(slot: TradeSlot) {
        val current = planner ?: return
        planner = when {
            current.slots.any { it.day == slot.day && it.time == slot.time && it.part == slot.part } ->
                current.copy(slots = current.slots.filterNot { it.day == slot.day && it.time == slot.time && it.part == slot.part })
            current.slots.size >= MAX_SLOTS -> current
            else -> current.copy(slots = (current.slots + slot).sortedWith(compareBy({ it.day }, { it.time ?: "" }, { SLOT_PARTS.indexOf(it.part) })))
        }
    }

    /** "Manca un negozio?": cerca dopo una breve pausa nella digitazione. */
    fun searchSpot(query: String) {
        val current = planner ?: return
        planner = current.copy(query = query, searchedEmpty = false)
        searchJob?.cancel()
        if (query.trim().length < 2) {
            planner = planner?.copy(searchResults = emptyList(), searching = false)
            return
        }
        searchJob = viewModelScope.launch {
            delay(SEARCH_DEBOUNCE_MS)
            planner = planner?.copy(searching = true)
            val results = (TradeApi.searchSpots(query.trim(), current.proposalId) as? TradeApi.Result.Ok)?.value?.results.orEmpty()
            planner = planner?.copy(searchResults = results, searching = false, searchedEmpty = results.isEmpty())
        }
    }

    /** Un luogo trovato con la ricerca entra fra i luoghi, gia' scelto. */
    fun addSearchedSpot(candidate: TradeSpotCandidate) {
        viewModelScope.launch {
            val result = TradeApi.addSpot(
                TradeAddSpotRequest(candidate.osmId, candidate.name.orEmpty(), candidate.kind, candidate.city, candidate.lat, candidate.lon)
            )
            (result as? TradeApi.Result.Ok)?.value?.spot?.let { spot -> takeSpot(spot.copy(distanceKm = candidate.distanceKm)) }
                ?: run { notice = problemOf(result) }
        }
    }

    /** Il negozio non c'e' nemmeno su OpenStreetMap: lo si segnala per nome, e lo approviamo noi. */
    fun reportSpot(name: String) {
        viewModelScope.launch {
            val result = TradeApi.addSpot(TradeAddSpotRequest(name = name.trim()))
            (result as? TradeApi.Result.Ok)?.value?.spot?.let { takeSpot(it); info = Info.SPOT_REPORTED }
                ?: run { notice = problemOf(result) }
        }
    }

    private fun takeSpot(spot: TradeSpot) {
        val current = planner ?: return
        planner = current.copy(
            spots = listOf(spot) + current.spots.filter { it.id != spot.id },
            selectedSpot = spot.id,
            query = "",
            searchResults = emptyList(),
            searchedEmpty = false
        )
    }

    fun sendPlanner() {
        val current = planner ?: return
        val spot = current.selectedSpot ?: return
        if (current.slots.isEmpty() || current.sending) return
        planner = current.copy(sending = true)
        viewModelScope.launch {
            val result = TradeApi.proposeMeeting(current.proposalId, TradeMeetingRequest(spot, current.slots))
            if (result is TradeApi.Result.Ok) {
                planner = null
                info = Info.MEETING_SENT
                refreshProposals()
            } else {
                notice = problemOf(result)
                planner = planner?.copy(sending = false)
            }
        }
    }

    /** L'altro ha proposto luogo e fasce: se ne sceglie una e l'appuntamento e' fissato. */
    fun confirmMeeting(proposalId: String, slotIndex: Int) {
        if (actingOn != null) return
        viewModelScope.launch {
            actingOn = proposalId
            val result = TradeApi.confirmMeeting(proposalId, slotIndex)
            actingOn = null
            if (result is TradeApi.Result.Ok) {
                info = Info.MEETING_CONFIRMED
                proposalsBucket = "MEETINGS"
            } else {
                notice = problemOf(result)
            }
            refreshProposals()
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
            reserved = serverHaves.filter { (it.reserved ?: 0) > 0 }
                .associate { listOf(it.key, it.variant, it.condition, it.language).joinToString("|") to (it.reserved ?: 0) }
            val serverNotify = serverHaves.filter { it.notify == true }
                .map { listOf(it.key, it.variant, it.condition, it.language).joinToString("|") }.toSet()
            notifyIds = singles.map { it.id }.filter { it in offers && it in serverNotify }.toSet()
            // Riallinea le quantita' (un doppione venduto non si offre piu').
            pushHaves()

            refreshMatches()
            refreshProposals()
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

    /** Identifica una carta offerta: stampa, condizione e lingua, come [TradeLists.Duplicate.id]. */
    fun offerId(item: TradeOfferItem): String =
        listOf(item.key.orEmpty(), item.variant.orEmpty(), item.condition.orEmpty(), item.language.orEmpty()).joinToString("|")

    private companion object {
        const val PREFS = "trade_radar"
        const val KEY_LEVELS_EXPLAINED = "levels_explained"
        const val PUSH_DEBOUNCE_MS = 600L
        const val SEARCH_DEBOUNCE_MS = 450L
        const val MAX_SLOTS = 3
        val SLOT_PARTS = listOf("morning", "afternoon", "evening")
    }

    private fun problemOf(result: TradeApi.Result<*>): Problem = when (result) {
        TradeApi.Result.Unauthorized -> Problem.UNAUTHORIZED
        is TradeApi.Result.Rejected -> Problem.REJECTED
        else -> Problem.UNAVAILABLE
    }
}

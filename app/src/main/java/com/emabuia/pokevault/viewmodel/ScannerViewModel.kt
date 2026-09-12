package com.emabuia.pokevault.viewmodel

import android.app.Application
import androidx.compose.runtime.getValue
import timber.log.Timber
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.emabuia.pokevault.data.firebase.FirestoreRepository
import com.emabuia.pokevault.data.model.PokemonCard
import com.emabuia.pokevault.data.model.ScannerMatcher
import com.emabuia.pokevault.data.remote.RepositoryProvider
import com.emabuia.pokevault.data.remote.SetCodeMapper
import com.emabuia.pokevault.data.remote.TcgCard
import com.emabuia.pokevault.ocr.CardFieldParser
import com.emabuia.pokevault.ocr.CardOCRResult
import com.emabuia.pokevault.ocr.CardReading
import com.emabuia.pokevault.ocr.ScanAggregator
import com.emabuia.pokevault.ocr.ScanConsensus
import com.emabuia.pokevault.ocr.ScannedFrame
import com.emabuia.pokevault.util.minimumEurPriceOrZero
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

/** Condizione di partenza: la grande maggioranza delle carte scansionate e' questa. */
private const val DEFAULT_CONDITION = "Near Mint"

data class ScannerUiState(
    val isSearching: Boolean = false,
    /** Carta trovata in attesa di conferma dall'utente */
    val pendingCard: TcgCard? = null,
    /** Candidati mostrati quando il match migliore non e abbastanza netto */
    val candidateCards: List<TcgCard> = emptyList(),
    /** Carta appena aggiunta (conferma visiva temporanea) */
    val lastAddedCard: TcgCard? = null,
    val addedCount: Int = 0,
    val errorMessage: String? = null,
    /** Suggerimento su come inquadrare, quando la lettura non basta */
    val hintMessage: String? = null,
    val flashEnabled: Boolean = false,
    /** Condizione applicata a tutto cio' che si scansiona, scelta prima di partire. */
    val condition: String = DEFAULT_CONDITION,
    /** Quando attiva, le carte certe entrano da sole senza toccare lo schermo. */
    val continuousMode: Boolean = false,
    val detectedName: String = "",
    /** ID letto in basso a sinistra, nella forma "67/87" */
    val detectedNumber: String = ""
)

/**
 * Orchestratore dello scanner: riceve i frame, decide quando i dati sono
 * abbastanza solidi per cercare, e porta in collezione la carta scelta.
 *
 * Le due parti delicate vivono fuori da qui, per poter essere messe sotto test
 * senza una camera e senza Firestore: [ScanAggregator] decide quando i frame
 * concordano, [ScannerMatcher] decide quale carta del catalogo corrisponde.
 */
class ScannerViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = RepositoryProvider.tcgRepository
    private val firestoreRepository = FirestoreRepository()
    private val appContext: Application get() = getApplication()
    private var searchJob: Job? = null

    /**
     * Carte scartate dall'utente per il numero che sta inquadrando adesso.
     *
     * Serve a "nessuna di queste", che deve proporre le tre successive. E' legato
     * al numero inquadrato, non alla sessione: uno scarto non puo' rendere una
     * carta introvabile per sempre. Per lo stesso motivo non esiste un elenco
     * delle carte aggiunte: di una carta si possono avere due copie e si
     * scansionano una dopo l'altra.
     */
    private val rejectedIds = mutableSetOf<String>()
    private var rejectionScope = ""
    private val recentSearchAttempts = mutableMapOf<String, Long>()
    private var lastSearchTimestamp = 0L

    /**
     * Chiave della ricerca attualmente in corso o completata.
     * Impedisce di rilanciare la stessa ricerca su ogni frame.
     */
    private var activeSearchKey = ""

    /**
     * Accumula le letture dei frame recenti e dice quando concordano: conta voti
     * in una finestra di tempo, non frame consecutivi, perche' un singolo frame
     * in cui l'ID non si legge non deve azzerare tutto.
     */
    private val aggregator = ScanAggregator()

    /** Istante della prima lettura senza ID, per il suggerimento di inquadratura. */
    private var firstFrameWithoutIdAt = 0L

    /** Frame consecutivi senza niente di leggibile davanti all'obiettivo. */
    private var emptyFrames = 0

    /** Ultima carta entrata da sola in modalita' continua, per non contarla due volte. */
    private var lastAutoAddedNumber = ""
    private var lastAutoAddedAt = 0L

    /** Avviso che deve restare leggibile per qualche frame, non lampeggiare. */
    private var stickyHint: String? = null
    private var stickyHintUntil = 0L

    var uiState by mutableStateOf(ScannerUiState())
        private set

    // ═══════════════════════════════════════════
    // FRAME DALLA CAMERA
    // ═══════════════════════════════════════════

    /**
     * Chiamata dalla camera a ogni frame analizzato.
     *
     * Il frame porta due letture: il testo dell'intera carta con le posizioni
     * dei blocchi, e il testo della striscia in basso ingrandita, da cui esce
     * l'ID. Quando abbastanza frame recenti concordano, parte la ricerca.
     */
    fun onFrameScanned(frame: ScannedFrame) {
        if (uiState.pendingCard != null || uiState.candidateCards.isNotEmpty() || uiState.lastAddedCard != null) return

        if (frame.isEmpty()) {
            viewModelScope.launch { onEmptyFrame() }
            return
        }

        // Il parsing resta sul thread dell'analyzer (e' gia' qui, e costa qualche
        // ms), ma lo stato si tocca solo dal main: viewModelScope usa
        // Dispatchers.Main, cosi' le scritture di uiState restano serializzate.
        val ocrResult = CardFieldParser.parseFrame(frame)
        viewModelScope.launch { onCardRead(ocrResult) }
    }

    /**
     * Davanti all'obiettivo non c'e' niente di leggibile: la carta e' stata
     * spostata. E' il momento giusto per ripartire da zero, ed e' l'unico modo
     * per riconoscere che una seconda copia della stessa carta e' una carta
     * nuova: il numero letto sarebbe identico, quindi nessun altro segnale
     * potrebbe distinguerle.
     */
    private fun onEmptyFrame() {
        emptyFrames++
        // Una volta sola per ogni passaggio a vuoto, non a ogni frame.
        if (emptyFrames != FRAMES_TO_REARM) return

        Timber.d("Obiettivo libero: scanner riarmato")
        lastAutoAddedNumber = ""
        lastAutoAddedAt = 0L
        resetStability()
        uiState = uiState.copy(detectedName = "", detectedNumber = "", hintMessage = null)
    }

    private fun onCardRead(ocrResult: CardOCRResult) {
        if (uiState.pendingCard != null || uiState.candidateCards.isNotEmpty() || uiState.lastAddedCard != null) return

        emptyFrames = 0

        val now = System.currentTimeMillis()
        aggregator.record(
            CardReading(
                number = ocrResult.cardNumber?.takeIf { it.isNotBlank() },
                setTotal = ocrResult.setTotal?.takeIf { it.isNotBlank() },
                name = ocrResult.cardName?.takeIf { ScannerMatcher.isUsableName(it) },
                setHint = (ocrResult.setCode ?: ocrResult.setName)?.takeIf { it.isNotBlank() },
                hp = ocrResult.hp,
                supertype = ocrResult.supertype
            ),
            nowMs = now
        )

        val consensus = aggregator.consensus(now) ?: return
        publishReadout(consensus = consensus, nowMs = now)
        syncRejectionScope(consensus.number)
        if (!consensus.isReady) return

        val searchKey = consensus.searchKey
        if (searchKey == activeSearchKey) return
        if (now - lastSearchTimestamp < SEARCH_MIN_INTERVAL_MS) return
        if (now - (recentSearchAttempts[searchKey] ?: 0L) < SEARCH_KEY_COOLDOWN_MS) return
        if (searchJob?.isActive == true) return

        Timber.d(
            "Consenso: ${consensus.displayId} ${consensus.name} " +
                "(id x${consensus.numberVotes}, nome x${consensus.nameVotes})"
        )

        activeSearchKey = searchKey
        recentSearchAttempts[searchKey] = now
        lastSearchTimestamp = now
        searchJob = viewModelScope.launch { searchCard(consensus) }
    }

    /**
     * Gli scarti valgono per la carta inquadrata: appena il numero cambia,
     * ricominciano da zero. Senza questo, scartare una carta la rendeva
     * introvabile per tutto il resto della sessione.
     */
    private fun syncRejectionScope(number: String?) {
        // Un numero che non si legge per un frame non e' un cambio di carta: senza
        // questa guardia, dopo "nessuna di queste" gli scarti sparivano al primo
        // frame sporco e tornavano in scena gli stessi tre candidati.
        val scope = number?.takeIf { it.isNotBlank() } ?: return
        if (scope == rejectionScope) return
        rejectedIds.clear()
        rejectionScope = scope
    }

    /**
     * Mostra il consenso, non la lettura del singolo frame: e' il motivo per cui
     * il nome non cambia a ogni fotogramma sotto la cornice. Se l'ID non si
     * legge per qualche secondo, dice anche come rimediare.
     */
    private fun publishReadout(consensus: ScanConsensus, nowMs: Long) {
        val missingId = aggregator.hasNoIdInWindow(nowMs)
        when {
            !missingId -> firstFrameWithoutIdAt = 0L
            firstFrameWithoutIdAt == 0L -> firstFrameWithoutIdAt = nowMs
        }

        val hint = when {
            nowMs < stickyHintUntil -> stickyHint
            missingId && nowMs - firstFrameWithoutIdAt >= HINT_AFTER_MS ->
                "Non leggo il numero in basso a sinistra: avvicina la carta e riempi la cornice."
            else -> null
        }

        uiState = uiState.copy(
            detectedName = consensus.name.orEmpty(),
            detectedNumber = consensus.displayId,
            hintMessage = hint
        )
    }

    // ═══════════════════════════════════════════
    // RICERCA NEL CATALOGO
    // ═══════════════════════════════════════════

    /**
     * Una sola ricerca nel catalogo ITA:
     *  - numero carta = filtro hard
     *  - totale set + set letto = disambiguazione dell'espansione
     *  - nome = conferma, mai bloccante
     *
     * Poi decide [ScannerMatcher]: un candidato che stacca gli altri si propone
     * da solo, in ogni altro caso si mostrano le tre carte piu' probabili.
     */
    private suspend fun searchCard(consensus: ScanConsensus) {
        uiState = uiState.copy(isSearching = true, errorMessage = null)

        try {
            val normalizedSetHint = consensus.setHint
                ?.let(SetCodeMapper::normalizeDecklistSetCode)
                ?.lowercase(Locale.ROOT)
                ?.takeIf { it.isNotBlank() }

            Timber.d(
                "Ricerca scanner ITA: name=${consensus.name} number=${consensus.number} " +
                    "total=${consensus.setTotal} set=$normalizedSetHint"
            )
            val candidates = repository.searchItalianScannerCandidates(
                name = consensus.name,
                number = consensus.number,
                setTotal = consensus.setTotal,
                targetSetId = normalizedSetHint,
                context = appContext,
                limit = CANDIDATE_POOL_SIZE
            ).getOrDefault(emptyList())

            val viable = candidates.filterNot { it.id in rejectedIds }
            if (viable.isEmpty()) {
                uiState = uiState.copy(
                    isSearching = false,
                    pendingCard = null,
                    candidateCards = emptyList(),
                    errorMessage = emptyResultMessage(consensus, hadCandidates = candidates.isNotEmpty())
                )
                activeSearchKey = ""
                return
            }

            val ranked = ScannerMatcher.rank(
                cards = viable,
                signals = ScannerMatcher.Signals(
                    name = consensus.name,
                    number = consensus.number,
                    setTotal = consensus.setTotal,
                    hp = consensus.hp,
                    supertype = consensus.supertype
                )
            )
            val top = ranked.first()
            val clearWinner = ScannerMatcher.hasClearWinner(ranked)

            Timber.d(
                "Scanner: top=${top.card.name} (${top.score}) " +
                    "second=${ranked.getOrNull(1)?.card?.name} netto=$clearWinner " +
                    "nome=${top.nameSimilarity} totaleEsatto=${top.totalExact}"
            )

            if (uiState.continuousMode && !ScannerMatcher.isCertain(ranked)) {
                // Se il continuo non scatta, si deve poter leggere perche: le due
                // condizioni sono queste, e il log dice quale e mancata.
                Timber.d(
                    "Continuo: chiedo conferma (netto=$clearWinner, " +
                        "nome=${top.nameSimilarity} serve >= 0.80)"
                )
            }

            // Modalita' continua: la carta entra da sola, ma solo col verdetto di
            // prima qualita'. Tutto cio' che e' meno di questo torna a passare
            // dalle mani dell'utente: il continuo risparmia tocchi, non precisione.
            if (uiState.continuousMode && ScannerMatcher.isCertain(ranked)) {
                if (!canAutoAddNow(consensus.number)) {
                    // La carta e' ancora davanti all'obiettivo. Non la conto due
                    // volte, ma non interrompo nemmeno la scansione con una proposta
                    // che l'utente dovrebbe scartare a mano: basta dirglielo.
                    showStickyHint("Già aggiunta: passa alla carta successiva.")
                    uiState = uiState.copy(
                        isSearching = false,
                        pendingCard = null,
                        candidateCards = emptyList(),
                        errorMessage = null,
                        hintMessage = stickyHint
                    )
                    return
                }

                Timber.d("Continuo: aggiungo ${top.card.name} senza conferma")
                lastAutoAddedNumber = consensus.number.orEmpty()
                lastAutoAddedAt = System.currentTimeMillis()
                uiState = uiState.copy(
                    isSearching = true,
                    pendingCard = null,
                    candidateCards = emptyList(),
                    errorMessage = null,
                    hintMessage = null
                )
                addToFirestore(top.card)
                return
            }

            uiState = if (clearWinner) {
                uiState.copy(
                    isSearching = false,
                    pendingCard = top.card,
                    candidateCards = emptyList(),
                    errorMessage = null,
                    hintMessage = null
                )
            } else {
                uiState.copy(
                    isSearching = false,
                    pendingCard = null,
                    candidateCards = ranked.take(ScannerMatcher.MAX_CANDIDATES).map { it.card },
                    errorMessage = null,
                    hintMessage = null
                )
            }
        } catch (e: Exception) {
            Timber.w("Search failed: ${e.message}")
            uiState = uiState.copy(
                isSearching = false,
                errorMessage = "Errore ricerca: ${e.message}"
            )
            activeSearchKey = ""
        }
    }

    /** Messaggio che dice cosa manca, non solo che non ha trovato niente. */
    private fun emptyResultMessage(consensus: ScanConsensus, hadCandidates: Boolean): String {
        return when {
            hadCandidates -> "Ho finito le carte da proporre per questo numero."
            consensus.number == null -> "Non riesco a leggere il numero in basso a sinistra. Avvicina la carta."
            consensus.setTotal == null -> "Numero ${consensus.number} letto, ma non il totale del set. Avvicina la carta."
            else -> "Nessuna carta ${consensus.number}/${consensus.setTotal} nel catalogo italiano."
        }
    }

    // ═══════════════════════════════════════════
    // CONFERMA / SCARTA
    // ═══════════════════════════════════════════

    fun confirmAdd() {
        val card = uiState.pendingCard ?: return
        uiState = uiState.copy(pendingCard = null, candidateCards = emptyList(), isSearching = true)
        viewModelScope.launch { addToFirestore(card) }
    }

    fun selectCandidate(card: TcgCard) {
        uiState = uiState.copy(
            pendingCard = card,
            candidateCards = emptyList(),
            errorMessage = null
        )
    }

    /**
     * "Nessuna di queste": le carte mostrate escono di scena per il numero
     * inquadrato e la stessa inquadratura viene ricercata di nuovo, cosi' la
     * rosa successiva propone le tre carte che vengono dopo.
     */
    fun dismissCard() {
        rejectedIds += uiState.candidateCards.map { it.id }
        uiState.pendingCard?.let { rejectedIds += it.id }

        uiState = uiState.copy(
            pendingCard = null,
            candidateCards = emptyList(),
            detectedName = "",
            detectedNumber = "",
            errorMessage = null,
            hintMessage = null
        )
        resetStability()
    }

    // ═══════════════════════════════════════════
    // SALVATAGGIO
    // ═══════════════════════════════════════════

    private suspend fun addToFirestore(tcgCard: TcgCard) {
        val resolvedCard = repository.getCard(tcgCard.id, preferNetwork = true).getOrNull() ?: tcgCard
        val price = resolvedCard.cardmarket?.prices.minimumEurPriceOrZero()

        val pokemonCard = PokemonCard(
            name = resolvedCard.name,
            imageUrl = resolvedCard.images.large.ifBlank { resolvedCard.images.small },
            set = resolvedCard.set?.name ?: "",
            rarity = resolvedCard.rarity ?: "",
            type = resolvedCard.types?.firstOrNull() ?: resolvedCard.supertype,
            hp = resolvedCard.hp?.toIntOrNull() ?: 0,
            // Senza questi due, ogni carta scansionata entrava in collezione come
            // Pokemon: CardClassifier si basa su supertype/subtypes, quindi gli
            // Allenatori e le Energie finivano nella categoria sbagliata in
            // statistiche e filtri.
            supertype = resolvedCard.supertype.ifBlank { "Pokémon" },
            subtypes = resolvedCard.subtypes.orEmpty(),
            estimatedValue = price,
            quantity = 1,
            condition = uiState.condition,
            apiCardId = resolvedCard.id,
            cardNumber = resolvedCard.number
        )

        // La quantita' non si somma qui: addCard riconosce la carta gia' in
        // collezione (stesso apiCardId, variante e lingua) e incrementa la riga.
        firestoreRepository.addCard(pokemonCard)
            .onSuccess {
                uiState = uiState.copy(
                    isSearching = false,
                    lastAddedCard = tcgCard,
                    addedCount = uiState.addedCount + 1,
                    errorMessage = null
                )
                val addedCardId = tcgCard.id
                // In continuo il banner e' solo un riscontro di passaggio: tenerlo
                // 2,5 secondi vorrebbe dire una carta ogni tre secondi.
                val bannerMs = if (uiState.continuousMode) CONTINUOUS_BANNER_MS else ADDED_BANNER_MS
                viewModelScope.launch {
                    delay(bannerMs)
                    if (uiState.lastAddedCard?.id == addedCardId) {
                        uiState = uiState.copy(
                            lastAddedCard = null,
                            detectedName = "",
                            detectedNumber = ""
                        )
                        resetStability()
                    }
                }
            }
            .onFailure { error ->
                uiState = uiState.copy(
                    isSearching = false,
                    errorMessage = "Errore salvataggio: ${error.message}"
                )
                resetStability()
            }
    }

    // ═══════════════════════════════════════════
    // PREFERENZE E RESET
    // ═══════════════════════════════════════════

    private fun resetStability() {
        activeSearchKey = ""
        aggregator.reset()
        firstFrameWithoutIdAt = 0L
        recentSearchAttempts.clear()
    }

    /**
     * In continuo manca la protezione che il tocco dava gratis: una carta
     * lasciata davanti all'obiettivo non deve entrare due volte. Una carta
     * diversa passa subito; la stessa va riproposta solo dopo una pausa, che
     * nella pratica vuol dire "l'hai davvero sostituita con una seconda copia".
     */
    private fun canAutoAddNow(number: String?): Boolean {
        if (number.orEmpty() != lastAutoAddedNumber) return true
        return System.currentTimeMillis() - lastAutoAddedAt >= CONTINUOUS_SAME_CARD_MS
    }

    private fun showStickyHint(message: String) {
        stickyHint = message
        stickyHintUntil = System.currentTimeMillis() + STICKY_HINT_MS
    }

    fun toggleFlash() {
        uiState = uiState.copy(flashEnabled = !uiState.flashEnabled)
    }

    fun setCondition(condition: String) {
        uiState = uiState.copy(condition = condition)
    }

    fun toggleContinuousMode() {
        uiState = uiState.copy(continuousMode = !uiState.continuousMode)
    }

    fun resetScanner() {
        rejectedIds.clear()
        rejectionScope = ""
        searchJob?.cancel()
        resetStability()
        lastAutoAddedNumber = ""
        lastAutoAddedAt = 0L
        emptyFrames = 0
        uiState = ScannerUiState(
            flashEnabled = uiState.flashEnabled,
            condition = uiState.condition,
            continuousMode = uiState.continuousMode,
            addedCount = uiState.addedCount
        )
    }

    fun clearError() {
        uiState = uiState.copy(errorMessage = null)
    }

    companion object {
        /** Dopo quanto, senza mai leggere l'ID, si suggerisce di avvicinare la carta. */
        private const val HINT_AFTER_MS = 2_500L

        /** Durata di un avviso puntuale, abbastanza da leggerlo. */
        private const val STICKY_HINT_MS = 1_800L

        /** Frame a vuoto dopo i quali si considera che la carta sia stata spostata. */
        private const val FRAMES_TO_REARM = 2

        /** Quante carte chiedere al catalogo: piu' di quante se ne mostrino. */
        private const val CANDIDATE_POOL_SIZE = 10

        /** Quanto deve passare prima che la stessa carta possa rientrare da sola. */
        private const val CONTINUOUS_SAME_CARD_MS = 3_000L

        private const val ADDED_BANNER_MS = 2_500L
        private const val CONTINUOUS_BANNER_MS = 1_100L

        private const val SEARCH_MIN_INTERVAL_MS = 1_200L
        private const val SEARCH_KEY_COOLDOWN_MS = 6_000L
    }
}

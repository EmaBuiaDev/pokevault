package com.emabuia.pokevault.viewmodel

import android.app.Application
import androidx.compose.runtime.getValue
import timber.log.Timber
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.emabuia.pokevault.data.firebase.FirestoreRepository
import com.emabuia.pokevault.data.model.CardOptions
import com.emabuia.pokevault.data.model.PokemonCard
import com.emabuia.pokevault.data.model.ScanRejections
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
import com.emabuia.pokevault.util.AppLocale
import com.emabuia.pokevault.util.minimumEurPriceOrZero
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

/** Condizione di partenza: la grande maggioranza delle carte scansionate e' questa. */
private const val DEFAULT_CONDITION = "Near Mint"

/**
 * I quattro tempi dello scanner, dal punto di vista di chi guarda.
 *
 * Non e' uno stato in piu' da tenere allineato: si ricava da [ScannerUiState]
 * (vedi [ScannerUiState.scanState]), che resta l'unica verita'. Serve a far
 * decidere all'interfaccia *una* cosa sola invece di combinare tre booleani in
 * ogni punto in cui deve cambiare aspetto.
 *
 * [RECOGNIZED] e' l'unico che la pipeline non produce da sola: e' il mezzo
 * secondo di conferma fra la lettura e il risultato, e lo inserisce la
 * schermata quando vede arrivare [RESULT].
 */
enum class ScanState {
    /** Nessun fermo immagine: si sta ancora cercando la carta nel riquadro. */
    FRAMING,

    /** OCR in corso sul testo letto. */
    READING,

    /** Carta riconosciuta: mezzo secondo di conferma prima del risultato. */
    RECOGNIZED,

    /** C'e' una carta da confermare, da scegliere, o appena aggiunta. */
    RESULT
}

/**
 * L'ultima azione che si puo' annullare.
 *
 * Una sola alla volta, la piu' recente: due "Annulla" sullo schermo insieme
 * farebbero chiedere quale dei due tocca cosa.
 */
sealed interface ScannerUndo {
    val id: Long

    /** Carta o rosa scartata: si rimette sullo schermo com'era. */
    data class Dismissed(
        override val id: Long,
        val pendingCard: TcgCard?,
        val candidates: List<TcgCard>
    ) : ScannerUndo

    /** Carta aggiunta: se ne toglie la copia appena entrata. */
    data class Added(
        override val id: Long,
        val card: TcgCard,
        val docId: String
    ) : ScannerUndo
}

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
    val detectedNumber: String = "",
    /** L'ultima azione annullabile, finche' e' ancora in tempo. */
    val undo: ScannerUndo? = null,
    /**
     * La ricerca non ha trovato niente da proporre: e' il momento di offrire la
     * ricerca a mano, invece di lasciare l'utente davanti a un messaggio.
     */
    val notFound: Boolean = false,
    /** Si sono scartate tutte le carte proponibili: si possono riproporre. */
    val canRetryRejected: Boolean = false
) {
    /**
     * Il tempo in cui si trova lo scanner adesso.
     *
     * Derivato e non memorizzato: un campo in piu' vorrebbe dire aggiornarlo in
     * ognuno dei punti in cui la pipeline scrive nello stato, e prima o poi
     * uno resterebbe indietro.
     */
    val scanState: ScanState
        get() = when {
            pendingCard != null || candidateCards.isNotEmpty() || lastAddedCard != null -> ScanState.RESULT
            isSearching -> ScanState.READING
            else -> ScanState.FRAMING
        }
}

/**
 * Orchestratore dello scanner: riceve i frame, decide quando i dati sono
 * abbastanza solidi per cercare, e porta in collezione la carta scelta.
 *
 * Le parti delicate vivono fuori da qui, per poter essere messe sotto test
 * senza una camera e senza Firestore: [ScanAggregator] decide quando i frame
 * concordano, [ScannerMatcher] decide quale carta del catalogo corrisponde,
 * [ScanRejections] ricorda cosa e' stato scartato e per quanto.
 */
class ScannerViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = RepositoryProvider.tcgRepository
    private val firestoreRepository = FirestoreRepository()
    private val appContext: Application get() = getApplication()
    private var searchJob: Job? = null
    private var undoJob: Job? = null
    private var undoCounter = 0L

    /**
     * Carte scartate dall'utente per la carta che sta inquadrando adesso. Non
     * esiste un elenco delle carte aggiunte: di una carta si possono avere due
     * copie e si scansionano una dopo l'altra.
     */
    private val rejections = ScanRejections()
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
     *
     * Per la stessa ragione qui si dimenticano gli scarti: rimettere davanti la
     * carta dopo averla scartata per sbaglio deve bastare a ritrovarla.
     */
    private fun onEmptyFrame() {
        emptyFrames++
        // Una volta sola per ogni passaggio a vuoto, non a ogni frame.
        if (emptyFrames != FRAMES_TO_REARM) return

        Timber.d("Obiettivo libero: scanner riarmato")
        lastAutoAddedNumber = ""
        lastAutoAddedAt = 0L
        rejections.onCardRemoved()
        resetStability()
        // Anche l'errore della carta di prima: restava sotto la cornice mentre
        // si inquadrava gia' la successiva.
        uiState = uiState.copy(
            detectedName = "",
            detectedNumber = "",
            hintMessage = null,
            errorMessage = null,
            notFound = false,
            canRetryRejected = false
        )
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
        rejections.onNumberRead(consensus.number)
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
            missingId && nowMs - firstFrameWithoutIdAt >= HINT_AFTER_MS -> AppLocale.scannerHintMissingId
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
        uiState = uiState.copy(isSearching = true, errorMessage = null, notFound = false, canRetryRejected = false)

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

            val viable = rejections.viable(candidates) { it.id }
            if (viable.isEmpty()) {
                val allRejected = candidates.isNotEmpty()
                uiState = uiState.copy(
                    isSearching = false,
                    pendingCard = null,
                    candidateCards = emptyList(),
                    errorMessage = emptyResultMessage(consensus, hadCandidates = allRejected),
                    notFound = true,
                    canRetryRejected = allRejected
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
                    showStickyHint(AppLocale.scannerAlreadyAdded)
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
                addToFirestore(top.card, defaultVariant(top.card))
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
                errorMessage = AppLocale.scannerSearchError(e.message.orEmpty()),
                notFound = true
            )
            activeSearchKey = ""
        }
    }

    /** Messaggio che dice cosa manca, non solo che non ha trovato niente. */
    private fun emptyResultMessage(consensus: ScanConsensus, hadCandidates: Boolean): String {
        return when {
            hadCandidates -> AppLocale.scannerAllRejected
            consensus.number == null -> AppLocale.scannerHintMissingId
            consensus.setTotal == null -> AppLocale.scannerMissingTotal(consensus.number)
            else -> AppLocale.scannerNotInCatalog(consensus.number, consensus.setTotal)
        }
    }

    // ═══════════════════════════════════════════
    // CONFERMA / SCARTA / ANNULLA
    // ═══════════════════════════════════════════

    /**
     * Le stampe di una carta, nell'ordine in cui proporle. La prima e' quella
     * che si usa quando non si sceglie: per una rara holo e' la Holo.
     *
     * Prima lo scanner non impostava mai la stampa, e ogni carta entrava come
     * "Normale" -- anche un'Illustrazione Rara, che normale non esiste.
     */
    fun variantsFor(card: TcgCard): List<String> {
        val priceKeys = card.tcgplayer?.prices?.keys ?: emptySet()
        // Senza rarita' e senza prezzi, CardOptions risponde "Holo": per una
        // carta di cui non sappiamo niente e' una stampa inventata. Qui si
        // resta su "Normale", che e' quello che lo scanner salvava da sempre.
        if (card.rarity.isNullOrBlank() && priceKeys.isEmpty()) return listOf(FALLBACK_VARIANT)
        return CardOptions.getVariantsForCard(priceKeys, card.rarity, card.set?.releaseDate)
            .ifEmpty { listOf(FALLBACK_VARIANT) }
    }

    private fun defaultVariant(card: TcgCard): String = variantsFor(card).first()

    fun confirmAdd(variant: String? = null) {
        val card = uiState.pendingCard ?: return
        val chosen = variant?.takeIf { it.isNotBlank() } ?: defaultVariant(card)
        uiState = uiState.copy(pendingCard = null, candidateCards = emptyList(), isSearching = true)
        viewModelScope.launch { addToFirestore(card, chosen) }
    }

    fun selectCandidate(card: TcgCard) {
        uiState = uiState.copy(
            pendingCard = card,
            candidateCards = emptyList(),
            errorMessage = null
        )
    }

    /**
     * "Scarta" e "Nessuna di queste": le carte mostrate escono di scena finche'
     * la carta resta inquadrata, e la stessa inquadratura viene ricercata di
     * nuovo, cosi' la rosa successiva propone le carte che vengono dopo.
     *
     * Lo scarto si puo' annullare per qualche secondo: un tocco sbagliato non
     * deve costare la carta.
     */
    fun dismissCard() {
        val pending = uiState.pendingCard
        val candidates = uiState.candidateCards
        rejections.reject(candidates.map { it.id } + listOfNotNull(pending?.id))

        uiState = uiState.copy(
            pendingCard = null,
            candidateCards = emptyList(),
            detectedName = "",
            detectedNumber = "",
            errorMessage = null,
            hintMessage = null
        )
        if (pending != null || candidates.isNotEmpty()) {
            offerUndo(ScannerUndo.Dismissed(nextUndoId(), pending, candidates))
        }
        resetStability()
    }

    /** Rimette sullo schermo quello che era stato scartato, e lo rende di nuovo proponibile. */
    fun undoDismiss() {
        val undo = uiState.undo as? ScannerUndo.Dismissed ?: return
        rejections.undoLast()
        searchJob?.cancel()
        undoJob?.cancel()
        resetStability()
        uiState = uiState.copy(
            isSearching = false,
            pendingCard = undo.pendingCard,
            candidateCards = if (undo.pendingCard == null) undo.candidates else emptyList(),
            lastAddedCard = null,
            errorMessage = null,
            hintMessage = null,
            notFound = false,
            canRetryRejected = false,
            undo = null
        )
    }

    /**
     * Toglie la copia appena aggiunta.
     *
     * La carta viene anche scartata finche' resta inquadrata: in modalita'
     * continua, annullare una carta entrata per errore mentre e' ancora davanti
     * all'obiettivo la farebbe rientrare da sola tre secondi dopo.
     */
    fun undoLastAdd() {
        val undo = uiState.undo as? ScannerUndo.Added ?: return
        undoJob?.cancel()
        uiState = uiState.copy(undo = null)
        viewModelScope.launch {
            firestoreRepository.removeOneCopy(undo.docId)
                .onSuccess {
                    rejections.reject(listOf(undo.card.id))
                    showStickyHint(AppLocale.scannerAddUndone(undo.card.name))
                    uiState = uiState.copy(
                        addedCount = (uiState.addedCount - 1).coerceAtLeast(0),
                        lastAddedCard = if (uiState.lastAddedCard?.id == undo.card.id) null else uiState.lastAddedCard,
                        hintMessage = stickyHint
                    )
                    resetStability()
                }
                .onFailure { error ->
                    uiState = uiState.copy(errorMessage = AppLocale.scannerUndoFailed(error.message.orEmpty()))
                }
        }
    }

    /** "Riproponi tutte": dopo averle scartate tutte, si ricomincia da capo. */
    fun retryRejected() {
        rejections.clear()
        resetStability()
        uiState = uiState.copy(errorMessage = null, notFound = false, canRetryRejected = false)
    }

    private fun nextUndoId(): Long = ++undoCounter

    private fun offerUndo(undo: ScannerUndo) {
        undoJob?.cancel()
        uiState = uiState.copy(undo = undo)
        undoJob = viewModelScope.launch {
            delay(UNDO_WINDOW_MS)
            if (uiState.undo?.id == undo.id) uiState = uiState.copy(undo = null)
        }
    }

    fun dismissUndo() {
        undoJob?.cancel()
        uiState = uiState.copy(undo = null)
    }

    // ═══════════════════════════════════════════
    // SALVATAGGIO
    // ═══════════════════════════════════════════

    private suspend fun addToFirestore(tcgCard: TcgCard, variant: String) {
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
            cardNumber = resolvedCard.number,
            variant = variant
        )

        // La quantita' non si somma qui: addCard riconosce la carta gia' in
        // collezione (stesso apiCardId, variante e lingua) e incrementa la riga.
        firestoreRepository.addCard(pokemonCard)
            .onSuccess { docId ->
                uiState = uiState.copy(
                    isSearching = false,
                    lastAddedCard = tcgCard,
                    addedCount = uiState.addedCount + 1,
                    errorMessage = null
                )
                offerUndo(ScannerUndo.Added(nextUndoId(), tcgCard, docId))
                val addedCardId = tcgCard.id
                // In continuo il banner e' solo un riscontro di passaggio: tenerlo
                // 2,5 secondi vorrebbe dire una carta ogni tre secondi. L'"Annulla"
                // invece resta di piu', in alto, dove non copre la carta dopo.
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
                    errorMessage = AppLocale.scannerSaveError(error.message.orEmpty())
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
        rejections.clear()
        searchJob?.cancel()
        undoJob?.cancel()
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

        /** Quanto resta disponibile "Annulla". */
        private const val UNDO_WINDOW_MS = 6_000L

        /** La stampa di prima, quando non se ne conosce nessuna. */
        private const val FALLBACK_VARIANT = "Normal"

        private const val SEARCH_MIN_INTERVAL_MS = 1_200L
        private const val SEARCH_KEY_COOLDOWN_MS = 6_000L
    }
}

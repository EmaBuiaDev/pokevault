package com.emabuia.pokevault.viewmodel

import android.content.Context
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.emabuia.pokevault.data.firebase.FirestoreRepository
import com.emabuia.pokevault.data.model.BasicEnergyResolver
import com.emabuia.pokevault.data.model.CardClassifier
import com.emabuia.pokevault.data.model.Deck
import com.emabuia.pokevault.data.model.DeckAnalysis
import com.emabuia.pokevault.data.model.DeckImportParser
import com.emabuia.pokevault.data.model.MetaDeck
import com.emabuia.pokevault.data.model.MetaDeckCard
import com.emabuia.pokevault.data.model.PokemonCard
import com.emabuia.pokevault.data.remote.RepositoryProvider
import com.emabuia.pokevault.data.remote.SetCodeMapper
import com.emabuia.pokevault.data.remote.TcgCard
import com.emabuia.pokevault.util.minimumEurPriceOrZero
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

class DeckLabViewModel : ViewModel() {
    private val repository = FirestoreRepository()
    private val pokeTcgRepository = RepositoryProvider.tcgRepository
    private val pokeWalletRepository = RepositoryProvider.pokeWalletRepository

    companion object {
        private val legacyClassificationBackfillStarted = java.util.concurrent.atomic.AtomicBoolean(false)
        private val cardStageBackfillStarted = java.util.concurrent.atomic.AtomicBoolean(false)

        /** Prefisso di apiCardId per le carte che vengono dal catalogo italiano. */
        private const val ITALIAN_CARD_ID_PREFIX = "ita:"

        /** Quanto aspettare la prima emissione del listener carte prima di lasciar perdere. */
        private const val STAGE_BACKFILL_WAIT_MS = 15_000L
    }

    var decks by mutableStateOf<List<Deck>>(emptyList())
        private set

    /**
     * Tutto quello che un deck puo' contenere: collezione + carte solo-deck.
     *
     * Serve per *risolvere* gli id dentro ai deck, mai per dire cosa l'utente
     * possiede. Per quello c'e' [ownedCards], che e' l'unica lista che il
     * selettore delle carte e i conteggi di disponibilita' devono vedere.
     */
    var allCards by mutableStateOf<List<PokemonCard>>(emptyList())
        private set

    /** Le sole carte possedute davvero. */
    val ownedCards: List<PokemonCard> by derivedStateOf {
        allCards.filter { !it.deckOnly }
    }

    /** Indice per id su [allCards]: un deck di prova contiene anche carte non possedute. */
    private val allCardsById by derivedStateOf {
        allCards.associateBy { it.id }
    }

    var isLoading by mutableStateOf(false)
        private set

    var isSaving by mutableStateOf(false)
        private set

    // New/Edit Deck State
    var editingDeckId by mutableStateOf<String?>(null)
    var newDeckName by mutableStateOf("")
    var selectedCardsIds by mutableStateOf<List<String>>(emptyList())
    var coverImageUrl by mutableStateOf("")
    var coverImageUrls by mutableStateOf<List<String>>(emptyList())
    var currentAnalysis by mutableStateOf(DeckAnalysis())
    var validationError by mutableStateOf<String?>(null)
        private set

    var isImportReviewMode by mutableStateOf(false)
        private set

    /**
     * Che fine fanno le carte che il deck usa e l'utente non possiede.
     *
     * E' una proprieta' della sessione di modifica, non del singolo import:
     * vale anche per le carte pescate da "Cerca nei set", che prima finivano
     * in collezione senza chiedere niente a nessuno.
     */
    enum class DeckCardSource {
        /** Le carte mancanti entrano in collezione: il deck e' fatto di carte tue. */
        COLLECTION,

        /** Le carte mancanti restano dentro al deck: deck di prova. */
        DECK_ONLY
    }

    var deckCardSource by mutableStateOf(DeckCardSource.COLLECTION)
        private set

    /**
     * L'utente ha scelto, prima ancora di aprire l'editor.
     *
     * Vale per il deck creato da zero: la domanda si fa una volta all'inizio
     * invece di tenere un selettore acceso in cima al pannello per tutto il
     * tempo. Per l'import c'e' [applyImportCardSource], che oltre a scegliere
     * deve anche materializzare le carte mancanti.
     */
    fun chooseDeckCardSource(source: DeckCardSource) {
        deckCardSource = source
    }

    /**
     * L'import ha trovato delle carte mancanti e aspetta che l'utente dica
     * cosa farne. Finche' e' true, al posto del risultato si mostra la scelta.
     */
    var isImportSourceChoicePending by mutableStateOf(false)
        private set

    /**
     * Gli id delle carte solo-deck create durante questa sessione di modifica.
     *
     * Li teniamo a parte perche' [allCards] arriva da uno snapshot listener:
     * fra la scrittura e l'emissione c'e' un istante in cui una carta appena
     * creata non e' ancora in lista, e in quell'istante il deck sembrerebbe
     * fatto di sole carte possedute.
     */
    private var sessionDeckOnlyCardIds by mutableStateOf<Set<String>>(emptySet())

    /** Il deck in modifica contiene almeno una carta non posseduta. */
    val editingDeckHasDeckOnlyCards: Boolean by derivedStateOf {
        selectedCardsIds.any { it in sessionDeckOnlyCardIds || allCardsById[it]?.deckOnly == true }
    }

    /**
     * Le carte aggiunte in collezione che il catalogo italiano non conosce.
     *
     * Entrano senza immagine e con i soli dati della decklist. Prima succedeva
     * in silenzio, e l'utente se le ritrovava fra le proprie carte come
     * rettangoli vuoti senza sapere da dove venissero.
     */
    var importPlaceholderNames by mutableStateOf<List<String>>(emptyList())
        private set

    // Card search in TCG sets (for "Cerca nei set")
    var isSearchingCards by mutableStateOf(false)
        private set
    var tcgSearchResults by mutableStateOf<List<TcgCard>>(emptyList())
        private set
    var tcgSearchError by mutableStateOf<String?>(null)
        private set

    // Optimized map for quick lookups during UI rendering.
    // Su allCards: mappa gli id che stanno nel deck, non quelli posseduti.
    private val cardIdToKeyMap by derivedStateOf {
        allCards.associate { it.id to getCardKey(it) }
    }

    /** Le carte solo-deck che stanno nel deck attualmente in modifica. */
    private val deckOnlyCardsInDeck: List<PokemonCard> by derivedStateOf {
        selectedCardsIds.distinct()
            .mapNotNull { allCardsById[it] }
            .filter { it.deckOnly }
    }

    /**
     * Quello che il selettore carte puo' offrire a questo deck.
     *
     * Per un deck normale e' esattamente la collezione, quindi niente cambia.
     * Per un deck di prova ci sono anche le sue carte solo-deck: senza,
     * sparirebbero dalla griglia appena aggiunte, e l'utente non avrebbe modo
     * di toglierle o rimetterle.
     */
    val deckUsableCards: List<PokemonCard> by derivedStateOf {
        val extra = deckOnlyCardsInDeck
        // La stessa lista, non una copia, quando non c'e' niente da
        // aggiungere: per un deck normale chi dipende da qui non si
        // invalida a ogni carta aggiunta o tolta.
        if (extra.isEmpty()) ownedCards else ownedCards + extra
    }

    // I due indici sono tenuti separati di proposito.
    //
    // Quello della collezione e' il pesante -- una chiave costruita per ogni
    // carta posseduta -- e cambia solo quando cambia la collezione. Quello
    // delle carte solo-deck e' lungo al massimo sessanta elementi e cambia a
    // ogni carta aggiunta o tolta. Calcolandoli insieme sul totale, ogni
    // singolo tocco ri-raggruppava anche tutta la collezione.

    /** Documenti posseduti per chiave carta. */
    private val ownedDocsByKey by derivedStateOf {
        ownedCards.groupBy { getCardKey(it) }
    }

    /** Documenti solo-deck del deck in modifica, per chiave carta. */
    private val deckOnlyDocsByKey by derivedStateOf {
        deckOnlyCardsInDeck.groupBy { getCardKey(it) }
    }

    /**
     * Copie possedute per chiave carta.
     *
     * getTotalOwnedQuantity filtrava l'intera lista posseduta costruendo una
     * stringa chiave per ogni elemento, e viene chiamata dentro gli item della
     * griglia del selettore: una volta per ogni cella visibile a ogni frame
     * durante lo scorrimento.
     */
    private val ownedQuantitiesByKey by derivedStateOf {
        ownedCards.groupingBy { getCardKey(it) }
            .fold(0) { acc, card -> acc + card.quantity }
    }

    /** Copie solo-deck per chiave carta. */
    private val deckOnlyQuantitiesByKey by derivedStateOf {
        deckOnlyCardsInDeck.groupingBy { getCardKey(it) }
            .fold(0) { acc, card -> acc + card.quantity }
    }

    // Counts of each card key currently in the deck
    private val deckQuantitiesByKey by derivedStateOf {
        selectedCardsIds.mapNotNull { cardIdToKeyMap[it] }
            .groupingBy { it }
            .eachCount()
    }

    init {
        runLegacyClassificationBackfillOnce()
        loadDecks()
        loadOwnedCards()
    }

    private fun runLegacyClassificationBackfillOnce() {
        if (!legacyClassificationBackfillStarted.compareAndSet(false, true)) return
        viewModelScope.launch {
            repository.backfillLegacyCardClassificationMetadata()
        }
    }

    /**
     * Riempie lo stadio mancante sulle carte gia' in collezione.
     *
     * Le carte importate prima che il catalogo avesse la colonna `stage`
     * (schema/009) sono in Firestore con `subtypes` vuoto: l'Hand-Simulator le
     * conta tutte come Base, quindi una Fase 1 in mano risulta giocabile e il
     * tasso di mulligan esce ottimista. Reimportare il mazzo non e' una
     * risposta accettabile per l'utente, e il dato serve una volta sola:
     * leggiamo lo stadio dal catalogo (gia' in cache, nessuna chiamata in piu')
     * e lo scriviamo sui documenti che ne sono privi.
     *
     * Scrive un campo solo, e solo dove manca: non tocca ne' quantita' ne'
     * valore, e una carta che lo stadio ce l'ha gia' non viene riscritta.
     */
    fun ensureCardStagesFromCatalog(context: Context) {
        if (!cardStageBackfillStarted.compareAndSet(false, true)) return
        val appContext = context.applicationContext
        viewModelScope.launch {
            // Le carte arrivano da un listener Firestore: alla prima
            // composizione la lista e' ancora vuota. Il timeout evita che una
            // collezione davvero vuota lasci qui una coroutine in attesa per
            // tutta la vita del ViewModel.
            // Anche le carte solo-deck: sono proprio quelle che l'Hand
            // Simulator deve valutare in un deck di prova.
            val cards = withTimeoutOrNull(STAGE_BACKFILL_WAIT_MS) {
                snapshotFlow { allCards }.first { it.isNotEmpty() }
            } ?: run {
                cardStageBackfillStarted.set(false)
                return@launch
            }

            val targets = cards.filter {
                it.subtypes.isEmpty() &&
                    it.apiCardId.startsWith(ITALIAN_CARD_ID_PREFIX) &&
                    CardClassifier.classify(it) == CardClassifier.POKEMON
            }
            if (targets.isEmpty()) return@launch

            val stages = pokeTcgRepository.italianStagesByCardId(
                context = appContext,
                cardIds = targets.mapTo(mutableSetOf()) { it.apiCardId }
            )
            if (stages.isEmpty()) {
                // Il catalogo non ha (ancora) lo stadio di queste carte, o non era
                // raggiungibile: non e' un tentativo consumato, si riprova alla
                // prossima apertura della schermata.
                cardStageBackfillStarted.set(false)
                return@launch
            }

            targets.forEach { card ->
                val stage = stages[card.apiCardId] ?: return@forEach
                repository.updateCardSubtypes(card.id, listOf(stage))
            }
        }
    }

    private fun loadDecks() {
        viewModelScope.launch {
            repository.getDecks().collectLatest {
                decks = it
            }
        }
    }

    private fun loadOwnedCards() {
        viewModelScope.launch {
            // Qui, e solo qui in tutta l'app, servono anche le carte solo-deck:
            // senza, i deck di prova mostrerebbero dei buchi al posto delle
            // carte che l'utente ha scelto di non mettere in collezione.
            repository.getCardsIncludingDeckOnly().collectLatest { cards ->
                allCards = cards.sortedWith(
                    compareBy<PokemonCard> { 
                        val category = classifyCard(it)
                        when (category) {
                            "Pokémon" -> 0
                            "Trainer" -> 1
                            "Energy" -> 2
                            else -> 3
                        }
                    }.thenBy { it.name }
                )
            }
        }
    }

    fun getCardKey(card: PokemonCard): String =
        card.apiCardId.ifEmpty { "${card.name}-${card.set}-${card.cardNumber}-${card.variant}" }

    fun getQuantityInDeck(card: PokemonCard): Int {
        return deckQuantitiesByKey[getCardKey(card)] ?: 0
    }

    /** Copie che questo deck puo' usare: possedute, piu' le sue solo-deck. */
    fun getTotalOwnedQuantity(card: PokemonCard): Int {
        val key = getCardKey(card)
        return (ownedQuantitiesByKey[key] ?: 0) + (deckOnlyQuantitiesByKey[key] ?: 0)
    }

    /** Vedi [CardClassifier]: implementazione unica condivisa da tutta l'app. */
    fun classifyCard(card: PokemonCard): String = CardClassifier.classify(card)

    private fun isEnergy(card: PokemonCard): Boolean {
        return classifyCard(card) == "Energy"
    }

    fun addCardToDeck(card: PokemonCard) {
        val key = getCardKey(card)
        val inDeckCount = getQuantityInDeck(card)
        val totalOwned = getTotalOwnedQuantity(card)
        
        if (inDeckCount >= totalOwned) {
            validationError = "Hai solo $totalOwned copie di questa carta."
            return
        }

        if (selectedCardsIds.size >= 60) {
            validationError = "Limite massimo di 60 carte raggiunto."
            return
        }

        if (!isEnergy(card)) {
            // Era ownedCards.find { } per ogni carta gia' nel deck, cioe'
            // O(deck x possedute) a ogni tocco.
            val sameNameCount = selectedCardsIds.count { id ->
                allCardsById[id]?.name == card.name
            }
            if (sameNameCount >= 4) {
                validationError = "Massimo 4 copie di ${card.name}."
                return
            }
        }

        val availableId = (ownedDocsByKey[key].orEmpty() + deckOnlyDocsByKey[key].orEmpty())
            .firstOrNull { doc ->
                val docInDeckCount = selectedCardsIds.count { it == doc.id }
                docInDeckCount < doc.quantity
            }?.id

        if (availableId != null) {
            selectedCardsIds = selectedCardsIds + availableId
            syncCoverImagesWithSelectedCards()
            validationError = null
            analyzeDeck()
        }
    }

    /**
     * Lo stato delle carte prima dell'ultima rimozione.
     *
     * Serve all'annulla: si rimette esattamente com'era invece di ricostruirlo
     * riaggiungendo la carta. Riaggiungerla passerebbe di nuovo dai controlli
     * di addCardToDeck -- copie possedute, limite di 4, tetto di 60 -- e
     * un'operazione che deve solo disfare l'ultima potrebbe fallire, o far
     * rientrare la carta in un'altra posizione. Copre anche le copertine,
     * perche' togliere una carta puo' averne fatta cadere una.
     */
    private data class DeckRemovalUndo(
        val cardIds: List<String>,
        val coverUrls: List<String>
    )

    private var lastRemoval by mutableStateOf<DeckRemovalUndo?>(null)

    /**
     * Toglie una copia dal deck e restituisce il nome della carta tolta, o
     * null se non c'era niente da togliere.
     *
     * Il nome torna al chiamante perche' e' lui a dover dire cosa e' appena
     * successo: la rimozione non chiede conferma prima, la offre dopo con un
     * annulla, che su un'azione reversibile costa un tocco invece di due.
     */
    fun removeCardFromDeck(card: PokemonCard): String? {
        val key = getCardKey(card)
        val idToRemove = selectedCardsIds.findLast { id ->
            cardIdToKeyMap[id] == key
        } ?: return null

        lastRemoval = DeckRemovalUndo(
            cardIds = selectedCardsIds,
            coverUrls = coverImageUrls
        )

        selectedCardsIds = selectedCardsIds - idToRemove
        syncCoverImagesWithSelectedCards()
        validationError = null
        analyzeDeck()
        return card.name
    }

    /** Rimette il deck com'era prima dell'ultima rimozione. */
    fun undoLastRemoval() {
        val snapshot = lastRemoval ?: return
        lastRemoval = null

        selectedCardsIds = snapshot.cardIds
        coverImageUrls = snapshot.coverUrls
        coverImageUrl = snapshot.coverUrls.firstOrNull().orEmpty()
        validationError = null
        analyzeDeck()
    }

    private fun analyzeDeck() {
        val selectedCards = selectedCardsIds.mapNotNull { allCardsById[it] }

        if (selectedCards.isEmpty()) {
            currentAnalysis = DeckAnalysis()
            return
        }

        // Solo i Pokemon. Il tipo di una Trainer non esiste: all'import prende
        // "Colorless" come ripiego, e in un mazzo da 60 ce ne sono quaranta
        // contro quindici Pokemon. Contandole tutte, "Colorless" vinceva
        // sempre e i due tipi mostrati sull'elenco dei mazzi non dicevano
        // niente del mazzo.
        val typesCount = selectedCards
            .filter { classifyCard(it) == CardClassifier.POKEMON }
            .flatMap { it.type.split(",").map { t -> t.trim() } }
            .filter { it.isNotEmpty() }
            .groupingBy { it }
            .eachCount()

        val supertypesCount = selectedCards.groupingBy { classifyCard(it) }.eachCount()

        val avgHp = if (selectedCards.any { it.hp > 0 }) selectedCards.filter { it.hp > 0 }.map { it.hp }.average() else 0.0
        
        currentAnalysis = DeckAnalysis(
            typesCount = typesCount,
            averageHp = avgHp,
            recommendedEnergy = emptyList(),
            synergies = emptyList(),
            commonWeaknesses = listOf("Variabile"),
            supertypesCount = supertypesCount
        )
    }

    fun prepareEdit(deck: Deck) {
        resetNewDeckState()
        editingDeckId = deck.id
        newDeckName = deck.name
        selectedCardsIds = deck.cards
        coverImageUrls = deck.displayCoverImageUrls()
        coverImageUrl = coverImageUrls.firstOrNull().orEmpty()
        // Riaprendo un deck di prova, le carte aggiunte adesso seguono la
        // stessa regola di quelle gia' dentro: nessuna sorpresa in collezione.
        deckCardSource = if (deck.deckOnly) DeckCardSource.DECK_ONLY else DeckCardSource.COLLECTION
        analyzeDeck()
    }

    fun saveDeck(onSuccess: () -> Unit) {
        if (newDeckName.isBlank()) {
            validationError = "Inserisci un nome per il deck."
            return
        }
        if (selectedCardsIds.isEmpty()) {
            validationError = "Seleziona almeno una carta."
            return
        }
        
        isSaving = true
        val mainTypes = currentAnalysis.typesCount.entries.sortedByDescending { it.value }.take(2).map { it.key }
        
        val deck = Deck(
            id = editingDeckId ?: "",
            name = newDeckName,
            cards = selectedCardsIds,
            mainTypes = mainTypes,
            averageHp = currentAnalysis.averageHp,
            totalCards = selectedCardsIds.size,
            recommendedEnergy = currentAnalysis.recommendedEnergy,
            coverImageUrl = coverImageUrls.firstOrNull().orEmpty(),
            coverImageUrls = coverImageUrls.take(2),
            // Non salviamo l'intenzione ma il fatto: un deck e' "di prova" se
            // dentro ci sono davvero carte che l'utente non ha. Cosi' l'etichetta
            // nell'elenco resta vera anche se l'utente cambia idea a meta' strada.
            deckOnly = editingDeckHasDeckOnlyCards
        )

        viewModelScope.launch {
            repository.saveDeck(deck).onSuccess {
                isSaving = false
                resetNewDeckState()
                onSuccess()
            }.onFailure {
                isSaving = false
                validationError = "Errore database: ${it.localizedMessage}"
            }
        }
    }

    /**
     * Cancella il deck e, con lui, le sue carte solo-deck rimaste orfane.
     *
     * Quelle carte esistono solo per questo deck e nessuna schermata le mostra:
     * lasciarle in Firestore vorrebbe dire accumulare documenti invisibili che
     * l'utente non ha modo di ripulire. Quelle usate anche da un altro deck
     * restano dove sono.
     */
    fun deleteDeck(deckId: String) {
        val deck = decks.find { it.id == deckId }
        viewModelScope.launch {
            repository.deleteDeck(deckId)

            if (deck == null) return@launch
            val idsStillInUse = decks.filter { it.id != deckId }.flatMapTo(mutableSetOf()) { it.cards }
            deck.cards.distinct()
                .filter { id -> id !in idsStillInUse && allCardsById[id]?.deckOnly == true }
                .forEach { repository.deleteCard(it) }
        }
    }

    /**
     * L'utente ha buttato via il deck che stava costruendo.
     *
     * Le carte solo-deck create durante questa sessione erano solo per lui:
     * se nessun deck salvato le referenzia, spariscono con lui. Senza questo,
     * ogni import di prova abbandonato lascerebbe in Firestore documenti che
     * nessuna schermata mostra e che l'utente non puo' cancellare.
     */
    fun discardEditingDeck() {
        val idsInSavedDecks = decks.flatMapTo(mutableSetOf()) { it.cards }
        val orphans = (sessionDeckOnlyCardIds + deckOnlyCardsInDeck.map { it.id })
            .filter { it !in idsInSavedDecks }

        resetNewDeckState()

        if (orphans.isEmpty()) return
        viewModelScope.launch {
            orphans.forEach { repository.deleteCard(it) }
        }
    }

    fun resetNewDeckState() {
        editingDeckId = null
        newDeckName = ""
        selectedCardsIds = emptyList()
        coverImageUrl = ""
        coverImageUrls = emptyList()
        isImportReviewMode = false
        importPlaceholderNames = emptyList()
        deckCardSource = DeckCardSource.COLLECTION
        isImportSourceChoicePending = false
        sessionDeckOnlyCardIds = emptySet()
        // Un annulla che risalisse a un deck precedente rimetterebbe dentro le
        // carte di quello.
        lastRemoval = null
        currentAnalysis = DeckAnalysis()
        validationError = null
    }

    fun duplicateDeck(deck: Deck) {
        viewModelScope.launch {
            val duplicated = deck.copy(id = "", name = "${deck.name} (Copia)")
            repository.saveDeck(duplicated)
        }
    }

    fun clearError() {
        validationError = null
    }

    fun toggleCoverCard(url: String) {
        if (url.isBlank()) return

        coverImageUrls = if (coverImageUrls.contains(url)) {
            coverImageUrls - url
        } else {
            (coverImageUrls + url).distinct().take(2)
        }

        syncCoverImagesWithSelectedCards()
    }

    private fun syncCoverImagesWithSelectedCards() {
        if (selectedCardsIds.isEmpty()) {
            coverImageUrls = emptyList()
            coverImageUrl = ""
            return
        }

        // Le copertine sono indirizzi di sprite, non di carte: qui non si
        // possono piu' confrontare con le immagini delle carte nel deck --
        // farlo le cancellerebbe tutte, perche' non combaciano mai.
        //
        // Una copertina che punta a un Pokemon non piu' nel mazzo non viene
        // tolta qui ma ignorata quando si disegna: l'elenco e il dettaglio
        // mostrano l'intersezione fra quelle scelte e quelle disponibili, e
        // saperlo richiede di risolvere i nomi, che e' lavoro della UI.
        coverImageUrls = coverImageUrls.take(2)
        coverImageUrl = coverImageUrls.firstOrNull().orEmpty()
    }

    // ══════════════════════════════════════
    // IMPORT DECK
    // ══════════════════════════════════════

    data class ImportResult(
        val matched: Int,
        val missing: Int,
        val missingCards: List<String>,
        val setMismatchWarnings: List<String> = emptyList(),
        val missingMetaDeckCards: List<MetaDeckCard> = emptyList(),
        val totalRequested: Int
    )

    private data class OwnedMatch(
        val cards: List<PokemonCard>,
        val usedFallbackSet: Boolean
    )

    var importResult by mutableStateOf<ImportResult?>(null)
        private set

    /**
     * Importa da testo (formato PTCG standard).
     * Matcha le carte con quelle possedute e pre-popola il deck.
     */
    fun importFromText(text: String): ImportResult {
        resetNewDeckState()
        isImportReviewMode = true

        val parsed = DeckImportParser.parse(text)
        if (parsed.deckName != null) {
            newDeckName = parsed.deckName
        }

        return matchAndPopulate(parsed.cards.map { card ->
            MetaDeckCard(
                name = card.name,
                set = card.set,
                number = card.number,
                qty = card.qty,
                type = card.type
            )
        })
    }

    /** Esce dalla modalita' revisione import: la scheda torna a mostrare tutta la collezione. */
    fun exitImportReviewMode() {
        isImportReviewMode = false
    }

    /**
     * Importa da un MetaDeck (dalla sezione Meta Deck).
     */
    fun importFromMetaDeck(metaDeck: MetaDeck): ImportResult {
        resetNewDeckState()
        isImportReviewMode = true
        newDeckName = metaDeck.archetype ?: metaDeck.player ?: "Deck Importato"

        return matchAndPopulate(metaDeck.cards)
    }

    /**
     * Matcha una lista di carte con le carte possedute e popola il deck.
     * Usa nome + set + numero per matching preciso, poi fallback su solo nome.
     */
    private fun matchAndPopulate(cards: List<MetaDeckCard>): ImportResult {
        val idsToAdd = mutableListOf<String>()
        val missingCards = mutableListOf<String>()
        val setMismatchWarnings = mutableListOf<String>()
        val missingMetaDeckCards = mutableListOf<MetaDeckCard>()
        var totalRequested = 0

        for (card in cards) {
            totalRequested += card.qty

            // Cerca nelle carte possedute
            val matched = findOwnedCards(card.name, card.set, card.number)

            if (matched.cards.isEmpty()) {
                missingCards.add("${card.qty}x ${card.name}")
                missingMetaDeckCards.add(card)
                continue
            }

            if (matched.usedFallbackSet && !card.set.isNullOrBlank()) {
                val withNumber = if (card.number.isNullOrBlank()) card.name else "${card.name} ${card.number}"
                setMismatchWarnings.add("${card.qty}x $withNumber (${card.set})")
            }

            // Aggiungi la quantità richiesta (se disponibile)
            var remaining = card.qty
            for (ownedCard in matched.cards) {
                if (remaining <= 0) break
                val alreadyInDeck = idsToAdd.count { it == ownedCard.id }
                val available = ownedCard.quantity - alreadyInDeck
                val toAdd = minOf(remaining, available)
                repeat(toAdd) { idsToAdd.add(ownedCard.id) }
                remaining -= toAdd
            }

            if (remaining > 0) {
                missingCards.add("${remaining}x ${card.name} (possiedi meno copie)")
                missingMetaDeckCards.add(card.copy(qty = remaining))
            }
        }

        selectedCardsIds = idsToAdd.take(60) // Limite 60 carte
        // Nessuna copertina assegnata d'ufficio: erano le prime due immagini
        // di carta che capitavano, e ora che il mazzo si presenta con gli
        // sprite non verrebbero comunque mostrate. Senza scelta esplicita
        // decide headlineScore, che sceglie meglio di "le prime due".
        analyzeDeck()

        val result = ImportResult(
            matched = idsToAdd.size,
            missing = missingCards.size,
            missingCards = missingCards,
            setMismatchWarnings = setMismatchWarnings.distinct(),
            missingMetaDeckCards = missingMetaDeckCards,
            totalRequested = totalRequested
        )
        importResult = result
        // Se non manca niente, non c'e' niente da chiedere: il deck e' gia'
        // fatto solo di carte che l'utente possiede.
        isImportSourceChoicePending = missingMetaDeckCards.isNotEmpty()
        return result
    }

    /**
     * Cerca le carte possedute che corrispondono a nome, set e numero.
     * Prima prova matching esatto, poi fallback su nome.
     */
    private fun findOwnedCards(name: String, set: String?, number: String?): OwnedMatch {
        val nameLower = name.lowercase().trim()

        // Le energie base si cercano per tipo, non per stampa: "Basic Psychic
        // Energy SVE 5" e l'"Energia Psico" gia' in collezione sono la stessa
        // carta, e pretendere lo stesso set e lo stesso numero significava non
        // trovarla mai e aggiungerne una copia nuova a ogni import.
        if (BasicEnergyResolver.isBasicEnergy(name)) {
            val ownedEnergies = ownedCards.filter { card ->
                BasicEnergyResolver.isSameBasicEnergy(card.name, name)
            }
            if (ownedEnergies.isNotEmpty()) {
                return OwnedMatch(
                    // Prima quelle con un'immagine: se in collezione ci sono
                    // gia' dei segnaposto di import precedenti, non e' il caso
                    // di sceglierli proprio adesso.
                    cards = ownedEnergies.sortedByDescending { it.imageUrl.isNotBlank() },
                    usedFallbackSet = false
                )
            }
        }

        // 1. Match esatto: nome + set + numero
        if (set != null && number != null) {
            val exact = ownedCards.filter { card ->
                card.name.lowercase().trim() == nameLower &&
                    SetCodeMapper.matchesImportedSet(
                        importedSet = set,
                        cardSetName = card.set,
                        cardApiSetId = card.apiCardId.substringBefore("-"),
                        cardApiId = card.apiCardId
                    ) &&
                    card.cardNumber == number
            }
            if (exact.isNotEmpty()) return OwnedMatch(exact, usedFallbackSet = false)
        }

        // 2. Match per nome + numero, con preferenza set quando disponibile
        if (number != null) {
            val byNameAndNumber = ownedCards
                .filter { card ->
                card.name.lowercase().trim() == nameLower &&
                    card.cardNumber == number
                }
                .sortedByDescending { card ->
                    SetCodeMapper.matchesImportedSet(
                        importedSet = set,
                        cardSetName = card.set,
                        cardApiSetId = card.apiCardId.substringBefore("-"),
                        cardApiId = card.apiCardId
                    )
                }

            if (byNameAndNumber.isNotEmpty()) {
                val hasSetMatch = set.isNullOrBlank() || SetCodeMapper.matchesImportedSet(
                    importedSet = set,
                    cardSetName = byNameAndNumber.first().set,
                    cardApiSetId = byNameAndNumber.first().apiCardId.substringBefore("-"),
                    cardApiId = byNameAndNumber.first().apiCardId
                )
                return OwnedMatch(byNameAndNumber, usedFallbackSet = !hasSetMatch)
            }
        }

        // 3. Match per nome esatto
        val byName = ownedCards.filter { card ->
            card.name.lowercase().trim() == nameLower
        }
        if (byName.isNotEmpty()) return OwnedMatch(byName, usedFallbackSet = !set.isNullOrBlank())

        // 4. Match parziale per nome (contiene)
        val byPartial = ownedCards.filter { card ->
            card.name.lowercase().contains(nameLower) ||
                nameLower.contains(card.name.lowercase())
        }
        return OwnedMatch(byPartial, usedFallbackSet = byPartial.isNotEmpty() && !set.isNullOrBlank())
    }

    fun clearImportResult() {
        importResult = null
    }

    fun buildPtcgDecklist(deck: Deck): String {
        // Anche le carte solo-deck: una decklist esportata deve essere la lista
        // che si porta al tavolo, non l'inventario di chi la esporta.
        val idToCard = allCardsById
        val grouped = linkedMapOf<String, Pair<PokemonCard, Int>>()

        for (cardId in deck.cards) {
            val card = idToCard[cardId] ?: continue
            val key = getCardKey(card)
            val existing = grouped[key]
            grouped[key] = if (existing == null) card to 1 else card to (existing.second + 1)
        }

        fun setCodeOrNull(card: PokemonCard): String? {
            val fromSet = card.set.uppercase().replace(Regex("[^A-Z0-9]"), "")
            val fromApi = card.apiCardId.substringBefore("-").uppercase().replace(Regex("[^A-Z0-9]"), "")

            val regex = Regex("^[A-Z]{2,5}\\d*$")
            return when {
                regex.matches(fromSet) -> fromSet
                regex.matches(fromApi) -> fromApi
                else -> null
            }
        }

        fun toDeckLine(card: PokemonCard, qty: Int): String {
            val setCode = setCodeOrNull(card)
            val number = card.cardNumber.trim()
            return if (!setCode.isNullOrBlank() && number.isNotBlank()) {
                "$qty ${card.name} $setCode $number"
            } else {
                "$qty ${card.name}"
            }
        }

        val pokemon = mutableListOf<String>()
        val trainer = mutableListOf<String>()
        val energy = mutableListOf<String>()

        grouped.values.forEach { (card, qty) ->
            when (classifyCard(card)) {
                "Pokémon" -> pokemon += toDeckLine(card, qty)
                "Trainer" -> trainer += toDeckLine(card, qty)
                "Energy" -> energy += toDeckLine(card, qty)
                else -> pokemon += toDeckLine(card, qty)
            }
        }

        val builder = StringBuilder()
        builder.appendLine(deck.name.ifBlank { "Deck" })
        builder.appendLine()

        if (pokemon.isNotEmpty()) {
            builder.appendLine("Pokémon: ${pokemon.sumOf { line -> line.substringBefore(' ').toIntOrNull() ?: 0 }}")
            pokemon.forEach { builder.appendLine(it) }
            builder.appendLine()
        }

        if (trainer.isNotEmpty()) {
            builder.appendLine("Trainer: ${trainer.sumOf { line -> line.substringBefore(' ').toIntOrNull() ?: 0 }}")
            trainer.forEach { builder.appendLine(it) }
            builder.appendLine()
        }

        if (energy.isNotEmpty()) {
            builder.appendLine("Energy: ${energy.sumOf { line -> line.substringBefore(' ').toIntOrNull() ?: 0 }}")
            energy.forEach { builder.appendLine(it) }
        }

        return builder.toString().trimEnd()
    }

    // ══════════════════════════════════════
    // ADD MISSING CARDS TO COLLECTION
    // ══════════════════════════════════════

    var isAddingMissingCards by mutableStateOf(false)
        private set

    /**
     * L'utente ha scelto cosa fare delle carte mancanti: eseguiamo.
     *
     * [source] decide se quelle carte diventano sue o restano confinate nel
     * deck. In entrambi i casi il deck esce completo -- e' l'unica differenza
     * rispetto a "continua senza", che invece lo lascia con dei buchi.
     */
    fun applyImportCardSource(source: DeckCardSource, context: Context) {
        deckCardSource = source

        val missing = importResult?.missingMetaDeckCards.orEmpty()
        if (missing.isEmpty()) {
            isImportSourceChoicePending = false
            return
        }

        // La scelta resta a schermo finche' il lavoro non e' finito: e' li' che
        // vive l'indicatore di avanzamento. Sparire subito mostrerebbe un
        // riepilogo che parla di carte non ancora create.
        addMissingCardsToCollection(missing, context) {
            isImportSourceChoicePending = false
        }
    }

    /**
     * L'utente non vuole ne' l'una ne' l'altra: il deck resta con le sole carte
     * che gia' possiede, incompleto. Era il vecchio "No, continua senza".
     */
    fun skipMissingCards() {
        isImportSourceChoicePending = false
    }

    /**
     * Materializza le carte mancanti e le aggiunge al deck corrente.
     *
     * Cerca ogni carta nel catalogo italiano per ottenere immagine, HP, tipo,
     * ecc.; se la ricerca fallisce, crea la carta con dati minimi. Finiscono in
     * collezione o restano solo-deck a seconda di [deckCardSource].
     */
    fun addMissingCardsToCollection(missingCards: List<MetaDeckCard>, context: Context, onComplete: () -> Unit = {}) {
        if (missingCards.isEmpty()) return
        isAddingMissingCards = true
        val deckOnly = deckCardSource == DeckCardSource.DECK_ONLY

        viewModelScope.launch {
            // Prima cosa, lookup di TUTTE le carte mancanti in parallelo. Prima era
            // sequenziale: per 20 carte mancanti si sommavano 20 latenze di rete.
            // Con async+awaitAll le chiamate partono insieme e l'import diventa
            // ~N volte piu' veloce.
            val built = coroutineScope {
                missingCards.map { card ->
                    async(Dispatchers.IO) {
                        card to lookupAndCreateCard(card, context, deckOnly)
                    }
                }.awaitAll()
            }

            // Poi le scritture su Firestore sono ~istantanee grazie alla cache
            // locale persistente, quindi possiamo farle in sequenza per
            // mantenere un ordine stabile nel deck.
            val newIds = mutableListOf<String>()
            val placeholders = mutableListOf<String>()
            for ((card, pokemonCard) in built) {
                if (pokemonCard.imageUrl.isBlank()) placeholders += pokemonCard.name
                val result = repository.addCard(pokemonCard)
                result.onSuccess { docId ->
                    repeat(card.qty) { newIds.add(docId) }
                    if (deckOnly) sessionDeckOnlyCardIds = sessionDeckOnlyCardIds + docId
                }
            }
            importPlaceholderNames = placeholders.distinct()

            // Se alcune carte entrano a 0, prova una hydration immediata del prezzo
            // per riallineare anche il totalValue della collezione. Per le carte
            // solo-deck non c'e' nessun totale da riallineare: sarebbero chiamate
            // a PokeWallet spese per un numero che non viene mostrato da nessuna
            // parte (vedi le note sul rate limit in MIGRATION_PLAN.md).
            if (!deckOnly) {
                hydrateImportedCardPrices(newIds.toSet(), context)
            }

            // Aggiungi al deck corrente
            if (newIds.isNotEmpty()) {
                selectedCardsIds = (selectedCardsIds + newIds).take(60)
                analyzeDeck()
            }

            isAddingMissingCards = false
            // Il risultato resta a schermo, ma senza piu' mancanti: adesso le
            // carte ci sono tutte e il dialog e' solo un riepilogo.
            importResult = importResult?.copy(
                matched = selectedCardsIds.size,
                missing = 0,
                missingCards = emptyList(),
                missingMetaDeckCards = emptyList()
            )
            onComplete()
        }
    }

    /**
     * Cerca una carta per set+numero nel nostro catalogo ITA -- nessuna chiamata
     * PokeWallet, mai (vedi MIGRATION_PLAN.md M4.6). Il numero carta e' indipendente
     * dalla lingua, quindi funziona anche su decklist in inglese (import PTCGL/Limitless)
     * senza bisogno di matchare il nome. Se la carta non e' ancora nel nostro D1, resta
     * il fallback a dati minimi sotto (nessuna immagine) -- niente piu' PokeWallet come
     * secondo tentativo.
     */
    private suspend fun lookupAndCreateCard(
        card: MetaDeckCard,
        context: Context,
        deckOnly: Boolean
    ): PokemonCard {
        // Tre tentativi prima di arrendersi a una carta senza immagine.
        // Prima ce n'era uno solo, e bastava un codice di set che il catalogo
        // italiano non conosce — SVE per le energie, o un'espansione appena
        // uscita — perche' la carta entrasse in collezione come un rettangolo
        // vuoto col nome sopra.
        // Il nome viene passato insieme a set e numero: due espansioni diverse
        // possono rispondere allo stesso codice, e senza il nome si prendeva la
        // prima del catalogo -- e' cosi' che un "Kadabra MEG 55" tornava un
        // Treecko. Sui Pokemon il nome e' anche un veto, perche' in italiano si
        // chiamano come in inglese: se non combacia, meglio cercare per nome
        // che tenersi la carta sbagliata. Su Allenatori ed Energie no, li' i
        // nomi sono tradotti e il confronto fallirebbe sempre.
        val isPokemon = card.type.equals("pokemon", ignoreCase = true)
        val tcgCard = pokeTcgRepository.findExactItalianCard(
            setCode = card.set,
            number = card.number,
            context = context,
            expectedName = card.name,
            requireNameMatch = isPokemon
        )
            ?: resolveBasicEnergyCard(card, context)
            ?: resolveByNameOnly(card, context)

        return if (tcgCard != null) {
            // Una carta solo-deck non vale niente perche' non e' posseduta:
            // cercarne il prezzo sarebbe una chiamata di rete per un numero
            // che nessuna schermata somma.
            val price = if (deckOnly) 0.0 else resolveBestPrice(
                card = tcgCard,
                fallbackSet = card.set,
                fallbackNumber = card.number,
                fallbackName = card.name,
                context = context
            )

            PokemonCard(
                name = tcgCard.name,
                imageUrl = tcgCard.images.small,
                set = tcgCard.set?.name ?: card.set ?: "",
                rarity = tcgCard.rarity ?: "Unknown",
                type = tcgCard.types?.firstOrNull() ?: "Colorless",
                hp = tcgCard.hp?.toIntOrNull() ?: 0,
                supertype = tcgCard.supertype.ifBlank {
                    when (card.type.lowercase()) {
                        "pokemon" -> "Pokémon"; "trainer" -> "Trainer"; "energy" -> "Energy"; else -> "Pokémon"
                    }
                },
                subtypes = tcgCard.subtypes ?: emptyList(),
                apiCardId = tcgCard.id,
                cardNumber = tcgCard.number,
                estimatedValue = price,
                quantity = card.qty,
                condition = "Near Mint",
                variant = "Normal",
                deckOnly = deckOnly
            )
        } else {
            // Fallback: dati minimi dal MetaDeckCard
            val supertype = when (card.type.lowercase()) {
                "pokemon" -> "Pokémon"; "trainer" -> "Trainer"; "energy" -> "Energy"; else -> "Pokémon"
            }
            PokemonCard(
                name = card.name,
                set = card.set ?: "",
                cardNumber = card.number ?: "",
                quantity = card.qty,
                estimatedValue = 0.0,
                supertype = supertype,
                hp = if (supertype == "Pokémon") 100 else 0,
                condition = "Near Mint",
                variant = "Normal",
                deckOnly = deckOnly
            )
        }
    }

    /**
     * Un'energia base qualsiasi del tipo giusto.
     *
     * Il set delle energie di PTCGL (SVE) non esiste nel catalogo italiano: le
     * energie base italiane escono dentro le espansioni normali. Cercarle per
     * set e numero non poteva funzionare, e infatti ogni energia di ogni import
     * finiva senza immagine.
     */
    private suspend fun resolveBasicEnergyCard(card: MetaDeckCard, context: Context): TcgCard? {
        val energyName = BasicEnergyResolver.italianEnergyName(card.name) ?: return null

        return pokeTcgRepository.searchItalianCardsByName(energyName, context, limit = 40)
            .getOrNull()
            ?.firstOrNull { candidate ->
                candidate.images.small.isNotBlank() &&
                    BasicEnergyResolver.isSameBasicEnergy(candidate.name, energyName)
            }
    }

    /**
     * La carta cercata per nome, ignorando il set.
     *
     * E' un ripiego dichiarato: l'illustrazione puo' essere di un'altra stampa.
     * Ma fra una carta giusta con l'arte di un'altra espansione e un rettangolo
     * grigio col nome scritto sopra, la prima resta piu' utile — e resta una
     * carta vera, con i suoi dati, non un segnaposto che sporca la collezione.
     */
    private suspend fun resolveByNameOnly(card: MetaDeckCard, context: Context): TcgCard? {
        val name = card.name.trim().takeIf { it.isNotBlank() } ?: return null
        val wantedName = normalizeCardNameForMatch(name)

        val candidates = pokeTcgRepository.searchItalianCardsByName(name, context, limit = 40)
            .getOrNull()
            ?.filter { candidate ->
                // Il nome deve coincidere, non somigliare. La ricerca per nome
                // e' volutamente generosa — cercando "Toucannon" restituisce
                // anche "Toucannon ex" — e importare una carta simile al posto
                // di quella chiesta e' peggio di non importarla: il mazzo
                // sembrerebbe completo e non lo sarebbe.
                candidate.images.small.isNotBlank() &&
                    normalizeCardNameForMatch(candidate.name) == wantedName
            }
            ?.takeIf { it.isNotEmpty() }
            ?: return null

        val wantedNumber = card.number?.trim()?.substringBefore('/')?.trimStart('0')

        return candidates.firstOrNull { candidate ->
            // A parita' di nome si preferisce lo stesso numero di carta: fra le
            // ristampe e' l'indizio piu' probabile che sia proprio quella.
            wantedNumber != null &&
                candidate.number.trim().trimStart('0').equals(wantedNumber, ignoreCase = true)
        } ?: candidates.first()
    }

    /** Minuscole, senza accenti e senza punteggiatura: per confrontare due nomi. */
    private fun normalizeCardNameForMatch(raw: String): String {
        val decomposed = java.text.Normalizer.normalize(raw.trim().lowercase(), java.text.Normalizer.Form.NFD)
        return decomposed
            .replace(Regex("\\p{Mn}+"), "")
            .replace(Regex("[^a-z0-9]+"), " ")
            .trim()
    }

    // ── Card search in TCG sets ────────────────────────────────────────────

    fun searchCardsInSets(query: String, targetSetId: String? = null, context: Context) {
        if (query.isBlank()) {
            tcgSearchResults = emptyList()
            tcgSearchError = null
            isSearchingCards = false
            return
        }
        isSearchingCards = true
        tcgSearchError = null
        viewModelScope.launch {
            pokeTcgRepository.searchItalianCardsByName(query, context, targetSetId = targetSetId)
                .onSuccess { cards ->
                    tcgSearchResults = cards.take(20)
                    if (cards.isEmpty()) tcgSearchError = if (query.length >= 2)
                        "Nessuna carta trovata per \"$query\"" else null
                    isSearchingCards = false
                }
                .onFailure {
                    tcgSearchError = "Errore durante la ricerca"
                    tcgSearchResults = emptyList()
                    isSearchingCards = false
                }
        }
    }

    fun clearTcgSearch() {
        tcgSearchResults = emptyList()
        tcgSearchError = null
        isSearchingCards = false
    }

    /**
     * Aggiunge al deck una carta trovata nei set.
     *
     * Rispetta [deckCardSource]: la stessa carta finisce in collezione o resta
     * confinata nel deck a seconda del tipo di deck che si sta costruendo.
     * Prima entrava sempre in collezione, e cercare una carta per provarla in
     * un mazzo significava dichiarare di possederla.
     */
    fun addTcgCardToDeck(card: TcgCard, qty: Int, context: Context, onComplete: () -> Unit = {}) {
        val deckOnly = deckCardSource == DeckCardSource.DECK_ONLY
        viewModelScope.launch {
            val price = if (deckOnly) 0.0 else resolveBestPrice(
                card = card,
                fallbackSet = card.set?.id ?: card.set?.name,
                fallbackNumber = card.number,
                fallbackName = card.name,
                context = context
            )
            val pokemonCard = PokemonCard(
                name = card.name,
                imageUrl = card.images.small,
                set = card.set?.name ?: "",
                rarity = card.rarity ?: "Unknown",
                type = card.types?.firstOrNull() ?: "Colorless",
                hp = card.hp?.toIntOrNull() ?: 0,
                supertype = card.supertype.ifBlank { "Pokémon" },
                subtypes = card.subtypes ?: emptyList(),
                apiCardId = card.id,
                cardNumber = card.number,
                estimatedValue = price,
                quantity = qty,
                condition = "Near Mint",
                variant = "Normal",
                deckOnly = deckOnly
            )
            val result = repository.addCard(pokemonCard)
            result.onSuccess { docId ->
                if (deckOnly) sessionDeckOnlyCardIds = sessionDeckOnlyCardIds + docId
                val newIds = List(qty) { docId }
                selectedCardsIds = (selectedCardsIds + newIds).take(60)
                analyzeDeck()
            }
            onComplete()
        }
    }

    private suspend fun hydrateImportedCardPrices(cardDocIds: Set<String>, context: Context) {
        if (cardDocIds.isEmpty()) return

        for (docId in cardDocIds) {
            val stored = repository.getCard(docId).getOrNull() ?: continue
            if (stored.estimatedValue > 0.0 || stored.apiCardId.isBlank()) continue

            val resolved = pokeTcgRepository.getCard(stored.apiCardId).getOrNull() ?: continue
            val hydratedPrice = resolveBestPrice(
                card = resolved,
                fallbackSet = stored.set,
                fallbackNumber = stored.cardNumber,
                fallbackName = stored.name,
                context = context
            )
            if (hydratedPrice <= 0.0) continue

            repository.updateCard(docId, stored.copy(estimatedValue = hydratedPrice))
        }
    }


    private fun italianPriceLookupKey(raw: String): String? {
        val clean = raw.split("/").firstOrNull()?.trim().orEmpty()
        if (clean.isBlank()) return null
        return clean.toIntOrNull()?.toString() ?: clean.uppercase()
    }

    private suspend fun resolveBestPrice(
        card: TcgCard,
        fallbackSet: String? = null,
        fallbackNumber: String? = null,
        fallbackName: String? = null,
        context: Context
    ): Double {
        val cardmarket = card.cardmarket?.prices
        val cm = cardmarket.minimumEurPriceOrZero().takeIf { it > 0.0 }
            ?: cardmarket?.trendPrice?.takeIf { it > 0.0 }
            ?: cardmarket?.avg7?.takeIf { it > 0.0 }
            ?: cardmarket?.avg30?.takeIf { it > 0.0 }

        if (cm != null) return cm

        val tcg = card.tcgplayer?.prices.orEmpty().values.asSequence()
            .mapNotNull { priceInfo ->
                listOf(priceInfo.market, priceInfo.mid, priceInfo.low)
                    .firstOrNull { value -> (value ?: 0.0) > 0.0 }
            }
            .firstOrNull { it > 0.0 }

        if (tcg != null) return tcg

        // Carte ITA: prezzo dal nostro snapshot pre-calcolato (/ita/prices.json), mai
        // una chiamata PokeWallet diretta -- vedi MIGRATION_PLAN.md M4.6. Se lo snapshot
        // non ha ancora un prezzo per questa carta, resta a 0 invece di consumare budget
        // PokeWallet: verra' popolato al prossimo giro dello snapshot lato Worker.
        if (card.id.startsWith("ita:", ignoreCase = true)) {
            val setCode = card.id.removePrefix("ita:").substringBefore(':').takeIf { it.isNotBlank() }
                ?: return 0.0
            val priceMap = RepositoryProvider.italianPriceSnapshotRepository.getPriceMap(context, setCode)
            val snapshotPrice = italianPriceLookupKey(card.number)?.let(priceMap::get)
            val snapshotBest = sequenceOf(
                snapshotPrice?.eurLow,
                snapshotPrice?.eurAvg,
                snapshotPrice?.eurTrend,
                snapshotPrice?.eurAvg7,
                snapshotPrice?.eurAvg30
            ).firstOrNull { (it ?: 0.0) > 0.0 }

            return snapshotBest ?: 0.0
        }

        val setCode = sequenceOf(
            card.set?.id,
            SetCodeMapper.normalizeDecklistSetCode(card.set?.id),
            SetCodeMapper.normalizeDecklistSetCode(card.set?.name),
            SetCodeMapper.normalizeDecklistSetCode(fallbackSet),
            fallbackSet
        ).firstOrNull { !it.isNullOrBlank() }

        val number = fallbackNumber?.takeIf { it.isNotBlank() } ?: card.number
        val name = fallbackName?.takeIf { it.isNotBlank() } ?: card.name

        if (!setCode.isNullOrBlank() && number.isNotBlank() && name.isNotBlank()) {
            val pw = pokeWalletRepository.getCardPrices(name, setCode, number).getOrNull()
            val pwPrice = sequenceOf(
                pw?.eurLow,
                pw?.eurAvg,
                pw?.eurTrend,
                pw?.eurAvg7,
                pw?.eurAvg30
            ).firstOrNull { (it ?: 0.0) > 0.0 }

            if ((pwPrice ?: 0.0) > 0.0) return pwPrice ?: 0.0
        }

        return 0.0
    }
}

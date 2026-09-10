package com.emabuia.pokevault.viewmodel

import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.emabuia.pokevault.data.firebase.FirestoreRepository
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
import kotlinx.coroutines.launch

class DeckLabViewModel : ViewModel() {
    private val repository = FirestoreRepository()
    private val pokeTcgRepository = RepositoryProvider.tcgRepository
    private val pokeWalletRepository = RepositoryProvider.pokeWalletRepository

    companion object {
        // Cache di processo per i risultati della ricerca Pokemon TCG API.
        // Sopravvive alla navigazione tra schermate: se l'utente reimporta
        // lo stesso archetipo, evitiamo di rifare le stesse query.
        // La chiave combina nome+set+numero per distinguere varianti.
        // Usa ConcurrentHashMap perché viene acceduta da più coroutine in
        // parallelo su Dispatchers.IO durante l'import massivo.
        // ConcurrentHashMap non accetta valori null, quindi incapsuliamo
        // il risultato in un Optional-like container.
        private val tcgLookupCache =
            java.util.concurrent.ConcurrentHashMap<String, CachedLookup>()

        private val legacyClassificationBackfillStarted = java.util.concurrent.atomic.AtomicBoolean(false)

        private data class CachedLookup(val card: TcgCard?)

        private fun lookupKey(name: String, set: String?, number: String?): String =
            "${name.lowercase().trim()}|${set?.lowercase()?.trim() ?: ""}|${number?.trim() ?: ""}"
    }

    var decks by mutableStateOf<List<Deck>>(emptyList())
        private set

    var ownedCards by mutableStateOf<List<PokemonCard>>(emptyList())
        private set

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

    // Card search in TCG sets (for "Cerca nei set")
    var isSearchingCards by mutableStateOf(false)
        private set
    var tcgSearchResults by mutableStateOf<List<TcgCard>>(emptyList())
        private set
    var tcgSearchError by mutableStateOf<String?>(null)
        private set

    // Optimized map for quick lookups during UI rendering
    private val cardIdToKeyMap by derivedStateOf {
        ownedCards.associate { it.id to getCardKey(it) }
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

    private fun loadDecks() {
        viewModelScope.launch {
            repository.getDecks().collectLatest {
                decks = it
            }
        }
    }

    private fun loadOwnedCards() {
        viewModelScope.launch {
            repository.getCards().collectLatest { cards ->
                ownedCards = cards.sortedWith(
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

    fun getTotalOwnedQuantity(card: PokemonCard): Int {
        val key = getCardKey(card)
        return ownedCards.filter { getCardKey(it) == key }.sumOf { it.quantity }
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
            val sameNameCount = selectedCardsIds.count { id ->
                ownedCards.find { it.id == id }?.name == card.name
            }
            if (sameNameCount >= 4) {
                validationError = "Massimo 4 copie di ${card.name}."
                return
            }
        }

        val availableId = ownedCards
            .filter { getCardKey(it) == key }
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

    fun removeCardFromDeck(card: PokemonCard) {
        val key = getCardKey(card)
        val idToRemove = selectedCardsIds.findLast { id ->
            cardIdToKeyMap[id] == key
        }
        
        if (idToRemove != null) {
            selectedCardsIds = selectedCardsIds - idToRemove
            syncCoverImagesWithSelectedCards()
            validationError = null
            analyzeDeck()
        }
    }

    fun addAllCopiesToDeck(card: PokemonCard) {
        val before = selectedCardsIds.size
        while (selectedCardsIds.size < 60) {
            val previousSize = selectedCardsIds.size
            addCardToDeck(card)
            if (selectedCardsIds.size == previousSize) break
        }
        if (selectedCardsIds.size > before) {
            validationError = null
        }
    }

    fun removeAllCopiesFromDeck(card: PokemonCard) {
        val key = getCardKey(card)
        val remainingIds = selectedCardsIds.filterNot { id -> cardIdToKeyMap[id] == key }
        if (remainingIds.size != selectedCardsIds.size) {
            selectedCardsIds = remainingIds
            syncCoverImagesWithSelectedCards()
            validationError = null
            analyzeDeck()
        }
    }

    private fun analyzeDeck() {
        val cardMap = ownedCards.associateBy { it.id }
        val selectedCards = selectedCardsIds.mapNotNull { cardMap[it] }
        
        if (selectedCards.isEmpty()) {
            currentAnalysis = DeckAnalysis()
            return
        }

        val typesCount = selectedCards.flatMap { it.type.split(",").map { t -> t.trim() } }
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
            coverImageUrls = coverImageUrls.take(2)
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

    fun deleteDeck(deckId: String) {
        viewModelScope.launch {
            repository.deleteDeck(deckId)
        }
    }

    fun resetNewDeckState() {
        editingDeckId = null
        newDeckName = ""
        selectedCardsIds = emptyList()
        coverImageUrl = ""
        coverImageUrls = emptyList()
        isImportReviewMode = false
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
        val availableUrls = selectedCardsIds
            .mapNotNull { id -> ownedCards.find { it.id == id }?.imageUrl }
            .filter { it.isNotBlank() }
            .distinct()

        if (availableUrls.isEmpty()) {
            coverImageUrls = emptyList()
            coverImageUrl = ""
            return
        }

        // Mantieni solo le copertine ancora presenti nel deck.
        // Non riempire automaticamente per permettere all'utente
        // di rimuoverle e sceglierne altre in modo esplicito.
        coverImageUrls = coverImageUrls
            .filter { it in availableUrls }
            .take(2)
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
        if (idsToAdd.isNotEmpty()) {
            val importedUrls = selectedCardsIds
                .mapNotNull { id -> ownedCards.find { it.id == id }?.imageUrl }
                .filter { it.isNotBlank() }
                .distinct()
            coverImageUrls = importedUrls.take(2)
            coverImageUrl = coverImageUrls.firstOrNull().orEmpty()
        }
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
        return result
    }

    /**
     * Cerca le carte possedute che corrispondono a nome, set e numero.
     * Prima prova matching esatto, poi fallback su nome.
     */
    private fun findOwnedCards(name: String, set: String?, number: String?): OwnedMatch {
        val nameLower = name.lowercase().trim()

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
        val idToCard = ownedCards.associateBy { it.id }
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
     * Aggiunge le carte mancanti alla collezione e poi al deck corrente.
     * Cerca ogni carta sulla Pokemon TCG API per ottenere immagine, HP, tipo, ecc.
     * Se la ricerca API fallisce, crea la carta con dati minimi.
     */
    fun addMissingCardsToCollection(missingCards: List<MetaDeckCard>, onComplete: () -> Unit = {}) {
        if (missingCards.isEmpty()) return
        isAddingMissingCards = true

        viewModelScope.launch {
            // Prima cosa, lookup di TUTTE le carte mancanti in parallelo sulla
            // Pokemon TCG API. Prima era sequenziale: per 20 carte mancanti si
            // sommavano 20 latenze di rete. Con async+awaitAll le chiamate
            // partono insieme e l'import diventa ~N volte più veloce.
            val built = coroutineScope {
                missingCards.map { card ->
                    async(Dispatchers.IO) {
                        card to lookupAndCreateCard(card)
                    }
                }.awaitAll()
            }

            // Poi le scritture su Firestore sono ~istantanee grazie alla cache
            // locale persistente, quindi possiamo farle in sequenza per
            // mantenere un ordine stabile nel deck.
            val newIds = mutableListOf<String>()
            for ((card, pokemonCard) in built) {
                val result = repository.addCard(pokemonCard)
                result.onSuccess { docId ->
                    repeat(card.qty) { newIds.add(docId) }
                }
            }

            // Se alcune carte entrano a 0, prova una hydration immediata del prezzo
            // per riallineare anche il totalValue della collezione.
            hydrateImportedCardPrices(newIds.toSet())

            // Aggiungi al deck corrente
            if (newIds.isNotEmpty()) {
                selectedCardsIds = (selectedCardsIds + newIds).take(60)
                analyzeDeck()
            }

            isAddingMissingCards = false
            importResult = null
            onComplete()
        }
    }

    /**
     * Cerca una carta su PokéWallet per set+numero (strict), con fallback
     * a ricerca per nome filtrata sullo stesso set+numero.
     */
    private suspend fun lookupAndCreateCard(card: MetaDeckCard): PokemonCard {
        val tcgCard = searchPokewalletCard(card.name, card.set, card.number)

        return if (tcgCard != null) {
            val price = resolveBestPrice(
                card = tcgCard,
                fallbackSet = card.set,
                fallbackNumber = card.number,
                fallbackName = card.name
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
                variant = "Normal"
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
                variant = "Normal"
            )
        }
    }

    // ── Card search in TCG sets ────────────────────────────────────────────

    fun searchCardsInSets(query: String, targetSetId: String? = null) {
        if (query.isBlank()) {
            tcgSearchResults = emptyList()
            tcgSearchError = null
            isSearchingCards = false
            return
        }
        isSearchingCards = true
        tcgSearchError = null
        viewModelScope.launch {
            pokeTcgRepository.searchCardsFuzzy(query, targetSetId = targetSetId)
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

    fun addTcgCardToDeck(card: TcgCard, qty: Int, onComplete: () -> Unit = {}) {
        viewModelScope.launch {
            val price = resolveBestPrice(
                card = card,
                fallbackSet = card.set?.id ?: card.set?.name,
                fallbackNumber = card.number,
                fallbackName = card.name
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
                variant = "Normal"
            )
            val result = repository.addCard(pokemonCard)
            result.onSuccess { docId ->
                val newIds = List(qty) { docId }
                selectedCardsIds = (selectedCardsIds + newIds).take(60)
                analyzeDeck()
            }
            onComplete()
        }
    }

    private suspend fun hydrateImportedCardPrices(cardDocIds: Set<String>) {
        if (cardDocIds.isEmpty()) return

        for (docId in cardDocIds) {
            val stored = repository.getCard(docId).getOrNull() ?: continue
            if (stored.estimatedValue > 0.0 || stored.apiCardId.isBlank()) continue

            val resolved = pokeTcgRepository.getCard(stored.apiCardId).getOrNull() ?: continue
            val hydratedPrice = resolveBestPrice(
                card = resolved,
                fallbackSet = stored.set,
                fallbackNumber = stored.cardNumber,
                fallbackName = stored.name
            )
            if (hydratedPrice <= 0.0) continue

            repository.updateCard(docId, stored.copy(estimatedValue = hydratedPrice))
        }
    }

    private suspend fun searchPokewalletCard(name: String, setCode: String?, number: String?): TcgCard? {
        val key = lookupKey(name, setCode, number)
        tcgLookupCache[key]?.let { return it.card }

        val found: TcgCard? = try {
            val normalizedSet = SetCodeMapper.normalizeDecklistSetCode(setCode)
            val localExact = pokeTcgRepository
                .findExactCardInCatalog(name, normalizedSet, number)
                .getOrNull()

            val direct = localExact ?: pokeTcgRepository
                .searchPokewalletCardByNameSetAndNumber(name, normalizedSet, number)
                .getOrNull()

            val strictSetNumber = direct ?: pokeTcgRepository
                .getPokewalletCardBySetAndNumber(normalizedSet, number)
                .getOrNull()

            strictSetNumber ?: run {
                val candidates = pokeTcgRepository
                    .searchByNameAndNumber(name, number, normalizedSet)
                    .getOrDefault(emptyList())

                if (candidates.isEmpty()) {
                    null
                } else {
                    val scoped = if (setCode.isNullOrBlank()) {
                        candidates
                    } else {
                        candidates.filter { card ->
                            SetCodeMapper.matchesImportedSet(
                                importedSet = setCode,
                                cardSetName = card.set?.name,
                                cardApiSetId = card.set?.id,
                                cardApiId = card.id
                            )
                        }
                    }

                    scoped.firstOrNull() ?: candidates.firstOrNull()
                }
            }
        } catch (_: Exception) {
            null
        }

        tcgLookupCache[key] = CachedLookup(found)
        return found
    }

    private suspend fun resolveBestPrice(
        card: TcgCard,
        fallbackSet: String? = null,
        fallbackNumber: String? = null,
        fallbackName: String? = null
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

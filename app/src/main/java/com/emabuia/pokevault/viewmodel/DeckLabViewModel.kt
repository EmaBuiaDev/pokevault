package com.emabuia.pokevault.viewmodel

import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.emabuia.pokevault.data.firebase.FirestoreRepository
import com.emabuia.pokevault.data.model.Deck
import com.emabuia.pokevault.data.model.DeckAnalysis
import com.emabuia.pokevault.data.model.DeckImportParser
import com.emabuia.pokevault.data.model.MetaDeck
import com.emabuia.pokevault.data.model.MetaDeckCard
import com.emabuia.pokevault.data.model.PokemonCard
import com.emabuia.pokevault.data.remote.PokeTcgRepository
import com.emabuia.pokevault.data.remote.TcgCard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class DeckLabViewModel : ViewModel() {
    private val repository = FirestoreRepository()
    private val pokeTcgRepository = PokeTcgRepository()

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
    var currentAnalysis by mutableStateOf(DeckAnalysis())
    var validationError by mutableStateOf<String?>(null)
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
        loadDecks()
        loadOwnedCards()
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

    fun classifyCard(card: PokemonCard): String {
        val s = card.supertype.lowercase()
        val n = card.name.lowercase()
        val sub = card.subtypes.map { it.lowercase() }
        val hasHp = card.hp > 0

        if (s.contains("energy") || sub.contains("energy") || n.contains("energy") || n.contains("energia")) {
            return "Energy"
        }
        if (s.contains("trainer") || sub.contains("item") || sub.contains("stadium") || sub.contains("supporter") || s.contains("aiuto") || !hasHp) {
            return "Trainer"
        }
        return "Pokémon"
    }

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
            if (coverImageUrl.isEmpty()) coverImageUrl = card.imageUrl
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
            if (coverImageUrl == card.imageUrl && !selectedCardsIds.any { id -> ownedCards.find { it.id == id }?.imageUrl == card.imageUrl }) {
                 val nextCardId = selectedCardsIds.firstOrNull()
                 coverImageUrl = ownedCards.find { it.id == nextCardId }?.imageUrl ?: ""
            }
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
        coverImageUrl = deck.coverImageUrl
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
            coverImageUrl = coverImageUrl
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

    fun selectCoverCard(url: String) {
        coverImageUrl = url
    }

    // ══════════════════════════════════════
    // IMPORT DECK
    // ══════════════════════════════════════

    data class ImportResult(
        val matched: Int,
        val missing: Int,
        val missingCards: List<String>,
        val missingMetaDeckCards: List<MetaDeckCard> = emptyList(),
        val totalRequested: Int
    )

    var importResult by mutableStateOf<ImportResult?>(null)
        private set

    /**
     * Importa da testo (formato PTCG standard).
     * Matcha le carte con quelle possedute e pre-popola il deck.
     */
    fun importFromText(text: String): ImportResult {
        resetNewDeckState()

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
        val missingMetaDeckCards = mutableListOf<MetaDeckCard>()
        var totalRequested = 0

        for (card in cards) {
            totalRequested += card.qty

            // Cerca nelle carte possedute
            val matched = findOwnedCards(card.name, card.set, card.number)

            if (matched.isEmpty()) {
                missingCards.add("${card.qty}x ${card.name}")
                missingMetaDeckCards.add(card)
                continue
            }

            // Aggiungi la quantità richiesta (se disponibile)
            var remaining = card.qty
            for (ownedCard in matched) {
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
            val firstCard = ownedCards.find { it.id == idsToAdd.first() }
            coverImageUrl = firstCard?.imageUrl ?: ""
        }
        analyzeDeck()

        val result = ImportResult(
            matched = idsToAdd.size,
            missing = missingCards.size,
            missingCards = missingCards,
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
    private fun findOwnedCards(name: String, set: String?, number: String?): List<PokemonCard> {
        val nameLower = name.lowercase().trim()

        // 1. Match esatto: nome + set + numero
        if (set != null && number != null) {
            val exact = ownedCards.filter { card ->
                card.name.lowercase().trim() == nameLower &&
                    (card.set.lowercase().contains(set.lowercase()) ||
                     card.apiCardId.lowercase().contains(set.lowercase())) &&
                    card.cardNumber == number
            }
            if (exact.isNotEmpty()) return exact
        }

        // 2. Match per nome + numero
        if (number != null) {
            val byNameAndNumber = ownedCards.filter { card ->
                card.name.lowercase().trim() == nameLower &&
                    card.cardNumber == number
            }
            if (byNameAndNumber.isNotEmpty()) return byNameAndNumber
        }

        // 3. Match per nome esatto
        val byName = ownedCards.filter { card ->
            card.name.lowercase().trim() == nameLower
        }
        if (byName.isNotEmpty()) return byName

        // 4. Match parziale per nome (contiene)
        val byPartial = ownedCards.filter { card ->
            card.name.lowercase().contains(nameLower) ||
                nameLower.contains(card.name.lowercase())
        }
        return byPartial
    }

    fun clearImportResult() {
        importResult = null
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
     * Cerca una carta sulla Pokemon TCG API per nome/numero/set e
     * restituisce un PokemonCard completo. Fallback su dati minimi se non trovata.
     */
    private suspend fun lookupAndCreateCard(card: MetaDeckCard): PokemonCard {
        // Prova ricerca precisa per nome + numero
        val tcgCard = searchTcgCard(card.name, card.set, card.number)

        return if (tcgCard != null) {
            val price = tcgCard.tcgplayer?.prices?.get("normal")?.market
                ?: tcgCard.cardmarket?.prices?.lowPrice
                ?: tcgCard.cardmarket?.prices?.averageSellPrice ?: 0.0

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
                supertype = supertype,
                hp = if (supertype == "Pokémon") 100 else 0,
                condition = "Near Mint",
                variant = "Normal"
            )
        }
    }

    /**
     * Cerca una carta sulla Pokemon TCG API con strategia a fallback:
     * 1. Nome + set + numero (più preciso)
     * 2. Nome + numero
     * 3. Solo nome (primo risultato)
     */
    private suspend fun searchTcgCard(name: String, set: String?, number: String?): TcgCard? {
        // Cache hit: ritorna immediatamente (anche null cached, per evitare
        // di ripetere lookup che sappiamo essere falliti).
        val key = lookupKey(name, set, number)
        tcgLookupCache[key]?.let { return it.card }

        val found: TcgCard? = try {
            // 1. Cerca per nome + numero (searchByNameAndNumber ha già fallback interni)
            val result = pokeTcgRepository.searchByNameAndNumber(name, number)
            var hit: TcgCard? = null
            result.onSuccess { cards ->
                if (cards.isNotEmpty()) {
                    // Se abbiamo il set, preferiamo la carta dallo stesso set
                    hit = if (set != null) {
                        val setLower = set.lowercase()
                        cards.find { card ->
                            card.set?.id?.lowercase()?.contains(setLower) == true ||
                                card.id.lowercase().startsWith(setLower)
                        } ?: cards.first()
                    } else {
                        cards.first()
                    }
                }
            }

            if (hit == null) {
                // 2. Fallback: ricerca fuzzy per nome
                val fallback = pokeTcgRepository.searchCardsFuzzy(name)
                fallback.onSuccess { cards ->
                    if (cards.isNotEmpty()) hit = cards.first()
                }
            }
            hit
        } catch (_: Exception) {
            null
        }

        tcgLookupCache[key] = CachedLookup(found)
        return found
    }
}

package com.emabuia.pokevault.viewmodel

import android.content.Context
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.emabuia.pokevault.data.firebase.FirestoreRepository
import com.emabuia.pokevault.data.model.PokemonCard
import com.emabuia.pokevault.data.remote.RepositoryProvider
import com.emabuia.pokevault.data.remote.TcgCard
import com.emabuia.pokevault.util.IllustratorEntry
import com.emabuia.pokevault.util.IllustratorRow
import com.emabuia.pokevault.util.Illustrators
import com.emabuia.pokevault.util.minimumEurPriceOrZero
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * La sezione illustratori: l'indice del catalogo incrociato con la collezione.
 *
 * Nessun album da creare e niente di congelato, a differenza dei chase: il
 * progresso di ogni artista si ricalcola dal vivo sulle carte possedute, quindi
 * una carta aggiunta oggi sposta subito tutte le barre che la riguardano.
 */
class IllustratorViewModel : ViewModel() {

    private val repository = FirestoreRepository()
    private val tcgRepository = RepositoryProvider.tcgRepository

    // ── Stato ──────────────────────────────────────────────────────────────

    var entries by mutableStateOf<List<IllustratorEntry>>(emptyList())
        private set

    var ownedCards by mutableStateOf<List<PokemonCard>>(emptyList())
        private set

    var followedKeys by mutableStateOf<Set<String>>(emptySet())
        private set

    /** Quante carte pubblicate del catalogo non dicono chi le ha disegnate. */
    var cardsWithoutIllustrator by mutableStateOf(0)
        private set

    // Parte a true: il caricamento si avvia in init, quindi al primo frame
    // stiamo gia' caricando. Con false la lista lampeggerebbe "nessun artista".
    var isLoading by mutableStateOf(true)
        private set

    var loadFailed by mutableStateOf(false)
        private set

    // ── Dettaglio ──────────────────────────────────────────────────────────

    var detailCards by mutableStateOf<List<TcgCard>>(emptyList())
        private set

    var isDetailLoading by mutableStateOf(false)
        private set

    private var loadedDetailKey: String? = null

    init {
        loadOwnedCards()
        loadFollowed()
    }

    // ── Caricamento ────────────────────────────────────────────────────────

    /**
     * L'indice arriva da `/v1/illustrators`, non dal catalogo intero: serve
     * quindi un Context, e per questo non sta in `init` come gli altri.
     * Chiamarlo piu' volte non ricarica niente, se non si forza.
     */
    fun loadIndex(context: Context, forceRefresh: Boolean = false) {
        if (entries.isNotEmpty() && !forceRefresh) return
        viewModelScope.launch {
            isLoading = true
            loadFailed = false
            val loaded = tcgRepository.italianIllustratorIndex(context, forceRefresh)
            entries = loaded
            cardsWithoutIllustrator = tcgRepository.italianCardsWithoutIllustrator(context)
            loadFailed = loaded.isEmpty()
            isLoading = false
        }
    }

    private fun loadOwnedCards() {
        viewModelScope.launch {
            repository.getCards()
                .catch { }
                .collectLatest { cards -> ownedCards = cards }
        }
    }

    private fun loadFollowed() {
        viewModelScope.launch {
            repository.getFollowedIllustrators()
                .catch { }
                .collectLatest { keys -> followedKeys = keys }
        }
    }

    // ── Incroci ────────────────────────────────────────────────────────────

    /**
     * Gli apiCardId posseduti, indicizzati una volta sola.
     *
     * Ricostruirlo dentro il conteggio di ogni riga vorrebbe dire, con
     * quattrocento artisti e mille carte in collezione, quattrocentomila trim()
     * per frame di scroll. Stessa ragione per cui i chase lo fanno qui.
     */
    private val ownedApiIds: Set<String> by derivedStateOf {
        ownedCards
            .asSequence()
            .filter { it.quantity >= 1 }
            .map { it.apiCardId.trim() }
            .toHashSet()
    }

    /**
     * Quante carte in collezione NON vengono dal catalogo italiano.
     *
     * Il progresso si calcola incrociando gli apiCardId, e quelli italiani
     * hanno la forma `ita:<set>:<numero>`. Una carta aggiunta dal vecchio
     * percorso inglese ha un id di tutt'altra forma: non si riconosce, e non
     * conta per nessun illustratore. Non e' un errore da correggere in
     * silenzio, e' un numero da dichiarare -- altrimenti chi ha una collezione
     * costruita anni fa vede zero ovunque senza capire perche'.
     */
    val nonItalianOwnedCount: Int by derivedStateOf {
        ownedCards.count { it.quantity >= 1 && !it.apiCardId.trim().startsWith("ita:") }
    }

    /** Le righe della lista, con l'avanzamento gia' calcolato. */
    val rows: List<IllustratorRow> by derivedStateOf {
        Illustrators.rows(entries, ownedApiIds, followedKeys)
    }

    fun rowFor(key: String): IllustratorRow? = rows.firstOrNull { it.key == key }

    fun entryFor(key: String): IllustratorEntry? = entries.firstOrNull { it.key == key }

    // ── Segui ──────────────────────────────────────────────────────────────

    fun toggleFollow(key: String) {
        val nowFollowed = key !in followedKeys
        // Ottimistico: la stellina risponde al tocco senza aspettare la rete, e
        // lo snapshot listener confermera' o rimettera' le cose a posto.
        followedKeys = if (nowFollowed) followedKeys + key else followedKeys - key
        viewModelScope.launch {
            repository.setIllustratorFollowed(key, nowFollowed)
        }
    }

    // ── Dettaglio ──────────────────────────────────────────────────────────

    fun loadDetail(context: Context, key: String, forceRefresh: Boolean = false) {
        if (loadedDetailKey == key && !forceRefresh) return
        val entry = entryFor(key)
        if (entry == null) {
            // L'indice non e' ancora arrivato: lo si aspetta, il dettaglio si
            // ricarichera' quando le voci ci sono.
            detailCards = emptyList()
            return
        }
        loadedDetailKey = key
        viewModelScope.launch {
            isDetailLoading = true
            detailCards = tcgRepository.italianCardsByIllustrator(context, entry, forceRefresh)
            isDetailLoading = false
        }
    }

    /** Le stampe possedute di ogni carta, per i badge sulla miniatura. */
    val ownedVariants: Map<String, Set<String>> by derivedStateOf {
        ownedCards
            .asSequence()
            .filter { it.apiCardId.isNotBlank() && it.variant.isNotBlank() && it.quantity >= 1 }
            .groupBy({ it.apiCardId.trim() }, { it.variant })
            .mapValues { (_, variants) -> variants.toSet() }
    }

    fun isOwned(cardId: String): Boolean = cardId.trim() in ownedApiIds

    // ── Aggiunta rapida ────────────────────────────────────────────────────

    var isAddingCard by mutableStateOf<String?>(null)
        private set

    /**
     * Aggiunge una carta dalla pagina di un illustratore, come si fa dalla
     * pagina di un'espansione.
     *
     * Il prezzo e' il **minimo** di Cardmarket, come ovunque nell'app: e' la
     * cifra a cui la carta si trova davvero, non la media di un mercato che
     * comprende copie in condizioni diverse.
     *
     * Non c'e' aggiornamento ottimistico dello stato posseduto: qui la lista
     * delle carte possedute arriva da un flusso Firestore vivo, e il badge
     * verde compare da solo appena il documento e' scritto. Il bordo blu di
     * [isAddingCard] copre l'attesa.
     */
    fun addCard(
        card: TcgCard,
        variant: String,
        quantity: Int,
        condition: String,
        language: String
    ) {
        viewModelScope.launch {
            isAddingCard = card.id
            val price = card.cardmarket?.prices.minimumEurPriceOrZero()
            val pokemonCard = PokemonCard(
                name = card.name,
                imageUrl = card.images.small,
                set = card.set?.name.orEmpty(),
                rarity = card.rarity.orEmpty(),
                type = card.types?.firstOrNull() ?: "Colorless",
                hp = card.hp?.toIntOrNull() ?: 0,
                supertype = card.supertype.ifBlank { "Pokémon" },
                subtypes = card.subtypes ?: emptyList(),
                estimatedValue = price,
                apiCardId = card.id,
                cardNumber = card.number,
                variant = variant,
                quantity = quantity,
                condition = condition,
                language = language.ifBlank { "🇮🇹 Italiano" }
            )
            repository.addCard(pokemonCard)
            // Il bordo resta visibile un attimo: senza, il riscontro del tocco
            // passa inosservato su una griglia da tre colonne.
            delay(350)
            isAddingCard = null
        }
    }

    /**
     * Stessa funzione della pagina espansione, di proposito: e' quella che sa
     * lasciare stare le copie solo-deck. Ricostruirla qui a mano vorrebbe dire
     * doversi ricordare quel filtro in due posti.
     */
    fun removeCard(card: TcgCard) {
        viewModelScope.launch {
            repository.deleteCardByApiId(card.id)
        }
    }
}

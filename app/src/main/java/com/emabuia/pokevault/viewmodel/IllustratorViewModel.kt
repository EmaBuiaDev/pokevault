package com.emabuia.pokevault.viewmodel

import android.content.Context
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.emabuia.pokevault.data.catalog.CardRef
import com.emabuia.pokevault.data.catalog.CatalogLang
import com.emabuia.pokevault.data.firebase.FirestoreRepository
import com.emabuia.pokevault.data.italian.ItalianPriceSnapshotRepository
import com.emabuia.pokevault.data.model.PokemonCard
import com.emabuia.pokevault.data.remote.PokeWalletPriceData
import com.emabuia.pokevault.data.remote.RepositoryProvider
import com.emabuia.pokevault.data.remote.TcgCard
import com.emabuia.pokevault.util.AppLocale
import com.emabuia.pokevault.util.IllustratorEntry
import com.emabuia.pokevault.util.IllustratorRow
import com.emabuia.pokevault.util.Illustrators
import com.emabuia.pokevault.util.minimumEurOrZero
import com.emabuia.pokevault.util.minimumEurPriceOrZero
import com.emabuia.pokevault.util.withSnapshotPrices
import kotlinx.coroutines.Job
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
/**
 * In che catalogo si guardano gli album per illustratore (piano carte EN).
 * Una lingua alla volta: i conteggi IT ed EN non si sommano. La scelta vale
 * finche' l'app non cambia lingua, come il selettore del Pokedex; aprire un
 * artista dalla scheda di una carta sceglie la lingua di quella carta.
 */
object IllustratorCatalogPref {
    private const val PREFS = "pokedex_prefs"
    private const val KEY = "illustrator_lang"
    private const val KEY_APP = "illustrator_lang_app"

    fun read(context: Context): CatalogLang {
        if (!RepositoryProvider.englishCatalogRepository.isEnabled) return CatalogLang.IT
        val prefs = runCatching { context.getSharedPreferences(PREFS, Context.MODE_PRIVATE) }.getOrNull()
        return defaultCatalogLang(
            runCatching { prefs?.getString(KEY, null) }.getOrNull(),
            runCatching { prefs?.getString(KEY_APP, null) }.getOrNull(),
            AppLocale.current
        )
    }

    fun write(context: Context, lang: CatalogLang) {
        runCatching {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString(KEY, lang.name)
                .putString(KEY_APP, AppLocale.current.code)
                .apply()
        }
    }

    /** Prima di aprire l'artista di una carta: il suo album nella lingua della carta. */
    fun followCard(context: Context, apiCardId: String?) {
        if (!RepositoryProvider.englishCatalogRepository.isEnabled) return
        CardRef.langOf(apiCardId)?.let { write(context, it) }
    }
}

class IllustratorViewModel : ViewModel() {

    private val repository = FirestoreRepository()
    private val tcgRepository = RepositoryProvider.tcgRepository
    private val englishCatalog = RepositoryProvider.englishCatalogRepository

    /** Il catalogo degli album mostrati: italiano, o inglese dove e' acceso. */
    var catalogLang by mutableStateOf(CatalogLang.IT)
        private set

    val englishAvailable: Boolean get() = englishCatalog.isEnabled

    private var langResolved = false

    private val snapshotPrices: ItalianPriceSnapshotRepository
        get() = if (catalogLang == CatalogLang.EN) RepositoryProvider.englishPriceSnapshotRepository
        else RepositoryProvider.italianPriceSnapshotRepository

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

    /**
     * Prezzi della carta aperta nella scheda, dallo snapshot italiano: le carte
     * di questa pagina arrivano dal catalogo, che di suo non ha prezzi, e la
     * scheda mostrava "N/D" anche per carte che nel Pokedex hanno il prezzo.
     */
    var sheetPrices by mutableStateOf<PokeWalletPriceData?>(null)
        private set

    var isSheetPriceLoading by mutableStateOf(false)
        private set

    private var sheetPriceJob: Job? = null

    // Per leggere lo snapshot (cache su disco) anche dall'aggiunta rapida
    // della griglia, che il Context non lo riceve.
    private var appContext: Context? = null

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
        if (!langResolved) {
            langResolved = true
            catalogLang = IllustratorCatalogPref.read(context)
        }
        if (entries.isNotEmpty() && !forceRefresh) return
        val lang = catalogLang
        viewModelScope.launch {
            isLoading = true
            loadFailed = false
            val loaded: List<IllustratorEntry>
            val withoutIllustrator: Int
            if (lang == CatalogLang.EN) {
                loaded = englishCatalog.illustratorIndex(forceRefresh)
                withoutIllustrator = englishCatalog.cardsWithoutIllustrator()
            } else {
                loaded = tcgRepository.italianIllustratorIndex(context, forceRefresh)
                withoutIllustrator = tcgRepository.italianCardsWithoutIllustrator(context)
            }
            // Cambiata lingua nel frattempo: questo risultato non serve piu'.
            if (lang != catalogLang) return@launch
            entries = loaded
            cardsWithoutIllustrator = withoutIllustrator
            loadFailed = loaded.isEmpty()
            isLoading = false
        }
    }

    /** Il selettore IT | EN: ricarica gli album nell'altra lingua e ricorda la scelta. */
    fun selectCatalogLang(context: Context, lang: CatalogLang) {
        if (lang == CatalogLang.EN && !englishCatalog.isEnabled) return
        langResolved = true
        IllustratorCatalogPref.write(context, lang)
        if (lang == catalogLang) return
        catalogLang = lang
        entries = emptyList()
        detailCards = emptyList()
        loadedDetailKey = null
        loadIndex(context)
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
        // Le copie del catalogo inglese non sono "scollegate": contano nei loro
        // album. Restano fuori solo quelle di nessun catalogo.
        ownedCards.count { it.quantity >= 1 && CardRef.langOf(it.apiCardId) == null }
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
        appContext = context.applicationContext
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
            val cards = if (catalogLang == CatalogLang.EN) {
                englishCatalog.cardsByIllustrator(context, entry)
            } else {
                tcgRepository.italianCardsByIllustrator(context, entry, forceRefresh)
            }
            detailCards = cards
            isDetailLoading = false
            // Poi i prezzi, senza far aspettare la griglia: le carte arrivano dal
            // catalogo, che non ne ha, e la scheda mostrava "N/D" anche per carte
            // che nel Pokedex hanno il prezzo.
            val priced = withSnapshotPrices(context, cards)
            if (loadedDetailKey == key) detailCards = priced
        }
    }

    /**
     * Le carte con il prezzo dello snapshot italiano, dove c'e'. Uno snapshot
     * per set ("ita:<set>:<numero>"), che [getPriceMap] tiene in cache: un
     * artista con carte in venti set costa venti letture, mai PokeWallet.
     */
    private suspend fun withSnapshotPrices(context: Context, cards: List<TcgCard>): List<TcgCard> {
        val setCodes = cards.mapNotNull { catalogSetCode(it.id) }.toSet()
        if (setCodes.isEmpty()) return cards
        val bySet = setCodes.associateWith { set ->
            runCatching { snapshotPrices.getPriceMap(context, set) }.getOrDefault(emptyMap())
        }
        return cards.map { card ->
            val set = catalogSetCode(card.id) ?: return@map card
            val number = card.id.substringAfterLast(':').trim()
            val key = number.toIntOrNull()?.toString() ?: number.uppercase(java.util.Locale.ROOT)
            val data = bySet[set]?.get(key)
            if (data != null && data.hasEurPrices && card.cardmarket?.prices.minimumEurPriceOrZero() <= 0.0) {
                card.withSnapshotPrices(data)
            } else {
                card
            }
        }
    }

    private fun catalogSetCode(cardId: String): String? =
        CardRef.parse(cardId)?.takeIf { it.lang == catalogLang }?.setCode

    /** Carica i prezzi della carta aperta nella scheda; null chiude la scheda. */
    fun loadSheetPrices(card: TcgCard?) {
        sheetPriceJob?.cancel()
        sheetPrices = null
        val context = appContext
        if (card == null || context == null) {
            isSheetPriceLoading = false
            return
        }
        sheetPriceJob = viewModelScope.launch {
            isSheetPriceLoading = true
            sheetPrices = RepositoryProvider.priceSnapshotRepositoryFor(card.id).priceForItalianCard(context, card.id)
            isSheetPriceLoading = false
        }
    }

    /**
     * Il prezzo da salvare: quello della STAMPA scelta dallo snapshot italiano
     * dove la carta c'e' (una Reverse non vale quanto la Normale), se no
     * quello della carta, come prima.
     */
    private suspend fun priceFor(card: TcgCard, variant: String): Double {
        val context = appContext
        val italian = context?.let { RepositoryProvider.priceSnapshotRepositoryFor(card.id).priceForItalianCard(it, card.id) }
        if (italian != null) return italian.forVariant(variant).minimumEurOrZero()
        return card.cardmarket?.prices.minimumEurPriceOrZero()
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
            val price = priceFor(card, variant)
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
                language = language.ifBlank { (CardRef.langOf(card.id) ?: CatalogLang.IT).copyLanguageLabel }
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

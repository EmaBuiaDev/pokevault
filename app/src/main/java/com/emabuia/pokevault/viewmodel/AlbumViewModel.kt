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
import com.emabuia.pokevault.data.model.Album
import com.emabuia.pokevault.data.model.AlbumCatalogCard
import com.emabuia.pokevault.data.model.AlbumCover
import com.emabuia.pokevault.data.model.BinderLayout
import com.emabuia.pokevault.data.model.CardOptions
import com.emabuia.pokevault.data.model.PokemonCard
import com.emabuia.pokevault.data.remote.RepositoryProvider
import com.emabuia.pokevault.data.remote.TcgCard
import com.emabuia.pokevault.data.remote.TcgCardSet
import com.emabuia.pokevault.util.AlbumEntries
import com.emabuia.pokevault.util.AlbumRow
import com.emabuia.pokevault.util.AlbumSlot
import com.emabuia.pokevault.util.AppLocale
import com.emabuia.pokevault.util.PokemonSpriteResolver
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class AlbumViewModel : ViewModel() {
    private val repository = FirestoreRepository()
    private val tcgRepository by lazy { RepositoryProvider.tcgRepository }
    private val englishCatalog by lazy { RepositoryProvider.englishCatalogRepository }

    var albums by mutableStateOf<List<Album>>(emptyList())
        private set

    var ownedCards by mutableStateOf<List<PokemonCard>>(emptyList())
        private set

    // Parte a true: il loader viene avviato in init, quindi al primo frame
    // stiamo gia' caricando. Con false, un dettaglio lampeggiava "non trovato".
    var isLoading by mutableStateOf(true)
        private set

    var isSaving by mutableStateOf(false)
        private set

    // Create/Edit Album state
    var editingAlbumId by mutableStateOf<String?>(null)
    var albumName by mutableStateOf("")
    var albumDescription by mutableStateOf("")
    var albumPokemonType by mutableStateOf("")
    var albumExpansion by mutableStateOf("")
    var albumSupertype by mutableStateOf("")
    var albumSize by mutableStateOf(18)
    var albumTheme by mutableStateOf("classic")
    var albumLayout by mutableStateOf(BinderLayout.DEFAULT)
    var albumCoverPattern by mutableStateOf(AlbumCover.DOTS)
    var albumCoverPokemon by mutableStateOf(0)

    // Ricerca del Pokemon di copertina: la tabella delle specie sta negli asset.
    var speciesQuery by mutableStateOf("")
        private set
    var speciesResults by mutableStateOf<List<PokemonSpriteResolver.Species>>(emptyList())
        private set
    private var allSpecies: List<PokemonSpriteResolver.Species> = emptyList()

    /** Le carte gia' nell'album in modifica: sotto questo numero di tasche non si scende. */
    var editingCardCount by mutableStateOf(0)
        private set

    // ── Ricerca nel catalogo ─────────────────────────────────────────────

    var catalogQuery by mutableStateOf("")
        private set
    var catalogResults by mutableStateOf<List<AlbumCatalogCard>>(emptyList())
        private set
    var isSearchingCatalog by mutableStateOf(false)
        private set
    var catalogSearchFailed by mutableStateOf(false)
        private set
    var catalogLang by mutableStateOf(if (englishAvailableSafe()) preferredCatalogLang() else CatalogLang.IT)
        private set

    val englishAvailable: Boolean get() = englishAvailableSafe()

    private var searchJob: Job? = null

    init {
        loadAlbums()
        loadOwnedCards()
    }

    private fun englishAvailableSafe(): Boolean =
        runCatching { RepositoryProvider.englishCatalogRepository.isEnabled }.getOrDefault(false)

    private fun loadAlbums() {
        viewModelScope.launch {
            isLoading = true
            repository.getAlbums()
                .catch { isLoading = false }
                .collectLatest { list ->
                    albums = list
                    isLoading = false
                }
        }
    }

    private fun loadOwnedCards() {
        viewModelScope.launch {
            repository.getCards()
                .catch { /* ignore */ }
                .collectLatest { cards ->
                    ownedCards = cards
                }
        }
    }

    fun getAlbumById(albumId: String): Album? {
        return albums.find { it.id == albumId }
    }

    /**
     * Indice per id delle carte possedute.
     *
     * getCardsForAlbum faceva ownedCards.find { } per ogni id dell'album, cioe'
     * O(carte album x carte possedute). Veniva chiamata anche dentro la lambda
     * items{} della lista album, quindi per ogni album visibile a ogni frame
     * durante lo scroll.
     */
    private val ownedCardsById by derivedStateOf {
        ownedCards.associateBy { it.id }
    }

    /** La copia posseduta di ogni carta, per apiCardId: accende le tasche "da trovare". */
    val ownedByApiId: Map<String, PokemonCard> by derivedStateOf {
        AlbumEntries.ownedByApiId(ownedCards)
    }

    /** Le tasche dell'album, carte della collezione e del catalogo, nell'ordine scelto. */
    fun slotsFor(album: Album): List<AlbumSlot> =
        AlbumEntries.resolve(album, ownedCardsById, ownedByApiId)

    /**
     * Le righe della lista album, con copertina, riempimento e valore.
     *
     * Derivate una volta per ogni cambio di album o collezione: prima la lista
     * rifaceva questi conti dentro `items { }`, cioe' per ogni album visibile a
     * ogni frame dello scroll.
     */
    val albumRows: List<AlbumRow> by derivedStateOf {
        val byId = ownedCardsById
        val byApiId = ownedByApiId
        albums.map { album ->
            val slots = AlbumEntries.resolve(album, byId, byApiId)
            AlbumRow(
                id = album.id,
                name = album.name,
                description = album.description,
                theme = album.theme,
                pokemonType = album.pokemonType,
                coverUrl = album.coverImageUrl.ifBlank { slots.firstOrNull()?.imageUrl ?: "" },
                previewUrls = slots.take(3).map { it.imageUrl }.filter { it.isNotBlank() },
                used = album.cardIds.size,
                size = album.size,
                // Una copia per slot, quindi senza `* quantity` come fa
                // StatsViewModel sul totale della collezione: nell'album entra
                // la carta, non la pila di doppie. E gli id di carte non piu'
                // in collezione non valgono niente: si sommano solo le trovate.
                value = slots.sumOf { it.value },
                createdAtSeconds = album.createdAt?.seconds ?: 0L,
                coverPattern = album.coverPattern,
                coverPokemon = album.coverPokemon,
                // Si tolgono solo le tasche "da trovare": un album di sole carte
                // della collezione resta contato esattamente come prima.
                found = album.cardIds.size - slots.count { !it.isOwned }
            )
        }
    }

    /**
     * Mette nell'album le carte scelte nelle due schede del foglio: le tue
     * dalla collezione e quelle del catalogo (che, se le hai gia', entrano
     * come tua copia; se no "da trovare"). Torna quante ne sono entrate.
     */
    fun addToAlbum(album: Album, ownedIds: List<String>, picked: List<AlbumCatalogCard>): Int {
        val plan = AlbumEntries.planAdditions(album, ownedIds, picked, ownedByApiId)
        if (plan.isEmpty) return 0
        viewModelScope.launch {
            repository.addAlbumEntries(album.id, plan.entries, plan.catalogCards)
        }
        return plan.entries.size
    }

    /** La copertina la sceglie l'utente fra le carte che ha messo nell'album. */
    fun setAlbumCover(albumId: String, coverImageUrl: String) {
        viewModelScope.launch {
            repository.updateAlbumCover(albumId, coverImageUrl)
        }
    }

    /**
     * Sposta una carta di [delta] posizioni. Ai bordi non fa niente: uno
     * spostamento che non puo' avvenire non deve diventare una riscrittura.
     */
    fun moveCardInAlbum(albumId: String, cardId: String, delta: Int) {
        val album = getAlbumById(albumId) ?: return
        val ids = album.cardIds.toMutableList()
        val from = ids.indexOf(cardId)
        if (from < 0) return
        val to = (from + delta).coerceIn(0, ids.lastIndex)
        if (to == from) return
        ids.removeAt(from)
        ids.add(to, cardId)
        viewModelScope.launch { repository.setAlbumCardIds(albumId, ids) }
    }

    /** Rimette una carta dove stava: e' l'annulla dello snackbar di rimozione. */
    fun restoreCardToAlbum(albumId: String, cardId: String, index: Int, catalog: AlbumCatalogCard? = null) {
        val album = getAlbumById(albumId) ?: return
        if (cardId in album.cardIds) return
        if (AlbumEntries.apiIdOf(cardId) == null) {
            val ids = album.cardIds.toMutableList()
            ids.add(index.coerceIn(0, ids.size), cardId)
            viewModelScope.launch { repository.setAlbumCardIds(albumId, ids) }
        } else {
            val content = AlbumEntries.withEntryAt(album, cardId, index, catalog)
            viewModelScope.launch { repository.setAlbumContent(albumId, content.cardIds, content.catalogCards) }
        }
    }

    fun getFilteredCardsForAlbum(album: Album): List<PokemonCard> {
        val albumTypeEn = AppLocale.typeToEnglish(album.pokemonType)
        // Una carta gia' nell'album "da trovare" non si ripropone dalla
        // collezione: entrerebbe due volte.
        val asCatalog = AlbumEntries.catalogApiIds(album)
        return ownedCards.filter { card ->
            val cardTypeEn = AppLocale.typeToEnglish(card.type)
            val matchesType = album.pokemonType.isBlank() ||
                    cardTypeEn.equals(albumTypeEn, ignoreCase = true)
            val matchesExpansion = album.expansion.isBlank() ||
                    card.set.equals(album.expansion, ignoreCase = true)
            val matchesSupertype = album.supertype.isBlank() ||
                    card.classify().equals(album.supertype, ignoreCase = true)
            val notAlreadyInAlbum = card.id !in album.cardIds && card.apiCardId.trim() !in asCatalog
            matchesType && matchesExpansion && matchesSupertype && notAlreadyInAlbum
        }
    }

    fun getAvailableExpansions(): List<String> {
        return ownedCards.map { it.set }.filter { it.isNotBlank() }.distinct().sorted()
    }

    // ── Catalogo ─────────────────────────────────────────────────────────

    /**
     * Cerca nel catalogo mentre si scrive. Si aspetta un attimo fra un tasto
     * e l'altro: la ricerca scorre tutto il catalogo in memoria, e farla a
     * ogni lettera rallenterebbe la tastiera.
     */
    fun searchCatalog(query: String, context: Context) {
        catalogQuery = query
        searchJob?.cancel()
        val q = query.trim()
        if (q.length < 2) {
            catalogResults = emptyList()
            catalogSearchFailed = false
            isSearchingCatalog = false
            return
        }
        isSearchingCatalog = true
        val appContext = context.applicationContext
        val lang = catalogLang
        searchJob = viewModelScope.launch {
            delay(SEARCH_DEBOUNCE_MS)
            val result = if (lang == CatalogLang.EN) {
                englishCatalog.searchByName(appContext, q, limit = SEARCH_LIMIT)
            } else {
                tcgRepository.searchItalianCardsByName(q, appContext, limit = SEARCH_LIMIT)
            }
            result
                .onSuccess { cards ->
                    catalogResults = cards.map(AlbumEntries::fromTcgCard).filter { it.apiId.isNotBlank() }
                    catalogSearchFailed = false
                }
                .onFailure {
                    catalogResults = emptyList()
                    catalogSearchFailed = true
                }
            isSearchingCatalog = false
        }
    }

    fun chooseCatalogLang(lang: CatalogLang, context: Context) {
        val chosen = if (lang == CatalogLang.EN && !englishAvailable) CatalogLang.IT else lang
        if (chosen == catalogLang) return
        catalogLang = chosen
        searchCatalog(catalogQuery, context)
    }

    fun clearCatalogSearch() {
        searchJob?.cancel()
        catalogQuery = ""
        catalogResults = emptyList()
        catalogSearchFailed = false
        isSearchingCatalog = false
    }

    /**
     * "Ce l'ho": la carta da trovare entra in collezione con le scelte di
     * base (stampa principale, Near Mint, una copia). La tasca si accende da
     * sola appena la collezione la riporta, senza toccare l'album.
     */
    fun addCatalogCardToCollection(card: AlbumCatalogCard, onResult: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            val tcg = TcgCard(
                id = card.apiId,
                name = card.name,
                number = card.number,
                rarity = card.rarity.ifBlank { null },
                set = TcgCardSet(id = card.setId, name = card.set)
            )
            val variant = CardOptions.getBaseVariantsForCard(tcg).firstOrNull() ?: "Normal"
            val pokemonCard = PokemonCard(
                name = card.name,
                imageUrl = card.imageUrl,
                set = card.set,
                rarity = card.rarity,
                type = card.type.ifBlank { "Colorless" },
                hp = card.hp,
                supertype = card.supertype.ifBlank { "Pokémon" },
                estimatedValue = card.price,
                apiCardId = card.apiId,
                cardNumber = card.number,
                variant = variant,
                quantity = 1,
                condition = "Near Mint",
                language = (CardRef.langOf(card.apiId) ?: CatalogLang.IT).copyLanguageLabel
            )
            repository.addCard(pokemonCard)
                .onSuccess { onResult(true) }
                .onFailure { onResult(false) }
        }
    }

    // ── Pokemon di copertina ─────────────────────────────────────────────

    /** Cerca la specie per nome: la tabella si legge dagli asset una volta sola. */
    fun searchSpecies(query: String, context: Context) {
        speciesQuery = query
        if (query.isBlank()) {
            speciesResults = emptyList()
            return
        }
        viewModelScope.launch {
            if (allSpecies.isEmpty()) allSpecies = PokemonSpriteResolver.allSpecies(context)
            // Il testo puo' essere cambiato mentre si caricava la tabella.
            if (speciesQuery == query) speciesResults = PokemonSpriteResolver.searchSpecies(allSpecies, query)
        }
    }

    // ── Salvataggio ──────────────────────────────────────────────────────

    fun saveAlbum(onSuccess: () -> Unit = {}) {
        if (albumName.isBlank()) return
        viewModelScope.launch {
            isSaving = true
            val existing = editingAlbumId?.let { id -> albums.find { it.id == id } }
            val cardIds = existing?.cardIds ?: emptyList()
            val album = Album(
                id = editingAlbumId ?: "",
                name = albumName.trim(),
                description = albumDescription.trim(),
                pokemonType = albumPokemonType,
                expansion = albumExpansion,
                supertype = albumSupertype,
                // Mai sotto le carte gia' dentro: rimpicciolire un album pieno
                // non deve far sparire tasche occupate.
                size = albumSize.coerceAtLeast(cardIds.size).coerceAtLeast(1),
                theme = albumTheme,
                cardIds = cardIds,
                coverImageUrl = existing?.coverImageUrl ?: "",
                layout = albumLayout.key,
                coverPattern = albumCoverPattern,
                coverPokemon = albumCoverPokemon,
                catalogCards = existing?.catalogCards ?: emptyList(),
                createdAt = existing?.createdAt
            )
            repository.saveAlbum(album)
            isSaving = false
            resetForm()
            onSuccess()
        }
    }

    fun deleteAlbum(albumId: String) {
        viewModelScope.launch {
            repository.deleteAlbum(albumId)
        }
    }

    /**
     * Toglie una tasca. Per una carta del catalogo si riscrive anche l'elenco
     * delle carte del catalogo, cosi' non restano dati che nessuna tasca usa.
     */
    fun removeCardFromAlbum(albumId: String, cardId: String) {
        if (AlbumEntries.apiIdOf(cardId) == null) {
            viewModelScope.launch { repository.removeCardFromAlbum(albumId, cardId) }
            return
        }
        val album = getAlbumById(albumId) ?: return
        val content = AlbumEntries.withoutEntry(album, cardId)
        viewModelScope.launch { repository.setAlbumContent(albumId, content.cardIds, content.catalogCards) }
    }

    fun loadAlbumForEdit(album: Album) {
        editingAlbumId = album.id
        albumName = album.name
        albumDescription = album.description
        albumPokemonType = album.pokemonType
        albumExpansion = album.expansion
        albumSupertype = album.supertype
        albumSize = album.size
        albumTheme = album.theme
        albumLayout = BinderLayout.of(album.layout)
        // Un album di prima, aperto nel modulo, passa alla copertina disegnata.
        albumCoverPattern = album.coverPattern.takeIf { AlbumCover.isDesigned(it) } ?: AlbumCover.DOTS
        albumCoverPokemon = album.coverPokemon
        editingCardCount = album.cardIds.size
    }

    fun resetForm() {
        editingAlbumId = null
        albumName = ""
        albumDescription = ""
        albumPokemonType = ""
        albumExpansion = ""
        albumSupertype = ""
        albumSize = 18
        albumTheme = "classic"
        albumLayout = BinderLayout.DEFAULT
        editingCardCount = 0
        albumCoverPattern = AlbumCover.DOTS
        albumCoverPokemon = 0
        speciesQuery = ""
        speciesResults = emptyList()
    }

    private companion object {
        const val SEARCH_DEBOUNCE_MS = 300L
        const val SEARCH_LIMIT = 60
    }
}

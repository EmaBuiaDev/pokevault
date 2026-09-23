package com.emabuia.pokevault.viewmodel

import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.emabuia.pokevault.data.firebase.FirestoreRepository
import com.emabuia.pokevault.data.model.Album
import com.emabuia.pokevault.data.model.PokemonCard
import com.emabuia.pokevault.util.AlbumRow
import com.emabuia.pokevault.util.AppLocale
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class AlbumViewModel : ViewModel() {
    private val repository = FirestoreRepository()

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
    var albumSize by mutableStateOf(9)
    var albumTheme by mutableStateOf("classic")

    init {
        loadAlbums()
        loadOwnedCards()
    }

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

    fun getCardsForAlbum(album: Album): List<PokemonCard> {
        val byId = ownedCardsById
        return album.cardIds.mapNotNull { byId[it] }
    }

    /**
     * Le righe della lista album, con copertina, riempimento e valore.
     *
     * Derivate una volta per ogni cambio di album o collezione: prima la lista
     * rifaceva questi conti dentro `items { }`, cioe' per ogni album visibile a
     * ogni frame dello scroll.
     */
    val albumRows: List<AlbumRow> by derivedStateOf {
        val byId = ownedCardsById
        albums.map { album ->
            val cards = album.cardIds.mapNotNull { byId[it] }
            AlbumRow(
                id = album.id,
                name = album.name,
                description = album.description,
                theme = album.theme,
                pokemonType = album.pokemonType,
                coverUrl = album.coverImageUrl.ifBlank { cards.firstOrNull()?.imageUrl ?: "" },
                previewUrls = cards.take(3).map { it.imageUrl }.filter { it.isNotBlank() },
                used = album.cardIds.size,
                size = album.size,
                // Una copia per slot, quindi senza `* quantity` come fa
                // StatsViewModel sul totale della collezione: nell'album entra
                // la carta, non la pila di doppie. E gli id di carte non piu'
                // in collezione non valgono niente: si sommano solo le trovate.
                value = cards.sumOf { it.estimatedValue },
                createdAtSeconds = album.createdAt?.seconds ?: 0L
            )
        }
    }

    fun getAlbumValue(album: Album): Double =
        getCardsForAlbum(album).sumOf { it.estimatedValue }

    fun addCardsToAlbum(albumId: String, cardIds: List<String>) {
        if (cardIds.isEmpty()) return
        viewModelScope.launch {
            repository.addCardsToAlbum(albumId, cardIds)
        }
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
    fun restoreCardToAlbum(albumId: String, cardId: String, index: Int) {
        val album = getAlbumById(albumId) ?: return
        if (cardId in album.cardIds) return
        val ids = album.cardIds.toMutableList()
        ids.add(index.coerceIn(0, ids.size), cardId)
        viewModelScope.launch { repository.setAlbumCardIds(albumId, ids) }
    }

    fun getFilteredCardsForAlbum(album: Album): List<PokemonCard> {
        val albumTypeEn = AppLocale.typeToEnglish(album.pokemonType)
        return ownedCards.filter { card ->
            val cardTypeEn = AppLocale.typeToEnglish(card.type)
            val matchesType = album.pokemonType.isBlank() ||
                    cardTypeEn.equals(albumTypeEn, ignoreCase = true)
            val matchesExpansion = album.expansion.isBlank() ||
                    card.set.equals(album.expansion, ignoreCase = true)
            val matchesSupertype = album.supertype.isBlank() ||
                    card.classify().equals(album.supertype, ignoreCase = true)
            val notAlreadyInAlbum = card.id !in album.cardIds
            matchesType && matchesExpansion && matchesSupertype && notAlreadyInAlbum
        }
    }

    fun getAvailableExpansions(): List<String> {
        return ownedCards.map { it.set }.filter { it.isNotBlank() }.distinct().sorted()
    }

    fun saveAlbum(onSuccess: () -> Unit = {}) {
        if (albumName.isBlank()) return
        viewModelScope.launch {
            isSaving = true
            val existing = editingAlbumId?.let { id -> albums.find { it.id == id } }
            val album = Album(
                id = editingAlbumId ?: "",
                name = albumName,
                description = albumDescription,
                pokemonType = albumPokemonType,
                expansion = albumExpansion,
                supertype = albumSupertype,
                size = albumSize,
                theme = albumTheme,
                cardIds = existing?.cardIds ?: emptyList(),
                coverImageUrl = existing?.coverImageUrl ?: "",
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

    fun addCardToAlbum(albumId: String, cardId: String) {
        viewModelScope.launch {
            repository.addCardToAlbum(albumId, cardId)
        }
    }

    fun removeCardFromAlbum(albumId: String, cardId: String) {
        viewModelScope.launch {
            repository.removeCardFromAlbum(albumId, cardId)
        }
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
    }

    fun resetForm() {
        editingAlbumId = null
        albumName = ""
        albumDescription = ""
        albumPokemonType = ""
        albumExpansion = ""
        albumSupertype = ""
        albumSize = 9
        albumTheme = "classic"
    }
}

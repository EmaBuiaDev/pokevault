package com.example.pokevault.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.pokevault.data.firebase.FirestoreRepository
import com.example.pokevault.data.model.Album
import com.example.pokevault.data.model.PokemonCard
import com.example.pokevault.util.AppLocale
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class AlbumViewModel : ViewModel() {
    private val repository = FirestoreRepository()

    var albums by mutableStateOf<List<Album>>(emptyList())
        private set

    var ownedCards by mutableStateOf<List<PokemonCard>>(emptyList())
        private set

    var isLoading by mutableStateOf(false)
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

    fun getCardsForAlbum(album: Album): List<PokemonCard> {
        return album.cardIds.mapNotNull { cardId ->
            ownedCards.find { it.id == cardId }
        }
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

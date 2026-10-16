package com.emabuia.pokevault.data.model

import com.google.firebase.Timestamp

data class Album(
    val id: String = "",
    val name: String = "",
    val description: String = "",
    val pokemonType: String = "",       // Filtro per tipo Pokémon (Fire, Water, etc.) - vuoto = tutti
    val expansion: String = "",         // Filtro per espansione specifica - vuoto = tutte
    val supertype: String = "",         // Filtro per categoria (Pokémon, Trainer, Energy) - vuoto = tutti
    val size: Int = 9,                  // Tasche totali dell'album
    val theme: String = "classic",      // Tematica visuale (classic, fire, water, grass, electric, dark, psychic)
    /**
     * Le tasche, in ordine. Ogni voce e' l'id di una carta della collezione
     * oppure `cat:<apiCardId>` per una carta cercata nel catalogo e non
     * ancora posseduta (vedi [AlbumEntries]). Il nome resta `cardIds` per gli
     * album gia' salvati.
     */
    val cardIds: List<String> = emptyList(),
    val coverImageUrl: String = "",
    /** Come e' fatta una pagina del raccoglitore: "2x2", "3x3" o "4x3" (vedi [BinderLayout]). */
    val layout: String = BinderLayout.DEFAULT.key,
    /** Le carte del catalogo messe nelle tasche, con quanto serve a mostrarle offline. */
    val catalogCards: List<AlbumCatalogCard> = emptyList(),
    /**
     * Il motivo della copertina disegnata ([AlbumCover]). Vuoto = album nato
     * prima delle copertine: continua a mostrare le sue carte.
     */
    val coverPattern: String = "",
    /** Il Pokemon della copertina, numero di Pokedex; 0 = nessuno. */
    val coverPokemon: Int = 0,
    val createdAt: Timestamp? = null
)

/** I motivi della copertina. Il colore e' quello dell'album ([Album.theme]). */
object AlbumCover {
    const val DOTS = "dots"
    const val STRIPES = "stripes"
    const val PLAIN = "plain"

    val PATTERNS = listOf(DOTS, STRIPES, PLAIN)

    /** Ha una copertina disegnata, o mostra ancora le carte come prima. */
    fun isDesigned(pattern: String): Boolean = pattern in PATTERNS

    /** L'artwork ufficiale del Pokemon: grande, nitido, su sfondo trasparente. */
    fun artworkUrl(dexNumber: Int): String? =
        if (dexNumber <= 0) null
        else "https://raw.githubusercontent.com/PokeAPI/sprites/master/sprites/pokemon/other/official-artwork/$dexNumber.png"
}

/**
 * Una carta del catalogo dentro un album, anche se non la possiedi.
 *
 * Si salva una fotografia minima della carta (nome, immagine, set) cosi'
 * l'album si apre senza rete e senza dover ricercare ogni tasca.
 */
data class AlbumCatalogCard(
    val apiId: String = "",
    val name: String = "",
    val imageUrl: String = "",
    val set: String = "",
    val setId: String = "",
    val number: String = "",
    val rarity: String = "",
    val type: String = "",
    val supertype: String = "",
    val hp: Int = 0,
    val price: Double = 0.0
)

/**
 * La pagina del raccoglitore: quante tasche per riga e quante righe.
 * Sono i tre formati che si trovano in negozio.
 */
enum class BinderLayout(val key: String, val columns: Int, val rows: Int) {
    POCKET_4("2x2", 2, 2),
    POCKET_9("3x3", 3, 3),
    POCKET_12("4x3", 4, 3);

    val perPage: Int get() = columns * rows

    companion object {
        val DEFAULT = POCKET_9

        /** Gli album nati prima del campo, o con un valore sconosciuto, sono da nove. */
        fun of(key: String?): BinderLayout = entries.firstOrNull { it.key == key } ?: DEFAULT
    }
}

/**
 * La carta come la scrive Firestore. Esplicita apposta: `FieldValue.arrayUnion`
 * confronta le mappe campo per campo, e due scritture della stessa carta
 * devono produrre la stessa mappa per non sdoppiarla.
 */
fun AlbumCatalogCard.toFirestoreMap(): Map<String, Any> = mapOf(
    "apiId" to apiId,
    "name" to name,
    "imageUrl" to imageUrl,
    "set" to set,
    "setId" to setId,
    "number" to number,
    "rarity" to rarity,
    "type" to type,
    "supertype" to supertype,
    "hp" to hp,
    "price" to price
)

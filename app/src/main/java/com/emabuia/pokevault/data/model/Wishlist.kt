package com.emabuia.pokevault.data.model

import com.google.firebase.Timestamp

/**
 * Una lista dei desideri.
 *
 * [budgetEur] e' il tetto che l'utente si da' per quella lista, zero se non lo
 * ha messo: la wishlist non e' un elenco di sogni ma una spesa da pianificare,
 * e senza un tetto il totale in cima non ha niente contro cui essere letto.
 */
data class Wishlist(
    val id: String = "",
    val name: String = "",
    val iconKey: String = WishlistIcons.DEFAULT,
    val accentKey: String = "",
    val budgetEur: Double = 0.0,
    val cardIds: List<String> = emptyList(),
    val createdAt: Timestamp? = null
) {
    /**
     * L'accento da usare per questa lista.
     *
     * Vuoto significa "quello dell'icona": le liste salvate prima che il colore
     * si potesse scegliere non devono diventare tutte viola.
     */
    val resolvedAccentKey: String
        get() = accentKey.takeIf { it in WishlistAccents.all }
            ?: WishlistIcons.defaultAccentFor(iconKey)
}

/** Quello che serve per creare o rinominare una lista. */
data class WishlistDraft(
    val name: String,
    val iconKey: String,
    val accentKey: String,
    val budgetEur: Double
)

/**
 * Le icone delle wishlist.
 *
 * Prima erano cinque e si chiamavano Poke Ball, Master Ball, Pikachu, Charizard
 * ed Eevee — disegnate pero' con le icone Material piu' vicine per modo di
 * dire: una stella per la Master Ball, un fulmine per Pikachu, un'impronta per
 * Eevee. Due problemi in uno. Il primo e' che il disegno non era la cosa che
 * diceva di essere. Il secondo, piu' grosso: sapere che una lista "e' Eevee"
 * non dice niente di quella lista, e l'icona di una lista serve a riconoscerla
 * in mezzo alle altre.
 *
 * Adesso le chiavi dicono *a cosa serve la lista*. Le quattro ball sono la
 * scala di priorita' — nel linguaggio Pokemon una Master Ball e' la carta che
 * non ti puo' sfuggire — e sono disegnate davvero (vedi `WishlistGlyphs`); le
 * altre sono gli usi concreti che una lista ha davvero: il budget, lo scambio,
 * il regalo, la gradazione, il deck, il set da chiudere.
 */
object WishlistIcons {
    // Priorita': ball vere, disegnate.
    const val POKE_BALL = "poke_ball"
    const val GREAT_BALL = "great_ball"
    const val ULTRA_BALL = "ultra_ball"
    const val MASTER_BALL = "master_ball"

    // Uso della lista.
    const val BUDGET = "budget"
    const val TRADE = "trade"
    const val GIFT = "gift"
    const val GRADED = "graded"
    const val DECK = "deck"
    const val SET = "set"

    const val DEFAULT = POKE_BALL

    val all = listOf(
        POKE_BALL, GREAT_BALL, ULTRA_BALL, MASTER_BALL,
        BUDGET, TRADE, GIFT, GRADED, DECK, SET
    )

    /**
     * Le chiavi del vecchio catalogo.
     *
     * Le liste gia' salvate su Firestore hanno ancora queste: senza la mappa
     * finirebbero tutte sull'icona di default, cioe' diventerebbero
     * indistinguibili proprio nel momento in cui le icone iniziano a voler dire
     * qualcosa. La corrispondenza tiene il livello di "pregio" che l'utente
     * aveva scelto: pikachu era la lista vivace, charizard quella dei pezzi
     * grossi.
     */
    private val legacy = mapOf(
        "pokeball" to POKE_BALL,
        "master_ball" to MASTER_BALL,
        "pikachu" to GREAT_BALL,
        "charizard" to ULTRA_BALL,
        "eevee" to GIFT
    )

    /** Accenti di provenienza, per non cambiare colore alle liste gia' create. */
    private val legacyAccent = mapOf(
        "pokeball" to WishlistAccents.RED,
        "master_ball" to WishlistAccents.PURPLE,
        "pikachu" to WishlistAccents.GOLD,
        "charizard" to WishlistAccents.ORANGE,
        "eevee" to WishlistAccents.BLUE
    )

    fun normalize(iconKey: String): String = when {
        iconKey in all -> iconKey
        legacy.containsKey(iconKey) -> legacy.getValue(iconKey)
        else -> DEFAULT
    }

    /** Il colore che quell'icona si porta dietro quando non se ne sceglie uno. */
    fun defaultAccentFor(iconKey: String): String {
        legacyAccent[iconKey]?.let { return it }
        return when (normalize(iconKey)) {
            POKE_BALL -> WishlistAccents.RED
            GREAT_BALL -> WishlistAccents.BLUE
            ULTRA_BALL -> WishlistAccents.GOLD
            MASTER_BALL -> WishlistAccents.PURPLE
            BUDGET -> WishlistAccents.GREEN
            TRADE -> WishlistAccents.BLUE
            GIFT -> WishlistAccents.RED
            GRADED -> WishlistAccents.GOLD
            DECK -> WishlistAccents.PURPLE
            SET -> WishlistAccents.ORANGE
            else -> WishlistAccents.RED
        }
    }
}

/**
 * I colori che una lista puo' prendere.
 *
 * Sono chiavi e non valori: il colore vero lo risolve il tema, perche' lo
 * stesso rosso non puo' valere su fondo chiaro e su fondo scuro.
 */
object WishlistAccents {
    const val RED = "red"
    const val ORANGE = "orange"
    const val GOLD = "gold"
    const val GREEN = "green"
    const val BLUE = "blue"
    const val PURPLE = "purple"

    val all = listOf(RED, ORANGE, GOLD, GREEN, BLUE, PURPLE)

    fun normalize(accentKey: String, iconKey: String): String =
        accentKey.takeIf { it in all } ?: WishlistIcons.defaultAccentFor(iconKey)
}

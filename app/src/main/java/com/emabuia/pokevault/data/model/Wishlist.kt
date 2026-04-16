package com.emabuia.pokevault.data.model

import com.google.firebase.Timestamp

data class Wishlist(
    val id: String = "",
    val name: String = "",
    val iconKey: String = WishlistIcons.POKEBALL,
    val cardIds: List<String> = emptyList(),
    val createdAt: Timestamp? = null
)

object WishlistIcons {
    const val POKEBALL = "pokeball"
    const val MASTER_BALL = "master_ball"
    const val PIKACHU = "pikachu"
    const val CHARIZARD = "charizard"
    const val EEVEE = "eevee"

    val all = listOf(POKEBALL, MASTER_BALL, PIKACHU, CHARIZARD, EEVEE)
}

package com.emabuia.pokevault.util

fun getTypeEmojiForCollection(type: String): String {
    return when (type.lowercase()) {
        "fire", "fuoco" -> "🔥"
        "water", "acqua" -> "💧"
        "grass", "erba" -> "🌿"
        "lightning", "elettro" -> "⚡"
        "psychic", "psico" -> "🔮"
        "fighting", "lotta" -> "👊"
        "darkness", "buio" -> "🌑"
        "metal", "metallo" -> "⚙️"
        "dragon", "drago" -> "🐉"
        "fairy", "folletto" -> "🧚"
        "colorless" -> "⭐"
        else -> "🎴"
    }
}

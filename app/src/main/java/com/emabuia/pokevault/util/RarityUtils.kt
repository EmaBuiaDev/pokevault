package com.emabuia.pokevault.util

import androidx.compose.ui.graphics.Color

data class RarityInfo(
    val emoji: String,
    val color: Color,
    val label: String,
    val sortOrder: Int
)

object RarityUtils {
    fun getRarityInfo(rarity: String?): RarityInfo {
        val r = rarity?.lowercase()?.trim() ?: ""
        val isIt = AppLocale.isItalian

        return when {
            // Priorità alle stringhe più specifiche per evitare raggruppamenti errati

            // 4. ACE SPEC
            r.contains("ace spec") ->
                RarityInfo("✦", Color(0xFFD300C5), "Ace Spec", 4)

            // 7. SPECIAL ILLUSTRATION RARE (Due stelle oro)
            // Deve stare sopra Illustration Rare perché ne contiene il nome
            r.contains("special illustration rare") || r.contains("special art rare") ->
                RarityInfo(
                    "☆☆",
                    Color(0xFFEAB308),
                    if (isIt) "Illustr. Spec." else "S. Illustration",
                    7
                )

            // 5. ILLUSTRATION RARE (Stella singola oro/bianca)
            r.contains("illustration rare") || r.contains("rare art") || r.contains("illustrazione rara") ->
                RarityInfo(
                    "☆",
                    Color(0xFFEAB308),
                    if (isIt) "Illustr. Rara" else "Illustration Rare",
                    5
                )

            // 10. SHINY ULTRA RARE (Due stelle shiny)
            // Deve stare sopra Ultra Rare
            r.contains("shiny ultra") || r.contains("ultra rara shiny") ->
                RarityInfo(
                    "★★",
                    Color(0xFF60A5FA),
                    if (isIt) "Ultra Shiny" else "Shiny Ultra Rare",
                    10
                )

            // 6. ULTRA RARE (Due stelle bianche/argento)
            r.contains("ultra rare") || r.contains("ultra rara") || r.contains("full art") ->
                RarityInfo("☆☆", Color(0xFF94A3B8), if (isIt) "Ultra Rara" else "Ultra Rare", 6)

            // 8. HYPER RARE (Tre stelle oro)
            r.contains("hyper rare") || r.contains("iper rara") || r.contains("gold") ->
                RarityInfo("✧", Color(0xFFEAB308), if (isIt) "Iper Rara" else "Hyper Rare", 8)

            // 9. SHINY RARE (Stella singola shiny)
            r.contains("shiny rare") || r.contains("rara shiny") ->
                RarityInfo("★", Color(0xFF60A5FA), if (isIt) "Rara Shiny" else "Shiny Rare", 9)

            // 3. DOUBLE RARE (V, VMAX, VSTAR, ex)
            // Deve stare sopra Rare perché "rare holo v" verrebbe preso da "rare holo"
            r.contains("double rare") || r.contains("doppia rara") || r.contains("ex") ||
                    r.contains("vmax") || r.contains("vstar") || r == "rare holo v" ->
                RarityInfo("★★", Color(0xFF000000), if (isIt) "Doppia Rara" else "Double Rare", 3)

            // 2. RARE (Holo o Standard)
            r == "rare" || r.contains("rare holo") || r == "rara" ->
                RarityInfo("★", Color(0xFF000000), if (isIt) "Rara" else "Rare", 2)

            // 11. PROMO
            r.contains("promo") ->
                RarityInfo("★", Color(0xFFEF4444), "Promo", 11)

            // 1. UNCOMMON
            r.contains("uncommon") || r == "non comune" ->
                RarityInfo("◆", Color(0xFF94A3B8), if (isIt) "Non Comune" else "Uncommon", 1)

            // 0. COMMON
            r.contains("common") && !r.contains("uncommon") || r == "comune" ->
                RarityInfo("●", Color(0xFF94A3B8), if (isIt) "Comune" else "Common", 0)

            else -> RarityInfo("★★", Color(0xFF7FBF6A), if (isIt) "Altro" else "Other", 12)
        }
    }
}

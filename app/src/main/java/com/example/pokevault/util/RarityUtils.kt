package com.example.pokevault.util

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
            // --- COMMON ---
            r.contains("common") && !r.contains("uncommon") ||
            r.contains("comune") && !r.contains("non comune") ->
                RarityInfo("●", Color(0xFF94A3B8), if (isIt) "Comuni" else "Common", 0)

            // --- UNCOMMON ---
            r.contains("uncommon") || r.contains("non comune") ->
                RarityInfo("◆", Color(0xFF94A3B8), if (isIt) "Non Comuni" else "Uncommon", 1)

            // --- ACE SPEC (before generic "rare" matches) ---
            r.contains("ace spec") ->
                RarityInfo("✦", Color(0xFFFFD700), "ACE SPEC", 8)

            // --- PROMO ---
            r.contains("promo") ->
                RarityInfo("★", Color(0xFFEF4444), "Promo", 9)

            // --- SPECIAL ILLUSTRATION RARE (MUST be before Illustration Rare!) ---
            r.contains("special illustration") || r.contains("special art") ||
            r.contains("illustrazione rara speciale") ->
                RarityInfo("☆☆", Color(0xFFFFD700), if (isIt) "Illustraz. Speciale" else "S. Illustration", 5)

            // --- ILLUSTRATION RARE ---
            r.contains("illustration rare") || r.contains("rare holo art") ||
            r.contains("illustrazione rara") ->
                RarityInfo("☆", Color(0xFFFFD700), if (isIt) "Illustraz. Rara" else "Illustration Rare", 4)

            // --- SHINY ULTRA RARE (before ultra rare and shiny rare) ---
            r.contains("shiny ultra") || r.contains("ultra rara shiny") ->
                RarityInfo("✧✧", Color(0xFFFFD700), if (isIt) "Ultra Rara Shiny" else "Shiny Ultra Rare", 7)

            // --- HYPER RARE / RAINBOW / SECRET / CROWN ---
            r.contains("hyper rare") || r.contains("rainbow") || r.contains("rare secret") ||
            r.contains("secret rare") || r.contains("crown") ||
            r.contains("iper rara") || r.contains("rara segreta") || r.contains("rara arcobaleno") ->
                RarityInfo("☆☆☆", Color(0xFFFFD700), if (isIt) "Iper Rara" else "Hyper Rare", 6)

            // --- ULTRA RARE ---
            r.contains("ultra rare") || r.contains("rare ultra") || r.contains("ultra rara") ->
                RarityInfo("★★★", Color(0xFFEAB308), if (isIt) "Ultra Rara" else "Ultra Rare", 6)

            // --- DOUBLE RARE / V / GX / VMAX / VSTAR / RADIANT (before generic Rare!) ---
            r.contains("double rare") || r.contains("rare holo ex") || r.contains("rare holo v") ||
            r.contains("rare holo gx") || r.contains("vmax") || r.contains("vstar") || r.contains("radiant") ||
            r.contains("doppia rara") || r.contains("rara radiante") ->
                RarityInfo("★★", Color(0xFFEAB308), if (isIt) "Doppia Rara" else "Double Rare", 3)

            // --- SHINY RARE ---
            r.contains("shiny rare") || r.contains("rara shiny") ->
                RarityInfo("✧", Color(0xFFFFD700), if (isIt) "Rara Shiny" else "Shiny Rare", 7)

            // --- RARE (Holo / Standard) - most general, must be last ---
            r == "rare" || r.contains("rare holo") || r.contains("holo rare") ||
            r == "rara" || r.contains("rara holo") ->
                RarityInfo("★", Color(0xFFEAB308), if (isIt) "Rara" else "Rare", 2)

            else -> RarityInfo("●", Color(0xFF94A3B8), if (isIt) "Altro" else "Other", 10)
        }
    }
}

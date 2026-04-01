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
        return when {
            // --- COMMON ---
            r.contains("common") && !r.contains("uncommon") ->
                RarityInfo("●", Color(0xFF94A3B8), "Comuni", 0)

            // --- UNCOMMON ---
            r.contains("uncommon") ->
                RarityInfo("◆", Color(0xFF94A3B8), "Non Comuni", 1)

            // --- ACE SPEC (before generic "rare" matches) ---
            r.contains("ace spec") ->
                RarityInfo("✦", Color(0xFFFFD700), "ACE SPEC", 8)

            // --- PROMO ---
            r.contains("promo") ->
                RarityInfo("★", Color(0xFFEF4444), "Promo", 9)

            // --- SPECIAL ILLUSTRATION RARE (MUST be before Illustration Rare!) ---
            r.contains("special illustration") || r.contains("special art") ->
                RarityInfo("☆☆", Color(0xFFFFD700), "S. Illustration", 5)

            // --- ILLUSTRATION RARE ---
            r.contains("illustration rare") || r.contains("rare holo art") ->
                RarityInfo("☆", Color(0xFFFFD700), "Illustration Rare", 4)

            // --- SHINY ULTRA RARE (before ultra rare and shiny rare) ---
            r.contains("shiny ultra") ->
                RarityInfo("✧✧", Color(0xFFFFD700), "Shiny Ultra Rare", 7)

            // --- HYPER RARE / RAINBOW / SECRET / CROWN ---
            r.contains("hyper rare") || r.contains("rainbow") || r.contains("rare secret") ||
            r.contains("secret rare") || r.contains("crown") ->
                RarityInfo("☆☆☆", Color(0xFFFFD700), "Hyper Rare", 6)

            // --- ULTRA RARE ---
            r.contains("ultra rare") || r.contains("rare ultra") ->
                RarityInfo("★★★", Color(0xFFEAB308), "Ultra Rare", 6)

            // --- DOUBLE RARE / V / GX / VMAX / VSTAR / RADIANT (before generic Rare!) ---
            r.contains("double rare") || r.contains("rare holo ex") || r.contains("rare holo v") ||
            r.contains("rare holo gx") || r.contains("vmax") || r.contains("vstar") || r.contains("radiant") ->
                RarityInfo("★★", Color(0xFFEAB308), "Double Rare", 3)

            // --- SHINY RARE ---
            r.contains("shiny rare") ->
                RarityInfo("✧", Color(0xFFFFD700), "Shiny Rare", 7)

            // --- RARE (Holo / Standard) - most general, must be last ---
            r == "rare" || r.contains("rare holo") || r.contains("holo rare") ->
                RarityInfo("★", Color(0xFFEAB308), "Rare", 2)

            else -> RarityInfo("●", Color(0xFF94A3B8), "Altro", 10)
        }
    }
}

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
                RarityInfo("◆", Color(0xFF64748B), "Non Comuni", 1)

            // --- RARE (Holo / Standard) ---
            r == "rare" || r.contains("rare holo") || r.contains("holo rare") || r.contains("shiny rare") -> 
                RarityInfo("★", Color(0xFFEAB308), "Rare", 2)
            
            // --- DOUBLE RARE (ex, V, GX, VMAX, VSTAR) ---
            r.contains("double rare") || r.contains("rare holo ex") || r.contains("rare holo v") || 
            r.contains("rare holo gx") || r.contains("vmax") || r.contains("vstar") || r.contains("radiant") -> 
                RarityInfo("★★", Color(0xFF1E293B), "Rare Speciali", 3)

            // --- ILLUSTRATION RARE (IR) ---
            r.contains("illustration rare") || r == "rare holo art" -> 
                RarityInfo("★", Color(0xFFFFD700), "Illustration Rare", 4)

            // --- SPECIAL ILLUSTRATION RARE (SIR) ---
            r.contains("special illustration") || r.contains("special art rare") -> 
                RarityInfo("★★", Color(0xFFFFD700), "S. Illustration", 5)

            // --- ULTRA RARE / HYPER RARE / SECRET ---
            r.contains("ultra rare") || r.contains("hyper rare") || r.contains("secret") || r.contains("rainbow") -> 
                RarityInfo("★★★", Color(0xFFFFD700), "Rare Segrete", 6)

            // --- PROMO / SPECIAL ---
            r.contains("promo") -> RarityInfo("★", Color(0xFFEF4444), "Promo", 7)
            r.contains("ace spec") -> RarityInfo("◈", Color(0xFF38BDF8), "ACE SPEC", 8)

            else -> RarityInfo("●", Color(0xFF94A3B8), "Altro", 9)
        }
    }
}

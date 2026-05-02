package com.emabuia.pokevault.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.PropertyName

data class PokemonCard(
    val id: String = "",
    val name: String = "",
    val imageUrl: String = "",
    val set: String = "",
    val rarity: String = "",
    val type: String = "",
    val hp: Int = 0,
    val supertype: String = "Pokémon", // Pokémon, Trainer, Energy
    val subtypes: List<String> = emptyList(), // Basic, Stage 1, Item, Supporter (Aiuto), etc.
    
    @get:PropertyName("isGraded")
    @set:PropertyName("isGraded")
    var isGraded: Boolean = false,
    
    val grade: Float? = null,
    val gradingCompany: String = "",  // PSA, BGS, CGC
    val estimatedValue: Double = 0.0,
    val quantity: Int = 1,
    val condition: String = "Near Mint",
    val notes: String = "",
    val addedAt: Timestamp? = null,
    val apiCardId: String = "",
    val cardNumber: String = "",
    val variant: String = "Normal",
    val language: String = "Italiano"
) {
    fun classify(): String {
        val s = supertype.lowercase()
        val t = type.lowercase()
        val n = name.lowercase()
        val sub = subtypes.map { it.lowercase() }

        // Energy ha priorità massima
        if (
            s.contains("energy") ||
            s.contains("energ") ||
            t.contains("energy") ||
            t.contains("energia") ||
            t.contains("energ") ||
            sub.any { it.contains("energy") || it.contains("energia") } ||
            n.contains("energy") ||
            n.contains("energia")
        ) {
            return "Energy"
        }
        // Trainer rilevato esplicitamente da supertype o subtype (non da hp=0)
        if (
            s.contains("trainer") ||
            s.contains("allenat") ||
            s.contains("aiuto") ||
            t.contains("trainer") ||
            t.contains("supporter") ||
            t.contains("item") ||
            t.contains("stadium") ||
            t.contains("tool") ||
            t.contains("allenat") ||
            t.contains("aiuto") ||
            t.contains("stadio") ||
            t.contains("strumento") ||
            sub.any {
                it == "item" ||
                    it == "stadium" ||
                    it == "supporter" ||
                    it == "tool" ||
                    it == "strumento" ||
                    it == "stadio" ||
                    it == "aiuto"
            }
        ) {
            return "Trainer"
        }
        // Pokémon rilevato esplicitamente
        if (s.contains("pok")) {
            return "Pokémon"
        }
        // Fallback quando il supertype non è esplicitamente valorizzato: usa hp come euristica
        return if (hp > 0) "Pokémon" else "Trainer"
    }
}

data class MenuSection(
    val title: String,
    val icon: String,
    val route: String,
    val badgeCount: Int = 0
)

object CardOptions {
    val CONDITIONS = listOf("Mint", "Near Mint", "Excellent", "Good", "Light Played", "Played", "Poor")
    val GRADING_COMPANIES = listOf("PSA", "BGS", "CGC", "ACE", "SGC")
    val LANGUAGES = listOf(
        "🇬🇧 English"
    )
    val DEFAULT_VARIANTS = listOf("Normal", "Reverse", "Holo")

    private val SINGLE_VARIANT_RARITIES = setOf(
        "ace spec", "special illustration rare", "special art rare",
        "illustration rare", "rare art", "illustrazione rara",
        "shiny ultra", "ultra rara shiny", "ultra rare", "ultra rara", "full art",
        "hyper rare", "iper rara", "gold",
        "shiny rare", "rara shiny",
        "double rare", "doppia rara", "ex", "vmax", "vstar", "rare holo v",
        "promo", "mega_attack_rare", "mega hyper rare"
    )

    fun getVariantsForCard(priceKeys: Set<String>, rarity: String?): List<String> {
        val apiVariants = priceKeys.map { key ->
            when (key) {
                "normal" -> "Normal"
                "holofoil" -> "Holo"
                "reverseHolofoil" -> "Reverse"
                "1stEditionHolofoil" -> "1st Edition Holo"
                "1stEditionNormal" -> "1st Edition"
                "unlimitedHolofoil" -> "Unlimited Holo"
                else -> key.replaceFirstChar { it.uppercase() }
            }
        }
        if (apiVariants.isNotEmpty()) return apiVariants

        // Fallback based on rarity
        val r = (rarity ?: "").lowercase().trim()
        return when {
            r in SINGLE_VARIANT_RARITIES -> listOf("Holo")
            r.contains("rare holo") -> listOf("Holo", "Reverse")
            r == "rare" || r == "rara" -> listOf("Normal", "Reverse", "Holo")
            r == "uncommon" || r == "non comune" -> listOf("Normal", "Reverse")
            r == "common" || r == "comune" -> listOf("Normal", "Reverse")
            else -> listOf("Holo") // Unknown special rarity → single variant
        }
    }

    fun getVariantsFromApi(priceKeys: Set<String>): List<String> {
        return priceKeys.map { key ->
            when (key) {
                "normal" -> "Normal"
                "holofoil" -> "Holo"
                "reverseHolofoil" -> "Reverse"
                "1stEditionHolofoil" -> "1st Edition Holo"
                "1stEditionNormal" -> "1st Edition"
                "unlimitedHolofoil" -> "Unlimited Holo"
                else -> key.replaceFirstChar { it.uppercase() }
            }
        }.ifEmpty { DEFAULT_VARIANTS }
    }

    fun getVariantApiKey(variant: String): String {
        return when (variant) {
            "Normal" -> "normal"
            "Holo" -> "holofoil"
            "Reverse" -> "reverseHolofoil"
            "1st Edition Holo" -> "1stEditionHolofoil"
            "1st Edition" -> "1stEditionNormal"
            "Unlimited Holo" -> "unlimitedHolofoil"
            else -> "normal"
        }
    }
}

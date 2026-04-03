package com.example.pokevault.data.model

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
)

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
        "🇮🇹 Italiano",
        "🇬🇧 Inglese",
        "🇯🇵 Giapponese",
        "🇫🇷 Francese",
        "🇩🇪 Tedesco",
        "🇪🇸 Spagnolo",
        "🇰🇷 Coreano",
        "🇨🇳 Cinese",
        "🇧🇷 Portoghese"
    )
    val DEFAULT_VARIANTS = listOf("Normal", "Reverse", "Holo")

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

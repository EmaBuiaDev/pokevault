package com.example.pokevault.util

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Gestione lingua dell'app (IT / EN).
 * Traduce rarità, tipi, condizioni e label UI.
 */
object AppLocale {

    enum class Language(val code: String, val displayName: String) {
        IT("it", "Italiano"),
        EN("en", "English")
    }

    var current by mutableStateOf(Language.IT)
        private set

    private const val PREFS_NAME = "pokevault_prefs"
    private const val KEY_LANG = "app_language"

    fun init(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val saved = prefs.getString(KEY_LANG, "it") ?: "it"
        current = if (saved == "en") Language.EN else Language.IT
    }

    fun setLanguage(lang: Language, context: Context) {
        current = lang
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY_LANG, lang.code).apply()
    }

    fun toggle(context: Context) {
        setLanguage(if (current == Language.IT) Language.EN else Language.IT, context)
    }

    val isItalian: Boolean get() = current == Language.IT

    // ══════════════════════════════════════
    // TRADUZIONI RARITÀ
    // ══════════════════════════════════════

    private val rarityItMap = mapOf(
        "common" to "Comune",
        "uncommon" to "Non Comune",
        "rare" to "Rara",
        "rare holo" to "Rara Holo",
        "holo rare" to "Rara Holo",
        "ultra rare" to "Ultra Rara",
        "rare ultra" to "Ultra Rara",
        "secret rare" to "Rara Segreta",
        "rare secret" to "Rara Segreta",
        "amazing rare" to "Rara Fantastica",
        "rare holo ex" to "Rara Holo EX",
        "rare holo v" to "Rara Holo V",
        "rare holo gx" to "Rara Holo GX",
        "rare holo vmax" to "Rara Holo VMAX",
        "rare holo vstar" to "Rara Holo VSTAR",
        "double rare" to "Doppia Rara",
        "illustration rare" to "Illustrazione Rara",
        "special illustration rare" to "Illustrazione Rara Speciale",
        "hyper rare" to "Iper Rara",
        "shiny rare" to "Rara Shiny",
        "shiny ultra rare" to "Ultra Rara Shiny",
        "rainbow rare" to "Rara Arcobaleno",
        "gold rare" to "Rara Oro",
        "full art" to "Full Art",
        "alt art" to "Arte Alternativa",
        "ace spec rare" to "ACE SPEC Rara",
        "promo" to "Promo",
        "radiant rare" to "Rara Radiante"
    )

    fun translateRarity(rarity: String): String {
        if (current == Language.EN || rarity.isBlank()) return rarity
        val key = rarity.lowercase().trim()
        return rarityItMap[key] ?: rarity
    }

    // ══════════════════════════════════════
    // TRADUZIONI TIPI POKÉMON
    // ══════════════════════════════════════

    private val typeEnToIt = mapOf(
        "fire" to "Fuoco",
        "water" to "Acqua",
        "grass" to "Erba",
        "lightning" to "Elettro",
        "psychic" to "Psico",
        "fighting" to "Lotta",
        "darkness" to "Buio",
        "metal" to "Metallo",
        "dragon" to "Drago",
        "fairy" to "Folletto",
        "colorless" to "Incolore",
        "normal" to "Normale"
    )

    private val typeItToEn = typeEnToIt.entries.associate { (k, v) -> v.lowercase() to k.replaceFirstChar { it.uppercase() } }

    fun translateType(type: String): String {
        if (type.isBlank()) return type
        val key = type.lowercase().trim()
        return if (current == Language.IT) {
            typeEnToIt[key] ?: type
        } else {
            typeItToEn[key] ?: type
        }
    }

    // ══════════════════════════════════════
    // TRADUZIONI CONDIZIONI
    // ══════════════════════════════════════

    private val conditionsIt = listOf(
        "Mint", "Near Mint", "Eccellente", "Buono", "Leggermente Giocata", "Giocata", "Povera"
    )
    private val conditionsEn = listOf(
        "Mint", "Near Mint", "Excellent", "Good", "Light Played", "Played", "Poor"
    )

    fun getConditions(): List<String> {
        return if (current == Language.IT) conditionsIt else conditionsEn
    }

    // ══════════════════════════════════════
    // TRADUZIONI RARITÀ PER DROPDOWN
    // ══════════════════════════════════════

    private val raritiesIt = listOf(
        "Comune", "Non Comune", "Rara", "Rara Holo", "Ultra Rara",
        "Rara Segreta", "Rara Fantastica", "Full Art", "Arte Alternativa",
        "Rara Arcobaleno", "Rara Oro"
    )
    private val raritiesEn = listOf(
        "Common", "Uncommon", "Rare", "Holo Rare", "Ultra Rare",
        "Secret Rare", "Amazing Rare", "Full Art", "Alt Art",
        "Rainbow Rare", "Gold Rare"
    )

    fun getRarities(): List<String> {
        return if (current == Language.IT) raritiesIt else raritiesEn
    }

    // ══════════════════════════════════════
    // TRADUZIONI TIPI PER DROPDOWN
    // ══════════════════════════════════════

    private val typesIt = listOf(
        "Fuoco", "Acqua", "Erba", "Elettro", "Psico", "Lotta",
        "Buio", "Metallo", "Drago", "Folletto", "Normale", "Incolore"
    )
    private val typesEn = listOf(
        "Fire", "Water", "Grass", "Lightning", "Psychic", "Fighting",
        "Darkness", "Metal", "Dragon", "Fairy", "Normal", "Colorless"
    )

    fun getTypes(): List<String> {
        return if (current == Language.IT) typesIt else typesEn
    }

    // ══════════════════════════════════════
    // LABEL UI GENERICHE
    // ══════════════════════════════════════

    val totalCards: String get() = if (isItalian) "Carte Totali" else "Total Cards"
    val uniqueCards: String get() = if (isItalian) "Carte Uniche" else "Unique Cards"
    val totalValue: String get() = if (isItalian) "Valore Totale" else "Total Value"
    val averageValue: String get() = if (isItalian) "Valore Medio" else "Average Value"
    val mostValuable: String get() = if (isItalian) "Più Preziosa" else "Most Valuable"
    val graded: String get() = if (isItalian) "Graduate" else "Graded"
    val bySet: String get() = if (isItalian) "Per Set" else "By Set"
    val byRarity: String get() = if (isItalian) "Per Rarità" else "By Rarity"
    val byType: String get() = if (isItalian) "Per Tipo" else "By Type"
    val statistics: String get() = if (isItalian) "Statistiche" else "Statistics"
    val back: String get() = if (isItalian) "Indietro" else "Back"
    val save: String get() = if (isItalian) "Salva" else "Save"
    val delete: String get() = if (isItalian) "Elimina" else "Delete"
    val cancel: String get() = if (isItalian) "Annulla" else "Cancel"
    val search: String get() = if (isItalian) "Cerca..." else "Search..."
    val addCard: String get() = if (isItalian) "Aggiungi carta" else "Add Card"
    val editCard: String get() = if (isItalian) "Modifica carta" else "Edit Card"
    val myCards: String get() = if (isItalian) "Le mie carte" else "My Cards"
    val cards: String get() = if (isItalian) "Carte" else "Cards"
    val unique: String get() = if (isItalian) "Uniche" else "Unique"
    val value: String get() = if (isItalian) "Valore" else "Value"
    val set: String get() = if (isItalian) "Set" else "Set"
    val rarity: String get() = if (isItalian) "Rarità" else "Rarity"
    val type: String get() = if (isItalian) "Tipo" else "Type"
    val condition: String get() = if (isItalian) "Condizione" else "Condition"
    val quantity: String get() = if (isItalian) "Quantità" else "Quantity"
    val notes: String get() = if (isItalian) "Note" else "Notes"
    val gradedCard: String get() = if (isItalian) "Carta gradata" else "Graded Card"
    val grade: String get() = if (isItalian) "Voto" else "Grade"
    val all: String get() = if (isItalian) "Tutti" else "All"
    val noSet: String get() = if (isItalian) "Senza set" else "No Set"
    val emptyCollection: String get() = if (isItalian) "La tua collezione è vuota" else "Your collection is empty"
    val noResults: String get() = if (isItalian) "Nessun risultato trovato" else "No results found"
    val unknown: String get() = if (isItalian) "Sconosciuto" else "Unknown"
    val other: String get() = if (isItalian) "Altro" else "Other"
}

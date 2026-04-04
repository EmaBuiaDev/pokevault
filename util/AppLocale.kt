package com.example.pokevault.util

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Gestione lingua dell'app (IT / EN).
 * Traduce rarità, tipi, condizioni e label UI.
 */
object AppLocale {

    enum class Language(val code: String) {
        IT("it"),
        EN("en")
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
    val setCompletion: String get() = if (isItalian) "Completamento Set" else "Set Completion"
    val byRarity: String get() = if (isItalian) "Per Rarità" else "By Rarity"
    val byType: String get() = if (isItalian) "Per Tipo" else "By Type"
    val statistics: String get() = if (isItalian) "Statistiche" else "Statistics"
    val back: String get() = if (isItalian) "Indietro" else "Back"
    val save: String get() = if (isItalian) "Salva" else "Save"
    val delete: String get() = if (isItalian) "Elimina" else "Delete"
    val cancel: String get() = if (isItalian) "Annulla" else "Cancel"
    val search: String get() = if (isItalian) "Cerca..." else "Search..."
    val searchCard: String get() = if (isItalian) "Cerca una carta" else "Search a card"
    val addCard: String get() = if (isItalian) "Aggiungi carta" else "Add Card"
    val editCard: String get() = if (isItalian) "Modifica carta" else "Edit Card"
    val myCards: String get() = if (isItalian) "Le mie\ncarte" else "My\nCards"
    val myCardsSingleLine: String get() = if (isItalian) "Le mie carte" else "My Cards"
    val collection: String get() = if (isItalian) "Collezione" else "Collection"
    val cards: String get() = if (isItalian) "Carte" else "Cards"
    fun cardsCount(count: Int) = if (isItalian) "$count carte" else "$count cards"
    val uniqueOwned: String get() = if (isItalian) "Uniche possedute" else "Unique owned"
    val value: String get() = if (isItalian) "Valore" else "Value"
    val set: String get() = "Set"
    val rarity: String get() = if (isItalian) "Rarità" else "Rarity"
    val type: String get() = if (isItalian) "Tipo" else "Type"
    val condition: String get() = if (isItalian) "Condizione" else "Condition"
    val quantity: String get() = if (isItalian) "Quantità" else "Quantity"
    val notes: String get() = if (isItalian) "Note" else "Notes"
    val gradedCard: String get() = if (isItalian) "Carta gradata" else "Graded Card"
    val gradedCardsTitle: String get() = if (isItalian) "Carte\ngradate" else "Graded\nCards"
    val grade: String get() = if (isItalian) "Voto" else "Grade"
    val all: String get() = if (isItalian) "Tutti" else "All"
    val noSet: String get() = if (isItalian) "Senza set" else "No Set"
    val emptyCollection: String get() = if (isItalian) "La tua collezione è vuota" else "Your collection is empty"
    val noCardsFound: String get() = if (isItalian) "Nessuna carta trovata.\nAggiungi la tua prima carta!" else "No cards found.\nAdd your first card!"
    val noResults: String get() = if (isItalian) "Nessun risultato trovato" else "No results found"
    val unknown: String get() = if (isItalian) "Sconosciuto" else "Unknown"
    val other: String get() = if (isItalian) "Altro" else "Other"
    val scanCard: String get() = if (isItalian) "Scansiona carta" else "Scan card"
    val retry: String get() = if (isItalian) "Riprova" else "Retry"
    val loading: String get() = if (isItalian) "Caricamento..." else "Loading..."

    // Pokedex / Sets
    val extensions: String get() = if (isItalian) "Espansioni" else "Expansions"
    val searchCards: String get() = if (isItalian) "Cerca carte" else "Search cards"
    val searchInSets: String get() = if (isItalian) "Cerca tra tutte le espansioni..." else "Search across all expansions..."
    val searchSetPlaceholder: String get() = if (isItalian) "Cerca un'espansione..." else "Search an expansion..."
    val loadingSets: String get() = if (isItalian) "Caricamento espansioni..." else "Loading expansions..."
    fun expansionsCount(count: Int) = if (isItalian) "$count espansioni" else "$count expansions"
    fun searchFor(query: String) = if (isItalian) "Cerco \"$query\"..." else "Searching for \"$query\"..."
    val writeAtLeast2: String get() = if (isItalian) "Scrivi almeno 2 caratteri" else "Type at least 2 characters"
    fun resultsCountInExpansions(cardCount: Int, setCount: Int) = 
        if (isItalian) "$cardCount carte in $setCount espansioni" else "$cardCount cards in $setCount expansions"
    fun resultsCount(count: Int) = if (isItalian) "$count risultati" else "$count results"

    // Home
    fun helloUser(name: String) = if (isItalian) "Ciao, $name!" else "Hello, $name!"
    val homeSubtitle: String get() = if (isItalian) "Gestisci la tua collezione con stile ✨" else "Manage your collection with style ✨"
    val deckLabSubtitle: String get() = if (isItalian) "Crea e gestisci i tuoi mazzi" else "Create and manage your decks"

    // Auth
    val welcomeTrainer: String get() = if (isItalian) "Benvenuto, Allenatore!" else "Welcome, Trainer!"
    val legendaryCollection: String get() = if (isItalian) "La tua collezione leggendaria" else "Your legendary collection"
    val emailPokemonCenter: String get() = if (isItalian) "Email Centro Pokémon" else "Pokémon Center Email"
    val passwordSecret: String get() = if (isItalian) "Password Segreta" else "Secret Password"
    val nameTrainer: String get() = if (isItalian) "Nome Allenatore" else "Trainer Name"
    val loginButton: String get() = if (isItalian) "INIZIA L'AVVENTURA" else "START ADVENTURE"
    val registerButton: String get() = if (isItalian) "CREA PROFILO" else "CREATE PROFILE"
    val forgotPassword: String get() = if (isItalian) "Password dimenticata?" else "Forgot password?"
    val loadingAuth: String get() = if (isItalian) "CATTURANDO SESSIONE..." else "CATCHING SESSION..."
    val googleSignInLabel: String get() = if (isItalian) "Continua con Google" else "Continue with Google"
    val loginTab: String get() = if (isItalian) "Accedi" else "Login"
    val registerTab: String get() = if (isItalian) "Unisciti" else "Join"
    val or: String get() = if (isItalian) " oppure " else " or "
}

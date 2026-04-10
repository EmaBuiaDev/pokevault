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

    /** Normalizza un tipo (italiano o inglese) alla versione inglese */
    fun typeToEnglish(type: String): String {
        if (type.isBlank()) return type
        val key = type.lowercase().trim()
        // Se è già inglese
        if (typeEnToIt.containsKey(key)) return key.replaceFirstChar { it.uppercase() }
        // Se è italiano, traduci in inglese
        return typeItToEn[key] ?: type
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

    // Network / Offline
    val offlineMessage: String get() = if (isItalian) "Sei offline. Alcune funzioni non sono disponibili." else "You are offline. Some features are unavailable."
    val errorLoadingData: String get() = if (isItalian) "Errore nel caricamento dei dati" else "Error loading data"

    // Empty states
    val emptyStatsTitle: String get() = if (isItalian) "Nessuna statistica disponibile" else "No statistics available"
    val emptyStatsSubtitle: String get() = if (isItalian) "Aggiungi carte alla tua collezione per vedere le statistiche" else "Add cards to your collection to see statistics"
    val emptyCollectionTitle: String get() = if (isItalian) "Nessuna carta trovata" else "No cards found"
    val emptyCollectionSubtitle: String get() = if (isItalian) "Prova a cambiare i filtri o aggiungi nuove carte" else "Try changing filters or add new cards"

    // Pokedex / Sets
    val cardsAndExpansions: String get() = if (isItalian) "Carte e Espansioni" else "Cards & Expansions"
    val extensions: String get() = if (isItalian) "Espansioni" else "Expansions"
    val searchCards: String get() = if (isItalian) "Cerca carte" else "Search cards"
    val searchInSets: String get() = if (isItalian) "Cerca tra tutte le espansioni..." else "Search across all expansions..."
    val searchSetPlaceholder: String get() = if (isItalian) "Cerca un'espansione..." else "Search an expansion..."
    val searchCardPlaceholder: String get() = if (isItalian) "Cerca in italiano o inglese (es. Gabbia di Lotta)..." else "Search in Italian or English (e.g. Battle Cage)..."
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

    // Competitive
    val competitiveTitle: String get() = if (isItalian) "Competitive" else "Competitive"
    val competitiveSubtitle: String get() = if (isItalian) "Mazzi, tornei e risultati" else "Decks, tournaments and results"
    val competitiveDeckLabTab: String get() = if (isItalian) "Deck Lab" else "Deck Lab"
    val competitiveLogTab: String get() = if (isItalian) "Match Log" else "Match Log"

    // Deck Lab tabs
    val deckLabMyDecks: String get() = if (isItalian) "I Miei Deck" else "My Decks"
    val deckLabMetaDeck: String get() = "Meta Deck"
    val deckLabWinTournament: String get() = if (isItalian) "Win Tournament" else "Win Tournament"
    val deckLabMyDecksSubtitle: String get() = if (isItalian) "Crea mini-deck con le carte che possiedi" else "Build mini-decks with your owned cards"
    val deckLabMetaDeckSubtitle: String get() = if (isItalian) "Archetipi e classifica del meta da LimitlessTCG" else "Meta archetypes and standings from LimitlessTCG"
    val deckLabWinTournamentSubtitle: String get() = if (isItalian) "Deck vincitori dai tornei competitivi" else "Winning decks from competitive tournaments"

    // Deck Import - Missing Cards
    val importResultTitle: String get() = if (isItalian) "Risultato Import" else "Import Result"
    val importCardsFound: String get() = if (isItalian) "carte trovate su" else "cards found out of"
    val importMissingTitle: String get() = if (isItalian) "Carte mancanti" else "Missing cards"
    val importAddMissingTitle: String get() = if (isItalian) "Aggiungere carte mancanti?" else "Add missing cards?"
    val importAddMissingMessage: String get() = if (isItalian)
        "Alcune carte del deck non sono nella tua collezione. Vuoi aggiungerle automaticamente per completare il deck?"
    else
        "Some cards in this deck are not in your collection. Do you want to add them automatically to complete the deck?"
    val importAddMissingConfirm: String get() = if (isItalian) "Sì, aggiungi alla collezione" else "Yes, add to collection"
    val importAddMissingSkip: String get() = if (isItalian) "No, continua senza" else "No, continue without"
    val importMatchedMessage: String get() = if (isItalian)
        "Le carte trovate sono state aggiunte al deck. Puoi modificarlo prima di salvare."
    else
        "Matched cards have been added to the deck. You can edit it before saving."
    val importNoMatchMessage: String get() = if (isItalian)
        "Nessuna carta corrisponde alla tua collezione."
    else
        "No cards match your collection."
    val importAddingCards: String get() = if (isItalian) "Aggiunta carte in corso..." else "Adding cards..."
    val importAndMore: String get() = if (isItalian) "e altre" else "and more"

    // Meta Archetype
    val metaShare: String get() = "Meta Share"
    val metaAvgWinrate: String get() = if (isItalian) "Win Rate medio" else "Avg Win Rate"
    val metaTopPlacement: String get() = if (isItalian) "Miglior piazzamento" else "Best placement"
    val metaDecksCount: String get() = if (isItalian) "Deck trovati" else "Decks found"
    val metaNoArchetypes: String get() = if (isItalian) "Nessun archetipo trovato" else "No archetypes found"
    val metaImportSample: String get() = if (isItalian) "Importa deck esempio" else "Import sample deck"

    // Tournaments
    val tournamentListTitle: String get() = if (isItalian) "Tornei" else "Tournaments"
    val addTournament: String get() = if (isItalian) "Registra Torneo" else "Register Tournament"
    val editTournament: String get() = if (isItalian) "Modifica Torneo" else "Edit Tournament"
    val tournamentType: String get() = if (isItalian) "Tipologia" else "Type"
    val tournamentParticipants: String get() = if (isItalian) "Partecipanti" else "Participants"
    val tournamentParticipantsPlaceholder: String get() = if (isItalian) "Es. 32" else "E.g. 32"
    val tournamentLocation: String get() = if (isItalian) "Luogo" else "Location"
    val tournamentLocationPlaceholder: String get() = if (isItalian) "Es. Game Store Milano" else "E.g. Game Store Milan"
    val tournamentDate: String get() = if (isItalian) "Data" else "Date"
    val tournamentFee: String get() = if (isItalian) "Budget iscrizione" else "Entry fee"
    val tournamentFeePlaceholder: String get() = if (isItalian) "Es. 15.00" else "E.g. 15.00"
    val tournamentFormat: String get() = if (isItalian) "Formato" else "Format"
    val tournamentDeck: String get() = if (isItalian) "Deck" else "Deck"
    val tournamentDeckFromList: String get() = if (isItalian) "Scegli dai tuoi deck" else "Choose from your decks"
    val tournamentDeckCustom: String get() = if (isItalian) "Scrivi il nome" else "Enter deck name"
    val tournamentDeckPlaceholder: String get() = if (isItalian) "Es. Charizard ex" else "E.g. Charizard ex"
    val tournamentEmpty: String get() = if (isItalian) "Nessun torneo registrato" else "No tournaments registered"
    val tournamentEmptySubtitle: String get() = if (isItalian) "Registra il tuo primo torneo!" else "Register your first tournament!"
    val tournamentDeleteTitle: String get() = if (isItalian) "Eliminare torneo?" else "Delete tournament?"
    val tournamentDeleteMessage: String get() = if (isItalian) "Verranno eliminate anche tutte le partite. Questa azione è irreversibile." else "All matches will also be deleted. This action is irreversible."
    fun tournamentMatches(count: Int) = if (isItalian) "$count partite" else "$count matches"

    // Match Log (within tournament)
    val matchLogTitle: String get() = if (isItalian) "Match Log" else "Match Log"
    val addMatch: String get() = if (isItalian) "Registra Partita" else "Log Match"
    val editMatch: String get() = if (isItalian) "Modifica Partita" else "Edit Match"
    val matchRound: String get() = if (isItalian) "Turno" else "Round"
    val matchRoundPlaceholder: String get() = if (isItalian) "Es. 1" else "E.g. 1"
    val matchResult: String get() = if (isItalian) "Risultato" else "Result"
    val matchWin: String get() = if (isItalian) "Vittoria" else "Win"
    val matchLoss: String get() = if (isItalian) "Sconfitta" else "Loss"
    val matchTie: String get() = if (isItalian) "Pareggio" else "Tie"
    val matchOpponent: String get() = if (isItalian) "Avversario" else "Opponent"
    val matchOpponentName: String get() = if (isItalian) "Nome avversario" else "Opponent name"
    val matchOpponentDeck: String get() = if (isItalian) "Mazzo avversario" else "Opponent deck"
    val matchNotes: String get() = if (isItalian) "Note" else "Notes"
    val matchNotesPlaceholder: String get() = if (isItalian) "Tech particolari, matchup, sensazioni..." else "Special techs, matchup, feelings..."
    val matchLogEmpty: String get() = if (isItalian) "Nessuna partita registrata" else "No matches logged"
    val matchLogEmptySubtitle: String get() = if (isItalian) "Registra la tua prima partita!" else "Log your first match!"
    val matchDeleteTitle: String get() = if (isItalian) "Eliminare partita?" else "Delete match?"
    val matchDeleteMessage: String get() = if (isItalian) "Questa azione è irreversibile." else "This action is irreversible."
    fun matchRecord(wins: Int, losses: Int, ties: Int) = "$wins W - $losses L - $ties T"
    val matchRecordLabel: String get() = if (isItalian) "Record" else "Record"
    val matchWinRate: String get() = if (isItalian) "Win Rate" else "Win Rate"
    val selectDate: String get() = if (isItalian) "Seleziona data" else "Select date"
    val today: String get() = if (isItalian) "Oggi" else "Today"

    // Album
    val albumTitle: String get() = if (isItalian) "Album" else "Album"
    val albumSubtitle: String get() = if (isItalian) "Crea e organizza i tuoi album" else "Create and organize your albums"
    val myAlbums: String get() = if (isItalian) "I miei Album" else "My Albums"
    val createAlbum: String get() = if (isItalian) "Crea Album" else "Create Album"
    val editAlbum: String get() = if (isItalian) "Modifica Album" else "Edit Album"
    val albumName: String get() = if (isItalian) "Nome Album" else "Album Name"
    val albumNamePlaceholder: String get() = if (isItalian) "Es. I miei preferiti di fuoco" else "E.g. My favorite fire types"
    val albumDescription: String get() = if (isItalian) "Descrizione (opzionale)" else "Description (optional)"
    val albumPokemonType: String get() = if (isItalian) "Tipo Pokémon" else "Pokémon Type"
    val albumExpansion: String get() = if (isItalian) "Espansione" else "Expansion"
    val albumCategory: String get() = if (isItalian) "Categoria" else "Category"
    val albumSize: String get() = if (isItalian) "Grandezza Album" else "Album Size"
    val albumTheme: String get() = if (isItalian) "Tematica" else "Theme"
    val albumAllTypes: String get() = if (isItalian) "Tutti i tipi" else "All types"
    val albumAllExpansions: String get() = if (isItalian) "Tutte le espansioni" else "All expansions"
    val albumAllCategories: String get() = if (isItalian) "Tutte le categorie" else "All categories"
    fun albumSlots(used: Int, total: Int) = if (isItalian) "$used / $total carte" else "$used / $total cards"
    val albumEmpty: String get() = if (isItalian) "Nessun album creato" else "No albums created"
    val albumEmptySubtitle: String get() = if (isItalian) "Crea il tuo primo album personalizzato!" else "Create your first custom album!"
    val albumDeleteTitle: String get() = if (isItalian) "Eliminare album?" else "Delete album?"
    val albumDeleteMessage: String get() = if (isItalian) "Questa azione è irreversibile. Le carte nella collezione non verranno eliminate." else "This action is irreversible. Cards in your collection won't be deleted."
    val albumAddCards: String get() = if (isItalian) "Aggiungi carte" else "Add cards"
    val albumNoMatchingCards: String get() = if (isItalian) "Nessuna carta compatibile trovata nella collezione" else "No matching cards found in your collection"
    val albumFull: String get() = if (isItalian) "Album pieno" else "Album full"
    fun albumSizeLabel(size: Int) = "$size ${if (isItalian) "carte" else "cards"}"
    val albumThemeClassic: String get() = if (isItalian) "Classico" else "Classic"
    val albumThemeFire: String get() = if (isItalian) "Fuoco" else "Fire"
    val albumThemeWater: String get() = if (isItalian) "Acqua" else "Water"
    val albumThemeGrass: String get() = if (isItalian) "Erba" else "Grass"
    val albumThemeElectric: String get() = if (isItalian) "Elettro" else "Electric"
    val albumThemeDark: String get() = if (isItalian) "Oscuro" else "Dark"
    val albumThemePsychic: String get() = if (isItalian) "Psico" else "Psychic"
    val albumCardAdded: String get() = if (isItalian) "Carta aggiunta all'album" else "Card added to album"
    val albumCardRemoved: String get() = if (isItalian) "Carta rimossa dall'album" else "Card removed from album"

    // Auth
    val welcomeTrainer: String get() = if (isItalian) "Benvenuto, Allenatore!" else "Welcome, Trainer!"
    val legendaryCollection: String get() = if (isItalian) "La tua collezione leggendaria" else "Your legendary collection"
    val emailPokemonCenter: String get() = if (isItalian) "Email Allenatore" else "Trainer Email"
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

    // ══════════════════════════════════════
    // LEGAL & COMPLIANCE
    // ══════════════════════════════════════

    // Age Gate
    val ageGateTitle: String get() = if (isItalian) "Verifica Età" else "Age Verification"
    val ageGateMessage: String get() = if (isItalian)
        "Questa app è adatta a tutte le età (PEGI 3).\nConfermi di voler continuare?"
    else
        "This app is suitable for all ages (PEGI 3).\nDo you confirm you want to continue?"
    val ageGateConfirm: String get() = if (isItalian) "Sì, continua" else "Yes, continue"
    val ageGateDeny: String get() = if (isItalian) "No, esci" else "No, exit"
    val ageGateDenied: String get() = if (isItalian)
        "Devi accettare per poter utilizzare l'app."
    else
        "You must accept to use the app."

    // Disclaimer
    val disclaimerTitle: String get() = if (isItalian) "Disclaimer" else "Disclaimer"
    val disclaimerBody: String get() = if (isItalian)
        "Pokémon, Pokémon TCG e tutti i nomi, le immagini e i marchi correlati sono " +
        "proprietà di Nintendo, The Pokémon Company e The Pokémon Company International. " +
        "Questa app non è affiliata, sponsorizzata o approvata da Nintendo, " +
        "The Pokémon Company o The Pokémon Company International.\n\n" +
        "Le immagini e i dati delle carte sono forniti tramite API di terze parti (PokéTCG.io) " +
        "e sono utilizzati esclusivamente a scopo informativo e di gestione della collezione personale.\n\n" +
        "Tutti gli altri marchi appartengono ai rispettivi proprietari."
    else
        "Pokémon, Pokémon TCG, and all related names, images, and trademarks are the property " +
        "of Nintendo, The Pokémon Company, and The Pokémon Company International. " +
        "This app is not affiliated with, sponsored by, or endorsed by Nintendo, " +
        "The Pokémon Company, or The Pokémon Company International.\n\n" +
        "Card images and data are provided through third-party APIs (PokéTCG.io) " +
        "and are used solely for informational and personal collection management purposes.\n\n" +
        "All other trademarks belong to their respective owners."
    val disclaimerAccept: String get() = if (isItalian) "Ho capito, continua" else "I understand, continue"

    // Privacy Policy
    val privacyPolicyUrl: String get() = "https://emabuiadev.github.io/pokevault/privacy-policy"
    val termsUrl: String get() = "https://emabuiadev.github.io/pokevault/terms"

    val privacyConsentTitle: String get() = if (isItalian) "Informativa Privacy" else "Privacy Policy"
    val privacyConsentSummary: String get() = if (isItalian)
        "Per utilizzare PokéVault, raccogliamo e conserviamo i seguenti dati:\n\n" +
        "• Email e nome — per l'autenticazione e il profilo\n" +
        "• Dati della collezione — carte, mazzi, valutazioni\n" +
        "• Dati della fotocamera — solo per la scansione OCR (elaborati sul dispositivo, non inviati a server esterni)\n\n" +
        "I tuoi dati sono conservati in modo sicuro su Firebase (Google Cloud). " +
        "Non vendiamo né condividiamo i tuoi dati personali con terze parti per scopi pubblicitari. " +
        "Puoi eliminare il tuo account e tutti i dati associati in qualsiasi momento dalle Impostazioni."
    else
        "To use PokéVault, we collect and store the following data:\n\n" +
        "• Email and name — for authentication and profile\n" +
        "• Collection data — cards, decks, valuations\n" +
        "• Camera data — only for OCR scanning (processed on-device, not sent to external servers)\n\n" +
        "Your data is securely stored on Firebase (Google Cloud). " +
        "We do not sell or share your personal data with third parties for advertising purposes. " +
        "You can delete your account and all associated data at any time from Settings."
    val readFullPrivacyPolicy: String get() = if (isItalian) "Leggi l'informativa completa" else "Read full privacy policy"
    val privacyConsentAccept: String get() = if (isItalian) "Accetto e continuo" else "I accept and continue"

    // Settings
    val languageLabel: String get() = if (isItalian) "Lingua" else "Language"
    val languageSubtitle: String get() = if (isItalian) "Attuale: Italiano — tocca per cambiare" else "Current: English — tap to change"
    val logoutLabel: String get() = if (isItalian) "Esci" else "Log out"
    val logoutSubtitle: String get() = if (isItalian) "Disconnettiti dal tuo account" else "Sign out of your account"
    val settingsTitle: String get() = if (isItalian) "Impostazioni" else "Settings"
    val privacyPolicyLabel: String get() = if (isItalian) "Informativa Privacy" else "Privacy Policy"
    val privacyPolicySubtitle: String get() = if (isItalian) "Come gestiamo i tuoi dati" else "How we handle your data"
    val termsLabel: String get() = if (isItalian) "Termini di Servizio" else "Terms of Service"
    val termsSubtitle: String get() = if (isItalian) "Condizioni d'uso dell'app" else "App usage conditions"
    val dangerZone: String get() = if (isItalian) "Zona Pericolosa" else "Danger Zone"
    val deleteAccountButton: String get() = if (isItalian) "Elimina Account" else "Delete Account"
    val deleteAccountTitle: String get() = if (isItalian) "Eliminare l'account?" else "Delete account?"
    val deleteAccountMessage: String get() = if (isItalian)
        "Questa azione è irreversibile. Tutti i tuoi dati verranno eliminati definitivamente:\n\n" +
        "• Profilo utente\n• Collezione di carte\n• Mazzi salvati\n• Carte graduate\n\n" +
        "Sei sicuro di voler procedere?"
    else
        "This action is irreversible. All your data will be permanently deleted:\n\n" +
        "• User profile\n• Card collection\n• Saved decks\n• Graded cards\n\n" +
        "Are you sure you want to proceed?"
    val deleteAccountConfirm: String get() = if (isItalian) "Elimina definitivamente" else "Delete permanently"
    val deletingAccount: String get() = if (isItalian) "Eliminazione in corso..." else "Deleting account..."
    val settings: String get() = if (isItalian) "Impostazioni" else "Settings"

    // ══════════════════════════════════════
    // PREMIUM
    // ══════════════════════════════════════

    val premiumTitle: String get() = if (isItalian) "Sblocca tutto il potenziale" else "Unlock full potential"
    val premiumSubtitle: String get() = if (isItalian)
        "Accedi a tutte le funzionalità premium di PokeVault"
    else
        "Access all PokeVault premium features"
    val premiumActiveTitle: String get() = if (isItalian) "Sei Premium!" else "You're Premium!"
    val premiumActiveSubtitle: String get() = if (isItalian)
        "Hai accesso a tutte le funzionalità senza limiti."
    else
        "You have unlimited access to all features."
    val premiumFeaturesTitle: String get() = if (isItalian) "Confronto funzionalità" else "Feature comparison"
    val premiumFeatureAlbumFree: String get() = if (isItalian) "1 Album gratuito" else "1 Free Album"
    val premiumFeatureAlbumPremium: String get() = if (isItalian) "Album illimitati" else "Unlimited Albums"
    val premiumFeatureDeckFree: String get() = if (isItalian) "1 Deck gratuito" else "1 Free Deck"
    val premiumFeatureDeckPremium: String get() = if (isItalian) "Deck illimitati" else "Unlimited Decks"
    val premiumFeatureMetaFree: String get() = if (isItalian)
        "10 visualizzazioni Meta Deck"
    else
        "10 Meta Deck views"
    val premiumFeatureMetaPremium: String get() = if (isItalian)
        "Meta Deck illimitati"
    else
        "Unlimited Meta Decks"
    val premiumChoosePlan: String get() = if (isItalian) "Scegli il tuo piano" else "Choose your plan"
    val premiumMonthly: String get() = if (isItalian) "Mensile" else "Monthly"
    val premiumAnnual: String get() = if (isItalian) "Annuale" else "Annual"
    fun premiumPriceMonthly(price: String) = if (isItalian) "$price / mese" else "$price / month"
    fun premiumPriceAnnual(price: String) = if (isItalian) "$price / anno" else "$price / year"
    val premiumSaveBadge: String get() = if (isItalian) "RISPARMIA" else "SAVE"
    val premiumRestore: String get() = if (isItalian) "Ripristina acquisti" else "Restore purchases"
    val premiumUpgradeButton: String get() = if (isItalian) "Passa a Premium" else "Go Premium"
    val premiumLegalNote: String get() = if (isItalian)
        "L'abbonamento si rinnova automaticamente. Puoi annullarlo in qualsiasi momento dal Google Play Store. " +
        "Il pagamento viene addebitato sul tuo account Google Play."
    else
        "Subscription auto-renews. You can cancel anytime from Google Play Store. " +
        "Payment is charged to your Google Play account."
    val premiumSettingsLabel: String get() = if (isItalian) "PokeVault Premium" else "PokeVault Premium"
    val premiumSettingsSubtitleFree: String get() = if (isItalian)
        "Sblocca album, deck illimitati e Meta Deck"
    else
        "Unlock unlimited albums, decks and Meta Decks"
    val premiumSettingsSubtitleActive: String get() = if (isItalian)
        "Abbonamento attivo — tutte le funzionalità sbloccate"
    else
        "Active subscription — all features unlocked"
    val premiumManage: String get() = if (isItalian) "Gestisci abbonamento" else "Manage subscription"

    // Premium gates
    val premiumAlbumLimitTitle: String get() = if (isItalian)
        "Limite Album raggiunto"
    else
        "Album limit reached"
    val premiumAlbumLimitMessage: String get() = if (isItalian)
        "Hai raggiunto il limite di 1 album gratuito.\n\nPassa a Premium per creare album illimitati!"
    else
        "You've reached the 1 free album limit.\n\nGo Premium to create unlimited albums!"
    val premiumTournamentLimitTitle: String get() = if (isItalian)
        "Limite Torneo raggiunto"
    else
        "Tournament limit reached"
    val premiumTournamentLimitMessage: String get() = if (isItalian)
        "Hai raggiunto il limite di 1 torneo gratuito.\n\nPassa a Premium per registrare tornei illimitati!"
    else
        "You've reached the 1 free tournament limit.\n\nGo Premium to register unlimited tournaments!"

    val premiumFeatureTournamentFree: String get() = if (isItalian) "1 Torneo gratuito" else "1 Free Tournament"
    val premiumFeatureTournamentPremium: String get() = if (isItalian) "Tornei illimitati" else "Unlimited Tournaments"

    val premiumDeckLimitTitle: String get() = if (isItalian)
        "Limite Deck raggiunto"
    else
        "Deck limit reached"
    val premiumDeckLimitMessage: String get() = if (isItalian)
        "Hai raggiunto il limite di 1 deck gratuito.\n\nPassa a Premium per creare deck illimitati!"
    else
        "You've reached the 1 free deck limit.\n\nGo Premium to create unlimited decks!"
    val premiumMetaDeckLimitTitle: String get() = if (isItalian)
        "Visualizzazioni Meta Deck esaurite"
    else
        "Meta Deck views exhausted"
    val premiumMetaDeckLimitMessage: String get() = if (isItalian)
        "Hai utilizzato tutte le 10 visualizzazioni gratuite dei Meta Deck.\n\nPassa a Premium per accesso illimitato!"
    else
        "You've used all 10 free Meta Deck views.\n\nGo Premium for unlimited access!"
    fun premiumMetaDeckViewsRemaining(count: Int) = if (isItalian)
        "$count visualizzazioni rimanenti"
    else
        "$count views remaining"
}

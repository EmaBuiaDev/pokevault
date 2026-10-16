package com.emabuia.pokevault.util

import android.content.Context
import android.os.Build

/**
 * Il pop-up "Novita'": una volta per versione, al primo avvio dopo un
 * aggiornamento, solo per le versioni che hanno delle note qui sotto.
 *
 * Chi installa l'app da zero non lo vede: per lui e' tutto nuovo, e il
 * pop-up arriverebbe sopra il primo accesso. Si riconosce dall'installazione
 * (firstInstallTime == lastUpdateTime), perche' la 3.1.7 e le precedenti non
 * scrivevano niente: per loro "mai visto" e "appena installato" sarebbero uguali.
 */
object WhatsNew {

    // pokevault_prefs e' escluso dal backup: chi reinstalla e' un'installazione nuova.
    private const val PREFS_NAME = "pokevault_prefs"
    private const val KEY_SEEN_VERSION = "whats_new_seen_version"

    enum class Icon { NAME, LANGUAGE, SCANNER, PRICE, ALBUM, HAND, RATING, META, IMPORT, THEME }

    data class Item(val icon: Icon, val title: String, val body: String)

    /** Le note di una versione, nella lingua dell'app; vuota se non ce ne sono. */
    fun notesFor(versionCode: Int): List<Item> = when (versionCode) {
        41 -> notes318()
        42 -> notes319()
        43 -> notes3110()
        else -> emptyList()
    }

    /** Il titolo del pop-up, con la versione che l'utente vede nelle Impostazioni. */
    fun title(versionName: String): String =
        if (AppLocale.isItalian) "Novità della $versionName" else "What's new in $versionName"

    val subtitle: String
        get() = if (AppLocale.isItalian) "Ecco cosa è cambiato da quando sei passato l'ultima volta."
        else "Here's what changed since your last visit."

    val closeCta: String get() = if (AppLocale.isItalian) "Inizia a esplorare" else "Start exploring"

    /**
     * Si mostra se l'utente arriva da una versione precedente e questa ha delle
     * note. [lastSeenVersion] e' 0 se non e' mai stato scritto.
     */
    fun shouldShow(lastSeenVersion: Int, currentVersion: Int, freshInstall: Boolean, hasNotes: Boolean): Boolean =
        hasNotes && lastSeenVersion < currentVersion && !freshInstall

    /**
     * Da chiamare una volta per avvio, con l'utente gia' dentro l'app. Segna la
     * versione come vista in ogni caso (mostrato o no): il pop-up non torna.
     */
    fun consumeOnLaunch(context: Context, currentVersion: Int): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val lastSeen = prefs.getInt(KEY_SEEN_VERSION, 0)
        if (lastSeen >= currentVersion) return false
        val show = shouldShow(
            lastSeenVersion = lastSeen,
            currentVersion = currentVersion,
            freshInstall = isFreshInstall(context),
            hasNotes = notesFor(currentVersion).isNotEmpty()
        )
        prefs.edit().putInt(KEY_SEEN_VERSION, currentVersion).apply()
        return show
    }

    private fun isFreshInstall(context: Context): Boolean = runCatching {
        val pm = context.packageManager
        val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getPackageInfo(context.packageName, android.content.pm.PackageManager.PackageInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            pm.getPackageInfo(context.packageName, 0)
        }
        info.firstInstallTime == info.lastUpdateTime
    }.getOrDefault(true) // nel dubbio non si disturba

    private fun notes318(): List<Item> = if (AppLocale.isItalian) listOf(
        Item(
            Icon.NAME, "PokeVault ora è ${AppLocale.APP_NAME}",
            "Nuovo nome, stessa app: account, collezione e Premium restano quelli di sempre."
        ),
        Item(
            Icon.LANGUAGE, "Arrivano le carte inglesi",
            "Tutto il catalogo inglese, con immagini e prezzi Cardmarket. Cerchi in italiano, in inglese o in tutte e due."
        ),
        Item(
            Icon.SCANNER, "Scanner e mazzi anche in inglese",
            "Lo scanner riconosce le carte inglesi e nel Deck Lab puoi metterle nei tuoi mazzi."
        ),
        Item(
            Icon.PRICE, "Il prezzo giusto della tua copia",
            "Prezzo e link Cardmarket seguono la stampa esatta che possiedi: Holo, Reverse, timbrate."
        ),
    ) else listOf(
        Item(
            Icon.NAME, "PokeVault is now ${AppLocale.APP_NAME}",
            "New name, same app: your account, collection and Premium stay exactly as they were."
        ),
        Item(
            Icon.LANGUAGE, "English cards are here",
            "The full English catalog, with images and Cardmarket prices. Search in Italian, English or both."
        ),
        Item(
            Icon.SCANNER, "Scanner and decks in English too",
            "The scanner recognizes English cards, and in Deck Lab you can add them to your decks."
        ),
        Item(
            Icon.PRICE, "The right price for your copy",
            "Cardmarket price and link follow the exact print you own: Holo, Reverse, stamped."
        ),
    )

    private fun notes319(): List<Item> = if (AppLocale.isItalian) listOf(
        Item(
            Icon.ALBUM, "Album tutti nuovi",
            "Pagine da 4, 9 o 12 tasche, colore e copertina col tuo Pokémon. Cerchi le carte dal catalogo: quelle che non hai restano «da trovare»."
        ),
        Item(
            Icon.HAND, "Hand Simulator con le regole vere",
            "Mulligan solo senza Pokémon Base, pulsanti sempre a portata di mano e analisi di Base, Energia e Aiuti nei primi turni."
        ),
        Item(
            Icon.RATING, "Vota le tue mani",
            "Pessima, rischiosa o buona: con un tocco la mano si salva con le sue 7 carte, accanto al giudizio dell'app."
        ),
        Item(
            Icon.META, "Meta Deck più chiaro",
            "Win rate su tutte le partite, piazzamenti con il loro contesto e la tendenza vera dei mazzi nei tornei recenti."
        ),
    ) else listOf(
        Item(
            Icon.ALBUM, "Brand-new albums",
            "Pages with 4, 9 or 12 pockets, color and a cover with your Pokémon. Search cards from the catalog: the ones you don't own stay \"to find\"."
        ),
        Item(
            Icon.HAND, "Hand Simulator with the real rules",
            "Mulligan only without Basic Pokémon, buttons always within reach and analysis of Basics, Energy and Supporters in the first turns."
        ),
        Item(
            Icon.RATING, "Rate your hands",
            "Bad, risky or good: one tap saves the hand with its 7 cards, next to the app's verdict."
        ),
        Item(
            Icon.META, "Clearer Meta Deck",
            "Win rate over all games, placements with their context and the real trend of decks in recent tournaments."
        ),
    )

    private fun notes3110(): List<Item> = if (AppLocale.isItalian) listOf(
        Item(
            Icon.IMPORT, "Import dei mazzi preciso",
            "Le liste da Limitless e dal Meta Deck prendono dalla tua collezione la stampa esatta: stesso set, stesso numero."
        ),
        Item(
            Icon.HAND, "Niente più carte scambiate",
            "Una carta ex non viene più sostituita dalla versione normale, e le copie che ti mancano arrivano nella stampa giusta."
        ),
        Item(
            Icon.THEME, "Tema chiaro più leggibile",
            "Il tuo nome in cima alla Home ora si legge bene anche col tema chiaro."
        ),
    ) else listOf(
        Item(
            Icon.IMPORT, "Accurate deck import",
            "Lists from Limitless and Meta Deck now take the exact print from your collection: same set, same number."
        ),
        Item(
            Icon.HAND, "No more swapped cards",
            "An ex card is no longer replaced by its regular version, and the copies you're missing come in the right print."
        ),
        Item(
            Icon.THEME, "Easier to read in light theme",
            "Your name at the top of Home is now clearly readable in the light theme too."
        ),
    )
}

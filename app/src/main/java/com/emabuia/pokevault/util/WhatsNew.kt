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

    enum class Icon { NAME, LANGUAGE, SCANNER, PRICE }

    data class Item(val icon: Icon, val title: String, val body: String)

    /** Le note di una versione, nella lingua dell'app; vuota se non ce ne sono. */
    fun notesFor(versionCode: Int): List<Item> = when (versionCode) {
        41 -> notes318()
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
}

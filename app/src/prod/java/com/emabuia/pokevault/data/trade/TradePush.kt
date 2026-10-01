package com.emabuia.pokevault.data.trade

import android.content.Context
import android.content.Intent

/**
 * In prod TradeRadar non c'e', e nemmeno Firebase Messaging: la libreria e'
 * solo nello staging (stagingImplementation), perche' aggiungerebbe all'app
 * il permesso per le notifiche e un ricevitore che prod non usa.
 *
 * Questa e' la stessa interfaccia della versione vera
 * (src/staging/.../TradePush.kt), senza fare niente: il codice comune la
 * chiama senza chiedersi in quale flavor si trova. Al lancio di TradeRadar
 * la versione vera torna in src/main e questo file sparisce.
 */
object TradePush {

    data class Open(val screen: String, val proposalId: String?)

    val pendingOpen: Open? get() = null

    fun consumeOpen(): Open? = null

    fun handleIntent(intent: Intent?) = Unit

    @Suppress("UNUSED_PARAMETER", "RedundantSuspendModifier")
    suspend fun register(context: Context): Boolean = false

    fun forget() = Unit
}

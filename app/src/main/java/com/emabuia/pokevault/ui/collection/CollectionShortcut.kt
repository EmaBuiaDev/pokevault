package com.emabuia.pokevault.ui.collection

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Una richiesta una-tantum per aprire "Le mie carte" su una vista precisa.
 *
 * Serve al "Vedi tutte" della Home: deve aprire la collezione con le ultime
 * aggiunte in cima. Non e' un argomento della rotta di proposito: la rotta
 * della collezione e' anche una tab della barra in basso, che la riconosce
 * per nome e la ripristina col suo stato; cambiarne la forma per una
 * scorciatoia voleva dire rischiare di rompere la tab. Qui la schermata la
 * consuma quando si compone, sia che nasca nuova sia che venga ripristinata.
 */
object CollectionShortcut {
    var pendingRecent by mutableStateOf(false)
        private set

    fun requestRecent() {
        pendingRecent = true
    }

    /** Vero una volta sola: chi la legge la spegne. */
    fun consumeRecent(): Boolean {
        val pending = pendingRecent
        pendingRecent = false
        return pending
    }
}

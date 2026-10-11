package com.emabuia.pokevault.data.firebase

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Una modifica salvata sul telefono che il server ha poi rifiutato.
 *
 * Le scritture sono "locale prima": l'app le mostra subito e le manda al
 * server dopo, anche molto dopo se si e' offline. Se il server le rifiuta
 * (regole, documento cancellato da un altro dispositivo) Firestore le
 * annulla da solo nella copia locale, e la modifica spariva dallo schermo
 * senza una parola. Qui arriva l'avviso, e AppNavigation lo mostra.
 *
 * Senza rete non arriva niente: le scritture restano in coda, non falliscono.
 */
object SyncErrors {

    // Un evento in attesa basta: dieci rifiuti insieme sono un avviso solo.
    private val _rejections = MutableSharedFlow<Unit>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val rejections: SharedFlow<Unit> = _rejections.asSharedFlow()

    /** Quanti rifiuti dall'avvio: serve ai test. */
    @Volatile
    var rejectedCount: Int = 0
        private set

    fun reportRejected() {
        rejectedCount++
        _rejections.tryEmit(Unit)
    }
}

package com.emabuia.pokevault.data.billing

/**
 * L'ultimo esito certo sull'abbonamento di un account, salvato sul telefono.
 *
 * All'avvio l'abbonamento lo dicono Google Play o il nostro server. Senza rete
 * capita che nessuno dei due risponda, e fino ad allora chi ha pagato vedeva
 * l'app da utente gratuito: limiti dei mazzi, delle wishlist, del simulatore.
 * Con questa copia, per qualche giorno vale l'ultima risposta avuta.
 *
 * Il mese regalo ha gia' la sua copia su disco: qui c'e' solo l'abbonamento.
 */
data class RememberedSubscription(
    val uid: String,
    val entitled: Boolean,
    val confirmedAtMs: Long
)

object SubscriptionMemory {

    /**
     * Quanto vale l'ultima risposta senza poterla riverificare. Abbastanza per
     * una fiera o un viaggio; poco, se nel frattempo l'abbonamento e' stato
     * disdetto o rimborsato.
     */
    const val GRACE_MS: Long = 7L * 24 * 60 * 60 * 1000

    /**
     * L'abbonamento vale? Prima il server, poi Google Play, e solo se nessuno
     * dei due ha risposto la copia salvata -- dello stesso account, non
     * scaduta, e mai "dal futuro" (un orologio spostato indietro non deve
     * allungarla).
     */
    fun isSubscribed(
        fromServer: Boolean?,
        fromBilling: Boolean?,
        remembered: RememberedSubscription?,
        uid: String,
        nowMs: Long
    ): Boolean {
        fromServer?.let { return it }
        fromBilling?.let { return it }
        return isStillValid(remembered, uid, nowMs)
    }

    fun isStillValid(remembered: RememberedSubscription?, uid: String, nowMs: Long): Boolean {
        if (remembered == null || !remembered.entitled) return false
        if (uid.isBlank() || remembered.uid != uid) return false
        val age = nowMs - remembered.confirmedAtMs
        return age in 0..GRACE_MS
    }
}

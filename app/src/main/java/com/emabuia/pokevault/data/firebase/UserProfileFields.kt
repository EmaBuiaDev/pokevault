package com.emabuia.pokevault.data.firebase

/**
 * I campi del profilo in `users/{uid}` (name, email, createdAt, totalCards)
 * che mancano nel documento, da scrivere in merge.
 *
 * Al primo login con Google il profilo si scriveva solo se il documento non
 * esisteva. Ma appena l'utente e' autenticato, la sync di `isPremium`
 * (PremiumManager, a ogni onResume, cioe' anche al ritorno dal selettore
 * dell'account) crea il documento in merge, e la lettura del login lo vede
 * gia': il profilo saltava, e in console restavano utenti senza nome ne'
 * email (08/10/2026). Ora si guarda campo per campo, e lo fa anche
 * l'apertura dell'app (touchLastSeen), che ripara i documenti gia' rotti.
 *
 * Mai sovrascrivere quello che c'e': `totalCards` lo tiene aggiornato la
 * collezione, e il nome di chi si e' registrato con la password e' quello
 * scelto da lui, non quello dell'account.
 *
 * @param existing i campi attuali del documento, null se non esiste
 * @param createdAtMs quando e' nato l'account (Firebase Auth), per createdAt
 * @return mappa vuota se non manca niente; createdAt come millisecondi, lo
 *   converte in Timestamp chi scrive
 */
object UserProfileFields {

    const val DEFAULT_NAME = "Allenatore"

    fun missing(
        existing: Map<String, Any?>?,
        name: String?,
        email: String?,
        createdAtMs: Long
    ): Map<String, Any> {
        val doc = existing.orEmpty()
        val out = linkedMapOf<String, Any>()
        if (doc.blank("name")) out["name"] = name?.takeIf { it.isNotBlank() } ?: DEFAULT_NAME
        if (doc.blank("email") && !email.isNullOrBlank()) out["email"] = email
        if (doc["createdAt"] == null) out["createdAt"] = createdAtMs
        if (doc["totalCards"] == null) out["totalCards"] = 0
        return out
    }

    private fun Map<String, Any?>.blank(key: String): Boolean =
        (this[key] as? String).isNullOrBlank()
}

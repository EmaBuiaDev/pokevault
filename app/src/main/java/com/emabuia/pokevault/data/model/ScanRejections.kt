package com.emabuia.pokevault.data.model

/**
 * Le carte che l'utente ha scartato mentre inquadra una carta.
 *
 * Servono a una cosa sola: far proporre a "nessuna di queste" le carte
 * successive invece delle stesse tre. Per questo valgono **solo finche' quella
 * carta resta davanti all'obiettivo**.
 *
 * Prima si azzeravano soltanto quando lo scanner leggeva un numero diverso.
 * Chi scartava per sbaglio la carta giusta e poi la rimetteva davanti non la
 * ritrovava piu': il numero letto era lo stesso, lo scarto restava, e la carta
 * era esclusa fino a quando non si scansionava un'altra carta -- cosa che
 * nessuno puo' indovinare. Ora si azzerano anche quando la carta esce
 * dall'inquadratura, e l'ultimo scarto si puo' annullare.
 *
 * Classe pura, senza fotocamera ne' Android: e' la parte da cui dipende se una
 * carta si ritrova, e deve poter stare sotto test.
 */
class ScanRejections {

    private val rejected = linkedSetOf<String>()
    private var scope = ""

    /** L'ultimo gruppo scartato, per poterlo annullare. */
    private var lastBatch: Set<String> = emptySet()

    val size: Int get() = rejected.size

    fun isRejected(cardId: String): Boolean = cardId in rejected

    fun <T> viable(items: List<T>, idOf: (T) -> String): List<T> = items.filterNot { idOf(it) in rejected }

    /**
     * Il numero letto sulla carta inquadrata. Un numero diverso e' una carta
     * diversa: gli scarti di prima non la riguardano. Un numero che per un
     * frame non si legge NON e' un cambio di carta -- altrimenti dopo
     * "nessuna di queste" gli scarti sparirebbero al primo frame sporco.
     */
    fun onNumberRead(number: String?) {
        val next = number?.takeIf { it.isNotBlank() } ?: return
        if (next == scope) return
        clear()
        scope = next
    }

    /** La carta e' uscita dall'inquadratura: quando torna, si riparte da zero. */
    fun onCardRemoved() = clear()

    fun reject(cardIds: Collection<String>) {
        val added = cardIds.filter { it.isNotBlank() && rejected.add(it) }.toSet()
        lastBatch = added
    }

    /** Annulla l'ultimo scarto: quelle carte tornano proponibili. */
    fun undoLast(): Set<String> {
        val batch = lastBatch
        rejected.removeAll(batch)
        lastBatch = emptySet()
        return batch
    }

    fun clear() {
        rejected.clear()
        lastBatch = emptySet()
        scope = ""
    }
}

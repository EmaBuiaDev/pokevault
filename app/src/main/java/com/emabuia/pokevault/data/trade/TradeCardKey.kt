package com.emabuia.pokevault.data.trade

import com.emabuia.pokevault.data.catalog.CardRef
import com.emabuia.pokevault.data.catalog.CatalogLang
import java.util.Locale

/**
 * La chiave con cui TradeRadar riconosce la stessa carta fra utenti diversi.
 *
 * Forma: `<codice set>:<numero>`, codice in minuscolo (me02:1, bwp:BW01,
 * 30th-c:12). E' il file del catalogo (ME02_IT_1.png) senza `_IT_` ne'
 * estensione, e il Worker la ricava allo stesso modo: vedi
 * pokevault-proxy-worker/schema-trade/002_fondamenta.sql.
 *
 * Nell'app la stessa carta arriva come id "ita:me02:1" (`apiCardId` in
 * collezione, `card.id` in wishlist, `targetCardApiIds` negli album): lo scrive
 * CatalogRepository.buildItalianCardId. NON si usa collectionCardKey(), che
 * dipende dal nome del set tradotto.
 *
 * Le carte senza id italiano del catalogo (quelle inglesi di PokeWallet)
 * restituiscono null: per ora restano fuori dagli scambi.
 */
object TradeCardKey {

    private val SET_CODE = Regex("^[a-z0-9-]{1,20}$")
    private val NUMBER = Regex("^[A-Za-z0-9_]{1,20}$")

    fun fromApiCardId(apiCardId: String?): String? {
        // Per ora solo il catalogo italiano: l'inglese entra negli scambi con la
        // stessa chiave della gemella IT nella Fase 3 del piano carte EN.
        val ref = CardRef.parse(apiCardId)?.takeIf { it.lang == CatalogLang.IT } ?: return null
        // Come ItalianCatalogNormalizer.toImageReference: i numeri perdono gli
        // zeri davanti, i promo a lettere (BW01, SWSH026) restano come sono.
        val number = ref.number.toIntOrNull()?.toString() ?: ref.number
        if (!SET_CODE.matches(ref.setCode) || !NUMBER.matches(number)) return null
        return "${ref.setCode}:$number"
    }

    /** L'apiCardId della carta del catalogo con questa chiave (me02:1 -> "ita:me02:1"). */
    fun toApiCardId(key: String): String = "${CatalogLang.IT.idPrefix}:$key"

    /** Il codice set di una chiave valida (me02:1 -> me02). */
    fun setCodeOf(key: String): String = key.substringBefore(':')

    /** "ME02 · 1": come si mostra una carta di cui si conosce solo la chiave. */
    fun label(key: String): String =
        "${setCodeOf(key).uppercase(Locale.ROOT)} · ${key.substringAfter(':')}"

    /**
     * Immagine piccola della carta, dallo stesso percorso che usa il catalogo
     * (images/it/<CODICE>/<numero>): serve per le carte degli altri, che non
     * sono in collezione e di cui il server manda solo la chiave.
     */
    fun imageUrl(key: String, baseUrl: String): String =
        "${baseUrl.trimEnd('/')}/images/it/${setCodeOf(key).uppercase(Locale.ROOT)}/${key.substringAfter(':')}?size=low"
}

package com.emabuia.pokevault.data.trade

import com.emabuia.pokevault.data.catalog.CardRef
import com.emabuia.pokevault.data.catalog.CatalogLang
import com.emabuia.pokevault.data.english.EnglishCatalogIds
import com.emabuia.pokevault.data.italian.ItalianImageReference
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
 * Le carte del catalogo inglese ("eng:sv08:1") danno la STESSA chiave della
 * gemella italiana: negli scambi IT ed EN sono la stessa carta, e la lingua
 * della copia viaggia a parte (trade_haves.language). Le carte senza id del
 * catalogo (quelle inglesi di PokeWallet) restituiscono null: restano fuori.
 */
object TradeCardKey {

    private val SET_CODE = Regex("^[a-z0-9-]{1,20}$")
    private val NUMBER = Regex("^[A-Za-z0-9_]{1,20}$")

    fun fromApiCardId(apiCardId: String?): String? {
        // Italiano e inglese insieme: stessa cartella e stesso numero, stessa chiave.
        val ref = CardRef.parse(apiCardId) ?: return null
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
    fun imageUrl(key: String, baseUrl: String): String = italianImageUrl(referenceOf(key), baseUrl, "low")

    /** Cartella e numero della chiave ("sv08:25" -> SV08, 25), come le immagini su R2. */
    private fun referenceOf(key: String) = ItalianImageReference(setCodeOf(key), key.substringAfter(':'))

    private fun italianImageUrl(reference: ItalianImageReference, baseUrl: String, size: String): String =
        "${baseUrl.trimEnd('/')}/images/it/${reference.folderName}/${reference.cardNumber}?size=$size"

    /**
     * Le immagini da provare per una copia, in ordine: prima quella della sua
     * lingua (inglese per una copia "English", italiana per le altre), poi
     * l'altra. La seconda serve ai set che esistono in una lingua sola: una
     * carta di un set solo inglese non ha l'immagine italiana. Con [englishBaseUrl]
     * null (inglese spento) c'e' solo l'italiana, come prima.
     */
    fun imageUrls(key: String, italianBaseUrl: String, englishBaseUrl: String?, language: String?, size: String = "low"): List<String> {
        val reference = referenceOf(key)
        val italian = italianImageUrl(reference, italianBaseUrl, size)
        // Lo stesso indirizzo delle carte del catalogo EN, da un posto solo.
        val english = englishBaseUrl?.takeIf { it.isNotBlank() }
            ?.let { EnglishCatalogIds.cardImageUrl(it, reference, size) }
            ?: return listOf(italian)
        return if (CatalogLang.ofCopyLanguage(language) == CatalogLang.EN) listOf(english, italian) else listOf(italian, english)
    }
}

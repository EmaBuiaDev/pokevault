package com.emabuia.pokevault.data.catalog

import com.emabuia.pokevault.data.model.CardOptions
import java.util.Locale

/**
 * Il catalogo nostro da cui viene una carta (D1 + R2, servito dal Worker).
 *
 * Fino alla 3.1.8 esiste solo l'italiano, e l'italiano era scritto a mano in
 * ogni punto che riconosce una carta del catalogo: `startsWith("ita:")`,
 * `endsWith("__ita")`, `"🇮🇹 Italiano"`. Qui sta in un posto solo, cosi' il
 * catalogo inglese (piano carte EN, 13/10/2026) si aggiunge come un valore in
 * piu' invece che come altri quaranta controlli sparsi.
 *
 * @property idPrefix prima parte dell'apiCardId: "ita" in "ita:sv08:12".
 * @property setSuffix coda dell'id del set in app: "__ita" in "sv08__ita".
 * @property macro la lingua come la scrivono TcgSet.language e la navigazione.
 */
enum class CatalogLang(val idPrefix: String, val setSuffix: String, val macro: String) {
    IT("ita", "__ita", "ITA"),
    EN("eng", "__eng", "ENG");

    /** Lingua predefinita della copia aggiunta in collezione ("🇮🇹 Italiano"). */
    val copyLanguageLabel: String
        get() = CardOptions.languageLabelForMacro(macro) ?: CardOptions.LANGUAGES.first()

    companion object {
        /**
         * Il catalogo della lingua di una copia ("🇬🇧 English" o "English" -> EN),
         * o null se la lingua non e' di un nostro catalogo o manca.
         */
        fun ofCopyLanguage(language: String?): CatalogLang? {
            fun clean(raw: String) = raw.trim().dropWhile { !it.isLetter() }.trim()
            val wanted = language?.let(::clean).orEmpty()
            if (wanted.isEmpty()) return null
            return entries.firstOrNull { clean(it.copyLanguageLabel).equals(wanted, ignoreCase = true) }
        }
    }
}

/**
 * Una carta del nostro catalogo, letta dal suo apiCardId "<lang>:<set>:<numero>".
 *
 * Le carte che non vengono dal catalogo (id PokeWallet "<setId>-<numero>",
 * collezioni vecchie) non sono un CardRef: [parse] e [langOf] restituiscono
 * null e i chiamanti tengono il loro ramo di sempre.
 */
data class CardRef(val lang: CatalogLang, val setCode: String, val number: String) {

    val apiCardId: String get() = "${lang.idPrefix}:$setCode:$number"

    companion object {

        /** Il codice set va minuscolo, il numero resta com'e' (promo SWSH026, 24a). */
        fun of(lang: CatalogLang, setCode: String, number: String): CardRef =
            CardRef(lang, setCode.trim().lowercase(Locale.ROOT), number.trim())

        /** Il catalogo di un apiCardId dal solo prefisso, o null se non e' nostro. */
        fun langOf(apiCardId: String?): CatalogLang? {
            val raw = apiCardId?.trim() ?: return null
            val prefix = raw.substringBefore(':', missingDelimiterValue = "")
            if (prefix.isEmpty()) return null
            return CatalogLang.entries.firstOrNull { it.idPrefix.equals(prefix, ignoreCase = true) }
        }

        fun isCatalogCard(apiCardId: String?, lang: CatalogLang): Boolean = langOf(apiCardId) == lang

        /** Il codice set ("ita:sv08:12" -> "sv08"), anche se manca il numero. */
        fun setCodeOf(apiCardId: String?, lang: CatalogLang): String? {
            if (langOf(apiCardId) != lang) return null
            return apiCardId!!.trim()
                .substringAfter(':')
                .substringBefore(':')
                .trim()
                .lowercase(Locale.ROOT)
                .ifBlank { null }
        }

        /** "ita:sv08:12" -> CardRef(IT, "sv08", "12"); serve set e numero, niente di piu'. */
        fun parse(apiCardId: String?): CardRef? {
            val lang = langOf(apiCardId) ?: return null
            val parts = apiCardId!!.trim().substringAfter(':').split(':')
            if (parts.size != 2) return null
            val setCode = parts[0].trim()
            val number = parts[1].trim()
            if (setCode.isEmpty() || number.isEmpty()) return null
            return of(lang, setCode, number)
        }
    }
}

/** Gli id dei set del catalogo in app: "<espansione>__ita". */
object CatalogSetId {

    fun of(lang: CatalogLang, expansionId: String): String =
        "${expansionId.trim().lowercase(Locale.ROOT)}${lang.setSuffix}"

    fun langOf(setId: String?): CatalogLang? {
        val normalized = setId?.trim()?.lowercase(Locale.ROOT) ?: return null
        return CatalogLang.entries.firstOrNull { normalized.endsWith(it.setSuffix) }
    }

    fun isCatalogSet(setId: String?, lang: CatalogLang): Boolean = langOf(setId) == lang

    /** "sv08__ita" -> "sv08"; null se il set non e' di quel catalogo. */
    fun expansionOf(setId: String?, lang: CatalogLang): String? {
        val normalized = setId?.trim()?.lowercase(Locale.ROOT) ?: return null
        if (!normalized.endsWith(lang.setSuffix)) return null
        return normalized.removeSuffix(lang.setSuffix).ifBlank { null }
    }
}

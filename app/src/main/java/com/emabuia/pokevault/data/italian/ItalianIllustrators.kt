package com.emabuia.pokevault.data.italian

import androidx.compose.runtime.Immutable
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * Le risposte di `GET /v1/illustrators` e `GET /v1/illustrators/{nome}/cards`.
 *
 * Stanno qui, in `data.italian`, e non e' indifferente: le regole di shrinking
 * tengono `data.italian.**` e `data.remote.**` (proguard-rules.pro). Un DTO
 * letto da Gson per reflection messo fuori da quei package lascerebbe la
 * release verde e i test verdi, e la sezione illustratori vuota solo
 * sull'artefatto firmato -- nessuno *scrive* questi campi nel bytecode, quindi
 * R8 li considera morti e li pota.
 */

/**
 * Un illustratore come lo conosce il catalogo, col suo nome GREZZO.
 *
 * Il worker raggruppa per `cards.illustratore` cosi' com'e': SQLite non sa
 * ripiegare gli accenti, e una normalizzazione fatta a meta' lato server e
 * meta' lato app vorrebbe dire due verita' diverse sullo stesso dato. A unire
 * le grafie e a separare le collaborazioni ci pensa `IllustratorNames`.
 *
 * [cardIds] sono le chiavi immagine del catalogo ("DP1_IT_1.png"), non gli
 * apiCardId della collezione: la conversione la fa `CatalogRepository`, che e'
 * l'unico posto dove `buildItalianCardId` e' definito.
 */
@Immutable
data class ItalianIllustratorRecord(
    val name: String = "",
    val cardCount: Int = 0,
    val expansionCount: Int = 0,
    val cardIds: List<String> = emptyList()
)

@Immutable
data class ItalianIllustratorsResponse(
    val illustrators: List<ItalianIllustratorRecord> = emptyList(),
    /**
     * Quante carte pubblicate non dicono chi le ha disegnate (~2% del
     * catalogo). La schermata lo dichiara invece di far credere che la
     * copertura sia piena.
     */
    val cardsWithoutIllustrator: Int = 0
)

@Immutable
data class ItalianIllustratorCardsResponse(
    val illustrator: String = "",
    val cards: List<ItalianCardRecord> = emptyList()
)

object ItalianIllustratorsNormalizer {
    private val gson = Gson()
    private val illustratorsType = object : TypeToken<ItalianIllustratorsResponse>() {}.type
    private val cardsType = object : TypeToken<ItalianIllustratorCardsResponse>() {}.type
    private const val UTF8_BOM = "\uFEFF"

    fun parseIllustrators(json: String): ItalianIllustratorsResponse {
        val raw = json.removePrefix(UTF8_BOM).trim()
        if (raw.isBlank()) return ItalianIllustratorsResponse()
        val payload = gson.fromJson<ItalianIllustratorsResponse>(raw, illustratorsType)
            ?: ItalianIllustratorsResponse()
        return payload.copy(
            illustrators = payload.illustrators
                .filter { it.name.isNotBlank() && it.cardIds.isNotEmpty() }
        )
    }

    fun parseIllustratorCards(json: String): List<ItalianCardRecord> {
        val raw = json.removePrefix(UTF8_BOM).trim()
        if (raw.isBlank()) return emptyList()
        val payload = gson.fromJson<ItalianIllustratorCardsResponse>(raw, cardsType)
            ?: ItalianIllustratorCardsResponse()
        return payload.cards
            .filter { it.cardId.isNotBlank() && it.espansioneId.isNotBlank() && it.nome.isNotBlank() }
    }
}

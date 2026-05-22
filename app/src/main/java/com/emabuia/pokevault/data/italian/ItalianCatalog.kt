package com.emabuia.pokevault.data.italian

import androidx.compose.runtime.Immutable
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.util.Locale

@Immutable
data class ItalianAttackRecord(
    val nome: String = "",
    val danno: String = "",
    val descrizione: String = ""
)

@Immutable
data class ItalianCardRecord(
    val cardId: String = "",
    val espansioneId: String = "",
    val nome: String = "",
    val tipo: String? = null,
    val ps: String? = null,
    val attacchi: List<ItalianAttackRecord> = emptyList(),
    val regolaSpeciale: String? = null
) {
    fun imageReference(): ItalianImageReference? = ItalianCatalogNormalizer.toImageReference(cardId)

    fun imageUrl(baseUrl: String, size: String = "low"): String? {
        val reference = imageReference() ?: return null
        val normalizedBaseUrl = baseUrl.trim().trimEnd('/')
        return "$normalizedBaseUrl/images/it/${reference.folderName}/${reference.cardNumber}?size=$size"
    }
}

@Immutable
data class ItalianExpansionManifest(
    val espansioneId: String = "",
    val cardCount: Int = 0,
    val order: Int = 0,
    val logoKey: String = ""
)

@Immutable
data class ItalianCatalog(
    val cards: List<ItalianCardRecord> = emptyList(),
    val expansions: List<ItalianExpansionManifest> = emptyList()
) {
    fun cardsByExpansion(): Map<String, List<ItalianCardRecord>> =
        cards.groupBy { it.espansioneId.lowercase(Locale.ROOT) }
}

data class ItalianCatalogPayload(
    val cards: List<ItalianCardRecord> = emptyList(),
    val expansions: List<ItalianExpansionManifest> = emptyList()
)

@Immutable
data class ItalianImageReference(
    val setCode: String,
    val cardNumber: String
) {
    val folderName: String get() = setCode.uppercase(Locale.ROOT)
    val preferredFileName: String get() = "${folderName}_IT_${cardNumber}.png"
    val fallbackFileName: String get() = "$cardNumber.png"
    val r2Prefix: String get() = "it/${folderName}"
    val preferredR2Key: String get() = "$r2Prefix/$preferredFileName"
    val fallbackR2Key: String get() = "$r2Prefix/$fallbackFileName"

    fun logoUrl(baseUrl: String): String {
        val normalizedBaseUrl = baseUrl.trim().trimEnd('/')
        return "$normalizedBaseUrl/sets/$folderName/image"
    }
}

object ItalianCatalogNormalizer {
    private val gson = Gson()
    private val cardListType = object : TypeToken<List<ItalianCardRecord>>() {}.type
    private val catalogPayloadType = object : TypeToken<ItalianCatalogPayload>() {}.type
    private const val UTF8_BOM = "\uFEFF"
    private val imageIdRegex = Regex(
        "^([A-Za-z0-9]+)_IT_([A-Za-z0-9_]+)\\.(png|webp|jpe?g)$",
        RegexOption.IGNORE_CASE
    )

    fun parseCards(json: String): List<ItalianCardRecord> {
        val parsed: List<ItalianCardRecord> = gson.fromJson(stripUtf8Bom(json), cardListType) ?: emptyList()
        return parsed
            .filter { it.cardId.isNotBlank() && it.espansioneId.isNotBlank() && it.nome.isNotBlank() }
            .sortedWith(cardComparator())
    }

    fun toCanonicalJson(cards: List<ItalianCardRecord>): String {
        return gson.toJson(cards.sortedWith(cardComparator()))
    }

    fun parseCatalogJson(json: String): ItalianCatalog {
        val raw = stripUtf8Bom(json).trim()
        if (raw.isBlank()) return ItalianCatalog()

        return if (raw.startsWith("[")) {
            buildCatalog(parseCards(raw))
        } else {
            val payload = gson.fromJson<ItalianCatalogPayload>(raw, catalogPayloadType) ?: ItalianCatalogPayload()
            val normalizedCards = payload.cards.sortedWith(cardComparator())
            if (payload.expansions.isNotEmpty()) {
                ItalianCatalog(
                    cards = normalizedCards,
                    expansions = payload.expansions.sortedBy { it.order }
                )
            } else {
                buildCatalog(normalizedCards)
            }
        }
    }

    fun toCatalogJson(catalog: ItalianCatalog): String {
        return gson.toJson(
            ItalianCatalogPayload(
                cards = catalog.cards.sortedWith(cardComparator()),
                expansions = catalog.expansions.sortedBy { it.order }
            )
        )
    }

    private fun stripUtf8Bom(raw: String): String = raw.removePrefix(UTF8_BOM)

    fun buildCatalog(cards: List<ItalianCardRecord>): ItalianCatalog {
        val normalizedCards = cards.sortedWith(cardComparator())
        val expansions = normalizedCards
            .groupBy { it.espansioneId.lowercase(Locale.ROOT) }
            .entries
            .sortedWith(compareBy<Map.Entry<String, List<ItalianCardRecord>>> { expansionOrder(it.key) }.thenBy { it.key })
            .mapIndexed { index, entry ->
                ItalianExpansionManifest(
                    espansioneId = entry.key,
                    cardCount = entry.value.size,
                    order = index,
                    logoKey = "it/${entry.key.uppercase(Locale.ROOT)}/logo.png"
                )
            }

        return ItalianCatalog(
            cards = normalizedCards,
            expansions = expansions
        )
    }

    fun toImageReference(cardId: String): ItalianImageReference? {
        val match = imageIdRegex.matchEntire(cardId.trim()) ?: return null
        val setCode = match.groupValues[1].trim()
        val rawCardNumber = match.groupValues[2].trim()
        val cardNumber = rawCardNumber.toIntOrNull()?.toString() ?: rawCardNumber
        return ItalianImageReference(setCode = setCode, cardNumber = cardNumber)
    }

    private fun cardComparator(): Comparator<ItalianCardRecord> {
        return compareBy<ItalianCardRecord> { expansionOrder(it.espansioneId.lowercase(Locale.ROOT)) }
            .thenBy { extractCardNumber(it.cardId) }
            .thenBy { it.cardId }
    }

    private fun extractCardNumber(cardId: String): Int {
        return toImageReference(cardId)?.cardNumber?.toIntOrNull() ?: Int.MAX_VALUE
    }

    private fun expansionOrder(expansionId: String): Int {
        return when (expansionId.lowercase(Locale.ROOT)) {
            "svp" -> 0
            "mep" -> 1
            else -> 100
        }
    }
}

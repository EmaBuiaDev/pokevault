package com.emabuia.pokevault.data.remote

import java.text.Normalizer

internal const val TCGDEX_SET_ID_PREFIX = "tcgdex__"

internal fun encodeTcgdexSetId(rawId: String): String = "$TCGDEX_SET_ID_PREFIX$rawId"

internal fun isTcgdexSetId(setId: String): Boolean = setId.startsWith(TCGDEX_SET_ID_PREFIX)

internal fun decodeTcgdexSetId(setId: String): String = setId.removePrefix(TCGDEX_SET_ID_PREFIX)

internal fun normalizeSetText(value: String): String {
    val normalized = Normalizer.normalize(value, Normalizer.Form.NFD)
        .replace("&", " ")
        .replace("\n", " ")
        .replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")
        .lowercase()

    return normalized
        .replace(Regex("[^a-z0-9]+"), " ")
        .trim()
        .replace(Regex("\\s+"), " ")
}

internal fun canonicalPrimaryCount(set: TcgSet): Int = when {
    set.printedTotal > 0 -> set.printedTotal
    set.total > 0 -> set.total
    else -> 0
}

internal fun canonicalTcgdexCount(cardCount: TcgdexCardCount): Int = when {
    cardCount.official > 0 -> cardCount.official
    cardCount.total > 0 -> cardCount.total
    else -> 0
}

internal fun buildSetMergeKey(name: String, series: String, count: Int): String {
    return "${normalizeSetText(name)}|${normalizeSetText(series)}|$count"
}

internal fun buildLooseSetMergeKey(name: String, count: Int): String {
    return "${normalizeSetText(name)}|$count"
}

internal fun hasTcgdexSummaryAssets(summary: TcgdexSetSummary): Boolean {
    return summary.logo.isNotBlank() && canonicalTcgdexCount(summary.cardCount) > 0
}

internal fun hasCompleteTcgdexAssets(detail: TcgdexSetDetail): Boolean {
    return detail.logo.isNotBlank() &&
        detail.cards.isNotEmpty() &&
        detail.cards.all { it.image.isNotBlank() }
}

internal fun mergePrimaryWithExtras(primarySets: List<TcgSet>, extraSets: List<TcgSet>): List<TcgSet> {
    if (extraSets.isEmpty()) return primarySets

    val seenKeys = primarySets
        .map { buildSetMergeKey(it.name, it.series, canonicalPrimaryCount(it)) }
        .toMutableSet()

    val extras = extraSets
        .sortedWith(compareByDescending<TcgSet> { it.releaseDate }.thenBy { it.name })
        .filter { extra ->
            val key = buildSetMergeKey(extra.name, extra.series, canonicalPrimaryCount(extra))
            seenKeys.add(key)
        }

    return primarySets + extras
}

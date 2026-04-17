package com.emabuia.pokevault.data.remote

import java.text.Normalizer

object SetCodeMapper {

    private val aliasToApiId: Map<String, String> = mapOf(
        "SVI" to "sv1",
        "PAL" to "sv2",
        "OBF" to "sv3",
        "MEW" to "sv3pt5",
        "PAR" to "sv4",
        "PAF" to "sv4pt5",
        "TEF" to "sv5",
        "TWM" to "sv6",
        "SFA" to "sv6pt5",
        "SCR" to "sv7",
        "SSP" to "sv8",
        "PRE" to "sv8pt5",
        "JTG" to "sv9",
        "DRI" to "sv10",
        "RCL" to "swsh2",
        "DAA" to "swsh3",
        "CPA" to "swsh35",
        "VIV" to "swsh4",
        "BST" to "swsh5",
        "CRE" to "swsh6",
        "EVS" to "swsh7",
        "FST" to "swsh8",
        "BRS" to "swsh9",
        "ASR" to "swsh10",
        "LOR" to "swsh11",
        "SIT" to "swsh12",
        "CRZ" to "swsh12pt5"
    )

    fun normalizeDecklistSetCode(raw: String?): String? {
        val cleaned = raw?.trim()?.uppercase()?.takeIf { it.isNotBlank() } ?: return null
        return aliasToApiId[cleaned] ?: cleaned
    }

    fun matchesImportedSet(importedSet: String?, cardSetName: String?, cardApiSetId: String?, cardApiId: String?): Boolean {
        val importedTokens = buildImportedTokens(importedSet)
        if (importedTokens.isEmpty()) return true
        val cardTokens = buildCardTokens(cardSetName, cardApiSetId, cardApiId)
        return importedTokens.any { it in cardTokens }
    }

    private fun buildImportedTokens(importedSet: String?): Set<String> {
        val input = importedSet?.trim()?.takeIf { it.isNotBlank() } ?: return emptySet()
        val upper = input.uppercase()
        val tokens = linkedSetOf<String>()

        normalizeToken(input)?.let { tokens += it }
        acronym(input)?.let { tokens += it }

        aliasToApiId[upper]?.let {
            normalizeToken(it)?.let { token -> tokens += token }
        }

        aliasToApiId.entries
            .filter { it.value.equals(upper, ignoreCase = true) }
            .forEach { entry ->
                normalizeToken(entry.key)?.let { tokens += it }
            }

        return tokens
    }

    private fun buildCardTokens(cardSetName: String?, cardApiSetId: String?, cardApiId: String?): Set<String> {
        val tokens = linkedSetOf<String>()

        normalizeToken(cardSetName)?.let { tokens += it }
        acronym(cardSetName)?.let { tokens += it }
        normalizeToken(cardApiSetId)?.let { tokens += it }

        val apiPrefix = cardApiId?.substringBefore("-")
        normalizeToken(apiPrefix)?.let { tokens += it }

        aliasToApiId.entries
            .filter { (_, apiId) ->
                cardApiSetId?.equals(apiId, ignoreCase = true) == true ||
                    apiPrefix?.equals(apiId, ignoreCase = true) == true
            }
            .forEach { entry ->
                normalizeToken(entry.key)?.let { tokens += it }
            }

        return tokens
    }

    private fun acronym(value: String?): String? {
        val normalizedWords = normalizeText(value)
            .split(" ")
            .filter { it.isNotBlank() }
        if (normalizedWords.size < 2) return null
        val built = normalizedWords.joinToString(separator = "") { it.first().toString() }
        return built.takeIf { it.length in 2..5 }
    }

    private fun normalizeToken(value: String?): String? {
        val normalized = normalizeText(value)
            .replace(" ", "")
            .takeIf { it.isNotBlank() }
        return normalized
    }

    private fun normalizeText(value: String?): String {
        if (value.isNullOrBlank()) return ""
        val normalized = Normalizer.normalize(value, Normalizer.Form.NFD)
            .replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")
            .lowercase()
        return normalized
            .replace(Regex("[^a-z0-9]+"), " ")
            .trim()
            .replace(Regex("\\s+"), " ")
    }
}
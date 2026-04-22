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
        "BLK" to "sv11",
        "WHT" to "sv11",
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

    private val apiIdRegex = Regex("^[a-z0-9]+$")

    private val setNameToApiId: Map<String, String> = mapOf(
        "scarlet violet" to "sv1",
        "paldea evolved" to "sv2",
        "obsidian flames" to "sv3",
        "pokemon 151" to "sv3pt5",
        "paradox rift" to "sv4",
        "paldean fates" to "sv4pt5",
        "temporal forces" to "sv5",
        "twilight masquerade" to "sv6",
        "shrouded fable" to "sv6pt5",
        "stellar crown" to "sv7",
        "surging sparks" to "sv8",
        "prismatic evolutions" to "sv8pt5",
        "journey together" to "sv9",
        "destined rivals" to "sv10",
        "black bolt" to "sv11",
        "white flare" to "sv11",
        "rebel clash" to "swsh2",
        "darkness ablaze" to "swsh3",
        "champions path" to "swsh35",
        "vivid voltage" to "swsh4",
        "battle styles" to "swsh5",
        "chilling reign" to "swsh6",
        "evolving skies" to "swsh7",
        "fusion strike" to "swsh8",
        "brilliant stars" to "swsh9",
        "astral radiance" to "swsh10",
        "lost origin" to "swsh11",
        "silver tempest" to "swsh12",
        "crown zenith" to "swsh12pt5"
    )

    fun normalizeDecklistSetCode(raw: String?): String? {
        val cleaned = raw?.trim()?.takeIf { it.isNotBlank() } ?: return null
        val upper = cleaned.uppercase()
        aliasToApiId[upper]?.let { return it }

        val normalizedName = normalizeSetName(cleaned)
        setNameToApiId[normalizedName]?.let { return it }

        val lower = cleaned.lowercase()
        if (apiIdRegex.matches(lower) && lower.any { it.isDigit() }) {
            return lower
        }

        return upper
    }

    fun matchesImportedSet(importedSet: String?, cardSetName: String?, cardApiSetId: String?, cardApiId: String?): Boolean {
        val importedCanonical = normalizeDecklistSetCode(importedSet) ?: return true
        val cardCanonical = linkedSetOf<String>()

        normalizeDecklistSetCode(cardApiSetId)?.let { cardCanonical += it }
        normalizeDecklistSetCode(cardApiId?.substringBefore("-"))?.let { cardCanonical += it }
        normalizeDecklistSetCode(cardSetName)?.let { cardCanonical += it }

        return importedCanonical in cardCanonical
    }

    fun searchTokensForSetQuery(raw: String?): List<String> {
        val canonical = normalizeDecklistSetCode(raw) ?: return emptyList()
        val tokens = linkedSetOf<String>()

        aliasToApiId.entries
            .filter { (_, apiId) -> apiId.equals(canonical, ignoreCase = true) }
            .forEach { (alias, _) -> tokens += alias }

        tokens += canonical.uppercase()

        raw?.trim()
            ?.takeIf { it.isNotBlank() }
            ?.uppercase()
            ?.let { tokens += it }

        return tokens.toList()
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

    private fun normalizeSetName(value: String?): String = normalizeText(value)
}
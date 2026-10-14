package com.emabuia.pokevault.data.catalog

import java.text.Normalizer

/**
 * Come la ricerca carte confronta un nome con quello che si e' scritto: la
 * stessa regola per il catalogo italiano e per quello inglese, cosi' con
 * "Tutte" i risultati delle due lingue si ordinano sulla stessa scala.
 */
object CardNameMatch {

    private val MULTI_SPACE = Regex("\\s+")
    private val CAMEL = Regex("([a-z])([A-Z])")
    private val DIACRITICS = Regex("\\p{InCombiningDiacriticalMarks}+")
    private val NON_ALNUM = Regex("[^a-z0-9]+")

    fun sanitize(raw: String): String = raw.replace(MULTI_SPACE, " ").trim()

    /** Minuscolo, senza accenti e senza segni: "Pikachu-EX" -> "pikachu ex". */
    fun normalize(raw: String?): String {
        if (raw.isNullOrBlank()) return ""
        val deCamel = raw.replace(CAMEL, "$1 $2")
        val normalized = Normalizer.normalize(deCamel, Normalizer.Form.NFD)
            .replace(DIACRITICS, "")
            .lowercase()
            .replace(NON_ALNUM, " ")
            .trim()
        return sanitize(normalized)
    }

    /** Da 1000 (nome identico) a 0 (non c'entra): nomi e query gia' normalizzati. */
    fun score(normalizedName: String, normalizedQuery: String, queryTokens: List<String>): Int {
        if (normalizedName == normalizedQuery) return 1000

        val startsWith = normalizedName.startsWith(normalizedQuery)
        val wordContains = " $normalizedName ".contains(" $normalizedQuery ")

        if (startsWith) return 850
        if (wordContains) return 700
        if (normalizedName.contains(normalizedQuery)) return 560

        val longTokens = queryTokens.filter { it.length >= 2 }
        if (longTokens.isNotEmpty() && longTokens.all { token -> normalizedName.contains(token) }) {
            return 420
        }
        if (longTokens.any { token -> token.length >= 3 && " $normalizedName ".contains(" $token ") }) {
            return 260
        }

        return 0
    }
}

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

    /**
     * Il confronto dello scanner, piu' tollerante: il nome letto dall'OCR e'
     * spesso sporco, quindi oltre a "comincia/contiene" conta anche quanto le
     * due parole si somigliano (distanza di Levenshtein). Da 120 a 0.
     */
    fun scannerScore(normalizedName: String, normalizedQuery: String): Int {
        if (normalizedQuery.isBlank()) return 0
        if (normalizedName == normalizedQuery) return 120
        if (normalizedName.startsWith(normalizedQuery) || normalizedQuery.startsWith(normalizedName)) return 105
        if (normalizedName.contains(normalizedQuery) || normalizedQuery.contains(normalizedName)) return 88

        val queryTokens = normalizedQuery.split(" ").filter { it.length >= 2 }
        val nameTokens = normalizedName.split(" ").filter { it.length >= 2 }
        if (queryTokens.isNotEmpty() && queryTokens.all { token -> nameTokens.any { it.contains(token) || token.contains(it) } }) {
            return 72
        }

        val distance = levenshtein(normalizedName, normalizedQuery)
        val maxLength = maxOf(normalizedName.length, normalizedQuery.length).coerceAtLeast(1)
        val similarity = 1.0 - distance.toDouble() / maxLength.toDouble()
        return when {
            similarity >= 0.82 -> 68
            similarity >= 0.72 -> 52
            similarity >= 0.62 -> 36
            else -> 0
        }
    }

    fun levenshtein(left: String, right: String): Int {
        if (left == right) return 0
        if (left.isEmpty()) return right.length
        if (right.isEmpty()) return left.length

        val previous = IntArray(right.length + 1) { it }
        val current = IntArray(right.length + 1)

        for (leftIndex in left.indices) {
            current[0] = leftIndex + 1
            for (rightIndex in right.indices) {
                val substitutionCost = if (left[leftIndex] == right[rightIndex]) 0 else 1
                current[rightIndex + 1] = minOf(
                    current[rightIndex] + 1,
                    previous[rightIndex + 1] + 1,
                    previous[rightIndex] + substitutionCost
                )
            }
            previous.indices.forEach { index -> previous[index] = current[index] }
        }

        return previous[right.length]
    }
}

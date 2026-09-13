package com.emabuia.pokevault.ocr

/**
 * Parser dell'ID stampato in basso a sinistra sulla carta ("067/087").
 *
 * E' l'unico dato identico in ogni lingua: numero da collezione + totale
 * stampato del set. Per lo scanner vale piu' del nome, che cambia lingua ed e'
 * il campo che l'OCR sbaglia piu' spesso; il totale, a sua volta, identifica
 * quasi sempre l'espansione da solo (087 -> Caos Nascente, 191 -> Scintille
 * Folgoranti, ...).
 *
 * Il testo in ingresso arriva da una striscia ritagliata e ingrandita
 * dell'angolo in basso a sinistra, dove oltre al numero compaiono il marchio
 * di regolamentazione (una lettera in un riquadro), il simbolo del set e il
 * simbolo di rarita': il parser deve sopravvivere a tutto quel contorno.
 */
object CardIdParser {

    /** Numero da collezione letto dall'angolo in basso a sinistra. */
    data class CardId(
        /** Numero normalizzato, senza zeri iniziali ("67", "TG05"). */
        val number: String,
        /** Totale stampato del set ("87"), null quando non leggibile. */
        val total: String?,
        /** 0..1 - quanto e' netta la lettura, non quanto e' sicura la carta. */
        val confidence: Float
    ) {
        val display: String get() = if (total != null) "$number/$total" else number
    }

    // ═══════════════════════════════════════════
    // API
    // ═══════════════════════════════════════════

    /**
     * Parsing della striscia ID (angolo in basso a sinistra, gia' ingrandita).
     * Qui possiamo permetterci le euristiche piu' aggressive: nella striscia
     * non esiste altro numero con cui confondersi.
     */
    fun parse(text: String): CardId? {
        if (text.isBlank()) return null
        return text.lines()
            .flatMap { line -> candidates(line, allowGluedDigits = true) }
            .maxByOrNull { it.confidence }
    }

    /**
     * Parsing dal testo dell'intera carta: ci sono danni, costi di ritirata e
     * anni di copyright, quindi accettiamo solo le forme esplicite e teniamo
     * l'ultima occorrenza (l'ID sta in fondo).
     */
    fun parseFromCardText(text: String): CardId? {
        if (text.isBlank()) return null
        val matches = text.lines().flatMap { line -> candidates(line, allowGluedDigits = false) }
        if (matches.isEmpty()) return null
        val best = matches.maxOf { it.confidence }
        return matches.last { it.confidence == best }
    }

    // ═══════════════════════════════════════════
    // RICONOSCIMENTO
    // ═══════════════════════════════════════════

    /** Separatore stampato come "/" e letto correttamente. */
    private val SLASH_PATTERN = Regex(
        """([A-Z]{0,3})\s*([0-9OoQIliSsBZzGTAgq|!]{1,3})\s*[/⁄∕]\s*([A-Z]{0,3})\s*([0-9OoQIliSsBZzGTAgq|!]{1,4})""",
        RegexOption.IGNORE_CASE
    )

    /** Separatore letto come una barra verticale o una lettera stretta. */
    private val BAR_PATTERN = Regex(
        """([A-Z]{0,3})\s*([0-9]{2,3})\s*[|Il!¦fjJ\\]\s*([A-Z]{0,3})\s*([0-9]{2,4})""",
        RegexOption.IGNORE_CASE
    )

    /** Numero da promo: "SVP 045", "SWSH123" - senza totale stampato. */
    private val PROMO_PATTERN = Regex(
        """\b(SVP|SWSH|SM|XY|BW|HGSS|DP|PROMO)\s*[-_ ]?\s*([0-9]{1,3})\b""",
        RegexOption.IGNORE_CASE
    )

    /** Cifre incollate: "067087", "0671087" (lo slash letto come 1). */
    private val GLUED_PATTERN = Regex("""(?<![0-9])([0-9]{5,7})(?![0-9])""")

    /** Righe che non possono contenere l'ID, per quanto assomiglino a un numero. */
    private val NOISE_LINE = Regex(
        """©|nintendo|creatures|game\s*freak|gamefreak|pok[eé]mon\s*co|\bHP\b|\bPV\b""",
        RegexOption.IGNORE_CASE
    )

    /** Spazio unificatore: ML Kit lo infila spesso tra numero e totale. */
    private const val NBSP = ' '

    private val YEAR = Regex("""^(19|20)\d{2}$""")

    /** Sottoserie che stampano il prefisso dentro il numero ("TG05/TG30"). */
    private val SUBSET_PREFIXES = setOf("TG", "GG", "SV", "H")

    private val DIGIT_LOOKALIKES = mapOf(
        'O' to '0', 'o' to '0', 'Q' to '0',
        'I' to '1', 'l' to '1', 'i' to '1', '|' to '1', '!' to '1',
        'S' to '5', 's' to '5',
        'B' to '8',
        'Z' to '2', 'z' to '2',
        'G' to '6',
        'T' to '7',
        'A' to '4',
        'g' to '9', 'q' to '9'
    )

    private fun candidates(rawLine: String, allowGluedDigits: Boolean): List<CardId> {
        val line = rawLine.replace(NBSP, ' ').trim()
        if (line.isBlank() || NOISE_LINE.containsMatchIn(line)) return emptyList()

        val found = mutableListOf<CardId>()

        SLASH_PATTERN.findAll(line).forEach { match ->
            val (prefix, rawNumber, totalPrefix, rawTotal) = match.destructured
            build(
                prefix = prefix,
                rawNumber = rawNumber,
                totalPrefix = totalPrefix,
                rawTotal = rawTotal,
                confidence = 0.95f
            )?.let(found::add)
        }

        BAR_PATTERN.findAll(line).forEach { match ->
            val (prefix, rawNumber, totalPrefix, rawTotal) = match.destructured
            build(
                prefix = prefix,
                rawNumber = rawNumber,
                totalPrefix = totalPrefix,
                rawTotal = rawTotal,
                confidence = 0.78f
            )?.let(found::add)
        }

        if (found.isEmpty()) {
            PROMO_PATTERN.find(line)?.let { match ->
                val number = match.groupValues[2].trimStart('0').ifBlank { "0" }
                if ((number.toIntOrNull() ?: 0) in 1..999) {
                    found += CardId(number = number, total = null, confidence = 0.70f)
                }
            }
        }

        if (allowGluedDigits && found.isEmpty()) {
            GLUED_PATTERN.findAll(line).forEach { match ->
                splitGlued(match.groupValues[1])?.let(found::add)
            }
        }

        return found
    }

    /**
     * "067087" -> 67/87, "0671087" -> 67/87 (lo slash letto come 1 o 7).
     * Solo sulla striscia ID: nel testo pieno produrrebbe falsi positivi.
     */
    private fun splitGlued(digits: String): CardId? {
        val splits = when (digits.length) {
            5 -> listOf(2 to 3)
            6 -> listOf(3 to 3, 2 to 4)
            7 -> if (digits[3] == '1' || digits[3] == '7') listOf(3 to 3) else emptyList()
            else -> emptyList()
        }

        return splits.firstNotNullOfOrNull { (left, right) ->
            build(
                prefix = "",
                rawNumber = digits.take(left),
                totalPrefix = "",
                rawTotal = digits.takeLast(right),
                confidence = 0.50f,
                minTotal = 20
            )
        }
    }

    private fun build(
        prefix: String,
        rawNumber: String,
        totalPrefix: String,
        rawTotal: String,
        confidence: Float,
        minTotal: Int = 12
    ): CardId? {
        val numberDigits = repairDigits(rawNumber) ?: return null
        val totalDigits = repairDigits(rawTotal) ?: return null
        if (YEAR.matches(numberDigits) || YEAR.matches(totalDigits)) return null

        val number = numberDigits.toIntOrNull() ?: return null
        val total = totalDigits.toIntOrNull() ?: return null
        if (number !in 1..999) return null
        if (total < minTotal || total > 999) return null
        // Le segrete superano il totale stampato, ma di poco: oltre questo margine
        // stiamo leggendo due numeri che non hanno niente a che fare tra loro.
        if (number > total + 150) return null

        val normalizedPrefix = prefix.ifBlank { totalPrefix }
            .uppercase()
            .takeIf { it in SUBSET_PREFIXES }

        val adjustedConfidence = if (total < 20) confidence - 0.15f else confidence

        // Con prefisso il numero resta come stampato ("TG05"): e' la forma con cui
        // sta nel catalogo. Senza prefisso si normalizza togliendo gli zeri iniziali.
        val normalizedNumber = if (normalizedPrefix != null) {
            normalizedPrefix + numberDigits
        } else {
            numberDigits.trimStart('0').ifBlank { "0" }
        }

        return CardId(
            number = normalizedNumber,
            total = totalDigits.trimStart('0').ifBlank { "0" },
            confidence = adjustedConfidence.coerceIn(0f, 1f)
        )
    }

    /**
     * Ripara le confusioni tipiche dell'OCR sulle cifre ("O67" -> "067").
     * Richiede almeno una cifra vera: senza, e' una parola, non un numero.
     */
    private fun repairDigits(raw: String): String? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty() || trimmed.length > 4) return null
        if (trimmed.none { it.isDigit() }) return null

        val repaired = buildString {
            trimmed.forEach { char ->
                val digit = if (char.isDigit()) char else DIGIT_LOOKALIKES[char] ?: return null
                append(digit)
            }
        }
        return repaired
    }
}

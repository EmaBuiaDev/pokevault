package com.example.pokevault.data.model

/**
 * Parser per decklist in formato testo standard PTCG.
 *
 * Formati supportati:
 * - "4 Charizard ex SVI 125"
 * - "4 Charizard ex sv1 125"
 * - "4 Charizard ex PAL 125"
 * - Con header di sezione: "Pokémon: 12", "Trainer: 30", "Energy: 18"
 * - Anche senza header (inferisce il tipo dal nome)
 */
object DeckImportParser {

    data class ParsedCard(
        val name: String,
        val set: String?,
        val number: String?,
        val qty: Int,
        val type: String // "pokemon", "trainer", "energy", "unknown"
    )

    data class ParseResult(
        val cards: List<ParsedCard>,
        val deckName: String?,
        val errors: List<String>
    )

    fun parse(text: String): ParseResult {
        val lines = text.lines().map { it.trim() }.filter { it.isNotEmpty() }
        val cards = mutableListOf<ParsedCard>()
        val errors = mutableListOf<String>()
        var currentSection: String? = null
        var deckName: String? = null

        for (line in lines) {
            // Ignora righe di commento
            if (line.startsWith("//") || line.startsWith("#")) continue

            // Controlla se è un header di sezione
            val sectionHeader = parseSectionHeader(line)
            if (sectionHeader != null) {
                currentSection = sectionHeader
                continue
            }

            // Prova a parsare come carta
            val card = parseCardLine(line, currentSection)
            if (card != null) {
                cards.add(card)
            } else if (line.length > 3) {
                // Potrebbe essere il nome del deck
                if (deckName == null && cards.isEmpty() && !line[0].isDigit()) {
                    deckName = line
                } else {
                    errors.add("Riga non riconosciuta: $line")
                }
            }
        }

        return ParseResult(cards = cards, deckName = deckName, errors = errors)
    }

    private fun parseSectionHeader(line: String): String? {
        val lower = line.lowercase().removeSuffix(":").trim()

        // "Pokémon: 12" or "Pokemon (12)" or just "Pokémon"
        val headerRegex = Regex("""^(pok[eé]mon|trainer|energy|energia|allenatore)[:\s]*(\d*).*$""", RegexOption.IGNORE_CASE)
        val match = headerRegex.find(lower)

        if (match != null) {
            return when {
                match.groupValues[1].startsWith("pok") -> "pokemon"
                match.groupValues[1] == "trainer" || match.groupValues[1] == "allenatore" -> "trainer"
                match.groupValues[1] == "energy" || match.groupValues[1] == "energia" -> "energy"
                else -> null
            }
        }
        return null
    }

    /**
     * Parsa una riga come carta.
     * Formato atteso: "QTY NAME SET NUMBER"
     * Esempio: "4 Charizard ex SVI 125"
     *
     * Il set e il numero sono opzionali:
     * "4 Charizard ex" è valido (set e number saranno null)
     */
    private fun parseCardLine(line: String, currentSection: String?): ParsedCard? {
        // Deve iniziare con un numero (quantità)
        val qtyRegex = Regex("""^(\d+)\s+(.+)$""")
        val qtyMatch = qtyRegex.find(line) ?: return null

        val qty = qtyMatch.groupValues[1].toIntOrNull() ?: return null
        if (qty <= 0 || qty > 60) return null

        val rest = qtyMatch.groupValues[2].trim()

        // Prova a estrarre set e numero dalla fine
        // Pattern: "... SET_CODE NUMBER" dove SET_CODE è 2-5 lettere/numeri e NUMBER è numeri
        val setNumberRegex = Regex("""^(.+?)\s+([A-Za-z]{2,5}\d*)\s+(\d+\w*)$""")
        val setMatch = setNumberRegex.find(rest)

        val name: String
        val set: String?
        val number: String?

        if (setMatch != null) {
            name = setMatch.groupValues[1].trim()
            set = setMatch.groupValues[2].uppercase()
            number = setMatch.groupValues[3]
        } else {
            name = rest
            set = null
            number = null
        }

        if (name.isBlank()) return null

        val type = currentSection ?: inferCardType(name)

        return ParsedCard(
            name = name,
            set = set,
            number = number,
            qty = qty,
            type = type
        )
    }

    private fun inferCardType(name: String): String {
        val lower = name.lowercase()
        return when {
            lower.contains("energy") || lower.contains("energia") -> "energy"
            lower.contains("professor") ||
                lower.contains("boss") ||
                lower.contains("judge") ||
                lower.contains("iono") ||
                lower.contains("research") ||
                lower.contains("nest ball") ||
                lower.contains("ultra ball") ||
                lower.contains("rare candy") ||
                lower.contains("switch") ||
                lower.contains("catcher") ||
                lower.contains("battle vip") ||
                lower.contains("pal pad") ||
                lower.contains("super rod") ||
                lower.contains("tool") ||
                lower.contains("cape") ||
                lower.contains("choice") ||
                lower.contains("rescue board") ||
                lower.contains("counter") ||
                lower.contains("forest seal") ||
                lower.contains("lost vacuum") ||
                lower.contains("escape rope") ||
                lower.contains("level ball") ||
                lower.contains("hisuian heavy ball") ||
                lower.contains("path to the peak") ||
                lower.contains("collapsed stadium") ||
                lower.contains("temple") ||
                lower.contains("beach court") ||
                lower.contains("artazon") ||
                lower.contains("arven") ||
                lower.contains("penny") ||
                lower.contains("worker") ||
                lower.contains("roxanne") ||
                lower.contains("marnie") ||
                lower.contains("colress") -> "trainer"
            else -> "pokemon"
        }
    }
}

package com.emabuia.pokevault.ocr

import android.util.Log
import com.emabuia.pokevault.BuildConfig

/**
 * Parser specializzato per estrarre campi strutturati dal testo OCR di carte Pokemon.
 *
 * Gestisce:
 * - Nome carta (con rimozione suffissi stage, HP, ecc.)
 * - Numero carta / totale set (es. "025/198")
 * - HP
 * - Stage/evoluzione
 * - Tipo (fuoco, acqua, ecc.)
 * - Rarita (da simbolo o testo)
 * - Variante (EX, GX, V, VSTAR, holo, reverse, ecc.)
 * - Illustratore
 * - Set (se identificabile dal testo)
 *
 * Supporta sia il parsing "full-frame" (testo intero) sia il parsing
 * "zone-aware" (testo gia suddiviso per zone della carta).
 */
object CardFieldParser {

    private const val TAG = "CardFieldParser"

    // ═══════════════════════════════════════════
    // PARSING FULL-FRAME (testo intero OCR)
    // ═══════════════════════════════════════════

    /**
     * Estrae tutti i campi dal testo OCR grezzo (full-frame).
     * Il testo viene suddiviso in righe e analizzato euristicamente.
     */
    fun parseFullText(rawText: String): CardOCRResult {
        if (rawText.isBlank()) return CardOCRResult(rawText = rawText)

        val lines = rawText.lines().map { it.trim() }.filter { it.isNotBlank() }
        if (lines.isEmpty()) return CardOCRResult(rawText = rawText)

        val number = extractCardNumber(rawText)
        val setTotal = extractSetTotal(rawText)
        val hp = extractHP(rawText)
        val stage = extractStage(rawText)
        val name = extractCardName(lines, hp, stage)
        val illustrator = extractIllustrator(rawText)
        val supertype = detectSupertype(rawText, hp, name)
        val variant = detectVariant(name, rawText)
        val rarity = detectRarity(rawText)

        val result = CardOCRResult(
            cardName = name,
            cardNumber = number,
            setTotal = setTotal,
            hp = hp,
            stage = stage,
            illustrator = illustrator,
            supertype = supertype,
            variant = variant,
            rarity = rarity,
            rawText = rawText,
            confidence = estimateConfidence(name, number, hp)
        )

        if (BuildConfig.DEBUG) Log.d(TAG, "Parsed: name=$name, number=$number/$setTotal, hp=$hp, variant=$variant")
        return result
    }

    // ═══════════════════════════════════════════
    // PARSING ZONE-AWARE (testo per zona)
    // ═══════════════════════════════════════════

    /**
     * Estrae campi dal testo OCR suddiviso per zone della carta.
     * Piu preciso del full-frame perche sa dove si trova ogni testo.
     */
    fun parseZonedText(textBlocks: List<OCRTextBlock>): CardOCRResult {
        if (textBlocks.isEmpty()) return CardOCRResult()

        val fullText = textBlocks.joinToString("\n") { it.text }

        // Classifica blocchi per zona verticale
        val topBlocks = textBlocks.filter { it.normalizedY < 0.15f }
        val middleBlocks = textBlocks.filter { it.normalizedY in 0.15f..0.80f }
        val bottomBlocks = textBlocks.filter { it.normalizedY > 0.80f }

        val topText = topBlocks.joinToString(" ") { it.text }
        val bottomText = bottomBlocks.joinToString(" ") { it.text }

        // Nome: dalla zona top, e il testo piu grande/primo
        val name = extractCardNameFromTop(topText)

        // HP: dalla zona top
        val hp = extractHP(topText)

        // Stage: dalla zona top
        val stage = extractStage(topText)

        // Numero carta: dalla zona bottom
        val number = extractCardNumber(bottomText) ?: extractCardNumber(fullText)
        val setTotal = extractSetTotal(bottomText) ?: extractSetTotal(fullText)

        // Illustratore: dalla zona bottom
        val illustrator = extractIllustrator(bottomText)

        // Variante: dal nome + testo completo
        val variant = detectVariant(name, fullText)

        // Rarita: dal footer
        val rarity = detectRarity(bottomText)

        // Supertype
        val supertype = detectSupertype(fullText, hp, name)

        // Confidenza basata sulla completezza
        val zoneConfidence = textBlocks.map { it.confidence }.average().toFloat()
        val fieldConfidence = estimateConfidence(name, number, hp)

        return CardOCRResult(
            cardName = name,
            cardNumber = number,
            setTotal = setTotal,
            hp = hp,
            stage = stage,
            illustrator = illustrator,
            supertype = supertype,
            variant = variant,
            rarity = rarity,
            rawText = fullText,
            confidence = (zoneConfidence + fieldConfidence) / 2f,
            detectedZones = classifyTextZones(textBlocks)
        )
    }

    // ═══════════════════════════════════════════
    // ESTRAZIONE NUMERO CARTA
    // ═══════════════════════════════════════════

    /** Pattern per numero carta: "025/198", "25 / 198", "025/198 C", ecc. */
    private val CARD_NUMBER_PATTERN = Regex("""(\d{1,3})\s*/\s*(\d{1,3})""")

    /**
     * Estrae il numero della carta (es. "25" da "025/198").
     * Questo e il dato OCR PIU AFFIDABILE su una carta Pokemon.
     */
    fun extractCardNumber(text: String): String? {
        val match = CARD_NUMBER_PATTERN.find(text) ?: return null
        val num = match.groupValues[1].trimStart('0')
        return if (num.isNotBlank()) num else "0"
    }

    /** Estrae il totale del set (es. "198" da "025/198"), senza zero iniziali */
    fun extractSetTotal(text: String): String? {
        val match = CARD_NUMBER_PATTERN.find(text) ?: return null
        val total = match.groupValues[2].trimStart('0')
        return if (total.isNotBlank()) total else "0"
    }

    // ═══════════════════════════════════════════
    // ESTRAZIONE NOME CARTA
    // ═══════════════════════════════════════════

    /** Parole da escludere come nomi di carta (meccaniche di gioco, etc.) */
    private val NAME_EXCLUSIONS = setOf(
        "weakness", "resistance", "retreat", "cost",
        "illustrator", "illus.", "illus",
        "pokémon", "pokemon", "pocket",
        "damage", "attach", "opponent", "energy", "energia",
        "trainer", "allenatore", "supporter", "aiuto",
        "item", "strumento", "stadium", "stadio",
        "coin", "discard", "shuffle", "search",
        "your", "this", "each", "does", "from", "into",
        "draw", "put", "take", "choose", "look",
        "basic", "once", "during", "turn",
        "rule", "regulation", "mark",
        "©", "®", "™"
    )

    /** Pattern per HP nella stessa riga del nome */
    private val HP_IN_NAME_PATTERN = Regex("""[\s\-]+HP\s*\d+|[\s\-]+\d+\s*HP""", RegexOption.IGNORE_CASE)

    /** Pattern per stage/evoluzione prefisso */
    private val STAGE_PREFIX_PATTERN = Regex(
        """^(BASIC|Stage\s*[12]|STAGE\s*[12]|MEGA|M\s+)\s+""",
        RegexOption.IGNORE_CASE
    )

    /**
     * Estrae il nome della carta dal testo OCR completo.
     * Filtra meccaniche di gioco, HP, numeri, ecc.
     */
    fun extractCardName(
        lines: List<String>,
        hp: Int? = null,
        stage: String? = null
    ): String? {
        val candidates = lines.filter { line ->
            val lower = line.lowercase()
            val len = line.length

            // Deve avere almeno 3 caratteri e contenere lettere
            len >= 3 &&
            line.any { it.isLetter() } &&
            // Escludi righe che sono solo numeri/HP
            !lower.matches(Regex("""^\d+\s*hp$""")) &&
            !lower.matches(Regex("""^hp\s*\d+$""")) &&
            !lower.matches(Regex("""^\d+\s*/\s*\d+.*$""")) &&
            !lower.matches(Regex("""^\d+[x×]\s*\d+.*$""")) &&
            // Escludi righe che contengono parole escluse
            NAME_EXCLUSIONS.none { excl -> lower.contains(excl) } &&
            // Escludi righe troppo lunghe (probabilmente descrizione attacco)
            len <= 50
        }

        // Il nome e tipicamente la prima riga valida
        val rawName = candidates.firstOrNull() ?: return null

        return cleanCardName(rawName)
    }

    /**
     * Estrae il nome dalla zona TOP (piu preciso del full-text).
     */
    private fun extractCardNameFromTop(topText: String): String? {
        if (topText.isBlank()) return null

        // Rimuovi HP dal testo top
        var cleaned = topText
            .replace(HP_IN_NAME_PATTERN, "")
            .replace(STAGE_PREFIX_PATTERN, "")
            .trim()

        // Se rimane qualcosa di valido, e il nome
        return cleanCardName(cleaned).takeIf { it != null && it.length >= 2 }
    }

    /** Pulisce il nome rimuovendo artefatti OCR e formattando */
    private fun cleanCardName(rawName: String): String? {
        return rawName
            // Rimuovi HP se presente
            .replace(HP_IN_NAME_PATTERN, "")
            // Rimuovi numero carta se presente alla fine
            .replace(Regex("""\s+\d{1,3}/\d{1,3}$"""), "")
            // Rimuovi stage/evolution prefix
            .replace(STAGE_PREFIX_PATTERN, "")
            // Rimuovi caratteri non validi per un nome
            .replace(Regex("""[^a-zA-ZÀ-ÿ\s'\-\.♂♀é]"""), "")
            // Normalizza spazi
            .replace(Regex("""\s+"""), " ")
            .trim()
            // Limite lunghezza
            .take(45)
            .takeIf { it.length >= 2 }
    }

    // ═══════════════════════════════════════════
    // ESTRAZIONE HP
    // ═══════════════════════════════════════════

    private val HP_PATTERNS = listOf(
        Regex("""(\d{2,3})\s*HP""", RegexOption.IGNORE_CASE),
        Regex("""HP\s*(\d{2,3})""", RegexOption.IGNORE_CASE)
    )

    /** Estrae gli HP della carta. Range valido: 30-360 */
    fun extractHP(text: String): Int? {
        for (pattern in HP_PATTERNS) {
            val match = pattern.find(text)
            if (match != null) {
                val hp = match.groupValues[1].toIntOrNull()
                if (hp != null && hp in 30..360) return hp
            }
        }
        return null
    }

    // ═══════════════════════════════════════════
    // ESTRAZIONE STAGE/EVOLUZIONE
    // ═══════════════════════════════════════════

    /** Rileva stage/livello evoluzione */
    fun extractStage(text: String): String? {
        val lower = text.lowercase()
        return when {
            lower.contains("stage 2") || lower.contains("stage2") -> "Stage 2"
            lower.contains("stage 1") || lower.contains("stage1") -> "Stage 1"
            lower.contains("mega") || lower.startsWith("m ") -> "MEGA"
            lower.contains("break") -> "BREAK"
            lower.contains("basic") || lower.contains("base") -> "Basic"
            lower.contains("v-union") -> "V-UNION"
            lower.contains("vstar") -> "VSTAR"
            lower.contains("vmax") -> "VMAX"
            else -> null
        }
    }

    // ═══════════════════════════════════════════
    // RILEVAMENTO VARIANTE
    // ═══════════════════════════════════════════

    /**
     * Rileva la variante della carta dal nome e dal contesto OCR.
     * Priorita ai suffissi nel nome (EX, GX, V, VSTAR, VMAX).
     */
    fun detectVariant(name: String?, fullText: String): CardVariant {
        // Prima controlla il nome per suffissi specifici
        if (name != null) {
            val fromName = CardVariant.detectFromName(name)
            if (fromName != CardVariant.NORMAL) return fromName
        }

        // Poi controlla il testo completo per indicatori
        val upper = fullText.uppercase()
        return when {
            upper.contains("VSTAR") -> CardVariant.VSTAR
            upper.contains("VMAX") -> CardVariant.VMAX
            upper.contains("V-UNION") -> CardVariant.VUNION
            // "V" standalone (non parte di altre parole)
            Regex("""\bV\b""").containsMatchIn(upper) &&
                !upper.contains("VSTAR") && !upper.contains("VMAX") -> CardVariant.V
            upper.contains("TAG TEAM") -> CardVariant.TAG_TEAM
            Regex("""\bGX\b""").containsMatchIn(upper) -> CardVariant.GX
            Regex("""\bEX\b""").containsMatchIn(upper) -> CardVariant.EX
            Regex("""\bex\b""").containsMatchIn(fullText) -> CardVariant.EX_TERA
            upper.contains("RADIANT") -> CardVariant.RADIANT
            upper.contains("BREAK") -> CardVariant.BREAK
            upper.contains("PRIME") -> CardVariant.PRIME
            upper.contains("LV.X") || upper.contains("LV. X") -> CardVariant.LV_X
            else -> CardVariant.NORMAL
        }
    }

    // ═══════════════════════════════════════════
    // RILEVAMENTO RARITA
    // ═══════════════════════════════════════════

    /**
     * Rileva la rarita dal testo del footer.
     * Le carte Pokemon usano simboli: ● (comune), ◆ (non comune), ★ (rara),
     * ma spesso l'OCR non riconosce questi simboli correttamente.
     */
    fun detectRarity(text: String): String? {
        val lower = text.lowercase()
        return when {
            // Rarita testuale esplicita
            lower.contains("secret rare") || lower.contains("segreta") -> "Secret Rare"
            lower.contains("illustration rare") || lower.contains("illustrazione rara") -> "Illustration Rare"
            lower.contains("special art") -> "Special Art Rare"
            lower.contains("hyper rare") || lower.contains("iper rara") -> "Hyper Rare"
            lower.contains("ultra rare") || lower.contains("ultra rara") -> "Ultra Rare"
            lower.contains("double rare") || lower.contains("doppia rara") -> "Double Rare"
            lower.contains("full art") -> "Full Art"
            lower.contains("rare holo") -> "Rare Holo"
            lower.contains("rare") || lower.contains("rara") -> "Rare"
            lower.contains("uncommon") || lower.contains("non comune") -> "Uncommon"
            lower.contains("common") || lower.contains("comune") -> "Common"
            lower.contains("promo") -> "Promo"

            // Simboli (spesso OCR-izzati male, ma proviamo)
            text.contains("★") || text.contains("☆") -> "Rare"
            text.contains("◆") || text.contains("◇") -> "Uncommon"
            text.contains("●") || text.contains("○") -> "Common"

            else -> null
        }
    }

    // ═══════════════════════════════════════════
    // RILEVAMENTO SUPERTYPE
    // ═══════════════════════════════════════════

    /** Rileva se la carta e un Pokemon, Trainer o Energy */
    fun detectSupertype(text: String, hp: Int?, name: String?): CardSupertype {
        val lower = text.lowercase()

        // Energy
        if (lower.contains("energy") || lower.contains("energia")) {
            return CardSupertype.ENERGY
        }

        // Trainer (include Supporter, Item, Stadium, Tool)
        if (lower.contains("trainer") || lower.contains("allenatore") ||
            lower.contains("supporter") || lower.contains("aiuto") ||
            lower.contains("item") || lower.contains("strumento") ||
            lower.contains("stadium") || lower.contains("stadio") ||
            lower.contains("tool") || lower.contains("oggetto")) {
            return CardSupertype.TRAINER
        }

        // Se ha HP, quasi sicuramente e un Pokemon
        if (hp != null && hp > 0) return CardSupertype.POKEMON

        // Se ha stage/evoluzione, e un Pokemon
        if (extractStage(text) != null) return CardSupertype.POKEMON

        // Default
        return CardSupertype.POKEMON
    }

    // ═══════════════════════════════════════════
    // ESTRAZIONE ILLUSTRATORE
    // ═══════════════════════════════════════════

    private val ILLUSTRATOR_PATTERNS = listOf(
        Regex("""[Ii]llus(?:trator)?\.?\s*:?\s*(.+)"""),
        Regex("""[Ii]ll\.?\s*:?\s*(.+)"""),
        Regex("""Illustrated by\s+(.+)""", RegexOption.IGNORE_CASE)
    )

    /** Estrae il nome dell'illustratore */
    fun extractIllustrator(text: String): String? {
        for (pattern in ILLUSTRATOR_PATTERNS) {
            val match = pattern.find(text)
            if (match != null) {
                return match.groupValues[1]
                    .replace(Regex("""\d{1,3}/\d{1,3}.*$"""), "") // Rimuovi numero carta
                    .replace(Regex("""[©®™].*$"""), "") // Rimuovi copyright
                    .trim()
                    .takeIf { it.length >= 2 }
            }
        }
        return null
    }

    // ═══════════════════════════════════════════
    // ZONE CLASSIFICATION
    // ═══════════════════════════════════════════

    /** Classifica i blocchi di testo nelle zone della carta */
    private fun classifyTextZones(blocks: List<OCRTextBlock>): List<DetectedTextZone> {
        return blocks.map { block ->
            val zone = when {
                block.normalizedY < 0.12f -> CardZone.TOP
                block.normalizedY < 0.40f -> CardZone.ARTWORK // Potrebbe essere testo sull'artwork
                block.normalizedY < 0.78f -> CardZone.MIDDLE
                block.normalizedY < 0.88f -> CardZone.BOTTOM_STATS
                block.normalizedY < 0.93f -> CardZone.ILLUSTRATOR
                else -> CardZone.FOOTER
            }
            DetectedTextZone(
                text = block.text,
                zone = zone,
                confidence = block.confidence,
                boundingBox = block.boundingBox
            )
        }
    }

    // ═══════════════════════════════════════════
    // UTILITY
    // ═══════════════════════════════════════════

    /**
     * Stima la confidenza del parsing basata sulla completezza dei campi.
     * Piu campi estratti = piu sicuri del risultato.
     */
    private fun estimateConfidence(name: String?, number: String?, hp: Int?): Float {
        var score = 0f
        if (name != null && name.length >= 3) score += 0.4f
        if (number != null) score += 0.35f
        if (hp != null) score += 0.25f
        return score.coerceIn(0f, 1f)
    }

    /**
     * Confronta due risultati OCR e restituisce quello migliore.
     * Utile quando si eseguono piu passate (full-frame + zone).
     */
    fun mergeResults(fullFrame: CardOCRResult, zoned: CardOCRResult): CardOCRResult {
        return CardOCRResult(
            cardName = zoned.cardName ?: fullFrame.cardName,
            cardNumber = fullFrame.cardNumber ?: zoned.cardNumber, // Numero piu affidabile da full
            setTotal = fullFrame.setTotal ?: zoned.setTotal,
            hp = zoned.hp ?: fullFrame.hp, // HP piu preciso da zona top
            stage = zoned.stage ?: fullFrame.stage,
            illustrator = zoned.illustrator ?: fullFrame.illustrator,
            supertype = if (zoned.hp != null) zoned.supertype else fullFrame.supertype,
            variant = if (zoned.variant != CardVariant.NORMAL) zoned.variant else fullFrame.variant,
            rarity = zoned.rarity ?: fullFrame.rarity,
            rawText = fullFrame.rawText,
            confidence = maxOf(fullFrame.confidence, zoned.confidence),
            detectedZones = zoned.detectedZones.ifEmpty { fullFrame.detectedZones }
        )
    }
}

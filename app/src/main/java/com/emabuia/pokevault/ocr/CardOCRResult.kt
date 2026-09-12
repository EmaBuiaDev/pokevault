package com.emabuia.pokevault.ocr

/**
 * Risultato strutturato dell'OCR su una carta Pokemon.
 * Tutti i campi estratti dalla pipeline di riconoscimento.
 */
data class CardOCRResult(
    val cardName: String? = null,
    val cardNumber: String? = null,
    val setTotal: String? = null,
    val setName: String? = null,
    val setCode: String? = null,
    val rarity: String? = null,
    val hp: Int? = null,
    val supertype: CardSupertype = CardSupertype.POKEMON,
    val variant: CardVariant = CardVariant.NORMAL,
    val stage: String? = null,
    val illustrator: String? = null,
    val rawText: String = "",
    val confidence: Float = 0f,
    val detectedZones: List<DetectedTextZone> = emptyList()
)

/**
 * Blocco di testo riconosciuto, con la sua posizione sulla carta.
 *
 * Le posizioni sono cio' che permette di sapere che il nome sta in alto e l'ID
 * in basso, invece di indovinarlo dall'ordine delle righe.
 */
data class OCRTextBlock(
    val text: String,
    val confidence: Float,
    val boundingBox: ZoneBoundingBox? = null,
    /** Posizione verticale normalizzata [0..1] nell'immagine originale */
    val normalizedY: Float = 0f
) {
    /** Posizione orizzontale del bordo sinistro [0..1]; 0 se il box manca. */
    val normalizedLeft: Float get() = boundingBox?.left ?: 0f
}

/**
 * Un frame della carta gia' passato all'OCR, con le due letture che lo scanner
 * fa su ogni fotogramma:
 *  - [blocks], il testo dell'intera carta con la posizione di ogni blocco;
 *  - [idStripText], il testo della sola striscia in fondo, ritagliata e
 *    ingrandita perche' il numero da collezione e' troppo piccolo per essere
 *    letto insieme al resto.
 *
 * Un frame vuoto non e' un frame inutile: dice che davanti all'obiettivo non
 * c'e' niente di leggibile, e lo scanner lo usa per riarmarsi.
 */
data class ScannedFrame(
    val blocks: List<OCRTextBlock> = emptyList(),
    val idStripText: String = ""
) {
    val fullText: String get() = blocks.joinToString("\n") { it.text }

    fun isEmpty(): Boolean = blocks.isEmpty() && idStripText.isBlank()
}

/** Zone di testo rilevate sull'immagine con bounding box */
data class DetectedTextZone(
    val text: String,
    val zone: CardZone,
    val confidence: Float,
    val boundingBox: ZoneBoundingBox? = null
)

/** Bounding box normalizzato [0..1] */
data class ZoneBoundingBox(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
)

/**
 * Zone logiche di una carta Pokemon.
 * Usate per guidare il parsing: ogni zona ha un significato specifico.
 *
 * Layout carta Pokemon standard:
 * ┌──────────────────────────┐
 * │ [Stage]  NOME     HP xxx │  ← TOP (nome, HP, stage)
 * │                          │
 * │      [ILLUSTRAZIONE]     │  ← ARTWORK (ignorata per OCR)
 * │                          │
 * │  Attacco 1       30      │  ← MIDDLE (attacchi, abilita)
 * │  Attacco 2       80      │
 * │                          │
 * │ Weakness Resistance Retr │  ← BOTTOM_STATS
 * │ Illus. Nome Artista      │  ← ILLUSTRATOR
 * │ SET_SYMBOL  025/198  ●   │  ← FOOTER (numero, set, rarita)
 * └──────────────────────────┘
 */
enum class CardZone {
    TOP,            // Nome carta, HP, stage/evolution
    ARTWORK,        // Illustrazione (non utile per OCR)
    MIDDLE,         // Attacchi, abilita, descrizione
    BOTTOM_STATS,   // Debolezza, resistenza, ritirata
    ILLUSTRATOR,    // Nome illustratore
    FOOTER          // Numero carta, simbolo set, rarita
}

enum class CardSupertype {
    POKEMON, TRAINER, ENERGY;

    companion object {
        fun fromText(text: String): CardSupertype {
            val lower = text.lowercase()
            return when {
                lower.contains("trainer") || lower.contains("allenatore") ||
                lower.contains("supporter") || lower.contains("aiuto") ||
                lower.contains("item") || lower.contains("strumento") ||
                lower.contains("stadium") || lower.contains("stadio") -> TRAINER

                lower.contains("energy") || lower.contains("energia") -> ENERGY

                else -> POKEMON
            }
        }
    }
}

/**
 * Varianti di carte Pokemon riconoscibili dall'OCR.
 * Le varianti speciali hanno suffissi nel nome (EX, GX, V, VSTAR, ecc.)
 * o indicatori visivi (holo pattern, texture).
 */
enum class CardVariant(val displayName: String) {
    NORMAL("Normal"),
    HOLO("Holo"),
    REVERSE_HOLO("Reverse Holo"),
    EX("EX"),
    GX("GX"),
    V("V"),
    VMAX("VMAX"),
    VSTAR("VSTAR"),
    VUNION("V-UNION"),
    TAG_TEAM("Tag Team GX"),
    MEGA("Mega"),
    BREAK("BREAK"),
    PRIME("Prime"),
    LV_X("Lv.X"),
    RADIANT("Radiant"),
    FULL_ART("Full Art"),
    ALT_ART("Alt Art"),
    SECRET_RARE("Secret Rare"),
    GOLD("Gold"),
    SHINY("Shiny"),
    EX_TERA("ex"),  // Scarlet & Violet era lowercase "ex"
    ILLUSTRATION_RARE("Illustration Rare"),
    SPECIAL_ART_RARE("Special Art Rare");

    companion object {
        /**
         * Rileva la variante dal nome della carta e dal testo circostante.
         * Ordine di priorita: suffissi piu specifici prima.
         */
        fun detectFromName(name: String): CardVariant {
            val upper = name.uppercase().trim()
            return when {
                upper.endsWith(" VSTAR") -> VSTAR
                upper.endsWith(" VMAX") -> VMAX
                upper.endsWith(" V-UNION") -> VUNION
                upper.endsWith(" V") -> V
                upper.contains("TAG TEAM") && upper.endsWith(" GX") -> TAG_TEAM
                upper.endsWith(" GX") -> GX
                upper.endsWith(" EX") && upper == upper.uppercase() -> EX // Old era uppercase
                upper.endsWith(" EX") -> EX_TERA  // SV era lowercase
                upper.endsWith(" ex") -> EX_TERA
                upper.startsWith("MEGA ") || upper.startsWith("M ") -> MEGA
                upper.endsWith(" BREAK") -> BREAK
                upper.endsWith(" PRIME") -> PRIME
                upper.contains("LV.X") || upper.contains("LV. X") -> LV_X
                upper.startsWith("RADIANT ") -> RADIANT
                else -> NORMAL
            }
        }

        /** Rileva da testo OCR generico (footer, rarita, ecc.) */
        fun detectFromRarity(rarityText: String): CardVariant {
            val lower = rarityText.lowercase()
            return when {
                lower.contains("special art") || lower.contains("sar") -> SPECIAL_ART_RARE
                lower.contains("illustration rare") || lower.contains("illustrazione rara") -> ILLUSTRATION_RARE
                lower.contains("secret") || lower.contains("segreta") -> SECRET_RARE
                lower.contains("gold") || lower.contains("oro") -> GOLD
                lower.contains("full art") -> FULL_ART
                lower.contains("alt art") || lower.contains("alternativa") -> ALT_ART
                lower.contains("shiny") -> SHINY
                lower.contains("reverse") -> REVERSE_HOLO
                lower.contains("holo") -> HOLO
                else -> NORMAL
            }
        }
    }
}

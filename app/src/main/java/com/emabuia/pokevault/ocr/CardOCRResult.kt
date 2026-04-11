package com.emabuia.pokevault.ocr

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName

/**
 * Risultato strutturato dell'OCR su una carta Pokemon.
 * Tutti i campi estratti dalla pipeline di riconoscimento.
 */
data class CardOCRResult(
    @SerializedName("card_name")
    val cardName: String? = null,

    @SerializedName("card_number")
    val cardNumber: String? = null,

    @SerializedName("set_total")
    val setTotal: String? = null,

    @SerializedName("set_name")
    val setName: String? = null,

    @SerializedName("set_code")
    val setCode: String? = null,

    @SerializedName("rarity")
    val rarity: String? = null,

    @SerializedName("hp")
    val hp: Int? = null,

    @SerializedName("supertype")
    val supertype: CardSupertype = CardSupertype.POKEMON,

    @SerializedName("variant")
    val variant: CardVariant = CardVariant.NORMAL,

    @SerializedName("stage")
    val stage: String? = null,

    @SerializedName("illustrator")
    val illustrator: String? = null,

    @SerializedName("raw_text")
    val rawText: String = "",

    @SerializedName("confidence")
    val confidence: Float = 0f,

    @SerializedName("detected_zones")
    val detectedZones: List<DetectedTextZone> = emptyList()
) {
    fun toJson(): String = Gson().toJson(this)

    /** Indica se il risultato ha abbastanza dati per una ricerca */
    fun isSearchable(): Boolean {
        return (cardName != null && cardName.length >= 3) || cardNumber != null
    }

    /** Genera una search key univoca per il debounce */
    fun searchKey(): String = "${cardName.orEmpty()}_${cardNumber.orEmpty()}"

    companion object {
        fun fromJson(json: String): CardOCRResult = Gson().fromJson(json, CardOCRResult::class.java)
    }
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

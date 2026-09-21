package com.emabuia.pokevault.data.model

import com.emabuia.pokevault.data.local.ItalianTranslations
import com.google.firebase.Timestamp
import com.google.firebase.firestore.PropertyName

data class PokemonCard(
    val id: String = "",
    val name: String = "",
    val imageUrl: String = "",
    val set: String = "",
    val rarity: String = "",
    val type: String = "",
    val hp: Int = 0,
    val supertype: String = "Pokémon", // Pokémon, Trainer, Energy
    val subtypes: List<String> = emptyList(), // Basic, Stage 1, Item, Supporter (Aiuto), etc.
    
    @get:PropertyName("isGraded")
    @set:PropertyName("isGraded")
    var isGraded: Boolean = false,
    
    val grade: Float? = null,
    val gradingCompany: String = "",  // PSA, BGS, CGC
    val estimatedValue: Double = 0.0,
    val quantity: Int = 1,
    val condition: String = "Near Mint",
    val notes: String = "",
    val addedAt: Timestamp? = null,
    val apiCardId: String = "",
    val cardNumber: String = "",
    val variant: String = "Normal",
    val language: String = "Italiano",

    /**
     * La carta esiste solo per stare dentro a un deck, non e' posseduta.
     *
     * Non compare in Collezione, Album, Wishlist, Graded o statistiche, e non
     * entra nei totali dell'utente: serve a costruire un mazzo completo senza
     * dichiarare di avere carte che non si hanno. Il filtro sta in un punto
     * solo, [com.emabuia.pokevault.data.firebase.FirestoreRepository.getCards],
     * cosi' chi legge la collezione non deve saperne niente.
     */
    val deckOnly: Boolean = false
) {
    /** Vedi [CardClassifier]: implementazione unica condivisa da tutta l'app. */
    fun classify(): String = CardClassifier.classify(this)
}

/**
 * La carta, a prescindere dalla stampa.
 *
 * [collectionGroupKey] tiene dentro anche la variante, perche' serve a unire
 * le copie *identiche* quando si salva: la Normale e la Reverse sono due
 * documenti diversi e devono restarlo. In collezione pero' quella distinzione
 * faceva comparire la stessa carta due volte; questa chiave le riunisce in una
 * tessera sola, che poi mostra "x2" e i badge delle stampe possedute.
 *
 * Il formato e' quello di [collectionGroupKey] senza l'ultimo campo, cosi' le
 * due restano confrontabili a colpo d'occhio nei log.
 */
fun PokemonCard.collectionCardKey(): String {
    val normalizedSet = ItalianTranslations.translateExpansionName(set.trim())
        .trim()
        .lowercase()
        .replace(Regex("\\s+"), " ")
    val normalizedNumber = normalizeCollectionCardNumber(cardNumber)

    return if (normalizedSet.isNotBlank() && normalizedNumber.isNotBlank()) {
        "${normalizedSet}|${normalizedNumber}"
    } else {
        apiCardId.ifBlank { "${name.trim().lowercase()}|${normalizedSet}|${normalizedNumber}" }
    }
}

fun PokemonCard.collectionGroupKey(): String {
    val normalizedSet = ItalianTranslations.translateExpansionName(set.trim())
        .trim()
        .lowercase()
        .replace(Regex("\\s+"), " ")
    val normalizedNumber = normalizeCollectionCardNumber(cardNumber)
    val normalizedVariant = variant.trim().lowercase()

    return if (normalizedSet.isNotBlank() && normalizedNumber.isNotBlank()) {
        "${normalizedSet}|${normalizedNumber}|${normalizedVariant}"
    } else {
        apiCardId.ifBlank { "${name.trim().lowercase()}|${normalizedSet}|${normalizedNumber}|${normalizedVariant}" }
    }
}

private fun normalizeCollectionCardNumber(raw: String): String {
    val token = raw.substringBefore('/').trim().uppercase()
    if (token.isBlank()) return ""

    if (token.all { it.isDigit() }) {
        return token.trimStart('0').ifEmpty { "0" }
    }

    val prefixDigits = Regex("^([A-Z]+)0*(\\d+)$").matchEntire(token)
    if (prefixDigits != null) {
        val prefix = prefixDigits.groupValues[1]
        val digits = prefixDigits.groupValues[2].trimStart('0').ifEmpty { "0" }
        return "$prefix$digits"
    }

    val splitDigits = Regex("^0*(\\d+)(.*)$").matchEntire(token)
    if (splitDigits != null) {
        val digits = splitDigits.groupValues[1].trimStart('0').ifEmpty { "0" }
        val suffix = splitDigits.groupValues[2]
        return "$digits$suffix"
    }

    return token
}

data class MenuSection(
    val title: String,
    val icon: String,
    val route: String,
    val badgeCount: Int = 0
)

object CardOptions {
    val CONDITIONS = listOf("Mint", "Near Mint", "Excellent", "Good", "Light Played", "Played", "Poor")
    val GRADING_COMPANIES = listOf("PSA", "BGS", "CGC", "ACE", "SGC")
    val LANGUAGES = listOf(
        "🇮🇹 Italiano",
        "🇬🇧 English",
        "🇯🇵 Giapponese",
        "🇨🇳 Cinese"
    )
    val DEFAULT_VARIANTS = listOf("Normal", "Reverse", "Holo")

    fun languageLabelForMacro(macro: String?): String? {
        return when (macro?.trim()?.uppercase()) {
            "ITA" -> "🇮🇹 Italiano"
            "ENG" -> "🇬🇧 English"
            "JAP", "JPN" -> "🇯🇵 Giapponese"
            "CHN" -> "🇨🇳 Cinese"
            else -> null
        }
    }

    fun languageOptionsForMacro(macro: String?): List<String> {
        return languageLabelForMacro(macro)?.let(::listOf) ?: LANGUAGES
    }

    // Rarita' che escono in una stampa sola: chiedere "Normale o Reverse?" su
    // una Ultra Rara non ha senso, quella stampa non esiste.
    //
    // Confrontate con `contains`, non con l'uguaglianza: il catalogo porta lo
    // stesso concetto scritto in modi diversi ("Holo Rare" e "Rare Holo",
    // "Illustration rare" e "Special illustration rare"), e un `in setOf(...)`
    // ne riconosceva solo la forma esatta -- tutte le altre finivano nel ramo
    // generico e si prendevano tre varianti inventate.
    private val SINGLE_VARIANT_TOKENS = listOf(
        "ace spec", "illustration rare", "rare art", "illustrazione rara",
        "futuristic", "futuristica",
        "shiny", "ultra rare", "ultra rara", "ultrarara", "full art",
        "hyper rare", "iper rara", "gold",
        "secret rare", "rara segreta", "segret",
        "double rare", "doppia rara", "vmax", "vstar", "holo ex", "rare holo v",
        "promo", "mega_attack_rare", "mega hyper rare",
        "legend", "prime", "radiant", "radiosa", "amazing",
        "lv.x", "black white rare", "classic collection", "pikachu rare"
    )

    /**
     * Il reverse holo non e' sempre esistito: debutta con Legendary Collection
     * (24 maggio 2002). Proporlo sulle carte del Set Base o di Neo vuol dire
     * offrire una stampa che non e' mai stata fatta.
     *
     * Senza data si tiene il comportamento di prima e il reverse si propone:
     * meglio una variante di troppo che togliere quella giusta a un set di cui
     * non sappiamo nulla.
     */
    private const val REVERSE_HOLO_FROM = "2002-05-24"

    private fun hasReverseHolo(setReleaseDate: String?): Boolean {
        val date = setReleaseDate?.trim()?.takeIf { it.length >= 10 } ?: return true
        return date >= REVERSE_HOLO_FROM
    }

    fun getVariantsForCard(priceKeys: Set<String>, rarity: String?, setReleaseDate: String? = null): List<String> {
        val apiVariants = priceKeys.map { key ->
            when (key) {
                "normal" -> "Normal"
                "holofoil" -> "Holo"
                "reverseHolofoil" -> "Reverse"
                "1stEditionHolofoil" -> "1st Edition Holo"
                "1stEditionNormal" -> "1st Edition"
                "unlimitedHolofoil" -> "Unlimited Holo"
                else -> key.replaceFirstChar { it.uppercase() }
            }
        }
        if (apiVariants.isNotEmpty()) return apiVariants

        // Fallback sulla rarita': le carte italiane non hanno prezzi TCGplayer,
        // quindi in pratica passano tutte di qui.
        val r = (rarity ?: "").lowercase().trim()
        val reverse = hasReverseHolo(setReleaseDate)
        return when {
            SINGLE_VARIANT_TOKENS.any { r.contains(it) } -> listOf("Holo")

            // Una holografica esce holo, e dove il reverse esiste anche reverse.
            r.contains("rare holo") || r.contains("holo rare") ->
                if (reverse) listOf("Holo", "Reverse") else listOf("Holo")

            // Una rara *non* holo esce normale, e reverse dove esiste: la
            // versione holografica e' un'altra rarita' ("Rare Holo"), non una
            // variante di questa, e proporla qui era la terza scelta inventata.
            r == "rare" || r == "rara" -> if (reverse) listOf("Normal", "Reverse") else listOf("Normal")

            r.contains("uncommon") || r == "non comune" ->
                if (reverse) listOf("Normal", "Reverse") else listOf("Normal")

            (r.contains("common") && !r.contains("uncommon")) || r == "comune" ->
                if (reverse) listOf("Normal", "Reverse") else listOf("Normal")

            // Rarita' che non conosciamo: una stampa sola, senza inventarne.
            else -> listOf("Holo")
        }
    }

    fun getVariantsFromApi(priceKeys: Set<String>): List<String> {
        return priceKeys.map { key ->
            when (key) {
                "normal" -> "Normal"
                "holofoil" -> "Holo"
                "reverseHolofoil" -> "Reverse"
                "1stEditionHolofoil" -> "1st Edition Holo"
                "1stEditionNormal" -> "1st Edition"
                "unlimitedHolofoil" -> "Unlimited Holo"
                else -> key.replaceFirstChar { it.uppercase() }
            }
        }.ifEmpty { DEFAULT_VARIANTS }
    }

    fun getVariantApiKey(variant: String): String {
        return when (variant) {
            "Normal" -> "normal"
            "Holo" -> "holofoil"
            "Reverse" -> "reverseHolofoil"
            "1st Edition Holo" -> "1stEditionHolofoil"
            "1st Edition" -> "1stEditionNormal"
            "Unlimited Holo" -> "unlimitedHolofoil"
            else -> "normal"
        }
    }
}

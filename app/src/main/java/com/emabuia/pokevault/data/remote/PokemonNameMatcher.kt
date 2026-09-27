package com.emabuia.pokevault.data.remote

import java.text.Normalizer
import java.util.Locale

/**
 * Dice se un nome di Pokemon in inglese e uno in italiano sono la stessa carta.
 *
 * Le specie si chiamano uguale nelle due lingue, ma tutto quello che ci sta
 * intorno no: "Teal Mask Ogerpon ex" e' "Ogerpon Maschera Turchese-ex",
 * "Lillie's Clefairy ex" e' "Clefairy-ex di Lylia". Pretendere il nome intero
 * scartava proprio queste carte, che dall'import uscivano come segnaposto
 * senza immagine. Basta che una parola della specie compaia in entrambi.
 */
object PokemonNameMatcher {

    /**
     * Parole che non dicono quale Pokemon e': meccaniche, articoli, possessivi.
     * Con queste in comune due specie diverse sembrerebbero la stessa
     * ("Kadabra ex" e "Treecko ex").
     */
    private val noise = setOf(
        "ex", "gx", "v", "vmax", "vstar", "vunion", "lv", "x", "mega", "tera",
        "the", "of", "s", "di", "del", "della", "dei", "degli", "delle"
    )

    /**
     * Le sole specie che l'italiano traduce: i Paradosso. Per tutte le altre
     * il nome della specie e' lo stesso nelle due lingue. Ogni nome italiano
     * e' verificato sul catalogo, non ricavato: "Iron Hands" e' "Manoferrea",
     * non "Manoferro" come verrebbe spontaneo.
     */
    internal val translatedSpecies: Map<String, String> = mapOf(
        "great tusk" to "grandizanne",
        "scream tail" to "codaurlante",
        "brute bonnet" to "fungofurioso",
        "flutter mane" to "crinealato",
        "slither wing" to "alirasenti",
        "sandy shocks" to "peldisabbia",
        "roaring moon" to "lunaruggente",
        "walking wake" to "acquecrespe",
        "gouging fire" to "vampeaguzze",
        "raging bolt" to "furiatonante",
        "iron treads" to "solcoferreo",
        "iron bundle" to "saccoferreo",
        "iron hands" to "manoferrea",
        "iron jugulis" to "colloferreo",
        "iron moth" to "falenaferrea",
        "iron thorns" to "spineferree",
        "iron valiant" to "eroeferreo",
        "iron leaves" to "fogliaferrea",
        "iron boulder" to "massoferreo",
        "iron crown" to "capoferreo"
    )

    fun sameSpecies(englishName: String?, italianName: String?): Boolean {
        val spaced = normalized(englishName).replace(Regex("[^a-z0-9]+"), " ").trim()
        val translated = translatedSpecies.entries
            .filter { (en, _) -> " $spaced ".contains(" $en ") }
            .map { (_, it) -> it }
        // In inglese la specie e' l'ultima parola: "Team Rocket's Zubat",
        // "Teal Mask Ogerpon", "Hop's Wooloo". Le altre dicono di chi e' o che
        // forma ha, e contarle farebbe combaciare "Hop's Wooloo" con "Dubwool
        // di Hop" solo perche' sono tutti e due di Hop.
        val species = listOfNotNull(speciesTokens(englishName).lastOrNull()) + translated
        if (species.isEmpty()) return false
        return speciesTokens(italianName).any { it in species }
    }

    private fun normalized(raw: String?): String =
        Normalizer.normalize(raw.orEmpty().lowercase(Locale.ROOT), Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")

    /** In ordine, perche' la posizione conta: vedi [sameSpecies]. */
    private fun speciesTokens(raw: String?): List<String> =
        normalized(raw)
            .split(Regex("[^a-z0-9]+"))
            .filter { it.length >= 3 && it !in noise }
}

package com.emabuia.pokevault.data.model

/**
 * Le chiavi delle stampe di una carta, come le scrive il worker (D1
 * card_variants, scripts/build-card-variants.mjs) e come si salvano in
 * Firestore nel campo `variant`.
 *
 * Le stampe base sono le stringhe di sempre -- "Normal", "Reverse", "Holo",
 * "1st Edition", "1st Edition Holo" -- cosi' le carte salvate prima restano
 * valide. Le speciali aggiungono lavorazione e timbri dopo una barra, in un
 * ordine fisso: "Reverse|foil=pokeball", "Normal|stamp=pokemon-day",
 * "Reverse|foil=league|stamp=30th-pokeday".
 */
object VariantKeys {

    data class Parsed(val base: String, val foil: String?, val stamps: List<String>)

    val BASE_ORDER = listOf("Normal", "Reverse", "Holo", "1st Edition", "1st Edition Holo", "Unlimited Holo")

    fun isSpecial(key: String): Boolean = '|' in key

    fun parse(key: String): Parsed {
        val parts = key.split('|')
        var foil: String? = null
        var stamps: List<String> = emptyList()
        parts.drop(1).forEach { part ->
            when {
                part.startsWith("foil=") -> foil = part.removePrefix("foil=").takeIf { it.isNotBlank() }
                part.startsWith("stamp=") -> stamps = part.removePrefix("stamp=").split('+').filter { it.isNotBlank() }
            }
        }
        return Parsed(base = parts.first(), foil = foil, stamps = stamps)
    }

    /** La stampa base di una chiave: "Reverse|foil=pokeball" -> "Reverse". */
    fun baseOf(key: String): String = key.substringBefore('|')

    /**
     * L'ordine in cui si mostrano: prima le base nell'ordine di sempre, poi le
     * speciali raggruppate per base e in ordine alfabetico.
     */
    fun sorted(keys: Collection<String>): List<String> =
        keys.distinct().sortedWith(
            compareBy<String>(
                { if (isSpecial(it)) 1 else 0 },
                { BASE_ORDER.indexOf(baseOf(it)).let { i -> if (i < 0) BASE_ORDER.size else i } },
                { it }
            )
        )

    fun baseLabel(base: String, italian: Boolean): String = when (base) {
        "Normal" -> if (italian) "Normale" else "Normal"
        "Reverse" -> "Reverse"
        "Holo" -> "Holo"
        "1st Edition" -> if (italian) "1ª Edizione" else "1st Edition"
        "1st Edition Holo" -> if (italian) "1ª Ed. Holo" else "1st Ed. Holo"
        "Unlimited Holo" -> "Unlimited Holo"
        else -> base
    }

    /**
     * Il nome per esteso: "Reverse Poké Ball", "Normale · Pokémon Day",
     * "Reverse Lega · Pokémon Day 30°".
     */
    fun label(key: String, italian: Boolean): String {
        if (!isSpecial(key)) return baseLabel(key, italian)
        val parsed = parse(key)
        val head = buildString {
            append(baseLabel(parsed.base, italian))
            parsed.foil?.let { append(' ').append(foilLabel(it, italian)) }
        }
        if (parsed.stamps.isEmpty()) return head
        return head + " · " + parsed.stamps.joinToString(", ") { stampLabel(it, italian) }
    }

    fun foilLabel(foil: String, italian: Boolean): String = when (foil) {
        "cosmos" -> if (italian) "Cosmo" else "Cosmos"
        "galaxy" -> if (italian) "Galassia" else "Galaxy"
        "pokeball" -> "Poké Ball"
        "masterball" -> "Master Ball"
        "gold" -> if (italian) "Oro" else "Gold"
        "rainbow" -> if (italian) "Arcobaleno" else "Rainbow"
        "cracked-ice" -> if (italian) "Ghiaccio" else "Cracked Ice"
        "league" -> if (italian) "Lega" else "League"
        "tinsel" -> "Tinsel"
        "starlight" -> "Starlight"
        "professor-program" -> if (italian) "Programma Professori" else "Professor Program"
        else -> prettify(foil)
    }

    fun stampLabel(stamp: String, italian: Boolean): String = when (stamp) {
        "set-logo" -> if (italian) "Logo del set" else "Set logo"
        "player-rewards-program" -> "Play! Pokémon"
        "staff" -> "Staff"
        "professor-program" -> if (italian) "Programma Professori" else "Professor Program"
        "pokemon-center" -> "Pokémon Center"
        "snowflake" -> if (italian) "Fiocco di neve" else "Snowflake"
        "countdown-calendar" -> if (italian) "Calendario dell'Avvento" else "Countdown Calendar"
        "pre-release" -> "Prerelease"
        "25th-celebration" -> if (italian) "25° anniversario" else "25th Celebration"
        "30th-anniversary" -> if (italian) "30° anniversario" else "30th Anniversary"
        "30th-pokeday" -> if (italian) "Pokémon Day 30°" else "Pokémon Day 30th"
        "pokemon-day" -> "Pokémon Day"
        "regional-championships" -> if (italian) "Campionati regionali" else "Regional Championships"
        "state-championships" -> if (italian) "Campionati statali" else "State Championships"
        "national-championships" -> if (italian) "Campionati nazionali" else "National Championships"
        "city-championships" -> "City Championships"
        "winner" -> if (italian) "Vincitore" else "Winner"
        "top-thirty-two" -> "Top 32"
        "top-sixteen" -> "Top 16"
        "top-eight" -> "Top 8"
        "semi-finalist" -> if (italian) "Semifinalista" else "Semi-finalist"
        "finalist" -> if (italian) "Finalista" else "Finalist"
        "wotc" -> "WotC"
        "pikachu" -> "Pikachu"
        "distributor-meeting" -> "Distributor Meeting"
        "gamestop" -> "GameStop"
        "eb-games" -> "EB Games"
        "pokemon-together" -> "Pokémon Together"
        "origins-2008" -> "Origins 2008"
        "comic-con" -> "Comic-Con"
        "platinum" -> if (italian) "Platino" else "Platinum"
        "horizons" -> "Horizons"
        "d-edition-error" -> if (italian) "Errore \"D Edition\"" else "\"D Edition\" error"
        "pikachu-tail" -> if (italian) "Errore coda di Pikachu" else "Pikachu tail error"
        "1st-edition-scratch-error" -> if (italian) "Errore 1ª Ed. graffiata" else "1st Ed. scratch error"
        "ultra-ball-league" -> if (italian) "Lega Ultra Ball" else "Ultra Ball League"
        "trick-or-trade" -> "Trick or Trade"
        "asia-2023-24" -> "Asia 2023-24"
        "poketour-99" -> "PokéTour '99"
        "w-promo" -> if (italian) "Promo W" else "W Promo"
        else -> Regex("^worlds-(\\d{4})$").matchEntire(stamp)?.let { "Worlds ${it.groupValues[1]}" } ?: prettify(stamp)
    }

    /** "cracked-ice" -> "Cracked Ice": per un timbro nuovo che non conosciamo ancora. */
    private fun prettify(raw: String): String =
        raw.split('-').filter { it.isNotBlank() }.joinToString(" ") { part -> part.replaceFirstChar { it.uppercase() } }
}

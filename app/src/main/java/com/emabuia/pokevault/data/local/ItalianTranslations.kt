package com.emabuia.pokevault.data.local

/**
 * Mappe statiche per la traduzione EN → IT di serie ed espansioni Pokémon TCG.
 * Fallback: se la chiave non è presente, restituisce il nome originale inglese.
 */
object ItalianTranslations {

    // ══════════════════════════════════════
    //  SERIE (raggruppamenti)
    // ══════════════════════════════════════

    private val SERIES_IT = mapOf(
        "Scarlet & Violet" to "Scarlatto e Violetto",
        "Sword & Shield" to "Spada e Scudo",
        "Sun & Moon" to "Sole e Luna",
        "XY" to "XY",
        "Black & White" to "Nero e Bianco",
        "HeartGold & SoulSilver" to "HeartGold & SoulSilver",
        "Diamond & Pearl" to "Diamante e Perla",
        "EX" to "EX",
        "Mega Evolutions" to "Mega Evoluzioni",
        "World Championships" to "Campionati Mondiali",
        "Promos" to "Promo",
        "e-Card" to "e-Card",
        "Neo" to "Neo",
        "Gym" to "Gym",
        "Base" to "Base",
        "Other" to "Altro"
    )

    // ══════════════════════════════════════
    //  ESPANSIONI (nomi singoli set)
    // ══════════════════════════════════════

    private val EXPANSIONS_IT = mapOf(
        // ── Scarlet & Violet ──
        "Prismatic Evolutions" to "Evoluzioni Prismatiche",
        "Surging Sparks" to "Scintille Folgoranti",
        "Stellar Crown" to "Corona Astrale",
        "Shrouded Fable" to "Favola Velata",
        "Twilight Masquerade" to "Mascherata Crepuscolare",
        "Temporal Forces" to "Forze Temporali",
        "Paldean Fates" to "Destino di Paldea",
        "Paradox Rift" to "Fenditura Paradosso",
        "151" to "151",
        "Obsidian Flames" to "Fiamme Ossidiana",
        "Paldea Evolved" to "Evoluzione a Paldea",
        "Scarlet & Violet" to "Scarlatto e Violetto",
        "Scarlet & Violet—Energies" to "Scarlatto e Violetto—Energie",
        "Journey Together" to "Avventura Insieme",

        // ── Sword & Shield ──
        "Crown Zenith" to "Zenit Regale",
        "Silver Tempest" to "Tempesta Argentata",
        "Lost Origin" to "Origine Perduta",
        "Pokémon GO" to "Pokémon GO",
        "Astral Radiance" to "Lucentezza Siderale",
        "Brilliant Stars" to "Astri Lucenti",
        "Fusion Strike" to "Colpo Fusione",
        "Evolving Skies" to "Cieli in Evoluzione",
        "Chilling Reign" to "Regno Glaciale",
        "Battle Styles" to "Stili di Lotta",
        "Shining Fates" to "Destino Splendente",
        "Vivid Voltage" to "Voltaggio Vivido",
        "Champion's Path" to "Cammino del Campione",
        "Darkness Ablaze" to "Fiamme Oscure",
        "Rebel Clash" to "Fragore Ribelle",
        "Sword & Shield" to "Spada e Scudo",

        // ── Sun & Moon ──
        "Cosmic Eclipse" to "Eclissi Cosmica",
        "Hidden Fates" to "Destino Nascosto",
        "Unified Minds" to "Sintonia Mentale",
        "Unbroken Bonds" to "Legami Inossidabili",
        "Detective Pikachu" to "Detective Pikachu",
        "Team Up" to "Gioco di Squadra",
        "Lost Thunder" to "Tuono Perduto",
        "Dragon Majesty" to "Trionfo del Drago",
        "Celestial Storm" to "Tempesta Celestiale",
        "Forbidden Light" to "Luce Proibita",
        "Ultra Prism" to "Ultraprisma",
        "Crimson Invasion" to "Invasione Scarlatta",
        "Shining Legends" to "Leggende Iridescenti",
        "Burning Shadows" to "Ombre Infuocate",
        "Guardians Rising" to "Guardiani Nascenti",
        "Sun & Moon" to "Sole e Luna",

        // ── XY ──
        "Evolutions" to "Evoluzioni",
        "Steam Siege" to "Vapori Assedianti",
        "Fates Collide" to "Destini Incrociati",
        "Generations" to "Generazioni",
        "BREAKpoint" to "Turbocrash",
        "BREAKthrough" to "Turbofiamma",
        "Ancient Origins" to "Origini Antiche",
        "Roaring Skies" to "Turbine Ruggente",
        "Double Crisis" to "Doppia Crisi",
        "Primal Clash" to "Scontro Primordiale",
        "Phantom Forces" to "Forze Spettrali",
        "Furious Fists" to "Pugni Furiosi",
        "Flashfire" to "Fuoco Infernale",
        "XY" to "XY",

        // ── Black & White ──
        "Legendary Treasures" to "Tesori Leggendari",
        "Plasma Blast" to "Esplosione Plasma",
        "Plasma Freeze" to "Glaciazione Plasma",
        "Plasma Storm" to "Tempesta Plasma",
        "Boundaries Crossed" to "Confini Varcati",
        "Dragons Exalted" to "Draghi Trascendenti",
        "Dark Explorers" to "Esploratori delle Tenebre",
        "Next Destinies" to "Destini Futuri",
        "Noble Victories" to "Vittorie Nobili",
        "Emerging Powers" to "Poteri Emergenti",
        "Black & White" to "Nero e Bianco",

        // ── Diamond & Pearl ──
        "Arceus" to "Arceus",
        "Supreme Victors" to "Rivali Supremi",
        "Rising Rivals" to "Rivali Nascenti",
        "Platinum" to "Platino",
        "Stormfront" to "Fronte di Tempesta",
        "Legends Awakened" to "Risvegli Leggendari",
        "Majestic Dawn" to "Alba Maestosa",
        "Great Encounters" to "Grandi Incontri",
        "Secret Wonders" to "Meraviglie Segrete",
        "Mysterious Treasures" to "Tesori Misteriosi",
        "Diamond & Pearl" to "Diamante e Perla"
    )

    fun translateSeriesName(english: String): String =
        SERIES_IT[english] ?: english

    fun translateExpansionName(english: String): String =
        EXPANSIONS_IT[english] ?: english
}

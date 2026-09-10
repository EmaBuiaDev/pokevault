package com.emabuia.pokevault.data.model

/**
 * Classificazione canonica di una carta in "Pokémon" / "Trainer" / "Energy".
 *
 * Prima di questo file la stessa logica esisteva in quattro copie divergenti
 * (PokemonCard.classify, DeckLabViewModel.classifyCard e due
 * classifyForDeckSections dentro DeckLabScreen), con risultati diversi per la
 * stessa carta a seconda del punto di chiamata.
 *
 * Due vincoli guidano l'ordine di valutazione:
 *
 * 1. Il nome va letto per ULTIMO. Le versioni precedenti mettevano il
 *    controllo sul nome nella stessa condizione del supertype, e
 *    classificavano come Energy i Trainer il cui nome contiene "energy" --
 *    Energy Retrieval, Energy Switch, Energy Search, Superior Energy
 *    Retrieval. Sono Item, e contarli come energie falsava le probabilita'
 *    del simulatore di mano (energyByT1Rate, setupByT2Rate).
 *
 * 2. `supertype` = "Pokémon" NON e' autorevole: e' il valore di default di
 *    PokemonCard, quindi moltissimi record storici lo riportano anche per i
 *    Trainer. "Energy" e "Trainer" invece non sono mai default, quindi li'
 *    ci si puo' fidare. Un supertype "pok" vale solo dopo aver escluso ogni
 *    marcatore di Trainer o Energy -- e' la stessa cautela che aveva
 *    DeckLabViewModel.classifyCard e che PokemonCard.classify non aveva.
 */
object CardClassifier {

    const val POKEMON = "Pokémon"
    const val TRAINER = "Trainer"
    const val ENERGY = "Energy"

    private val TRAINER_SUBTYPES = setOf(
        "item", "stadium", "supporter", "tool",
        "strumento", "stadio", "aiuto", "oggetto"
    )

    private val POKEMON_SUBTYPES = setOf(
        "basic", "stage 1", "stage 2", "baby", "ex", "v", "vmax", "vstar",
        "base", "stadio 1", "stadio 2"
    )

    // "colorless" e' volutamente escluso: la logica precedente lo trattava
    // come segnale non affidabile e non c'e' motivo di cambiarlo qui. I
    // Pokemon incolori hanno comunque hp > 0 e vengono presi dal controllo HP.
    private val POKEMON_TYPES = setOf(
        "grass", "fire", "water", "lightning", "electric", "fighting",
        "psychic", "darkness", "metal", "dragon", "fairy",
        "erba", "fuoco", "acqua", "lampo", "lotta", "psico", "oscurita",
        "metallo", "drago", "folletto"
    )

    fun classify(
        supertype: String,
        type: String,
        name: String,
        subtypes: List<String>,
        hp: Int
    ): String {
        val s = supertype.lowercase().trim()
        val t = type.lowercase().trim()
        val sub = subtypes.map { it.lowercase().trim() }

        // 1. Supertype Energy/Trainer: mai valori di default, quindi affidabili.
        if (s.contains("energ")) return ENERGY
        if (s.contains("trainer") || s.contains("allenat") || s.contains("aiuto")) return TRAINER

        // 2. Subtype esplicito.
        if (sub.any { it.contains("energ") }) return ENERGY
        if (sub.any { it in TRAINER_SUBTYPES }) return TRAINER
        if (sub.any { it in POKEMON_SUBTYPES }) return POKEMON

        // 3. Marcatori sul campo type.
        if (t.contains("energ")) return ENERGY
        if (
            t.contains("trainer") || t.contains("supporter") || t.contains("item") ||
            t.contains("stadium") || t.contains("tool") || t.contains("allenat") ||
            t.contains("aiuto") || t.contains("stadio") || t.contains("strumento")
        ) {
            return TRAINER
        }
        if (t in POKEMON_TYPES) return POKEMON

        // 4. Gli HP sono un segnale forte: solo i Pokemon ne hanno.
        if (hp > 0) return POKEMON

        // 5. Supertype "pok", ormai senza alcun marcatore contrario.
        if (s.contains("pok")) return POKEMON

        // 6. Ultima risorsa, per le carte inserite a mano senza alcun campo
        //    valorizzato. Volutamente in fondo: e' l'euristica che causava il bug.
        val n = name.lowercase().trim()
        if (n.contains("energia") || n.contains("energy")) return ENERGY

        return TRAINER
    }

    fun classify(card: PokemonCard): String = classify(
        supertype = card.supertype,
        type = card.type,
        name = card.name,
        subtypes = card.subtypes,
        hp = card.hp
    )
}

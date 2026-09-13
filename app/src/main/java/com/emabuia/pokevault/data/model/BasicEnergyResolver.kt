package com.emabuia.pokevault.data.model

import java.text.Normalizer

/**
 * Riconosce le energie base di una decklist e le riconduce al nome italiano.
 *
 * Le energie base sono l'unica carta di un mazzo che non ha una stampa sua:
 * una decklist PTCGL le esporta come `4 Basic Psychic Energy SVE 5`, dove SVE
 * e' il set delle energie — un set che il catalogo italiano non ha, perche' le
 * energie base italiane escono dentro le espansioni normali.
 *
 * Il risultato era che ogni energia importata falliva il match per set+numero,
 * cadeva nel ripiego a dati minimi e finiva nella collezione come una carta
 * senza immagine. E succedeva a *tutte* le energie di *ogni* import.
 *
 * Qui si risolve per tipo invece che per stampa, che e' anche il modo in cui
 * funzionano davvero: un'Energia Psico vale l'altra.
 *
 * Il riconoscimento e' per **forma esatta** e non per parole contenute. Il
 * catalogo italiano e' pieno di energie speciali che si chiamano come una base
 * piu' un aggettivo — "Energia Drago doppia", "Energia Darkness Nascosta",
 * "Energia Infuocata" — e cercare la parola del tipo dentro il nome le
 * scambierebbe per base: si finirebbe per mettere nel mazzo una carta diversa
 * da quella chiesta, che e' peggio di non trovarla.
 */
object BasicEnergyResolver {

    /**
     * Le sole energie base che esistono.
     *
     * Non c'e' il Drago: "Energia Drago" come carta base non e' mai esistita,
     * nel catalogo c'e' solo "Energia Drago doppia", che e' speciale. Non c'e'
     * nemmeno l'Incolore, per la stessa ragione.
     */
    private val basicEnergies: List<BasicEnergy> = listOf(
        BasicEnergy("Energia Erba", listOf("energia erba", "grass energy", "g energy")),
        BasicEnergy("Energia Fuoco", listOf("energia fuoco", "fire energy", "r energy")),
        BasicEnergy("Energia Acqua", listOf("energia acqua", "water energy", "w energy")),
        BasicEnergy(
            "Energia Lampo",
            listOf("energia lampo", "lightning energy", "electric energy", "l energy")
        ),
        BasicEnergy("Energia Psico", listOf("energia psico", "psychic energy", "p energy")),
        BasicEnergy("Energia Lotta", listOf("energia lotta", "fighting energy", "f energy")),
        BasicEnergy(
            "Energia Oscurità",
            listOf("energia oscurita", "darkness energy", "dark energy", "d energy")
        ),
        BasicEnergy(
            "Energia Metallo",
            listOf("energia metallo", "metal energy", "steel energy", "m energy")
        ),
        BasicEnergy("Energia Folletto", listOf("energia folletto", "fairy energy", "y energy"))
    )

    private data class BasicEnergy(
        /** Come si chiama la carta nel catalogo italiano, accento compreso. */
        val italianName: String,
        /** Le forme esatte, gia' normalizzate, con cui puo' comparire. */
        val forms: List<String>
    )

    private val byForm: Map<String, String> = buildMap {
        basicEnergies.forEach { energy ->
            energy.forms.forEach { form -> put(form, energy.italianName) }
        }
    }

    /**
     * Il nome italiano dell'energia base corrispondente, o null.
     *
     * Null significa "non e' un'energia base": le energie speciali sono carte
     * vere, con una stampa e un numero, e devono continuare a passare dal
     * normale match per set e numero.
     */
    fun italianEnergyName(cardName: String): String? {
        val form = canonicalForm(cardName) ?: return null
        return byForm[form]
    }

    fun isBasicEnergy(cardName: String): Boolean = italianEnergyName(cardName) != null

    /**
     * Vero se le due carte sono la stessa energia base.
     *
     * Serve a riusare l'energia che l'utente possiede gia' invece di
     * aggiungerne una copia nuova a ogni import: con dieci import si
     * ritrovava dieci "Energia Psico" diverse in collezione.
     */
    fun isSameBasicEnergy(first: String, second: String): Boolean {
        val a = italianEnergyName(first) ?: return false
        val b = italianEnergyName(second) ?: return false
        return a == b
    }

    /**
     * Il nome ridotto alla forma con cui si confronta.
     *
     * "Basic" e "Base" spariscono perche' sono il modo in cui PTCGL marca le
     * energie base e non fanno parte del nome della carta.
     */
    private fun canonicalForm(raw: String): String? {
        val words = normalize(raw)
            .split(" ")
            .filter { it.isNotBlank() && it != "basic" && it != "base" }

        return words.joinToString(" ").takeIf { it.isNotBlank() }
    }

    private fun normalize(raw: String): String {
        val decomposed = Normalizer.normalize(raw.trim().lowercase(), Normalizer.Form.NFD)
        return decomposed
            .replace(Regex("\\p{Mn}+"), "")
            .replace(Regex("[^a-z0-9]+"), " ")
            .trim()
    }
}

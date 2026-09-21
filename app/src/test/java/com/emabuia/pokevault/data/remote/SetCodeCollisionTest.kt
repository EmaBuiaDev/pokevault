package com.emabuia.pokevault.data.remote

import org.junit.Assert.*
import org.junit.Test

/**
 * Due set diversi non possono normalizzare allo stesso codice.
 *
 * Quando succede, la ricerca di una carta per set+numero trova due record che
 * rispondono tutti e due e si tiene il primo del catalogo: importando
 * "Kadabra MEG 55" usciva la 55 di MEP, cioe' un altro Pokemon, senza nessun
 * avviso. E' un errore che non si vede nel codice e non si vede nei log: si
 * vede solo guardando il mazzo importato e non riconoscendo le carte.
 */
class SetCodeCollisionTest {

    private fun code(raw: String) = SetCodeMapper.normalizeDecklistSetCode(raw)

    @Test
    fun testMegENonMep() {
        assertNotEquals(
            "MEG (Mega Evolution) e MEP (le sue promo) sono espansioni diverse",
            code("MEG")?.lowercase(),
            code("MEP")?.lowercase()
        )
    }

    /**
     * "30 Anniversario" e "30 Anniversario Collezione Classica" sono usciti lo
     * stesso giorno e il codice del secondo comincia col primo, quindi basta
     * un taglio al trattino di troppo per far uscire dall'import la carta
     * dell'altro set: la 1 del 30° e' Exeggcute, la 1 della Classica e'
     * Charizard.
     */
    @Test
    fun testTrentesimoENonLaSuaCollezioneClassica() {
        assertNotEquals(
            "il 30° e la sua Collezione Classica sono due espansioni diverse",
            code("30TH")?.lowercase(),
            code("30TH-C")?.lowercase()
        )
    }

    @Test
    fun testBlackBoltENonWhiteFlare() {
        assertNotEquals(
            "Black Bolt e White Flare sono due set diversi usciti insieme",
            code("BLK")?.lowercase(),
            code("WHT")?.lowercase()
        )
    }

    /**
     * Gli alias legittimi devono continuare a combaciare: sono lo stesso set
     * chiamato in due modi, non due set diversi.
     */
    @Test
    fun testGliAliasVeriRestanoTali() {
        assertEquals(code("SVI"), code("SV01"))
        assertEquals(code("TWM"), code("SV06"))
        assertEquals(code("CRI"), code("ME04"))
        assertEquals(code("POR"), code("ME03"))
        assertEquals(code("ASC"), code("ME2PT5"))
    }

    /**
     * Nessun codice conosciuto deve condividere la forma normalizzata con un
     * altro che non sia un suo alias dichiarato. Tiene il passo con i set
     * nuovi: chi ne aggiunge uno se ne accorge qui, non dopo un import
     * sbagliato.
     */
    @Test
    fun testNessunaCollisioneFraSetDiversi() {
        // Gruppi di codici che indicano davvero lo stesso set.
        val aliasVeri = listOf(
            setOf("SVI", "SV01"), setOf("PAL", "SV02"), setOf("OBF", "SV03"),
            setOf("PAR", "SV04"), setOf("TEF", "SV05"), setOf("TWM", "SV06"),
            setOf("SCR", "SV07"), setOf("SSP", "SV08"), setOf("JTG", "SV09"),
            setOf("DRI", "SV10"), setOf("CRI", "ME04"), setOf("POR", "ME03"),
            setOf("ASC", "ME2PT5"),
            // Il codice inglese e l'id italiano dello stesso set.
            setOf("MEG", "ME01"), setOf("PFL", "ME02")
        )

        val codici = listOf(
            "SVI", "SV01", "PAL", "SV02", "OBF", "SV03", "PAR", "SV04",
            "TEF", "SV05", "TWM", "SV06", "SCR", "SV07", "SSP", "SV08",
            "JTG", "SV09", "DRI", "SV10", "CRI", "ME04", "POR", "ME03",
            "ASC", "ME2PT5", "MEG", "MEP", "ME01", "ME02", "PFL",
            "BLK", "WHT", "PRE", "SFA", "MEW", "PAF",
            // Il 30° e la sua Collezione Classica: il secondo codice comincia
            // col primo, che e' la forma in cui la collisione si presenta.
            "30TH", "30TH-C"
        )

        for (a in codici) {
            for (b in codici) {
                if (a == b) continue
                if (aliasVeri.any { a in it && b in it }) continue
                assertNotEquals(
                    "$a e $b sono set diversi ma normalizzano allo stesso codice",
                    code(a)?.lowercase(),
                    code(b)?.lowercase()
                )
            }
        }
    }

    /**
     * Non basta che BLK e WHT siano diversi: devono essere quelli giusti.
     * Black Bolt e' "Luce Nera" (rsv10pt5), White Flare e' "Fuoco Bianco"
     * (zsv10pt5), e la lettera iniziale dell'id non segue la lettera del
     * codice. Scambiandoli i due restano distinti, quindi ogni verifica di
     * sola collisione passa lo stesso mentre ogni carta importata esce dal
     * set sbagliato: e' l'unico modo per accorgersene da qui.
     */
    @Test
    fun testBlackBoltEWhiteFlareVannoSuiSetGiusti() {
        assertEquals("rsv10pt5", code("BLK")?.lowercase())
        assertEquals("zsv10pt5", code("WHT")?.lowercase())
    }

    /**
     * Il codice e il nome per esteso dello stesso set devono dare lo stesso
     * id. normalizeDecklistSetCode prova prima l'alias e poi il nome, e una
     * decklist puo' contenere l'uno o l'altro: sistemata una meta' sola, il
     * bug resta in piedi la meta' delle volte. E' quello che era successo a
     * BLK/WHT, corretti fra gli alias e lasciati su "sv11" fra i nomi.
     */
    @Test
    fun testCodiceENomeDelloStessoSetCoincidono() {
        val stessoSet = listOf(
            "BLK" to "Black Bolt",
            "WHT" to "White Flare",
            "SVI" to "Scarlet Violet",
            "TWM" to "Twilight Masquerade",
            "ASC" to "Ascended Heroes",
            "CRI" to "Chaos Rising",
            "CRZ" to "Crown Zenith"
        )

        for ((codice, nome) in stessoSet) {
            assertEquals(
                "$codice e \"$nome\" sono lo stesso set",
                code(codice)?.lowercase(),
                code(nome)?.lowercase()
            )
        }
    }

    /**
     * La stessa regola di testNessunaCollisioneFraSetDiversi, applicata ai
     * nomi per esteso: erano il ramo dove la collisione BLK/WHT era
     * sopravvissuta, con "black bolt" e "white flare" tutti e due su un
     * "sv11" che in catalogo non esiste nemmeno.
     */
    @Test
    fun testNessunaCollisioneFraNomiDiSetDiversi() {
        val nomi = listOf(
            "Black Bolt", "White Flare", "Scarlet Violet", "Paldea Evolved",
            "Obsidian Flames", "Pokemon 151", "Paradox Rift", "Paldean Fates",
            "Temporal Forces", "Twilight Masquerade", "Shrouded Fable",
            "Stellar Crown", "Surging Sparks", "Prismatic Evolutions",
            "Journey Together", "Destined Rivals", "Ascended Heroes",
            "Chaos Rising", "Rebel Clash", "Darkness Ablaze", "Champions Path",
            "Vivid Voltage", "Battle Styles", "Chilling Reign",
            "Evolving Skies", "Fusion Strike", "Brilliant Stars",
            "Astral Radiance", "Lost Origin", "Silver Tempest", "Crown Zenith"
        )

        for (a in nomi) {
            for (b in nomi) {
                if (a == b) continue
                assertNotEquals(
                    "\"$a\" e \"$b\" sono set diversi ma normalizzano allo stesso codice",
                    code(a)?.lowercase(),
                    code(b)?.lowercase()
                )
            }
        }
    }
}

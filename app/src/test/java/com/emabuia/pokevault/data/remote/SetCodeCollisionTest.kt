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
            "BLK", "WHT", "PRE", "SFA", "MEW", "PAF"
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
}

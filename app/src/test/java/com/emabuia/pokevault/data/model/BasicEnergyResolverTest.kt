package com.emabuia.pokevault.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Il rischio grosso qui non e' mancare un'energia base: e' scambiare per base
 * un'energia speciale.
 *
 * Un'energia base viene risolta per tipo, ignorando set e numero, perche' una
 * vale l'altra. Un'Energia Doppia Turbo invece e' una carta con la sua stampa:
 * risolverla per tipo significherebbe mettere nel mazzo la carta sbagliata.
 */
class BasicEnergyResolverTest {

    @Test
    fun `le energie base inglesi si riconoscono`() {
        assertEquals("Energia Psico", BasicEnergyResolver.italianEnergyName("Basic Psychic Energy"))
        assertEquals("Energia Fuoco", BasicEnergyResolver.italianEnergyName("Fire Energy"))
        assertEquals("Energia Erba", BasicEnergyResolver.italianEnergyName("Basic Grass Energy"))
        assertEquals("Energia Lampo", BasicEnergyResolver.italianEnergyName("Lightning Energy"))
        assertEquals("Energia Metallo", BasicEnergyResolver.italianEnergyName("Basic Metal Energy"))
    }

    @Test
    fun `le energie base italiane si riconoscono`() {
        assertEquals("Energia Psico", BasicEnergyResolver.italianEnergyName("Energia Psico"))
        assertEquals("Energia Acqua", BasicEnergyResolver.italianEnergyName("Energia Acqua"))
        assertEquals("Energia Lotta", BasicEnergyResolver.italianEnergyName("Energia Lotta"))
    }

    @Test
    fun `gli accenti non contano nel confronto e restano nel nome`() {
        // Il nome restituito e' quello vero del catalogo, accento compreso,
        // perche' e' anche la stringa con cui poi si cerca la carta.
        assertEquals("Energia Oscurità", BasicEnergyResolver.italianEnergyName("Energia Oscurità"))
        assertEquals("Energia Oscurità", BasicEnergyResolver.italianEnergyName("Energia Oscurita"))
        assertEquals("Energia Oscurità", BasicEnergyResolver.italianEnergyName("Darkness Energy"))
    }

    @Test
    fun `le energie speciali che si chiamano come una base non ingannano`() {
        // Sono nomi veri del catalogo italiano. Cercare la parola del tipo
        // dentro il nome li scambierebbe per energie base, e finirebbero nel
        // mazzo al posto della carta chiesta.
        listOf(
            "Energia Drago doppia",
            "Energia Darkness Nascosta",
            "Energia Incolore Doppia",
            "Energia Colorless Potente",
            "Energia Infuocata",
            "Energia Appuntita",
            "Energia Fortuna",
            "Energia Medica",
            "Energia Memoria"
        ).forEach { name ->
            assertNull("'$name' non doveva essere trattata come base", BasicEnergyResolver.italianEnergyName(name))
        }
    }

    @Test
    fun `la forma con la lettera del tipo di PTCGL funziona`() {
        assertEquals("Energia Psico", BasicEnergyResolver.italianEnergyName("Basic {P} Energy"))
        assertEquals("Energia Erba", BasicEnergyResolver.italianEnergyName("Basic {G} Energy"))
    }

    @Test
    fun `il drago non e' un'energia base`() {
        // "Energia Drago" come carta base non esiste: nel catalogo c'e' solo
        // "Energia Drago doppia", che e' speciale.
        assertNull(BasicEnergyResolver.italianEnergyName("Dragon Energy"))
        assertNull(BasicEnergyResolver.italianEnergyName("Energia Drago"))
    }

    @Test
    fun `le maiuscole e gli spazi non contano`() {
        assertEquals("Energia Fuoco", BasicEnergyResolver.italianEnergyName("  BASIC FIRE ENERGY  "))
    }

    @Test
    fun `le energie speciali non sono energie base`() {
        listOf(
            "Double Turbo Energy",
            "Energia Doppia Turbo",
            "Jet Energy",
            "Reflective Energy",
            "Luminous Energy",
            "Therapeutic Energy",
            "Gift Energy",
            "Neo Upper Energy",
            "Prism Energy",
            "Aurora Energy",
            "Rainbow Energy",
            "Twin Energy"
        ).forEach { name ->
            assertNull("'$name' non doveva essere trattata come base", BasicEnergyResolver.italianEnergyName(name))
            assertFalse(BasicEnergyResolver.isBasicEnergy(name))
        }
    }

    @Test
    fun `una carta che non e' un'energia non viene toccata`() {
        assertNull(BasicEnergyResolver.italianEnergyName("Charizard ex"))
        assertNull(BasicEnergyResolver.italianEnergyName("Ultra Ball"))
        assertNull(BasicEnergyResolver.italianEnergyName("Professor's Research"))
    }

    @Test
    fun `un'energia di un tipo sconosciuto non viene indovinata`() {
        // Contiene "energy" ma nessun tipo riconoscibile: meglio lasciarla al
        // percorso normale che inventarle un tipo.
        assertNull(BasicEnergyResolver.italianEnergyName("Mystery Energy"))
    }

    @Test
    fun `due nomi dello stesso tipo sono la stessa energia`() {
        assertTrue(BasicEnergyResolver.isSameBasicEnergy("Basic Psychic Energy", "Energia Psico"))
        assertTrue(BasicEnergyResolver.isSameBasicEnergy("Fire Energy", "Energia Fuoco"))
    }

    @Test
    fun `due tipi diversi non sono la stessa energia`() {
        assertFalse(BasicEnergyResolver.isSameBasicEnergy("Energia Psico", "Energia Fuoco"))
        assertFalse(BasicEnergyResolver.isSameBasicEnergy("Water Energy", "Energia Lampo"))
    }

    @Test
    fun `un'energia speciale non e' mai la stessa di una base`() {
        assertFalse(BasicEnergyResolver.isSameBasicEnergy("Double Turbo Energy", "Energia Psico"))
        assertFalse(BasicEnergyResolver.isSameBasicEnergy("Jet Energy", "Jet Energy"))
    }

    @Test
    fun `una carta qualsiasi non e' la stessa di un'energia`() {
        assertFalse(BasicEnergyResolver.isSameBasicEnergy("Charizard ex", "Energia Fuoco"))
    }

    @Test
    fun `electric vale come lightning`() {
        // Alcune liste usano "Electric" invece di "Lightning".
        assertEquals("Energia Lampo", BasicEnergyResolver.italianEnergyName("Electric Energy"))
    }

    @Test
    fun `steel vale come metal`() {
        assertEquals("Energia Metallo", BasicEnergyResolver.italianEnergyName("Steel Energy"))
    }
}

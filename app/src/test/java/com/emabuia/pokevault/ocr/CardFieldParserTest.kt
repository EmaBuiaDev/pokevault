package com.emabuia.pokevault.ocr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CardFieldParserTest {

    @Test
    fun `extract set code ignores italian layout words`() {
        assertNull(CardFieldParser.extractSetCode("Fase 1 Sliggoo PV 90"))
        assertNull(CardFieldParser.extractSetCode("Base Pokemon PV 70"))
    }

    @Test
    fun `extract set code accepts known mega aliases`() {
        assertEquals("dcr", CardFieldParser.extractSetCode("ME04 067/086"))
        assertEquals("dcr", CardFieldParser.extractSetCode("CRI 067/086"))
    }

    @Test
    fun `extract set name maps italian expansion name`() {
        assertEquals("Chaos Rising", CardFieldParser.extractSetName("Caos Nascente 067/086"))
    }
}
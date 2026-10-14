package com.emabuia.pokevault.data.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** L'interruttore remoto dell'inglese: solo un true vero lo accende. */
class AppConfigTest {

    @Test
    fun `true e false del Worker`() {
        assertEquals(true, AppConfig.parseEnglishCatalog("""{"englishCatalog":true}"""))
        assertEquals(false, AppConfig.parseEnglishCatalog("""{"englishCatalog":false}"""))
    }

    @Test
    fun `valori strani spengono, risposte illeggibili tengono l'ultimo valore`() {
        assertEquals(false, AppConfig.parseEnglishCatalog("""{"englishCatalog":"true"}"""))
        assertEquals(false, AppConfig.parseEnglishCatalog("""{"englishCatalog":1}"""))
        assertNull(AppConfig.parseEnglishCatalog("""{"altro":true}"""))
        assertNull(AppConfig.parseEnglishCatalog("{rotto"))
        assertNull(AppConfig.parseEnglishCatalog(""))
        assertNull(AppConfig.parseEnglishCatalog(null))
    }

    @Test
    fun `senza nessuna risposta l'inglese e' spento`() {
        assertEquals(false, AppConfig.englishCatalog)
    }
}

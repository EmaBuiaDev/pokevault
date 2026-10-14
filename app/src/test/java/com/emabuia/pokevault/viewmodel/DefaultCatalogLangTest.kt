package com.emabuia.pokevault.viewmodel

import com.emabuia.pokevault.data.catalog.CatalogLang
import com.emabuia.pokevault.util.AppLocale
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Il catalogo con cui si apre il Pokedex: chi e' inglese parte dall'inglese,
 * chi e' italiano dall'italiano, e la scelta fatta col selettore vince sempre.
 */
class DefaultCatalogLangTest {

    @Test
    fun `senza scelta segue la lingua dell'app`() {
        assertEquals(CatalogLang.IT, defaultCatalogLang(null, AppLocale.Language.IT))
        assertEquals(CatalogLang.EN, defaultCatalogLang(null, AppLocale.Language.EN))
    }

    @Test
    fun `la scelta del selettore vince sulla lingua dell'app`() {
        assertEquals(CatalogLang.IT, defaultCatalogLang("IT", AppLocale.Language.EN))
        assertEquals(CatalogLang.EN, defaultCatalogLang("EN", AppLocale.Language.IT))
    }

    @Test
    fun `una preferenza illeggibile vale come nessuna scelta`() {
        assertEquals(CatalogLang.EN, defaultCatalogLang("boh", AppLocale.Language.EN))
    }

    @Test
    fun `l'app parte in italiano solo sui telefoni in italiano`() {
        assertEquals(AppLocale.Language.IT, AppLocale.forDeviceLanguage("it"))
        assertEquals(AppLocale.Language.EN, AppLocale.forDeviceLanguage("en"))
        assertEquals(AppLocale.Language.EN, AppLocale.forDeviceLanguage("de"))
        assertEquals(AppLocale.Language.EN, AppLocale.forDeviceLanguage(null))
    }
}

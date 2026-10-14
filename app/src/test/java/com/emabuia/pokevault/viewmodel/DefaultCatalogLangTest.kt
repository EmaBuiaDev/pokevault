package com.emabuia.pokevault.viewmodel

import com.emabuia.pokevault.data.catalog.CatalogLang
import com.emabuia.pokevault.util.AppLocale
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Il catalogo con cui si apre il Pokedex: chi e' inglese parte dall'inglese,
 * chi e' italiano dall'italiano; la scelta fatta col selettore vale finche'
 * l'app non cambia lingua.
 */
class DefaultCatalogLangTest {

    @Test
    fun `senza scelta segue la lingua dell'app`() {
        assertEquals(CatalogLang.IT, defaultCatalogLang(null, null, AppLocale.Language.IT))
        assertEquals(CatalogLang.EN, defaultCatalogLang(null, null, AppLocale.Language.EN))
    }

    @Test
    fun `la scelta del selettore vince finche' l'app resta nella stessa lingua`() {
        assertEquals(CatalogLang.IT, defaultCatalogLang("IT", "en", AppLocale.Language.EN))
        assertEquals(CatalogLang.EN, defaultCatalogLang("EN", "it", AppLocale.Language.IT))
    }

    @Test
    fun `cambiare lingua all'app rimette il catalogo nella lingua nuova`() {
        assertEquals(CatalogLang.EN, defaultCatalogLang("IT", "it", AppLocale.Language.EN))
        assertEquals(CatalogLang.IT, defaultCatalogLang("EN", "en", AppLocale.Language.IT))
    }

    @Test
    fun `una scelta salvata senza la lingua dell'app non conta`() {
        // Le prove fatte con l'APK precedente salvavano solo "catalog_lang".
        assertEquals(CatalogLang.EN, defaultCatalogLang("IT", null, AppLocale.Language.EN))
    }

    @Test
    fun `una preferenza illeggibile vale come nessuna scelta`() {
        assertEquals(CatalogLang.EN, defaultCatalogLang("boh", "en", AppLocale.Language.EN))
    }

    @Test
    fun `l'app parte in italiano solo sui telefoni in italiano`() {
        assertEquals(AppLocale.Language.IT, AppLocale.forDeviceLanguage("it"))
        assertEquals(AppLocale.Language.EN, AppLocale.forDeviceLanguage("en"))
        assertEquals(AppLocale.Language.EN, AppLocale.forDeviceLanguage("de"))
        assertEquals(AppLocale.Language.EN, AppLocale.forDeviceLanguage(null))
    }
}

/** Dove si cerca la prima volta: nella lingua dell'utente; "Tutte" solo se lo si sceglie. */
class DefaultSearchScopeTest {

    @Test
    fun `senza scelta cerca nella lingua dell'app`() {
        assertEquals(CardSearchScope.IT, defaultSearchScope(null, null, AppLocale.Language.IT))
        assertEquals(CardSearchScope.EN, defaultSearchScope(null, null, AppLocale.Language.EN))
    }

    @Test
    fun `Tutte resta finche' l'app non cambia lingua`() {
        assertEquals(CardSearchScope.ALL, defaultSearchScope("ALL", "it", AppLocale.Language.IT))
        assertEquals(CardSearchScope.EN, defaultSearchScope("ALL", "it", AppLocale.Language.EN))
    }

    @Test
    fun `cosa include ogni ambito`() {
        assertEquals(true to false, CardSearchScope.IT.includesItalian to CardSearchScope.IT.includesEnglish)
        assertEquals(false to true, CardSearchScope.EN.includesItalian to CardSearchScope.EN.includesEnglish)
        assertEquals(true to true, CardSearchScope.ALL.includesItalian to CardSearchScope.ALL.includesEnglish)
    }
}

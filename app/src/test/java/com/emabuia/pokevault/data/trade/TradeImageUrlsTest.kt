package com.emabuia.pokevault.data.trade

import com.emabuia.pokevault.data.catalog.CatalogLang
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Le immagini negli scambi: la lingua della copia prima, l'altra se manca. */
class TradeImageUrlsTest {

    private val it = "https://it.example"
    private val en = "https://en.example/"

    @Test
    fun `inglese spento - solo l'immagine italiana, come prima`() {
        assertEquals(
            listOf("https://it.example/images/it/SV08/25?size=low"),
            TradeCardKey.imageUrls("sv08:25", it, null, "🇬🇧 English")
        )
        // Uguale all'indirizzo di sempre.
        assertEquals(TradeCardKey.imageUrl("sv08:25", it), TradeCardKey.imageUrls("sv08:25", it, null, null).single())
    }

    @Test
    fun `copia inglese - prima l'immagine inglese, poi l'italiana`() {
        assertEquals(
            listOf("https://en.example/images/en/SV08/25?size=low", "https://it.example/images/it/SV08/25?size=low"),
            TradeCardKey.imageUrls("sv08:25", it, en, "🇬🇧 English")
        )
    }

    @Test
    fun `copia italiana o senza lingua - prima l'italiana, poi l'inglese (set solo inglesi)`() {
        val expected = listOf("https://it.example/images/it/CEL25/5?size=high", "https://en.example/images/en/CEL25/5?size=high")
        assertEquals(expected, TradeCardKey.imageUrls("cel25:5", it, en, "🇮🇹 Italiano", size = "high"))
        assertEquals(expected, TradeCardKey.imageUrls("cel25:5", it, en, null, size = "high"))
    }

    @Test
    fun `la lingua di una copia, con o senza bandiera`() {
        assertEquals(CatalogLang.EN, CatalogLang.ofCopyLanguage("🇬🇧 English"))
        assertEquals(CatalogLang.EN, CatalogLang.ofCopyLanguage(" english "))
        assertEquals(CatalogLang.IT, CatalogLang.ofCopyLanguage("🇮🇹 Italiano"))
        assertNull(CatalogLang.ofCopyLanguage("🇯🇵 Giapponese"))
        assertNull(CatalogLang.ofCopyLanguage(""))
    }
}

/** All'altra lingua solo se l'immagine manca, non se la rete non va. */
class MissingImageTest {

    private fun httpError(code: Int): Throwable = coil.network.HttpException(
        okhttp3.Response.Builder()
            .request(okhttp3.Request.Builder().url("https://x.example/img").build())
            .protocol(okhttp3.Protocol.HTTP_1_1)
            .code(code)
            .message("")
            .build()
    )

    @Test
    fun `404 vuol dire immagine mancante, timeout e 500 no`() {
        assertEquals(true, com.emabuia.pokevault.ui.trade.isMissingImage(httpError(404)))
        assertEquals(false, com.emabuia.pokevault.ui.trade.isMissingImage(httpError(503)))
        assertEquals(false, com.emabuia.pokevault.ui.trade.isMissingImage(java.net.SocketTimeoutException()))
    }
}

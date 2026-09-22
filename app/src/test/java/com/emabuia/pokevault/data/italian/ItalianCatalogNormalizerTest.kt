package com.emabuia.pokevault.data.italian

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ItalianCatalogNormalizerTest {

    @Test
    fun parseCards_stripsExternalUrlsAndBuildsManifest() {
        val rawJson = """
            [
              {
                "cardId": "SVP_IT_1.png",
                "espansioneId": "svp",
                "nome": "Sprigatito",
                "tipo": null,
                "ps": "70",
                "attacchi": [
                  {
                    "nome": "Miniassorbimento",
                    "danno": "10",
                    "descrizione": "Cura questo Pokémon da 10 danni."
                  }
                ],
                "regolaSpeciale": null,
                "immagineUrlOriginale": "https://assets.pokemon.com/static-assets/content-assets/cms2-it-it/img/cards/web/SVP/SVP_IT_1.png",
                "sourceUrl": "https://www.pokemon.com/it/gcc/archivio-carte/series/svp/1/"
              },
              {
                "cardId": "MEP_IT_1.png",
                "espansioneId": "mep",
                "nome": "Meganium",
                "tipo": null,
                "ps": "160",
                "attacchi": [],
                "regolaSpeciale": null,
                "immagineUrlOriginale": "https://assets.pokemon.com/static-assets/content-assets/cms2-it-it/img/cards/web/MEP/MEP_IT_1.png",
                "sourceUrl": "https://www.pokemon.com/it/gcc/archivio-carte/series/mep/1/"
              }
            ]
        """.trimIndent()

        val cards = ItalianCatalogNormalizer.parseCards(rawJson)

        assertEquals(2, cards.size)
        assertEquals("svp", cards[0].espansioneId)
        assertEquals("mep", cards[1].espansioneId)

        val canonicalJson = ItalianCatalogNormalizer.toCanonicalJson(cards)
        assertFalse(canonicalJson.contains("sourceUrl"))
        assertFalse(canonicalJson.contains("immagineUrlOriginale"))
        assertFalse(canonicalJson.contains("pokemon.com"))

        val catalog = ItalianCatalogNormalizer.buildCatalog(cards)
        assertEquals(2, catalog.cards.size)
        assertEquals(2, catalog.expansions.size)
        assertEquals("svp", catalog.expansions.first().espansioneId)
        assertEquals(1, catalog.expansions.first().cardCount)

        val imageReference = cards.first().imageReference()
        assertNotNull(imageReference)
        assertEquals("SVP", imageReference?.folderName)
        assertEquals("SVP_IT_1.png", imageReference?.preferredFileName)
    }

    @Test
    fun parseExpansionCardsResponse_parsesSingleExpansionPayload() {
        // Shape returned by GET /v1/expansions/{id}/cards on the Worker.
        val rawJson = """
            {
              "expansionId": "me04",
              "cards": [
                {
                  "cardId": "ME04_IT_1.png",
                  "espansioneId": "me04",
                  "nome": "Weedle",
                  "tipo": null,
                  "ps": "50",
                  "attacchi": [
                    {
                      "nome": "Attacco a Sorpresa",
                      "danno": "30",
                      "descrizione": "Lancia una moneta. Se esce croce, questo attacco non ha effetto."
                    }
                  ],
                  "regolaSpeciale": null
                }
              ]
            }
        """.trimIndent()

        val cards = ItalianCatalogNormalizer.parseExpansionCardsResponse(rawJson)

        assertEquals(1, cards.size)
        assertEquals("me04", cards[0].espansioneId)
        assertEquals("Weedle", cards[0].nome)
        assertEquals("50", cards[0].ps)
        assertEquals(1, cards[0].attacchi.size)
        assertEquals("Attacco a Sorpresa", cards[0].attacchi.first().nome)
    }

    @Test
    fun parseExpansionCardsResponse_blankInputReturnsEmptyList() {
        assertEquals(emptyList<ItalianCardRecord>(), ItalianCatalogNormalizer.parseExpansionCardsResponse("  "))
    }

    /**
     * "30 Anniversario Collezione Classica" e' il primo set il cui codice porta
     * un trattino (30TH-C, imposto dall'archivio ufficiale). Se il cardId non
     * viene riconosciuto non fallisce niente: l'immagine finisce su un URL
     * costruito col nome del file al posto del numero, e il numero carta
     * diventa Int.MAX_VALUE, cioe' il set si ordina alfabeticamente.
     */
    @Test
    fun toImageReference_reggeIlTrattinoNelCodiceSet() {
        val ref = ItalianCatalogNormalizer.toImageReference("30TH-C_IT_1.png")
        assertEquals("30TH-C", ref?.setCode)
        assertEquals("1", ref?.cardNumber)

        // I codici senza trattino continuano a comportarsi come prima, padding
        // del numero compreso.
        val storico = ItalianCatalogNormalizer.toImageReference("DP1_IT_007.png")
        assertEquals("DP1", storico?.setCode)
        assertEquals("7", storico?.cardNumber)

        // E le promo numerate a lettere restano tali, non diventano numeri.
        val promo = ItalianCatalogNormalizer.toImageReference("SWSHP_IT_SWSH026.png")
        assertEquals("SWSH026", promo?.cardNumber)
    }

    /**
     * L'illustratore e' l'unico campo che il Worker OMETTE quando manca invece
     * di serializzarlo a null -- il catalogo completo porta diciottomila carte
     * a ogni client e una chiave vuota per ognuna sarebbe peso puro. Le due
     * forme devono quindi convivere nella stessa risposta: chi ce l'ha lo
     * legge, chi non ce l'ha resta a null e non fa saltare il parse.
     */
    @Test
    fun parseExpansionCardsResponse_leggeIllustratoreEReggeLaChiaveAssente() {
        val rawJson = """
            {
              "expansionId": "base1",
              "cards": [
                {
                  "cardId": "BASE1_IT_4.png",
                  "espansioneId": "base1",
                  "nome": "Charizard",
                  "attacchi": [],
                  "illustratore": "Mitsuhiro Arita"
                },
                {
                  "cardId": "BASE1_IT_5.png",
                  "espansioneId": "base1",
                  "nome": "Clefairy",
                  "attacchi": []
                }
              ]
            }
        """.trimIndent()

        val cards = ItalianCatalogNormalizer.parseExpansionCardsResponse(rawJson)

        assertEquals(2, cards.size)
        assertEquals("Mitsuhiro Arita", cards[0].illustratore)
        assertNull(cards[1].illustratore)
    }

    /**
     * Il catalogo completo non viene messo in cache com'e' arrivato: prima di
     * finire in SharedPreferences passa per toCatalogJson, che lo riserializza
     * dai record (vedi saveToPrefs in ItalianCatalogRemoteRepository). Se un
     * campo si perdesse in quel giro non si romperebbe niente subito -- la
     * prima lettura viene dalla rete ed e' completa -- ma da li' in avanti la
     * scheda in collezione, che legge il catalogo cachato, resterebbe senza
     * illustratore fino al riavvio dell'app.
     */
    @Test
    fun toCatalogJson_nonPerdeLIllustratoreNelGiroInCache() {
        val original = ItalianCatalogNormalizer.parseCatalogJson(
            """
            [
              {
                "cardId": "BASE1_IT_4.png",
                "espansioneId": "base1",
                "nome": "Charizard",
                "attacchi": [],
                "illustratore": "Mitsuhiro Arita"
              }
            ]
            """.trimIndent()
        )
        assertEquals("Mitsuhiro Arita", original.cards.single().illustratore)

        val roundTripped = ItalianCatalogNormalizer.parseCatalogJson(
            ItalianCatalogNormalizer.toCatalogJson(original)
        )
        assertEquals("Mitsuhiro Arita", roundTripped.cards.single().illustratore)
    }
}

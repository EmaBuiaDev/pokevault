package com.emabuia.pokevault.data.italian

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
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
}

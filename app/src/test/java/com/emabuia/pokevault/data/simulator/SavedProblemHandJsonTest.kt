package com.emabuia.pokevault.data.simulator

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Le mani salvate dalle versioni pubblicate fino alla 3.1.5 hanno le chiavi
 * che R8 dava ai campi, `a`..`g` (mapping della 3.1.4, vc37). Dopo
 * l'aggiornamento devono rileggersi uguali, non sparire o arrivare a null.
 */
class SavedProblemHandJsonTest {

    private val gson = Gson()
    private val listType = object : TypeToken<List<SavedProblemHand>>() {}.type

    @Test
    fun `una mano salvata dalla 3_1_4 si rilegge con tutti i campi`() {
        val legacy = """
            [{"a":"1b4e28ba-2fa1-11d2-883f-0016d3cca427","b":"deck42","c":"Mega Excadrill",
              "d":["Drilbur","Excadrill"],"e":["no-energy"],"f":"mulligan","g":1727000000000}]
        """.trimIndent()

        val hands: List<SavedProblemHand> = gson.fromJson(legacy, listType)

        assertEquals(1, hands.size)
        val hand = hands.single()
        assertEquals("1b4e28ba-2fa1-11d2-883f-0016d3cca427", hand.id)
        assertEquals("deck42", hand.deckId)
        assertEquals("Mega Excadrill", hand.deckName)
        assertEquals(listOf("Drilbur", "Excadrill"), hand.cards)
        assertEquals(listOf("no-energy"), hand.tags)
        assertEquals("mulligan", hand.note)
        assertEquals(1727000000000L, hand.createdAtMillis)
    }

    @Test
    fun `da ora si scrive coi nomi veri, e si rilegge uguale`() {
        val hand = SavedProblemHand(
            id = "id-1",
            deckId = "deck42",
            deckName = "Prova",
            cards = listOf("Pikachu"),
            tags = emptyList(),
            note = "",
            createdAtMillis = 42L
        )

        val json = gson.toJson(listOf(hand))

        assertTrue(json, json.contains("\"deckId\":\"deck42\""))
        assertTrue(json, json.contains("\"createdAtMillis\":42"))
        assertEquals(listOf(hand), gson.fromJson<List<SavedProblemHand>>(json, listType))
    }
}

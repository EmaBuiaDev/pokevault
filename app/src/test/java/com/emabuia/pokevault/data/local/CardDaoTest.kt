package com.emabuia.pokevault.data.local

import android.app.Application
import androidx.room.Room
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)

/**
 * Integration tests per CardDao
 * 
 * Testa:
 * - Inserimento/aggiornamento carte
 * - Ricerca per SetId
 * - Ricerca per numero carta
 * - Ricerca per nome
 * - Cancellazione dati scaduti
 */
class CardDaoTest {

    private lateinit var database: PokeVaultDatabase
    private lateinit var cardDao: CardDao

    @Before
    fun setup() {
        val app = RuntimeEnvironment.getApplication()
        database = Room.inMemoryDatabaseBuilder(
            app,
            PokeVaultDatabase::class.java
        ).allowMainThreadQueries().build()

        cardDao = database.cardDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    private fun createTestCard(
        id: String,
        name: String,
        setId: String,
        number: String,
        cachedAt: Long = System.currentTimeMillis()
    ): CachedCardEntity {
        return CachedCardEntity(
            id = id,
            name = name,
            supertype = "Pokémon",
            subtypesJson = null,
            hp = "60",
            typesJson = null,
            setId = setId,
            setName = "Test Set",
            setSeries = "Test Series",
            number = number,
            rarity = "Rare",
            smallImageUrl = "https://example.com/$id-small.jpg",
            largeImageUrl = "https://example.com/$id-large.jpg",
            tcgplayerUrl = null,
            tcgplayerPricesJson = null,
            cardmarketUrl = null,
            cardmarketAvgSellPrice = null,
            cardmarketLowPrice = null,
            cardmarketTrendPrice = null,
            cardmarketAvg1 = null,
            cardmarketAvg7 = null,
            cardmarketAvg30 = null,
            cachedAt = cachedAt
        )
    }

    @Test
    fun testInsertAndRetrieveCard() {
        runBlocking {
            // Arrange
            val card = createTestCard(
                id = "card_001",
                name = "Pikachu",
                setId = "set_001",
                number = "025"
            )

            // Act
            cardDao.upsertCards(listOf(card))
            val retrieved = cardDao.getById("card_001")

            // Assert
            assertNotNull(retrieved)
            assertEquals("Pikachu", retrieved?.name)
            assertEquals("set_001", retrieved?.setId)
        }
    }

    @Test
    fun testGetCardsBySetId() {
        runBlocking {
            // Arrange
            val cards = listOf(
                createTestCard(
                    id = "card_001",
                    name = "Pikachu",
                    setId = "set_001",
                    number = "025"
                ),
                createTestCard(
                    id = "card_002",
                    name = "Charizard",
                    setId = "set_001",
                    number = "006"
                )
            )

            // Act
            cardDao.upsertCards(cards)
            val retrieved = cardDao.getBySetId("set_001")

            // Assert
            assertEquals(2, retrieved.size)
            assertTrue(retrieved.any { it.name == "Pikachu" })
            assertTrue(retrieved.any { it.name == "Charizard" })
        }
    }

    @Test
    fun testSearchCardByName() {
        runBlocking {
            // Arrange
            val cards = listOf(
                createTestCard(
                    id = "card_001",
                    name = "Pikachu",
                    setId = "set_001",
                    number = "025"
                ),
                createTestCard(
                    id = "card_002",
                    name = "Raichu",
                    setId = "set_001",
                    number = "026"
                )
            )

            // Act
            cardDao.upsertCards(cards)
            val results = cardDao.searchByName("%pika%", 10)

            // Assert
            assertEquals(1, results.size)
            assertEquals("Pikachu", results[0].name)
        }
    }

    @Test
    fun testDeleteExpiredCards() {
        runBlocking {
            // Arrange
            val now = System.currentTimeMillis()
            val oldCard = createTestCard(
                id = "card_old",
                name = "OldCard",
                setId = "set_001",
                number = "001",
                cachedAt = now - 1000000
            )
            val newCard = createTestCard(
                id = "card_new",
                name = "NewCard",
                setId = "set_001",
                number = "002",
                cachedAt = now
            )

            // Act
            cardDao.upsertCards(listOf(oldCard, newCard))
            cardDao.deleteExpired(now - 10000)
            val remaining = cardDao.getBySetId("set_001")

            // Assert
            assertEquals(1, remaining.size)
            assertEquals("NewCard", remaining[0].name)
        }
    }
}

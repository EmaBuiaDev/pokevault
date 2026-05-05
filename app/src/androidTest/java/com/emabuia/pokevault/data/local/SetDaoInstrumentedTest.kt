package com.emabuia.pokevault.data.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith

/**
 * Instrumented integration tests per SetDao
 * 
 * Usa AndroidJUnit4Runner e il database vero in memoria
 * Testa:
 * - Inserimento/aggiornamento set
 * - Recupero set
 * - Cancellazione dati scaduti
 */
@RunWith(AndroidJUnit4::class)
class SetDaoInstrumentedTest {

    private lateinit var database: PokeVaultDatabase
    private lateinit var setDao: SetDao

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(
            context,
            PokeVaultDatabase::class.java
        ).build()

        setDao = database.setDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun testInsertAndRetrieveSets() {
        runBlocking {
            // Arrange
            val sets = listOf(
                CachedSetEntity(
                    id = "sv01",
                    name = "Scarlet & Violet",
                    series = "Scarlet & Violet",
                    releaseDate = "2023-04-14",
                    totalCards = 198,
                    logoUrl = "https://example.com/sv01.png",
                    symbolUrl = "https://example.com/sv01_symbol.png",
                    cachedAt = System.currentTimeMillis()
                ),
                CachedSetEntity(
                    id = "sv04pt",
                    name = "Paradox Rift",
                    series = "Scarlet & Violet",
                    releaseDate = "2023-11-03",
                    totalCards = 182,
                    logoUrl = "https://example.com/sv04pt.png",
                    symbolUrl = "https://example.com/sv04pt_symbol.png",
                    cachedAt = System.currentTimeMillis()
                )
            )

            // Act
            setDao.upsertSets(sets)
            val retrieved = setDao.getAll()

            // Assert
            assertEquals(2, retrieved.size)
            assertTrue(retrieved.any { it.id == "sv01" })
            assertTrue(retrieved.any { it.id == "sv04pt" })
        }
    }

    @Test
    fun testGetLastCacheTime() {
        runBlocking {
            // Arrange
            val now = System.currentTimeMillis()
            val sets = listOf(
                CachedSetEntity(
                    id = "sv01",
                    name = "Set 1",
                    series = "Series 1",
                    releaseDate = "2023-01-01",
                    totalCards = 100,
                    logoUrl = "https://example.com/1.png",
                    symbolUrl = "https://example.com/1_symbol.png",
                    cachedAt = now - 10000
                ),
                CachedSetEntity(
                    id = "sv02",
                    name = "Set 2",
                    series = "Series 1",
                    releaseDate = "2023-02-01",
                    totalCards = 120,
                    logoUrl = "https://example.com/2.png",
                    symbolUrl = "https://example.com/2_symbol.png",
                    cachedAt = now
                )
            )

            // Act
            setDao.upsertSets(sets)
            val lastTime = setDao.getLastCacheTime()

            // Assert
            assertNotNull(lastTime)
            assertEquals(now, lastTime)
        }
    }

    @Test
    fun testDeleteAll() {
        runBlocking {
            // Arrange
            val sets = listOf(
                CachedSetEntity(
                    id = "sv01",
                    name = "Set 1",
                    series = "Series 1",
                    releaseDate = "2023-01-01",
                    totalCards = 100,
                    logoUrl = "https://example.com/1.png",
                    symbolUrl = "https://example.com/1_symbol.png",
                    cachedAt = System.currentTimeMillis()
                )
            )

            // Act
            setDao.upsertSets(sets)
            var retrieved = setDao.getAll()
            assertEquals(1, retrieved.size)

            setDao.deleteAll()
            retrieved = setDao.getAll()

            // Assert
            assertEquals(0, retrieved.size)
        }
    }
}

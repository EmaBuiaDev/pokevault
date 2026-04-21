package com.emabuia.pokevault.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert

data class CachedSetCardCount(
    val setId: String,
    val count: Int
)

@Dao
interface CardDao {

    @Upsert
    suspend fun upsertCards(cards: List<CachedCardEntity>)

    @Query("SELECT * FROM cards WHERE setId = :setId")
    suspend fun getBySetId(setId: String): List<CachedCardEntity>

    @Query("SELECT MAX(cachedAt) FROM cards WHERE setId = :setId")
    suspend fun getLastCacheTimeForSet(setId: String): Long?

    @Query("SELECT DISTINCT setId FROM cards")
    suspend fun getVisitedSetIds(): List<String>

    // All rows were saved through the isActualCard filter, so historical data is accurate.
    @Query("SELECT setId, COUNT(*) as count FROM cards GROUP BY setId")
    suspend fun getCachedCardCountsBySet(): List<CachedSetCardCount>

    @Query("DELETE FROM cards WHERE cachedAt < :threshold")
    suspend fun deleteExpired(threshold: Long)
}

package com.emabuia.pokevault.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert

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

    @Query("DELETE FROM cards WHERE cachedAt < :threshold")
    suspend fun deleteExpired(threshold: Long)
}

package com.superencrypter.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.superencrypter.data.local.entity.HistoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface HistoryDao {
    @Query("SELECT * FROM history ORDER BY createdAt DESC LIMIT 100")
    fun observeRecent(): Flow<List<HistoryEntity>>

    @Insert
    suspend fun insert(history: HistoryEntity)
}

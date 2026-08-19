package com.agoitdev.spenvo.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface SyncStateDao {
    @Upsert
    suspend fun upsert(state: SyncStateEntity)

    @Query("SELECT * FROM sync_state WHERE id = :id")
    fun observe(id: String = SyncStateEntity.SINGLETON_ID): Flow<SyncStateEntity?>

    @Query("SELECT * FROM sync_state WHERE id = :id")
    suspend fun get(id: String = SyncStateEntity.SINGLETON_ID): SyncStateEntity?
}

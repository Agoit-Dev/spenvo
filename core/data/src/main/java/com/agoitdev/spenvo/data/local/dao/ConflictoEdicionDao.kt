package com.agoitdev.spenvo.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.agoitdev.spenvo.data.local.entity.ConflictoEdicionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ConflictoEdicionDao {
    @Query("SELECT * FROM conflictos_pendientes")
    fun observeAll(): Flow<List<ConflictoEdicionEntity>>

    @Query("SELECT * FROM conflictos_pendientes WHERE clave = :clave")
    suspend fun get(clave: String): ConflictoEdicionEntity?

    @Upsert
    suspend fun upsert(entity: ConflictoEdicionEntity)

    @Query("DELETE FROM conflictos_pendientes WHERE clave = :clave")
    suspend fun delete(clave: String)
}

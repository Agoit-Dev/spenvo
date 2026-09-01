package com.agoitdev.spenvo.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.agoitdev.spenvo.data.local.entity.EdicionPendienteEntity

@Dao
interface EdicionPendienteDao {
    @Query("SELECT * FROM ediciones_pendientes WHERE clave = :clave")
    suspend fun get(clave: String): EdicionPendienteEntity?

    @Upsert
    suspend fun upsert(entity: EdicionPendienteEntity)

    @Query("DELETE FROM ediciones_pendientes WHERE clave = :clave")
    suspend fun delete(clave: String)
}

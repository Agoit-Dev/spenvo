package com.agoitdev.spenvo.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.agoitdev.spenvo.data.local.entity.UsuarioEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface UsuarioDao {
    @Upsert
    suspend fun upsert(usuario: UsuarioEntity)

    @Upsert
    suspend fun upsertAll(usuarios: List<UsuarioEntity>)

    @Query("SELECT * FROM usuarios WHERE id = :id")
    fun observe(id: String): Flow<UsuarioEntity?>

    @Query("SELECT * FROM usuarios WHERE id = :id")
    suspend fun get(id: String): UsuarioEntity?
}

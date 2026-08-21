package com.agoitdev.spenvo.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.agoitdev.spenvo.data.local.entity.CategoriaEntity
import com.agoitdev.spenvo.domain.model.TipoCategoria
import kotlinx.coroutines.flow.Flow

@Dao
interface CategoriaDao {
    @Upsert
    suspend fun upsert(categoria: CategoriaEntity)

    @Upsert
    suspend fun upsertAll(categorias: List<CategoriaEntity>)

    @Query("SELECT * FROM categorias WHERE planId = :planId AND deletedAt IS NULL")
    fun observeByPlan(planId: String): Flow<List<CategoriaEntity>>

    @Query("SELECT * FROM categorias WHERE planId = :planId AND tipo = :tipo AND deletedAt IS NULL")
    fun observeByPlanAndTipo(planId: String, tipo: TipoCategoria): Flow<List<CategoriaEntity>>
}

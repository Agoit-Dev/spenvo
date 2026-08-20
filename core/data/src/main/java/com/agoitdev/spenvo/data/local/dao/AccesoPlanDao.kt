package com.agoitdev.spenvo.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.agoitdev.spenvo.data.local.entity.AccesoPlanEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AccesoPlanDao {
    @Upsert
    suspend fun upsert(acceso: AccesoPlanEntity)

    @Upsert
    suspend fun upsertAll(accesos: List<AccesoPlanEntity>)

    @Query("SELECT * FROM acceso_plan_financiero WHERE usuarioId = :usuarioId")
    fun observeByUsuario(usuarioId: String): Flow<List<AccesoPlanEntity>>

    @Query("SELECT * FROM acceso_plan_financiero WHERE planId = :planId")
    fun observeByPlan(planId: String): Flow<List<AccesoPlanEntity>>

    @Query("SELECT * FROM acceso_plan_financiero WHERE usuarioId = :usuarioId AND planId = :planId")
    suspend fun get(usuarioId: String, planId: String): AccesoPlanEntity?
}

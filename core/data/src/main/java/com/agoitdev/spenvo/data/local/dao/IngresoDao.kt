package com.agoitdev.spenvo.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.agoitdev.spenvo.data.local.entity.IngresoEntity
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

@Dao
interface IngresoDao {
    @Upsert
    suspend fun upsert(ingreso: IngresoEntity)

    @Upsert
    suspend fun upsertAll(ingresos: List<IngresoEntity>)

    @Query("SELECT * FROM ingresos WHERE planId = :planId")
    fun observeByPlan(planId: String): Flow<List<IngresoEntity>>

    @Query("SELECT * FROM ingresos WHERE planId = :planId AND fecha >= :desde AND fecha <= :hasta")
    fun observeByPlanAndRange(planId: String, desde: LocalDate, hasta: LocalDate): Flow<List<IngresoEntity>>

    @Query("DELETE FROM ingresos WHERE id = :id")
    suspend fun delete(id: String)
}

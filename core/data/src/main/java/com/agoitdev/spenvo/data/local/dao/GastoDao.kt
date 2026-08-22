package com.agoitdev.spenvo.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.agoitdev.spenvo.data.local.entity.GastoEntity
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

@Dao
interface GastoDao {
    @Upsert
    suspend fun upsert(gasto: GastoEntity)

    @Upsert
    suspend fun upsertAll(gastos: List<GastoEntity>)

    @Query("SELECT * FROM gastos WHERE planId = :planId")
    fun observeByPlan(planId: String): Flow<List<GastoEntity>>

    @Query("SELECT * FROM gastos WHERE planId = :planId AND fecha >= :desde AND fecha <= :hasta")
    fun observeByPlanAndRange(planId: String, desde: LocalDate, hasta: LocalDate): Flow<List<GastoEntity>>

    @Query("DELETE FROM gastos WHERE id = :id")
    suspend fun delete(id: String)
}

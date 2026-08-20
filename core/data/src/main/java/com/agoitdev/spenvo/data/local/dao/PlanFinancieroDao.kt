package com.agoitdev.spenvo.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.agoitdev.spenvo.data.local.entity.PlanFinancieroEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PlanFinancieroDao {
    @Upsert
    suspend fun upsert(plan: PlanFinancieroEntity)

    @Upsert
    suspend fun upsertAll(planes: List<PlanFinancieroEntity>)

    @Query("SELECT * FROM planes_financieros WHERE id = :id")
    fun observe(id: String): Flow<PlanFinancieroEntity?>

    @Query("SELECT * FROM planes_financieros WHERE id = :id")
    suspend fun get(id: String): PlanFinancieroEntity?

    @Query(
        """
        SELECT p.* FROM planes_financieros p
        INNER JOIN acceso_plan_financiero a ON a.planId = p.id
        WHERE a.usuarioId = :usuarioId AND p.deletedAt IS NULL
        """,
    )
    fun observeByUsuario(usuarioId: String): Flow<List<PlanFinancieroEntity>>
}

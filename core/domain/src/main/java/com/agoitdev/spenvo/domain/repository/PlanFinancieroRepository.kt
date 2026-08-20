package com.agoitdev.spenvo.domain.repository

import com.agoitdev.spenvo.domain.model.PlanFinanciero
import kotlinx.coroutines.flow.Flow

interface PlanFinancieroRepository {
    fun observarPlanesDelUsuario(usuarioId: String): Flow<List<PlanFinanciero>>

    fun observarPlan(planId: String): Flow<PlanFinanciero?>

    suspend fun crearPlan(plan: PlanFinanciero)

    suspend fun actualizarPlan(plan: PlanFinanciero)
}

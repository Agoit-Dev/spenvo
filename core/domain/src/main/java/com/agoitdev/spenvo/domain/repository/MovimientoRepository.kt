package com.agoitdev.spenvo.domain.repository

import com.agoitdev.spenvo.domain.model.Gasto
import com.agoitdev.spenvo.domain.model.Ingreso
import kotlinx.coroutines.flow.Flow

interface MovimientoRepository {
    suspend fun addGasto(gasto: Gasto)
    suspend fun addIngreso(ingreso: Ingreso)
    fun observeGastos(planId: String): Flow<List<Gasto>>
    fun observeIngresos(planId: String): Flow<List<Ingreso>>
}

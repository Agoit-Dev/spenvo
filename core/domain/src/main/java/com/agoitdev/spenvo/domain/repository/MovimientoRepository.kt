package com.agoitdev.spenvo.domain.repository

import com.agoitdev.spenvo.domain.model.Gasto
import com.agoitdev.spenvo.domain.model.Ingreso
import kotlinx.coroutines.flow.Flow

@Suppress("TooManyFunctions")
interface MovimientoRepository {
    suspend fun addGasto(gasto: Gasto)
    suspend fun addIngreso(ingreso: Ingreso)
    suspend fun actualizarGasto(gasto: Gasto)
    suspend fun eliminarGasto(gasto: Gasto)
    suspend fun actualizarIngreso(ingreso: Ingreso)
    suspend fun eliminarIngreso(ingreso: Ingreso)
    fun observeGastos(planId: String): Flow<List<Gasto>>
    fun observeIngresos(planId: String): Flow<List<Ingreso>>

    /**
     * Conflict resolution (ARCH-M501) — distinct names per entity type: a generic
     * `resolverConflictoUsandoRemoto(id, clave)` would collide on erased signature for Gasto vs
     * Ingreso, matching every other method on this interface (never overloaded by type).
     */
    suspend fun resolverConflictoGastoUsandoLocal(gasto: Gasto, clave: String)
    suspend fun resolverConflictoIngresoUsandoLocal(ingreso: Ingreso, clave: String)
    suspend fun resolverConflictoGastoUsandoRemoto(id: String, clave: String)
    suspend fun resolverConflictoIngresoUsandoRemoto(id: String, clave: String)
}

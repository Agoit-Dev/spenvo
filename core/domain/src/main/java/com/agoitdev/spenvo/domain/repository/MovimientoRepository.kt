package com.agoitdev.spenvo.domain.repository

import com.agoitdev.spenvo.domain.model.Gasto
import com.agoitdev.spenvo.domain.model.Ingreso
import kotlinx.coroutines.flow.Flow

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
     * Persists the remote (Firestore) version of a gasto/ingreso directly into
     * Room, bypassing the edit-attribution use case: used to resolve a
     * conflict in favor of the other user's write, which is already correctly
     * attributed and was only held back from the last sync (Slice 4 design
     * Decision 7). A no-op if the document no longer exists remotely.
     */
    suspend fun aplicarGastoRemoto(id: String)
    suspend fun aplicarIngresoRemoto(id: String)
}

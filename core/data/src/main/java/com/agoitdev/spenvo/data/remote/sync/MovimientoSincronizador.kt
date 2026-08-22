package com.agoitdev.spenvo.data.remote.sync

import com.agoitdev.spenvo.data.local.dao.GastoDao
import com.agoitdev.spenvo.data.local.dao.IngresoDao
import com.agoitdev.spenvo.data.local.mapper.toDomain
import com.agoitdev.spenvo.data.local.mapper.toEntity
import com.agoitdev.spenvo.data.remote.dto.GastoDto
import com.agoitdev.spenvo.data.remote.dto.IngresoDto
import com.google.firebase.firestore.FirebaseFirestore
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch

interface MovimientoSincronizacion {
    fun sincronizar(planId: String): Flow<Unit>
}

/**
 * Syncs a plan's gastos/ingresos from Firestore into Room while collected.
 * Same active-scope-only pattern as CategoriaSincronizador (AGENTS.md rule 3).
 */
@Singleton
class MovimientoSincronizador @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val gastoDao: GastoDao,
    private val ingresoDao: IngresoDao,
) : MovimientoSincronizacion {

    override fun sincronizar(planId: String): Flow<Unit> = callbackFlow {
        val gastosListener = firestore.collection(GASTOS_COLLECTION)
            .whereEqualTo("planId", planId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                trySend(Unit)
                val gastos = snapshot?.documents.orEmpty()
                    .mapNotNull { GastoDto.fromData(it.data ?: return@mapNotNull null) }
                    .map { it.toDomain().toEntity() }
                if (gastos.isNotEmpty()) {
                    launch { gastoDao.upsertAll(gastos) }
                }
            }
        val ingresosListener = firestore.collection(INGRESOS_COLLECTION)
            .whereEqualTo("planId", planId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                trySend(Unit)
                val ingresos = snapshot?.documents.orEmpty()
                    .mapNotNull { IngresoDto.fromData(it.data ?: return@mapNotNull null) }
                    .map { it.toDomain().toEntity() }
                if (ingresos.isNotEmpty()) {
                    launch { ingresoDao.upsertAll(ingresos) }
                }
            }
        awaitClose {
            gastosListener.remove()
            ingresosListener.remove()
        }
    }

    private companion object {
        const val GASTOS_COLLECTION = "gastos"
        const val INGRESOS_COLLECTION = "ingresos"
    }
}

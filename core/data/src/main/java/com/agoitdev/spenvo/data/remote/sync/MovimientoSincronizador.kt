package com.agoitdev.spenvo.data.remote.sync

import com.agoitdev.spenvo.data.local.dao.GastoDao
import com.agoitdev.spenvo.data.local.dao.IngresoDao
import com.agoitdev.spenvo.data.local.mapper.toDomain
import com.agoitdev.spenvo.data.local.mapper.toEntity
import com.agoitdev.spenvo.data.remote.dto.GastoDto
import com.agoitdev.spenvo.data.remote.dto.IngresoDto
import com.agoitdev.spenvo.domain.sync.ConflictosPendientes
import com.agoitdev.spenvo.domain.sync.EdicionesPendientes
import com.agoitdev.spenvo.domain.sync.TipoRegistro
import com.agoitdev.spenvo.domain.sync.aSnapshotConflicto
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.channels.ProducerScope
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
 * A document flagged as a genuine conflict (Slice 4) is held back from Room
 * and registered in [conflictosPendientes] instead of upserted.
 */
@Singleton
class MovimientoSincronizador @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val gastoDao: GastoDao,
    private val ingresoDao: IngresoDao,
    private val edicionesPendientes: EdicionesPendientes,
    private val conflictosPendientes: ConflictosPendientes,
) : MovimientoSincronizacion {

    override fun sincronizar(planId: String): Flow<Unit> = callbackFlow {
        val gastosListener = registrarGastos(planId)
        val ingresosListener = registrarIngresos(planId)
        awaitClose {
            gastosListener.remove()
            ingresosListener.remove()
        }
    }

    private fun ProducerScope<Unit>.registrarGastos(planId: String): ListenerRegistration =
        firestore.collection(GASTOS_COLLECTION)
            .whereEqualTo("planId", planId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                trySend(Unit)
                val gastos = snapshot?.documents.orEmpty()
                    .mapNotNull { GastoDto.fromData(it.data ?: return@mapNotNull null) }
                    .map { it.toDomain() }
                    .filter { gasto ->
                        evaluarDocumentoRemoto(
                            edicionesPendientes,
                            conflictosPendientes,
                            DocumentoParaSincronizar(
                                GASTOS_COLLECTION,
                                gasto.id,
                                gasto.editedBy,
                                gasto.editedAt,
                                TipoRegistro.GASTO,
                            ),
                        ) { gasto.aSnapshotConflicto() }
                    }
                    .map { it.toEntity() }
                if (gastos.isNotEmpty()) {
                    launch { gastoDao.upsertAll(gastos) }
                }
            }

    private fun ProducerScope<Unit>.registrarIngresos(planId: String): ListenerRegistration =
        firestore.collection(INGRESOS_COLLECTION)
            .whereEqualTo("planId", planId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                trySend(Unit)
                val ingresos = snapshot?.documents.orEmpty()
                    .mapNotNull { IngresoDto.fromData(it.data ?: return@mapNotNull null) }
                    .map { it.toDomain() }
                    .filter { ingreso ->
                        evaluarDocumentoRemoto(
                            edicionesPendientes,
                            conflictosPendientes,
                            DocumentoParaSincronizar(
                                INGRESOS_COLLECTION,
                                ingreso.id,
                                ingreso.editedBy,
                                ingreso.editedAt,
                                TipoRegistro.INGRESO,
                            ),
                        ) { ingreso.aSnapshotConflicto() }
                    }
                    .map { it.toEntity() }
                if (ingresos.isNotEmpty()) {
                    launch { ingresoDao.upsertAll(ingresos) }
                }
            }

    private companion object {
        const val GASTOS_COLLECTION = "gastos"
        const val INGRESOS_COLLECTION = "ingresos"
    }
}

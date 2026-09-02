package com.agoitdev.spenvo.data.remote.sync

import androidx.room.withTransaction
import com.agoitdev.spenvo.data.local.SpenvoDatabase
import com.agoitdev.spenvo.data.local.dao.GastoDao
import com.agoitdev.spenvo.data.local.dao.IngresoDao
import com.agoitdev.spenvo.data.local.mapper.toDomain
import com.agoitdev.spenvo.data.local.mapper.toEntity
import com.agoitdev.spenvo.data.remote.dto.GastoDto
import com.agoitdev.spenvo.data.remote.dto.IngresoDto
import com.agoitdev.spenvo.domain.model.Gasto
import com.agoitdev.spenvo.domain.model.Ingreso
import com.agoitdev.spenvo.domain.sync.ConflictoEdicion
import com.agoitdev.spenvo.domain.sync.DecisionSincronizacion
import com.agoitdev.spenvo.domain.sync.RegistroConflictosPendientes
import com.agoitdev.spenvo.domain.sync.RegistroEdicionesPendientes
import com.agoitdev.spenvo.domain.sync.TipoRegistro
import com.agoitdev.spenvo.domain.sync.aSnapshotConflicto
import com.agoitdev.spenvo.domain.sync.claveRegistro
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.channels.Channel
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
 * Same active-scope-only pattern as CategoriaSincronizador (AGENTS.md rule 3),
 * same one-transaction-per-batch + single-consumer-channel-per-listener shape
 * (ARCH-M501) as its documents-received counterparts.
 */
@Singleton
class MovimientoSincronizador @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val database: SpenvoDatabase,
    private val gastoDao: GastoDao,
    private val ingresoDao: IngresoDao,
    private val registroEdicionesPendientes: RegistroEdicionesPendientes,
    private val registroConflictosPendientes: RegistroConflictosPendientes,
) : MovimientoSincronizacion {

    override fun sincronizar(planId: String): Flow<Unit> = callbackFlow {
        val gastoLotes = Channel<List<Gasto>>(Channel.UNLIMITED)
        val ingresoLotes = Channel<List<Ingreso>>(Channel.UNLIMITED)
        val consumidorGastos = launch {
            for (lote in gastoLotes) {
                procesarSnapshotGastos(
                    database,
                    gastoDao,
                    registroEdicionesPendientes,
                    registroConflictosPendientes,
                    lote,
                )
            }
        }
        val consumidorIngresos = launch {
            for (lote in ingresoLotes) {
                procesarSnapshotIngresos(
                    database,
                    ingresoDao,
                    registroEdicionesPendientes,
                    registroConflictosPendientes,
                    lote,
                )
            }
        }
        val gastosListener = registrarGastos(planId, gastoLotes)
        val ingresosListener = registrarIngresos(planId, ingresoLotes)
        awaitClose {
            gastosListener.remove()
            ingresosListener.remove()
            gastoLotes.close()
            ingresoLotes.close()
            consumidorGastos.cancel()
            consumidorIngresos.cancel()
        }
    }

    private fun ProducerScope<Unit>.registrarGastos(
        planId: String,
        lotes: Channel<List<Gasto>>,
    ): ListenerRegistration =
        firestore.collection(GASTOS_COLLECTION)
            .whereEqualTo("planId", planId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                trySend(Unit)
                lotes.trySend(
                    snapshot?.documents.orEmpty()
                        .mapNotNull { GastoDto.fromData(it.data ?: return@mapNotNull null) }
                        .map { it.toDomain() },
                )
            }

    private fun ProducerScope<Unit>.registrarIngresos(
        planId: String,
        lotes: Channel<List<Ingreso>>,
    ): ListenerRegistration =
        firestore.collection(INGRESOS_COLLECTION)
            .whereEqualTo("planId", planId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                trySend(Unit)
                lotes.trySend(
                    snapshot?.documents.orEmpty()
                        .mapNotNull { IngresoDto.fromData(it.data ?: return@mapNotNull null) }
                        .map { it.toDomain() },
                )
            }

    private companion object {
        const val GASTOS_COLLECTION = "gastos"
        const val INGRESOS_COLLECTION = "ingresos"
    }
}

internal suspend fun procesarSnapshotGastos(
    database: SpenvoDatabase,
    gastoDao: GastoDao,
    registroEdicionesPendientes: RegistroEdicionesPendientes,
    registroConflictosPendientes: RegistroConflictosPendientes,
    gastos: List<Gasto>,
) {
    database.withTransaction {
        val aplicables = gastos.mapNotNull { gasto ->
            val clave = claveRegistro("gastos", gasto.id)
            when (registroEdicionesPendientes.evaluar(clave, gasto.editedBy, gasto.editedAt)) {
                DecisionSincronizacion.APLICAR, DecisionSincronizacion.PROPIA_CONFIRMADA -> gasto.toEntity()
                DecisionSincronizacion.CONFLICTO -> {
                    val local = gastoDao.get(gasto.id)
                    if (local == null) {
                        registroEdicionesPendientes.limpiar(clave)
                        gasto.toEntity()
                    } else {
                        registroConflictosPendientes.registrar(
                            clave,
                            ConflictoEdicion(
                                registroId = gasto.id,
                                tipo = TipoRegistro.GASTO,
                                local = local.toDomain().aSnapshotConflicto(),
                                remoto = gasto.aSnapshotConflicto(),
                            ),
                        )
                        null
                    }
                }
            }
        }
        gastoDao.upsertAll(aplicables)
    }
}

internal suspend fun procesarSnapshotIngresos(
    database: SpenvoDatabase,
    ingresoDao: IngresoDao,
    registroEdicionesPendientes: RegistroEdicionesPendientes,
    registroConflictosPendientes: RegistroConflictosPendientes,
    ingresos: List<Ingreso>,
) {
    database.withTransaction {
        val aplicables = ingresos.mapNotNull { ingreso ->
            val clave = claveRegistro("ingresos", ingreso.id)
            when (registroEdicionesPendientes.evaluar(clave, ingreso.editedBy, ingreso.editedAt)) {
                DecisionSincronizacion.APLICAR, DecisionSincronizacion.PROPIA_CONFIRMADA -> ingreso.toEntity()
                DecisionSincronizacion.CONFLICTO -> {
                    val local = ingresoDao.get(ingreso.id)
                    if (local == null) {
                        registroEdicionesPendientes.limpiar(clave)
                        ingreso.toEntity()
                    } else {
                        registroConflictosPendientes.registrar(
                            clave,
                            ConflictoEdicion(
                                registroId = ingreso.id,
                                tipo = TipoRegistro.INGRESO,
                                local = local.toDomain().aSnapshotConflicto(),
                                remoto = ingreso.aSnapshotConflicto(),
                            ),
                        )
                        null
                    }
                }
            }
        }
        ingresoDao.upsertAll(aplicables)
    }
}

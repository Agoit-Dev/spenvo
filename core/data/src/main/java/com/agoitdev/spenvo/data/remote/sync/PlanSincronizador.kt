package com.agoitdev.spenvo.data.remote.sync

import androidx.room.withTransaction
import com.agoitdev.spenvo.data.local.SpenvoDatabase
import com.agoitdev.spenvo.data.local.dao.AccesoPlanDao
import com.agoitdev.spenvo.data.local.dao.PlanFinancieroDao
import com.agoitdev.spenvo.data.local.mapper.toDomain
import com.agoitdev.spenvo.data.local.mapper.toEntity
import com.agoitdev.spenvo.data.remote.dto.AccesoPlanDto
import com.agoitdev.spenvo.data.remote.dto.PlanFinancieroDto
import com.agoitdev.spenvo.domain.model.PlanFinanciero
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
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch

interface PlanSincronizacion {
    fun sincronizar(usuarioId: String): Flow<Unit>
}

/**
 * Syncs the user's plans and accesses from Firestore into Room while it is
 * collected. Listens on the user's accesses and attaches a snapshot listener
 * per active plan document, so remote plan edits (not only access changes) are
 * reflected in Room. Listeners only live during collection (active scope), per
 * AGENTS.md rule 3. Same one-transaction-per-batch shape as the other two
 * sincronizadores (ARCH-M501) — a single plan document processed as a
 * one-element list.
 */
@Singleton
class PlanSincronizador @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val database: SpenvoDatabase,
    private val planDao: PlanFinancieroDao,
    private val accesoDao: AccesoPlanDao,
    private val registroEdicionesPendientes: RegistroEdicionesPendientes,
    private val registroConflictosPendientes: RegistroConflictosPendientes,
) : PlanSincronizacion {

    override fun sincronizar(usuarioId: String): Flow<Unit> = callbackFlow {
        val planListeners = mutableMapOf<String, ListenerRegistration>()
        val planLotes = Channel<List<PlanFinanciero>>(Channel.UNLIMITED)
        val consumidor = launch {
            for (lote in planLotes) {
                procesarSnapshotPlanes(
                    database,
                    planDao,
                    registroEdicionesPendientes,
                    registroConflictosPendientes,
                    lote,
                )
            }
        }
        val accesoListener = firestore.collection(ACCESO_COLLECTION)
            .whereEqualTo("usuarioId", usuarioId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                trySend(Unit)
                val accesos = snapshot?.documents.orEmpty()
                    .mapNotNull { AccesoPlanDto.fromData(it.data ?: return@mapNotNull null) }
                val planIds = accesos.map { it.planId }.toSet()
                accesos.forEach { acceso ->
                    launch { accesoDao.upsert(acceso.toDomain().toEntity()) }
                }
                planIds.forEach { planId ->
                    if (!planListeners.containsKey(planId)) {
                        planListeners[planId] = firestore.collection(PLANES_COLLECTION)
                            .document(planId)
                            .addSnapshotListener { doc, planError ->
                                if (planError == null && doc != null && doc.data != null) {
                                    val planDto = PlanFinancieroDto.fromData(doc.data ?: emptyMap())
                                    if (planDto != null) planLotes.trySend(listOf(planDto.toDomain()))
                                }
                            }
                    }
                }
                planListeners.keys.filter { it !in planIds }.forEach { planId ->
                    planListeners.remove(planId)?.remove()
                }
            }
        awaitClose {
            planListeners.values.forEach { it.remove() }
            accesoListener.remove()
            planLotes.close()
            consumidor.cancel()
        }
    }

    private companion object {
        const val ACCESO_COLLECTION = "acceso_plan_financiero"
        const val PLANES_COLLECTION = "planes_financieros"
    }
}

internal suspend fun procesarSnapshotPlanes(
    database: SpenvoDatabase,
    planDao: PlanFinancieroDao,
    registroEdicionesPendientes: RegistroEdicionesPendientes,
    registroConflictosPendientes: RegistroConflictosPendientes,
    planes: List<PlanFinanciero>,
) {
    database.withTransaction {
        val aplicables = planes.mapNotNull { plan ->
            val clave = claveRegistro("planes_financieros", plan.id)
            when (registroEdicionesPendientes.evaluar(clave, plan.editedBy, plan.editedAt)) {
                DecisionSincronizacion.APLICAR, DecisionSincronizacion.PROPIA_CONFIRMADA -> plan.toEntity()
                DecisionSincronizacion.CONFLICTO -> {
                    val local = planDao.get(plan.id)
                    if (local == null) {
                        registroEdicionesPendientes.limpiar(clave)
                        plan.toEntity()
                    } else {
                        registroConflictosPendientes.registrar(
                            clave,
                            ConflictoEdicion(
                                registroId = plan.id,
                                tipo = TipoRegistro.PLAN,
                                local = local.toDomain().aSnapshotConflicto(),
                                remoto = plan.aSnapshotConflicto(),
                            ),
                        )
                        null
                    }
                }
            }
        }
        planDao.upsertAll(aplicables)
    }
}

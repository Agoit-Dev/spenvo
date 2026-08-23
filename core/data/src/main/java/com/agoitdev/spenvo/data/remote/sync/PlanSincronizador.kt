package com.agoitdev.spenvo.data.remote.sync

import com.agoitdev.spenvo.data.local.dao.AccesoPlanDao
import com.agoitdev.spenvo.data.local.dao.PlanFinancieroDao
import com.agoitdev.spenvo.data.local.mapper.toEntity
import com.agoitdev.spenvo.data.remote.dto.AccesoPlanDto
import com.agoitdev.spenvo.data.remote.dto.PlanFinancieroDto
import com.agoitdev.spenvo.domain.sync.ConflictosPendientes
import com.agoitdev.spenvo.domain.sync.EdicionesPendientes
import com.agoitdev.spenvo.domain.sync.TipoRegistro
import com.agoitdev.spenvo.domain.sync.aSnapshotConflicto
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch

/**
 * Syncs the user's plans and accesses from Firestore into Room while it is
 * collected. Listens on the user's accesses and attaches a snapshot listener
 * per active plan document, so remote plan edits (not only access changes) are
 * reflected in Room. Listeners only live during collection (active scope), per
 * AGENTS.md rule 3. A plan document flagged as a genuine conflict (Slice 4) is
 * held back from Room and registered in [conflictosPendientes] instead.
 */
@Singleton
class PlanSincronizador @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val planDao: PlanFinancieroDao,
    private val accesoDao: AccesoPlanDao,
    private val edicionesPendientes: EdicionesPendientes,
    private val conflictosPendientes: ConflictosPendientes,
) {

    fun sincronizar(usuarioId: String): Flow<Unit> = callbackFlow {
        val planListeners = mutableMapOf<String, ListenerRegistration>()
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
                // Listener por plan activo: refleja ediciones remotas del plan.
                planIds.forEach { planId ->
                    if (!planListeners.containsKey(planId)) {
                        planListeners[planId] = firestore.collection(PLANES_COLLECTION)
                            .document(planId)
                            .addSnapshotListener { doc, planError ->
                                if (planError == null && doc != null && doc.data != null) {
                                    val planDto = PlanFinancieroDto.fromData(doc.data ?: emptyMap())
                                    if (planDto != null) {
                                        val plan = planDto.toDomain()
                                        val aplicar = evaluarDocumentoRemoto(
                                            edicionesPendientes,
                                            conflictosPendientes,
                                            DocumentoParaSincronizar(
                                                PLANES_COLLECTION,
                                                plan.id,
                                                plan.editedBy,
                                                plan.editedAt,
                                                TipoRegistro.PLAN,
                                            ),
                                        ) { plan.aSnapshotConflicto() }
                                        if (aplicar) {
                                            launch { planDao.upsert(plan.toEntity()) }
                                        }
                                    }
                                }
                            }
                    }
                }
                // Retira listeners de planes que ya no pertenecen al usuario.
                planListeners.keys.filter { it !in planIds }.forEach { planId ->
                    planListeners.remove(planId)?.remove()
                }
            }
        awaitClose {
            planListeners.values.forEach { it.remove() }
            accesoListener.remove()
        }
    }

    private companion object {
        const val ACCESO_COLLECTION = "acceso_plan_financiero"
        const val PLANES_COLLECTION = "planes_financieros"
    }
}

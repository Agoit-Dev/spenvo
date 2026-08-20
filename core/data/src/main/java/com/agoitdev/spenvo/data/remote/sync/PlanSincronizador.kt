package com.agoitdev.spenvo.data.remote.sync

import com.agoitdev.spenvo.data.local.dao.AccesoPlanDao
import com.agoitdev.spenvo.data.local.dao.PlanFinancieroDao
import com.agoitdev.spenvo.data.local.mapper.toEntity
import com.agoitdev.spenvo.data.remote.await
import com.agoitdev.spenvo.data.remote.dto.AccesoPlanDto
import com.agoitdev.spenvo.data.remote.dto.PlanFinancieroDto
import com.google.firebase.firestore.FirebaseFirestore
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch

/**
 * Syncs the user's plans and accesses from Firestore into Room while it is
 * collected. Snapshot listeners only live during collection (active scope),
 * per AGENTS.md rule 3.
 */
@Singleton
class PlanSincronizador @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val planDao: PlanFinancieroDao,
    private val accesoDao: AccesoPlanDao,
) {

    fun sincronizar(usuarioId: String): Flow<Unit> = callbackFlow {
        val listener = firestore.collection(ACCESO_COLLECTION)
            .whereEqualTo("usuarioId", usuarioId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                val accesos = snapshot?.documents.orEmpty()
                    .mapNotNull { AccesoPlanDto.fromData(it.data ?: return@mapNotNull null) }
                trySend(Unit)
                accesos.forEach { acceso ->
                    launch {
                        accesoDao.upsert(acceso.toDomain().toEntity())
                        val planDoc = firestore.collection(PLANES_COLLECTION)
                            .document(acceso.planId)
                            .get()
                            .await()
                        val plan = PlanFinancieroDto.fromData(planDoc.data ?: emptyMap())
                        if (plan != null) {
                            planDao.upsert(plan.toDomain().toEntity())
                        }
                    }
                }
            }
        awaitClose { listener.remove() }
    }

    private companion object {
        const val ACCESO_COLLECTION = "acceso_plan_financiero"
        const val PLANES_COLLECTION = "planes_financieros"
    }
}

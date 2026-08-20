package com.agoitdev.spenvo.data.remote.repository

import com.agoitdev.spenvo.data.local.dao.AccesoPlanDao
import com.agoitdev.spenvo.data.local.mapper.toDomain
import com.agoitdev.spenvo.data.local.mapper.toEntity
import com.agoitdev.spenvo.data.remote.dto.AccesoPlanDto
import com.agoitdev.spenvo.data.remote.await
import com.agoitdev.spenvo.domain.model.AccesoPlan
import com.agoitdev.spenvo.domain.model.InvitacionEstado
import com.agoitdev.spenvo.domain.repository.AccesoPlanRepository
import com.google.firebase.firestore.FirebaseFirestore
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Singleton
class FirebaseAccesoPlanRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val accesoDao: AccesoPlanDao,
) : AccesoPlanRepository {

    override fun observarAccesosDelUsuario(usuarioId: String): Flow<List<AccesoPlan>> =
        accesoDao.observeByUsuario(usuarioId).map { entities ->
            entities.map { it.toDomain() }
        }

    override fun observarAccesosDelPlan(planId: String): Flow<List<AccesoPlan>> =
        accesoDao.observeByPlan(planId).map { entities ->
            entities.map { it.toDomain() }
        }

    override suspend fun invitarMiembro(acceso: AccesoPlan) {
        val dto = AccesoPlanDto.fromDomain(acceso)
        firestore.collection(ACCESO_COLLECTION)
            .document(accesoDocId(acceso.usuarioId, acceso.planId))
            .set(dto.toMap())
            .await()
        accesoDao.upsert(acceso.toEntity())
    }

    override suspend fun aceptarInvitacion(usuarioId: String, planId: String) {
        val acceso = accesoDao.get(usuarioId, planId)?.toDomain() ?: return
        val actualizado = acceso.copy(
            invitacionEstado = InvitacionEstado.ACEPTADA,
            updatedAt = Instant.now(),
        )
        val dto = AccesoPlanDto.fromDomain(actualizado)
        firestore.collection(ACCESO_COLLECTION)
            .document(accesoDocId(usuarioId, planId))
            .set(dto.toMap())
            .await()
        accesoDao.upsert(actualizado.toEntity())
    }

    private companion object {
        const val ACCESO_COLLECTION = "acceso_plan_financiero"

        fun accesoDocId(usuarioId: String, planId: String): String = "${usuarioId}_$planId"
    }
}

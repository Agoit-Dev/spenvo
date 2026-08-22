package com.agoitdev.spenvo.data.remote.repository

import com.agoitdev.spenvo.data.local.dao.PlanFinancieroDao
import com.agoitdev.spenvo.data.local.mapper.toDomain
import com.agoitdev.spenvo.data.local.mapper.toEntity
import com.agoitdev.spenvo.data.remote.dto.PlanFinancieroDto
import com.agoitdev.spenvo.data.remote.await
import com.agoitdev.spenvo.domain.model.PlanFinanciero
import com.agoitdev.spenvo.domain.repository.PlanFinancieroRepository
import com.google.firebase.firestore.FirebaseFirestore
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Optimistic Room-first writes: Room updates immediately, then Firestore. A
 * permanent Firestore error rolls Room back to the previous snapshot (see
 * `data-consistency.md` write contract).
 */
@Singleton
class FirebasePlanFinancieroRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val planDao: PlanFinancieroDao,
) : PlanFinancieroRepository {

    override fun observarPlanesDelUsuario(usuarioId: String): Flow<List<PlanFinanciero>> =
        planDao.observeByUsuario(usuarioId).map { entities ->
            entities.map { it.toDomain() }
        }

    override fun observarPlan(planId: String): Flow<PlanFinanciero?> =
        planDao.observe(planId).map { it?.toDomain() }

    @Suppress("TooGenericExceptionCaught")
    override suspend fun crearPlan(plan: PlanFinanciero) {
        planDao.upsert(plan.toEntity())
        try {
            persistRemoto(plan)
        } catch (e: Exception) {
            planDao.delete(plan.id)
            throw e
        }
    }

    @Suppress("TooGenericExceptionCaught")
    override suspend fun actualizarPlan(plan: PlanFinanciero) {
        val previo = planDao.get(plan.id)
        planDao.upsert(plan.toEntity())
        try {
            persistRemoto(plan)
        } catch (e: Exception) {
            if (previo != null) planDao.upsert(previo) else planDao.delete(plan.id)
            throw e
        }
    }

    private suspend fun persistRemoto(plan: PlanFinanciero) {
        val dto = PlanFinancieroDto.fromDomain(plan)
        firestore.collection(PLANES_COLLECTION)
            .document(plan.id)
            .set(dto.toMap())
            .await()
    }

    private companion object {
        const val PLANES_COLLECTION = "planes_financieros"
    }
}

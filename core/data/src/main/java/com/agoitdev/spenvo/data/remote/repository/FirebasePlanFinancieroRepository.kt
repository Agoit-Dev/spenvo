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

    override suspend fun crearPlan(plan: PlanFinanciero) {
        val dto = PlanFinancieroDto.fromDomain(plan)
        firestore.collection(PLANES_COLLECTION)
            .document(plan.id)
            .set(dto.toMap())
            .await()
        planDao.upsert(plan.toEntity())
    }

    override suspend fun actualizarPlan(plan: PlanFinanciero) {
        val dto = PlanFinancieroDto.fromDomain(plan)
        firestore.collection(PLANES_COLLECTION)
            .document(plan.id)
            .set(dto.toMap())
            .await()
        planDao.upsert(plan.toEntity())
    }

    private companion object {
        const val PLANES_COLLECTION = "planes_financieros"
    }
}

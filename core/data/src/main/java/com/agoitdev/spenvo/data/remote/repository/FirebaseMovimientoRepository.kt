package com.agoitdev.spenvo.data.remote.repository

import com.agoitdev.spenvo.data.local.dao.GastoDao
import com.agoitdev.spenvo.data.local.dao.IngresoDao
import com.agoitdev.spenvo.data.local.mapper.toDomain
import com.agoitdev.spenvo.data.local.mapper.toEntity
import com.agoitdev.spenvo.data.remote.await
import com.agoitdev.spenvo.data.remote.dto.GastoDto
import com.agoitdev.spenvo.data.remote.dto.IngresoDto
import com.agoitdev.spenvo.domain.model.Gasto
import com.agoitdev.spenvo.domain.model.Ingreso
import com.agoitdev.spenvo.domain.repository.MovimientoRepository
import com.google.firebase.firestore.FirebaseFirestore
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Optimistic Room-first writes: Room updates immediately, then Firestore. A
 * permanent Firestore error rolls Room back (delete, since movimientos are
 * only created today, never updated) - see `data-consistency.md`.
 */
@Singleton
class FirebaseMovimientoRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val gastoDao: GastoDao,
    private val ingresoDao: IngresoDao,
) : MovimientoRepository {

    override fun observeGastos(planId: String): Flow<List<Gasto>> =
        gastoDao.observeByPlan(planId).map { entities -> entities.map { it.toDomain() } }

    override fun observeIngresos(planId: String): Flow<List<Ingreso>> =
        ingresoDao.observeByPlan(planId).map { entities -> entities.map { it.toDomain() } }

    @Suppress("TooGenericExceptionCaught")
    override suspend fun addGasto(gasto: Gasto) {
        gastoDao.upsert(gasto.toEntity())
        try {
            firestore.collection(GASTOS_COLLECTION)
                .document(gasto.id)
                .set(GastoDto.fromDomain(gasto).toMap())
                .await()
        } catch (e: Exception) {
            gastoDao.delete(gasto.id)
            throw e
        }
    }

    @Suppress("TooGenericExceptionCaught")
    override suspend fun addIngreso(ingreso: Ingreso) {
        ingresoDao.upsert(ingreso.toEntity())
        try {
            firestore.collection(INGRESOS_COLLECTION)
                .document(ingreso.id)
                .set(IngresoDto.fromDomain(ingreso).toMap())
                .await()
        } catch (e: Exception) {
            ingresoDao.delete(ingreso.id)
            throw e
        }
    }

    private companion object {
        const val GASTOS_COLLECTION = "gastos"
        const val INGRESOS_COLLECTION = "ingresos"
    }
}

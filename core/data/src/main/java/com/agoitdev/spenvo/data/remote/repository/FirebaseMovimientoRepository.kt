package com.agoitdev.spenvo.data.remote.repository

import com.agoitdev.spenvo.data.local.dao.GastoDao
import com.agoitdev.spenvo.data.local.dao.IngresoDao
import com.agoitdev.spenvo.data.local.entity.GastoEntity
import com.agoitdev.spenvo.data.local.entity.IngresoEntity
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
 * permanent Firestore error rolls Room back to the previous snapshot (see
 * `data-consistency.md` write contract).
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

    @Suppress("TooGenericExceptionCaught")
    override suspend fun actualizarGasto(gasto: Gasto) {
        val previo = gastoDao.get(gasto.id)
        gastoDao.upsert(gasto.toEntity())
        try {
            persistRemotoGasto(firestore, gasto)
        } catch (e: Exception) {
            restaurarGasto(gastoDao, previo, gasto.id)
            throw e
        }
    }

    @Suppress("TooGenericExceptionCaught")
    override suspend fun eliminarGasto(gasto: Gasto) {
        val previo = gastoDao.get(gasto.id)
        gastoDao.upsert(gasto.toEntity())
        try {
            persistRemotoGasto(firestore, gasto)
        } catch (e: Exception) {
            restaurarGasto(gastoDao, previo, gasto.id)
            throw e
        }
    }

    @Suppress("TooGenericExceptionCaught")
    override suspend fun actualizarIngreso(ingreso: Ingreso) {
        val previo = ingresoDao.get(ingreso.id)
        ingresoDao.upsert(ingreso.toEntity())
        try {
            persistRemotoIngreso(firestore, ingreso)
        } catch (e: Exception) {
            restaurarIngreso(ingresoDao, previo, ingreso.id)
            throw e
        }
    }

    @Suppress("TooGenericExceptionCaught")
    override suspend fun eliminarIngreso(ingreso: Ingreso) {
        val previo = ingresoDao.get(ingreso.id)
        ingresoDao.upsert(ingreso.toEntity())
        try {
            persistRemotoIngreso(firestore, ingreso)
        } catch (e: Exception) {
            restaurarIngreso(ingresoDao, previo, ingreso.id)
            throw e
        }
    }

}

private const val GASTOS_COLLECTION = "gastos"
private const val INGRESOS_COLLECTION = "ingresos"

private suspend fun persistRemotoGasto(firestore: FirebaseFirestore, gasto: Gasto) {
    firestore.collection(GASTOS_COLLECTION)
        .document(gasto.id)
        .set(GastoDto.fromDomain(gasto).toMap())
        .await()
}

private suspend fun persistRemotoIngreso(firestore: FirebaseFirestore, ingreso: Ingreso) {
    firestore.collection(INGRESOS_COLLECTION)
        .document(ingreso.id)
        .set(IngresoDto.fromDomain(ingreso).toMap())
        .await()
}

private suspend fun restaurarGasto(gastoDao: GastoDao, previo: GastoEntity?, id: String) {
    if (previo != null) gastoDao.upsert(previo) else gastoDao.delete(id)
}

private suspend fun restaurarIngreso(ingresoDao: IngresoDao, previo: IngresoEntity?, id: String) {
    if (previo != null) ingresoDao.upsert(previo) else ingresoDao.delete(id)
}

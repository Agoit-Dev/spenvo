package com.agoitdev.spenvo.data.remote.repository

import androidx.room.withTransaction
import com.agoitdev.spenvo.data.local.SpenvoDatabase
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
import com.agoitdev.spenvo.domain.sync.ConflictoEdicion
import com.agoitdev.spenvo.domain.sync.RegistroConflictosPendientes
import com.agoitdev.spenvo.domain.sync.RegistroEdicionesPendientes
import com.agoitdev.spenvo.domain.sync.TipoRegistro
import com.agoitdev.spenvo.domain.sync.claveRegistro
import com.google.firebase.firestore.FirebaseFirestore
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Optimistic Room-first writes: Room updates immediately, then Firestore. A
 * permanent Firestore error rolls Room back to the previous snapshot (see
 * `data-consistency.md` write contract). Update/delete also register an
 * unconfirmed pending edit (Slice 4 conflict detection) at the point `previo`
 * is read, for free — both land in one Room transaction (ARCH-M501).
 */
@Singleton
@Suppress("TooManyFunctions")
class FirebaseMovimientoRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val database: SpenvoDatabase,
    private val gastoDao: GastoDao,
    private val ingresoDao: IngresoDao,
    private val registroEdicionesPendientes: RegistroEdicionesPendientes,
    private val registroConflictosPendientes: RegistroConflictosPendientes,
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
        val previo = escribirGasto(gasto)
        try {
            persistRemotoGasto(gasto)
        } catch (e: Exception) {
            rollbackGasto(gasto.id, previo)
            throw e
        }
    }

    @Suppress("TooGenericExceptionCaught")
    override suspend fun eliminarGasto(gasto: Gasto) {
        val previo = escribirGasto(gasto)
        try {
            persistRemotoGasto(gasto)
        } catch (e: Exception) {
            rollbackGasto(gasto.id, previo)
            throw e
        }
    }

    @Suppress("TooGenericExceptionCaught")
    override suspend fun actualizarIngreso(ingreso: Ingreso) {
        val previo = escribirIngreso(ingreso)
        try {
            persistRemotoIngreso(ingreso)
        } catch (e: Exception) {
            rollbackIngreso(ingreso.id, previo)
            throw e
        }
    }

    @Suppress("TooGenericExceptionCaught")
    override suspend fun eliminarIngreso(ingreso: Ingreso) {
        val previo = escribirIngreso(ingreso)
        try {
            persistRemotoIngreso(ingreso)
        } catch (e: Exception) {
            rollbackIngreso(ingreso.id, previo)
            throw e
        }
    }

    override suspend fun aplicarGastoRemoto(id: String) {
        val data = firestore.collection(GASTOS_COLLECTION).document(id).get().await().data ?: return
        gastoDao.upsert(GastoDto.fromData(data)?.toDomain()?.toEntity() ?: return)
    }

    override suspend fun aplicarIngresoRemoto(id: String) {
        val data = firestore.collection(INGRESOS_COLLECTION).document(id).get().await().data ?: return
        ingresoDao.upsert(IngresoDto.fromData(data)?.toDomain()?.toEntity() ?: return)
    }

    @Suppress("TooGenericExceptionCaught")
    override suspend fun resolverConflictoGastoUsandoRemoto(id: String, clave: String) {
        val data = firestore.collection(GASTOS_COLLECTION).document(id).get().await().data
            ?: error("El movimiento remoto ya no existe")
        val remoto = GastoDto.fromData(data)?.toDomain()
            ?: error("El movimiento remoto no es válido")
        database.withTransaction {
            gastoDao.upsert(remoto.toEntity())
            registroConflictosPendientes.resolver(clave)
        }
    }

    @Suppress("TooGenericExceptionCaught")
    override suspend fun resolverConflictoIngresoUsandoRemoto(id: String, clave: String) {
        val data = firestore.collection(INGRESOS_COLLECTION).document(id).get().await().data
            ?: error("El movimiento remoto ya no existe")
        val remoto = IngresoDto.fromData(data)?.toDomain()
            ?: error("El movimiento remoto no es válido")
        database.withTransaction {
            ingresoDao.upsert(remoto.toEntity())
            registroConflictosPendientes.resolver(clave)
        }
    }

    @Suppress("TooGenericExceptionCaught")
    override suspend fun resolverConflictoGastoUsandoLocal(gasto: Gasto, clave: String) {
        var conflictoPrevio: ConflictoEdicion? = null
        var entidadPrevia: GastoEntity? = null
        database.withTransaction {
            entidadPrevia = gastoDao.get(gasto.id)
            conflictoPrevio = registroConflictosPendientes.conflictoPara(clave)
            registroEdicionesPendientes.registrarSiCorresponde(
                clave, gasto.editedBy, entidadPrevia?.editedAt, gasto.editedAt, TipoRegistro.GASTO,
            )
            gastoDao.upsert(gasto.toEntity())
            registroConflictosPendientes.resolver(clave)
        }
        try {
            persistRemotoGasto(gasto)
        } catch (e: Exception) {
            database.withTransaction {
                registroEdicionesPendientes.limpiar(clave)
                entidadPrevia?.let { gastoDao.upsert(it) } ?: gastoDao.delete(gasto.id)
                conflictoPrevio?.let { registroConflictosPendientes.registrar(clave, it) }
            }
            throw e
        }
    }

    @Suppress("TooGenericExceptionCaught")
    override suspend fun resolverConflictoIngresoUsandoLocal(ingreso: Ingreso, clave: String) {
        var conflictoPrevio: ConflictoEdicion? = null
        var entidadPrevia: IngresoEntity? = null
        database.withTransaction {
            entidadPrevia = ingresoDao.get(ingreso.id)
            conflictoPrevio = registroConflictosPendientes.conflictoPara(clave)
            registroEdicionesPendientes.registrarSiCorresponde(
                clave, ingreso.editedBy, entidadPrevia?.editedAt, ingreso.editedAt, TipoRegistro.INGRESO,
            )
            ingresoDao.upsert(ingreso.toEntity())
            registroConflictosPendientes.resolver(clave)
        }
        try {
            persistRemotoIngreso(ingreso)
        } catch (e: Exception) {
            database.withTransaction {
                registroEdicionesPendientes.limpiar(clave)
                entidadPrevia?.let { ingresoDao.upsert(it) } ?: ingresoDao.delete(ingreso.id)
                conflictoPrevio?.let { registroConflictosPendientes.registrar(clave, it) }
            }
            throw e
        }
    }

    private suspend fun escribirGasto(gasto: Gasto): GastoEntity? = database.withTransaction {
        val previo = gastoDao.get(gasto.id)
        registroEdicionesPendientes.registrarSiCorresponde(
            clave = claveRegistro(GASTOS_COLLECTION, gasto.id),
            editorId = gasto.editedBy,
            base = previo?.editedAt,
            miEditedAt = gasto.editedAt,
            tipo = TipoRegistro.GASTO,
        )
        gastoDao.upsert(gasto.toEntity())
        previo
    }

    private suspend fun rollbackGasto(id: String, previo: GastoEntity?) = database.withTransaction {
        registroEdicionesPendientes.limpiar(claveRegistro(GASTOS_COLLECTION, id))
        if (previo != null) gastoDao.upsert(previo) else gastoDao.delete(id)
    }

    private suspend fun escribirIngreso(ingreso: Ingreso): IngresoEntity? = database.withTransaction {
        val previo = ingresoDao.get(ingreso.id)
        registroEdicionesPendientes.registrarSiCorresponde(
            clave = claveRegistro(INGRESOS_COLLECTION, ingreso.id),
            editorId = ingreso.editedBy,
            base = previo?.editedAt,
            miEditedAt = ingreso.editedAt,
            tipo = TipoRegistro.INGRESO,
        )
        ingresoDao.upsert(ingreso.toEntity())
        previo
    }

    private suspend fun rollbackIngreso(id: String, previo: IngresoEntity?) = database.withTransaction {
        registroEdicionesPendientes.limpiar(claveRegistro(INGRESOS_COLLECTION, id))
        if (previo != null) ingresoDao.upsert(previo) else ingresoDao.delete(id)
    }

    private suspend fun persistRemotoGasto(gasto: Gasto) {
        firestore.collection(GASTOS_COLLECTION)
            .document(gasto.id)
            .set(GastoDto.fromDomain(gasto).toMap())
            .await()
    }

    private suspend fun persistRemotoIngreso(ingreso: Ingreso) {
        firestore.collection(INGRESOS_COLLECTION)
            .document(ingreso.id)
            .set(IngresoDto.fromDomain(ingreso).toMap())
            .await()
    }

    private companion object {
        const val GASTOS_COLLECTION = "gastos"
        const val INGRESOS_COLLECTION = "ingresos"
    }
}

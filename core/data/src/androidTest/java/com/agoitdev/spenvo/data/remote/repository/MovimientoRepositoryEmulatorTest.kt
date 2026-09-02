package com.agoitdev.spenvo.data.remote.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.agoitdev.spenvo.data.local.SpenvoDatabase
import com.agoitdev.spenvo.data.local.dao.GastoDao
import com.agoitdev.spenvo.data.local.dao.IngresoDao
import com.agoitdev.spenvo.data.local.mapper.toDomain
import com.agoitdev.spenvo.data.local.mapper.toEntity
import com.agoitdev.spenvo.data.remote.await
import com.agoitdev.spenvo.data.remote.dto.GastoDto
import com.agoitdev.spenvo.data.remote.dto.IngresoDto
import com.agoitdev.spenvo.domain.model.Gasto
import com.agoitdev.spenvo.domain.model.Ingreso
import com.agoitdev.spenvo.domain.model.Monto
import com.agoitdev.spenvo.data.remote.sync.RegistroConflictosPendientesRoom
import com.agoitdev.spenvo.data.remote.sync.RegistroEdicionesPendientesRoom
import com.agoitdev.spenvo.data.remote.sync.procesarSnapshotGastos
import com.agoitdev.spenvo.data.remote.sync.procesarSnapshotIngresos
import com.agoitdev.spenvo.domain.sync.ConflictoEdicion
import com.agoitdev.spenvo.domain.sync.TipoRegistro
import com.agoitdev.spenvo.domain.sync.aSnapshotConflicto
import com.agoitdev.spenvo.domain.sync.claveRegistro
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.firestore.FirebaseFirestore
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Tests the movimiento repository (optimistic Room-first) update/delete paths,
 * including the get-before-upsert rollback, against the local Firestore
 * Emulator (see `firebase.json`, port 8081). Start the emulator before
 * running: `firebase emulators:start --only firestore --project spenvo-dev`.
 */
@RunWith(AndroidJUnit4::class)
class MovimientoRepositoryEmulatorTest {

    private lateinit var db: SpenvoDatabase
    private lateinit var gastoDao: GastoDao
    private lateinit var ingresoDao: IngresoDao
    private lateinit var firestore: FirebaseFirestore
    private lateinit var repo: FirebaseMovimientoRepository
    private lateinit var registroEdicionesPendientes: RegistroEdicionesPendientesRoom
    private lateinit var registroConflictosPendientes: RegistroConflictosPendientesRoom

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val options = FirebaseOptions.Builder()
            .setApplicationId("com.agoitdev.spenvo")
            .setApiKey("test-api-key")
            .setProjectId("spenvo-dev")
            .build()
        val app = FirebaseApp.initializeApp(
            context,
            options,
            "emulator-movimientos-${System.currentTimeMillis()}",
        )!!
        firestore = FirebaseFirestore.getInstance(app)
        firestore.useEmulator("10.0.2.2", 8081)
        db = Room.inMemoryDatabaseBuilder(context, SpenvoDatabase::class.java).build()
        gastoDao = db.gastoDao()
        ingresoDao = db.ingresoDao()
        registroEdicionesPendientes = RegistroEdicionesPendientesRoom(db.edicionPendienteDao())
        registroConflictosPendientes = RegistroConflictosPendientesRoom(db.conflictoEdicionDao())
        repo = FirebaseMovimientoRepository(
            firestore,
            db,
            gastoDao,
            ingresoDao,
            registroEdicionesPendientes,
            registroConflictosPendientes,
        )
    }

    @After
    fun teardown() {
        db.close()
    }

    private fun gasto(id: String, monto: Long = 1000) = Gasto(
        id = id,
        planId = "p1",
        categoriaId = "c1",
        monto = Monto(monto),
        fecha = LocalDate.of(2026, 8, 20),
        creadoPor = "user-1",
    )

    private fun ingreso(id: String, monto: Long = 1000) = Ingreso(
        id = id,
        planId = "p1",
        categoriaId = "c1",
        monto = Monto(monto),
        fecha = LocalDate.of(2026, 8, 20),
        creadoPor = "user-1",
    )

    @Test
    fun actualizarGasto_escribe_firestore_y_room() = runBlocking {
        gastoDao.upsert(gasto("g1").toEntity())

        repo.actualizarGasto(gasto("g1", monto = 5000).copy(editedBy = "user-2"))

        val doc = firestore.collection("gastos").document("g1").get().await()
        assertEquals(5000L, (doc.data?.get("montoUnidadesMenores") as? Number)?.toLong())
        assertEquals(5000L, gastoDao.get("g1")?.montoUnidadesMenores)
    }

    @Test
    fun actualizarGasto_registra_edicion_pendiente_para_deteccion_de_conflictos() = runBlocking {
        gastoDao.upsert(gasto("g1").toEntity())

        repo.actualizarGasto(gasto("g1", monto = 5000).copy(editedBy = "user-2"))

        val pendiente = db.edicionPendienteDao().get("gastos:g1")
        assertEquals("user-2", pendiente?.editorId)
    }

    @Test
    fun eliminarGasto_registra_edicion_pendiente_para_deteccion_de_conflictos() = runBlocking {
        gastoDao.upsert(gasto("g1").toEntity())

        repo.eliminarGasto(gasto("g1").copy(editedBy = "user-2", deletedAt = java.time.Instant.now()))

        val pendiente = db.edicionPendienteDao().get("gastos:g1")
        assertEquals("user-2", pendiente?.editorId)
    }

    @Test
    fun actualizarGasto_error_permanente_rollback_room() = runBlocking {
        val id = "g/invalido"
        gastoDao.upsert(gasto(id, monto = 1000).toEntity())

        var lanzado = false
        try {
            repo.actualizarGasto(gasto(id, monto = 9999))
        } catch (e: Exception) {
            lanzado = true
        }

        assertTrue(lanzado)
        assertEquals(1000L, gastoDao.get(id)?.montoUnidadesMenores)
    }

    @Test
    fun eliminarGasto_marca_deletedAt_en_firestore_y_room() = runBlocking {
        gastoDao.upsert(gasto("g1").toEntity())

        repo.eliminarGasto(gasto("g1").copy(deletedAt = java.time.Instant.now()))

        val doc = firestore.collection("gastos").document("g1").get().await()
        assertTrue(doc.data?.get("deletedAt") != null)
        assertTrue(gastoDao.get("g1")?.deletedAt != null)
    }

    @Test
    fun eliminarGasto_error_permanente_rollback_room() = runBlocking {
        val id = "g/invalido"
        gastoDao.upsert(gasto(id, monto = 1000).toEntity())

        var lanzado = false
        try {
            repo.eliminarGasto(gasto(id).copy(deletedAt = java.time.Instant.now()))
        } catch (e: Exception) {
            lanzado = true
        }

        assertTrue(lanzado)
        assertEquals(null, gastoDao.get(id)?.deletedAt)
    }

    @Test
    fun actualizarIngreso_escribe_firestore_y_room() = runBlocking {
        ingresoDao.upsert(ingreso("i1").toEntity())

        repo.actualizarIngreso(ingreso("i1", monto = 7000).copy(editedBy = "user-2"))

        val doc = firestore.collection("ingresos").document("i1").get().await()
        assertEquals(7000L, (doc.data?.get("montoUnidadesMenores") as? Number)?.toLong())
        assertEquals(7000L, ingresoDao.get("i1")?.montoUnidadesMenores)
    }

    @Test
    fun actualizarIngreso_error_permanente_rollback_room() = runBlocking {
        val id = "i/invalido"
        ingresoDao.upsert(ingreso(id, monto = 1000).toEntity())

        var lanzado = false
        try {
            repo.actualizarIngreso(ingreso(id, monto = 9999))
        } catch (e: Exception) {
            lanzado = true
        }

        assertTrue(lanzado)
        assertEquals(1000L, ingresoDao.get(id)?.montoUnidadesMenores)
    }

    @Test
    fun eliminarIngreso_marca_deletedAt_en_firestore_y_room() = runBlocking {
        ingresoDao.upsert(ingreso("i1").toEntity())

        repo.eliminarIngreso(ingreso("i1").copy(deletedAt = java.time.Instant.now()))

        val doc = firestore.collection("ingresos").document("i1").get().await()
        assertTrue(doc.data?.get("deletedAt") != null)
        assertTrue(ingresoDao.get("i1")?.deletedAt != null)
    }

    @Test
    fun eliminarIngreso_error_permanente_rollback_room() = runBlocking {
        val id = "i/invalido"
        ingresoDao.upsert(ingreso(id, monto = 1000).toEntity())

        var lanzado = false
        try {
            repo.eliminarIngreso(ingreso(id).copy(deletedAt = java.time.Instant.now()))
        } catch (e: Exception) {
            lanzado = true
        }

        assertTrue(lanzado)
        assertEquals(null, ingresoDao.get(id)?.deletedAt)
    }

    // --- Conflict resolution (ARCH-M501 P1 fix + coverage) ---

    @Test
    fun resolverConflictoGastoUsandoRemoto_actualiza_room_y_limpia_edicionPendiente_y_conflicto() = runBlocking {
        gastoDao.upsert(gasto("g1", monto = 1000).toEntity())
        val clave = claveRegistro("gastos", "g1")
        registroEdicionesPendientes.registrarSiCorresponde(clave, "user-2", null, Instant.now(), TipoRegistro.GASTO)
        registroConflictosPendientes.registrar(clave, conflictoGasto("g1"))
        firestore.collection("gastos").document("g1")
            .set(GastoDto.fromDomain(gasto("g1", monto = 5000).copy(editedBy = "user-3", editedAt = Instant.now())).toMap())
            .await()

        repo.resolverConflictoGastoUsandoRemoto("g1", clave)

        assertEquals(5000L, gastoDao.get("g1")?.montoUnidadesMenores)
        assertEquals(null, db.edicionPendienteDao().get(clave))
        assertEquals(null, db.conflictoEdicionDao().get(clave))
    }

    @Test
    fun resolverConflictoGastoUsandoRemoto_evita_que_el_conflicto_reaparezca_al_reevaluar_el_mismo_remoto() = runBlocking {
        // Regression test for the ARCH-M501 P1 bug: resolverConflictoGastoUsandoRemoto used to
        // clear only the conflict record, never the pending-edit marker that caused it. The stale
        // marker survived with its original editorId/base, so the next time this exact
        // already-applied document was re-evaluated (e.g. a metadata-only Firestore re-delivery of
        // the same doc), decidirSincronizacion compared the incoming editedBy/editedAt against the
        // stale marker and flagged CONFLICTO again -- even though the user had already explicitly
        // resolved it by accepting the remote version. This drives the actual production method
        // (not a re-implementation of its transaction) and then re-runs the real snapshot-processing
        // path to prove the conflict does not reappear.
        gastoDao.upsert(gasto("g1", monto = 1000).toEntity())
        val clave = claveRegistro("gastos", "g1")
        registroEdicionesPendientes.registrarSiCorresponde(clave, "user-2", null, Instant.now(), TipoRegistro.GASTO)
        registroConflictosPendientes.registrar(clave, conflictoGasto("g1"))
        firestore.collection("gastos").document("g1")
            .set(GastoDto.fromDomain(gasto("g1", monto = 5000).copy(editedBy = "user-3", editedAt = Instant.now())).toMap())
            .await()

        repo.resolverConflictoGastoUsandoRemoto("g1", clave)

        // Same remote data re-delivered (redundant snapshot event) must NOT reopen the conflict.
        val aplicado = gastoDao.get("g1")!!.toDomain()
        procesarSnapshotGastos(db, gastoDao, registroEdicionesPendientes, registroConflictosPendientes, listOf(aplicado))

        assertEquals(null, db.conflictoEdicionDao().get(clave))
        assertEquals(5000L, gastoDao.get("g1")?.montoUnidadesMenores)
    }

    @Test
    fun resolverConflictoIngresoUsandoRemoto_actualiza_room_y_limpia_edicionPendiente_y_conflicto() = runBlocking {
        ingresoDao.upsert(ingreso("i1", monto = 1000).toEntity())
        val clave = claveRegistro("ingresos", "i1")
        registroEdicionesPendientes.registrarSiCorresponde(clave, "user-2", null, Instant.now(), TipoRegistro.INGRESO)
        registroConflictosPendientes.registrar(clave, conflictoIngreso("i1"))
        firestore.collection("ingresos").document("i1")
            .set(IngresoDto.fromDomain(ingreso("i1", monto = 7000).copy(editedBy = "user-3", editedAt = Instant.now())).toMap())
            .await()

        repo.resolverConflictoIngresoUsandoRemoto("i1", clave)

        assertEquals(7000L, ingresoDao.get("i1")?.montoUnidadesMenores)
        assertEquals(null, db.edicionPendienteDao().get(clave))
        assertEquals(null, db.conflictoEdicionDao().get(clave))
    }

    @Test
    fun resolverConflictoIngresoUsandoRemoto_evita_que_el_conflicto_reaparezca_al_reevaluar_el_mismo_remoto() = runBlocking {
        // Ingreso mirror of the Gasto regression test above -- same P1 bug, same fix, same shape.
        ingresoDao.upsert(ingreso("i1", monto = 1000).toEntity())
        val clave = claveRegistro("ingresos", "i1")
        registroEdicionesPendientes.registrarSiCorresponde(clave, "user-2", null, Instant.now(), TipoRegistro.INGRESO)
        registroConflictosPendientes.registrar(clave, conflictoIngreso("i1"))
        firestore.collection("ingresos").document("i1")
            .set(IngresoDto.fromDomain(ingreso("i1", monto = 7000).copy(editedBy = "user-3", editedAt = Instant.now())).toMap())
            .await()

        repo.resolverConflictoIngresoUsandoRemoto("i1", clave)

        val aplicado = ingresoDao.get("i1")!!.toDomain()
        procesarSnapshotIngresos(db, ingresoDao, registroEdicionesPendientes, registroConflictosPendientes, listOf(aplicado))

        assertEquals(null, db.conflictoEdicionDao().get(clave))
        assertEquals(7000L, ingresoDao.get("i1")?.montoUnidadesMenores)
    }

    @Test
    fun resolverConflictoGastoUsandoLocal_en_fallo_permanente_restaura_entidad_marcador_y_conflicto() = runBlocking {
        // Exercises resolverConflictoGastoUsandoLocal's catch block: a permanent Firestore failure
        // must roll back ALL THREE things the happy path touched -- the entity, the pending-edit
        // marker it just wrote, and the conflict it was about to consider resolved -- not just the
        // entity (see transaction #1's existing rollback, which this method deliberately doesn't reuse).
        val id = "g/invalido" // forces a permanent Firestore write failure, same trick as the other rollback tests
        gastoDao.upsert(gasto(id, monto = 1000).toEntity())
        val clave = claveRegistro("gastos", id)
        val conflictoPrevio = conflictoGasto(id)
        registroConflictosPendientes.registrar(clave, conflictoPrevio)

        var lanzado = false
        try {
            repo.resolverConflictoGastoUsandoLocal(gasto(id, monto = 2000).copy(editedBy = "user-2"), clave)
        } catch (e: Exception) {
            lanzado = true
        }

        assertTrue(lanzado)
        assertEquals(1000L, gastoDao.get(id)?.montoUnidadesMenores)
        assertEquals(null, db.edicionPendienteDao().get(clave))
        assertEquals("user-3", db.conflictoEdicionDao().get(clave)?.remoto?.editadoPor)
    }

    private fun conflictoGasto(id: String) = ConflictoEdicion(
        registroId = id,
        tipo = TipoRegistro.GASTO,
        local = gasto(id, monto = 1000).aSnapshotConflicto(),
        remoto = gasto(id, monto = 9000).copy(editedBy = "user-3").aSnapshotConflicto(),
    )

    private fun conflictoIngreso(id: String) = ConflictoEdicion(
        registroId = id,
        tipo = TipoRegistro.INGRESO,
        local = ingreso(id, monto = 1000).aSnapshotConflicto(),
        remoto = ingreso(id, monto = 9000).copy(editedBy = "user-3").aSnapshotConflicto(),
    )
}

package com.agoitdev.spenvo.data.remote.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.agoitdev.spenvo.data.local.SpenvoDatabase
import com.agoitdev.spenvo.data.local.dao.GastoDao
import com.agoitdev.spenvo.data.local.dao.IngresoDao
import com.agoitdev.spenvo.data.local.mapper.toEntity
import com.agoitdev.spenvo.data.remote.await
import com.agoitdev.spenvo.domain.model.Gasto
import com.agoitdev.spenvo.domain.model.Ingreso
import com.agoitdev.spenvo.domain.model.Monto
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.firestore.FirebaseFirestore
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
        repo = FirebaseMovimientoRepository(firestore, gastoDao, ingresoDao)
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
}

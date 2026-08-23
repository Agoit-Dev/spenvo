package com.agoitdev.spenvo.data.local.dao

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.agoitdev.spenvo.data.local.SpenvoDatabase
import com.agoitdev.spenvo.data.local.entity.GastoEntity
import com.agoitdev.spenvo.data.local.entity.IngresoEntity
import com.agoitdev.spenvo.domain.model.Monto
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MovimientoDaoSoftDeleteTest {

    private lateinit var db: SpenvoDatabase
    private lateinit var gastoDao: GastoDao
    private lateinit var ingresoDao: IngresoDao

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, SpenvoDatabase::class.java).build()
        gastoDao = db.gastoDao()
        ingresoDao = db.ingresoDao()
    }

    @After
    fun close() {
        db.close()
    }

    private fun gasto(id: String, deletedAt: Instant? = null) = GastoEntity(
        id = id,
        planId = "p1",
        categoriaId = "c1",
        montoUnidadesMenores = Monto(1000).unidadesMenores,
        fecha = LocalDate.of(2026, 8, 1),
        creadoPor = "user-1",
        deletedAt = deletedAt,
    )

    private fun ingreso(id: String, deletedAt: Instant? = null) = IngresoEntity(
        id = id,
        planId = "p1",
        categoriaId = "c1",
        montoUnidadesMenores = Monto(1000).unidadesMenores,
        fecha = LocalDate.of(2026, 8, 1),
        creadoPor = "user-1",
        deletedAt = deletedAt,
    )

    @Test
    fun gasto_observeByPlan_oculta_soft_deleted() = runBlocking {
        gastoDao.upsert(gasto("g1"))
        gastoDao.upsert(gasto("g2", deletedAt = Instant.ofEpochMilli(123)))

        val ids = gastoDao.observeByPlan("p1").first().map { it.id }

        assertEquals(listOf("g1"), ids)
    }

    @Test
    fun gasto_observeByPlanAndRange_oculta_soft_deleted() = runBlocking {
        gastoDao.upsert(gasto("g1"))
        gastoDao.upsert(gasto("g2", deletedAt = Instant.ofEpochMilli(123)))

        val ids = gastoDao.observeByPlanAndRange(
            "p1",
            LocalDate.of(2026, 7, 1),
            LocalDate.of(2026, 9, 1),
        ).first().map { it.id }

        assertEquals(listOf("g1"), ids)
    }

    @Test
    fun gasto_get_devuelve_la_fila_por_id() = runBlocking {
        gastoDao.upsert(gasto("g1"))

        val encontrado = gastoDao.get("g1")

        assertEquals("g1", encontrado?.id)
    }

    @Test
    fun ingreso_observeByPlan_oculta_soft_deleted() = runBlocking {
        ingresoDao.upsert(ingreso("i1"))
        ingresoDao.upsert(ingreso("i2", deletedAt = Instant.ofEpochMilli(123)))

        val ids = ingresoDao.observeByPlan("p1").first().map { it.id }

        assertEquals(listOf("i1"), ids)
    }

    @Test
    fun ingreso_observeByPlanAndRange_oculta_soft_deleted() = runBlocking {
        ingresoDao.upsert(ingreso("i1"))
        ingresoDao.upsert(ingreso("i2", deletedAt = Instant.ofEpochMilli(123)))

        val ids = ingresoDao.observeByPlanAndRange(
            "p1",
            LocalDate.of(2026, 7, 1),
            LocalDate.of(2026, 9, 1),
        ).first().map { it.id }

        assertEquals(listOf("i1"), ids)
    }

    @Test
    fun ingreso_get_devuelve_la_fila_por_id() = runBlocking {
        ingresoDao.upsert(ingreso("i1"))

        val encontrado = ingresoDao.get("i1")

        assertEquals("i1", encontrado?.id)
    }
}

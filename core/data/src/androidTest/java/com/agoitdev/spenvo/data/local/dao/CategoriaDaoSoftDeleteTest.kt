package com.agoitdev.spenvo.data.local.dao

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.agoitdev.spenvo.data.local.SpenvoDatabase
import com.agoitdev.spenvo.data.local.entity.CategoriaEntity
import com.agoitdev.spenvo.domain.model.TipoCategoria
import java.time.Instant
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CategoriaDaoSoftDeleteTest {

    private lateinit var db: SpenvoDatabase
    private lateinit var dao: CategoriaDao

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, SpenvoDatabase::class.java).build()
        dao = db.categoriaDao()
    }

    @After
    fun close() {
        db.close()
    }

    private fun categoria(id: String, deletedAt: Instant? = null) = CategoriaEntity(
        id = id,
        planId = "p1",
        nombre = "Comida",
        icono = "comida",
        tipo = TipoCategoria.GASTO,
        deletedAt = deletedAt,
    )

    @Test
    fun observeByPlan_oculta_soft_deleted() = runBlocking {
        dao.upsert(categoria("c1"))
        dao.upsert(categoria("c2", deletedAt = Instant.ofEpochMilli(123)))

        val ids = dao.observeByPlan("p1").first().map { it.id }

        assertEquals(listOf("c1"), ids)
    }

    @Test
    fun observeByPlanAndTipo_filtra_soft_deleted_y_tipo() = runBlocking {
        dao.upsert(categoria("c1"))
        dao.upsert(categoria("c2", deletedAt = Instant.ofEpochMilli(123)))
        dao.upsert(
            CategoriaEntity(
                id = "c3",
                planId = "p1",
                nombre = "Salario",
                icono = "sueldo",
                tipo = TipoCategoria.INGRESO,
            ),
        )

        val gastos = dao.observeByPlanAndTipo("p1", TipoCategoria.GASTO).first().map { it.id }

        assertEquals(listOf("c1"), gastos)
    }

    @Test
    fun delete_quita_la_fila_y_get_devuelve_la_previa() = runBlocking {
        dao.upsert(categoria("c1"))
        val previo = dao.get("c1")
        dao.delete("c1")

        assertEquals("c1", previo?.id)
        assertEquals(0, dao.observeByPlan("p1").first().size)
        assertEquals(null, dao.get("c1"))
    }
}

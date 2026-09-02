package com.agoitdev.spenvo.data.local.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.agoitdev.spenvo.data.local.SpenvoDatabase
import com.agoitdev.spenvo.data.local.entity.ConflictoEdicionEntity
import com.agoitdev.spenvo.domain.sync.CampoConflicto
import com.agoitdev.spenvo.domain.sync.SnapshotConflicto
import com.agoitdev.spenvo.domain.sync.TipoRegistro
import java.time.Instant
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ConflictoEdicionDaoTest {
    private lateinit var db: SpenvoDatabase
    private lateinit var dao: ConflictoEdicionDao

    private fun snapshot(borrado: Boolean = false) = SnapshotConflicto(
        editadoPor = "user-1",
        editadoEn = Instant.now(),
        borrado = borrado,
        campos = listOf(CampoConflicto("monto", "1000")),
    )

    private fun entidad(clave: String = "gastos:g1", registroId: String = "g1") = ConflictoEdicionEntity(
        clave = clave,
        registroId = registroId,
        tipo = TipoRegistro.GASTO,
        local = snapshot(),
        remoto = snapshot(),
    )

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), SpenvoDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.conflictoEdicionDao()
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun get_sin_registrar_devuelve_null() = runTest {
        assertNull(dao.get("gastos:g1"))
    }

    @Test
    fun upsert_luego_get_y_observeAll_devuelven_el_conflicto() = runTest {
        val entity = entidad()

        dao.upsert(entity)

        assertEquals(entity, dao.get("gastos:g1"))
        assertEquals(listOf(entity), dao.observeAll().first())
    }

    @Test
    fun delete_elimina_el_conflicto() = runTest {
        dao.upsert(entidad())

        dao.delete("gastos:g1")

        assertNull(dao.get("gastos:g1"))
        assertTrue(dao.observeAll().first().isEmpty())
    }

    @Test
    fun snapshotConflicto_sobrevive_el_roundtrip_por_el_converter_JSON() = runTest {
        val original = entidad(clave = "gastos:g2", registroId = "g2")

        dao.upsert(original)
        val leido = dao.get("gastos:g2")

        assertEquals(original.local, leido?.local)
        assertEquals(original.remoto, leido?.remoto)
    }
}

package com.agoitdev.spenvo.data.local.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.agoitdev.spenvo.data.local.SpenvoDatabase
import com.agoitdev.spenvo.data.local.entity.EdicionPendienteEntity
import com.agoitdev.spenvo.domain.sync.TipoRegistro
import java.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class EdicionPendienteDaoTest {
    private lateinit var db: SpenvoDatabase
    private lateinit var dao: EdicionPendienteDao

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), SpenvoDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.edicionPendienteDao()
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun get_sin_registrar_devuelve_null() = runTest {
        assertNull(dao.get("gastos:g1"))
    }

    @Test
    fun upsert_luego_get_devuelve_la_edicion_pendiente() = runTest {
        val ahora = Instant.now()
        val entity = EdicionPendienteEntity("gastos:g1", TipoRegistro.GASTO, "user-1", ahora.minusSeconds(60), ahora)

        dao.upsert(entity)

        assertEquals(entity, dao.get("gastos:g1"))
    }

    @Test
    fun delete_elimina_la_edicion_pendiente() = runTest {
        val ahora = Instant.now()
        dao.upsert(EdicionPendienteEntity("gastos:g1", TipoRegistro.GASTO, "user-1", ahora, ahora))

        dao.delete("gastos:g1")

        assertNull(dao.get("gastos:g1"))
    }
}

package com.agoitdev.spenvo.data.remote.sync

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.agoitdev.spenvo.data.local.SpenvoDatabase
import com.agoitdev.spenvo.domain.sync.CampoConflicto
import com.agoitdev.spenvo.domain.sync.ConflictoEdicion
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

class RegistroConflictosPendientesRoomTest {
    private lateinit var db: SpenvoDatabase
    private lateinit var registro: RegistroConflictosPendientesRoom

    private fun snapshot() = SnapshotConflicto("user-1", Instant.now(), false, listOf(CampoConflicto("monto", "1000")))
    private fun conflicto(id: String = "g1") = ConflictoEdicion(id, TipoRegistro.GASTO, snapshot(), snapshot())

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), SpenvoDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        registro = RegistroConflictosPendientesRoom(db.conflictoEdicionDao())
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun conflictoPara_sin_conflictos_registrados_devuelve_null() = runTest {
        assertNull(registro.conflictoPara("gastos:g1"))
    }

    @Test
    fun registrar_agrega_el_conflicto_y_lo_emite_en_el_flow() = runTest {
        val conflicto = conflicto()

        registro.registrar("gastos:g1", conflicto)

        assertEquals(conflicto, registro.conflictoPara("gastos:g1"))
        assertEquals(conflicto, registro.conflictos.first()["gastos:g1"])
    }

    @Test
    fun resolver_quita_el_conflicto() = runTest {
        registro.registrar("gastos:g1", conflicto())

        registro.resolver("gastos:g1")

        assertNull(registro.conflictoPara("gastos:g1"))
        assertTrue(registro.conflictos.first().isEmpty())
    }
}

package com.agoitdev.spenvo.data.remote.sync

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.agoitdev.spenvo.data.local.SpenvoDatabase
import com.agoitdev.spenvo.domain.sync.DecisionSincronizacion
import com.agoitdev.spenvo.domain.sync.TipoRegistro
import java.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class RegistroEdicionesPendientesRoomTest {
    private lateinit var db: SpenvoDatabase
    private lateinit var registro: RegistroEdicionesPendientesRoom
    private val ahora: Instant = Instant.now()

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), SpenvoDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        registro = RegistroEdicionesPendientesRoom(db.edicionPendienteDao())
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun evaluar_sin_edicion_pendiente_aplica_el_remoto() = runTest {
        val decision = registro.evaluar("gastos:g1", editedBy = "user-2", editedAt = ahora)

        assertEquals(DecisionSincronizacion.APLICAR, decision)
    }

    @Test
    fun registrarSiCorresponde_no_registra_si_editorId_es_null() = runTest {
        registro.registrarSiCorresponde("gastos:g1", editorId = null, base = ahora, miEditedAt = ahora, tipo = TipoRegistro.GASTO)

        assertEquals(DecisionSincronizacion.APLICAR, registro.evaluar("gastos:g1", "user-2", ahora))
    }

    @Test
    fun registrar_luego_evaluar_con_eco_propio_confirma_y_limpia() = runTest {
        registro.registrarSiCorresponde("gastos:g1", editorId = "user-1", base = ahora.minusSeconds(60), miEditedAt = ahora, tipo = TipoRegistro.GASTO)

        val decision = registro.evaluar("gastos:g1", editedBy = "user-1", editedAt = ahora)

        assertEquals(DecisionSincronizacion.PROPIA_CONFIRMADA, decision)
        assertNull(db.edicionPendienteDao().get("gastos:g1"))
    }

    @Test
    fun registrar_luego_evaluar_con_conflicto_genuino_deja_el_marcador_intacto() = runTest {
        registro.registrarSiCorresponde("gastos:g1", editorId = "user-1", base = ahora.minusSeconds(60), miEditedAt = ahora, tipo = TipoRegistro.GASTO)

        val decision = registro.evaluar("gastos:g1", editedBy = "user-2", editedAt = ahora.plusSeconds(30))

        assertEquals(DecisionSincronizacion.CONFLICTO, decision)
        assertEquals("user-1", db.edicionPendienteDao().get("gastos:g1")?.editorId)
    }

    @Test
    fun limpiar_elimina_el_marcador() = runTest {
        registro.registrarSiCorresponde("gastos:g1", editorId = "user-1", base = ahora, miEditedAt = ahora, tipo = TipoRegistro.GASTO)

        registro.limpiar("gastos:g1")

        assertNull(db.edicionPendienteDao().get("gastos:g1"))
    }
}

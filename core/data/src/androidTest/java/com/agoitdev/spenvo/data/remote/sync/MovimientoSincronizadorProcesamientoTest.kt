package com.agoitdev.spenvo.data.remote.sync

import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import com.agoitdev.spenvo.data.local.SpenvoDatabase
import com.agoitdev.spenvo.data.local.entity.EdicionPendienteEntity
import com.agoitdev.spenvo.data.local.mapper.toEntity
import com.agoitdev.spenvo.domain.model.Gasto
import com.agoitdev.spenvo.domain.model.Ingreso
import com.agoitdev.spenvo.domain.model.Monto
import com.agoitdev.spenvo.domain.sync.TipoRegistro
import com.agoitdev.spenvo.domain.sync.claveRegistro
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Mirrors CategoriaSincronizadorProcesamientoTest's three cases (plain apply, genuine conflict
 * registers with the local snapshot reconstructed from Room, orphaned marker with no local row
 * cleans up and applies remote) for procesarSnapshotGastos/procesarSnapshotIngresos (ARCH-M501).
 * procesarSnapshotPlanes shares the exact same transaction shape (see PlanSincronizador.kt) and is
 * deliberately not duplicated here — Categoria + Gasto + Ingreso already prove the pattern three
 * times over.
 */
class MovimientoSincronizadorProcesamientoTest {
    private lateinit var db: SpenvoDatabase
    private lateinit var registroEdiciones: RegistroEdicionesPendientesRoom
    private lateinit var registroConflictos: RegistroConflictosPendientesRoom

    private fun gasto(id: String = "g1", monto: Long = 1000, editedBy: String? = "user-2", editedAt: Instant? = Instant.now()) = Gasto(
        id = id,
        planId = "p1",
        categoriaId = "c1",
        monto = Monto(monto),
        fecha = LocalDate.of(2026, 8, 20),
        creadoPor = "user-1",
        editedBy = editedBy,
        editedAt = editedAt,
    )

    private fun ingreso(id: String = "i1", monto: Long = 1000, editedBy: String? = "user-2", editedAt: Instant? = Instant.now()) = Ingreso(
        id = id,
        planId = "p1",
        categoriaId = "c1",
        monto = Monto(monto),
        fecha = LocalDate.of(2026, 8, 20),
        creadoPor = "user-1",
        editedBy = editedBy,
        editedAt = editedAt,
    )

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), SpenvoDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        registroEdiciones = RegistroEdicionesPendientesRoom(db.edicionPendienteDao())
        registroConflictos = RegistroConflictosPendientesRoom(db.conflictoEdicionDao())
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun gasto_sin_edicion_pendiente_se_aplica() = runTest {
        procesarSnapshotGastos(db, db.gastoDao(), registroEdiciones, registroConflictos, listOf(gasto()))

        assertEquals(1000L, db.gastoDao().get("g1")?.montoUnidadesMenores)
    }

    @Test
    fun gasto_conflicto_genuino_no_se_aplica_y_se_registra_con_la_version_local_reconstruida_de_Room() = runTest {
        val local = gasto(editedBy = "user-1", editedAt = Instant.now().minusSeconds(60))
        db.withTransaction {
            registroEdiciones.registrarSiCorresponde(
                claveRegistro(GASTOS_COLLECTION_TEST, local.id), "user-1", local.editedAt?.minusSeconds(60), local.editedAt, TipoRegistro.GASTO,
            )
            db.gastoDao().upsert(local.toEntity())
        }

        val remoto = gasto(monto = 5000, editedBy = "user-2", editedAt = Instant.now().plusSeconds(30))
        procesarSnapshotGastos(db, db.gastoDao(), registroEdiciones, registroConflictos, listOf(remoto))

        assertEquals(1000L, db.gastoDao().get("g1")?.montoUnidadesMenores) // local unchanged, not overwritten
        val conflicto = registroConflictos.conflictoPara(claveRegistro(GASTOS_COLLECTION_TEST, local.id))
        assertEquals("user-1", conflicto?.local?.editadoPor)
        assertEquals("user-2", conflicto?.remoto?.editadoPor)
    }

    @Test
    fun gasto_marcador_huerfano_sin_fila_local_se_limpia_y_se_aplica_el_remoto_sin_crash() = runTest {
        db.edicionPendienteDao().upsert(
            EdicionPendienteEntity(
                claveRegistro(GASTOS_COLLECTION_TEST, "g1"), TipoRegistro.GASTO, "user-1", null, Instant.now().minusSeconds(60),
            ),
        )

        val remoto = gasto(monto = 5000, editedBy = "user-2", editedAt = Instant.now())
        procesarSnapshotGastos(db, db.gastoDao(), registroEdiciones, registroConflictos, listOf(remoto))

        assertEquals(5000L, db.gastoDao().get("g1")?.montoUnidadesMenores)
        assertNull(db.edicionPendienteDao().get(claveRegistro(GASTOS_COLLECTION_TEST, "g1")))
        assertTrue(registroConflictos.conflictos.first().isEmpty())
    }

    @Test
    fun ingreso_sin_edicion_pendiente_se_aplica() = runTest {
        procesarSnapshotIngresos(db, db.ingresoDao(), registroEdiciones, registroConflictos, listOf(ingreso()))

        assertEquals(1000L, db.ingresoDao().get("i1")?.montoUnidadesMenores)
    }

    @Test
    fun ingreso_conflicto_genuino_no_se_aplica_y_se_registra_con_la_version_local_reconstruida_de_Room() = runTest {
        val local = ingreso(editedBy = "user-1", editedAt = Instant.now().minusSeconds(60))
        db.withTransaction {
            registroEdiciones.registrarSiCorresponde(
                claveRegistro(INGRESOS_COLLECTION_TEST, local.id), "user-1", local.editedAt?.minusSeconds(60), local.editedAt, TipoRegistro.INGRESO,
            )
            db.ingresoDao().upsert(local.toEntity())
        }

        val remoto = ingreso(monto = 7000, editedBy = "user-2", editedAt = Instant.now().plusSeconds(30))
        procesarSnapshotIngresos(db, db.ingresoDao(), registroEdiciones, registroConflictos, listOf(remoto))

        assertEquals(1000L, db.ingresoDao().get("i1")?.montoUnidadesMenores) // local unchanged, not overwritten
        val conflicto = registroConflictos.conflictoPara(claveRegistro(INGRESOS_COLLECTION_TEST, local.id))
        assertEquals("user-1", conflicto?.local?.editadoPor)
        assertEquals("user-2", conflicto?.remoto?.editadoPor)
    }

    @Test
    fun ingreso_marcador_huerfano_sin_fila_local_se_limpia_y_se_aplica_el_remoto_sin_crash() = runTest {
        db.edicionPendienteDao().upsert(
            EdicionPendienteEntity(
                claveRegistro(INGRESOS_COLLECTION_TEST, "i1"), TipoRegistro.INGRESO, "user-1", null, Instant.now().minusSeconds(60),
            ),
        )

        val remoto = ingreso(monto = 7000, editedBy = "user-2", editedAt = Instant.now())
        procesarSnapshotIngresos(db, db.ingresoDao(), registroEdiciones, registroConflictos, listOf(remoto))

        assertEquals(7000L, db.ingresoDao().get("i1")?.montoUnidadesMenores)
        assertNull(db.edicionPendienteDao().get(claveRegistro(INGRESOS_COLLECTION_TEST, "i1")))
        assertTrue(registroConflictos.conflictos.first().isEmpty())
    }

    private companion object {
        const val GASTOS_COLLECTION_TEST = "gastos"
        const val INGRESOS_COLLECTION_TEST = "ingresos"
    }
}

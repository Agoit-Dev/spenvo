package com.agoitdev.spenvo.data.remote.sync

import com.agoitdev.spenvo.domain.model.Gasto
import com.agoitdev.spenvo.domain.model.Monto
import com.agoitdev.spenvo.domain.sync.CampoConflicto
import com.agoitdev.spenvo.domain.sync.ConflictosPendientes
import com.agoitdev.spenvo.domain.sync.EdicionPendiente
import com.agoitdev.spenvo.domain.sync.EdicionesPendientes
import com.agoitdev.spenvo.domain.sync.SnapshotConflicto
import com.agoitdev.spenvo.domain.sync.TipoRegistro
import com.agoitdev.spenvo.domain.sync.VersionPendiente
import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Exercises the exact decision embedded in each sincronizador's per-document
 * partition step (Slice 4, task 4.7/4.8) — pure and mock-free: no Firestore
 * SDK types are involved.
 */
class ConflictoParticionTest {

    private val ahora: Instant = Instant.now()

    private fun gasto(editedBy: String? = "user-1", editedAt: Instant? = ahora) = Gasto(
        id = "g1",
        planId = "p1",
        categoriaId = "c1",
        monto = Monto(1000),
        fecha = LocalDate.of(2026, 8, 20),
        creadoPor = "user-1",
        editedBy = editedBy,
        editedAt = editedAt,
    )

    private fun remotoSnapshot() = SnapshotConflicto(
        editadoPor = "user-2",
        editadoEn = ahora,
        borrado = false,
        campos = listOf(CampoConflicto("monto", "5000")),
    )

    private fun documento(
        editedBy: String? = "user-2",
        editedAt: Instant? = ahora.plusSeconds(30),
    ) = DocumentoParaSincronizar(
        coleccion = "gastos",
        id = "g1",
        editedBy = editedBy,
        editedAt = editedAt,
        tipo = TipoRegistro.GASTO,
    )

    @Test
    fun `remoto plano sin edicion pendiente se aplica - plain remote update is not a conflict`() {
        val edicionesPendientes = EdicionesPendientes()
        val conflictosPendientes = ConflictosPendientes()

        val aplicar = evaluarDocumentoRemoto(
            edicionesPendientes,
            conflictosPendientes,
            documento(),
        ) { remotoSnapshot() }

        assertTrue(aplicar)
        assertNull(conflictosPendientes.conflictoPara("gastos:g1"))
    }

    @Test
    fun `eco de mi propia escritura se aplica y limpia la pendiente`() {
        val edicionesPendientes = EdicionesPendientes()
        val conflictosPendientes = ConflictosPendientes()
        edicionesPendientes.registrar(
            EdicionPendiente(
                clave = "gastos:g1",
                editorId = "user-1",
                base = ahora.minusSeconds(60),
                miEditedAt = ahora,
                miVersion = VersionPendiente.DeGasto(gasto()),
            ),
        )

        val aplicar = evaluarDocumentoRemoto(
            edicionesPendientes,
            conflictosPendientes,
            documento(editedBy = "user-1", editedAt = ahora),
        ) { remotoSnapshot() }

        assertTrue(aplicar)
        assertNull(edicionesPendientes.obtener("gastos:g1"))
        assertNull(conflictosPendientes.conflictoPara("gastos:g1"))
    }

    @Test
    fun `conflicto genuino no se aplica y se registra con la version local y remota - genuine conflict detected`() {
        val edicionesPendientes = EdicionesPendientes()
        val conflictosPendientes = ConflictosPendientes()
        val local = gasto(editedBy = "user-1", editedAt = ahora)
        edicionesPendientes.registrar(
            EdicionPendiente(
                clave = "gastos:g1",
                editorId = "user-1",
                base = ahora.minusSeconds(60),
                miEditedAt = ahora,
                miVersion = VersionPendiente.DeGasto(local),
            ),
        )

        val aplicar = evaluarDocumentoRemoto(
            edicionesPendientes,
            conflictosPendientes,
            documento(editedBy = "user-2", editedAt = ahora.plusSeconds(30)),
        ) { remotoSnapshot() }

        assertFalse(aplicar)
        val conflicto = conflictosPendientes.conflictoPara("gastos:g1")
        assertEquals("g1", conflicto?.registroId)
        assertEquals(TipoRegistro.GASTO, conflicto?.tipo)
        assertEquals("user-1", conflicto?.local?.editadoPor)
        assertEquals("user-2", conflicto?.remoto?.editadoPor)
    }

    @Test
    fun `borrado remoto concurrente con edicion local pendiente tambien se marca como conflicto - delete vs edit`() {
        val edicionesPendientes = EdicionesPendientes()
        val conflictosPendientes = ConflictosPendientes()
        edicionesPendientes.registrar(
            EdicionPendiente(
                clave = "gastos:g1",
                editorId = "user-1",
                base = ahora.minusSeconds(120),
                miEditedAt = ahora.minusSeconds(60),
                miVersion = VersionPendiente.DeGasto(gasto(editedAt = ahora.minusSeconds(60))),
            ),
        )
        val remotoBorrado = SnapshotConflicto(
            editadoPor = "user-2",
            editadoEn = ahora,
            borrado = true,
            campos = listOf(CampoConflicto("monto", "1000")),
        )

        val aplicar = evaluarDocumentoRemoto(
            edicionesPendientes,
            conflictosPendientes,
            documento(editedBy = "user-2", editedAt = ahora),
        ) { remotoBorrado }

        assertFalse(aplicar)
        assertTrue(conflictosPendientes.conflictoPara("gastos:g1")?.remoto?.borrado == true)
    }
}

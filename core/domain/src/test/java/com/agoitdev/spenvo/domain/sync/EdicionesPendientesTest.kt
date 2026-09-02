package com.agoitdev.spenvo.domain.sync

import java.time.Instant
import java.time.temporal.ChronoUnit
import org.junit.Assert.assertEquals
import org.junit.Test

class EdicionesPendientesTest {

    private val ahora: Instant = Instant.now()

    private fun edicion(
        editorId: String = "user-1",
        base: Instant? = ahora.minusSeconds(60),
        miEditedAt: Instant? = ahora,
    ) = EdicionPendiente(
        clave = "gastos:g1",
        tipo = TipoRegistro.GASTO,
        editorId = editorId,
        base = base,
        miEditedAt = miEditedAt,
    )

    @Test
    fun `claveRegistro combina coleccion e id`() {
        assertEquals("gastos:g1", claveRegistro("gastos", "g1"))
    }

    @Test
    fun `sin edicion pendiente aplica el remoto - plain remote update is not a conflict`() {
        val decision = decidirSincronizacion(pendiente = null, editedBy = "user-2", editedAt = ahora)

        assertEquals(DecisionSincronizacion.APLICAR, decision)
    }

    @Test
    fun `eco de mi propia escritura confirma - own write echoed back`() {
        val decision = decidirSincronizacion(
            pendiente = edicion(editorId = "user-1", miEditedAt = ahora),
            editedBy = "user-1",
            editedAt = ahora,
        )

        assertEquals(DecisionSincronizacion.PROPIA_CONFIRMADA, decision)
    }

    @Test
    fun `edicion concurrente de otro usuario mas reciente es conflicto - genuine conflict detected`() {
        val decision = decidirSincronizacion(
            pendiente = edicion(editorId = "user-1", base = ahora.minusSeconds(60)),
            editedBy = "user-2",
            editedAt = ahora.plusSeconds(30),
        )

        assertEquals(DecisionSincronizacion.CONFLICTO, decision)
    }

    @Test
    fun `edicion de otro usuario pero no mas reciente que la base no es conflicto`() {
        val base = ahora
        val decision = decidirSincronizacion(
            pendiente = edicion(editorId = "user-1", base = base),
            editedBy = "user-2",
            editedAt = base.minus(1, ChronoUnit.SECONDS),
        )

        assertEquals(DecisionSincronizacion.APLICAR, decision)
    }

    @Test
    fun `sin editedAt remoto nunca es conflicto`() {
        val decision = decidirSincronizacion(
            pendiente = edicion(editorId = "user-1", base = ahora.minusSeconds(60)),
            editedBy = "user-2",
            editedAt = null,
        )

        assertEquals(DecisionSincronizacion.APLICAR, decision)
    }

    @Test
    fun `sin base conocida cualquier edicion concurrente es conflicto`() {
        val decision = decidirSincronizacion(
            pendiente = edicion(editorId = "user-1", base = null),
            editedBy = "user-2",
            editedAt = ahora,
        )

        assertEquals(DecisionSincronizacion.CONFLICTO, decision)
    }

    @Test
    fun `borrado remoto concurrente con edicion local pendiente se marca como conflicto - delete vs edit`() {
        val decision = decidirSincronizacion(
            pendiente = edicion(editorId = "user-1", base = ahora.minusSeconds(120)),
            editedBy = "user-2",
            editedAt = ahora.plusSeconds(10),
        )

        assertEquals(DecisionSincronizacion.CONFLICTO, decision)
    }
}

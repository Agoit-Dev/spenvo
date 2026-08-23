package com.agoitdev.spenvo.domain.sync

import com.agoitdev.spenvo.domain.model.Gasto
import com.agoitdev.spenvo.domain.model.Monto
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EdicionesPendientesTest {

    private val ahora = java.time.Instant.now()

    private fun gasto(id: String = "g1", editedBy: String? = "user-1") = Gasto(
        id = id,
        planId = "p1",
        categoriaId = "c1",
        monto = Monto(1000),
        fecha = LocalDate.of(2026, 8, 20),
        creadoPor = "user-1",
        editedBy = editedBy,
        editedAt = ahora,
    )

    private fun edicion(
        clave: String = "gastos:g1",
        editorId: String = "user-1",
        base: java.time.Instant? = ahora.minusSeconds(60),
        miEditedAt: java.time.Instant? = ahora,
    ) = EdicionPendiente(
        clave = clave,
        editorId = editorId,
        base = base,
        miEditedAt = miEditedAt,
        miVersion = VersionPendiente.DeGasto(gasto(editedBy = editorId)),
    )

    @Test
    fun `clave combina coleccion e id`() {
        assertEquals("gastos:g1", EdicionesPendientes.clave("gastos", "g1"))
    }

    @Test
    fun `obtener sin registrar devuelve null`() {
        val registro = EdicionesPendientes()

        assertNull(registro.obtener("gastos:g1"))
    }

    @Test
    fun `registrar luego obtener devuelve la edicion pendiente`() {
        val registro = EdicionesPendientes()
        val pendiente = edicion()

        registro.registrar(pendiente)

        assertEquals(pendiente, registro.obtener("gastos:g1"))
    }

    @Test
    fun `limpiar elimina la edicion pendiente`() {
        val registro = EdicionesPendientes()
        registro.registrar(edicion())

        registro.limpiar("gastos:g1")

        assertNull(registro.obtener("gastos:g1"))
    }

    @Test
    fun `registrarSiCorresponde no registra si editorId es null`() {
        val registro = EdicionesPendientes()

        registro.registrarSiCorresponde(
            clave = "gastos:g1",
            editorId = null,
            base = ahora,
            miEditedAt = ahora,
            miVersion = VersionPendiente.DeGasto(gasto()),
        )

        assertNull(registro.obtener("gastos:g1"))
    }

    @Test
    fun `registrarSiCorresponde registra cuando hay editorId`() {
        val registro = EdicionesPendientes()

        registro.registrarSiCorresponde(
            clave = "gastos:g1",
            editorId = "user-1",
            base = ahora,
            miEditedAt = ahora,
            miVersion = VersionPendiente.DeGasto(gasto()),
        )

        assertEquals("user-1", registro.obtener("gastos:g1")?.editorId)
    }

    // --- evaluar(): the settled conflict predicate ---

    @Test
    fun `evaluar sin edicion pendiente aplica el remoto - plain remote update is not a conflict`() {
        val registro = EdicionesPendientes()

        val decision = registro.evaluar("gastos:g1", editedBy = "user-2", editedAt = ahora)

        assertEquals(DecisionSincronizacion.APLICAR, decision)
    }

    @Test
    fun `evaluar con eco propio limpia la pendiente y confirma - own write echoed back`() {
        val registro = EdicionesPendientes()
        registro.registrar(edicion(editorId = "user-1", miEditedAt = ahora))

        val decision = registro.evaluar("gastos:g1", editedBy = "user-1", editedAt = ahora)

        assertEquals(DecisionSincronizacion.PROPIA_CONFIRMADA, decision)
        assertNull(registro.obtener("gastos:g1"))
    }

    @Test
    fun `evaluar edicion concurrente de otro usuario mas reciente es conflicto - genuine conflict detected`() {
        val registro = EdicionesPendientes()
        registro.registrar(edicion(editorId = "user-1", base = ahora.minusSeconds(60)))

        val decision = registro.evaluar(
            "gastos:g1",
            editedBy = "user-2",
            editedAt = ahora.plusSeconds(30),
        )

        assertEquals(DecisionSincronizacion.CONFLICTO, decision)
    }

    @Test
    fun `evaluar edicion de otro usuario pero no mas reciente que la base no es conflicto`() {
        val registro = EdicionesPendientes()
        val base = ahora
        registro.registrar(edicion(editorId = "user-1", base = base))

        val decision = registro.evaluar(
            "gastos:g1",
            editedBy = "user-2",
            editedAt = base.minus(1, ChronoUnit.SECONDS),
        )

        assertEquals(DecisionSincronizacion.APLICAR, decision)
    }

    @Test
    fun `evaluar sin editedAt remoto nunca es conflicto`() {
        val registro = EdicionesPendientes()
        registro.registrar(edicion(editorId = "user-1", base = ahora.minusSeconds(60)))

        val decision = registro.evaluar("gastos:g1", editedBy = "user-2", editedAt = null)

        assertEquals(DecisionSincronizacion.APLICAR, decision)
    }

    @Test
    fun `evaluar sin base conocida cualquier edicion concurrente es conflicto`() {
        val registro = EdicionesPendientes()
        registro.registrar(edicion(editorId = "user-1", base = null))

        val decision = registro.evaluar("gastos:g1", editedBy = "user-2", editedAt = ahora)

        assertEquals(DecisionSincronizacion.CONFLICTO, decision)
    }

    @Test
    fun `evaluar borrado remoto concurrente con edicion local pendiente se marca como conflicto - delete vs edit`() {
        // A soft-delete stamps editedBy/editedAt exactly like an edit, so the
        // predicate flags it without inspecting deletedAt at all.
        val registro = EdicionesPendientes()
        registro.registrar(edicion(editorId = "user-1", base = ahora.minusSeconds(120)))

        val decisionBorradoRemoto = registro.evaluar(
            "gastos:g1",
            editedBy = "user-2",
            editedAt = ahora.plusSeconds(10),
        )

        assertEquals(DecisionSincronizacion.CONFLICTO, decisionBorradoRemoto)
    }
}

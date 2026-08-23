package com.agoitdev.spenvo.domain.sync

import com.agoitdev.spenvo.domain.model.Categoria
import com.agoitdev.spenvo.domain.model.Gasto
import com.agoitdev.spenvo.domain.model.Ingreso
import com.agoitdev.spenvo.domain.model.Monto
import com.agoitdev.spenvo.domain.model.PlanFinanciero
import com.agoitdev.spenvo.domain.model.TipoCategoria
import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ConflictosPendientesTest {

    private val ahora: Instant = Instant.now()

    private fun snapshot(borrado: Boolean = false) = SnapshotConflicto(
        editadoPor = "user-1",
        editadoEn = ahora,
        borrado = borrado,
        campos = listOf(CampoConflicto("monto", "1000")),
    )

    private fun conflicto(id: String = "g1") = ConflictoEdicion(
        registroId = id,
        tipo = TipoRegistro.GASTO,
        local = snapshot(),
        remoto = snapshot(),
    )

    @Test
    fun `conflictoPara sin conflictos registrados devuelve null`() {
        val registro = ConflictosPendientes()

        assertNull(registro.conflictoPara("gastos:g1"))
    }

    @Test
    fun `registrar agrega el conflicto y lo emite en el stateflow`() {
        val registro = ConflictosPendientes()
        val conflicto = conflicto()

        registro.registrar("gastos:g1", conflicto)

        assertEquals(conflicto, registro.conflictos.value["gastos:g1"])
        assertEquals(conflicto, registro.conflictoPara("gastos:g1"))
    }

    @Test
    fun `resolver quita el conflicto del mapa`() {
        val registro = ConflictosPendientes()
        registro.registrar("gastos:g1", conflicto())

        registro.resolver("gastos:g1")

        assertNull(registro.conflictoPara("gastos:g1"))
        assertTrue(registro.conflictos.value.isEmpty())
    }

    @Test
    fun `registrar no afecta otras claves ya presentes`() {
        val registro = ConflictosPendientes()
        registro.registrar("gastos:g1", conflicto("g1"))

        registro.registrar("gastos:g2", conflicto("g2"))

        assertEquals(2, registro.conflictos.value.size)
        assertEquals("g1", registro.conflictoPara("gastos:g1")?.registroId)
        assertEquals("g2", registro.conflictoPara("gastos:g2")?.registroId)
    }

    // --- per-entity SnapshotConflicto projections (design's field table) ---

    @Test
    fun `gasto aSnapshotConflicto incluye monto fecha categoria y descripcion`() {
        val gasto = Gasto(
            id = "g1",
            planId = "p1",
            categoriaId = "c1",
            monto = Monto(2500),
            fecha = LocalDate.of(2026, 8, 20),
            descripcion = "Cena",
            creadoPor = "user-1",
            editedBy = "user-1",
            editedAt = ahora,
        )

        val snapshot = gasto.aSnapshotConflicto()

        assertEquals("user-1", snapshot.editadoPor)
        assertEquals(ahora, snapshot.editadoEn)
        assertEquals(false, snapshot.borrado)
        assertEquals(
            listOf(
                CampoConflicto("monto", "2500"),
                CampoConflicto("fecha", "2026-08-20"),
                CampoConflicto("categoria", "c1"),
                CampoConflicto("descripcion", "Cena"),
            ),
            snapshot.campos,
        )
    }

    @Test
    fun `ingreso aSnapshotConflicto usa la misma proyeccion que gasto via Movimiento`() {
        val ingreso = Ingreso(
            id = "i1",
            planId = "p1",
            categoriaId = "c2",
            monto = Monto(500),
            fecha = LocalDate.of(2026, 1, 1),
            creadoPor = "user-1",
            editedBy = "user-2",
            editedAt = ahora,
            deletedAt = ahora,
        )

        val snapshot = ingreso.aSnapshotConflicto()

        assertEquals(true, snapshot.borrado)
        assertEquals(
            listOf(
                CampoConflicto("monto", "500"),
                CampoConflicto("fecha", "2026-01-01"),
                CampoConflicto("categoria", "c2"),
                CampoConflicto("descripcion", ""),
            ),
            snapshot.campos,
        )
    }

    @Test
    fun `categoria aSnapshotConflicto incluye nombre tipo e icono`() {
        val categoria = Categoria(
            id = "p1:gasto_comida",
            planId = "p1",
            nombre = "Comida",
            icono = "comida",
            tipo = TipoCategoria.GASTO,
            editedBy = "user-1",
            editedAt = ahora,
        )

        val snapshot = categoria.aSnapshotConflicto()

        assertEquals(
            listOf(
                CampoConflicto("nombre", "Comida"),
                CampoConflicto("tipo", "GASTO"),
                CampoConflicto("icono", "comida"),
            ),
            snapshot.campos,
        )
    }

    @Test
    fun `plan aSnapshotConflicto incluye nombre descripcion y moneda`() {
        val plan = PlanFinanciero(
            id = "p1",
            nombre = "Casa",
            descripcion = "Gastos compartidos",
            moneda = "EUR",
            createdBy = "user-1",
            editedBy = "user-1",
            editedAt = ahora,
        )

        val snapshot = plan.aSnapshotConflicto()

        assertEquals(
            listOf(
                CampoConflicto("nombre", "Casa"),
                CampoConflicto("descripcion", "Gastos compartidos"),
                CampoConflicto("moneda", "EUR"),
            ),
            snapshot.campos,
        )
    }

    @Test
    fun `aSnapshotConflicto de VersionPendiente delega en la proyeccion del tipo concreto`() {
        val gasto = Gasto(
            id = "g1",
            planId = "p1",
            categoriaId = "c1",
            monto = Monto(100),
            fecha = LocalDate.of(2026, 1, 1),
            creadoPor = "user-1",
            editedBy = "user-1",
            editedAt = ahora,
        )
        val version: VersionPendiente = VersionPendiente.DeGasto(gasto)

        assertEquals(gasto.aSnapshotConflicto(), version.aSnapshotConflicto())
    }
}

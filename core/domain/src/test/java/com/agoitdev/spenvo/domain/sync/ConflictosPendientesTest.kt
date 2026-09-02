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
import org.junit.Test

class ConflictosPendientesTest {

    private val ahora: Instant = Instant.now()

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
}

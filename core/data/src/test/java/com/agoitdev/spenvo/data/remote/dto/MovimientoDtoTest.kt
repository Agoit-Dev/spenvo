package com.agoitdev.spenvo.data.remote.dto

import com.agoitdev.spenvo.domain.model.Gasto
import com.agoitdev.spenvo.domain.model.Ingreso
import com.agoitdev.spenvo.domain.model.Monto
import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MovimientoDtoTest {

    private val creado = Instant.parse("2026-08-20T10:00:00Z")

    @Test
    fun `GastoDto fromDomain y toDomain son inversos`() {
        val gasto = Gasto(
            id = "g1",
            planId = "plan-1",
            categoriaId = "cat-1",
            monto = Monto(2500),
            fecha = LocalDate.of(2026, 8, 20),
            descripcion = "Mercadona",
            creadoPor = "user-1",
            createdAt = creado,
            updatedAt = creado,
        )

        val dto = GastoDto.fromDomain(gasto)

        assertEquals(gasto, dto.toDomain())
    }

    @Test
    fun `IngresoDto fromDomain y toDomain son inversos`() {
        val ingreso = Ingreso(
            id = "i1",
            planId = "plan-1",
            categoriaId = "cat-2",
            monto = Monto(325000),
            fecha = LocalDate.of(2026, 8, 20),
            creadoPor = "user-1",
            createdAt = creado,
            updatedAt = creado,
        )

        val dto = IngresoDto.fromDomain(ingreso)

        assertEquals(ingreso, dto.toDomain())
    }

    @Test
    fun `fromData devuelve null si falta un campo requerido`() {
        assertNull(GastoDto.fromData(mapOf("id" to "g1", "planId" to "p1")))
        assertNull(IngresoDto.fromData(mapOf("id" to "i1", "planId" to "p1")))
    }
}

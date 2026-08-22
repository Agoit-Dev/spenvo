package com.agoitdev.spenvo.data.remote.dto

import com.agoitdev.spenvo.domain.model.Categoria
import com.agoitdev.spenvo.domain.model.TipoCategoria
import com.google.firebase.Timestamp
import java.time.Instant
import java.util.Date
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CategoriaDtoTest {

    private val instante = Instant.parse("2026-08-21T10:00:00Z")

    @Test
    fun `fromDomain y toDomain son inversos`() {
        val categoria = Categoria(
            id = "plan1:gasto_comida",
            planId = "plan1",
            nombre = "Comida",
            icono = "comida",
            iconoUrl = null,
            tipo = TipoCategoria.GASTO,
            editedBy = "user-1",
            editedAt = instante,
            deletedAt = null,
        )

        val dto = CategoriaDto.fromDomain(categoria)

        assertEquals(categoria, dto.toDomain())
    }

    @Test
    fun `toMap guarda el tipo en minusculas`() {
        val dto = CategoriaDto.fromDomain(
            Categoria(
                id = "p1:c",
                planId = "p1",
                nombre = "Comida",
                icono = "comida",
                tipo = TipoCategoria.GASTO,
            ),
        )

        val mapa = dto.toMap()

        assertEquals("gasto", mapa["tipo"])
        assertNull(mapa["deletedAt"])
        assertEquals("p1", mapa["planId"])
    }

    @Test
    fun `fromData lee los campos almacenados`() {
        val data = mapOf(
            "id" to "p1:c",
            "planId" to "p1",
            "nombre" to "Salario",
            "icono" to "sueldo",
            "tipo" to "ingreso",
            "deletedAt" to Timestamp(Date.from(instante)),
        )

        val dto = CategoriaDto.fromData(data)

        assertEquals(TipoCategoria.INGRESO, dto?.tipo)
        assertEquals(instante, dto?.deletedAt?.toInstant())
        assertEquals("sueldo", dto?.icono)
    }

    @Test
    fun `fromData devuelve null si falta el tipo o el plan`() {
        assertNull(CategoriaDto.fromData(mapOf("id" to "x", "planId" to "p1", "nombre" to "n", "icono" to "i")))
        assertNull(CategoriaDto.fromData(mapOf("id" to "x", "nombre" to "n", "icono" to "i", "tipo" to "gasto")))
    }
}

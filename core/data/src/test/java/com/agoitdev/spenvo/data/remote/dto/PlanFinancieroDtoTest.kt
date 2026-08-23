package com.agoitdev.spenvo.data.remote.dto

import com.agoitdev.spenvo.domain.model.PlanFinanciero
import com.google.firebase.Timestamp
import java.time.Instant
import java.util.Date
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlanFinancieroDtoTest {

    private val instante = Instant.parse("2026-08-23T10:00:00Z")

    @Test
    fun `fromDomain y toDomain son inversos incluyendo editedBy y editedAt`() {
        val plan = PlanFinanciero(
            id = "p1",
            nombre = "Casa",
            descripcion = null,
            moneda = "ARS",
            createdBy = "user-1",
            createdAt = instante,
            updatedAt = instante,
            editedBy = "user-2",
            editedAt = instante,
            deletedAt = null,
        )

        val dto = PlanFinancieroDto.fromDomain(plan)

        assertEquals(plan, dto.toDomain())
    }

    @Test
    fun `toMap incluye editedBy y editedAt`() {
        val dto = PlanFinancieroDto.fromDomain(
            PlanFinanciero(
                id = "p1",
                nombre = "Casa",
                moneda = "ARS",
                createdBy = "user-1",
                editedBy = "user-2",
                editedAt = instante,
            ),
        )

        val mapa = dto.toMap()

        assertEquals("user-2", mapa["editedBy"])
        assertEquals(Timestamp(Date.from(instante)), mapa["editedAt"])
    }

    @Test
    fun `fromData lee editedBy y editedAt cuando estan presentes`() {
        val data = mapOf(
            "id" to "p1",
            "nombre" to "Casa",
            "moneda" to "ARS",
            "createdBy" to "user-1",
            "createdAt" to Timestamp(Date.from(instante)),
            "updatedAt" to Timestamp(Date.from(instante)),
            "editedBy" to "user-2",
            "editedAt" to Timestamp(Date.from(instante)),
        )

        val dto = PlanFinancieroDto.fromData(data)

        assertEquals("user-2", dto?.editedBy)
        assertEquals(instante, dto?.editedAt?.toInstant())
    }

    @Test
    fun `fromData tolera ausencia de editedBy y editedAt`() {
        val data = mapOf(
            "id" to "p1",
            "nombre" to "Casa",
            "moneda" to "ARS",
            "createdBy" to "user-1",
            "createdAt" to Timestamp(Date.from(instante)),
            "updatedAt" to Timestamp(Date.from(instante)),
        )

        val dto = PlanFinancieroDto.fromData(data)

        assertNull(dto?.editedBy)
        assertNull(dto?.editedAt)
    }
}

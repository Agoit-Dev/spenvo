package com.agoitdev.spenvo.data.local.mapper

import com.agoitdev.spenvo.domain.model.AccesoPlan
import com.agoitdev.spenvo.domain.model.Categoria
import com.agoitdev.spenvo.domain.model.Gasto
import com.agoitdev.spenvo.domain.model.Ingreso
import com.agoitdev.spenvo.domain.model.InvitacionEstado
import com.agoitdev.spenvo.domain.model.Monto
import com.agoitdev.spenvo.domain.model.PlanFinanciero
import com.agoitdev.spenvo.domain.model.Rol
import com.agoitdev.spenvo.domain.model.TipoCategoria
import com.agoitdev.spenvo.domain.model.Usuario
import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class MappersTest {

    @Test
    fun `usuario roundtrip entidad y dominio`() {
        val domain = Usuario(
            id = "user-1",
            nombre = "Tiago",
            email = "tiago@spenvo.dev",
            avatarUrl = "https://img/spenvo.png",
            createdAt = Instant.ofEpochMilli(1000),
            updatedAt = Instant.ofEpochMilli(2000),
        )

        val entity = domain.toEntity()
        assertEquals("user-1", entity.id)
        assertEquals(1000, entity.createdAt.toEpochMilli())
        assertEquals(domain, entity.toDomain())
    }

    @Test
    fun `plan financiero roundtrip entidad y dominio`() {
        val domain = PlanFinanciero(
            id = "plan-1",
            nombre = "Casa",
            descripcion = "Gastos del hogar",
            moneda = "EUR",
            createdBy = "user-1",
            createdAt = Instant.ofEpochMilli(1000),
            updatedAt = Instant.ofEpochMilli(2000),
            editedBy = "user-2",
            editedAt = Instant.ofEpochMilli(3000),
            deletedAt = null,
        )

        val entity = domain.toEntity()
        assertEquals("Casa", entity.nombre)
        assertEquals("user-2", entity.editedBy)
        assertEquals(domain, entity.toDomain())
    }

    @Test
    fun `acceso plan roundtrip entidad y dominio`() {
        val domain = AccesoPlan(
            usuarioId = "user-1",
            planId = "plan-1",
            rol = Rol.ADMIN,
            invitacionEstado = InvitacionEstado.ACEPTADA,
            createdAt = Instant.ofEpochMilli(1000),
            updatedAt = Instant.ofEpochMilli(2000),
        )

        val entity = domain.toEntity()
        assertEquals(Rol.ADMIN, entity.rol)
        assertEquals(InvitacionEstado.ACEPTADA, entity.invitacionEstado)
        assertEquals(domain, entity.toDomain())
    }

    @Test
    fun `acceso plan usa pendiente por defecto`() {
        val domain = AccesoPlan(
            usuarioId = "user-1",
            planId = "plan-1",
            rol = Rol.VIEWER,
        )

        assertEquals(InvitacionEstado.PENDIENTE, domain.toEntity().invitacionEstado)
    }

    @Test
    fun `categoria roundtrip entidad y dominio`() {
        val domain = Categoria(
            id = "cat-1",
            planId = "plan-1",
            nombre = "Comida",
            icono = "restaurant",
            iconoUrl = "https://img/restaurant.png",
            tipo = TipoCategoria.GASTO,
            editedBy = "user-1",
            editedAt = Instant.ofEpochMilli(3000),
            deletedAt = null,
        )

        val entity = domain.toEntity()
        assertEquals(TipoCategoria.GASTO, entity.tipo)
        assertEquals(domain, entity.toDomain())
    }

    @Test
    fun `gasto roundtrip entidad y dominio`() {
        val domain = Gasto(
            id = "mov-1",
            planId = "plan-1",
            categoriaId = "cat-1",
            monto = Monto(2500),
            fecha = LocalDate.of(2026, 8, 20),
            descripcion = "Mercadona",
            creadoPor = "user-1",
            createdAt = Instant.ofEpochMilli(1000),
            updatedAt = Instant.ofEpochMilli(2000),
            editedBy = "user-2",
            editedAt = Instant.ofEpochMilli(3000),
            deletedAt = null,
        )

        val entity = domain.toEntity()
        assertEquals(2500, entity.montoUnidadesMenores)
        assertEquals(LocalDate.of(2026, 8, 20), entity.fecha)
        assertEquals(domain, entity.toDomain())
    }

    @Test
    fun `ingreso roundtrip entidad y dominio`() {
        val domain = Ingreso(
            id = "mov-2",
            planId = "plan-1",
            categoriaId = "cat-2",
            monto = Monto(150000),
            fecha = LocalDate.of(2026, 8, 1),
            descripcion = "Nomina",
            creadoPor = "user-1",
            createdAt = Instant.ofEpochMilli(1000),
            updatedAt = Instant.ofEpochMilli(2000),
            editedBy = null,
            editedAt = null,
            deletedAt = null,
        )

        val entity = domain.toEntity()
        assertEquals(150000, entity.montoUnidadesMenores)
        assertEquals(domain, entity.toDomain())
    }
}

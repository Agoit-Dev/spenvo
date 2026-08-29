package com.agoitdev.spenvo.data.remote.dto

import com.agoitdev.spenvo.domain.model.Usuario
import com.google.firebase.Timestamp
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UsuarioDtoTest {

    @Test
    fun `fromDomain y toDomain hacen un round-trip completo`() {
        val usuario = Usuario(
            id = "u1",
            nombreUsuario = "GatoAzul42",
            nombre = "Ana",
            email = "ana@example.com",
            avatarUrl = "https://example.com/a.jpg",
            createdAt = Instant.parse("2026-08-30T10:00:00Z"),
            updatedAt = Instant.parse("2026-08-30T10:00:00Z"),
        )

        val dto = UsuarioDto.fromDomain(usuario)
        val vuelta = dto.toDomain()

        assertEquals(usuario, vuelta)
    }

    @Test
    fun `fromData con campos ausentes usa null para nombre y email`() {
        val data = mapOf(
            "uid" to "u1",
            "nombreUsuario" to "GatoAzul42",
            "createdAt" to Timestamp.now(),
            "updatedAt" to Timestamp.now(),
        )

        val dto = UsuarioDto.fromData(data)

        assertEquals("u1", dto?.uid)
        assertNull(dto?.nombre)
        assertNull(dto?.email)
        assertNull(dto?.avatarUrl)
    }

    @Test
    fun `fromData sin nombreUsuario devuelve null`() {
        val data = mapOf("uid" to "u1", "createdAt" to Timestamp.now(), "updatedAt" to Timestamp.now())

        assertNull(UsuarioDto.fromData(data))
    }
}

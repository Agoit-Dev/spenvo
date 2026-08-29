package com.agoitdev.spenvo.domain.usecase

import com.agoitdev.spenvo.domain.model.Usuario
import com.agoitdev.spenvo.domain.repository.UsuarioRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GenerarNombreUsuarioUnicoUseCaseTest {

    @Test
    fun `genera un candidato y lo reserva en el primer intento si esta libre`() = runTest {
        val repo = FakeUsuarioRepository()
        val useCase = GenerarNombreUsuarioUnicoUseCase(repo)

        val nombreUsuario = useCase("u1")

        assertEquals(1, repo.intentosDeReserva)
        assertTrue(repo.reservados.containsKey(nombreUsuario.lowercase()))
        assertEquals("u1", repo.reservados[nombreUsuario.lowercase()])
    }

    @Test
    fun `reintenta con otro candidato si el primero esta tomado`() = runTest {
        val repo = FakeUsuarioRepository(rechazarPrimerosIntentos = 2)
        val useCase = GenerarNombreUsuarioUnicoUseCase(repo)

        val nombreUsuario = useCase("u1")

        assertEquals(3, repo.intentosDeReserva)
        assertTrue(repo.reservados.containsKey(nombreUsuario.lowercase()))
    }

    @Test
    fun `falla si ningun intento logra reservar`() = runTest {
        val repo = FakeUsuarioRepository(rechazarPrimerosIntentos = Int.MAX_VALUE)
        val useCase = GenerarNombreUsuarioUnicoUseCase(repo)

        val resultado = runCatching { useCase("u1") }

        assertTrue(resultado.isFailure)
    }
}

private class FakeUsuarioRepository(
    private val rechazarPrimerosIntentos: Int = 0,
) : UsuarioRepository {
    var intentosDeReserva = 0
    val reservados = mutableMapOf<String, String>()

    override suspend fun obtener(usuarioId: String): Usuario? = null
    override suspend fun obtenerVarios(usuarioIds: List<String>): List<Usuario> = emptyList()

    override suspend fun intentarReservarNombreUsuario(
        nombreUsuarioNormalizado: String,
        usuarioId: String,
    ): Boolean {
        intentosDeReserva++
        val disponible = intentosDeReserva > rechazarPrimerosIntentos &&
            !reservados.containsKey(nombreUsuarioNormalizado)
        if (disponible) reservados[nombreUsuarioNormalizado] = usuarioId
        return disponible
    }

    override suspend fun crear(usuario: Usuario) = Unit
    override suspend fun actualizar(usuario: Usuario) = Unit
    override suspend fun renombrar(usuarioId: String, anterior: String, nuevo: String): Boolean = true
    override suspend fun registrarIndiceEmail(usuarioId: String, emailNormalizado: String) = Unit
    override suspend fun resolverPorNombreUsuario(nombreUsuarioNormalizado: String): String? = null
    override suspend fun resolverPorEmail(emailNormalizado: String): String? = null
}

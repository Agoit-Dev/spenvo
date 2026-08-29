package com.agoitdev.spenvo.domain.usecase

import com.agoitdev.spenvo.domain.model.Usuario
import com.agoitdev.spenvo.domain.repository.UsuarioRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class AsegurarUsuarioUseCaseTest {

    @Test
    fun `crea un Usuario nuevo con nombreUsuario generado si no existe`() = runTest {
        val repo = FakeUsuarioRepositorioAsegurar()
        val generar = GenerarNombreUsuarioUnicoUseCase(repo)
        val useCase = AsegurarUsuarioUseCase(repo, generar)

        useCase.paraSesionAnonima(usuarioId = "u1")

        val creado = repo.creados.single()
        assertEquals("u1", creado.id)
        assertNotNull(creado.nombreUsuario)
        assertNull(creado.nombre)
        assertNull(creado.email)
    }

    @Test
    fun `no crea de nuevo si el Usuario ya existe`() = runTest {
        val existente = Usuario(id = "u1", nombreUsuario = "GatoAzul1")
        val repo = FakeUsuarioRepositorioAsegurar(existentes = listOf(existente))
        val useCase = AsegurarUsuarioUseCase(repo, GenerarNombreUsuarioUnicoUseCase(repo))

        useCase.paraSesionAnonima(usuarioId = "u1")

        assertEquals(0, repo.creados.size)
    }

    @Test
    fun `al vincular email actualiza nombre y email conservando el nombreUsuario`() = runTest {
        val existente = Usuario(id = "u1", nombreUsuario = "GatoAzul1")
        val repo = FakeUsuarioRepositorioAsegurar(existentes = listOf(existente))
        val useCase = AsegurarUsuarioUseCase(repo, GenerarNombreUsuarioUnicoUseCase(repo))

        useCase.paraVincularEmail(usuarioId = "u1", nombre = "Ana", email = "ana@example.com")

        val actualizado = repo.actualizados.single()
        assertEquals("GatoAzul1", actualizado.nombreUsuario)
        assertEquals("Ana", actualizado.nombre)
        assertEquals("ana@example.com", actualizado.email)
        assertEquals(listOf("ana@example.com" to "u1"), repo.indicesEmail)
    }
}

private class FakeUsuarioRepositorioAsegurar(
    existentes: List<Usuario> = emptyList(),
) : UsuarioRepository {
    private val usuarios = existentes.associateBy { it.id }.toMutableMap()
    val creados = mutableListOf<Usuario>()
    val actualizados = mutableListOf<Usuario>()
    val indicesEmail = mutableListOf<Pair<String, String>>()

    override suspend fun obtener(usuarioId: String): Usuario? = usuarios[usuarioId]
    override suspend fun obtenerVarios(usuarioIds: List<String>): List<Usuario> =
        usuarioIds.mapNotNull { usuarios[it] }

    override suspend fun intentarReservarNombreUsuario(
        nombreUsuarioNormalizado: String,
        usuarioId: String,
    ): Boolean = true

    override suspend fun crear(usuario: Usuario) {
        creados.add(usuario)
        usuarios[usuario.id] = usuario
    }

    override suspend fun actualizar(usuario: Usuario) {
        actualizados.add(usuario)
        usuarios[usuario.id] = usuario
    }

    override suspend fun renombrar(
        usuarioId: String,
        nombreUsuarioAnterior: String,
        nombreUsuarioNuevo: String,
    ): Boolean = true

    override suspend fun registrarIndiceEmail(usuarioId: String, emailNormalizado: String) {
        indicesEmail.add(emailNormalizado to usuarioId)
    }

    override suspend fun resolverPorNombreUsuario(nombreUsuarioNormalizado: String): String? = null
    override suspend fun resolverPorEmail(emailNormalizado: String): String? = null
}

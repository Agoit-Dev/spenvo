package com.agoitdev.spenvo.domain.usecase

import com.agoitdev.spenvo.domain.model.Usuario
import com.agoitdev.spenvo.domain.repository.UsuarioRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RenombrarUsuarioUseCaseTest {

    @Test
    fun `renombra exitosamente cuando el repositorio confirma la reserva`() = runTest {
        val repo = FakeUsuarioRepositorioRenombrar(resultadoRenombrar = true)
        val useCase = RenombrarUsuarioUseCase(repo)

        val resultado = useCase(
            usuarioId = "u1",
            nombreUsuarioAnterior = "GatoAzul1",
            nombreUsuarioNuevo = "ZorroVeloz9",
        )

        assertTrue(resultado)
    }

    @Test
    fun `devuelve false cuando el nuevo nombreUsuario ya esta tomado`() = runTest {
        val repo = FakeUsuarioRepositorioRenombrar(resultadoRenombrar = false)
        val useCase = RenombrarUsuarioUseCase(repo)

        val resultado = useCase(
            usuarioId = "u1",
            nombreUsuarioAnterior = "GatoAzul1",
            nombreUsuarioNuevo = "ZorroVeloz9",
        )

        assertFalse(resultado)
    }

    @Test
    fun `pasa el usuarioId y los nombres tal cual los recibio al repositorio`() = runTest {
        val repo = FakeUsuarioRepositorioRenombrar(resultadoRenombrar = true)
        val useCase = RenombrarUsuarioUseCase(repo)

        useCase(
            usuarioId = "u1",
            nombreUsuarioAnterior = "GatoAzul1",
            nombreUsuarioNuevo = "ZorroVeloz9",
        )

        assertEquals("u1", repo.usuarioIdRecibido)
        assertEquals("GatoAzul1", repo.nombreUsuarioAnteriorRecibido)
        assertEquals("ZorroVeloz9", repo.nombreUsuarioNuevoRecibido)
    }

    @Test
    fun `no normaliza mayusculas ni recorta espacios del nombreUsuario nuevo`() = runTest {
        val repo = FakeUsuarioRepositorioRenombrar(resultadoRenombrar = true)
        val useCase = RenombrarUsuarioUseCase(repo)

        useCase(
            usuarioId = "u1",
            nombreUsuarioAnterior = "GatoAzul1",
            nombreUsuarioNuevo = "  ZorroVeloz9  ",
        )

        assertEquals("  ZorroVeloz9  ", repo.nombreUsuarioNuevoRecibido)
    }
}

private class FakeUsuarioRepositorioRenombrar(
    private val resultadoRenombrar: Boolean,
) : UsuarioRepository {
    var usuarioIdRecibido: String? = null
    var nombreUsuarioAnteriorRecibido: String? = null
    var nombreUsuarioNuevoRecibido: String? = null

    override suspend fun obtener(usuarioId: String): Usuario? = null
    override suspend fun obtenerVarios(usuarioIds: List<String>): List<Usuario> = emptyList()

    override suspend fun intentarReservarNombreUsuario(
        nombreUsuarioNormalizado: String,
        usuarioId: String,
    ): Boolean = true

    override suspend fun crear(usuario: Usuario) = Unit
    override suspend fun actualizar(usuario: Usuario) = Unit

    override suspend fun renombrar(
        usuarioId: String,
        nombreUsuarioAnterior: String,
        nombreUsuarioNuevo: String,
    ): Boolean {
        usuarioIdRecibido = usuarioId
        nombreUsuarioAnteriorRecibido = nombreUsuarioAnterior
        nombreUsuarioNuevoRecibido = nombreUsuarioNuevo
        return resultadoRenombrar
    }

    override suspend fun registrarIndiceEmail(usuarioId: String, emailNormalizado: String) = Unit
    override suspend fun resolverPorNombreUsuario(nombreUsuarioNormalizado: String): String? = null
    override suspend fun resolverPorEmail(emailNormalizado: String): String? = null
}

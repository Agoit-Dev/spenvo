package com.agoitdev.spenvo.domain.usecase

import com.agoitdev.spenvo.domain.model.AccesoPlan
import com.agoitdev.spenvo.domain.model.InvitacionPendiente
import com.agoitdev.spenvo.domain.model.Rol
import com.agoitdev.spenvo.domain.model.Usuario
import com.agoitdev.spenvo.domain.repository.AccesoPlanRepository
import com.agoitdev.spenvo.domain.repository.InvitacionPendienteRepository
import com.agoitdev.spenvo.domain.repository.UsuarioRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AsegurarUsuarioUseCaseTest {

    private fun useCase(
        repo: UsuarioRepository,
        accesosRepo: AccesoPlanRepository = FakeAccesoPlanRepositorioAsegurar(),
        pendientesRepo: InvitacionPendienteRepository = FakePendientesRepositorioAsegurar(),
    ) = AsegurarUsuarioUseCase(repo, GenerarNombreUsuarioUnicoUseCase(repo), accesosRepo, pendientesRepo)

    @Test
    fun `crea un Usuario nuevo con nombreUsuario generado si no existe`() = runTest {
        val repo = FakeUsuarioRepositorioAsegurar()
        val useCase = useCase(repo)

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
        val useCase = useCase(repo)

        useCase.paraSesionAnonima(usuarioId = "u1")

        assertEquals(0, repo.creados.size)
    }

    @Test
    fun `al vincular email actualiza nombre y email conservando el nombreUsuario`() = runTest {
        val existente = Usuario(id = "u1", nombreUsuario = "GatoAzul1")
        val repo = FakeUsuarioRepositorioAsegurar(existentes = listOf(existente))
        val useCase = useCase(repo)

        useCase.paraVincularEmail(usuarioId = "u1", nombre = "Ana", email = "ana@example.com")

        val actualizado = repo.actualizados.single()
        assertEquals("GatoAzul1", actualizado.nombreUsuario)
        assertEquals("Ana", actualizado.nombre)
        assertEquals("ana@example.com", actualizado.email)
        assertEquals(listOf("ana@example.com" to "u1"), repo.indicesEmail)
    }

    @Test
    fun `al vincular email resuelve invitaciones pendientes para ese email`() = runTest {
        val existente = Usuario(id = "u1", nombreUsuario = "GatoAzul1")
        val usuarioRepo = FakeUsuarioRepositorioAsegurar(existentes = listOf(existente))
        val accesosRepo = FakeAccesoPlanRepositorioAsegurar()
        val pendienteExistente =
            InvitacionPendiente(email = "ana@example.com", planId = "p1", rol = Rol.EDITOR, invitadoPor = "u2")
        val pendientesRepo = FakePendientesRepositorioAsegurar(existentes = listOf(pendienteExistente))
        val useCase = useCase(usuarioRepo, accesosRepo, pendientesRepo)

        useCase.paraVincularEmail(usuarioId = "u1", nombre = "Ana", email = "ana@example.com")

        assertEquals(listOf("u1"), accesosRepo.invitados.map { it.usuarioId })
        assertEquals(listOf(Rol.EDITOR), accesosRepo.invitados.map { it.rol })
        assertTrue(pendientesRepo.eliminadas.contains("ana@example.com" to "p1"))
    }

    @Test
    fun `al vincular email sin invitaciones pendientes no crea AccesoPlan`() = runTest {
        val existente = Usuario(id = "u1", nombreUsuario = "GatoAzul1")
        val usuarioRepo = FakeUsuarioRepositorioAsegurar(existentes = listOf(existente))
        val accesosRepo = FakeAccesoPlanRepositorioAsegurar()
        val pendientesRepo = FakePendientesRepositorioAsegurar()
        val useCase = useCase(usuarioRepo, accesosRepo, pendientesRepo)

        useCase.paraVincularEmail(usuarioId = "u1", nombre = "Ana", email = "ana@example.com")

        assertTrue(accesosRepo.invitados.isEmpty())
        assertTrue(pendientesRepo.eliminadas.isEmpty())
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

private class FakeAccesoPlanRepositorioAsegurar : AccesoPlanRepository {
    val invitados = mutableListOf<AccesoPlan>()

    override fun observarAccesosDelUsuario(usuarioId: String): Flow<List<AccesoPlan>> = flowOf(emptyList())
    override fun observarAccesosDelPlan(planId: String): Flow<List<AccesoPlan>> = flowOf(emptyList())

    override suspend fun invitarMiembro(acceso: AccesoPlan) {
        invitados.add(acceso)
    }

    override suspend fun aceptarInvitacion(usuarioId: String, planId: String) = Unit
}

private class FakePendientesRepositorioAsegurar(
    existentes: List<InvitacionPendiente> = emptyList(),
) : InvitacionPendienteRepository {
    private val pendientes = existentes.toMutableList()
    val eliminadas = mutableListOf<Pair<String, String>>()

    override suspend fun crear(invitacion: InvitacionPendiente) {
        pendientes.add(invitacion)
    }

    override suspend fun obtenerPorEmail(emailNormalizado: String): List<InvitacionPendiente> =
        pendientes.filter { it.email == emailNormalizado }

    override suspend fun eliminar(emailNormalizado: String, planId: String) {
        eliminadas.add(emailNormalizado to planId)
        pendientes.removeAll { it.email == emailNormalizado && it.planId == planId }
    }
}

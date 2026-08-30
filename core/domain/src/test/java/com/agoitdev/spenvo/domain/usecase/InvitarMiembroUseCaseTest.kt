package com.agoitdev.spenvo.domain.usecase

import com.agoitdev.spenvo.domain.model.AccesoPlan
import com.agoitdev.spenvo.domain.model.InvitacionPendiente
import com.agoitdev.spenvo.domain.model.Rol
import com.agoitdev.spenvo.domain.repository.AccesoPlanRepository
import com.agoitdev.spenvo.domain.repository.InvitacionPendienteRepository
import com.agoitdev.spenvo.domain.repository.UsuarioRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class InvitarMiembroUseCaseTest {

    @Test
    fun `invitar por nombreUsuario resuelto crea el AccesoPlan directo`() = runTest {
        val usuarioRepo = FakeUsuarioRepositorioInvitar(resolucionesNombreUsuario = mapOf("gatoazul1" to "u1"))
        val accesosRepo = FakeAccesoPlanRepositorioInvitar()
        val pendientesRepo = FakePendientesRepositorioInvitar()
        val useCase = InvitarMiembroUseCase(accesosRepo, usuarioRepo, pendientesRepo)

        useCase(planId = "p1", identificador = "GatoAzul1", rol = Rol.EDITOR, invitadoPor = "u2")

        assertEquals(listOf("u1"), accesosRepo.invitados.map { it.usuarioId })
        assertEquals(listOf("p1"), accesosRepo.invitados.map { it.planId })
        assertEquals(listOf(Rol.EDITOR), accesosRepo.invitados.map { it.rol })
        assertTrue(pendientesRepo.creadas.isEmpty())
    }

    @Test
    fun `invitar por email resuelto usa la busqueda por email, no por nombreUsuario`() = runTest {
        val usuarioRepo = FakeUsuarioRepositorioInvitar(resolucionesEmail = mapOf("familia@example.com" to "u3"))
        val accesosRepo = FakeAccesoPlanRepositorioInvitar()
        val pendientesRepo = FakePendientesRepositorioInvitar()
        val useCase = InvitarMiembroUseCase(accesosRepo, usuarioRepo, pendientesRepo)

        useCase(planId = "p1", identificador = "Familia@Example.com", rol = Rol.VIEWER, invitadoPor = "u2")

        assertEquals(listOf("familia@example.com"), usuarioRepo.emailsConsultados)
        assertTrue(usuarioRepo.nombresUsuarioConsultados.isEmpty())
        assertEquals(listOf("u3"), accesosRepo.invitados.map { it.usuarioId })
        assertTrue(pendientesRepo.creadas.isEmpty())
    }

    @Test
    fun `invitar por email no resuelto crea una invitacion pendiente con los datos exactos`() = runTest {
        val usuarioRepo = FakeUsuarioRepositorioInvitar()
        val accesosRepo = FakeAccesoPlanRepositorioInvitar()
        val pendientesRepo = FakePendientesRepositorioInvitar()
        val useCase = InvitarMiembroUseCase(accesosRepo, usuarioRepo, pendientesRepo)

        useCase(planId = "p1", identificador = "Familia@Example.com", rol = Rol.VIEWER, invitadoPor = "u2")

        assertTrue(accesosRepo.invitados.isEmpty())
        val pendiente = pendientesRepo.creadas.single()
        assertEquals("familia@example.com", pendiente.email)
        assertEquals("p1", pendiente.planId)
        assertEquals(Rol.VIEWER, pendiente.rol)
        assertEquals("u2", pendiente.invitadoPor)
    }

    @Test
    fun `invitar por nombreUsuario no resuelto no crea nada, sin lanzar`() = runTest {
        val usuarioRepo = FakeUsuarioRepositorioInvitar()
        val accesosRepo = FakeAccesoPlanRepositorioInvitar()
        val pendientesRepo = FakePendientesRepositorioInvitar()
        val useCase = InvitarMiembroUseCase(accesosRepo, usuarioRepo, pendientesRepo)

        useCase(planId = "p1", identificador = "NoExiste99", rol = Rol.VIEWER, invitadoPor = "u2")

        assertTrue(accesosRepo.invitados.isEmpty())
        assertTrue(pendientesRepo.creadas.isEmpty())
    }

    @Test
    fun `nombreUsuario no resuelto espera la misma cantidad de llamadas que las demas ramas`() = runTest {
        // Anti-enumeracion: si esta rama esperara una sola llamada mientras las otras tres esperan
        // dos, el tiempo de respuesta por si solo delataria si el nombreUsuario existia o no.
        val usuarioRepo = FakeUsuarioRepositorioInvitar()
        val accesosRepo = FakeAccesoPlanRepositorioInvitar()
        val pendientesRepo = FakePendientesRepositorioInvitar()
        val useCase = InvitarMiembroUseCase(accesosRepo, usuarioRepo, pendientesRepo)

        useCase(planId = "p1", identificador = "NoExiste99", rol = Rol.VIEWER, invitadoPor = "u2")

        assertEquals(listOf("noexiste99", "noexiste99"), usuarioRepo.nombresUsuarioConsultados)
    }

    @Test(expected = IllegalStateException::class)
    fun `una falla real de Firestore al resolver se propaga, no se confunde con no-resuelto`() = runTest {
        val excepcion = IllegalStateException("PERMISSION_DENIED")
        val usuarioRepo = FakeUsuarioRepositorioInvitar(excepcionResolverEmail = excepcion)
        val accesosRepo = FakeAccesoPlanRepositorioInvitar()
        val pendientesRepo = FakePendientesRepositorioInvitar()
        val useCase = InvitarMiembroUseCase(accesosRepo, usuarioRepo, pendientesRepo)

        useCase(planId = "p1", identificador = "familia@example.com", rol = Rol.VIEWER, invitadoPor = "u2")
    }
}

private class FakeUsuarioRepositorioInvitar(
    private val resolucionesNombreUsuario: Map<String, String> = emptyMap(),
    private val resolucionesEmail: Map<String, String> = emptyMap(),
    private val excepcionResolverEmail: Throwable? = null,
) : UsuarioRepository {
    val emailsConsultados = mutableListOf<String>()
    val nombresUsuarioConsultados = mutableListOf<String>()

    override suspend fun obtener(usuarioId: String): com.agoitdev.spenvo.domain.model.Usuario? = null
    override suspend fun obtenerVarios(
        usuarioIds: List<String>,
    ): List<com.agoitdev.spenvo.domain.model.Usuario> = emptyList()

    override suspend fun intentarReservarNombreUsuario(
        nombreUsuarioNormalizado: String,
        usuarioId: String,
    ): Boolean = true

    override suspend fun crear(usuario: com.agoitdev.spenvo.domain.model.Usuario) = Unit
    override suspend fun actualizar(usuario: com.agoitdev.spenvo.domain.model.Usuario) = Unit

    override suspend fun renombrar(
        usuarioId: String,
        nombreUsuarioAnterior: String,
        nombreUsuarioNuevo: String,
    ): Boolean = true

    override suspend fun registrarIndiceEmail(usuarioId: String, emailNormalizado: String) = Unit

    override suspend fun resolverPorNombreUsuario(nombreUsuarioNormalizado: String): String? {
        nombresUsuarioConsultados.add(nombreUsuarioNormalizado)
        return resolucionesNombreUsuario[nombreUsuarioNormalizado]
    }

    override suspend fun resolverPorEmail(emailNormalizado: String): String? {
        emailsConsultados.add(emailNormalizado)
        excepcionResolverEmail?.let { throw it }
        return resolucionesEmail[emailNormalizado]
    }
}

private class FakeAccesoPlanRepositorioInvitar : AccesoPlanRepository {
    val invitados = mutableListOf<AccesoPlan>()

    override fun observarAccesosDelUsuario(usuarioId: String): Flow<List<AccesoPlan>> = flowOf(emptyList())
    override fun observarAccesosDelPlan(planId: String): Flow<List<AccesoPlan>> = flowOf(emptyList())

    override suspend fun invitarMiembro(acceso: AccesoPlan) {
        invitados.add(acceso)
    }

    override suspend fun aceptarInvitacion(usuarioId: String, planId: String) = Unit
}

private class FakePendientesRepositorioInvitar : InvitacionPendienteRepository {
    val creadas = mutableListOf<InvitacionPendiente>()

    override suspend fun crear(invitacion: InvitacionPendiente) {
        creadas.add(invitacion)
    }

    override suspend fun obtenerPorEmail(emailNormalizado: String): List<InvitacionPendiente> = emptyList()
    override suspend fun eliminar(emailNormalizado: String, planId: String) = Unit
}

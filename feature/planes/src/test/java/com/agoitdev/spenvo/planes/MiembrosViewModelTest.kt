package com.agoitdev.spenvo.planes

import com.agoitdev.spenvo.domain.model.AccesoPlan
import com.agoitdev.spenvo.domain.model.InvitacionPendiente
import com.agoitdev.spenvo.domain.model.Rol
import com.agoitdev.spenvo.domain.model.Sesion
import com.agoitdev.spenvo.domain.model.Usuario
import com.agoitdev.spenvo.domain.repository.AccesoPlanRepository
import com.agoitdev.spenvo.domain.repository.AnalyticsRepository
import com.agoitdev.spenvo.domain.repository.AuthRepository
import com.agoitdev.spenvo.domain.repository.InvitacionPendienteRepository
import com.agoitdev.spenvo.domain.repository.UsuarioRepository
import com.agoitdev.spenvo.domain.usecase.InvitarMiembroUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MiembrosViewModelTest {

    private val accesosRepo = FakeAccesoPlanRepositorioMiembros()
    private val usuarioRepo = FakeUsuarioRepositorioMiembros()
    private val pendientesRepo = FakePendientesRepositorioMiembros()
    private val authRepo = FakeAuthRepositorioMiembros()
    private val analyticsRepo = FakeAnalyticsRepositorioMiembros()

    @Before
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun crearViewModel(
        accesosRepo: AccesoPlanRepository = this.accesosRepo,
        usuarioRepo: UsuarioRepository = this.usuarioRepo,
        pendientesRepo: InvitacionPendienteRepository = this.pendientesRepo,
        authRepo: AuthRepository = this.authRepo,
    ) = MiembrosViewModel(
        accesosRepository = accesosRepo,
        invitarMiembro = InvitarMiembroUseCase(accesosRepo, usuarioRepo, pendientesRepo, analyticsRepo),
        usuarioRepository = usuarioRepo,
        authRepository = authRepo,
    )

    @Test
    fun `resuelve el nombreUsuario de cada miembro del plan`() = runTest {
        val accesosRepo = FakeAccesoPlanRepositorioMiembros(
            accesos = listOf(AccesoPlan(usuarioId = "u1", planId = "p1", rol = Rol.EDITOR)),
        )
        val usuarioRepo = FakeUsuarioRepositorioMiembros(
            existentes = listOf(Usuario(id = "u1", nombreUsuario = "GatoAzul1")),
        )
        val viewModel = crearViewModel(accesosRepo = accesosRepo, usuarioRepo = usuarioRepo)
        val miembros = viewModel.miembrosResueltos("p1")

        val job = launch { miembros.collect {} }
        advanceUntilIdle()

        assertEquals("GatoAzul1", miembros.value.single().usuario?.nombreUsuario)
        job.cancel()
    }

    @Test
    fun `deja usuario en null cuando aun no se pudo resolver el usuario`() = runTest {
        val accesosRepo = FakeAccesoPlanRepositorioMiembros(
            accesos = listOf(AccesoPlan(usuarioId = "u1", planId = "p1", rol = Rol.EDITOR)),
        )
        val usuarioRepo = FakeUsuarioRepositorioMiembros(existentes = emptyList())
        val viewModel = crearViewModel(accesosRepo = accesosRepo, usuarioRepo = usuarioRepo)
        val miembros = viewModel.miembrosResueltos("p1")

        val job = launch { miembros.collect {} }
        advanceUntilIdle()

        val resuelto = miembros.value.single()
        assertEquals("u1", resuelto.acceso.usuarioId)
        assertNull(resuelto.usuario)
        job.cancel()
    }

    @Test
    fun `invitar por nombreUsuario resuelto crea el acceso y marca invitado`() = runTest {
        val usuarioRepo = FakeUsuarioRepositorioMiembros(resolucionesNombreUsuario = mapOf("gatoazul1" to "u1"))
        val viewModel = crearViewModel(usuarioRepo = usuarioRepo)

        viewModel.invitar(planId = "p1", identificador = "GatoAzul1", rol = Rol.EDITOR)
        advanceUntilIdle()

        assertEquals(listOf("u1"), accesosRepo.invitados.map { it.usuarioId })
        assertTrue(viewModel.estadoInvitar.value.invitado)
        assertNull(viewModel.estadoInvitar.value.error)
    }

    @Test
    fun `invitar con un identificador que no resuelve a ninguna cuenta igual marca invitado sin error`() = runTest {
        // This is the anti-enumeration guarantee: the UI must show the exact same confirmation
        // whether or not a real account was found. A broken implementation that branched the
        // outcome on resolution success would leave `invitado` false or `error` non-null here.
        val viewModel = crearViewModel()

        viewModel.invitar(planId = "p1", identificador = "NoExisteNadie99", rol = Rol.VIEWER)
        advanceUntilIdle()

        assertTrue(accesosRepo.invitados.isEmpty())
        assertTrue(pendientesRepo.creadas.isEmpty())
        assertTrue(viewModel.estadoInvitar.value.invitado)
        assertNull(viewModel.estadoInvitar.value.error)
    }

    @Test
    fun `invitar por email no resuelto crea invitacion pendiente y tambien marca invitado`() = runTest {
        val viewModel = crearViewModel()

        viewModel.invitar(planId = "p1", identificador = "familia@example.com", rol = Rol.VIEWER)
        advanceUntilIdle()

        assertEquals(listOf("familia@example.com"), pendientesRepo.creadas.map { it.email })
        assertEquals(listOf("user-1"), pendientesRepo.creadas.map { it.invitadoPor })
        assertTrue(viewModel.estadoInvitar.value.invitado)
        assertNull(viewModel.estadoInvitar.value.error)
    }

    @Test
    fun `invitar con identificador en blanco expone un error de validacion sin llamar al caso de uso`() = runTest {
        val viewModel = crearViewModel()

        viewModel.invitar(planId = "p1", identificador = "   ", rol = Rol.VIEWER)
        advanceUntilIdle()

        assertTrue(accesosRepo.invitados.isEmpty())
        assertTrue(pendientesRepo.creadas.isEmpty())
        assertEquals(false, viewModel.estadoInvitar.value.invitado)
        // El mensaje vive en strings.xml (es/en), no hardcodeado en el ViewModel.
        assertNull(viewModel.estadoInvitar.value.error)
        assertEquals(R.string.members_invite_identificador_requerido, viewModel.estadoInvitar.value.errorRes)
    }

    @Test
    fun `consumirError limpia tanto el mensaje de fallo como el error de validacion`() = runTest {
        val viewModel = crearViewModel()

        viewModel.invitar(planId = "p1", identificador = "   ", rol = Rol.VIEWER)
        advanceUntilIdle()
        viewModel.consumirError()

        assertNull(viewModel.estadoInvitar.value.error)
        assertNull(viewModel.estadoInvitar.value.errorRes)
    }
}

private class FakeAccesoPlanRepositorioMiembros(
    private val accesos: List<AccesoPlan> = emptyList(),
) : AccesoPlanRepository {
    val invitados = mutableListOf<AccesoPlan>()

    override fun observarAccesosDelUsuario(usuarioId: String): Flow<List<AccesoPlan>> =
        flowOf(accesos.filter { it.usuarioId == usuarioId })

    override fun observarAccesosDelPlan(planId: String): Flow<List<AccesoPlan>> =
        flowOf(accesos.filter { it.planId == planId })

    override suspend fun invitarMiembro(acceso: AccesoPlan) {
        invitados.add(acceso)
    }

    override suspend fun aceptarInvitacion(usuarioId: String, planId: String) = Unit
}

private class FakeUsuarioRepositorioMiembros(
    private val existentes: List<Usuario> = emptyList(),
    private val resolucionesNombreUsuario: Map<String, String> = emptyMap(),
    private val resolucionesEmail: Map<String, String> = emptyMap(),
) : UsuarioRepository {
    override suspend fun obtener(usuarioId: String): Usuario? = existentes.find { it.id == usuarioId }

    override suspend fun obtenerVarios(usuarioIds: List<String>): List<Usuario> =
        existentes.filter { it.id in usuarioIds }

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
    ): Boolean = true

    override suspend fun registrarIndiceEmail(usuarioId: String, emailNormalizado: String) = Unit

    override suspend fun resolverPorNombreUsuario(nombreUsuarioNormalizado: String): String? =
        resolucionesNombreUsuario[nombreUsuarioNormalizado]

    override suspend fun resolverPorEmail(emailNormalizado: String): String? = resolucionesEmail[emailNormalizado]
}

private class FakePendientesRepositorioMiembros : InvitacionPendienteRepository {
    val creadas = mutableListOf<InvitacionPendiente>()

    override suspend fun crear(invitacion: InvitacionPendiente) {
        creadas.add(invitacion)
    }

    override suspend fun obtenerPorEmail(emailNormalizado: String): List<InvitacionPendiente> = emptyList()
    override suspend fun eliminar(emailNormalizado: String, planId: String) = Unit
}

private class FakeAuthRepositorioMiembros : AuthRepository {
    private val sesionFlow = MutableStateFlow(Sesion(uid = "user-1", esAnonima = false))

    override fun observeSesion(): Flow<Sesion> = sesionFlow
    override suspend fun iniciarSesionAnonima() = Unit
    override suspend fun vincularEmail(email: String, password: String, nombre: String) = Unit
    override suspend fun iniciarSesionConEmail(email: String, password: String) = Unit
    override suspend fun enviarRecuperacionPassword(email: String) = Unit
    override suspend fun actualizarPerfil(nombre: String?, photoUrl: String?) = Unit
    override suspend fun cerrarSesion() = Unit
}

private class FakeAnalyticsRepositorioMiembros : AnalyticsRepository {
    val eventosRegistrados = mutableListOf<String>()

    override fun registrarEvento(nombre: String) {
        eventosRegistrados.add(nombre)
    }
}

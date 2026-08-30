package com.agoitdev.spenvo.planes

import com.agoitdev.spenvo.domain.model.AccesoPlan
import com.agoitdev.spenvo.domain.model.Rol
import com.agoitdev.spenvo.domain.model.Usuario
import com.agoitdev.spenvo.domain.repository.AccesoPlanRepository
import com.agoitdev.spenvo.domain.repository.UsuarioRepository
import com.agoitdev.spenvo.domain.usecase.InvitarMiembroUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
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
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MiembrosViewModelTest {

    private val accesosRepo = FakeAccesoPlanRepositorioMiembros()
    private val usuarioRepo = FakeUsuarioRepositorioMiembros()

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
    ) = MiembrosViewModel(
        accesosRepository = accesosRepo,
        invitarMiembro = InvitarMiembroUseCase(accesosRepo),
        usuarioRepository = usuarioRepo,
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
}

private class FakeAccesoPlanRepositorioMiembros(
    private val accesos: List<AccesoPlan> = emptyList(),
) : AccesoPlanRepository {
    override fun observarAccesosDelUsuario(usuarioId: String): Flow<List<AccesoPlan>> =
        flowOf(accesos.filter { it.usuarioId == usuarioId })

    override fun observarAccesosDelPlan(planId: String): Flow<List<AccesoPlan>> =
        flowOf(accesos.filter { it.planId == planId })

    override suspend fun invitarMiembro(acceso: AccesoPlan) = Unit

    override suspend fun aceptarInvitacion(usuarioId: String, planId: String) = Unit
}

private class FakeUsuarioRepositorioMiembros(
    private val existentes: List<Usuario> = emptyList(),
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

    override suspend fun resolverPorNombreUsuario(nombreUsuarioNormalizado: String): String? = null

    override suspend fun resolverPorEmail(emailNormalizado: String): String? = null
}

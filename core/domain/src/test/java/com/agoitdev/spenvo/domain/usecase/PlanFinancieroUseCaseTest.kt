package com.agoitdev.spenvo.domain.usecase

import com.agoitdev.spenvo.domain.model.AccesoPlan
import com.agoitdev.spenvo.domain.model.Categoria
import com.agoitdev.spenvo.domain.model.InvitacionEstado
import com.agoitdev.spenvo.domain.model.PlanFinanciero
import com.agoitdev.spenvo.domain.model.Rol
import com.agoitdev.spenvo.domain.model.TipoCategoria
import com.agoitdev.spenvo.domain.repository.AccesoPlanRepository
import com.agoitdev.spenvo.domain.repository.CategoriaRepository
import com.agoitdev.spenvo.domain.repository.PlanFinancieroRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CrearPlanUseCaseTest {

    private val planesRepo = FakePlanRepository()
    private val accesosRepo = FakeAccesoRepository()
    private val categoriasRepo = FakeCategoriaSeedRepository()

    @Test
    fun `crea plan y acceso OWNER aceptado y siembra categorias`() = runTest {
        val useCase = CrearPlanUseCase(
            planesRepo,
            accesosRepo,
            SembrarCategoriasPorDefectoUseCase(categoriasRepo),
        )

        val plan = useCase(
            CrearPlanRequest(
                nombre = "Casa",
                moneda = "ARS",
                creadoPor = "user-1",
            ),
        )

        assertEquals("Casa", plan.nombre)
        assertEquals("ARS", plan.moneda)
        assertEquals("user-1", plan.createdBy)
        assertEquals(1, planesRepo.creados.size)

        val acceso = accesosRepo.invitados.single()
        assertEquals("user-1", acceso.usuarioId)
        assertEquals(plan.id, acceso.planId)
        assertEquals(Rol.OWNER, acceso.rol)
        assertEquals(InvitacionEstado.ACEPTADA, acceso.invitacionEstado)

        assertEquals(SembrarCategoriasPorDefectoUseCase.DEFAULT_CATEGORIAS.size, categoriasRepo.creadas.size)
        assertTrue(categoriasRepo.creadas.all { it.planId == plan.id })
    }
}

class ObservarPlanesDelUsuarioUseCaseTest {

    @Test
    fun `expone los planes del usuario`() = runTest {
        val repo = FakePlanRepository(
            planes = listOf(PlanFinanciero(id = "p1", nombre = "Casa", moneda = "ARS", createdBy = "user-1")),
        )
        val useCase = ObservarPlanesDelUsuarioUseCase(repo)

        val planes = useCase("user-1").first()

        assertEquals(listOf("p1"), planes.map { it.id })
    }
}

class InvitarMiembroUseCaseTest {

    private val accesosRepo = FakeAccesoRepository()

    @Test
    fun `invita miembro con rol y estado pendiente`() = runTest {
        val useCase = InvitarMiembroUseCase(accesosRepo)

        useCase(planId = "p1", usuarioId = "user-2", rol = Rol.EDITOR)

        val acceso = accesosRepo.invitados.single()
        assertEquals("p1", acceso.planId)
        assertEquals("user-2", acceso.usuarioId)
        assertEquals(Rol.EDITOR, acceso.rol)
        assertEquals(InvitacionEstado.PENDIENTE, acceso.invitacionEstado)
    }
}

class AceptarInvitacionUseCaseTest {

    private val accesosRepo = FakeAccesoRepository()

    @Test
    fun `acepta invitacion pendiente`() = runTest {
        val useCase = AceptarInvitacionUseCase(accesosRepo)

        useCase(usuarioId = "user-2", planId = "p1")

        assertEquals("user-2", accesosRepo.ultimoAceptadoUsuarioId)
        assertEquals("p1", accesosRepo.ultimoAceptadoPlanId)
    }
}

class ActualizarPlanUseCaseTest {

    @Test
    fun `actualiza el plan y lo persiste`() = runTest {
        val repo = FakePlanRepository()
        val useCase = ActualizarPlanUseCase(repo)
        val plan = PlanFinanciero(id = "p1", nombre = "Nuevo", moneda = "USD", createdBy = "user-1")

        useCase(plan)

        assertEquals(true, repo.fueActualizado("p1"))
    }
}

private class FakePlanRepository(
    private val planes: List<PlanFinanciero> = emptyList(),
) : PlanFinancieroRepository {
    val creados = mutableListOf<PlanFinanciero>()
    private val actualizados = mutableListOf<PlanFinanciero>()

    override fun observarPlanesDelUsuario(usuarioId: String): Flow<List<PlanFinanciero>> =
        flowOf(planes.filter { it.createdBy == usuarioId })

    override fun observarPlan(planId: String): Flow<PlanFinanciero?> =
        flowOf(planes.firstOrNull { it.id == planId })

    override suspend fun crearPlan(plan: PlanFinanciero) {
        creados.add(plan)
    }

    override suspend fun actualizarPlan(plan: PlanFinanciero) {
        actualizados.add(plan)
    }

    fun fueActualizado(id: String): Boolean = actualizados.any { it.id == id }
}

private class FakeAccesoRepository : AccesoPlanRepository {
    val invitados = mutableListOf<AccesoPlan>()
    var ultimoAceptadoUsuarioId: String? = null
    var ultimoAceptadoPlanId: String? = null

    override fun observarAccesosDelUsuario(usuarioId: String): Flow<List<AccesoPlan>> =
        flowOf(invitados.filter { it.usuarioId == usuarioId })

    override fun observarAccesosDelPlan(planId: String): Flow<List<AccesoPlan>> =
        flowOf(invitados.filter { it.planId == planId })

    override suspend fun invitarMiembro(acceso: AccesoPlan) {
        invitados.add(acceso)
    }

    override suspend fun aceptarInvitacion(usuarioId: String, planId: String) {
        ultimoAceptadoUsuarioId = usuarioId
        ultimoAceptadoPlanId = planId
        invitados.replaceAll {
            if (it.usuarioId == usuarioId && it.planId == planId) {
                it.copy(invitacionEstado = InvitacionEstado.ACEPTADA)
            } else {
                it
            }
        }
    }
}

private class FakeCategoriaSeedRepository : CategoriaRepository {
    val creadas = mutableListOf<Categoria>()

    override fun observarCategorias(planId: String): Flow<List<Categoria>> = flowOf(emptyList())

    override fun observarCategoriasPorTipo(
        planId: String,
        tipo: TipoCategoria,
    ): Flow<List<Categoria>> = flowOf(emptyList())

    override suspend fun crearCategoria(categoria: Categoria) {
        creadas.add(categoria)
    }

    override suspend fun crearCategorias(categorias: List<Categoria>) {
        creadas.addAll(categorias)
    }

    override suspend fun actualizarCategoria(categoria: Categoria) {
        // no-op
    }

    override suspend fun eliminarCategoria(categoria: Categoria) {
        // no-op
    }
}

package com.agoitdev.spenvo.domain.usecase

import com.agoitdev.spenvo.domain.model.AccesoPlan
import com.agoitdev.spenvo.domain.model.Categoria
import com.agoitdev.spenvo.domain.model.Gasto
import com.agoitdev.spenvo.domain.model.Ingreso
import com.agoitdev.spenvo.domain.model.PlanFinanciero
import com.agoitdev.spenvo.domain.model.TipoCategoria
import com.agoitdev.spenvo.domain.repository.AccesoPlanRepository
import com.agoitdev.spenvo.domain.repository.CategoriaRepository
import com.agoitdev.spenvo.domain.repository.MovimientoRepository
import com.agoitdev.spenvo.domain.repository.PlanFinancieroRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SembrarPlanEjemploUseCaseTest {

    private val planRepo = FakePlanRepo()
    private val accesoRepo = FakeAccesoRepo()
    private val categoriaRepo = FakeCategoriaRepo()
    private val movimientoRepo = FakeMovimientoRepo()

    private fun crearUseCase() = SembrarPlanEjemploUseCase(
        observarPlanes = ObservarPlanesDelUsuarioUseCase(planRepo),
        crearPlan = CrearPlanUseCase(planRepo, accesoRepo, SembrarCategoriasPorDefectoUseCase(categoriaRepo)),
        crearGasto = CrearGastoUseCase(movimientoRepo, ValidarMontoUseCase()),
        crearIngreso = CrearIngresoUseCase(movimientoRepo, ValidarMontoUseCase()),
    )

    @Test
    fun `siembra un plan de ejemplo en EUR con gastos e ingresos cuando el usuario no tiene planes`() = runTest {
        val useCase = crearUseCase()

        useCase("user-1")

        assertEquals(1, planRepo.planesCreados.size)
        assertEquals("EUR", planRepo.planesCreados.single().moneda)
        assertTrue(movimientoRepo.gastosCreados.isNotEmpty())
        assertTrue(movimientoRepo.ingresosCreados.isNotEmpty())
        assertTrue(movimientoRepo.gastosCreados.all { it.planId == planRepo.planesCreados.single().id })
    }

    @Test
    fun `no siembra nada si el usuario ya tiene al menos un plan`() = runTest {
        planRepo.planesExistentes.value = listOf(
            PlanFinanciero(id = "p-existente", nombre = "Ya existe", moneda = "USD", createdBy = "user-1"),
        )
        val useCase = crearUseCase()

        useCase("user-1")

        assertTrue(planRepo.planesCreados.isEmpty())
        assertTrue(movimientoRepo.gastosCreados.isEmpty())
        assertTrue(movimientoRepo.ingresosCreados.isEmpty())
    }
}

private class FakePlanRepo : PlanFinancieroRepository {
    val planesExistentes = MutableStateFlow<List<PlanFinanciero>>(emptyList())
    val planesCreados = mutableListOf<PlanFinanciero>()

    override fun observarPlanesDelUsuario(usuarioId: String): Flow<List<PlanFinanciero>> =
        planesExistentes.map { existentes -> existentes + planesCreados }

    override fun observarPlan(planId: String): Flow<PlanFinanciero?> = flowOf(null)
    override suspend fun crearPlan(plan: PlanFinanciero) {
        planesCreados.add(plan)
    }
    override suspend fun actualizarPlan(plan: PlanFinanciero) = Unit
}

private class FakeAccesoRepo : AccesoPlanRepository {
    override fun observarAccesosDelUsuario(usuarioId: String): Flow<List<AccesoPlan>> = flowOf(emptyList())
    override fun observarAccesosDelPlan(planId: String): Flow<List<AccesoPlan>> = flowOf(emptyList())
    override suspend fun invitarMiembro(acceso: AccesoPlan) = Unit
    override suspend fun aceptarInvitacion(usuarioId: String, planId: String) = Unit
}

private class FakeCategoriaRepo : CategoriaRepository {
    val categorias = mutableListOf<Categoria>()
    override fun observarCategorias(planId: String): Flow<List<Categoria>> =
        flowOf(categorias.filter { it.planId == planId })
    override fun observarCategoriasPorTipo(planId: String, tipo: TipoCategoria): Flow<List<Categoria>> =
        flowOf(categorias.filter { it.planId == planId && it.tipo == tipo })
    override suspend fun crearCategoria(categoria: Categoria) {
        categorias.add(categoria)
    }
    override suspend fun crearCategorias(categorias: List<Categoria>) {
        this.categorias.addAll(categorias)
    }
    override suspend fun actualizarCategoria(categoria: Categoria) = Unit
    override suspend fun eliminarCategoria(categoria: Categoria) = Unit
}

private class FakeMovimientoRepo : MovimientoRepository {
    val gastosCreados = mutableListOf<Gasto>()
    val ingresosCreados = mutableListOf<Ingreso>()
    override suspend fun addGasto(gasto: Gasto) {
        gastosCreados.add(gasto)
    }
    override suspend fun addIngreso(ingreso: Ingreso) {
        ingresosCreados.add(ingreso)
    }
    override suspend fun actualizarGasto(gasto: Gasto) = Unit
    override suspend fun eliminarGasto(gasto: Gasto) = Unit
    override suspend fun actualizarIngreso(ingreso: Ingreso) = Unit
    override suspend fun eliminarIngreso(ingreso: Ingreso) = Unit
    override fun observeGastos(planId: String): Flow<List<Gasto>> = flowOf(gastosCreados)
    override fun observeIngresos(planId: String): Flow<List<Ingreso>> = flowOf(ingresosCreados)
    override suspend fun aplicarGastoRemoto(id: String) = Unit
    override suspend fun aplicarIngresoRemoto(id: String) = Unit
    override suspend fun resolverConflictoGastoUsandoLocal(gasto: Gasto, clave: String) = Unit
    override suspend fun resolverConflictoIngresoUsandoLocal(ingreso: Ingreso, clave: String) = Unit
    override suspend fun resolverConflictoGastoUsandoRemoto(id: String, clave: String) = Unit
    override suspend fun resolverConflictoIngresoUsandoRemoto(id: String, clave: String) = Unit
}

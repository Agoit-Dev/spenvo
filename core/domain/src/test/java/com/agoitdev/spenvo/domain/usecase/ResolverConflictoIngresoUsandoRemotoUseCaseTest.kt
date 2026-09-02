package com.agoitdev.spenvo.domain.usecase

import com.agoitdev.spenvo.domain.model.Gasto
import com.agoitdev.spenvo.domain.model.Ingreso
import com.agoitdev.spenvo.domain.repository.MovimientoRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class ResolverConflictoIngresoUsandoRemotoUseCaseTest {
    @Test
    fun `delega id y clave al repositorio`() = runTest {
        val repo = FakeMovimientoRepositorioResolverConflictoRemotoIngreso()
        val useCase = ResolverConflictoIngresoUsandoRemotoUseCase(repo)

        useCase(id = "i1", clave = "ingresos:i1")

        assertEquals("i1" to "ingresos:i1", repo.idClaveResuelto)
    }
}

private class FakeMovimientoRepositorioResolverConflictoRemotoIngreso : MovimientoRepository {
    var idClaveResuelto: Pair<String, String>? = null

    override suspend fun addGasto(gasto: Gasto) = Unit
    override suspend fun addIngreso(ingreso: Ingreso) = Unit
    override suspend fun actualizarGasto(gasto: Gasto) = Unit
    override suspend fun eliminarGasto(gasto: Gasto) = Unit
    override suspend fun actualizarIngreso(ingreso: Ingreso) = Unit
    override suspend fun eliminarIngreso(ingreso: Ingreso) = Unit
    override fun observeGastos(planId: String): Flow<List<Gasto>> = flowOf(emptyList())
    override fun observeIngresos(planId: String): Flow<List<Ingreso>> = flowOf(emptyList())
    override suspend fun resolverConflictoGastoUsandoLocal(gasto: Gasto, clave: String) = Unit
    override suspend fun resolverConflictoIngresoUsandoLocal(ingreso: Ingreso, clave: String) = Unit
    override suspend fun resolverConflictoGastoUsandoRemoto(id: String, clave: String) = Unit
    override suspend fun resolverConflictoIngresoUsandoRemoto(id: String, clave: String) {
        idClaveResuelto = id to clave
    }
}

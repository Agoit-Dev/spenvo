package com.agoitdev.spenvo.domain.usecase

import com.agoitdev.spenvo.domain.model.Gasto
import com.agoitdev.spenvo.domain.model.Ingreso
import com.agoitdev.spenvo.domain.model.Monto
import com.agoitdev.spenvo.domain.repository.MovimientoRepository
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class ObservarBalanceAcumuladoPlanUseCaseTest {

    private fun gasto(id: String, montoMenor: Long, fecha: LocalDate, deletedAt: java.time.Instant? = null) = Gasto(
        id = id,
        planId = "p1",
        categoriaId = "cat-1",
        monto = Monto(montoMenor),
        fecha = fecha,
        creadoPor = "user-1",
        deletedAt = deletedAt,
    )

    private fun ingreso(id: String, montoMenor: Long, fecha: LocalDate, deletedAt: java.time.Instant? = null) = Ingreso(
        id = id,
        planId = "p1",
        categoriaId = "cat-2",
        monto = Monto(montoMenor),
        fecha = fecha,
        creadoPor = "user-1",
        deletedAt = deletedAt,
    )

    @Test
    fun `suma todos los movimientos sin importar el mes`() = runTest {
        val repo = FakeMovimientoRepositorioBalance(
            gastos = listOf(
                gasto("g1", 1000, LocalDate.of(2026, 1, 5)),
                gasto("g2", 500, LocalDate.of(2026, 8, 20)),
            ),
            ingresos = listOf(
                ingreso("i1", 3000, LocalDate.of(2026, 1, 1)),
                ingreso("i2", 3000, LocalDate.of(2026, 8, 1)),
            ),
        )
        val useCase = ObservarBalanceAcumuladoPlanUseCase(repo)

        val balance = useCase("p1").first()

        assertEquals(4500L, balance.unidadesMenores)
    }

    @Test
    fun `excluye movimientos borrados`() = runTest {
        val repo = FakeMovimientoRepositorioBalance(
            gastos = listOf(gasto("g1", 1000, LocalDate.of(2026, 8, 5), deletedAt = java.time.Instant.now())),
            ingresos = listOf(ingreso("i1", 3000, LocalDate.of(2026, 8, 1))),
        )
        val useCase = ObservarBalanceAcumuladoPlanUseCase(repo)

        val balance = useCase("p1").first()

        assertEquals(3000L, balance.unidadesMenores)
    }
}

private class FakeMovimientoRepositorioBalance(
    gastos: List<Gasto> = emptyList(),
    ingresos: List<Ingreso> = emptyList(),
) : MovimientoRepository {
    private val gastosFlow = MutableStateFlow(gastos)
    private val ingresosFlow = MutableStateFlow(ingresos)
    override suspend fun addGasto(gasto: Gasto) = Unit
    override suspend fun addIngreso(ingreso: Ingreso) = Unit
    override suspend fun actualizarGasto(gasto: Gasto) = Unit
    override suspend fun eliminarGasto(gasto: Gasto) = Unit
    override suspend fun actualizarIngreso(ingreso: Ingreso) = Unit
    override suspend fun eliminarIngreso(ingreso: Ingreso) = Unit
    override suspend fun aplicarGastoRemoto(id: String) = Unit
    override suspend fun aplicarIngresoRemoto(id: String) = Unit
    override suspend fun resolverConflictoGastoUsandoLocal(gasto: Gasto, clave: String) = Unit
    override suspend fun resolverConflictoIngresoUsandoLocal(ingreso: Ingreso, clave: String) = Unit
    override suspend fun resolverConflictoGastoUsandoRemoto(id: String, clave: String) = Unit
    override suspend fun resolverConflictoIngresoUsandoRemoto(id: String, clave: String) = Unit
    override fun observeGastos(planId: String): Flow<List<Gasto>> = gastosFlow
    override fun observeIngresos(planId: String): Flow<List<Ingreso>> = ingresosFlow
}

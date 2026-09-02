package com.agoitdev.spenvo.domain.usecase

import com.agoitdev.spenvo.domain.model.Categoria
import com.agoitdev.spenvo.domain.model.Gasto
import com.agoitdev.spenvo.domain.model.Monto
import com.agoitdev.spenvo.domain.model.TipoCategoria
import com.agoitdev.spenvo.domain.repository.MovimientoRepository
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class CrearGastoUseCaseTest {

    private val repo = FakeGastoRepository()

    @Test
    fun `crea gasto valido y lo persiste`() = runTest {
        val useCase = CrearGastoUseCase(repo, ValidarMontoUseCase())
        val categoria = Categoria(
            id = "cat-1",
            planId = "plan-1",
            nombre = "Comida",
            tipo = TipoCategoria.GASTO,
        )

        val result = useCase(
            CrearGastoRequest(
                planId = "plan-1",
                categoriaId = categoria.id,
                monto = Monto(2500),
                fecha = LocalDate.of(2026, 8, 20),
                descripcion = "Mercadona",
                creadoPor = "user-1",
            ),
        )

        assertEquals("plan-1", result.planId)
        assertEquals(Monto(2500), result.monto)
        assertEquals(1, repo.saved.size)
    }

    @Test
    fun `rechaza monto invalido sin persistir`() = runTest {
        val useCase = CrearGastoUseCase(repo, ValidarMontoUseCase())

        val error = try {
            useCase(
                CrearGastoRequest(
                    planId = "plan-1",
                    categoriaId = "cat-1",
                    monto = Monto(0),
                    fecha = LocalDate.of(2026, 8, 20),
                    creadoPor = "user-1",
                ),
            )
            null
        } catch (e: IllegalArgumentException) {
            e
        }

        assertNotNull(error)
        assertEquals(0, repo.saved.size)
    }
}

private class FakeGastoRepository : MovimientoRepository {
    val saved = mutableListOf<Gasto>()

    override suspend fun addGasto(gasto: Gasto) {
        saved.add(gasto)
    }

    override suspend fun addIngreso(ingreso: com.agoitdev.spenvo.domain.model.Ingreso) = Unit
    override suspend fun actualizarGasto(gasto: Gasto) = Unit
    override suspend fun eliminarGasto(gasto: Gasto) = Unit
    override suspend fun actualizarIngreso(ingreso: com.agoitdev.spenvo.domain.model.Ingreso) = Unit
    override suspend fun eliminarIngreso(ingreso: com.agoitdev.spenvo.domain.model.Ingreso) = Unit
    override suspend fun resolverConflictoGastoUsandoLocal(gasto: Gasto, clave: String) = Unit
    override suspend fun resolverConflictoIngresoUsandoLocal(
        ingreso: com.agoitdev.spenvo.domain.model.Ingreso,
        clave: String,
    ) = Unit
    override suspend fun resolverConflictoGastoUsandoRemoto(id: String, clave: String) = Unit
    override suspend fun resolverConflictoIngresoUsandoRemoto(id: String, clave: String) = Unit

    override fun observeGastos(planId: String): Flow<List<Gasto>> = flowOf(emptyList())

    override fun observeIngresos(planId: String): Flow<List<com.agoitdev.spenvo.domain.model.Ingreso>> =
        flowOf(emptyList())
}

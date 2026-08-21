package com.agoitdev.spenvo.domain.usecase

import com.agoitdev.spenvo.domain.model.Categoria
import com.agoitdev.spenvo.domain.model.TipoCategoria
import com.agoitdev.spenvo.domain.repository.CategoriaRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CrearCategoriaUseCaseTest {

    private val repo = FakeCategoriaRepository()

    @Test
    fun `crea categoria con nombre y tipo`() = runTest {
        val useCase = CrearCategoriaUseCase(repo)

        val categoria = useCase(
            CrearCategoriaRequest(
                planId = "p1",
                nombre = "  Comida  ",
                tipo = TipoCategoria.GASTO,
            ),
        )

        assertEquals("p1", categoria.planId)
        assertEquals("Comida", categoria.nombre)
        assertEquals(TipoCategoria.GASTO, categoria.tipo)
        assertEquals("default", categoria.icono)
        assertEquals(1, repo.creadas.size)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rechaza nombre vacio`() = runTest {
        val useCase = CrearCategoriaUseCase(repo)

        useCase(
            CrearCategoriaRequest(
                planId = "p1",
                nombre = "   ",
                tipo = TipoCategoria.GASTO,
            ),
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rechaza nombre duplicado en el mismo plan y tipo`() = runTest {
        val repo = FakeCategoriaRepository(
            categorias = listOf(
                Categoria(id = "c1", planId = "p1", nombre = "Comida", tipo = TipoCategoria.GASTO),
            ),
        )
        val useCase = CrearCategoriaUseCase(repo)

        useCase(
            CrearCategoriaRequest(
                planId = "p1",
                nombre = "Comida",
                tipo = TipoCategoria.GASTO,
            ),
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rechaza duplicado ignorando mayusculas`() = runTest {
        val repo = FakeCategoriaRepository(
            categorias = listOf(
                Categoria(id = "c1", planId = "p1", nombre = "Comida", tipo = TipoCategoria.GASTO),
            ),
        )
        val useCase = CrearCategoriaUseCase(repo)

        useCase(
            CrearCategoriaRequest(
                planId = "p1",
                nombre = "comida",
                tipo = TipoCategoria.GASTO,
            ),
        )
    }

    @Test
    fun `permite el mismo nombre en otro plan u otro tipo`() = runTest {
        val repo = FakeCategoriaRepository(
            categorias = listOf(
                Categoria(id = "c1", planId = "p1", nombre = "Comida", tipo = TipoCategoria.GASTO),
            ),
        )
        val useCase = CrearCategoriaUseCase(repo)

        useCase(
            CrearCategoriaRequest(planId = "p2", nombre = "Comida", tipo = TipoCategoria.GASTO),
        )
        useCase(
            CrearCategoriaRequest(planId = "p1", nombre = "Comida", tipo = TipoCategoria.INGRESO),
        )

        assertEquals(2, repo.creadas.size)
    }
}

class ObservarCategoriasUseCaseTest {

    @Test
    fun `expone las categorias del plan`() = runTest {
        val repo = FakeCategoriaRepository(
            categorias = listOf(
                Categoria(id = "c1", planId = "p1", nombre = "Comida", tipo = TipoCategoria.GASTO),
                Categoria(id = "c2", planId = "p2", nombre = "Otra", tipo = TipoCategoria.GASTO),
            ),
        )
        val useCase = ObservarCategoriasUseCase(repo)

        val categorias = useCase("p1").first()

        assertEquals(listOf("c1"), categorias.map { it.id })
    }
}

class ObservarCategoriasPorTipoUseCaseTest {

    @Test
    fun `expone las categorias del plan filtradas por tipo`() = runTest {
        val repo = FakeCategoriaRepository(
            categorias = listOf(
                Categoria(id = "c1", planId = "p1", nombre = "Comida", tipo = TipoCategoria.GASTO),
                Categoria(id = "c2", planId = "p1", nombre = "Sueldo", tipo = TipoCategoria.INGRESO),
                Categoria(id = "c3", planId = "p2", nombre = "Ocio", tipo = TipoCategoria.GASTO),
            ),
        )
        val useCase = ObservarCategoriasPorTipoUseCase(repo)

        val gastos = useCase("p1", TipoCategoria.GASTO).first()

        assertEquals(listOf("c1"), gastos.map { it.id })
    }
}

class ActualizarCategoriaUseCaseTest {

    private val repo = FakeCategoriaRepository()

    @Test
    fun `actualiza y persiste la categoria`() = runTest {
        val useCase = ActualizarCategoriaUseCase(repo)
        val categoria = Categoria(id = "c1", planId = "p1", nombre = "Transporte", tipo = TipoCategoria.GASTO)

        useCase(categoria)

        assertTrue(repo.fueActualizada("c1"))
    }

    @Test
    fun `recorta espacios del nombre`() = runTest {
        val useCase = ActualizarCategoriaUseCase(repo)
        val categoria = Categoria(id = "c1", planId = "p1", nombre = "  Comida  ", tipo = TipoCategoria.GASTO)

        useCase(categoria)

        assertEquals("Comida", repo.actualizadas.single().nombre)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rechaza nombre vacio`() = runTest {
        val useCase = ActualizarCategoriaUseCase(repo)
        val categoria = Categoria(id = "c1", planId = "p1", nombre = "", tipo = TipoCategoria.GASTO)

        useCase(categoria)
    }
}

class EliminarCategoriaUseCaseTest {

    private val repo = FakeCategoriaRepository()

    @Test
    fun `marca la categoria como eliminada (soft delete)`() = runTest {
        val useCase = EliminarCategoriaUseCase(repo)
        val categoria = Categoria(id = "c1", planId = "p1", nombre = "Comida", tipo = TipoCategoria.GASTO)

        useCase(categoria)

        val eliminada = repo.eliminadas.single()
        assertEquals("c1", eliminada.id)
        assertEquals(null, categoria.deletedAt)
        assertTrue(eliminada.deletedAt != null)
    }
}

class SembrarCategoriasPorDefectoUseCaseTest {

    @Test
    fun `siembra categorias por defecto cuando el plan esta vacio`() = runTest {
        val repo = FakeCategoriaRepository()
        val useCase = SembrarCategoriasPorDefectoUseCase(repo)

        useCase("p1")

        assertEquals(SembrarCategoriasPorDefectoUseCase.DEFAULT_CATEGORIAS.size, repo.creadas.size)
        assertTrue(repo.creadas.all { it.planId == "p1" })
        val nombres = repo.creadas.map { it.nombre }
        assertTrue(nombres.contains("Comida"))
        assertTrue(nombres.contains("Salario"))
        assertEquals(
            TipoCategoria.GASTO,
            repo.creadas.first { it.nombre == "Comida" }.tipo,
        )
        assertEquals(
            TipoCategoria.INGRESO,
            repo.creadas.first { it.nombre == "Salario" }.tipo,
        )
        assertEquals("comida", repo.creadas.first { it.nombre == "Comida" }.icono)
        assertEquals("sueldo", repo.creadas.first { it.nombre == "Salario" }.icono)
    }

    @Test
    fun `no duplica categorias si el plan ya tiene`() = runTest {
        val repo = FakeCategoriaRepository(
            categorias = listOf(
                Categoria(id = "c1", planId = "p1", nombre = "Comida", tipo = TipoCategoria.GASTO),
            ),
        )
        val useCase = SembrarCategoriasPorDefectoUseCase(repo)

        useCase("p1")

        assertTrue(repo.creadas.isEmpty())
    }
}

private class FakeCategoriaRepository(
    private val categorias: List<Categoria> = emptyList(),
) : CategoriaRepository {
    val creadas = mutableListOf<Categoria>()
    val actualizadas = mutableListOf<Categoria>()
    val eliminadas = mutableListOf<Categoria>()

    override fun observarCategorias(planId: String): Flow<List<Categoria>> =
        flowOf(categorias.filter { it.planId == planId })

    override fun observarCategoriasPorTipo(
        planId: String,
        tipo: TipoCategoria,
    ): Flow<List<Categoria>> =
        flowOf(categorias.filter { it.planId == planId && it.tipo == tipo })

    override suspend fun crearCategoria(categoria: Categoria) {
        creadas.add(categoria)
    }

    override suspend fun actualizarCategoria(categoria: Categoria) {
        actualizadas.add(categoria)
    }

    override suspend fun eliminarCategoria(categoria: Categoria) {
        eliminadas.add(categoria)
    }

    fun fueActualizada(id: String): Boolean = actualizadas.any { it.id == id }
}

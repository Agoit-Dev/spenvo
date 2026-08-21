package com.agoitdev.spenvo.domain.usecase

import com.agoitdev.spenvo.domain.model.Categoria
import com.agoitdev.spenvo.domain.model.TipoCategoria
import com.agoitdev.spenvo.domain.repository.CategoriaRepository
import java.util.UUID
import kotlinx.coroutines.flow.first

/**
 * Seeds the default categories for a plan. Idempotent: does nothing if the
 * plan already has any category, so it can be called safely both when a plan
 * is created and when the categories screen first opens.
 */
class SembrarCategoriasPorDefectoUseCase(
    private val repository: CategoriaRepository,
) {
    suspend operator fun invoke(planId: String) {
        if (repository.observarCategorias(planId).first().isNotEmpty()) return

        DEFAULT_CATEGORIAS.forEach { seed ->
            repository.crearCategoria(
                Categoria(
                    id = UUID.randomUUID().toString(),
                    planId = planId,
                    nombre = seed.nombre,
                    icono = seed.icono,
                    tipo = seed.tipo,
                ),
            )
        }
    }

    companion object {
        data class CategoriaSeed(
            val tipo: TipoCategoria,
            val nombre: String,
            val icono: String,
        )

        val DEFAULT_CATEGORIAS: List<CategoriaSeed> = listOf(
            CategoriaSeed(TipoCategoria.GASTO, "Comida", "comida"),
            CategoriaSeed(TipoCategoria.GASTO, "Transporte", "transporte"),
            CategoriaSeed(TipoCategoria.GASTO, "Entretenimiento", "entretenimiento"),
            CategoriaSeed(TipoCategoria.GASTO, "Salud", "salud"),
            CategoriaSeed(TipoCategoria.GASTO, "Vivienda", "vivienda"),
            CategoriaSeed(TipoCategoria.GASTO, "Mercado", "mercado"),
            CategoriaSeed(TipoCategoria.GASTO, "Ropa", "ropa"),
            CategoriaSeed(TipoCategoria.GASTO, "Suministros", "suministros"),
            CategoriaSeed(TipoCategoria.GASTO, "Otros gastos", "otra"),
            CategoriaSeed(TipoCategoria.INGRESO, "Salario", "sueldo"),
            CategoriaSeed(TipoCategoria.INGRESO, "Regalos", "regalos"),
            CategoriaSeed(TipoCategoria.INGRESO, "Otros ingresos", "otra"),
        )
    }
}

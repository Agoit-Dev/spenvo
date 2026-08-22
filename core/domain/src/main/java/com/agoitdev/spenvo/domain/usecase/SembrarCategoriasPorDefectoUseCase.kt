package com.agoitdev.spenvo.domain.usecase

import com.agoitdev.spenvo.domain.model.Categoria
import com.agoitdev.spenvo.domain.model.TipoCategoria
import com.agoitdev.spenvo.domain.repository.CategoriaRepository
import kotlinx.coroutines.flow.first

/**
 * Seeds the default categories for a plan. Deterministic and idempotent: each
 * seed uses a stable technical key, so the Firestore document id is
 * `planId:clave` and re-running never duplicates. It does nothing when the plan
 * already has any category (fast path); the deterministic ids make a concurrent
 * race safe too.
 */
class SembrarCategoriasPorDefectoUseCase(
    private val repository: CategoriaRepository,
) {
    suspend operator fun invoke(planId: String) {
        if (repository.observarCategorias(planId).first().isNotEmpty()) return

        val categorias = DEFAULT_CATEGORIAS.map { seed ->
            Categoria(
                id = "$planId:${seed.clave}",
                planId = planId,
                nombre = seed.nombre,
                icono = seed.icono,
                tipo = seed.tipo,
            )
        }
        repository.crearCategorias(categorias)
    }

    companion object {
        data class CategoriaSeed(
            val tipo: TipoCategoria,
            val nombre: String,
            val icono: String,
            val clave: String,
        )

        val DEFAULT_CATEGORIAS: List<CategoriaSeed> = listOf(
            CategoriaSeed(TipoCategoria.GASTO, "Comida", "comida", "gasto_comida"),
            CategoriaSeed(TipoCategoria.GASTO, "Transporte", "transporte", "gasto_transporte"),
            CategoriaSeed(TipoCategoria.GASTO, "Entretenimiento", "entretenimiento", "gasto_entretenimiento"),
            CategoriaSeed(TipoCategoria.GASTO, "Salud", "salud", "gasto_salud"),
            CategoriaSeed(TipoCategoria.GASTO, "Vivienda", "vivienda", "gasto_vivienda"),
            CategoriaSeed(TipoCategoria.GASTO, "Mercado", "mercado", "gasto_mercado"),
            CategoriaSeed(TipoCategoria.GASTO, "Ropa", "ropa", "gasto_ropa"),
            CategoriaSeed(TipoCategoria.GASTO, "Suministros", "suministros", "gasto_suministros"),
            CategoriaSeed(TipoCategoria.GASTO, "Otros gastos", "otra", "gasto_otros"),
            CategoriaSeed(TipoCategoria.INGRESO, "Salario", "sueldo", "ingreso_salario"),
            CategoriaSeed(TipoCategoria.INGRESO, "Regalos", "regalos", "ingreso_regalos"),
            CategoriaSeed(TipoCategoria.INGRESO, "Otros ingresos", "otra", "ingreso_otros"),
        )
    }
}

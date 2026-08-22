package com.agoitdev.spenvo.domain.repository

import com.agoitdev.spenvo.domain.model.Categoria
import com.agoitdev.spenvo.domain.model.TipoCategoria
import kotlinx.coroutines.flow.Flow

interface CategoriaRepository {
    fun observarCategorias(planId: String): Flow<List<Categoria>>

    fun observarCategoriasPorTipo(planId: String, tipo: TipoCategoria): Flow<List<Categoria>>

    suspend fun crearCategoria(categoria: Categoria)

    suspend fun crearCategorias(categorias: List<Categoria>)

    suspend fun actualizarCategoria(categoria: Categoria)

    suspend fun eliminarCategoria(categoria: Categoria)
}

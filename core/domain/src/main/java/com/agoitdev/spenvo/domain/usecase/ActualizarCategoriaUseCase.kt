package com.agoitdev.spenvo.domain.usecase

import com.agoitdev.spenvo.domain.model.Categoria
import com.agoitdev.spenvo.domain.repository.CategoriaRepository

class ActualizarCategoriaUseCase(
    private val repository: CategoriaRepository,
) {
    suspend operator fun invoke(categoria: Categoria) {
        val nombre = categoria.nombre.trim()
        require(nombre.isNotEmpty()) { "El nombre no puede estar vacío" }
        repository.actualizarCategoria(categoria.copy(nombre = nombre))
    }
}

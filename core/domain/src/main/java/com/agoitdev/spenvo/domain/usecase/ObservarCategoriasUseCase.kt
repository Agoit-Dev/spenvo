package com.agoitdev.spenvo.domain.usecase

import com.agoitdev.spenvo.domain.model.Categoria
import com.agoitdev.spenvo.domain.repository.CategoriaRepository
import kotlinx.coroutines.flow.Flow

class ObservarCategoriasUseCase(
    private val repository: CategoriaRepository,
) {
    operator fun invoke(planId: String): Flow<List<Categoria>> =
        repository.observarCategorias(planId)
}

package com.agoitdev.spenvo.domain.usecase

import com.agoitdev.spenvo.domain.model.Categoria
import com.agoitdev.spenvo.domain.model.TipoCategoria
import com.agoitdev.spenvo.domain.repository.CategoriaRepository
import kotlinx.coroutines.flow.Flow

class ObservarCategoriasPorTipoUseCase(
    private val repository: CategoriaRepository,
) {
    operator fun invoke(planId: String, tipo: TipoCategoria): Flow<List<Categoria>> =
        repository.observarCategoriasPorTipo(planId, tipo)
}

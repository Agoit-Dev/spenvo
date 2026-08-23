package com.agoitdev.spenvo.domain.usecase

import com.agoitdev.spenvo.domain.model.Categoria
import com.agoitdev.spenvo.domain.repository.CategoriaRepository
import java.time.Instant

class EliminarCategoriaUseCase(
    private val repository: CategoriaRepository,
) {
    suspend operator fun invoke(categoria: Categoria, editorId: String) {
        val ahora = Instant.now()
        repository.eliminarCategoria(
            categoria.copy(editedBy = editorId, editedAt = ahora, deletedAt = ahora),
        )
    }
}

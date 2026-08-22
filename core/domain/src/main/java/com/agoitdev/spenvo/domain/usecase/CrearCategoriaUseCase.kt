package com.agoitdev.spenvo.domain.usecase

import com.agoitdev.spenvo.domain.model.Categoria
import com.agoitdev.spenvo.domain.model.TipoCategoria
import com.agoitdev.spenvo.domain.repository.CategoriaRepository
import java.util.UUID
import kotlinx.coroutines.flow.first

data class CrearCategoriaRequest(
    val planId: String,
    val nombre: String,
    val tipo: TipoCategoria,
    val icono: String = "default",
)

class CrearCategoriaUseCase(
    private val repository: CategoriaRepository,
) {
    suspend operator fun invoke(request: CrearCategoriaRequest): Categoria {
        val nombre = request.nombre.trim()
        require(nombre.isNotEmpty()) { "El nombre no puede estar vacío" }
        val yaExiste = repository.observarCategorias(request.planId).first()
            .any { it.deletedAt == null && it.tipo == request.tipo && it.nombre.equals(nombre, ignoreCase = true) }
        require(!yaExiste) { "Ya existe una categoría con ese nombre en este plan" }
        val categoria = Categoria(
            id = UUID.randomUUID().toString(),
            planId = request.planId,
            nombre = nombre,
            icono = request.icono,
            tipo = request.tipo,
        )
        repository.crearCategoria(categoria)
        return categoria
    }
}

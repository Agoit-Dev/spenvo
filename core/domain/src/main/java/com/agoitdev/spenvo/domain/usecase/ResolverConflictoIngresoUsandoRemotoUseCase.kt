package com.agoitdev.spenvo.domain.usecase

import com.agoitdev.spenvo.domain.repository.MovimientoRepository

class ResolverConflictoIngresoUsandoRemotoUseCase(
    private val repository: MovimientoRepository,
) {
    suspend operator fun invoke(id: String, clave: String) = repository.resolverConflictoIngresoUsandoRemoto(id, clave)
}

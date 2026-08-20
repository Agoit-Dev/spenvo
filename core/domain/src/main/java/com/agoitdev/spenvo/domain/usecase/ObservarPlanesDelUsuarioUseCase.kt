package com.agoitdev.spenvo.domain.usecase

import com.agoitdev.spenvo.domain.model.PlanFinanciero
import com.agoitdev.spenvo.domain.repository.PlanFinancieroRepository
import kotlinx.coroutines.flow.Flow

class ObservarPlanesDelUsuarioUseCase(
    private val planesRepository: PlanFinancieroRepository,
) {
    operator fun invoke(usuarioId: String): Flow<List<PlanFinanciero>> =
        planesRepository.observarPlanesDelUsuario(usuarioId)
}

package com.agoitdev.spenvo.domain.usecase

import com.agoitdev.spenvo.domain.model.PlanFinanciero
import com.agoitdev.spenvo.domain.repository.PlanFinancieroRepository

class ActualizarPlanUseCase(
    private val planesRepository: PlanFinancieroRepository,
) {
    suspend operator fun invoke(plan: PlanFinanciero) {
        planesRepository.actualizarPlan(plan)
    }
}

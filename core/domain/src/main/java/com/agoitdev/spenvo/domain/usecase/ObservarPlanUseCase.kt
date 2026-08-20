package com.agoitdev.spenvo.domain.usecase

import com.agoitdev.spenvo.domain.model.PlanFinanciero
import com.agoitdev.spenvo.domain.repository.PlanFinancieroRepository
import kotlinx.coroutines.flow.Flow

class ObservarPlanUseCase(
    private val planesRepository: PlanFinancieroRepository,
) {
    fun invoke(planId: String): Flow<PlanFinanciero?> =
        planesRepository.observarPlan(planId)
}

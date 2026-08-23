package com.agoitdev.spenvo.domain.usecase

import com.agoitdev.spenvo.domain.model.PlanFinanciero
import com.agoitdev.spenvo.domain.repository.PlanFinancieroRepository
import java.time.Instant

class ActualizarPlanUseCase(
    private val planesRepository: PlanFinancieroRepository,
) {
    suspend operator fun invoke(plan: PlanFinanciero, editorId: String) {
        planesRepository.actualizarPlan(
            plan.copy(editedBy = editorId, editedAt = Instant.now()),
        )
    }
}

package com.agoitdev.spenvo.movimientos

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.agoitdev.spenvo.domain.model.Monto
import com.agoitdev.spenvo.domain.model.PlanFinanciero
import com.agoitdev.spenvo.domain.model.ResumenMensualPlan
import com.agoitdev.spenvo.domain.usecase.ObservarBalanceAcumuladoPlanUseCase
import com.agoitdev.spenvo.domain.usecase.ObservarPlanUseCase
import com.agoitdev.spenvo.domain.usecase.ObservarResumenMensualPlanUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val observarPlan: ObservarPlanUseCase,
    private val observarResumenMensual: ObservarResumenMensualPlanUseCase,
    private val observarBalanceAcumulado: ObservarBalanceAcumuladoPlanUseCase,
) : ViewModel() {

    fun plan(planId: String): StateFlow<PlanFinanciero?> =
        observarPlan.invoke(planId)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(WHILE_SUBSCRIBED_TIMEOUT_MS), null)

    fun resumenMensual(planId: String): StateFlow<ResumenMensualPlan?> =
        observarResumenMensual(planId)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(WHILE_SUBSCRIBED_TIMEOUT_MS), null)

    fun balanceAcumulado(planId: String): StateFlow<Monto> =
        observarBalanceAcumulado(planId)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(WHILE_SUBSCRIBED_TIMEOUT_MS), Monto(0))

    private companion object {
        const val WHILE_SUBSCRIBED_TIMEOUT_MS = 5_000L
    }
}

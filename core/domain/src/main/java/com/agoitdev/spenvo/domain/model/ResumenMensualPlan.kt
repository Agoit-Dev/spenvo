package com.agoitdev.spenvo.domain.model

data class ResumenMensualPlan(
    val planId: String,
    val ingresosMes: Monto,
    val gastosMes: Monto,
) {
    val netoDelMes: Monto get() = Monto(ingresosMes.unidadesMenores - gastosMes.unidadesMenores)
}

package com.agoitdev.spenvo.domain.model

data class ResumenMensualPlan(
    val planId: String,
    val netoDelMes: Monto,
    val ingresosMes: Monto,
    val gastosMes: Monto,
)

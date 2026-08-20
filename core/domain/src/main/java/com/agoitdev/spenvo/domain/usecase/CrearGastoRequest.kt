package com.agoitdev.spenvo.domain.usecase

import com.agoitdev.spenvo.domain.model.Monto
import java.time.LocalDate

data class CrearGastoRequest(
    val planId: String,
    val categoriaId: String,
    val monto: Monto,
    val fecha: LocalDate,
    val descripcion: String? = null,
    val creadoPor: String,
)

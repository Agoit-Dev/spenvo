package com.agoitdev.spenvo.movimientos

import com.agoitdev.spenvo.domain.model.Movimiento

internal sealed interface FormularioMovimiento {
    data object Cerrado : FormularioMovimiento
    data object Nuevo : FormularioMovimiento
    data class Editar(val movimiento: Movimiento) : FormularioMovimiento
}

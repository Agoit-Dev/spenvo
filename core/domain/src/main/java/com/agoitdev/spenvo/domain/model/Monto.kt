package com.agoitdev.spenvo.domain.model

@JvmInline
value class Monto(val unidadesMenores: Long) {
    companion object {
        private const val UNIDADES_POR_EURO = 100L

        fun fromEuros(euros: Int, centimos: Int): Monto = Monto(euros * UNIDADES_POR_EURO + centimos)
    }
}

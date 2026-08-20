package com.agoitdev.spenvo.domain.usecase

import com.agoitdev.spenvo.domain.model.Monto

class ValidarMontoUseCase {
    operator fun invoke(monto: Monto): Boolean = monto.unidadesMenores > 0
}

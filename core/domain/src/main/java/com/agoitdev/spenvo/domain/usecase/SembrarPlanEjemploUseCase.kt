package com.agoitdev.spenvo.domain.usecase

import com.agoitdev.spenvo.domain.model.Monto
import java.time.LocalDate
import kotlinx.coroutines.flow.first

private const val CENTIMOS_POR_UNIDAD = 100.0

/**
 * Seeds one sample plan (Spain-locale: EUR, Spanish category/description names)
 * the very first time a user has zero plans, so a fresh install shows a
 * populated demo instead of an empty state. Idempotent: does nothing once the
 * user has any plan, mirroring [SembrarCategoriasPorDefectoUseCase]'s guard.
 */
class SembrarPlanEjemploUseCase(
    private val observarPlanes: ObservarPlanesDelUsuarioUseCase,
    private val crearPlan: CrearPlanUseCase,
    private val crearGasto: CrearGastoUseCase,
    private val crearIngreso: CrearIngresoUseCase,
) {
    suspend operator fun invoke(uid: String) {
        if (observarPlanes(uid).first().isNotEmpty()) return

        val plan = crearPlan(
            CrearPlanRequest(nombre = "Gastos del hogar", moneda = "EUR", creadoPor = uid),
        )
        val hoy = LocalDate.now()

        EJEMPLOS_GASTO.forEach { ejemplo ->
            crearGasto(
                CrearGastoRequest(
                    planId = plan.id,
                    categoriaId = "${plan.id}:${ejemplo.clave}",
                    monto = ejemplo.monto(),
                    fecha = hoy.minusDays(ejemplo.diasAtras),
                    descripcion = ejemplo.descripcion,
                    creadoPor = uid,
                ),
            )
        }
        EJEMPLOS_INGRESO.forEach { ejemplo ->
            crearIngreso(
                CrearIngresoRequest(
                    planId = plan.id,
                    categoriaId = "${plan.id}:${ejemplo.clave}",
                    monto = ejemplo.monto(),
                    fecha = hoy.minusDays(ejemplo.diasAtras),
                    descripcion = ejemplo.descripcion,
                    creadoPor = uid,
                ),
            )
        }
    }

    companion object {
        data class EjemploMovimiento(
            val clave: String,
            val descripcion: String,
            val euros: Double,
            val diasAtras: Long,
        ) {
            fun monto(): Monto = Monto(Math.round(euros * CENTIMOS_POR_UNIDAD))
        }

        val EJEMPLOS_GASTO: List<EjemploMovimiento> = listOf(
            EjemploMovimiento("gasto_mercado", "Mercadona", 62.30, diasAtras = 1),
            EjemploMovimiento("gasto_comida", "Cena en restaurante", 28.50, diasAtras = 3),
            EjemploMovimiento("gasto_transporte", "Gasolina", 45.00, diasAtras = 4),
            EjemploMovimiento("gasto_vivienda", "Factura de la luz", 78.90, diasAtras = 6),
            EjemploMovimiento("gasto_suministros", "Factura de internet", 39.99, diasAtras = 6),
            EjemploMovimiento("gasto_entretenimiento", "Cine", 12.00, diasAtras = 8),
            EjemploMovimiento("gasto_salud", "Farmacia", 15.40, diasAtras = 10),
            EjemploMovimiento("gasto_ropa", "Zara", 54.95, diasAtras = 12),
        )

        val EJEMPLOS_INGRESO: List<EjemploMovimiento> = listOf(
            EjemploMovimiento("ingreso_salario", "Nómina", 1850.00, diasAtras = 2),
        )
    }
}

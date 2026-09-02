package com.agoitdev.spenvo.movimientos

import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import com.agoitdev.spenvo.designsystem.theme.SpenvoExtendedColors
import com.agoitdev.spenvo.domain.model.Gasto
import com.agoitdev.spenvo.domain.model.Ingreso
import com.agoitdev.spenvo.domain.model.Monto
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class MovimientoItemTest {

    @Test
    fun `income item resolves financial and material roles`() {
        val colors = resolveMovimientoItemColors(ingreso(), ExtendedColors, MaterialColors)

        assertEquals(IncomeColor, colors.amount)
        assertEquals(SurfaceVariantColor, colors.categoryIconContainer)
        assertEquals(OnSurfaceVariantColor, colors.categoryIconContent)
        assertEquals(ErrorColor, colors.conflict)
    }

    @Test
    fun `expense item resolves financial and material roles`() {
        val colors = resolveMovimientoItemColors(gasto(), ExtendedColors, MaterialColors)

        assertEquals(ExpenseColor, colors.amount)
        assertEquals(SurfaceVariantColor, colors.categoryIconContainer)
        assertEquals(OnSurfaceVariantColor, colors.categoryIconContent)
        assertEquals(ErrorColor, colors.conflict)
    }

    @Test
    fun `selected category uses primary container pair`() {
        val colors = resolveSelectorCategoriaColors(selected = true, enabled = true, MaterialColors)

        assertEquals(PrimaryContainerColor, colors.container)
        assertEquals(OnPrimaryContainerColor, colors.content)
    }

    @Test
    fun `unselected category uses surface variant pair`() {
        val colors = resolveSelectorCategoriaColors(selected = false, enabled = true, MaterialColors)

        assertEquals(SurfaceVariantColor, colors.container)
        assertEquals(OnSurfaceVariantColor, colors.content)
    }

    @Test
    fun `disabled category preserves container and reduces matching content alpha`() {
        val selected = resolveSelectorCategoriaColors(selected = true, enabled = false, MaterialColors)
        val unselected = resolveSelectorCategoriaColors(selected = false, enabled = false, MaterialColors)

        assertEquals(PrimaryContainerColor, selected.container)
        assertEquals(OnPrimaryContainerColor.copy(alpha = DISABLED_ALPHA), selected.content)
        assertEquals(SurfaceVariantColor, unselected.container)
        assertEquals(OnSurfaceVariantColor.copy(alpha = DISABLED_ALPHA), unselected.content)
    }
}

private const val DISABLED_ALPHA = 0.38f

private val IncomeColor = Color(0xFF123456)
private val ExpenseColor = Color(0xFF654321)
private val PrimaryContainerColor = Color(0xFF112233)
private val OnPrimaryContainerColor = Color(0xFF445566)
private val SurfaceVariantColor = Color(0xFF778899)
private val OnSurfaceVariantColor = Color(0xFFAABBCC)
private val ErrorColor = Color(0xFFDDEEFF)

private val MaterialColors = lightColorScheme(
    primaryContainer = PrimaryContainerColor,
    onPrimaryContainer = OnPrimaryContainerColor,
    surfaceVariant = SurfaceVariantColor,
    onSurfaceVariant = OnSurfaceVariantColor,
    error = ErrorColor,
)

private val ExtendedColors = SpenvoExtendedColors(
    income = IncomeColor,
    onIncome = Color(0xFFABCDEF),
    incomeContainer = Color(0xFF234567),
    onIncomeContainer = Color(0xFF345678),
    expense = ExpenseColor,
    onExpense = Color(0xFFFEDCBA),
    expenseContainer = Color(0xFF765432),
    onExpenseContainer = Color(0xFF876543),
)

private fun ingreso() = Ingreso(
    id = "income-id",
    planId = "plan-id",
    categoriaId = "income-category-id",
    monto = Monto(100),
    fecha = LocalDate.of(2026, 9, 2),
    creadoPor = "user-id",
)

private fun gasto() = Gasto(
    id = "expense-id",
    planId = "plan-id",
    categoriaId = "expense-category-id",
    monto = Monto(100),
    fecha = LocalDate.of(2026, 9, 2),
    creadoPor = "user-id",
)

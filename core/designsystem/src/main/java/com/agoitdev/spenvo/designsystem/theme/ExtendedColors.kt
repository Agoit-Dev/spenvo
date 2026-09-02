package com.agoitdev.spenvo.designsystem.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

@Immutable
data class SpenvoExtendedColors(
    val income: Color,
    val onIncome: Color,
    val incomeContainer: Color,
    val onIncomeContainer: Color,
    val expense: Color,
    val onExpense: Color,
    val expenseContainer: Color,
    val onExpenseContainer: Color,
)

internal val LightExtendedColors = SpenvoExtendedColors(
    income = Color(0xFF466730), onIncome = Color(0xFFFFFFFF),
    incomeContainer = Color(0xFFC7EEA9), onIncomeContainer = Color(0xFF304F1A),
    expense = Color(0xFF8A5023), onExpense = Color(0xFFFFFFFF),
    expenseContainer = Color(0xFFFFDCC6), onExpenseContainer = Color(0xFF6E390D),
)

internal val DarkExtendedColors = SpenvoExtendedColors(
    income = Color(0xFFACD28F), onIncome = Color(0xFF1A3705),
    incomeContainer = Color(0xFF304F1A), onIncomeContainer = Color(0xFFC7EEA9),
    expense = Color(0xFFFFB786), onExpense = Color(0xFF502400),
    expenseContainer = Color(0xFF6E390D), onExpenseContainer = Color(0xFFFFDCC6),
)

internal val LocalSpenvoExtendedColors = staticCompositionLocalOf {
    SpenvoExtendedColors(
        income = Color.Unspecified, onIncome = Color.Unspecified,
        incomeContainer = Color.Unspecified, onIncomeContainer = Color.Unspecified,
        expense = Color.Unspecified, onExpense = Color.Unspecified,
        expenseContainer = Color.Unspecified, onExpenseContainer = Color.Unspecified,
    )
}

object SpenvoTheme {
    val extendedColors: SpenvoExtendedColors
        @Composable @ReadOnlyComposable get() = LocalSpenvoExtendedColors.current
}

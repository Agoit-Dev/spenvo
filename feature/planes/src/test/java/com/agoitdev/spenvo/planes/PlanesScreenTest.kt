package com.agoitdev.spenvo.planes

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.agoitdev.spenvo.domain.model.Monto
import com.agoitdev.spenvo.domain.model.PlanFinanciero
import com.agoitdev.spenvo.domain.model.ResumenMensualPlan
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PlanesScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val plan = PlanFinanciero(
        id = "p1",
        nombre = "Casa",
        moneda = "EUR",
        createdBy = "user-1",
    )

    @Test
    fun `muestra un balance positivo`() {
        composeTestRule.setContent {
            PlanCard(
                plan = plan,
                resumen = ResumenMensualPlan(planId = "p1", netoDelMes = Monto(2000)),
                onClick = {},
            )
        }

        composeTestRule.onNodeWithText("+20,00 EUR", substring = true).assertIsDisplayed()
    }

    @Test
    fun `muestra un balance negativo`() {
        composeTestRule.setContent {
            PlanCard(
                plan = plan,
                resumen = ResumenMensualPlan(planId = "p1", netoDelMes = Monto(-4000)),
                onClick = {},
            )
        }

        composeTestRule.onNodeWithText("-40,00 EUR", substring = true).assertIsDisplayed()
    }
}

package com.agoitdev.spenvo

import android.app.Application
import androidx.compose.material3.Text
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// A plain android.app.Application is used here (instead of the app's real, Hilt/Firebase-backed
// SpenvoApplication) because PlanScaffold under test has no DI or Firebase dependency, and letting
// Robolectric instantiate SpenvoApplication would run its onCreate() App Check setup, which crashes
// with "Default FirebaseApp is not initialized" outside a real Firebase-configured process.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "es", application = Application::class)
class PlanScaffoldTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `arranca en la pestana Home y cambia de contenido al tocar cada pestana`() {
        composeTestRule.setContent {
            PlanScaffold(
                contenidoHome = { Text("CONTENIDO_HOME") },
                contenidoMovimientos = { Text("CONTENIDO_MOVIMIENTOS") },
                contenidoCategorias = { Text("CONTENIDO_CATEGORIAS") },
                contenidoMiembros = { Text("CONTENIDO_MIEMBROS") },
            )
        }

        composeTestRule.onNodeWithText("CONTENIDO_HOME").assertIsDisplayed()

        composeTestRule.onNodeWithText("Movimientos").performClick()
        composeTestRule.onNodeWithText("CONTENIDO_MOVIMIENTOS").assertIsDisplayed()

        composeTestRule.onNodeWithText("Categorías").performClick()
        composeTestRule.onNodeWithText("CONTENIDO_CATEGORIAS").assertIsDisplayed()

        composeTestRule.onNodeWithText("Miembros").performClick()
        composeTestRule.onNodeWithText("CONTENIDO_MIEMBROS").assertIsDisplayed()

        composeTestRule.onNodeWithText("Inicio").performClick()
        composeTestRule.onNodeWithText("CONTENIDO_HOME").assertIsDisplayed()
    }
}

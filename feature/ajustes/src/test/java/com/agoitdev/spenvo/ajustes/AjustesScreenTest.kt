package com.agoitdev.spenvo.ajustes

import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.agoitdev.spenvo.domain.model.ColorPreference
import com.agoitdev.spenvo.domain.model.ThemePreference
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "es")
class AjustesScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `tocar Oscuro invoca onSeleccionarTema con DARK`() {
        var temaSeleccionado: ThemePreference? = null
        composeTestRule.setContent {
            AjustesContenido(
                estado = AjustesUiState(),
                dynamicDisponible = true,
                onSeleccionarTema = { temaSeleccionado = it },
                onSeleccionarColor = {},
            )
        }

        composeTestRule.onNodeWithText("Oscuro").performClick()

        assertEquals(ThemePreference.DARK, temaSeleccionado)
    }

    @Test
    fun `tocar Colores dinamicos invoca onSeleccionarColor con DYNAMIC cuando esta disponible`() {
        var colorSeleccionado: ColorPreference? = null
        composeTestRule.setContent {
            AjustesContenido(
                estado = AjustesUiState(),
                dynamicDisponible = true,
                onSeleccionarTema = {},
                onSeleccionarColor = { colorSeleccionado = it },
            )
        }

        composeTestRule.onNodeWithText("Colores dinámicos").performClick()

        assertEquals(ColorPreference.DYNAMIC, colorSeleccionado)
    }

    @Test
    fun `Colores dinamicos deshabilitado no invoca onSeleccionarColor por debajo de API 31`() {
        var invocado = false
        composeTestRule.setContent {
            AjustesContenido(
                estado = AjustesUiState(),
                dynamicDisponible = false,
                onSeleccionarTema = {},
                onSeleccionarColor = { invocado = true },
            )
        }

        composeTestRule.onNodeWithText("Colores dinámicos").performClick()

        assertEquals(false, invocado)
        composeTestRule.onNodeWithText("Requiere Android 12").assertExists()
    }

    @Test
    fun `tema DARK marca Oscuro como seleccionado y Sistema como no seleccionado`() {
        composeTestRule.setContent {
            AjustesContenido(
                estado = AjustesUiState(theme = ThemePreference.DARK),
                dynamicDisponible = true,
                onSeleccionarTema = {},
                onSeleccionarColor = {},
            )
        }

        composeTestRule.onNodeWithText("Oscuro").assertIsSelected()
        composeTestRule.onNodeWithText("Sistema").assertIsNotSelected()
    }

    @Test
    fun `color DYNAMIC marca Colores dinamicos como seleccionado`() {
        composeTestRule.setContent {
            AjustesContenido(
                estado = AjustesUiState(color = ColorPreference.DYNAMIC),
                dynamicDisponible = true,
                onSeleccionarTema = {},
                onSeleccionarColor = {},
            )
        }

        composeTestRule.onNodeWithText("Colores dinámicos").assertIsSelected()
    }
}

package com.agoitdev.spenvo.designsystem.components

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AvatarMenuTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `el menu esta cerrado hasta tocar el avatar`() {
        composeTestRule.setContent {
            AvatarMenu(
                photoUrl = null,
                contentDescription = "Cuenta",
                textos = AvatarMenuTextos(estado = null, cuenta = "Cuenta", ajustes = "Ajustes"),
                onOpenAccount = {},
                onOpenSettings = {},
            )
        }

        composeTestRule.onNodeWithText("Cuenta").assertDoesNotExist()
    }

    @Test
    fun `estado nulo no muestra fila de estado`() {
        composeTestRule.setContent {
            AvatarMenu(
                photoUrl = null,
                contentDescription = "Cuenta",
                textos = AvatarMenuTextos(estado = null, cuenta = "Cuenta", ajustes = "Ajustes"),
                onOpenAccount = {},
                onOpenSettings = {},
            )
        }
        composeTestRule.onNodeWithTag(TAG_AVATAR_TOPBAR_PLACEHOLDER, useUnmergedTree = true).performClick()

        composeTestRule.onAllNodesWithTag(TAG_AVATAR_MENU_ESTADO, useUnmergedTree = true).assertCountEquals(0)
    }

    @Test
    fun `estado no nulo muestra una fila de estado no interactiva`() {
        composeTestRule.setContent {
            AvatarMenu(
                photoUrl = null,
                contentDescription = "Cuenta",
                textos = AvatarMenuTextos(estado = "test@spenvo.com", cuenta = "Cuenta", ajustes = "Ajustes"),
                onOpenAccount = {},
                onOpenSettings = {},
            )
        }
        composeTestRule.onNodeWithTag(TAG_AVATAR_TOPBAR_PLACEHOLDER, useUnmergedTree = true).performClick()

        composeTestRule.onNodeWithTag(TAG_AVATAR_MENU_ESTADO, useUnmergedTree = true).assertIsDisplayed()
        composeTestRule.onNodeWithText("test@spenvo.com").assertIsDisplayed()
    }

    @Test
    fun `tocar el avatar abre cuenta y ajustes`() {
        composeTestRule.setContent {
            AvatarMenu(
                photoUrl = null,
                contentDescription = "Cuenta",
                textos = AvatarMenuTextos(estado = null, cuenta = "Cuenta", ajustes = "Ajustes"),
                onOpenAccount = {},
                onOpenSettings = {},
            )
        }

        composeTestRule.onNodeWithTag(TAG_AVATAR_TOPBAR_PLACEHOLDER, useUnmergedTree = true).performClick()

        composeTestRule.onNodeWithText("Cuenta").assertIsDisplayed()
        composeTestRule.onNodeWithText("Ajustes").assertIsDisplayed()
    }

    @Test
    fun `Cuenta invoca solamente onOpenAccount`() {
        var cuentaClics = 0
        var ajustesClics = 0
        composeTestRule.setContent {
            AvatarMenu(
                photoUrl = null,
                contentDescription = "Cuenta",
                textos = AvatarMenuTextos(estado = null, cuenta = "Cuenta", ajustes = "Ajustes"),
                onOpenAccount = { cuentaClics++ },
                onOpenSettings = { ajustesClics++ },
            )
        }
        composeTestRule.onNodeWithTag(TAG_AVATAR_TOPBAR_PLACEHOLDER, useUnmergedTree = true).performClick()

        composeTestRule.onNodeWithText("Cuenta").performClick()

        assertEquals(1, cuentaClics)
        assertEquals(0, ajustesClics)
    }

    @Test
    fun `Ajustes invoca solamente onOpenSettings`() {
        var cuentaClics = 0
        var ajustesClics = 0
        composeTestRule.setContent {
            AvatarMenu(
                photoUrl = null,
                contentDescription = "Cuenta",
                textos = AvatarMenuTextos(estado = null, cuenta = "Cuenta", ajustes = "Ajustes"),
                onOpenAccount = { cuentaClics++ },
                onOpenSettings = { ajustesClics++ },
            )
        }
        composeTestRule.onNodeWithTag(TAG_AVATAR_TOPBAR_PLACEHOLDER, useUnmergedTree = true).performClick()

        composeTestRule.onNodeWithText("Ajustes").performClick()

        assertEquals(0, cuentaClics)
        assertEquals(1, ajustesClics)
    }

    @Test
    fun `elegir una opcion cierra el menu`() {
        composeTestRule.setContent {
            AvatarMenu(
                photoUrl = null,
                contentDescription = "Cuenta",
                textos = AvatarMenuTextos(estado = null, cuenta = "Cuenta", ajustes = "Ajustes"),
                onOpenAccount = {},
                onOpenSettings = {},
            )
        }
        composeTestRule.onNodeWithTag(TAG_AVATAR_TOPBAR_PLACEHOLDER, useUnmergedTree = true).performClick()

        composeTestRule.onNodeWithText("Ajustes").performClick()

        composeTestRule.onNodeWithText("Cuenta").assertDoesNotExist()
    }
}

package com.agoitdev.spenvo.designsystem.conflict

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ConflictoDialogTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val textos = ConflictoDialogTextos(
        titulo = "Conflicto detectado",
        encabezadoLocal = "editado por yo · hoy",
        encabezadoRemoto = "editado por otro · ayer",
        etiquetaBorrado = "Borrado",
        accionPrimaria = "Usar la mía",
        accionSecundaria = "Usar la suya",
        cancelar = "Cancelar",
    )

    @Test
    fun `muestra las dos columnas local y remota`() {
        composeTestRule.setContent {
            ConflictoDialog(
                textos = textos,
                campos = listOf(CampoConflictoUi("monto", "1000", "1000")),
                borradoLocal = false,
                borradoRemoto = false,
                onAccionPrimaria = {},
                onAccionSecundaria = {},
                onDismiss = {},
            )
        }

        composeTestRule.onNodeWithTag(TAG_COLUMNA_LOCAL).assertIsDisplayed()
        composeTestRule.onNodeWithTag(TAG_COLUMNA_REMOTO).assertIsDisplayed()
    }

    @Test
    fun `emphasises fields that differ between local and remote`() {
        composeTestRule.setContent {
            ConflictoDialog(
                textos = textos,
                campos = listOf(
                    CampoConflictoUi("monto", "1000", "2000"),
                    CampoConflictoUi("descripcion", "igual", "igual"),
                ),
                borradoLocal = false,
                borradoRemoto = false,
                onAccionPrimaria = {},
                onAccionSecundaria = {},
                onDismiss = {},
            )
        }

        composeTestRule.onNodeWithTag("conflicto_campo_monto_local_diferente").assertIsDisplayed()
        composeTestRule.onNodeWithTag("conflicto_campo_monto_remoto_diferente").assertIsDisplayed()
        composeTestRule.onAllNodesWithTag("conflicto_campo_descripcion_local_diferente").assertCountEquals(0)
        composeTestRule.onAllNodesWithTag("conflicto_campo_descripcion_remoto_diferente").assertCountEquals(0)
    }

    @Test
    fun `shows a borrado indicator when a side is deleted`() {
        composeTestRule.setContent {
            ConflictoDialog(
                textos = textos,
                campos = listOf(CampoConflictoUi("monto", "1000", "1000")),
                borradoLocal = false,
                borradoRemoto = true,
                onAccionPrimaria = {},
                onAccionSecundaria = {},
                onDismiss = {},
            )
        }

        composeTestRule.onNodeWithTag(TAG_BORRADO_REMOTO).assertIsDisplayed()
        composeTestRule.onAllNodesWithTag(TAG_BORRADO_LOCAL).assertCountEquals(0)
    }
}

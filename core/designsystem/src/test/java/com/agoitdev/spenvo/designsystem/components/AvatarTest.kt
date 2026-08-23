package com.agoitdev.spenvo.designsystem.components

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AvatarTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `muestra un placeholder cuando no hay foto`() {
        composeTestRule.setContent {
            AvatarConBadge(
                photoUrl = null,
                contentDescription = "Foto de perfil",
                editContentDescription = "Cambiar foto",
                onEditarClick = {},
            )
        }

        composeTestRule.onNodeWithTag(TAG_AVATAR_PLACEHOLDER).assertIsDisplayed()
        composeTestRule.onAllNodesWithTag(TAG_AVATAR_IMAGEN).assertCountEquals(0)
    }

    @Test
    fun `muestra la imagen cuando hay foto`() {
        composeTestRule.setContent {
            AvatarConBadge(
                photoUrl = "https://example.com/avatar.jpg",
                contentDescription = "Foto de perfil",
                editContentDescription = "Cambiar foto",
                onEditarClick = {},
            )
        }

        composeTestRule.onNodeWithTag(TAG_AVATAR_IMAGEN).assertIsDisplayed()
        composeTestRule.onAllNodesWithTag(TAG_AVATAR_PLACEHOLDER).assertCountEquals(0)
    }

    @Test
    fun `siempre muestra la insignia de edicion y la invoca al tocarla`() {
        var clics = 0

        composeTestRule.setContent {
            AvatarConBadge(
                photoUrl = "https://example.com/avatar.jpg",
                contentDescription = "Foto de perfil",
                editContentDescription = "Cambiar foto",
                onEditarClick = { clics++ },
            )
        }

        composeTestRule.onNodeWithTag(TAG_AVATAR_BADGE).assertIsDisplayed()
        composeTestRule.onNodeWithTag(TAG_AVATAR_BADGE).performClick()

        assert(clics == 1)
    }
}

package com.agoitdev.spenvo.designsystem.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

const val TAG_AVATAR_MENU_ESTADO = "avatar_menu_estado"

/**
 * Shared top-bar avatar entry point: a tap on [AvatarTopBarAction] opens a dropdown offering
 * "account" and "settings" navigation, with an optional non-interactive status row above them
 * (e.g. the signed-in identity) mirroring the disabled-row pattern used by
 * `feature/planes`'s account menu. This component carries no navigation knowledge itself —
 * callers own routing via [onOpenAccount] and [onOpenSettings].
 */
@Suppress("LongParameterList") // detekt's functionThreshold (6) flags at >=6 params; already minimal at 6.
@Composable
fun AvatarMenu(
    photoUrl: String?,
    contentDescription: String,
    textos: AvatarMenuTextos,
    onOpenAccount: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var abierto by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        AvatarTopBarAction(
            photoUrl = photoUrl,
            contentDescription = contentDescription,
            onClick = { abierto = true },
        )
        DropdownMenu(expanded = abierto, onDismissRequest = { abierto = false }) {
            if (textos.estado != null) {
                Text(
                    text = textos.estado,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier
                        .testTag(TAG_AVATAR_MENU_ESTADO)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                )
            }
            DropdownMenuItem(
                text = { Text(textos.cuenta) },
                onClick = {
                    abierto = false
                    onOpenAccount()
                },
            )
            DropdownMenuItem(
                text = { Text(textos.ajustes) },
                onClick = {
                    abierto = false
                    onOpenSettings()
                },
            )
        }
    }
}

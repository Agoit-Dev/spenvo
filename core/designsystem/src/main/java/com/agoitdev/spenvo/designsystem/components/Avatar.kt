package com.agoitdev.spenvo.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage

const val TAG_AVATAR_IMAGEN = "avatar_imagen"
const val TAG_AVATAR_PLACEHOLDER = "avatar_placeholder"
const val TAG_AVATAR_BADGE = "avatar_badge"
const val TAG_AVATAR_TOPBAR_IMAGEN = "avatar_topbar_imagen"
const val TAG_AVATAR_TOPBAR_PLACEHOLDER = "avatar_topbar_placeholder"

private val TamanoAvatarPorDefecto = 96.dp
private val TamanoAvatarTopBar = 32.dp
private val TamanoBadge = 28.dp
private val TamanoIconoBadge = 16.dp

/**
 * Circular avatar with a small edit-icon badge overlay (M7 Slice A2).
 * Renders [photoUrl] via Coil when present, falling back to a placeholder icon otherwise.
 * The caller supplies both content descriptions pre-resolved from `stringResource(...)`,
 * mirroring [com.agoitdev.spenvo.designsystem.conflict.ConflictoDialog]'s caller-supplies-strings
 * pattern so this module stays free of its own string resources.
 */
@Composable
fun AvatarConBadge(
    photoUrl: String?,
    contentDescription: String,
    editContentDescription: String,
    onEditarClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.size(TamanoAvatarPorDefecto)) {
        if (photoUrl != null) {
            AsyncImage(
                model = photoUrl,
                contentDescription = contentDescription,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape)
                    .testTag(TAG_AVATAR_IMAGEN),
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .testTag(TAG_AVATAR_PLACEHOLDER),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.Person,
                    contentDescription = contentDescription,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .size(TamanoBadge)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary)
                .clickable(onClick = onEditarClick)
                .testTag(TAG_AVATAR_BADGE),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.Edit,
                contentDescription = editContentDescription,
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(TamanoIconoBadge),
            )
        }
    }
}

/**
 * Compact, badge-less avatar for a [androidx.compose.material3.TopAppBar]'s `actions` slot — the
 * "open my account" entry point reachable from every screen (front 3 of the auth/identity series).
 * Same photoUrl-or-placeholder shape as [AvatarConBadge], without the edit badge: this button only
 * navigates, it never edits the photo directly.
 */
@Composable
fun AvatarTopBarAction(
    photoUrl: String?,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    IconButton(onClick = onClick, modifier = modifier) {
        Box(modifier = Modifier.size(TamanoAvatarTopBar)) {
            if (photoUrl != null) {
                AsyncImage(
                    model = photoUrl,
                    contentDescription = contentDescription,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape)
                        .testTag(TAG_AVATAR_TOPBAR_IMAGEN),
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .testTag(TAG_AVATAR_TOPBAR_PLACEHOLDER),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Person,
                        contentDescription = contentDescription,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
    }
}

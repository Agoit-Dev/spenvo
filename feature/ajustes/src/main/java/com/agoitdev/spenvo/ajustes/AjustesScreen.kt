package com.agoitdev.spenvo.ajustes

import android.os.Build
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.agoitdev.spenvo.ajustes.R
import com.agoitdev.spenvo.domain.model.ColorPreference
import com.agoitdev.spenvo.domain.model.ThemePreference

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AjustesScreen(modifier: Modifier = Modifier, viewModel: AjustesViewModel = hiltViewModel()) {
    val estado by viewModel.estado.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val mensajeError = stringResource(R.string.settings_save_error)

    LaunchedEffect(estado.errorGuardado) {
        if (estado.errorGuardado) {
            snackbarHostState.showSnackbar(mensajeError)
            viewModel.consumirErrorGuardado()
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = { TopAppBar(title = { Text(stringResource(R.string.settings_title)) }) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        AjustesContenido(
            estado = estado,
            dynamicDisponible = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S,
            onSeleccionarTema = viewModel::seleccionarTema,
            onSeleccionarColor = viewModel::seleccionarColor,
            modifier = Modifier.padding(innerPadding),
        )
    }
}

@Composable
internal fun AjustesContenido(
    estado: AjustesUiState,
    dynamicDisponible: Boolean,
    onSeleccionarTema: (ThemePreference) -> Unit,
    onSeleccionarColor: (ColorPreference) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth().padding(16.dp)) {
        Text(
            text = stringResource(R.string.settings_appearance_section),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        Text(
            text = stringResource(R.string.settings_theme_label),
            style = MaterialTheme.typography.labelLarge,
        )
        Column(Modifier.selectableGroup()) {
            FilaOpcion(
                seleccionado = estado.theme == ThemePreference.SYSTEM,
                etiqueta = stringResource(R.string.settings_theme_system),
                onClick = { onSeleccionarTema(ThemePreference.SYSTEM) },
            )
            FilaOpcion(
                seleccionado = estado.theme == ThemePreference.LIGHT,
                etiqueta = stringResource(R.string.settings_theme_light),
                onClick = { onSeleccionarTema(ThemePreference.LIGHT) },
            )
            FilaOpcion(
                seleccionado = estado.theme == ThemePreference.DARK,
                etiqueta = stringResource(R.string.settings_theme_dark),
                onClick = { onSeleccionarTema(ThemePreference.DARK) },
            )
        }
        Text(
            text = stringResource(R.string.settings_color_label),
            style = MaterialTheme.typography.labelLarge,
        )
        Column(Modifier.selectableGroup()) {
            FilaOpcion(
                seleccionado = estado.color == ColorPreference.BRAND,
                etiqueta = stringResource(R.string.settings_color_brand),
                onClick = { onSeleccionarColor(ColorPreference.BRAND) },
                habilitado = true,
                textoSoporte = null,
            )
            FilaOpcion(
                seleccionado = estado.color == ColorPreference.DYNAMIC,
                etiqueta = stringResource(R.string.settings_color_dynamic),
                onClick = { onSeleccionarColor(ColorPreference.DYNAMIC) },
                habilitado = dynamicDisponible,
                textoSoporte = if (dynamicDisponible) {
                    null
                } else {
                    stringResource(R.string.settings_color_dynamic_unsupported)
                },
            )
        }
    }
}

@Composable
private fun FilaOpcion(
    seleccionado: Boolean,
    etiqueta: String,
    onClick: () -> Unit,
    habilitado: Boolean = true,
    textoSoporte: String? = null,
) {
    ListItem(
        headlineContent = { Text(etiqueta) },
        supportingContent = textoSoporte?.let { { Text(it) } },
        leadingContent = {
            RadioButton(selected = seleccionado, onClick = null, enabled = habilitado)
        },
        modifier = Modifier.selectable(
            selected = seleccionado,
            enabled = habilitado,
            onClick = onClick,
            role = Role.RadioButton,
        ),
    )
}

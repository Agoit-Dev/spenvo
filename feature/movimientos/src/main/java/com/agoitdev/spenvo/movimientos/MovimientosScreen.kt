package com.agoitdev.spenvo.movimientos

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MovimientosScreen(
    viewModel: MovimientosViewModel = hiltViewModel(),
    modifier: Modifier = Modifier,
) {
    val sesion by viewModel.sesion.collectAsStateWithLifecycle()
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.movimientos_title)) },
                actions = {
                    CuentaMenu(
                        estado = if (sesion.estaAutenticada) {
                            stringResource(R.string.cuenta_estado_invitado)
                        } else {
                            null
                        },
                    )
                },
            )
        },
    ) { innerPadding ->
        Text(
            text = stringResource(R.string.movimientos_placeholder),
            modifier = Modifier.padding(innerPadding),
        )
    }
}

@Composable
private fun CuentaMenu(estado: String?) {
    var abierto by remember { mutableStateOf(false) }
    IconButton(onClick = { abierto = true }) {
        Icon(
            imageVector = Icons.Filled.AccountCircle,
            contentDescription = stringResource(R.string.cuenta_menu_descripcion),
        )
    }
    DropdownMenu(expanded = abierto, onDismissRequest = { abierto = false }) {
        if (estado != null) {
            DropdownMenuItem(
                text = {
                    Text(
                        text = estado,
                        style = MaterialTheme.typography.labelLarge,
                    )
                },
                onClick = { abierto = false },
                enabled = false,
            )
        }
        DropdownMenuItem(
            text = {
                Text(
                    text = stringResource(R.string.cuenta_crear),
                    modifier = Modifier.padding(end = 8.dp),
                )
            },
            trailingIcon = { Text(stringResource(R.string.cuenta_proximamente)) },
            onClick = { abierto = false },
            enabled = false,
        )
    }
}


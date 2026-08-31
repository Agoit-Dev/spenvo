package com.agoitdev.spenvo.planes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.agoitdev.spenvo.designsystem.components.AvatarTopBarAction
import com.agoitdev.spenvo.domain.model.InvitacionEstado
import com.agoitdev.spenvo.domain.model.MiembroResuelto
import com.agoitdev.spenvo.domain.model.Rol

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MiembrosScreen(
    planId: String,
    avatarUrl: String?,
    onAbrirCuenta: () -> Unit,
    viewModel: MiembrosViewModel = hiltViewModel(),
    modifier: Modifier = Modifier,
) {
    val miembrosFlow = remember(planId) { viewModel.miembrosResueltos(planId) }
    val miembros by miembrosFlow.collectAsStateWithLifecycle()
    val estadoInvitar by viewModel.estadoInvitar.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var mostrarDialogoInvitar by rememberSaveable { mutableStateOf(false) }

    // errorRes es una validación propia de la UI (traducible); error es el mensaje ya resuelto
    // de un fallo real de Firestore/red. Se muestra el que haya.
    val mensajeError = estadoInvitar.errorRes?.let { stringResource(it) } ?: estadoInvitar.error
    LaunchedEffect(mensajeError) {
        mensajeError?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumirError()
        }
    }
    LaunchedEffect(estadoInvitar.invitado) {
        if (estadoInvitar.invitado) {
            mostrarDialogoInvitar = false
            viewModel.consumirInvitado()
        }
    }

Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.members_title)) },
                actions = {
                    AvatarTopBarAction(
                        photoUrl = avatarUrl,
                        contentDescription = stringResource(R.string.account_menu_description),
                        onClick = onAbrirCuenta,
                    )
                    IconButton(onClick = { mostrarDialogoInvitar = true }) {
                        Icon(
                            imageVector = Icons.Filled.Add,
                            contentDescription = stringResource(R.string.members_invite),
                        )
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        ListaMiembros(
            miembros = miembros,
            modifier = Modifier.padding(innerPadding),
        )
    }

    if (mostrarDialogoInvitar) {
        InvitarDialog(
            cargando = estadoInvitar.cargando,
            onConfirmar = { identificador, rol ->
                viewModel.invitar(planId = planId, identificador = identificador, rol = rol)
            },
            onCancelar = { mostrarDialogoInvitar = false },
        )
    }
}

@Composable
private fun ListaMiembros(
    miembros: List<MiembroResuelto>,
    modifier: Modifier = Modifier,
) {
    if (miembros.isEmpty()) {
        Text(
            text = stringResource(R.string.members_empty),
            modifier = modifier
                .fillMaxSize()
                .padding(16.dp),
        )
    } else {
        LazyColumn(
            modifier = modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(miembros, key = { it.acceso.usuarioId }) { miembro ->
                MiembroCard(miembro)
            }
        }
    }
}

@Composable
private fun MiembroCard(miembro: MiembroResuelto) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(
                text = miembro.usuario?.nombreUsuario ?: stringResource(R.string.members_loading),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = miembro.acceso.rol.name.lowercase() + " · " +
                    miembro.acceso.invitacionEstado.name.lowercase(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InvitarDialog(
    cargando: Boolean,
    onConfirmar: (String, Rol) -> Unit,
    onCancelar: () -> Unit,
) {
    var identificador by rememberSaveable { mutableStateOf("") }
    var rol by rememberSaveable { mutableStateOf(Rol.EDITOR) }

    AlertDialog(
        onDismissRequest = onCancelar,
        title = { Text(stringResource(R.string.members_invite)) },
        text = {
            Column {
                OutlinedTextField(
                    value = identificador,
                    onValueChange = { identificador = it },
                    label = { Text(stringResource(R.string.members_invite_identificador)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                RolSelector(
                    rol = rol,
                    onRolChange = { rol = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirmar(identificador.trim(), rol) },
                enabled = !cargando,
            ) {
                if (cargando) {
                    CircularProgressIndicator(modifier = Modifier.padding(4.dp))
                } else {
                    Text(stringResource(R.string.members_invite_confirm))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onCancelar) {
                Text(stringResource(R.string.members_invite_cancel))
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RolSelector(
    rol: Rol,
    onRolChange: (Rol) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expandido by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expandido,
        onExpandedChange = { expandido = it },
        modifier = modifier,
    ) {
        OutlinedTextField(
            value = rol.name.lowercase(),
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(R.string.members_invite_role)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandido) },
            modifier = Modifier
                .menuAnchor(
                    type = ExposedDropdownMenuAnchorType.PrimaryNotEditable,
                    enabled = true,
                )
                .fillMaxWidth(),
        )
        ExposedDropdownMenu(
            expanded = expandido,
            onDismissRequest = { expandido = false },
        ) {
            Rol.entries.forEach { opcion ->
                DropdownMenuItem(
                    text = { Text(opcion.name.lowercase()) },
                    onClick = {
                        onRolChange(opcion)
                        expandido = false
                    },
                )
            }
        }
    }
}

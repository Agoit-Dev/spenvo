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
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
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
import com.agoitdev.spenvo.domain.model.AccesoPlan
import com.agoitdev.spenvo.domain.model.PlanFinanciero
import com.agoitdev.spenvo.domain.model.Sesion

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlanesScreen(
    onCrearCuenta: () -> Unit,
    onAbrirPlan: (String) -> Unit,
    viewModel: PlanesViewModel = hiltViewModel(),
    modifier: Modifier = Modifier,
) {
    val planes by viewModel.planes.collectAsStateWithLifecycle()
    val invitaciones by viewModel.invitacionesPendientes.collectAsStateWithLifecycle()
    val estadoCrear by viewModel.estadoCrear.collectAsStateWithLifecycle()
    val sesion by viewModel.sesion.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var mostrarDialogoCrear by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(estadoCrear.error) {
        estadoCrear.error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumirError()
        }
    }
    LaunchedEffect(estadoCrear.creado) {
        if (estadoCrear.creado) {
            mostrarDialogoCrear = false
            viewModel.consumirCreado()
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            PlanesTopBar(
                sesion = sesion,
                onCrearCuenta = onCrearCuenta,
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { mostrarDialogoCrear = true }) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = stringResource(R.string.plans_create),
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        PlanesLista(
            planes = planes,
            invitaciones = invitaciones,
            onAceptarInvitacion = viewModel::aceptarInvitacion,
            onAbrirPlan = onAbrirPlan,
            modifier = Modifier.padding(innerPadding),
        )
    }

    if (mostrarDialogoCrear) {
        CrearPlanDialog(
            cargando = estadoCrear.cargando,
            onConfirmar = { nombre, moneda ->
                viewModel.crearPlan(nombre, moneda)
            },
            onCancelar = { mostrarDialogoCrear = false },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlanesTopBar(
    sesion: Sesion,
    onCrearCuenta: () -> Unit,
) {
    TopAppBar(
        title = { Text(stringResource(R.string.plans_title)) },
        actions = {
            CuentaMenu(
                estado = when {
                    !sesion.estaAutenticada -> null
                    sesion.email != null -> sesion.email
                    else -> stringResource(R.string.account_guest_state)
                },
                onCrearCuenta = onCrearCuenta,
            )
        },
    )
}

@Composable
private fun PlanesLista(
    planes: List<PlanFinanciero>,
    invitaciones: List<AccesoPlan>,
    onAceptarInvitacion: (String) -> Unit,
    onAbrirPlan: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (invitaciones.isNotEmpty()) {
            item {
                Text(
                    text = stringResource(R.string.plans_invitations_title),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            items(invitaciones, key = { it.planId }) { acceso ->
                InvitacionCard(
                    acceso = acceso,
                    onAceptar = { onAceptarInvitacion(acceso.planId) },
                )
            }
        }
        if (planes.isEmpty() && invitaciones.isEmpty()) {
            item {
                Text(
                    text = stringResource(R.string.plans_empty),
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        } else {
            items(planes, key = { it.id }) { plan ->
                PlanCard(
                    plan = plan,
                    onClick = { onAbrirPlan(plan.id) },
                )
            }
        }
    }
}

@Composable
private fun PlanCard(plan: PlanFinanciero, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(text = plan.nombre, style = MaterialTheme.typography.titleMedium)
            Text(
                text = plan.moneda,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun InvitacionCard(acceso: AccesoPlan, onAceptar: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(text = acceso.planId, style = MaterialTheme.typography.titleMedium)
            Text(
                text = stringResource(R.string.plans_invitation_role) + ": " + acceso.rol.name.lowercase(),
                style = MaterialTheme.typography.bodyMedium,
            )
            Button(onClick = onAceptar) {
                Text(stringResource(R.string.plans_invitation_accept))
            }
        }
    }
}

@Composable
private fun CrearPlanDialog(
    cargando: Boolean,
    onConfirmar: (String, String) -> Unit,
    onCancelar: () -> Unit,
) {
    var nombre by rememberSaveable { mutableStateOf("") }
    var moneda by rememberSaveable { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onCancelar,
        title = { Text(stringResource(R.string.plans_create)) },
        text = {
            Column {
                OutlinedTextField(
                    value = nombre,
                    onValueChange = { nombre = it },
                    label = { Text(stringResource(R.string.plans_create_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = moneda,
                    onValueChange = { moneda = it.uppercase() },
                    label = { Text(stringResource(R.string.plans_create_currency)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirmar(nombre.trim(), moneda.trim()) },
                enabled = !cargando,
            ) {
                if (cargando) {
                    CircularProgressIndicator(modifier = Modifier.padding(4.dp))
                } else {
                    Text(stringResource(R.string.plans_create_confirm))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onCancelar) {
                Text(stringResource(R.string.plans_create_cancel))
            }
        },
    )
}

@Composable
private fun CuentaMenu(estado: String?, onCrearCuenta: () -> Unit) {
    var abierto by remember { mutableStateOf(false) }
    IconButton(onClick = { abierto = true }) {
        Icon(
            imageVector = Icons.Filled.AccountCircle,
            contentDescription = stringResource(R.string.account_menu_description),
        )
    }
    DropdownMenu(expanded = abierto, onDismissRequest = { abierto = false }) {
        if (estado != null) {
            DropdownMenuItem(
                text = {
                    Text(text = estado, style = MaterialTheme.typography.labelLarge)
                },
                onClick = { abierto = false },
                enabled = false,
            )
        }
        DropdownMenuItem(
            text = { Text(stringResource(R.string.account_create)) },
            onClick = {
                abierto = false
                onCrearCuenta()
            },
        )
    }
}

package com.agoitdev.spenvo.cuenta

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.agoitdev.spenvo.designsystem.components.AvatarConBadge
import com.agoitdev.spenvo.domain.model.Sesion

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CuentaScreen(
    onRegistroCompletado: () -> Unit,
    viewModel: CuentaViewModel = hiltViewModel(),
    modifier: Modifier = Modifier,
) {
    val estado by viewModel.estado.collectAsStateWithLifecycle()
    val sesion by viewModel.sesion.collectAsStateWithLifecycle()
    val perfilEstado by viewModel.perfilEstado.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    CuentaSideEffects(estado, perfilEstado, snackbarHostState, onRegistroCompletado, viewModel)
    val seleccionarImagen = rememberSeleccionarImagenLauncher(onImagenSeleccionada = viewModel::subirAvatar)

    Scaffold(
        modifier = modifier,
        topBar = { TopAppBar(title = { Text(stringResource(tituloDe(sesion))) }) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        val contentModifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
            .padding(horizontal = 24.dp)
        if (sesion.esAnonima) {
            RegistroForm(cargando = estado.cargando, onRegistrar = viewModel::registrar, modifier = contentModifier)
        } else {
            PerfilContenido(
                sesion = sesion,
                perfilEstado = perfilEstado,
                onEditarAvatar = { seleccionarImagen() },
                onLogout = viewModel::logout,
                onEditarNombreUsuario = viewModel::editarNombreUsuario,
                modifier = contentModifier,
            )
        }
    }
}

private fun tituloDe(sesion: Sesion): Int =
    if (sesion.esAnonima) R.string.account_registration_title else R.string.account_profile_title

@Composable
private fun CuentaSideEffects(
    estado: RegistroEstado,
    perfilEstado: PerfilEstado,
    snackbarHostState: SnackbarHostState,
    onRegistroCompletado: () -> Unit,
    viewModel: CuentaViewModel,
) {
    LaunchedEffect(estado.completado) {
        if (estado.completado) onRegistroCompletado()
    }

    LaunchedEffect(estado.error) {
        estado.error?.let { error ->
            snackbarHostState.showSnackbar(error)
            viewModel.consumirError()
        }
    }

    LaunchedEffect(perfilEstado.avatarError) {
        perfilEstado.avatarError?.let { error ->
            snackbarHostState.showSnackbar(error)
            viewModel.consumirAvatarError()
        }
    }
}

/** Reads the picked [Uri]'s bytes/content-type in the Composable, then delegates. */
@Composable
private fun rememberSeleccionarImagenLauncher(onImagenSeleccionada: (ByteArray, String) -> Unit): () -> Unit {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri: Uri? ->
        uri?.let {
            val contentType = context.contentResolver.getType(it) ?: "image/*"
            context.contentResolver.openInputStream(it)?.use { stream ->
                onImagenSeleccionada(stream.readBytes(), contentType)
            }
        }
    }
    return {
        launcher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }
}

@Suppress("LongParameterList")
@Composable
private fun PerfilContenido(
    sesion: Sesion,
    perfilEstado: PerfilEstado,
    onEditarAvatar: () -> Unit,
    onLogout: () -> Unit,
    onEditarNombreUsuario: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(24.dp))
        AvatarConBadge(
            photoUrl = sesion.photoUrl,
            contentDescription = stringResource(R.string.account_profile_avatar_description),
            editContentDescription = stringResource(R.string.account_profile_avatar_edit_description),
            onEditarClick = onEditarAvatar,
        )
        if (perfilEstado.subiendoAvatar) {
            Spacer(Modifier.height(8.dp))
            CircularProgressIndicator(modifier = Modifier.height(16.dp))
        }
        Spacer(Modifier.height(16.dp))
        Text(
            text = sesion.nombre ?: stringResource(R.string.account_profile_sin_nombre),
            style = MaterialTheme.typography.headlineSmall,
        )
        Spacer(Modifier.height(8.dp))
        CampoNombreUsuario(
            nombreUsuario = perfilEstado.nombreUsuario,
            error = perfilEstado.nombreUsuarioError,
            onGuardar = onEditarNombreUsuario,
        )
        Spacer(Modifier.height(16.dp))
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = stringResource(R.string.account_profile_info_title),
                    style = MaterialTheme.typography.labelLarge,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.account_profile_info_email, sesion.email.orEmpty()),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        Spacer(Modifier.height(24.dp))
        Button(
            onClick = onLogout,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(imageVector = Icons.AutoMirrored.Filled.Logout, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.account_profile_logout))
        }
    }
}

@Composable
private fun CampoNombreUsuario(
    nombreUsuario: String?,
    @StringRes error: Int?,
    onGuardar: (String) -> Unit,
) {
    val mensajeError = error?.let { stringResource(it) }
    var nombreUsuarioEditado by rememberSaveable(nombreUsuario) {
        mutableStateOf(nombreUsuario.orEmpty())
    }
    OutlinedTextField(
        value = nombreUsuarioEditado,
        onValueChange = { nombreUsuarioEditado = it },
        label = { Text(stringResource(R.string.account_profile_nombre_usuario)) },
        isError = mensajeError != null,
        supportingText = mensajeError?.let { { Text(it) } },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    TextButton(
        onClick = { onGuardar(nombreUsuarioEditado) },
        enabled = nombreUsuarioEditado != nombreUsuario && nombreUsuarioEditado.isNotBlank(),
    ) {
        Text(stringResource(R.string.account_profile_guardar_nombre_usuario))
    }
}

@Composable
private fun RegistroForm(
    cargando: Boolean,
    onRegistrar: (String, String, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var nombre by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = stringResource(R.string.account_registration_subtitle),
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(
            value = nombre,
            onValueChange = { nombre = it },
            label = { Text(stringResource(R.string.account_registration_name)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text(stringResource(R.string.account_registration_email)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text(stringResource(R.string.account_registration_password)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(24.dp))
        Button(
            onClick = { onRegistrar(nombre, email, password) },
            enabled = !cargando && nombre.isNotBlank() && email.isNotBlank() && password.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (cargando) {
                CircularProgressIndicator(modifier = Modifier.height(20.dp))
            } else {
                Text(stringResource(R.string.account_registration_create))
            }
        }
    }
}

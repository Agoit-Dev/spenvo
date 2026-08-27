package com.agoitdev.spenvo

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource

private enum class PlanTab { HOME, MOVIMIENTOS, CATEGORIAS, MIEMBROS }

@Suppress("LongParameterList")
@Composable
internal fun PlanScaffold(
    contenidoHome: @Composable () -> Unit,
    contenidoMovimientos: @Composable () -> Unit,
    contenidoCategorias: @Composable () -> Unit,
    contenidoMiembros: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    var tabSeleccionada by rememberSaveable { mutableStateOf(PlanTab.HOME) }

    Scaffold(
        modifier = modifier,
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = tabSeleccionada == PlanTab.HOME,
                    onClick = { tabSeleccionada = PlanTab.HOME },
                    icon = { Icon(Icons.Filled.Home, contentDescription = null) },
                    label = { Text(stringResource(R.string.nav_home)) },
                )
                NavigationBarItem(
                    selected = tabSeleccionada == PlanTab.MOVIMIENTOS,
                    onClick = { tabSeleccionada = PlanTab.MOVIMIENTOS },
                    icon = { Icon(Icons.AutoMirrored.Filled.ReceiptLong, contentDescription = null) },
                    label = { Text(stringResource(R.string.nav_movements)) },
                )
                NavigationBarItem(
                    selected = tabSeleccionada == PlanTab.CATEGORIAS,
                    onClick = { tabSeleccionada = PlanTab.CATEGORIAS },
                    icon = { Icon(Icons.Filled.Category, contentDescription = null) },
                    label = { Text(stringResource(R.string.nav_categories)) },
                )
                NavigationBarItem(
                    selected = tabSeleccionada == PlanTab.MIEMBROS,
                    onClick = { tabSeleccionada = PlanTab.MIEMBROS },
                    icon = { Icon(Icons.Filled.Group, contentDescription = null) },
                    label = { Text(stringResource(R.string.nav_members)) },
                )
            }
        },
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding).consumeWindowInsets(innerPadding)) {
            when (tabSeleccionada) {
                PlanTab.HOME -> contenidoHome()
                PlanTab.MOVIMIENTOS -> contenidoMovimientos()
                PlanTab.CATEGORIAS -> contenidoCategorias()
                PlanTab.MIEMBROS -> contenidoMiembros()
            }
        }
    }
}

package com.agoitdev.spenvo.movimientos

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.agoitdev.spenvo.designsystem.components.AvatarTopBarAction
import com.agoitdev.spenvo.domain.model.TipoCategoria

/** Extracted from `MovimientosScreen.kt` to stay under detekt's `TooManyFunctions` file threshold. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MovimientosTopBar(avatarUrl: String?, onAbrirCuenta: () -> Unit) {
    TopAppBar(
        title = { Text(stringResource(R.string.movements_title)) },
        actions = {
            AvatarTopBarAction(
                photoUrl = avatarUrl,
                contentDescription = stringResource(R.string.account_menu_description),
                onClick = onAbrirCuenta,
            )
        },
    )
}

@Composable
internal fun FiltroTipoMovimientoLista(
    tipoSeleccionado: TipoCategoria?,
    onTipoChange: (TipoCategoria?) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
    ) {
        FilterChip(
            selected = tipoSeleccionado == null,
            onClick = { onTipoChange(null) },
            label = { Text(stringResource(R.string.movements_filter_all)) },
        )
        FilterChip(
            selected = tipoSeleccionado == TipoCategoria.GASTO,
            onClick = { onTipoChange(TipoCategoria.GASTO) },
            label = { Text(stringResource(R.string.movements_filter_expense)) },
        )
        FilterChip(
            selected = tipoSeleccionado == TipoCategoria.INGRESO,
            onClick = { onTipoChange(TipoCategoria.INGRESO) },
            label = { Text(stringResource(R.string.movements_filter_income)) },
        )
    }
}

@Composable
internal fun FiltroTipoMovimiento(
    tipoSeleccionado: TipoCategoria,
    onTipoChange: (TipoCategoria) -> Unit,
    modifier: Modifier = Modifier,
    habilitado: Boolean = true,
) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(
            selected = tipoSeleccionado == TipoCategoria.GASTO,
            onClick = { onTipoChange(TipoCategoria.GASTO) },
            label = { Text(stringResource(R.string.movements_filter_expense)) },
            enabled = habilitado,
        )
        FilterChip(
            selected = tipoSeleccionado == TipoCategoria.INGRESO,
            onClick = { onTipoChange(TipoCategoria.INGRESO) },
            label = { Text(stringResource(R.string.movements_filter_income)) },
            enabled = habilitado,
        )
    }
}

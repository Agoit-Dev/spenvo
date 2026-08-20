package com.agoitdev.spenvo.movimientos

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MovimientosScreen(
    planId: String,
    onVerMiembros: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.movimientos_title)) },
                actions = {
                    IconButton(onClick = onVerMiembros) {
                        Icon(
                            imageVector = Icons.Filled.Person,
                            contentDescription = stringResource(R.string.movimientos_ver_miembros),
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        Text(
            text = stringResource(R.string.movimientos_placeholder, planId),
            modifier = Modifier.padding(innerPadding),
        )
    }
}

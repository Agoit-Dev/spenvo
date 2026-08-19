package com.agoitdev.spenvo.movimientos

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MovimientosScreen(modifier: Modifier = Modifier) {
    Scaffold(
        modifier = modifier,
        topBar = { TopAppBar(title = { Text(stringResource(R.string.movimientos_title)) }) },
    ) { innerPadding ->
        Text(
            text = stringResource(R.string.movimientos_placeholder),
            modifier = Modifier.padding(innerPadding),
        )
    }
}

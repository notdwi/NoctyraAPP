package com.noctyra.app.ui.screens.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.noctyra.app.ui.components.SettingsCard
import com.noctyra.app.ui.theme.Accent

@Composable
fun SettingsScreen() {
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Ajustes", style = MaterialTheme.typography.headlineLarge)
        Spacer(Modifier.height(8.dp))

        SettingsCard(
            icon = { Icon(Icons.Default.Storage, contentDescription = null, tint = Accent) },
            title = "Armazenamento",
            subtitle = "Gerencie dados em cache e libere espaço.",
            onClick = { }
        )

        SettingsCard(
            icon = { Icon(Icons.Default.CloudSync, contentDescription = null, tint = Accent) },
            title = "Backup e Restauração",
            subtitle = "Faça backup dos seus dados e restaure em qualquer dispositivo.",
            onClick = { }
        )
    }
}

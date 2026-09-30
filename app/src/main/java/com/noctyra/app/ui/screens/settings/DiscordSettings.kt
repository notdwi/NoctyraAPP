package com.noctyra.app.ui.screens.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VideogameAsset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.noctyra.app.discord.DiscordPresence
import com.noctyra.app.discord.DiscordStatus
import com.noctyra.app.ui.components.SettingsRow
import com.noctyra.app.ui.theme.ErrorRed
import com.noctyra.app.ui.theme.Pink

@Composable
internal fun DiscordSettingsGroup() {
    val status by DiscordPresence.status.collectAsStateWithLifecycle()
    val user by DiscordPresence.user.collectAsStateWithLifecycle()
    val show by DiscordPresence.showActivity.collectAsStateWithLifecycle()
    val error by DiscordPresence.error.collectAsStateWithLifecycle()

    SettingsGroup("Discord") {
        val subtitle = when (status) {
            DiscordStatus.Unavailable -> "Indisponível neste aparelho"
            DiscordStatus.Connecting -> "Conectando…"
            DiscordStatus.Connected -> "Conectado como ${user?.name.orEmpty()}"
            DiscordStatus.LoggedOut -> error ?: "Mostre no seu perfil o anime que está assistindo"
        }
        SettingsRow(
            Icons.Default.VideogameAsset, "Discord", subtitle,
            trailing = {
                when (status) {
                    DiscordStatus.LoggedOut -> TextButton(onClick = DiscordPresence::login) {
                        Text("Entrar", color = Pink, fontWeight = FontWeight.Bold)
                    }
                    DiscordStatus.Connected, DiscordStatus.Connecting -> TextButton(onClick = DiscordPresence::logout) {
                        Text("Sair", color = ErrorRed, fontWeight = FontWeight.Bold)
                    }
                    DiscordStatus.Unavailable -> Unit
                }
            }
        )
        if (status == DiscordStatus.Connected) {
            SettingsRow(
                Icons.Default.Visibility, "Mostrar o que estou assistindo", "Anime, temporada e episódio no seu status",
                onClick = { DiscordPresence.setShowActivity(!show) },
                trailing = { NoctyraSwitch(show) { DiscordPresence.setShowActivity(it) } }
            )
        }
    }
}

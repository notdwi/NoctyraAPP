package com.noctyra.app.ui.screens.party

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.noctyra.app.data.local.AppSettings
import com.noctyra.app.data.model.cleanTitle
import com.noctyra.app.discord.DiscordPresence
import com.noctyra.app.party.Party
import com.noctyra.app.party.PartySession
import com.noctyra.app.party.PartyState
import com.noctyra.app.ui.components.*
import com.noctyra.app.ui.theme.*
import kotlinx.coroutines.launch

@Composable
fun PartyScreen(onBack: () -> Unit, onPickAnime: () -> Unit, onOpenPlayer: (PartyState) -> Unit) {
    val session by Party.session.collectAsStateWithLifecycle()
    var nick by remember { mutableStateOf(AppSettings.nick.ifEmpty { DiscordPresence.user.value?.name.orEmpty() }) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    Column(
        Modifier
            .fillMaxSize()
            .background(BackgroundDark)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 32.dp)
    ) {
        Row(Modifier.padding(start = 8.dp, end = 16.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            CircleIconButton(Icons.AutoMirrored.Filled.ArrowBack, "Voltar", onBack, background = SurfaceCard)
            Spacer(Modifier.width(12.dp))
            Column {
                Text("Party", style = MaterialTheme.typography.headlineLarge)
                Text("Assista junto, no mesmo segundo", style = MaterialTheme.typography.bodyMedium)
            }
        }
        Spacer(Modifier.height(12.dp))

        val current = session
        when {
            current == null -> Lobby(
                nick = nick,
                error = error,
                onNickChange = { nick = it.take(20); AppSettings.nick = nick },
                onCreate = { error = Party.host(nick) },
                onJoin = { code -> error = null; scope.launch { error = Party.join(code, nick) } },
                onClearError = { error = null }
            )
            !current.connected -> Connecting()
            else -> InRoom(current, onPickAnime, onOpenPlayer)
        }
    }
}

@Composable
private fun Lobby(
    nick: String,
    error: String?,
    onNickChange: (String) -> Unit,
    onCreate: () -> Unit,
    onJoin: (String) -> Unit,
    onClearError: () -> Unit
) {
    var code by rememberSaveable { mutableStateOf("") }

    PartyCard("Seu nick", "É assim que seus amigos vão te ver no chat") {
        PartyTextField(nick, onNickChange, placeholder = "Ex.: Gojo")
    }

    PartyCard("Criar sala", "Você escolhe o anime e controla a sessão junto com a galera") {
        PinkButton(
            "Criar sala",
            onClick = onCreate,
            icon = Icons.Default.AddCircle,
            modifier = Modifier.fillMaxWidth()
        )
    }

    PartyCard("Entrar numa sala", "Digite o código que seu amigo mandou") {
        PartyTextField(code, { code = it.uppercase().take(21); onClearError() }, placeholder = "NX-XXX-XXXX", monospace = true)
        Spacer(Modifier.height(12.dp))
        GhostButton(
            "Entrar",
            onClick = { if (code.isNotBlank()) onJoin(code) },
            icon = Icons.AutoMirrored.Filled.Login,
            modifier = Modifier.fillMaxWidth()
        )
    }

    error?.let {
        Text(
            it,
            color = ErrorRed,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 4.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(ErrorRed.copy(alpha = 0.1f))
                .padding(14.dp)
        )
    }

    PartyCard("Como funciona") {
        HowToStep(1, "Na mesma casa: fiquem no mesmo Wi‑Fi e use o código \"Mesma rede Wi‑Fi\".")
        HowToStep(2, "Longe: instalem o ZeroTier, entrem na mesma rede e use o código \"ZeroTier / VPN\".")
        HowToStep(3, "Só o host escolhe o anime. Play, pause e avanço valem para todos, e se alguém travar a sala espera.")
    }
}

@Composable
private fun Connecting() {
    PartyCard("Conectando à sala…", "Isso leva só alguns segundos") {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(color = Pink, strokeWidth = 3.dp, modifier = Modifier.size(26.dp))
            Spacer(Modifier.weight(1f))
            TextButton(onClick = Party::leave) { Text("Cancelar", color = TextSecondary) }
        }
    }
}

@Composable
private fun InRoom(session: PartySession, onPickAnime: () -> Unit, onOpenPlayer: (PartyState) -> Unit) {
    if (session.isHost) {
        PartyCard("Código da sala", "Toque para copiar e mande para seus amigos") {
            if (session.addresses.isEmpty()) {
                Text(
                    "Nenhuma rede encontrada. Conecte ao Wi‑Fi ou ligue o ZeroTier.",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            session.addresses.forEach {
                RoomCodeRow(it)
                Spacer(Modifier.height(10.dp))
            }
            GhostButton("Atualizar redes", Party::refreshAddresses, icon = Icons.Default.Refresh, height = 42.dp)
        }
    } else {
        PartyCard("Conectado", "Você está na sala de ${session.hostNick.ifEmpty { "um amigo" }}") {}
    }

    PartyCard("Na sala • ${session.members.size}") {
        session.members.forEach { MemberRow(it, isMe = it.id == session.myId) }
    }

    val state = session.state
    PartyCard("Assistindo agora") {
        if (state == null) {
            Text(
                if (session.isHost) "Escolha um anime e todos vão junto com você." else "Esperando o host escolher o anime…",
                style = MaterialTheme.typography.bodyMedium
            )
            if (session.isHost) {
                Spacer(Modifier.height(12.dp))
                PinkButton("Escolher anime", onPickAnime, icon = Icons.Default.Search, modifier = Modifier.fillMaxWidth())
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                NetImage(state.poster, Modifier.width(56.dp).aspectRatio(0.72f).clip(RoundedCornerShape(10.dp)))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        cleanTitle(state.title.ifEmpty { state.slug }),
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text("T${state.season} • EP ${state.episode}", style = MaterialTheme.typography.bodySmall)
                }
            }
            Spacer(Modifier.height(12.dp))
            PinkButton(
                if (session.isHost) "Abrir player" else "Assistir junto",
                { onOpenPlayer(state) },
                icon = Icons.Default.PlayArrow,
                modifier = Modifier.fillMaxWidth()
            )
            if (session.isHost) {
                Spacer(Modifier.height(8.dp))
                GhostButton("Trocar anime", onPickAnime, icon = Icons.Default.Search, modifier = Modifier.fillMaxWidth(), height = 44.dp)
            }
        }
    }

    TextButton(onClick = Party::leave, modifier = Modifier.padding(horizontal = 16.dp)) {
        Text(if (session.isHost) "Encerrar sala" else "Sair da sala", color = ErrorRed, fontWeight = FontWeight.Bold)
    }
}

package com.noctyra.app.ui.screens.player

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.noctyra.app.party.ChatMessage
import com.noctyra.app.ui.theme.*
import kotlinx.coroutines.delay

@Composable
fun PartyChatPanel(messages: List<ChatMessage>, onSend: (String) -> Unit, modifier: Modifier = Modifier) {
    val listState = rememberLazyListState()
    var draft by remember { mutableStateOf("") }
    val send = {
        if (draft.isNotBlank()) { onSend(draft); draft = "" }
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.scrollToItem(messages.lastIndex)
    }

    Column(modifier) {
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(messages, key = { it.seq }, contentType = { if (it.isSystem) 0 else 1 }) { ChatBubble(it) }
        }
        val shape = RoundedCornerShape(26.dp)
        Row(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp)
                    .clip(shape)
                    .background(SurfaceCard)
                    .border(1.dp, CardBorder, shape)
                    .padding(horizontal = 18.dp, vertical = 12.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                if (draft.isEmpty()) Text("Mandar mensagem…", color = TextMuted, fontSize = 15.sp)
                BasicTextField(
                    value = draft,
                    onValueChange = { if (it.length <= 300) draft = it },
                    textStyle = MaterialTheme.typography.bodyLarge,
                    cursorBrush = SolidColor(Pink),
                    maxLines = 3,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { send() }),
                    modifier = Modifier.fillMaxWidth()
                )
            }
            Spacer(Modifier.width(8.dp))
            Box(
                Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(if (draft.isBlank()) SurfaceElevated else Pink)
                    .clickable { send() },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.AutoMirrored.Filled.Send, "Enviar", tint = Color.White, modifier = Modifier.size(22.dp))
            }
        }
    }
}

@Composable
private fun ChatBubble(msg: ChatMessage) {
    if (msg.isSystem) {
        Text(
            msg.text,
            style = MaterialTheme.typography.labelMedium,
            color = TextSecondary,
            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        return
    }
    val shape = RoundedCornerShape(
        topStart = 18.dp, topEnd = 18.dp,
        bottomStart = if (msg.mine) 18.dp else 4.dp,
        bottomEnd = if (msg.mine) 4.dp else 18.dp
    )
    Box(Modifier.fillMaxWidth(), contentAlignment = if (msg.mine) Alignment.CenterEnd else Alignment.CenterStart) {
        Column(
            Modifier
                .widthIn(max = 280.dp)
                .clip(shape)
                .background(if (msg.mine) Pink else SurfaceElevated)
                .padding(horizontal = 14.dp, vertical = 9.dp)
        ) {
            if (!msg.mine) {
                Text(msg.nick, color = PinkLight, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(2.dp))
            }
            Text(msg.text, color = Color.White, fontSize = 15.sp, lineHeight = 20.sp)
        }
    }
}

@Composable
fun FullscreenChatOverlay(messages: List<ChatMessage>, modifier: Modifier = Modifier) {
    val last = messages.lastOrNull()
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(last?.seq) {
        if (last == null) return@LaunchedEffect
        visible = true
        delay(6_000)
        visible = false
    }
    if (!visible) return
    Column(
        modifier
            .widthIn(max = 360.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Color.Black.copy(alpha = 0.55f))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        messages.takeLast(3).forEach { msg ->
            Text(
                if (msg.isSystem) msg.text else "${msg.nick}: ${msg.text}",
                color = if (msg.isSystem) TextSecondary else Color.White,
                fontSize = 13.sp,
                maxLines = 2
            )
        }
    }
}

@Composable
fun PartyHoldBanner(waitingFor: List<String>, modifier: Modifier = Modifier) {
    Row(
        modifier
            .clip(RoundedCornerShape(50))
            .background(Color.Black.copy(alpha = 0.7f))
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CircularProgressIndicator(color = Pink, strokeWidth = 2.dp, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(8.dp))
        Text(
            if (waitingFor.isEmpty()) "Sincronizando…" else "Esperando ${waitingFor.joinToString()}…",
            color = Color.White,
            fontSize = 13.sp,
            maxLines = 1
        )
    }
}

@Composable
fun PartyChip(members: Int, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier
            .clip(RoundedCornerShape(50))
            .background(Pink.copy(alpha = 0.9f))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Default.Groups, null, tint = Color.White, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text("Party • $members", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    }
}

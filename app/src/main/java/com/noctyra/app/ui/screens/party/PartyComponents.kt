package com.noctyra.app.ui.screens.party

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.noctyra.app.party.Member
import com.noctyra.app.party.RoomAddress
import com.noctyra.app.ui.theme.*

private val CardShape = RoundedCornerShape(20.dp)

@Composable
fun PartyCard(title: String, subtitle: String? = null, content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .fillMaxWidth()
            .clip(CardShape)
            .background(SurfaceCard)
            .border(1.dp, CardBorder, CardShape)
            .padding(18.dp)
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, fontSize = 17.sp))
        if (subtitle != null) {
            Spacer(Modifier.height(4.dp))
            Text(subtitle, style = MaterialTheme.typography.bodyMedium)
        }
        Spacer(Modifier.height(14.dp))
        content()
    }
}

@Composable
fun PartyTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    monospace: Boolean = false,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(16.dp)
    Box(
        modifier
            .fillMaxWidth()
            .height(54.dp)
            .clip(shape)
            .background(SurfaceElevated)
            .border(1.dp, CardBorder, shape)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        val style = TextStyle(
            color = TextPrimary,
            fontSize = if (monospace) 20.sp else 16.sp,
            fontFamily = if (monospace) FontFamily.Monospace else FontFamily.Default,
            fontWeight = if (monospace) FontWeight.Bold else FontWeight.Normal,
            letterSpacing = if (monospace) 2.sp else 0.sp
        )
        if (value.isEmpty()) Text(placeholder, style = style.copy(color = TextMuted, fontWeight = FontWeight.Normal))
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = style,
            cursorBrush = SolidColor(Pink),
            keyboardOptions = KeyboardOptions(
                capitalization = if (monospace) KeyboardCapitalization.Characters else KeyboardCapitalization.Words
            ),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
fun RoomCodeRow(address: RoomAddress) {
    val clipboard = LocalClipboardManager.current
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Pink.copy(alpha = 0.1f))
            .border(1.dp, Pink.copy(alpha = 0.35f), RoundedCornerShape(16.dp))
            .clickable { clipboard.setText(AnnotatedString(address.code)) }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(address.label, style = MaterialTheme.typography.labelMedium.copy(color = PinkLight))
            Text(
                address.code,
                color = TextPrimary,
                fontSize = 24.sp,
                fontWeight = FontWeight.Black,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 1.sp
            )
            Text(address.ip, style = MaterialTheme.typography.labelMedium)
        }
        Icon(Icons.Default.ContentCopy, "Copiar código", tint = Pink, modifier = Modifier.size(24.dp))
    }
}

@Composable
fun MemberRow(member: Member, isMe: Boolean) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(if (member.isHost) Pink else SurfaceElevated),
            contentAlignment = Alignment.Center
        ) {
            Text(member.nick.take(1).uppercase(), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }
        Spacer(Modifier.width(12.dp))
        Text(member.nick, style = MaterialTheme.typography.titleSmall.copy(fontSize = 15.sp), modifier = Modifier.weight(1f))
        if (member.isHost) Tag("HOST", Pink)
        if (isMe) {
            Spacer(Modifier.width(6.dp))
            Tag("VOCÊ", TextSecondary)
        }
    }
}

@Composable
private fun Tag(text: String, color: Color) {
    Text(
        text,
        color = color,
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.sp,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .border(1.dp, color.copy(alpha = 0.6f), RoundedCornerShape(50))
            .padding(horizontal = 8.dp, vertical = 3.dp)
    )
}

@Composable
fun HowToStep(number: Int, text: String) {
    Row(Modifier.padding(vertical = 5.dp), verticalAlignment = Alignment.Top) {
        Box(
            Modifier.size(24.dp).clip(CircleShape).background(Pink.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center
        ) {
            Text("$number", color = PinkLight, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(10.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 2.dp))
    }
}

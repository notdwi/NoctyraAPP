package com.noctyra.app.ui.screens.settings

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.annotation.ExperimentalCoilApi
import coil.imageLoader
import com.noctyra.app.data.local.AppSettings
import com.noctyra.app.data.local.LibraryStore
import com.noctyra.app.data.repository.AnimeRepository
import com.noctyra.app.download.DownloadCenter
import com.noctyra.app.download.DownloadStatus
import com.noctyra.app.ui.components.*
import com.noctyra.app.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalCoilApi::class)
@Composable
fun ProfileScreen(onOpenDownloads: () -> Unit, onOpenHistory: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val favorites by LibraryStore.favorites.collectAsStateWithLifecycle()
    val history by LibraryStore.history.collectAsStateWithLifecycle()
    val downloads by DownloadCenter.downloads.collectAsStateWithLifecycle()
    val lite by AppSettings.liteMode.collectAsStateWithLifecycle()
    val autoplay by AppSettings.autoplayNext.collectAsStateWithLifecycle()
    var cacheSize by remember { mutableLongStateOf(-1L) }
    var confirmClear by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        cacheSize = withContext(Dispatchers.IO) { AnimeRepository.cacheSizeBytes(context) }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(BackgroundDark)
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .padding(bottom = BottomBarSpace)
    ) {
        Column(
            Modifier.fillMaxWidth().padding(top = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            ProfileAvatar(size = 96)
            Spacer(Modifier.height(12.dp))
            Text("Noctyra", style = MaterialTheme.typography.headlineMedium)
            Text("ANIMES SEM LIMITES", color = TextSecondary, fontSize = 10.sp, letterSpacing = 3.sp)
        }

        Row(
            Modifier.padding(16.dp).padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            StatCard("${favorites.size}", "Na lista", Modifier.weight(1f))
            StatCard("${history.size}", "Episódios vistos", Modifier.weight(1f))
            StatCard("${downloads.values.count { it.status == DownloadStatus.COMPLETED }}", "Baixados", Modifier.weight(1f))
        }

        SettingsGroup("Biblioteca") {
            SettingsRow(Icons.Default.Download, "Downloads", "Episódios salvos para assistir offline", onClick = onOpenDownloads)
            SettingsRow(Icons.Default.History, "Histórico", "Continue de onde parou", onClick = onOpenHistory)
        }

        SettingsGroup("Reprodução") {
            SettingsRow(
                Icons.Default.SkipNext, "Próximo episódio automático", "Começa o próximo ao terminar",
                onClick = { AppSettings.setAutoplayNext(!autoplay) },
                trailing = { NoctyraSwitch(autoplay) { AppSettings.setAutoplayNext(it) } }
            )
            SettingsRow(
                Icons.Default.Bolt, "Modo leve",
                if (AppSettings.isLowEndDevice) "Recomendado para este aparelho • menos animações e memória"
                else "Menos animações, sombras e uso de memória",
                onClick = { AppSettings.setLiteMode(!lite) },
                trailing = { NoctyraSwitch(lite) { AppSettings.setLiteMode(it) } }
            )
        }

        SettingsGroup("Armazenamento") {
            SettingsRow(
                Icons.Default.CleaningServices, "Limpar cache",
                if (cacheSize >= 0) "Imagens e páginas salvas • ${formatBytes(cacheSize)}" else "Calculando…",
                onClick = { confirmClear = true }
            )
            SettingsRow(Icons.Default.Info, "Versão", appVersion(context))
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            containerColor = SurfaceElevated,
            title = { Text("Limpar cache?") },
            text = { Text("Imagens e páginas salvas serão baixadas de novo. Seus favoritos, histórico e downloads continuam.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmClear = false
                    scope.launch {
                        withContext(Dispatchers.IO) {
                            context.imageLoader.memoryCache?.clear()
                            context.imageLoader.diskCache?.clear()
                            AnimeRepository.clearCaches()
                        }
                        cacheSize = withContext(Dispatchers.IO) { AnimeRepository.cacheSizeBytes(context) }
                    }
                }) { Text("Limpar", color = Pink) }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancelar", color = TextSecondary) } }
        )
    }
}

@Composable
private fun StatCard(value: String, label: String, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier
            .clip(shape)
            .background(SurfaceCard)
            .border(1.dp, CardBorder, shape)
            .padding(vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(value, color = Pink, fontSize = 22.sp, fontWeight = FontWeight.Black)
        Text(label, style = MaterialTheme.typography.labelMedium, maxLines = 1)
    }
}

@Composable
private fun SettingsGroup(title: String, content: @Composable ColumnScope.() -> Unit) {
    Text(
        title.uppercase(),
        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp),
        modifier = Modifier.padding(start = 20.dp, top = 20.dp, bottom = 8.dp)
    )
    val shape = RoundedCornerShape(18.dp)
    Column(
        Modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth()
            .clip(shape)
            .background(SurfaceCard)
            .border(1.dp, CardBorder, shape),
        content = content
    )
}

@Composable
private fun NoctyraSwitch(checked: Boolean, onChange: (Boolean) -> Unit) {
    Switch(
        checked = checked,
        onCheckedChange = onChange,
        colors = SwitchDefaults.colors(
            checkedThumbColor = androidx.compose.ui.graphics.Color.White,
            checkedTrackColor = Pink,
            uncheckedThumbColor = TextSecondary,
            uncheckedTrackColor = SurfaceElevated,
            uncheckedBorderColor = CardBorder
        )
    )
}

private fun appVersion(context: Context): String =
    runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "—"

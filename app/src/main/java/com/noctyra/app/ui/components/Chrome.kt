package com.noctyra.app.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.noctyra.app.R
import com.noctyra.app.discord.DiscordPresence
import com.noctyra.app.ui.theme.*

data class Category(val name: String, val slug: String, val colors: List<Color>, val icon: ImageVector)

val Categories = listOf(
    Category("Ação", "acao", listOf(Color(0xFFEC268F), Color(0xFF7A1250)), Icons.Default.LocalFireDepartment),
    Category("Aventura", "aventura", listOf(Color(0xFFFF8A3D), Color(0xFF8E3A12)), Icons.Default.Explore),
    Category("Comédia", "comedia", listOf(Color(0xFFFFC23D), Color(0xFF8E6112)), Icons.Default.SentimentVerySatisfied),
    Category("Fantasia", "fantasia", listOf(Color(0xFF8B5CF6), Color(0xFF3B1D86)), Icons.Default.AutoAwesome),
    Category("Romance", "romance", listOf(Color(0xFFFF4D6D), Color(0xFF7D1030)), Icons.Default.Favorite),
    Category("Isekai", "isekai", listOf(Color(0xFF22C3E6), Color(0xFF0E5A73)), Icons.Default.Public),
    Category("Drama", "drama", listOf(Color(0xFF5B7CFA), Color(0xFF1E2F80)), Icons.Default.TheaterComedy),
    Category("Sobrenatural", "sobrenatural", listOf(Color(0xFF9D4EDD), Color(0xFF3C096C)), Icons.Default.NightsStay),
    Category("Shounen", "sh-nen", listOf(Color(0xFFF72585), Color(0xFF560BAD)), Icons.Default.Bolt),
    Category("Esportes", "esporte", listOf(Color(0xFF2EC4B6), Color(0xFF0B5D56)), Icons.Default.SportsSoccer),
    Category("Mistério", "misterio", listOf(Color(0xFF6C757D), Color(0xFF212529)), Icons.Default.Search),
    Category("Terror", "terror", listOf(Color(0xFFD00000), Color(0xFF370617)), Icons.Default.Warning),
    Category("Ficção Científica", "sci-fi", listOf(Color(0xFF4CC9F0), Color(0xFF3A0CA3)), Icons.Default.RocketLaunch),
    Category("Slice of Life", "slice-of-life", listOf(Color(0xFF80ED99), Color(0xFF22577A)), Icons.Default.LocalCafe),
    Category("Mecha", "mecha", listOf(Color(0xFFADB5BD), Color(0xFF343A40)), Icons.Default.PrecisionManufacturing),
    Category("Escolar", "escolar", listOf(Color(0xFFFFB5A7), Color(0xFFB5838D)), Icons.Default.School)
)

@Composable
fun BrandHeader(onSearch: () -> Unit, onProfile: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(64.dp)
            .padding(start = 14.dp, end = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Image(
            painter = painterResource(R.drawable.noc_logo),
            contentDescription = null,
            modifier = Modifier.size(width = 50.dp, height = 38.dp)
        )
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(
                "Noctyra",
                color = TextPrimary,
                fontSize = 26.sp,
                fontWeight = FontWeight.ExtraBold,
                lineHeight = 26.sp
            )
            Text(
                "ANIMES SEM LIMITES",
                color = TextSecondary,
                fontSize = 9.sp,
                letterSpacing = 3.sp,
                fontWeight = FontWeight.Medium
            )
        }
        Icon(
            Icons.Default.Search,
            contentDescription = "Buscar",
            tint = Color.White,
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .clickable(onClick = onSearch)
                .padding(9.dp)
        )
        Spacer(Modifier.width(10.dp))
        ProfileAvatar(size = 44, onClick = onProfile)
    }
}

@Composable
fun ProfileAvatar(size: Int, onClick: (() -> Unit)? = null) {
    val user by DiscordPresence.user.collectAsStateWithLifecycle()
    Box(
        Modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(SurfaceElevated)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center
    ) {
        val avatar = user?.avatarUrl.orEmpty()
        if (avatar.isNotEmpty()) {
            NetImage(avatar, Modifier.fillMaxSize(), contentDescription = user?.name)
        } else {
            Image(
                painter = painterResource(R.drawable.noc_logo),
                contentDescription = "Perfil",
                modifier = Modifier.fillMaxSize(0.62f)
            )
        }
    }
}

val HomeTabs = listOf("Início", "Animes", "Lançamentos", "Categorias", "Minha Lista")

@Composable
fun HomeTabRow(selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth()) {
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(1.dp)
                .background(Color.White.copy(alpha = 0.12f))
        )
        Row(
            Modifier
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 6.dp)
        ) {
            HomeTabs.forEachIndexed { index, label ->
                val isSelected = index == selected
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { onSelect(index) }
                        .padding(horizontal = 5.dp)
                        .width(IntrinsicSize.Max)
                ) {
                    Text(
                        label,
                        color = if (isSelected) Pink else TextPrimary,
                        fontSize = 15.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 3.dp, vertical = 10.dp)
                    )
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(3.dp)
                            .clip(RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp))
                            .background(if (isSelected) Pink else Color.Transparent)
                    )
                }
            }
        }
    }
}

enum class BottomTab(val route: String, val label: String, val icon: ImageVector, val selectedIcon: ImageVector) {
    Home("home", "Início", Icons.Outlined.Home, Icons.Filled.Home),
    Explore("explore", "Explorar", Icons.Outlined.Explore, Icons.Filled.Explore),
    MyList("mylist", "Minha Lista", Icons.Outlined.BookmarkBorder, Icons.Filled.Bookmark),
    Profile("profile", "Perfil", Icons.Outlined.Person, Icons.Filled.Person)
}

@Composable
fun NoctyraBottomBar(
    currentRoute: String?,
    onTab: (BottomTab) -> Unit,
    onSearch: () -> Unit,
    modifier: Modifier = Modifier
) {
    val lite = rememberLiteMode()
    val shape = RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp)
    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.BottomCenter) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(BottomBarColor)
                .border(1.dp, Color.White.copy(alpha = 0.06f), shape)
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(70.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                BarItem(BottomTab.Home, currentRoute, onTab, Modifier.weight(1f))
                BarItem(BottomTab.Explore, currentRoute, onTab, Modifier.weight(1f))
                Spacer(Modifier.weight(1f))
                BarItem(BottomTab.MyList, currentRoute, onTab, Modifier.weight(1f))
                BarItem(BottomTab.Profile, currentRoute, onTab, Modifier.weight(1f))
            }
            Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
        }
        Box(
            Modifier
                .align(Alignment.TopCenter)
                .offset(y = (-26).dp)
                .size(64.dp)
                .then(if (lite) Modifier else Modifier.shadow(18.dp, CircleShape, ambientColor = Pink, spotColor = Pink))
                .clip(CircleShape)
                .background(Pink)
                .clickable(onClick = onSearch),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Search, "Buscar", tint = Color.White, modifier = Modifier.size(32.dp))
        }
    }
}

@Composable
private fun BarItem(tab: BottomTab, currentRoute: String?, onTab: (BottomTab) -> Unit, modifier: Modifier) {
    val selected = currentRoute?.substringBefore('?') == tab.route
    val color = if (selected) Pink else TextSecondary
    Column(
        modifier = modifier
            .fillMaxHeight()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onTab(tab) },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(if (selected) tab.selectedIcon else tab.icon, tab.label, tint = color, modifier = Modifier.size(28.dp))
        Spacer(Modifier.height(3.dp))
        Text(tab.label, color = color, fontSize = 12.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium, maxLines = 1)
    }
}

val BottomBarSpace = 110.dp

@Composable
fun ScreenTitle(title: String, subtitle: String? = null, modifier: Modifier = Modifier) {
    Column(modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text(title, style = MaterialTheme.typography.headlineLarge)
        if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodyMedium)
    }
}

package llm.slop.spazradio.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.EventNote
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Waves
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import llm.slop.spazradio.ActiveUtility
import llm.slop.spazradio.AppMode
import llm.slop.spazradio.ui.theme.DeepBlue
import llm.slop.spazradio.ui.theme.NeonGreen

@Composable
fun FooterToolbar(
    appMode: AppMode,
    activeUtility: ActiveUtility,
    onUtilityClick: (ActiveUtility) -> Unit,
    modifier: Modifier = Modifier
) {
    NavigationBar(
        modifier = modifier.fillMaxWidth(),
        containerColor = Color.Black.copy(alpha = 0.5f),
        contentColor = NeonGreen,
        tonalElevation = 0.dp
    ) {
        // Chat
        NavigationBarItem(
            selected = activeUtility == ActiveUtility.CHAT,
            onClick = { onUtilityClick(ActiveUtility.CHAT) },
            icon = { Icon(Icons.Default.Chat, contentDescription = "Chat") },
            label = { Text("Chat") },
            colors = navigationBarItemColors()
        )

        // Info (Dynamic: Schedule or Archive List)
        val infoLabel = if (appMode == AppMode.RADIO) "Schedule" else "List"
        val infoIcon = if (appMode == AppMode.RADIO) Icons.Default.EventNote else Icons.Default.List
        
        NavigationBarItem(
            selected = activeUtility == ActiveUtility.INFO,
            onClick = { onUtilityClick(ActiveUtility.INFO) },
            icon = { Icon(infoIcon, contentDescription = infoLabel) },
            label = { Text(infoLabel) },
            colors = navigationBarItemColors()
        )

        // Visuals
        NavigationBarItem(
            selected = activeUtility == ActiveUtility.VISUALS,
            onClick = { onUtilityClick(ActiveUtility.VISUALS) },
            icon = { Icon(Icons.Default.Waves, contentDescription = "Visuals") },
            label = { Text("Visuals") },
            colors = navigationBarItemColors()
        )

        // Settings
        NavigationBarItem(
            selected = activeUtility == ActiveUtility.SETTINGS,
            onClick = { onUtilityClick(ActiveUtility.SETTINGS) },
            icon = { Icon(Icons.Default.Settings, contentDescription = "Settings") },
            label = { Text("Settings") },
            colors = navigationBarItemColors()
        )
    }
}

@Composable
private fun navigationBarItemColors() = NavigationBarItemDefaults.colors(
    selectedIconColor = Color.Black,
    selectedTextColor = NeonGreen,
    indicatorColor = NeonGreen,
    unselectedIconColor = NeonGreen,
    unselectedTextColor = NeonGreen.copy(alpha = 0.7f)
)

private val androidx.compose.ui.unit.Dp.Companion.dp get() = 0.dp // Fallback if 0.dp is needed

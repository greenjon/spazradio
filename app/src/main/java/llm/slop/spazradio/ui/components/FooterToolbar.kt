package llm.slop.spazradio.ui.components

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.EventNote
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Waves
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import llm.slop.spazradio.ActiveUtility
import llm.slop.spazradio.AppMode
import llm.slop.spazradio.ui.theme.NeonGreen

@Composable
fun FooterToolbar(
    appMode: AppMode,
    activeUtility: ActiveUtility,
    visualsEnabled: Boolean,
    onUtilityClick: (ActiveUtility) -> Unit,
    onToggleVisuals: () -> Unit,
    modifier: Modifier = Modifier
) {
    NavigationBar(
        modifier = modifier.fillMaxWidth(),
        // Changed base color to #00007F with 75% opacity (BF in hex)
        containerColor = Color(0xBF00007F), 
        contentColor = NeonGreen,
        tonalElevation = 0.dp,
        // Disable automatic window insets because we handle them in the parent Scaffold/Box
        windowInsets = WindowInsets(0, 0, 0, 0)
    ) {
        // Info (Dynamic: Schedule or Archive List) - Moved to the left
        val infoLabel = if (appMode == AppMode.RADIO) "Schedule" else "List"
        val infoIcon = if (appMode == AppMode.RADIO) Icons.Default.EventNote else Icons.AutoMirrored.Filled.List
        
        NavigationBarItem(
            selected = activeUtility == ActiveUtility.INFO,
            onClick = { onUtilityClick(ActiveUtility.INFO) },
            icon = { Icon(infoIcon, contentDescription = infoLabel) },
            label = { Text(infoLabel) },
            colors = navigationBarItemColors()
        )

        // Chat
        NavigationBarItem(
            selected = activeUtility == ActiveUtility.CHAT,
            onClick = { onUtilityClick(ActiveUtility.CHAT) },
            icon = { Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = "Chat") },
            label = { Text("Chat") },
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

        // Visuals (Independent sticky button)
        NavigationBarItem(
            selected = visualsEnabled,
            onClick = onToggleVisuals,
            icon = { Icon(Icons.Default.Waves, contentDescription = "Visuals") },
            label = { Text("Visuals") },
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

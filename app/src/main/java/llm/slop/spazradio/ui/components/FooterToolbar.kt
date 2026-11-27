package llm.slop.spazradio.ui.components

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.EventNote
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Waves
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarDefaults
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import llm.slop.spazradio.R
import llm.slop.spazradio.ActiveUtility
import llm.slop.spazradio.AppMode

@Composable
fun FooterToolbar(
    appMode: AppMode,
    activeUtility: ActiveUtility,
    visualsEnabled: Boolean,
    onUtilityClick: (ActiveUtility) -> Unit,
    onToggleVisuals: () -> Unit,
    modifier: Modifier = Modifier
) {
    // We check the navigation bar height to ensure we don't overlap system buttons.
    // NavigationBar handles this internally via windowInsets, but we explicitly
    // use the default to ensure it "checks" if the user is using a nav bar.
    NavigationBar(
        modifier = modifier.fillMaxWidth(),
        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.25f),
        contentColor = MaterialTheme.colorScheme.primary,
        tonalElevation = 0.dp,
        windowInsets = NavigationBarDefaults.windowInsets
    ) {
        val infoLabel = stringResource(if (appMode == AppMode.RADIO) R.string.schedule_title else R.string.label_list)
        val infoIcon = if (appMode == AppMode.RADIO) Icons.AutoMirrored.Filled.EventNote else Icons.AutoMirrored.Filled.List
        
        NavigationBarItem(
            selected = activeUtility == ActiveUtility.INFO,
            onClick = { onUtilityClick(ActiveUtility.INFO) },
            icon = { Icon(infoIcon, contentDescription = infoLabel) },
            label = { Text(infoLabel) },
            colors = navigationBarItemColors()
        )

        NavigationBarItem(
            selected = activeUtility == ActiveUtility.CHAT,
            onClick = { onUtilityClick(ActiveUtility.CHAT) },
            icon = { Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = stringResource(R.string.label_chat)) },
            label = { Text(stringResource(R.string.label_chat)) },
            colors = navigationBarItemColors()
        )

        NavigationBarItem(
            selected = activeUtility == ActiveUtility.SETTINGS,
            onClick = { onUtilityClick(ActiveUtility.SETTINGS) },
            icon = { Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.settings_title)) },
            label = { Text(stringResource(R.string.settings_title)) },
            colors = navigationBarItemColors()
        )

        NavigationBarItem(
            selected = visualsEnabled,
            onClick = onToggleVisuals,
            icon = { Icon(Icons.Default.Waves, contentDescription = stringResource(R.string.label_visuals)) },
            label = { Text(stringResource(R.string.label_visuals)) },
            colors = navigationBarItemColors()
        )
    }
}

@Composable
private fun navigationBarItemColors() = NavigationBarItemDefaults.colors(
    selectedIconColor = MaterialTheme.colorScheme.onPrimary,
    unselectedIconColor = MaterialTheme.colorScheme.onSecondary,
    selectedTextColor = MaterialTheme.colorScheme.onSurface,
    unselectedTextColor = MaterialTheme.colorScheme.onSurface,
    indicatorColor = MaterialTheme.colorScheme.primary
)

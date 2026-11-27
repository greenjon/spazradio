package llm.slop.spazradio.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.EventNote
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Waves
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import llm.slop.spazradio.ActiveUtility
import llm.slop.spazradio.AppMode
import llm.slop.spazradio.R

/**
 * The landscape-phone controls: header and toolbar squeezed into one strip along the bottom, so the
 * visuals get the rest of the screen. A seek bar sits above it while an archive show plays.
 */
@Composable
fun CinemaStrip(
    trackStatus: String,
    listenerCount: String,
    archiveCount: Int,
    isPlaying: Boolean,
    isArchivePlaying: Boolean,
    playbackPosition: Long,
    playbackDuration: Long,
    appMode: AppMode,
    onPlayPause: () -> Unit,
    onSeek: (Long) -> Unit,
    onModeChange: (AppMode) -> Unit,
    download: HeaderDownload?,
    onDownload: () -> Unit,
    activeUtility: ActiveUtility,
    visualsEnabled: Boolean,
    onUtilityClick: (ActiveUtility) -> Unit,
    onToggleVisuals: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scrim = MaterialTheme.colorScheme.surface
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color.Transparent, scrim.copy(alpha = 0.75f))))
            // The scrim runs edge to edge; the controls keep clear of a side navigation bar or camera cutout.
            .windowInsetsPadding(WindowInsets.navigationBars.only(WindowInsetsSides.Bottom))
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
            .padding(horizontal = 8.dp)
    ) {
        if (isArchivePlaying && playbackDuration > 0) {
            PlaybackControls(
                position = playbackPosition,
                duration = playbackDuration,
                onSeek = onSeek,
                modifier = Modifier.padding(horizontal = 8.dp)
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onPlayPause) {
                Icon(
                    painter = painterResource(id = if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play_arrow),
                    contentDescription = stringResource(if (isPlaying) R.string.label_pause else R.string.label_play),
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(36.dp)
                )
            }
            Text(
                text = trackStatus,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                // Long show names scroll instead of being cut off.
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 4.dp)
                    .basicMarquee(iterations = Int.MAX_VALUE)
            )
            HeaderDownloadButton(download, onDownload)
            ModeTab(
                label = stringResource(R.string.label_radio),
                detail = listenerCount,
                selected = appMode == AppMode.RADIO,
                onClick = { onModeChange(AppMode.RADIO) }
            )
            ModeTab(
                label = stringResource(R.string.label_archives),
                detail = if (archiveCount > 0) stringResource(R.string.label_archives_count, archiveCount) else "",
                selected = appMode == AppMode.ARCHIVES,
                onClick = { onModeChange(AppMode.ARCHIVES) }
            )
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                val radio = appMode == AppMode.RADIO
                StripToggle(
                    icon = if (radio) Icons.AutoMirrored.Filled.EventNote else Icons.AutoMirrored.Filled.List,
                    label = stringResource(if (radio) R.string.schedule_title else R.string.label_list),
                    checked = activeUtility == ActiveUtility.INFO,
                    onClick = { onUtilityClick(ActiveUtility.INFO) }
                )
                StripToggle(
                    icon = Icons.AutoMirrored.Filled.Chat,
                    label = stringResource(R.string.label_chat),
                    checked = activeUtility == ActiveUtility.CHAT,
                    onClick = { onUtilityClick(ActiveUtility.CHAT) }
                )
                StripToggle(
                    icon = Icons.Default.Settings,
                    label = stringResource(R.string.settings_title),
                    checked = activeUtility == ActiveUtility.SETTINGS,
                    onClick = { onUtilityClick(ActiveUtility.SETTINGS) }
                )
                StripToggle(
                    icon = Icons.Default.Waves,
                    label = stringResource(R.string.label_visuals),
                    checked = visualsEnabled,
                    onClick = onToggleVisuals
                )
            }
        }
    }
}

/** RADIO / ARCHIVES as two small tabs, each with its count underneath; the selected one is underlined. */
@Composable
private fun ModeTab(label: String, detail: String, selected: Boolean, onClick: () -> Unit) {
    val color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (selected) 1f else 0.55f)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .selectable(selected = selected, onClick = onClick, role = Role.Tab)
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(
            text = label.uppercase(),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = color
        )
        if (detail.isNotBlank()) {
            Text(text = detail, fontSize = 10.sp, color = color, maxLines = 1)
        }
        Box(
            Modifier
                .padding(top = 2.dp)
                .width(24.dp)
                .height(3.dp)
                .background(
                    if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
                    RoundedCornerShape(2.dp)
                )
        )
    }
}

/** An icon-only toolbar button: a filled circle when on, like the portrait toolbar's indicator. */
@Composable
private fun StripToggle(icon: ImageVector, label: String, checked: Boolean, onClick: () -> Unit) {
    IconToggleButton(
        checked = checked,
        onCheckedChange = { onClick() },
        colors = IconButtonDefaults.iconToggleButtonColors(
            contentColor = MaterialTheme.colorScheme.primary,
            checkedContainerColor = MaterialTheme.colorScheme.primary,
            checkedContentColor = MaterialTheme.colorScheme.onPrimary
        )
    ) {
        Icon(icon, contentDescription = label)
    }
}

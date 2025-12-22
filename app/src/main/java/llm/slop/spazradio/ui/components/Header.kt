package llm.slop.spazradio.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import llm.slop.spazradio.AppMode
import llm.slop.spazradio.R
import java.util.Locale

@Composable
fun PlayerHeader(
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
    onModeChange: (AppMode) -> Unit
) {
    val tabRowBackgroundColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.75f)

    Column(modifier = Modifier.fillMaxWidth()) {
        // Full-width background covering status bar and tab row
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(tabRowBackgroundColor)
                .statusBarsPadding()
        ) {
            PrimaryTabRow(
                selectedTabIndex = if (appMode == AppMode.RADIO) 0 else 1,
                containerColor = Color.Transparent, // Color handled by the outer Box
                contentColor = MaterialTheme.colorScheme.onSurface,
                indicator = {
                    TabRowDefaults.PrimaryIndicator(
                        modifier = Modifier.tabIndicatorOffset(if (appMode == AppMode.RADIO) 0 else 1),
                        color = MaterialTheme.colorScheme.primary
                    )
                },
                divider = {}
            ) {
                Tab(
                    selected = appMode == AppMode.RADIO,
                    onClick = { onModeChange(AppMode.RADIO) },
                    text = { 
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                stringResource(R.string.label_radio).uppercase(), 
                                style = MaterialTheme.typography.titleLarge, // Changed from titleMedium
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            if (listenerCount.isNotBlank()) {
                                Text(
                                    text = listenerCount,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontSize = 11.sp, // Slightly bigger
                                        shadow = Shadow(
                                            color = Color.Black.copy(alpha = 0.5f),
                                            offset = Offset(1f, 1f),
                                            blurRadius = 2f
                                        )
                                    ),
                                    color = MaterialTheme.colorScheme.onSurface,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                )
                Tab(
                    selected = appMode == AppMode.ARCHIVES,
                    onClick = { onModeChange(AppMode.ARCHIVES) },
                    text = { 
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                stringResource(R.string.label_archives).uppercase(), 
                                style = MaterialTheme.typography.titleLarge, // Changed from titleMedium
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            if (archiveCount > 0) {
                                Text(
                                    text = stringResource(R.string.label_archives_count, archiveCount),
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontSize = 11.sp, // Slightly bigger
                                        shadow = Shadow(
                                            color = Color.Black.copy(alpha = 0.5f),
                                            offset = Offset(1f, 1f),
                                            blurRadius = 2f
                                        )
                                    ),
                                    color = MaterialTheme.colorScheme.onSurface,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onPlayPause) {
                Icon(
                    painter = painterResource(id = if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play_arrow),
                    contentDescription = if (isPlaying) stringResource(R.string.label_pause) else stringResource(R.string.label_play),
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(48.dp)
                )
            }
            
            Box(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                if (trackStatus.isNotBlank()) {
                    Text(
                        text = trackStatus,
                        style = MaterialTheme.typography.titleMedium.copy(
                            shadow = Shadow(
                                color = Color.Black.copy(alpha = 0.3f),
                                offset = Offset(2f, 2f),
                                blurRadius = 4f
                            )
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            
            Box(modifier = Modifier.size(48.dp))
        }

        if (isArchivePlaying && playbackDuration > 0) {
            PlaybackControls(
                position = playbackPosition,
                duration = playbackDuration,
                onSeek = onSeek,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )
        }
    }
}

@Composable
fun PlaybackControls(
    position: Long,
    duration: Long,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = formatTime(position),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
            fontSize = 12.sp,
            modifier = Modifier.widthIn(min = 45.dp)
        )
        Slider(
            value = position.toFloat(),
            onValueChange = { onSeek(it.toLong()) },
            valueRange = 0f..duration.toFloat(),
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary,
                inactiveTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.24f)
            ),
            modifier = Modifier.weight(1f)
        )
        Text(
            text = formatTime(duration),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
            fontSize = 12.sp,
            modifier = Modifier.widthIn(min = 45.dp),
            textAlign = TextAlign.End
        )
    }
}

private fun formatTime(ms: Long): String {
    val totalSeconds = ms / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format(Locale.getDefault(), "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
    }
}

package llm.slop.spazradio.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import llm.slop.spazradio.AppMode
import llm.slop.spazradio.R
import llm.slop.spazradio.ui.theme.NeonGreen
import java.util.Locale

@Composable
fun PlayerHeader(
    title: String,
    isPlaying: Boolean,
    isArchivePlaying: Boolean,
    playbackPosition: Long,
    playbackDuration: Long,
    appMode: AppMode,
    onPlayPause: () -> Unit,
    onSeek: (Long) -> Unit,
    onModeChange: (AppMode) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        TabRow(
            selectedTabIndex = if (appMode == AppMode.RADIO) 0 else 1,
            // Changed base color to #00007F with 75% opacity (BF in hex)
            containerColor = Color(0xBF00007F), 
            contentColor = NeonGreen,
            indicator = { tabPositions ->
                TabRowDefaults.PrimaryIndicator(
                    modifier = Modifier.tabIndicatorOffset(tabPositions[if (appMode == AppMode.RADIO) 0 else 1]),
                    color = NeonGreen
                )
            },
            divider = {}
        ) {
            Tab(
                selected = appMode == AppMode.RADIO,
                onClick = { onModeChange(AppMode.RADIO) },
                text = { Text("RADIO", style = MaterialTheme.typography.labelLarge) }
            )
            Tab(
                selected = appMode == AppMode.ARCHIVES,
                onClick = { onModeChange(AppMode.ARCHIVES) },
                text = { Text("ARCHIVES", style = MaterialTheme.typography.labelLarge) }
            )
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
                    tint = NeonGreen,
                    modifier = Modifier.size(48.dp)
                )
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge.copy(
                    shadow = Shadow(
                        color = Color.Black,
                        offset = Offset(2f, 2f),
                        blurRadius = 4f
                    )
                ),
                color = Color(0xFFFFFF00),
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f)
            )
            // Spacer to keep title centered since gear icon is gone
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
            color = NeonGreen,
            fontSize = 12.sp,
            modifier = Modifier.widthIn(min = 45.dp)
        )
        Slider(
            value = position.toFloat(),
            onValueChange = { onSeek(it.toLong()) },
            valueRange = 0f..duration.toFloat(),
            colors = SliderDefaults.colors(
                thumbColor = NeonGreen,
                activeTrackColor = NeonGreen,
                inactiveTrackColor = NeonGreen.copy(alpha = 0.24f)
            ),
            modifier = Modifier.weight(1f)
        )
        Text(
            text = formatTime(duration),
            style = MaterialTheme.typography.labelSmall,
            color = NeonGreen,
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

@Composable
fun TrackTitle(title: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge.copy(
                shadow = Shadow(
                    color = Color.Black,
                    offset = Offset(2f, 2f),
                    blurRadius = 4f
                )
            ),
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            color = NeonGreen
        )
    }
}

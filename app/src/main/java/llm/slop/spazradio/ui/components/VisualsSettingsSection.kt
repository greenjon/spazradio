package llm.slop.spazradio.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.TextButton
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import java.text.DateFormat
import java.util.Date
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import llm.slop.spazradio.R
import llm.slop.spazradio.RadioViewModel
import llm.slop.spazradio.visuals.ReducedMotion
import llm.slop.spazradio.visuals.VisualQuality
import llm.slop.spazradio.visuals.VisualsSettings
import kotlin.math.roundToInt

/** Settings > Visuals: how the web visuals behind the radio look and move. */
@Composable
fun VisualsSettingsSection(radioViewModel: RadioViewModel) {
    val v by radioViewModel.visuals.collectAsState()
    val failed by radioViewModel.webVisualsFailed.collectAsState()
    val muted = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)

    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(12.dp))
            Column {
                Text(stringResource(R.string.visuals_settings), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                if (failed) Text(stringResource(R.string.visuals_fallback), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
        }

        val visualsOn by radioViewModel.lissajousMode.collectAsState()
        val autoHide by radioViewModel.autoHideControls.collectAsState()
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.visuals_auto_hide), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
                Text(stringResource(R.string.visuals_auto_hide_help), style = MaterialTheme.typography.bodySmall, color = muted)
            }
            Switch(
                checked = autoHide,
                onCheckedChange = { radioViewModel.setAutoHideControls(it) },
                enabled = visualsOn,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = MaterialTheme.colorScheme.primary,
                    checkedTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                ),
            )
        }

        Label(stringResource(R.string.visuals_quality), muted)
        ChipRow(
            options = listOf(
                VisualQuality.AUTO to stringResource(R.string.visuals_auto),
                VisualQuality.LOW to stringResource(R.string.visuals_quality_low),
                VisualQuality.HIGH to stringResource(R.string.visuals_quality_high),
            ),
            selected = v.quality,
            onSelect = { q -> radioViewModel.updateVisuals { it.copy(quality = q) } },
        )

        Label(stringResource(R.string.visuals_reactivity, (v.reactivity * 100).roundToInt()), muted)
        Slider(
            value = v.reactivity,
            onValueChange = { r -> radioViewModel.updateVisuals { it.copy(reactivity = (r * 20).roundToInt() / 20f) } },
            valueRange = 0.25f..4f,
        )

        Label(stringResource(R.string.visuals_reduced_motion), muted)
        ChipRow(
            options = listOf(
                ReducedMotion.AUTO to stringResource(R.string.visuals_auto),
                ReducedMotion.ON to stringResource(R.string.visuals_on),
                ReducedMotion.OFF to stringResource(R.string.visuals_off),
            ),
            selected = v.reducedMotion,
            onSelect = { m -> radioViewModel.updateVisuals { it.copy(reducedMotion = m) } },
        )

        Label(stringResource(R.string.visuals_sync, v.syncMs), muted)
        Slider(
            value = v.syncMs.toFloat(),
            onValueChange = { ms -> radioViewModel.updateVisuals { it.copy(syncMs = (ms / 10).roundToInt() * 10) } },
            valueRange = 0f..VisualsSettings.MAX_SYNC_MS.toFloat(),
        )
        Text(stringResource(R.string.visuals_sync_help), style = MaterialTheme.typography.bodySmall, color = muted)

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.visuals_live), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
                Text(stringResource(R.string.visuals_live_help), style = MaterialTheme.typography.bodySmall, color = muted)
            }
            Switch(
                checked = v.live,
                onCheckedChange = { on -> radioViewModel.updateVisuals { it.copy(live = on) } },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = MaterialTheme.colorScheme.primary,
                    checkedTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                ),
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.visuals_updates), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
                Text(stringResource(R.string.visuals_updates_help), style = MaterialTheme.typography.bodySmall, color = muted)
            }
            Switch(
                checked = v.contentUpdates,
                onCheckedChange = { on -> radioViewModel.updateVisuals { it.copy(contentUpdates = on) } },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = MaterialTheme.colorScheme.primary,
                    checkedTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                ),
            )
        }
        val version by radioViewModel.contentVersion.collectAsState()
        val status by radioViewModel.contentStatus.collectAsState()
        val shown = version?.let { DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(it * 1000)) }
            ?: stringResource(R.string.visuals_updates_builtin)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.visuals_updates_version, shown), style = MaterialTheme.typography.bodySmall, color = muted)
                val statusText = when (status) {
                    RadioViewModel.ContentStatus.IDLE -> null
                    RadioViewModel.ContentStatus.CHECKING -> R.string.visuals_updates_checking
                    RadioViewModel.ContentStatus.UPDATED -> R.string.visuals_updates_updated
                    RadioViewModel.ContentStatus.UP_TO_DATE -> R.string.visuals_updates_current
                    RadioViewModel.ContentStatus.NEEDS_NEWER_APP -> R.string.visuals_updates_newer_app
                    RadioViewModel.ContentStatus.FAILED -> R.string.visuals_updates_failed
                }
                if (statusText != null) Text(stringResource(statusText), style = MaterialTheme.typography.bodySmall, color = muted)
            }
            TextButton(onClick = { radioViewModel.checkContentNow() }, enabled = status != RadioViewModel.ContentStatus.CHECKING) {
                Text(stringResource(R.string.visuals_updates_check))
            }
        }
    }
}

@Composable
private fun Label(text: String, color: androidx.compose.ui.graphics.Color) {
    Spacer(Modifier.height(12.dp))
    Text(text, style = MaterialTheme.typography.bodyMedium, color = color)
}

@Composable
private fun <T> ChipRow(options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
        options.forEach { (value, label) ->
            FilterChip(selected = value == selected, onClick = { onSelect(value) }, label = { Text(label) })
        }
    }
}

package llm.slop.spazradio.ui.components

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import llm.slop.spazradio.EqBands
import llm.slop.spazradio.EqMode
import llm.slop.spazradio.R
import llm.slop.spazradio.RadioService
import llm.slop.spazradio.RadioViewModel
import llm.slop.spazradio.SystemEq
import kotlin.math.roundToInt

/**
 * Settings > Equalizer. With a system equalizer on the phone: Off / Built-in / System.
 * Without one, a plain on/off switch for the built-in EQ, and no mention of a system one.
 */
@Composable
fun EqualizerSection(radioViewModel: RadioViewModel) {
    val context = LocalContext.current
    val systemAvailable = remember { SystemEq.isAvailable(context) }
    val saved by radioViewModel.eqMode.collectAsState()
    val mode = if (saved == EqMode.SYSTEM && !systemAvailable) EqMode.OFF else saved
    val bands by radioViewModel.eqBands.collectAsState()
    val sessionId by RadioService.audioSessionId.collectAsState()
    val muted = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
    // The panel reads the calling package, which only an activity-for-result launch provides.
    val panel = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {}

    Column(modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) {
        if (systemAvailable) {
            Text(stringResource(R.string.eq_title), style = MaterialTheme.typography.bodyMedium, color = muted)
            ChipRow(
                options = listOf(
                    EqMode.OFF to stringResource(R.string.eq_off),
                    EqMode.BUILT_IN to stringResource(R.string.eq_built_in),
                    EqMode.SYSTEM to stringResource(R.string.eq_system),
                ),
                selected = mode,
                onSelect = { radioViewModel.setEqMode(it) },
            )
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(stringResource(R.string.eq_title), style = MaterialTheme.typography.bodyMedium, color = muted, modifier = Modifier.weight(1f))
                Switch(
                    checked = mode == EqMode.BUILT_IN,
                    onCheckedChange = { radioViewModel.setEqMode(if (it) EqMode.BUILT_IN else EqMode.OFF) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = MaterialTheme.colorScheme.primary,
                        checkedTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                    ),
                )
            }
        }

        when (mode) {
            EqMode.BUILT_IN -> {
                Band(stringResource(R.string.eq_bass), bands.bassDb) { radioViewModel.setEqBands(bands.copy(bassDb = it)) }
                Band(stringResource(R.string.eq_mid), bands.midDb) { radioViewModel.setEqBands(bands.copy(midDb = it)) }
                Band(stringResource(R.string.eq_treble), bands.trebleDb) { radioViewModel.setEqBands(bands.copy(trebleDb = it)) }
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.eq_built_in_help), style = MaterialTheme.typography.bodySmall, color = muted, modifier = Modifier.weight(1f))
                    TextButton(onClick = { radioViewModel.setEqBands(EqBands()) }, enabled = !bands.isFlat) {
                        Text(stringResource(R.string.eq_flat))
                    }
                }
            }
            EqMode.SYSTEM -> {
                Text(stringResource(R.string.eq_system_help), style = MaterialTheme.typography.bodySmall, color = muted)
                OutlinedButton(
                    onClick = { panel.launch(SystemEq.panelIntent(context, sessionId)) },
                    modifier = Modifier.padding(top = 4.dp),
                ) { Text(stringResource(R.string.eq_open_system)) }
            }
            EqMode.OFF -> Unit
        }
    }
}

@Composable
private fun Band(name: String, db: Int, onChange: (Int) -> Unit) {
    val shown = if (db > 0) "+$db" else "$db"
    Text(
        stringResource(R.string.eq_band_value, name, shown),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.padding(top = 8.dp),
    )
    Slider(
        value = db.toFloat(),
        onValueChange = { onChange(it.roundToInt()) },
        valueRange = EqBands.MIN_DB.toFloat()..EqBands.MAX_DB.toFloat(),
        steps = EqBands.MAX_DB - EqBands.MIN_DB - 1,
    )
}

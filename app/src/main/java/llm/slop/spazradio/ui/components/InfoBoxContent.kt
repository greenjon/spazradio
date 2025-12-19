package llm.slop.spazradio.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import llm.slop.spazradio.RadioViewModel
import llm.slop.spazradio.ScheduleItem
import llm.slop.spazradio.ScheduleViewModel
import llm.slop.spazradio.ui.theme.DeepBlue
import llm.slop.spazradio.ui.theme.NeonGreen

@Composable
fun SettingsContent(
    radioViewModel: RadioViewModel
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { /* Absorb clicks */ },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "Settings",
                style = MaterialTheme.typography.titleMedium,
                color = NeonGreen
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Other settings will appear here.",
                style = MaterialTheme.typography.bodySmall,
                color = NeonGreen.copy(alpha = 0.5f)
            )
        }
    }
}

@Composable
fun VisualsContent(
    radioViewModel: RadioViewModel
) {
    val lissajousMode by radioViewModel.lissajousMode.collectAsState()

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Waveform Visualizer",
                style = MaterialTheme.typography.bodyLarge,
                color = NeonGreen
            )
            Checkbox(
                checked = lissajousMode,
                onCheckedChange = { radioViewModel.setLissajousMode(it) },
                colors = CheckboxDefaults.colors(
                    checkedColor = NeonGreen,
                    uncheckedColor = NeonGreen,
                    checkmarkColor = DeepBlue
                )
            )
        }
        
        Text(
            text = "The audio-responsive waveform is displayed in the main player area.",
            style = MaterialTheme.typography.bodyMedium,
            color = NeonGreen.copy(alpha = 0.7f)
        )
    }
}

@Composable
fun ScheduleContent(
    scheduleViewModel: ScheduleViewModel
) {
    val schedule by scheduleViewModel.schedule.collectAsState()
    val loading by scheduleViewModel.loading.collectAsState()
    val error by scheduleViewModel.error.collectAsState()

    if (loading) {
        LoadingContent()
    } else if (error != null) {
        ErrorContent(message = "Schedule unavailable", onRetry = { scheduleViewModel.loadSchedule() })
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize()
        ) {
            items(schedule) { item ->
                ScheduleItemRow(item)
            }
        }
    }
}

@Composable
fun ScheduleItemRow(item: ScheduleItem) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Text(
            text = "${item.datePart} • ${item.startTime} - ${item.endTime}",
            style = MaterialTheme.typography.bodyLarge,
            color = NeonGreen
        )
        Text(
            text = item.showName,
            style = MaterialTheme.typography.bodyLarge,
            color = Color(0xFFFFFF00)
        )
    }
}

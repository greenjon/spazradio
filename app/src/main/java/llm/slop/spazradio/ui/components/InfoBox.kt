package llm.slop.spazradio.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import llm.slop.spazradio.*
import llm.slop.spazradio.R
import llm.slop.spazradio.ui.theme.DeepBlue
import llm.slop.spazradio.ui.theme.NeonGreen

@Composable
fun InfoBox(
    showSettings: Boolean,
    onCloseSettings: () -> Unit,
    lissajousMode: MutableState<Boolean>,
    showSchedule: MutableState<Boolean>,
    scheduleViewModel: ScheduleViewModel,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .background(Color(0x80000000), RoundedCornerShape(16.dp)) // 50% transparent black
            .border(3.dp, NeonGreen, RoundedCornerShape(16.dp))
            .clip(RoundedCornerShape(16.dp))
    ) {
        if (showSettings) {
            SettingsScreen(
                onBack = onCloseSettings,
                lissajousMode = lissajousMode,
                showSchedule = showSchedule
            )
        } else {
            // Schedule Section (Bottom)
            val schedule by scheduleViewModel.schedule.collectAsState()
            val loading by scheduleViewModel.loading.collectAsState()
            val error by scheduleViewModel.error.collectAsState()

            if (loading) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                    color = NeonGreen
                )
            } else if (error != null) {
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("Schedule unavailable", color = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = { scheduleViewModel.loadSchedule() }) {
                        Text("Retry")
                    }
                }
            }
            else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(8.dp)
                ) {
                    item {
                        Text(
                            text = stringResource(R.string.schedule_title),
                            style = MaterialTheme.typography.titleLarge,
                            modifier = Modifier.padding(bottom = 8.dp),
                            color = Color(0xFFFFFF00)
                        )
                    }
                    items(schedule) { item ->
                        ScheduleItemRow(item)
                    }
                }
            }
        }
    }
}

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    lissajousMode: MutableState<Boolean>,
    showSchedule: MutableState<Boolean>
) {
    // Use a column but ensure it fits in the container
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { /* Absorb clicks */ },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top // Align top to start listing settings
    ) {
        Text(
            text = stringResource(R.string.settings_title),
            style = MaterialTheme.typography.headlineMedium,
            color = Color(0xFFFFFF00),
            textAlign = TextAlign.Left,
            //       fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        // Scrollable content if settings grow
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            // Lissajous Mode Control
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = stringResource(R.string.show_visuals),
                    style = MaterialTheme.typography.bodyLarge,
                    color = NeonGreen
                )
                Checkbox(
                    checked = lissajousMode.value,
                    onCheckedChange = { lissajousMode.value = it },
                    colors = CheckboxDefaults.colors(
                        checkedColor = NeonGreen,
                        uncheckedColor = NeonGreen,
                        checkmarkColor = DeepBlue
                    )
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Show Schedule Control
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = stringResource(R.string.show_schedule),
                    style = MaterialTheme.typography.bodyLarge,
                    color = NeonGreen
                )
                Checkbox(
                    checked = showSchedule.value,
                    onCheckedChange = { showSchedule.value = it },
                    colors = CheckboxDefaults.colors(
                        checkedColor = NeonGreen,
                        uncheckedColor = NeonGreen,
                        checkmarkColor = DeepBlue
                    )
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

        }

        IconButton(onClick = onBack) {
            Icon(
                painter = painterResource(id = R.drawable.ic_close),
                contentDescription = "Close Settings",
                tint = NeonGreen,
                modifier = Modifier.size(48.dp)
            )
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

package llm.slop.spazradio.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PersonRemove
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import llm.slop.spazradio.ChatViewModel
import llm.slop.spazradio.R
import llm.slop.spazradio.RadioViewModel
import llm.slop.spazradio.ScheduleItem
import llm.slop.spazradio.ScheduleViewModel
import llm.slop.spazradio.ui.theme.NeonGreen

@Composable
fun SettingsContent(
    radioViewModel: RadioViewModel,
    chatViewModel: ChatViewModel
) {
    var showResetDialog by remember { mutableStateOf(false) }

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
                text = stringResource(R.string.chat_settings),
                style = MaterialTheme.typography.titleMedium,
                color = NeonGreen
            )
            Spacer(modifier = Modifier.height(16.dp))
            
            Button(
                onClick = { showResetDialog = true },
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.Red.copy(alpha = 0.6f),
                    contentColor = Color.White
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.PersonRemove, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.reset_username))
            }

            if (showResetDialog) {
                androidx.compose.material3.AlertDialog(
                    onDismissRequest = { showResetDialog = false },
                    title = { Text(stringResource(R.string.reset_dialog_title)) },
                    text = { Text(stringResource(R.string.reset_dialog_text)) },
                    confirmButton = {
                        Button(
                            onClick = {
                                chatViewModel.resetUsername()
                                showResetDialog = false
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
                        ) {
                            Text(stringResource(R.string.action_reset))
                        }
                    },
                    dismissButton = {
                        Button(onClick = { showResetDialog = false }) {
                            Text(stringResource(R.string.action_cancel))
                        }
                    },
                    containerColor = Color(0xFF1A1A1A),
                    titleContentColor = NeonGreen,
                    textContentColor = Color.White
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = stringResource(R.string.settings_placeholder),
                style = MaterialTheme.typography.bodySmall,
                color = NeonGreen.copy(alpha = 0.5f)
            )
        }
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
        ErrorContent(message = stringResource(R.string.error_archives), onRetry = { scheduleViewModel.loadSchedule() })
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
        // Note: item.datePart, startTime, endTime are assumed to be formatted by the ViewModel/Data layer.
        // If not, they should be localized here.
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

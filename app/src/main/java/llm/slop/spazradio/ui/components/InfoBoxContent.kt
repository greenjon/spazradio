package llm.slop.spazradio.ui.components

import android.content.Intent
import android.net.Uri
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.PersonRemove
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import llm.slop.spazradio.AppTheme
import llm.slop.spazradio.ChatViewModel
import llm.slop.spazradio.R
import llm.slop.spazradio.RadioViewModel
import llm.slop.spazradio.ScheduleItem
import llm.slop.spazradio.ScheduleViewModel
import llm.slop.spazradio.utils.LogCollector

@Composable
fun SettingsContent(
    radioViewModel: RadioViewModel,
    chatViewModel: ChatViewModel
) {
    var showResetDialog by remember { mutableStateOf(false) }
    val currentTheme by radioViewModel.appTheme.collectAsState()
    val context = LocalContext.current

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
        Column(modifier = Modifier.fillMaxWidth().weight(1f)) {
            // Theme Section
            Text(
                text = stringResource(R.string.theme_settings),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(8.dp))

            ThemeOption(
                label = stringResource(R.string.theme_neon),
                selected = currentTheme == AppTheme.NEON,
                onClick = { radioViewModel.setAppTheme(AppTheme.NEON) }
            )
            ThemeOption(
                label = stringResource(R.string.theme_light),
                selected = currentTheme == AppTheme.LIGHT,
                onClick = { radioViewModel.setAppTheme(AppTheme.LIGHT) }
            )
            ThemeOption(
                label = stringResource(R.string.theme_dark),
                selected = currentTheme == AppTheme.DARK,
                onClick = { radioViewModel.setAppTheme(AppTheme.DARK) }
            )
            ThemeOption(
                label = stringResource(R.string.theme_auto),
                selected = currentTheme == AppTheme.AUTO,
                onClick = { radioViewModel.setAppTheme(AppTheme.AUTO) }
            )

            Spacer(modifier = Modifier.height(24.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f))
            Spacer(modifier = Modifier.height(24.dp))

            // Debug & Logs Section
            Text(
                text = "Debug & Support",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(16.dp))
            
            Button(
                onClick = { LogCollector.shareLogs(context) },
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.8f),
                    contentColor = MaterialTheme.colorScheme.onSecondary
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.BugReport, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Send Debug Logs")
            }

            Spacer(modifier = Modifier.height(24.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f))
            Spacer(modifier = Modifier.height(24.dp))

            // Chat Section
            Text(
                text = stringResource(R.string.chat_settings),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(16.dp))
            
            Button(
                onClick = { showResetDialog = true },
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.8f),
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
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                        ) {
                            Text(stringResource(R.string.action_reset))
                        }
                    },
                    dismissButton = {
                        Button(onClick = { showResetDialog = false }) {
                            Text(stringResource(R.string.action_cancel))
                        }
                    },
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.primary,
                    textContentColor = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        // Privacy & Info Section
        val privacyInfo = stringResource(R.string.privacy_info_text, "")
        val linkText = stringResource(R.string.privacy_link_text)
        val annotatedString = buildAnnotatedString {
            val parts = privacyInfo.split("%s")
            append(parts[0])
            pushStringAnnotation(tag = "URL", annotation = "https://github.com/greenjon/spazradio")
            withStyle(style = SpanStyle(
                color = MaterialTheme.colorScheme.primary,
                textDecoration = TextDecoration.Underline
            )) {
                append(linkText)
            }
            pop()
            if (parts.size > 1) {
                append(parts[1])
            }
        }

        ClickableText(
            text = annotatedString,
            style = MaterialTheme.typography.bodySmall.copy(
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                fontSize = 10.sp
            ),
            modifier = Modifier.padding(top = 16.dp, bottom = 8.dp),
            onClick = { offset ->
                annotatedString.getStringAnnotations(tag = "URL", start = offset, end = offset)
                    .firstOrNull()?.let { annotation ->
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(annotation.item))
                        context.startActivity(intent)
                    }
            }
        )
    }
}

@Composable
fun ThemeOption(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            selected = selected,
            onClick = onClick,
            colors = RadioButtonDefaults.colors(
                selectedColor = MaterialTheme.colorScheme.primary,
                unselectedColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface
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
        Text(
            text = "${item.datePart} • ${item.startTime} - ${item.endTime}",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = item.showName,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

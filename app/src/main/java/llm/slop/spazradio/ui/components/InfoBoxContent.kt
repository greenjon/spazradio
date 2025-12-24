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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.ClickableText
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PersonRemove
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
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
    var showThemeMenu by remember { mutableStateOf(false) }
    
    val currentTheme by radioViewModel.appTheme.collectAsState()
    val autoPlayEnabled by radioViewModel.autoPlayEnabled.collectAsState()
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { /* Absorb clicks */ },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Theme Section (Collapsible)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showThemeMenu = !showThemeMenu }
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Palette, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(
                            text = stringResource(R.string.theme_settings),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = when(currentTheme) {
                                AppTheme.NEON -> stringResource(R.string.theme_neon)
                                AppTheme.LIGHT -> stringResource(R.string.theme_light)
                                AppTheme.DARK -> stringResource(R.string.theme_dark)
                                AppTheme.AUTO -> stringResource(R.string.theme_auto)
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                Text(
                    text = if (showThemeMenu) "CLOSE" else "CHANGE",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            if (showThemeMenu) {
                Column(modifier = Modifier.padding(start = 36.dp, top = 8.dp, bottom = 16.dp)) {
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
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))

            // Playback Section
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.PlayCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(
                            text = stringResource(R.string.playback_settings),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = stringResource(R.string.autoplay_radio),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                }
                Switch(
                    checked = autoPlayEnabled,
                    onCheckedChange = { radioViewModel.setAutoPlayEnabled(it) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = MaterialTheme.colorScheme.primary,
                        checkedTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                    )
                )
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))

            // Debug & Logs Section
            Text(
                text = "Debug & Support",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 8.dp)
            )
            Spacer(modifier = Modifier.height(12.dp))
            
            OutlinedButton(
                onClick = { LogCollector.shareLogs(context) },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.primary,
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 1f) // Forced opaque
                )
            ) {
                Icon(Icons.Default.BugReport, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Send Debug Logs")
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))

            // Chat Section
            Text(
                text = stringResource(R.string.chat_settings),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(12.dp))
            
            Button(
                onClick = { showResetDialog = true },
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
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
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        // Privacy & Info Section - Updated to avoid deprecated ClickableText
        val privacyInfo = stringResource(R.string.privacy_info_text, "")
        val linkText = stringResource(R.string.privacy_link_text)
        val annotatedString = buildAnnotatedString {
            val parts = privacyInfo.split("%s")
            append(parts[0])
            withLink(
                LinkAnnotation.Url(
                    url = "https://github.com/greenjon/spazradio",
                    styles = TextLinkStyles(
                        style = SpanStyle(
                            color = MaterialTheme.colorScheme.primary,
                            textDecoration = TextDecoration.Underline
                        )
                    )
                )
            ) {
                append(linkText)
            }
            if (parts.size > 1) {
                append(parts[1])
            }
        }

        Text(
            text = annotatedString,
            style = MaterialTheme.typography.bodySmall.copy(
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                fontSize = 10.sp
            ),
            modifier = Modifier.padding(top = 16.dp, bottom = 16.dp)
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

package llm.slop.spazradio.ui.components

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import llm.slop.spazradio.ArchiveUiState
import llm.slop.spazradio.ArchiveViewModel
import llm.slop.spazradio.R
import llm.slop.spazradio.RadioViewModel
import llm.slop.spazradio.data.ArchiveShow
import llm.slop.spazradio.ui.theme.NeonGreen

@Composable
fun ArchiveContent(
    archiveViewModel: ArchiveViewModel,
    radioViewModel: RadioViewModel
) {
    val uiState by archiveViewModel.uiState.collectAsState()

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ ->
        // Permission result handled by the system notification logic
    }

    LaunchedEffect(Unit) {
        archiveViewModel.fetchArchivesIfNeeded()
    }

    when (val state = uiState) {
        is ArchiveUiState.Loading -> {
            LoadingArchivesText()
        }
        is ArchiveUiState.Success -> {
            Column(modifier = Modifier.fillMaxSize()) {
                // Search Bar
                OutlinedTextField(
                    value = state.searchQuery,
                    onValueChange = { archiveViewModel.updateSearchQuery(it) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    placeholder = { Text(stringResource(R.string.search_archives), color = Color.Gray, fontSize = 14.sp) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = NeonGreen) },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = NeonGreen,
                        unfocusedBorderColor = NeonGreen.copy(alpha = 0.5f),
                        cursorColor = NeonGreen,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    )
                )

                LazyColumn(modifier = Modifier.weight(1f)) {
                    items(
                        items = state.filteredShows,
                        key = { show -> show.url }
                    ) { show ->
                        val isDownloaded = state.downloadedUrls.contains(show.url)
                        val isDownloading = state.downloadingUrls.contains(show.url)
                        
                        ArchiveShowRow(
                            show = show,
                            isDownloaded = isDownloaded,
                            isDownloading = isDownloading,
                            onPlay = {
                                val localFile = archiveViewModel.getLocalFileIfDownloaded(show)
                                if (localFile != null) {
                                    radioViewModel.playArchive(show.copy(url = "file://${localFile.absolutePath}"))
                                } else {
                                    radioViewModel.playArchive(show)
                                }
                            },
                            onDownload = {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                }
                                archiveViewModel.downloadArchive(show)
                            }
                        )
                    }
                }
            }
        }
        is ArchiveUiState.Error -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(text = state.message, color = Color.Red, modifier = Modifier.padding(16.dp))
                    Text(
                        text = stringResource(R.string.label_retry),
                        color = NeonGreen,
                        modifier = Modifier
                            .clickable { archiveViewModel.fetchArchives() }
                            .padding(8.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun LoadingArchivesText() {
    val infiniteTransition = rememberInfiniteTransition(label = "loadingPulsing")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = stringResource(R.string.loading_archives),
            color = NeonGreen.copy(alpha = alpha),
            style = MaterialTheme.typography.titleLarge
        )
    }
}

@Composable
fun ArchiveShowRow(
    show: ArchiveShow,
    isDownloaded: Boolean,
    isDownloading: Boolean,
    onPlay: () -> Unit,
    onDownload: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onPlay() }
            .padding(vertical = 8.dp, horizontal = 4.dp)
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.fillMaxWidth(0.7f)) {
                Text(
                    text = show.date,
                    style = MaterialTheme.typography.labelSmall,
                    color = NeonGreen
                )
                Text(
                    text = show.title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.White
                )
            }
            Row(
                modifier = Modifier.align(Alignment.CenterEnd),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onDownload,
                    modifier = Modifier.size(40.dp),
                    enabled = !isDownloaded && !isDownloading
                ) {
                    if (isDownloading) {
                        CircularProgressIndicator(
                            color = NeonGreen,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(20.dp)
                        )
                    } else {
                        Icon(
                            imageVector = if (isDownloaded) Icons.Default.CheckCircle else Icons.Default.Download,
                            contentDescription = if (isDownloaded) stringResource(R.string.label_downloaded) else stringResource(R.string.label_download),
                            tint = if (isDownloaded) NeonGreen else Color.Gray
                        )
                    }
                }
                Spacer(modifier = Modifier.width(4.dp))
                IconButton(
                    onClick = onPlay,
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = stringResource(R.string.label_play),
                        tint = NeonGreen
                    )
                }
            }
        }
    }
}

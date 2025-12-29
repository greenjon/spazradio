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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import llm.slop.spazradio.ArchiveUiState
import llm.slop.spazradio.ArchiveViewModel
import llm.slop.spazradio.R
import llm.slop.spazradio.RadioViewModel
import llm.slop.spazradio.data.ArchiveShow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArchiveContent(
    archiveViewModel: ArchiveViewModel,
    radioViewModel: RadioViewModel
) {
    val uiState by archiveViewModel.uiState.collectAsState()
    val searchQuery by archiveViewModel.searchQuery.collectAsState()
    val isRefreshing by archiveViewModel.isRefreshing.collectAsState()
    val keyboardController = LocalSoftwareKeyboardController.current

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ -> }

    LaunchedEffect(Unit) {
        archiveViewModel.fetchArchivesIfNeeded()
    }

    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = { archiveViewModel.fetchArchives() },
        modifier = Modifier.fillMaxSize()
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { archiveViewModel.updateSearchQuery(it) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                placeholder = { Text(stringResource(R.string.search_archives), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f), fontSize = 14.sp) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { 
                            archiveViewModel.updateSearchQuery("")
                            keyboardController?.hide()
                        }) {
                            Icon(Icons.Default.Close, contentDescription = "Clear Search", tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(
                    onSearch = { keyboardController?.hide() }
                ),
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                    cursorColor = MaterialTheme.colorScheme.primary,
                    focusedTextColor = MaterialTheme.colorScheme.onSurface,
                    unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                )
            )

            when (val state = uiState) {
                is ArchiveUiState.Loading -> {
                    LoadingArchivesText()
                }
                is ArchiveUiState.Success -> {
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
                is ArchiveUiState.EmptySearch -> {
                    EmptySearchState(query = searchQuery) {
                        archiveViewModel.updateSearchQuery("")
                        keyboardController?.hide()
                    }
                }
                is ArchiveUiState.Error -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(text = state.message, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp))
                            Button(onClick = { archiveViewModel.fetchArchives() }) {
                                Text(stringResource(R.string.label_retry))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun EmptySearchState(query: String, onClear: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
            Icon(
                Icons.Default.SearchOff,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.search_no_results, query),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(24.dp))
            Button(onClick = onClear) {
                Text(stringResource(R.string.action_clear_search))
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
            color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.graphicsLayer { this.alpha = alpha }
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
            .padding(vertical = 4.dp, horizontal = 4.dp)
    ) {
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.CenterStart
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = show.date,
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary
                )
                if (!show.duration.isNullOrBlank()) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "(${show.duration})",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                    )
                }
            }
            Row(
                modifier = Modifier.align(Alignment.CenterEnd),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isDownloading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                } else if (isDownloaded) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "Downloaded",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                } else {
                    IconButton(onClick = onDownload) {
                        Icon(
                            imageVector = Icons.Default.Download,
                            contentDescription = "Download",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                Spacer(modifier = Modifier.width(8.dp))
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "Play",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
        Text(
            text = show.title,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
        )
        Spacer(modifier = Modifier.height(8.dp))
        androidx.compose.material3.HorizontalDivider(
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
        )
    }
}

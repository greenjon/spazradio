package llm.slop.spazradio.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import llm.slop.spazradio.ArchiveUiState
import llm.slop.spazradio.ArchiveViewModel
import llm.slop.spazradio.RadioViewModel
import llm.slop.spazradio.data.ArchiveShow
import llm.slop.spazradio.ui.theme.NeonGreen

@Composable
fun ArchiveContent(
    archiveViewModel: ArchiveViewModel,
    radioViewModel: RadioViewModel
) {
    val uiState by archiveViewModel.uiState.collectAsState()

    LaunchedEffect(Unit) {
        archiveViewModel.fetchArchivesIfNeeded()
    }

    when (val state = uiState) {
        is ArchiveUiState.Loading -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = NeonGreen)
            }
        }
        is ArchiveUiState.Success -> {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(state.shows) { show ->
                    ArchiveShowRow(
                        show = show,
                        onPlay = { radioViewModel.playArchive(show) }
                    )
                }
            }
        }
        is ArchiveUiState.Error -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(text = state.message, color = Color.Red, modifier = Modifier.padding(16.dp))
                    Text(
                        text = "Retry",
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
fun ArchiveShowRow(
    show: ArchiveShow,
    onPlay: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onPlay() }
            .padding(vertical = 8.dp, horizontal = 4.dp)
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.fillMaxWidth(0.85f)) {
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
            IconButton(
                onClick = onPlay,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .size(40.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "Play",
                    tint = NeonGreen
                )
            }
        }
    }
}

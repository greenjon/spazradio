package llm.slop.spazradio.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import llm.slop.spazradio.ui.theme.LocalNeonTheme
import androidx.compose.ui.unit.dp
import llm.slop.spazradio.R

@Composable
fun InfoBox(
    title: String,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    /** Drops the title bar and most of the padding, for when a landscape keyboard leaves only a sliver. */
    compact: Boolean = false,
    content: @Composable () -> Unit
) {
    // Neon gets 50% transparent black; every other theme uses its (nearly opaque) surface color.
    val backgroundColor = if (LocalNeonTheme.current) {
        Color.Black.copy(alpha = 0.5f)
    } else {
        MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
    }

    Box(
        modifier = modifier
            .background(
                backgroundColor,
                RoundedCornerShape(16.dp)
            )
            .border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(16.dp))
            .clip(RoundedCornerShape(16.dp))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(if (compact) 4.dp else 16.dp)
        ) {
            if (!compact) Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.align(Alignment.CenterStart)
                )
                IconButton(
                    onClick = onClose,
                    modifier = Modifier.align(Alignment.CenterEnd).size(32.dp)
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_close),
                        contentDescription = stringResource(R.string.action_close),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
            
            Box(modifier = Modifier.weight(1f)) {
                content()
            }
        }
    }
}

@Composable
fun LoadingContent() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
fun ErrorContent(message: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(message, color = MaterialTheme.colorScheme.error)
        Spacer(Modifier.height(8.dp))
        Button(onClick = onRetry) {
            Text(stringResource(R.string.label_retry))
        }
    }
}

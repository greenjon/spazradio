package llm.slop.spazradio.ui.components

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.os.Build
import android.text.format.Formatter
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import llm.slop.spazradio.ArchiveViewModel
import llm.slop.spazradio.R
import llm.slop.spazradio.RadioViewModel
import llm.slop.spazradio.data.ArchiveShow

/**
 * Starts a show download the same way from the Archives list and the player header: asks for the
 * notification permission (Android 13+) so the progress shows, and on mobile data first asks whether
 * to go ahead, unless the listener said not to ask again. Returns the function to call with the show.
 */
@Composable
fun rememberShowDownloader(
    archiveViewModel: ArchiveViewModel,
    radioViewModel: RadioViewModel
): (ArchiveShow) -> Unit {
    val context = LocalContext.current
    val askMetered by radioViewModel.askMeteredDownload.collectAsState()
    var pending by remember { mutableStateOf<ArchiveShow?>(null) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ -> }

    // Android 7-9 can't save into the public Music folder without storage access. The download waits
    // for the answer; if it's refused the enqueue fails and the tracker reports the failure.
    var awaitingStorage by remember { mutableStateOf<ArchiveShow?>(null) }
    val storageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ ->
        awaitingStorage?.let { archiveViewModel.downloadArchive(it) }
        awaitingStorage = null
    }

    val start: (ArchiveShow) -> Unit = { show ->
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
        ) {
            awaitingStorage = show
            storageLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) {
                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
            archiveViewModel.downloadArchive(show)
        }
    }

    pending?.let { show ->
        MeteredDownloadDialog(
            show = show,
            archiveViewModel = archiveViewModel,
            onConfirm = { dontAskAgain ->
                pending = null
                if (dontAskAgain) radioViewModel.setAskMeteredDownload(false)
                start(show)
            },
            onDismiss = { pending = null }
        )
    }

    val latestAsk by rememberUpdatedState(askMetered)
    return remember(archiveViewModel) {
        { show ->
            if (latestAsk && isMetered(context)) pending = show else start(show)
        }
    }
}

private fun isMetered(context: Context): Boolean {
    val connectivity = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    return connectivity.isActiveNetworkMetered
}

@Composable
private fun MeteredDownloadDialog(
    show: ArchiveShow,
    archiveViewModel: ArchiveViewModel,
    onConfirm: (dontAskAgain: Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var size by remember(show.url) { mutableStateOf<Long?>(null) }
    var dontAskAgain by remember { mutableStateOf(false) }

    LaunchedEffect(show.url) {
        size = archiveViewModel.remoteSize(show.url)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.download_metered_title)) },
        text = {
            Column {
                Text(
                    size?.let { stringResource(R.string.download_metered_size, show.title, Formatter.formatShortFileSize(context, it)) }
                        ?: stringResource(R.string.download_metered_text, show.title)
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .padding(top = 8.dp)
                        .clickable { dontAskAgain = !dontAskAgain }
                ) {
                    Checkbox(checked = dontAskAgain, onCheckedChange = { dontAskAgain = it })
                    Text(
                        stringResource(R.string.download_metered_dont_ask),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(dontAskAgain) }) {
                Text(stringResource(R.string.label_download))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    )
}

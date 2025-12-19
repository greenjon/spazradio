package llm.slop.spazradio.ui.components

import android.text.util.Linkify
import android.widget.TextView
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.People
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.text.HtmlCompat
import llm.slop.spazradio.ChatViewModel
import llm.slop.spazradio.R
import llm.slop.spazradio.ui.theme.NeonGreen
import llm.slop.spazradio.ui.theme.Magenta
import java.text.DateFormat
import java.util.*

@Composable
fun ChatContent(
    viewModel: ChatViewModel,
    modifier: Modifier = Modifier
) {
    val username by viewModel.username
    val messages = viewModel.messages
    val onlineNames by viewModel.onlineNames.collectAsState()

    if (username.isEmpty()) {
        NicknameEntry(onJoin = { viewModel.setUsername(it) })
    } else {
        ChatLayout(
            messages = messages,
            onlineNames = onlineNames,
            onSendMessage = { viewModel.sendMessage(it) },
            modifier = modifier
        )
    }
}

@Composable
fun NicknameEntry(onJoin: (String) -> Unit) {
    var text by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = stringResource(R.string.chat_join),
            style = MaterialTheme.typography.headlineSmall,
            color = Color.White
        )
        Spacer(modifier = Modifier.height(16.dp))
        TextField(
            value = text,
            onValueChange = { text = it },
            placeholder = { Text(stringResource(R.string.chat_name_hint)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(
            onClick = { if (text.isNotBlank()) onJoin(text) },
            enabled = text.isNotBlank(),
            colors = ButtonDefaults.buttonColors(containerColor = NeonGreen)
        ) {
            Text(stringResource(R.string.label_chat), color = Color.Black)
        }
    }
}

@Composable
fun ChatLayout(
    messages: List<llm.slop.spazradio.data.ChatMessage>,
    onlineNames: List<String>,
    onSendMessage: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var messageText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    var isInitialLoad by remember { mutableStateOf(true) }

    // Automatic scrolling logic
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            if (isInitialLoad) {
                listState.scrollToItem(messages.size - 1)
                isInitialLoad = false
            } else {
                val layoutInfo = listState.layoutInfo
                val lastVisibleItemIndex = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                val totalItemsCount = layoutInfo.totalItemsCount
                if (lastVisibleItemIndex >= totalItemsCount - 3) {
                    listState.animateScrollToItem(messages.size - 1)
                }
            }
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        // Online users list header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.People,
                contentDescription = null,
                tint = Color(0xFFFFFF00),
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = if (onlineNames.isEmpty()) "..." else onlineNames.joinToString(", "),
                style = MaterialTheme.typography.labelSmall,
                color = Color(0xFFFFFF00),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }

        SelectionContainer(modifier = Modifier.weight(1f)) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(messages) { msg ->
                    ChatMessageItem(msg)
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextField(
                value = messageText,
                onValueChange = { messageText = it },
                placeholder = { Text(stringResource(R.string.chat_hint), color = NeonGreen.copy(alpha = 0.6f)) },
                modifier = Modifier
                    .weight(1f)
                    .border(1.dp, NeonGreen, RoundedCornerShape(12.dp)),
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.White.copy(alpha = 0.1f),
                    unfocusedContainerColor = Color.White.copy(alpha = 0.05f),
                    focusedTextColor = NeonGreen,
                    unfocusedTextColor = NeonGreen,
                    cursorColor = NeonGreen,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent
                )
            )
            IconButton(
                onClick = {
                    if (messageText.isNotBlank()) {
                        onSendMessage(messageText)
                        messageText = ""
                    }
                },
                enabled = messageText.isNotBlank()
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Send,
                    contentDescription = "Send",
                    tint = NeonGreen
                )
            }
        }
    }
}

@Composable
fun ChatMessageItem(msg: llm.slop.spazradio.data.ChatMessage) {
    val dateTimeStr = remember(msg.timeReceived) {
        if (msg.timeReceived > 0) {
            val date = Date(msg.timeReceived * 1000L)
            val dateFormat = DateFormat.getDateTimeInstance(
                DateFormat.SHORT, 
                DateFormat.SHORT, 
                Locale.getDefault()
            )
            dateFormat.format(date)
        } else {
            ""
        }
    }

    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom
        ) {
            Text(
                text = msg.user,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = Magenta
                )
            )
            if (dateTimeStr.isNotEmpty()) {
                Text(
                    text = dateTimeStr,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.4f)
                )
            }
        }
        AndroidView(
            factory = { context ->
                TextView(context).apply {
                    setTextColor(android.graphics.Color.parseColor("#39FF14")) // Neon Green
                    setTextSize(14f)
                    autoLinkMask = Linkify.WEB_URLS
                    setLinkTextColor(android.graphics.Color.parseColor("#39FF14"))
                }
            },
            update = { textView ->
                val decoded = HtmlCompat.fromHtml(msg.message, HtmlCompat.FROM_HTML_MODE_LEGACY)
                textView.text = decoded
            },
            modifier = Modifier.fillMaxWidth()
        )
    }
}

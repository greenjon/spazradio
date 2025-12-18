package llm.slop.spazradio.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.text.HtmlCompat
import llm.slop.spazradio.ChatViewModel
import llm.slop.spazradio.R
import llm.slop.spazradio.ui.theme.Cyan
import llm.slop.spazradio.ui.theme.Magenta

@Composable
fun ChatContent(
    viewModel: ChatViewModel,
    modifier: Modifier = Modifier
) {
    val username by viewModel.username
    val messages = viewModel.messages
    val onlineCount by viewModel.onlineCount.collectAsState()

    if (username.isEmpty()) {
        NicknameEntry(onJoin = { viewModel.setUsername(it) })
    } else {
        ChatLayout(
            messages = messages,
            onlineCount = onlineCount,
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
            colors = ButtonDefaults.buttonColors(containerColor = Cyan)
        ) {
            Text(stringResource(R.string.label_chat), color = Color.Black)
        }
    }
}

@Composable
fun ChatLayout(
    messages: List<llm.slop.spazradio.data.ChatMessage>,
    onlineCount: Int,
    onSendMessage: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var messageText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        // Online count header
        Text(
            text = stringResource(R.string.chat_online_count, onlineCount),
            style = MaterialTheme.typography.labelSmall,
            color = Cyan,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
        )

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
                placeholder = { Text(stringResource(R.string.chat_hint)) },
                modifier = Modifier.weight(1f),
                singleLine = true,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.White.copy(alpha = 0.1f),
                    unfocusedContainerColor = Color.White.copy(alpha = 0.05f)
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
                    tint = Cyan
                )
            }
        }
    }
}

@Composable
fun ChatMessageItem(msg: llm.slop.spazradio.data.ChatMessage) {
    val decodedMessage = remember(msg.message) {
        HtmlCompat.fromHtml(msg.message, HtmlCompat.FROM_HTML_MODE_LEGACY).toString()
    }

    Column {
        Text(
            text = msg.user,
            style = MaterialTheme.typography.labelMedium.copy(
                fontWeight = FontWeight.Bold,
                color = Magenta
            )
        )
        Text(
            text = decodedMessage,
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White
        )
    }
}

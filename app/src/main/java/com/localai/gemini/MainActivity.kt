package com.localai.gemini

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch

data class ChatMessage(
    val id: Long = System.currentTimeMillis(),
    val content: String,
    val isUser: Boolean,
    val isStreaming: Boolean = false
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            GeminiLocalTheme {
                GeminiChatScreen()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GeminiChatScreen() {
    var messages by remember { mutableStateOf(listOf<ChatMessage>()) }
    var inputText by remember { mutableStateOf("") }
    var selectedModelUri by remember { mutableStateOf<Uri?>(null) }
    var isLiveVoiceOpen by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) selectedModelUri = uri
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = "Gemini",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (selectedModelUri != null) "Local Model Loaded" else "Gemini Local",
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { filePicker.launch(arrayOf("*/*")) }) {
                        Icon(Icons.Default.Folder, contentDescription = "Load GGUF Model")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        bottomBar = {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { /* Image picker */ }) {
                        Icon(Icons.Default.Add, contentDescription = "Add Image")
                    }
                    TextField(
                        value = inputText,
                        onValueChange = { inputText = it },
                        placeholder = { Text("Ask Gemini Local...") },
                        modifier = Modifier.weight(1f),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent
                        ),
                        maxLines = 4
                    )
                    if (inputText.isNotBlank()) {
                        IconButton(onClick = {
                            val userText = inputText
                            inputText = ""
                            messages = messages + ChatMessage(content = userText, isUser = true)

                            scope.launch {
                                val botMsgId = System.currentTimeMillis()
                                messages = messages + ChatMessage(id = botMsgId, content = "", isUser = false, isStreaming = true)

                                simulateLocalStream(userText).collect { token ->
                                    messages = messages.map { msg ->
                                        if (msg.id == botMsgId) {
                                            msg.copy(content = msg.content + token)
                                        } else msg
                                    }
                                }
                            }
                        }) {
                            Icon(Icons.Default.ArrowUpward, contentDescription = "Send", tint = MaterialTheme.colorScheme.primary)
                        }
                    } else {
                        IconButton(onClick = { isLiveVoiceOpen = true }) {
                            Icon(Icons.Default.Mic, contentDescription = "Live Voice", tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        }
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            if (messages.isEmpty()) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(24.dp),
                    verticalArrangement = Arrangement.Center
                ) {
                    Text("Hello,", style = MaterialTheme.typography.headlineLarge.copy(fontSize = 36.sp), color = MaterialTheme.colorScheme.primary)
                    Text("Where would you like to start?", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(24.dp))
                    AssistChip(
                        onClick = { inputText = "Kya tum Hinglish me baat kar sakte ho?" },
                        label = { Text("Speak in Hinglish / Hindi") }
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    items(messages) { msg ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = if (msg.isUser) Arrangement.End else Arrangement.Start
                        ) {
                            if (!msg.isUser) {
                                Icon(
                                    imageVector = Icons.Default.Star,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp).padding(top = 2.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                            }
                            Surface(
                                shape = RoundedCornerShape(18.dp),
                                color = if (msg.isUser) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent,
                                modifier = Modifier.widthIn(max = 300.dp)
                            ) {
                                Text(
                                    text = msg.content + if (msg.isStreaming) " ▍" else "",
                                    style = MaterialTheme.typography.bodyLarge,
                                    modifier = Modifier.padding(if (msg.isUser) 12.dp else 0.dp),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            }

            if (isLiveVoiceOpen) {
                AlertDialog(
                    onDismissRequest = { isLiveVoiceOpen = false },
                    title = { Text("Gemini Live") },
                    text = {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.fillMaxWidth().padding(16.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(90.dp)
                                    .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Mic, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(40.dp))
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                            Text("Listening locally offline...", style = MaterialTheme.typography.bodyMedium)
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = { isLiveVoiceOpen = false }) { Text("End") }
                    }
                )
            }
        }
    }
}

fun simulateLocalStream(prompt: String) = flow {
    val sampleTokens = ("Namaste! Mai aapke phone ke physical RAM par locally run ho raha hu. " +
            "No internet, no paid API needed. Kaise madad karu aapki?").split(" ")
    for (token in sampleTokens) {
        delay(120)
        emit("$token ")
    }
}

@Composable
fun GeminiLocalTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Color(0xFF1B6EF3),
            surface = Color(0xFFFFFFFF),
            surfaceVariant = Color(0xFFF0F4F9)
        ),
        content = content
    )
}

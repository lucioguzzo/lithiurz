package com.lucio.guzzobot.ui.chat

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.AddComment
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lucio.guzzobot.R
import com.lucio.guzzobot.data.local.ConversationEntity
import com.lucio.guzzobot.data.model.AttachmentKind
import com.lucio.guzzobot.data.model.Roles
import com.lucio.guzzobot.ui.components.Spark
import kotlinx.coroutines.launch
import java.io.File
import java.util.Calendar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    viewModel: ChatViewModel,
    onOpenSettings: () -> Unit,
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val conversations by viewModel.conversations.collectAsStateWithLifecycle()
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val streaming by viewModel.streaming.collectAsStateWithLifecycle()
    val input by viewModel.input.collectAsStateWithLifecycle()
    val currentId by viewModel.conversationId.collectAsStateWithLifecycle()
    val ready by viewModel.ready.collectAsStateWithLifecycle()
    val pending by viewModel.pending.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val recording by viewModel.recording.collectAsStateWithLifecycle()
    val abilities by viewModel.abilities.collectAsStateWithLifecycle()
    val playing by viewModel.audioPlayer.playing.collectAsStateWithLifecycle()
    val speaking by viewModel.speaking.collectAsStateWithLifecycle()

    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    val listState = rememberLazyListState()

    var showModelSheet by remember { mutableStateOf(false) }
    var showAttachSheet by remember { mutableStateOf(false) }
    var cameraFile by remember { mutableStateOf<File?>(null) }
    var renaming by remember { mutableStateOf<ConversationEntity?>(null) }

    LaunchedEffect(Unit) {
        viewModel.notices.collect { snackbarHostState.showSnackbar(it) }
    }

    val photoPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri -> uri?.let { viewModel.attach(it, AttachmentKind.IMAGE) } }

    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { taken ->
        val file = cameraFile
        cameraFile = null
        if (file != null) {
            if (taken) viewModel.attachPhotoFile(file) else file.delete()
        }
    }

    val documentPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> uri?.let { viewModel.attach(it, AttachmentKind.DOCUMENT) } }

    val audioPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> uri?.let { viewModel.attach(it, AttachmentKind.AUDIO) } }

    val videoPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> uri?.let { viewModel.attach(it, AttachmentKind.VIDEO) } }

    val micPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            viewModel.startRecording()
        } else {
            scope.launch { snackbarHostState.showSnackbar(context.getString(R.string.mic_denied)) }
        }
    }

    fun openVideo(attachment: com.lucio.guzzobot.data.model.Attachment) {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(viewModel.shareUri(attachment), attachment.mime)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        runCatching { context.startActivity(intent) }.onFailure {
            scope.launch { snackbarHostState.showSnackbar(context.getString(R.string.open_video)) }
        }
    }

    fun record() {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        if (granted) viewModel.startRecording() else micPermission.launch(Manifest.permission.RECORD_AUDIO)
    }

    val itemCount = messages.size + if (streaming != null) 1 else 0
    val nearBottom by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            info.totalItemsCount == 0 || last >= info.totalItemsCount - 2
        }
    }
    LaunchedEffect(messages.size) {
        if (itemCount > 0) listState.animateScrollToItem(itemCount - 1)
    }
    LaunchedEffect(streaming?.content?.length, streaming?.reasoning?.length) {
        if (streaming != null && nearBottom && itemCount > 0) listState.scrollToItem(itemCount - 1)
    }

    fun share(text: String) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        context.startActivity(Intent.createChooser(intent, null))
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                drawerShape = RoundedCornerShape(topEnd = 20.dp, bottomEnd = 20.dp),
            ) {
                ChatDrawer(
                    conversations = conversations,
                    currentId = currentId,
                    onNewChat = {
                        viewModel.newChat()
                        scope.launch { drawerState.close() }
                    },
                    onOpen = {
                        viewModel.openConversation(it)
                        scope.launch { drawerState.close() }
                    },
                    onRename = { renaming = it },
                    onDelete = { viewModel.deleteConversation(it.id) },
                    onShare = { conversation ->
                        viewModel.conversationAsText(conversation.id) { share(it) }
                    },
                    onOpenSettings = {
                        scope.launch { drawerState.close() }
                        onOpenSettings()
                    },
                )
            }
        },
    ) {
        Scaffold(
            modifier = Modifier.imePadding(),
            containerColor = MaterialTheme.colorScheme.background,
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background,
                    ),
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(
                                imageVector = Icons.Outlined.Menu,
                                contentDescription = stringResource(R.string.menu),
                                tint = MaterialTheme.colorScheme.onBackground,
                            )
                        }
                    },
                    title = {
                        ModelChip(
                            model = settings.activeModel,
                            onClick = { showModelSheet = true },
                        )
                    },
                    actions = {
                        IconButton(onClick = viewModel::newChat) {
                            Icon(
                                imageVector = Icons.Outlined.AddComment,
                                contentDescription = stringResource(R.string.new_chat),
                                tint = MaterialTheme.colorScheme.onBackground,
                            )
                        }
                    },
                )
            },
            bottomBar = {
                Composer(
                    value = input,
                    onValueChange = viewModel::setInput,
                    onSend = viewModel::send,
                    onStop = viewModel::stop,
                    streaming = streaming != null,
                    ready = ready,
                    onOpenSettings = onOpenSettings,
                    pending = pending,
                    onRemoveAttachment = viewModel::removeAttachment,
                    onAttach = { showAttachSheet = true },
                    onRecord = ::record,
                    recording = recording,
                    onStopRecording = viewModel::stopRecording,
                    onCancelRecording = viewModel::cancelRecording,
                    busy = busy,
                )
            },
        ) { padding ->
            Box(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize(),
            ) {
                if (messages.isEmpty() && streaming == null) {
                    EmptyState(onSuggestion = viewModel::setInput)
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(
                            start = 16.dp,
                            end = 16.dp,
                            top = 8.dp,
                            bottom = 24.dp,
                        ),
                        verticalArrangement = Arrangement.spacedBy(18.dp),
                    ) {
                        items(messages, key = { it.id }) { message ->
                            if (message.role == Roles.USER) {
                                UserMessage(
                                    message = message,
                                    playingPath = playing,
                                    onCopy = { clipboard.setText(AnnotatedString(message.content)) },
                                    onEdit = { viewModel.editMessage(message) },
                                    onPlay = viewModel::togglePlayback,
                                    onShare = { shareFile(context, viewModel.shareUri(it), it.mime) },
                                    onOpenVideo = ::openVideo,
                                )
                            } else {
                                AssistantMessage(
                                    content = message.content,
                                    reasoning = message.reasoning,
                                    isError = message.isError,
                                    showReasoning = settings.showReasoning,
                                    streaming = false,
                                    showActions = streaming == null && message.id == messages.last().id,
                                    onCopy = { clipboard.setText(AnnotatedString(message.content)) },
                                    onRetry = viewModel::regenerate,
                                    attachments = message.attachments,
                                    playingPath = playing,
                                    onPlay = viewModel::togglePlayback,
                                    onShare = { shareFile(context, viewModel.shareUri(it), it.mime) },
                                    onOpenVideo = ::openVideo,
                                    onSpeak = {
                                        if (speaking) viewModel.stopSpeaking() else viewModel.speak(message)
                                    },
                                    speaking = speaking,
                                )
                            }
                        }
                        streaming?.let { current ->
                            item(key = "streaming") {
                                AssistantMessage(
                                    content = current.content,
                                    reasoning = current.reasoning,
                                    isError = false,
                                    showReasoning = settings.showReasoning,
                                    streaming = true,
                                    showActions = false,
                                    onCopy = {},
                                    onRetry = {},
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAttachSheet) {
        AttachmentSheet(
            canGenerateImages = abilities.canGenerateImages,
            onPhoto = {
                showAttachSheet = false
                photoPicker.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                )
            },
            onCamera = {
                showAttachSheet = false
                val (file, uri) = viewModel.cameraTarget()
                cameraFile = file
                camera.launch(uri)
            },
            onDocument = {
                showAttachSheet = false
                documentPicker.launch(
                    arrayOf(
                        "application/pdf",
                        "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                        "application/vnd.openxmlformats-officedocument.presentationml.presentation",
                        "text/*",
                        "application/json",
                        "application/xml",
                    ),
                )
            },
            onVideo = {
                showAttachSheet = false
                videoPicker.launch(arrayOf("video/*"))
            },
            onAudio = {
                showAttachSheet = false
                audioPicker.launch(arrayOf("audio/*"))
            },
            onRecord = {
                showAttachSheet = false
                record()
            },
            onGenerateImage = {
                showAttachSheet = false
                viewModel.generateImage()
            },
            onDismiss = { showAttachSheet = false },
        )
    }

    if (showModelSheet) {
        ModelSheet(
            providers = settings.providers,
            activeProviderId = settings.activeProviderId,
            activeModel = settings.activeModel,
            onPick = { providerId, model ->
                viewModel.selectModel(providerId, model)
                showModelSheet = false
            },
            onOpenSettings = {
                showModelSheet = false
                onOpenSettings()
            },
            onDismiss = { showModelSheet = false },
        )
    }

    renaming?.let { conversation ->
        RenameDialog(
            initial = conversation.title,
            onConfirm = { title ->
                viewModel.renameConversation(conversation.id, title)
                renaming = null
            },
            onDismiss = { renaming = null },
        )
    }
}

@Composable
private fun ModelChip(model: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = model,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 220.dp),
        )
        Icon(
            imageVector = Icons.Outlined.ExpandMore,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
        )
    }
}

@Composable
private fun EmptyState(onSuggestion: (String) -> Unit) {
    val greeting = remember {
        when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
            in 5..12 -> R.string.greeting_morning
            in 13..17 -> R.string.greeting_afternoon
            in 18..22 -> R.string.greeting_evening
            else -> R.string.greeting_night
        }
    }
    val suggestions = listOf(R.string.suggestion_1, R.string.suggestion_2, R.string.suggestion_3)
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 28.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spark(size = 34.dp)
        Text(
            text = stringResource(greeting),
            style = MaterialTheme.typography.displaySmall,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 18.dp),
        )
        Text(
            text = stringResource(R.string.empty_subtitle),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 6.dp, bottom = 28.dp),
        )
        suggestions.forEach { resource ->
            val text = stringResource(resource)
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(14.dp))
                    .clickable { onSuggestion(text) }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            )
        }
    }
}

@Composable
private fun RenameDialog(
    initial: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var value by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.rename)) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(value) }) { Text(stringResource(R.string.save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}

private fun shareFile(context: android.content.Context, uri: android.net.Uri, mime: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = mime
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, null))
}

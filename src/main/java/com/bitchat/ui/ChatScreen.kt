@file:OptIn(ExperimentalMaterial3Api::class)

package com.bitchat.ui
import android.Manifest
import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.EaseInCubic
import androidx.compose.animation.core.EaseInOutCubic
import androidx.compose.animation.core.EaseInOutSine
import androidx.compose.animation.core.EaseOutCubic
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.ScrollableDefaults
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.TextSnippet
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PersonRemove
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SignalWifiOff
import androidx.compose.material.icons.filled.Slideshow
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import coil.compose.AsyncImage
import com.bitchat.model.Message
import com.bitchat.model.MessageStatus
import com.bitchat.model.MessageType
import com.bitchat.util.AudioRecorder
import com.bitchat.viewmodel.ChatViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.random.Random
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ChatScreen(
    viewModel: ChatViewModel,
    deviceId: String,
    deviceName: String,
    onNavigateUp: () -> Unit
) {
    val TAG = "ChatScreen"

    // Input and UI state
    var input by remember { mutableStateOf(TextFieldValue("")) }
    var showAttachmentPicker by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val haptic = LocalHapticFeedback.current
    val context = LocalContext.current
    var isSelectionMode by remember { mutableStateOf(false) }

    // Connection and sync state
    var isInitialized by remember { mutableStateOf(false) }
    var lastConnectionCheck by remember { mutableLongStateOf(0L) }
    var showReconnecting by remember { mutableStateOf(false) }

    // Typing state
    var typingJob by remember { mutableStateOf<Job?>(null) }
    var lastTypingTime by remember { mutableLongStateOf(0L) }

    // Observe ViewModel state with proper device filtering
    val currentDeviceId by viewModel.currentDeviceId.collectAsState()
    val currentDeviceName by viewModel.currentDeviceName.collectAsState()
    val allConnectedDevices by viewModel.connectedDevices.collectAsState()
    val connectionStatus by viewModel.connectionStatus.collectAsState()
    val allMessages by viewModel.allMessages.collectAsState()

    // Enhanced Search States
    val isSearchMode by viewModel.isSearchMode.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val searchResults by viewModel.searchResults.collectAsState()
    val currentSearchIndex by viewModel.currentSearchIndex.collectAsState()
    val highlightedMessageId by viewModel.highlightedMessageId.collectAsState()

    // Device-specific states with safety checks
    val isCorrectDevice = currentDeviceId == deviceId
    val isDeviceConnected = remember(deviceId, allConnectedDevices) {
        allConnectedDevices.contains(deviceId)
    }

    // Get device messages - use all messages, not filtered for search
    val deviceMessages = remember(allMessages, deviceId, isCorrectDevice) {
        if (isCorrectDevice) {
            allMessages[deviceId] ?: emptyList()
        } else {
            emptyList()
        }
    }

    // Message selection and reply state
    var selectedMessages by remember { mutableStateOf(setOf<String>()) }
    var showCopyButton by remember { mutableStateOf(false) }
    var replyToMessage by remember { mutableStateOf<Message?>(null) }

    // Typing indicator - only for current device
    val isFriendTyping by viewModel.isFriendTyping.collectAsState()
    val deviceTyping = remember(isFriendTyping, isCorrectDevice) {
        isFriendTyping && isCorrectDevice
    }

    // Update showCopyButton based on selection
    LaunchedEffect(selectedMessages) {
        showCopyButton = selectedMessages.isNotEmpty()
    }

    // Auto-scroll to highlighted search result
    LaunchedEffect(highlightedMessageId) {
        highlightedMessageId?.let { messageId ->
            val messageIndex = deviceMessages.indexOfFirst { it.id == messageId }
            if (messageIndex != -1) {
                val reversedIndex = deviceMessages.size - messageIndex - 1

                Log.d("ChatScreen", "Scrolling to message: $messageId, messageIndex: $messageIndex, reversedIndex: $reversedIndex")

                try {
                    listState.animateScrollToItem(
                        index = reversedIndex,
                        scrollOffset = -100
                    )
                } catch (e: Exception) {
                    Log.e("ChatScreen", "Error scrolling to search result", e)
                    try {
                        listState.scrollToItem(reversedIndex)
                    } catch (fallbackError: Exception) {
                        Log.e("ChatScreen", "Fallback scroll also failed", fallbackError)
                    }
                }
            } else {
                Log.w("ChatScreen", "Message with ID $messageId not found in deviceMessages")
            }
        }
    }

    // Initialize device context
    LaunchedEffect(deviceId, deviceName) {
        Log.d(TAG, "=== ChatScreen LaunchedEffect: Setting device context ===")
        Log.d(TAG, "Device: $deviceName ($deviceId)")

        try {
            viewModel.setCurrentDevice(deviceId, deviceName)
            val isConnected = viewModel.isConnectedToDevice(deviceId)
            Log.d(TAG, "Initial connection check: $isConnected")

            if (!isConnected) {
                Log.d(TAG, "Device not connected, attempting connection...")
                showReconnecting = true
                viewModel.connectToDeviceByAddress(deviceId)
                delay(2000L)
                val stillConnected = viewModel.isConnectedToDevice(deviceId)
                Log.d(TAG, "Connection attempt result: $stillConnected")
                showReconnecting = false
            }

            isInitialized = true
            Log.d(TAG, "=== Device context initialization complete ===")
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing device context", e)
            showReconnecting = false
            isInitialized = true
        }
    }

    // Enhanced Back Handler with search support
    BackHandler {
        when {
            isSelectionMode -> {
                selectedMessages = emptySet()
                isSelectionMode = false
            }
            isSearchMode -> {
                viewModel.exitSearchMode()
            }
            selectedMessages.isNotEmpty() -> {
                selectedMessages = emptySet()
            }
            replyToMessage != null -> {
                replyToMessage = null
            }
            else -> {
                typingJob?.cancel()
                if (lastTypingTime > 0L) {
                    viewModel.onStoppedTyping()
                }
                onNavigateUp()
            }
        }
    }

    // Connection health monitoring
    LaunchedEffect(deviceId, allConnectedDevices) {
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastConnectionCheck > 5000L) {
            lastConnectionCheck = currentTime
            val connected = allConnectedDevices.contains(deviceId)
            Log.d(TAG, "Connection health check - Device: $deviceId, Connected: $connected")

            if (!connected && isInitialized) {
                Log.d(TAG, "Device disconnected, attempting reconnection...")
                showReconnecting = true
                viewModel.connectToDeviceByAddress(deviceId)
                delay(3000L)
                showReconnecting = false
            }
        }
    }

    // Voice Recording State
    val audioRecorder = remember { AudioRecorder(context) }
    var isRecording by remember { mutableStateOf(false) }
    var recordingDuration by remember { mutableStateOf(0) }
    var currentAmplitude by remember { mutableStateOf(0) }

    // Handle typing with device context validation
    LaunchedEffect(input.text, isCorrectDevice, isDeviceConnected) {
        typingJob?.cancel()

        if (input.text.isNotEmpty() && isCorrectDevice && isDeviceConnected) {
            val currentTime = System.currentTimeMillis()
            viewModel.onTyping()
            lastTypingTime = currentTime

            typingJob = launch {
                while (input.text.isNotEmpty() && isDeviceConnected) {
                    delay(2500)
                    if (input.text.isNotEmpty() && isDeviceConnected) {
                        viewModel.onTyping()
                        lastTypingTime = System.currentTimeMillis()
                    }
                }
            }
        } else if (lastTypingTime > 0L) {
            viewModel.onStoppedTyping()
            lastTypingTime = 0L
        }
    }

    // Auto-scroll for new messages with improved logic
    LaunchedEffect(deviceMessages.size, deviceTyping) {
        if (isInitialized && (deviceMessages.isNotEmpty() || deviceTyping) && !isSearchMode) {
            delay(100)
            try {
                if (listState.firstVisibleItemIndex <= 3) {
                    listState.animateScrollToItem(index = 0, scrollOffset = 0)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error auto-scrolling", e)
            }
        }
    }

    // Audio recording state management
    LaunchedEffect(isRecording) {
        if (isRecording) {
            while (isRecording) {
                delay(100)
                recordingDuration += 100
                currentAmplitude = audioRecorder.getMaxAmplitude()
            }
        } else {
            recordingDuration = 0
            currentAmplitude = 0
        }
    }

    // File selection launchers
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted && isDeviceConnected) {
            val file = audioRecorder.startRecording()
            if (file != null) {
                isRecording = true
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            }
        }
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            if (isCorrectDevice && isDeviceConnected) {
                viewModel.sendFile(it, "image")
                showAttachmentPicker = false
            }
        }
    }

    val documentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            if (isCorrectDevice && isDeviceConnected) {
                viewModel.sendFile(it, "document")
                showAttachmentPicker = false
            }
        }
    }

    // Show loading screen during initialization
    if (!isInitialized) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                CircularProgressIndicator()
                Text(
                    text = "Connecting to $deviceName...",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }
        }
        return
    }

    // Show error if device context is incorrect
    if (!isCorrectDevice) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            item {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Default.Error,
                            contentDescription = "Error",
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Device Context Error",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Text(
                            text = "Expected: $deviceName ($deviceId)\nCurrent: ${currentDeviceName ?: "None"} (${currentDeviceId ?: "None"})",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = {
                                viewModel.setCurrentDevice(deviceId, deviceName)
                            }
                        ) {
                            Text("Fix Context")
                        }
                    }
                }
            }
        }
        return
    }

    Scaffold(
        topBar = {
            PremiumSearchTopAppBar(
                deviceName = deviceName,
                connectionStatus = connectionStatus,
                isFriendTyping = deviceTyping,
                isConnected = isDeviceConnected,
                isReconnecting = showReconnecting,
                isSearchMode = isSearchMode,
                searchQuery = searchQuery,
                searchResults = searchResults,
                currentSearchIndex = currentSearchIndex,
                onSearchQueryChange = { viewModel.updateSearchQuery(it) },
                onSearchToggle = {
                    if (isSearchMode) {
                        viewModel.exitSearchMode()
                    } else {
                        viewModel.toggleSearchMode()
                    }
                },
                onSearchNext = { viewModel.searchNext() },
                onSearchPrevious = { viewModel.searchPrevious() },
                onNavigateUp = {
                    when {
                        isSelectionMode -> {
                            selectedMessages = emptySet()
                            isSelectionMode = false
                        }
                        isSearchMode -> {
                            viewModel.exitSearchMode()
                        }
                        selectedMessages.isNotEmpty() -> {
                            selectedMessages = emptySet()
                        }
                        else -> {
                            typingJob?.cancel()
                            if (lastTypingTime > 0L) {
                                viewModel.onStoppedTyping()
                            }
                            onNavigateUp()
                        }
                    }
                },
                onReconnectClick = {
                    if (!isDeviceConnected) {
                        showReconnecting = true
                        coroutineScope.launch {
                            viewModel.connectToDeviceByAddress(deviceId)
                            delay(3000L)
                            showReconnecting = false
                        }
                    }
                },
                // Selection mode parameters
                isSelectionMode = isSelectionMode,
                selectedCount = selectedMessages.size,
                onCopySelected = {
                    // Copy only message content, no timestamps
                    val selectedMessagesText = deviceMessages
                        .filter { selectedMessages.contains(it.id) }
                        .sortedBy { it.timestamp }
                        .joinToString("\n") { message ->
                            when (message.type) {
                                MessageType.TEXT -> message.content
                                MessageType.IMAGE -> "📷 Photo"
                                MessageType.DOCUMENT -> "📄 ${message.fileName ?: "Document"}"
                                MessageType.AUDIO -> "🎵 Voice message"
                            }
                        }

                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("Messages", selectedMessagesText))

                    selectedMessages = emptySet()
                    isSelectionMode = false
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                },
                // FIXED: Pass proper delete callbacks that handle state management
                onDeleteForMe = {
                    viewModel.deleteMessagesForMe(selectedMessages)
                    selectedMessages = emptySet()
                    isSelectionMode = false
                },
                viewModel = viewModel,
                selectedMessages = selectedMessages
            )
        },
        bottomBar = {
            Box {
                Column(
                    modifier = Modifier
                        .background(MaterialTheme.colorScheme.surface)
                        .zIndex(1f)
                ) {
                    // Enhanced Reply Preview
                    PremiumReplyPreview(
                        replyToMessage = replyToMessage,
                        deviceName = deviceName,
                        onDismiss = { replyToMessage = null }
                    )

                    // Attachment picker (hide in search mode)
                    AnimatedVisibility(
                        visible = showAttachmentPicker && !isRecording && isDeviceConnected && !isSearchMode,
                        enter = slideInVertically(
                            initialOffsetY = { it / 2 },
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                stiffness = Spring.StiffnessMedium
                            )
                        ) + fadeIn(
                            animationSpec = tween(300, easing = EaseOutCubic)
                        ),
                        exit = slideOutVertically(
                            targetOffsetY = { it / 2 },
                            animationSpec = tween(200, easing = EaseInCubic)
                        ) + fadeOut(
                            animationSpec = tween(200, easing = EaseInCubic)
                        )
                    ) {
                        PremiumAttachmentPicker(
                            onGalleryClick = {
                                galleryLauncher.launch("image/*")
                                showAttachmentPicker = false
                            },
                            onDocumentClick = {
                                documentLauncher.launch(arrayOf("*/*"))
                                showAttachmentPicker = false
                            },
                            onCameraClick = {
                                showAttachmentPicker = false
                            }
                        )
                    }

                    // Chat input area - hide in search mode
                    AnimatedVisibility(
                        visible = !isSearchMode,
                        enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                        exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
                    ) {
                        PremiumChatInputArea(
                            input = input,
                            onInputChange = { newInput -> input = newInput },
                            onSendClick = {
                                if (input.text.isNotBlank()) {
                                    typingJob?.cancel()
                                    viewModel.onStoppedTyping()
                                    lastTypingTime = 0L

                                    Log.d(TAG, "Sending message to device: $deviceId (Connected: $isDeviceConnected)")

                                    // Always allow sending - the ViewModel should handle queuing when disconnected
                                    if (replyToMessage != null) {
                                        viewModel.sendReplyMessage(input.text.trim(), replyToMessage!!)
                                        replyToMessage = null
                                    } else {
                                        viewModel.sendMessage(input.text.trim())
                                    }

                                    input = TextFieldValue("")
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)

                                    // Show a toast or snackbar when message is queued due to disconnection
                                    if (!isDeviceConnected) {
                                        // You can add a Toast or Snackbar here if needed:
                                        // Toast.makeText(context, "Message queued - will send when reconnected", Toast.LENGTH_SHORT).show()
                                    }
                                } else if (!isDeviceConnected) {
                                    Log.w(TAG, "Cannot send message - device not connected")
                                }
                            },
                            onAttachmentClick = {
                                if (isDeviceConnected) {
                                    showAttachmentPicker = !showAttachmentPicker
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                }
                            },
                            onTextFieldFocus = { showAttachmentPicker = false },
                            isRecording = isRecording,
                            isConnected = isDeviceConnected,
                            replyToMessage = replyToMessage,
                            onStartRecording = {
                                if (!isDeviceConnected) return@PremiumChatInputArea

                                val hasPermission = ContextCompat.checkSelfPermission(
                                    context,
                                    Manifest.permission.RECORD_AUDIO
                                ) == PackageManager.PERMISSION_GRANTED

                                if (hasPermission) {
                                    val file = audioRecorder.startRecording()
                                    if (file != null) {
                                        isRecording = true
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    }
                                } else {
                                    permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                }
                            }
                        )
                    }
                }

                // Recording interface overlay
                AnimatedVisibility(
                    visible = isRecording,
                    enter = slideInHorizontally(
                        initialOffsetX = { fullWidth -> fullWidth },
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioLowBouncy,
                            stiffness = Spring.StiffnessMedium
                        )
                    ) + fadeIn(
                        animationSpec = tween(400, easing = CubicBezierEasing(0.25f, 0.46f, 0.45f, 0.94f))
                    ),
                    exit = slideOutHorizontally(
                        targetOffsetX = { fullWidth -> fullWidth },
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessHigh
                        )
                    ) + fadeOut(
                        animationSpec = tween(250, easing = CubicBezierEasing(0.55f, 0.06f, 0.68f, 0.19f))
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .zIndex(2f)
                ) {
                    PremiumRecordingInterface(
                        duration = recordingDuration,
                        amplitude = currentAmplitude,
                        onCancel = {
                            isRecording = false
                            audioRecorder.cancelRecording()
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        },
                        onSend = {
                            val recordedFile = audioRecorder.stopRecording()
                            isRecording = false
                            recordedFile?.let {
                                if (isDeviceConnected) {
                                    viewModel.sendAudioFile(it)
                                }
                            }
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        }
                    )
                }
            }
        },
        floatingActionButton = {

        },
        modifier = Modifier.fillMaxSize()
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.background,
                            MaterialTheme.colorScheme.surface.copy(alpha = 0.3f),
                            MaterialTheme.colorScheme.background
                        )
                    )
                )
        ) {
            // Messages list - Base layer (z-index 0)
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .zIndex(1f) // Messages on base layer
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.background,
                                MaterialTheme.colorScheme.surface.copy(alpha = 0.3f),
                                MaterialTheme.colorScheme.background
                            )
                        )
                    ),
                state = listState,
                reverseLayout = true,
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.Bottom),
                userScrollEnabled = true,
                flingBehavior = ScrollableDefaults.flingBehavior()
            ) {

                // Typing indicator
                if (deviceTyping && !isSearchMode) {
                    item(key = "typing_indicator_$deviceId") {
                        AnimatedVisibility(
                            visible = deviceTyping,
                            enter = slideInVertically(
                                initialOffsetY = { it },
                                animationSpec = spring(
                                    dampingRatio = Spring.DampingRatioMediumBouncy,
                                    stiffness = Spring.StiffnessMedium
                                )
                            ) + fadeIn(
                                animationSpec = tween(300, easing = EaseOutCubic)
                            ),
                            exit = slideOutVertically(
                                targetOffsetY = { it },
                                animationSpec = tween(200, easing = EaseInCubic)
                            ) + fadeOut(
                                animationSpec = tween(200, easing = EaseInCubic)
                            )
                        ) {
                            AnimatedTypingIndicatorBubble(deviceName = deviceName)
                        }
                    }
                }

                // Messages - show all messages, highlight search results
                items(
                    items = deviceMessages.reversed(),
                    key = { message ->
                        "msg_${deviceId}_${message.timestamp}_${message.content.hashCode()}_${message.type}"
                    }
                ) { message ->
                    // Get highlighting state for both text and container
                    val isCurrentSearchResult = viewModel.isCurrentSearchResult(message.id)
                    val isHighlighted = highlightedMessageId == message.id

                    // Better container that doesn't interfere with scrolling
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .animateItemPlacement(
                                animationSpec = spring(
                                    dampingRatio = Spring.DampingRatioMediumBouncy,
                                    stiffness = Spring.StiffnessMedium
                                )
                            )
                    ) {
                        PremiumSwipeableMessageBubble(
                            message = message,
                            isSelected = selectedMessages.contains(message.id),
                            isSelectionMode = isSelectionMode,
                            onLongPress = {
                                if (isSearchMode) return@PremiumSwipeableMessageBubble

                                if (!isSelectionMode) {
                                    // Start selection mode
                                    isSelectionMode = true
                                    selectedMessages = setOf(message.id)
                                } else {
                                    // Toggle selection
                                    selectedMessages = if (selectedMessages.contains(message.id)) {
                                        val newSet = selectedMessages - message.id
                                        if (newSet.isEmpty()) {
                                            isSelectionMode = false
                                        }
                                        newSet
                                    } else {
                                        selectedMessages + message.id
                                    }
                                }
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            },
                            onTap = {
                                if (isSelectionMode) {
                                    selectedMessages = if (selectedMessages.contains(message.id)) {
                                        val newSet = selectedMessages - message.id
                                        if (newSet.isEmpty()) {
                                            isSelectionMode = false
                                        }
                                        newSet
                                    } else {
                                        selectedMessages + message.id
                                    }
                                }
                            },
                            onSwipeReply = {
                                if (!isSearchMode && replyToMessage == null && !isSelectionMode) {
                                    replyToMessage = message
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                }
                            }
                        ) {
                            // Pass the correct highlighting state to the container
                            PremiumSearchMessageContainer(
                                message = message,
                                isHighlighted = isHighlighted // This should highlight the container
                            ) {
                                PremiumChatBubble(
                                    message = message,
                                    searchQuery = if (isSearchMode) searchQuery else "",
                                    isCurrentSearchResult = isCurrentSearchResult // This highlights the text
                                )
                            }
                        }
                    }
                }

                // Empty state
                if (deviceMessages.isEmpty() && !deviceTyping) {
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.Chat,
                                contentDescription = "Start chatting",
                                modifier = Modifier.size(64.dp),
                                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                            )
                            Text(
                                text = "Start your conversation with $deviceName",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }

            // Premium background particles effect
            if (!isSearchMode) {
                PremiumBackgroundParticles()
            }

            // Connection status overlay
            if (!isDeviceConnected && !showReconnecting) {
                ConnectionStatusOverlay(
                    deviceName = deviceName,
                    onReconnectClick = {
                        Log.d(TAG, "Reconnect button clicked")
                        showReconnecting = true
                        coroutineScope.launch {
                            viewModel.connectToDeviceByAddress(deviceId)
                            delay(3000L)
                            showReconnecting = false
                        }
                    }
                )
            }

            // Reconnecting overlay - Top layer (z-index 11)
            if (showReconnecting) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .zIndex(11f) // Higher than connection overlay
                ) {
                    ReconnectingOverlay(deviceName = deviceName)
                }
            }
        }
    }

    // Cleanup on dispose
    DisposableEffect(deviceId) {
        onDispose {
            Log.d(TAG, "ChatScreen disposed for device: $deviceName ($deviceId)")
            typingJob?.cancel()
            if (lastTypingTime > 0L) {
                viewModel.onStoppedTyping()
            }
        }
    }
}

// 4. FIXED: Delete function extensions for ChatViewModel (add these to your ChatViewModel)
/*
Add these functions to your ChatViewModel class:

// Delete messages locally only (for me)
fun deleteMessagesForMe(messageIds: Set<String>) {
    viewModelScope.launch {
        try {
            val currentDevice = _currentDeviceId.value ?: return@launch

            // Get current messages from BluetoothService
            val currentMessages = BluetoothService.getMessagesForDevice(currentDevice).toMutableList()

            // Remove messages with matching IDs
            val updatedMessages = currentMessages.filterNot { messageIds.contains(it.id) }

            // Update the messages through BluetoothService
            BluetoothService.setMessagesForDevice(currentDevice, updatedMessages)

            // Update the UI state
            _allMessages.value = _allMessages.value.toMutableMap().apply {
                put(currentDevice, updatedMessages)
            }

            Log.d(TAG, "Deleted ${messageIds.size} messages locally for device $currentDevice")

        } catch (e: Exception) {
            Log.e(TAG, "Error deleting messages for me", e)
        }
    }
}

// Delete messages for everyone (send delete command to other device)
fun deleteMessagesForEveryone(messageIds: Set<String>) {
    viewModelScope.launch {
        try {
            val currentDevice = _currentDeviceId.value ?: return@launch

            // First delete locally
            deleteMessagesForMe(messageIds)

            // Send delete command to the other device
            messageIds.forEach { messageId ->
                sendDeleteCommand(messageId)
            }

            Log.d(TAG, "Sent delete command for ${messageIds.size} messages to device $currentDevice")

        } catch (e: Exception) {
            Log.e(TAG, "Error deleting messages for everyone", e)
        }
    }
}

// Send delete command to connected device
private suspend fun sendDeleteCommand(messageId: String) {
    try {
        val currentDevice = _currentDeviceId.value ?: return

        // Verify connection before sending delete command
        if (!isConnectedToDevice(currentDevice)) {
            Log.w(TAG, "Device not connected for delete command, attempting to reconnect...")
            connectToDeviceByAddress(currentDevice)
            delay(1000L) // Wait for connection

            if (!isConnectedToDevice(currentDevice)) {
                Log.e(TAG, "Failed to establish connection for delete command")
                return
            }
        }

        // Create delete command message
        val deleteCommand = createDeleteCommand(messageId)

        // Send via existing Bluetooth client
        val success = client.sendMessage(deleteCommand, currentDevice)

        if (success) {
            Log.d(TAG, "Delete command sent successfully for message: $messageId")
        } else {
            Log.e(TAG, "Failed to send delete command for message: $messageId")
        }

    } catch (e: Exception) {
        Log.e(TAG, "Error sending delete command for message: $messageId", e)
    }
}

// Create delete command string
private fun createDeleteCommand(messageId: String): String {
    return "DELETE_MESSAGE:$messageId"
}

// Handle incoming delete command from other device
fun handleIncomingDeleteCommand(command: String) {
    viewModelScope.launch {
        try {
            if (command.startsWith("DELETE_MESSAGE:")) {
                val messageId = command.substringAfter("DELETE_MESSAGE:")

                val currentDevice = _currentDeviceId.value ?: return@launch

                // Get current messages from BluetoothService
                val currentMessages = BluetoothService.getMessagesForDevice(currentDevice).toMutableList()

                // Remove the message with matching ID
                val updatedMessages = currentMessages.filterNot { it.id == messageId }

                // Update messages through BluetoothService
                BluetoothService.setMessagesForDevice(currentDevice, updatedMessages)

                // Update UI state
                _allMessages.value = _allMessages.value.toMutableMap().apply {
                    put(currentDevice, updatedMessages)
                }

                Log.d(TAG, "Deleted message $messageId from incoming delete command")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error handling delete command", e)
        }
    }
}
*/

// 5. FIXED: Simple Pixel-style delete option buttons
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PremiumSearchTopAppBar(
    deviceName: String,
    connectionStatus: String,
    isFriendTyping: Boolean,
    isConnected: Boolean,
    isReconnecting: Boolean,
    isSearchMode: Boolean,
    searchQuery: String,
    searchResults: List<Message> = emptyList(),
    currentSearchIndex: Int = -1,
    onSearchQueryChange: (String) -> Unit,
    onSearchToggle: () -> Unit,
    onSearchNext: () -> Unit,
    onSearchPrevious: () -> Unit,
    onNavigateUp: () -> Unit,
    onReconnectClick: () -> Unit,
    // Selection mode parameters
    isSelectionMode: Boolean = false,
    selectedCount: Int = 0,
    onCopySelected: () -> Unit = {},
    // FIXED: Replace onDeleteSelected with specific delete callbacks
    onDeleteForMe: () -> Unit = {},
    viewModel: ChatViewModel? = null,
    selectedMessages: Set<String> = emptySet()
) {
    val keyboardController = LocalSoftwareKeyboardController.current
    var showDeleteDialog by remember { mutableStateOf(false) }

    TopAppBar(
        title = {
            when {
                isSelectionMode -> {
                    Text(
                        text = "$selectedCount selected",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.SemiBold
                        )
                    )
                }

                isSearchMode -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        TextField(
                            value = searchQuery,
                            onValueChange = onSearchQueryChange,
                            placeholder = {
                                Text(
                                    "Search messages...",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                )
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp),
                            singleLine = true,
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(
                                    alpha = 0.4f
                                ),
                                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(
                                    alpha = 0.2f
                                ),
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                                cursorColor = MaterialTheme.colorScheme.primary
                            ),
                            textStyle = MaterialTheme.typography.bodyMedium,
                            shape = RoundedCornerShape(24.dp),
                            leadingIcon = {
                                Icon(
                                    Icons.Default.Search,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp),
                                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                )
                            },
                            keyboardOptions = KeyboardOptions(
                                imeAction = ImeAction.Search
                            ),
                            keyboardActions = KeyboardActions(
                                onSearch = {
                                    keyboardController?.hide()
                                    if (searchResults.isNotEmpty()) {
                                        onSearchNext()
                                    }
                                }
                            )
                        )

                        PremiumSearchNavigationButtons(
                            searchResults = searchResults,
                            currentSearchIndex = currentSearchIndex,
                            searchQuery = searchQuery,
                            onSearchPrevious = onSearchPrevious,
                            onSearchNext = onSearchNext
                        )
                    }
                }

                else -> {
                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = deviceName,
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.SemiBold
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false)
                            )

                            ConnectionIndicator(
                                isConnected = isConnected,
                                isReconnecting = isReconnecting
                            )
                        }

                        Text(
                            text = when {
                                isReconnecting -> "Reconnecting..."
                                !isConnected -> "Disconnected"
                                isFriendTyping -> "typing..."
                                else -> "Connected"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = when {
                                isReconnecting -> MaterialTheme.colorScheme.primary
                                !isConnected -> MaterialTheme.colorScheme.error
                                isFriendTyping -> MaterialTheme.colorScheme.primary
                                else -> Color.Green
                            }
                        )
                    }
                }
            }
        },
        navigationIcon = {
            IconButton(
                onClick = onNavigateUp,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                    )
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
        },
        actions = {
            when {
                isSelectionMode -> {
                    IconButton(onClick = onCopySelected) {
                        Icon(
                            Icons.Default.ContentCopy,
                            contentDescription = "Copy",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    IconButton(onClick = { showDeleteDialog = true }) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "Delete",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }

                isSearchMode -> {
                    IconButton(onClick = onSearchToggle) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Close search",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                else -> {
                    IconButton(onClick = onSearchToggle) {
                        Icon(
                            Icons.Default.Search,
                            contentDescription = "Search",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    if (!isConnected && !isReconnecting) {
                        IconButton(
                            onClick = onReconnectClick,
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                                )
                        ) {
                            Icon(
                                Icons.Default.Refresh,
                                contentDescription = "Reconnect",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)
        )
    )

    // FIXED: Simplified delete dialog that uses the passed callbacks
    if (showDeleteDialog) {
        PremiumDeleteDialog(
            selectedCount = selectedCount,
            onDeleteForMe = {
                onDeleteForMe() // This will call the callback from ChatScreen
                showDeleteDialog = false
            },
            onCancel = {
                showDeleteDialog = false
            }
        )
    }
}

@Composable
private fun PremiumHighlightedText(
    text: String,
    searchQuery: String,
    style: androidx.compose.ui.text.TextStyle,
    color: Color,
    isCurrentResult: Boolean = false,
    modifier: Modifier = Modifier
) {
    // Show normal text if no search query
    if (searchQuery.isEmpty() || searchQuery.isBlank()) {
        Text(
            text = text,
            style = style,
            color = color,
            modifier = modifier
        )
        return
    }

    val annotatedString = buildAnnotatedString {
        val lowercaseText = text.lowercase()
        val lowercaseQuery = searchQuery.lowercase().trim()

        // Return normal text if query is too short or empty after trim
        if (lowercaseQuery.isEmpty()) {
            append(text)
            return@buildAnnotatedString
        }

        var currentIndex = 0

        // Find all matches - even single characters
        val matches = mutableListOf<IntRange>()
        var searchIndex = 0

        while (searchIndex <= lowercaseText.length - lowercaseQuery.length) {
            val matchIndex = lowercaseText.indexOf(lowercaseQuery, searchIndex)
            if (matchIndex == -1) break

            matches.add(matchIndex until (matchIndex + lowercaseQuery.length))
            searchIndex = matchIndex + 1 // Allow overlapping matches
        }

        // Build annotated string with immediate highlighting
        matches.forEach { match ->
            // Add text before match
            if (match.first > currentIndex) {
                append(text.substring(currentIndex, match.first))
            }

            // FIXED: High contrast highlighting - black text on white background
            val highlightBackgroundColor = if (isCurrentResult) {
                // Current result: Bright white background with subtle border effect
                Color.White
            } else {
                // Other matches: Slightly off-white for distinction
                Color(0xFFF5F5F5)
            }

            val highlightTextColor = Color.Black // Always black text for maximum contrast

            // Add highlighted match with high contrast styling
            withStyle(
                style = SpanStyle(
                    background = highlightBackgroundColor,
                    color = highlightTextColor,
                    fontWeight = if (isCurrentResult) FontWeight.ExtraBold else FontWeight.Bold
                )
            ) {
                append(text.substring(match.first, match.last + 1))
            }

            currentIndex = match.last + 1
        }

        // Add remaining text
        if (currentIndex < text.length) {
            append(text.substring(currentIndex))
        }
    }

    // Enhanced animation for current result with better visibility
    val animationModifier = if (isCurrentResult) {
        val infiniteTransition = rememberInfiniteTransition(label = "current_result_highlight")
        val pulseAlpha by infiniteTransition.animateFloat(
            initialValue = 0.8f,
            targetValue = 1.0f,
            animationSpec = infiniteRepeatable(
                animation = tween(800, easing = EaseInOutSine),
                repeatMode = RepeatMode.Reverse
            ),
            label = "visibility_pulse"
        )

        modifier.graphicsLayer {
            alpha = pulseAlpha
        }
    } else {
        modifier
    }

    Text(
        text = annotatedString,
        style = style,
        color = color,
        modifier = animationModifier
    )
}

@Composable
private fun ConnectionIndicator(
    isConnected: Boolean,
    isReconnecting: Boolean
) {
    val infiniteTransition = rememberInfiniteTransition(label = "connection_indicator")

    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (isReconnecting) 1.2f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    val color = when {
        isReconnecting -> MaterialTheme.colorScheme.primary
        isConnected -> Color.Green
        else -> Color.Red
    }

    Box(
        modifier = Modifier
            .size(10.dp)
            .scale(pulseScale)
            .clip(CircleShape)
            .background(color)
    )
}

@Composable
private fun ConnectionStatusOverlay(
    deviceName: String,
    onReconnectClick: () -> Unit
) {
    // FIXED: Proper z-index positioning to avoid interference with messages
    Box(
        modifier = Modifier
            .fillMaxSize()
            .zIndex(10f), // High z-index to appear above messages
        contentAlignment = Alignment.TopCenter
    ) {
        AnimatedVisibility(
            visible = true,
            enter = slideInVertically(
                initialOffsetY = { -it },
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMedium
                )
            ) + fadeIn(),
            exit = slideOutVertically(
                targetOffsetY = { -it },
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessHigh
                )
            ) + fadeOut()
        ) {
            Card(
                modifier = Modifier
                    .padding(16.dp)
                    .widthIn(max = 300.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.95f)
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 12.dp), // Increased elevation
                shape = RoundedCornerShape(20.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // FIXED: Bluetooth icon with cross strike instead of check mark
                    Box(
                        modifier = Modifier.size(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        // FIXED: Bluetooth icon (using a placeholder - you should use actual Bluetooth icon)
                        Icon(
                            // You should use the actual Bluetooth icon from your icon set
                            // For now using SignalWifiOff as a placeholder that represents connectivity
                            Icons.Default.SignalWifiOff,
                            contentDescription = "Bluetooth",
                            modifier = Modifier.size(28.dp),
                            tint = MaterialTheme.colorScheme.error.copy(alpha = 0.6f)
                        )

                        // Cross strike line
                        Canvas(
                            modifier = Modifier.fillMaxSize()
                        ) {
                            drawLine(
                                color = androidx.compose.ui.graphics.Color.Red,
                                start = Offset(size.width * 0.2f, size.height * 0.8f),
                                end = Offset(size.width * 0.8f, size.height * 0.2f),
                                strokeWidth = 3.dp.toPx(),
                                cap = StrokeCap.Round
                            )
                        }
                    }

                    Text(
                        text = "Bluetooth disconnected from $deviceName",
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.Bold
                        ),
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        textAlign = TextAlign.Center
                    )

                    Text(
                        text = "Messages will be queued and sent when reconnected.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f),
                        textAlign = TextAlign.Center
                    )

                    Button(
                        onClick = onReconnectClick,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        ),
                        modifier = Modifier.padding(top = 4.dp)
                    ) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Reconnect",
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ReconnectingOverlay(deviceName: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.4f)), // Slightly more opaque
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier.padding(32.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 16.dp) // Higher elevation
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(48.dp),
                    color = MaterialTheme.colorScheme.primary
                )

                Text(
                    text = "Reconnecting to $deviceName...",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    textAlign = TextAlign.Center
                )

                Text(
                    text = "Please wait",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}


@Composable
private fun AnimatedTypingIndicatorBubble(deviceName: String) {
    var isVisible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        isVisible = true
    }

    val bubbleScale by animateFloatAsState(
        targetValue = if (isVisible) 1f else 0.8f,
        animationSpec = spring(
            dampingRatio = 0.6f,
            stiffness = 400f
        ),
        label = "typing_bubble_scale"
    )

    val bubbleAlpha by animateFloatAsState(
        targetValue = if (isVisible) 1f else 0f,
        animationSpec = tween(300, easing = CubicBezierEasing(0.4f, 0.0f, 0.2f, 1f)),
        label = "typing_bubble_alpha"
    )

    val dots = remember {
        listOf(Animatable(0f), Animatable(0f), Animatable(0f))
    }

    dots.forEachIndexed { index, animatable ->
        LaunchedEffect(animatable) {
            delay(index * 200L)
            animatable.animateTo(
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = keyframes {
                        durationMillis = 1200
                        0f at 0 using EaseInOutCubic
                        -6f at 300 using EaseInOutCubic
                        0f at 600 using EaseInOutCubic
                        0f at 1200
                    },
                    repeatMode = RepeatMode.Restart
                )
            )
        }
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start
    ) {
        Box(
            modifier = Modifier
                .graphicsLayer {
                    scaleX = bubbleScale
                    scaleY = bubbleScale
                    alpha = bubbleAlpha
                }
                .clip(RoundedCornerShape(4.dp, 20.dp, 20.dp, 20.dp))
                .background(
                    brush = Brush.horizontalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.surfaceVariant,
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f)
                        )
                    )
                )
                .padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                dots.forEach { dot ->
                    Box(
                        modifier = Modifier
                            .offset(y = dot.value.dp)
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                            )
                    )
                }
            }
        }
    }
}

@Composable
private fun PremiumChatInputArea(
    input: TextFieldValue,
    onInputChange: (TextFieldValue) -> Unit,
    onSendClick: () -> Unit,
    onAttachmentClick: () -> Unit,
    onTextFieldFocus: () -> Unit,
    isRecording: Boolean,
    isConnected: Boolean,
    replyToMessage: Message? = null,
    onStartRecording: () -> Unit
) {
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shadowElevation = 12.dp,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.98f),
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Box(
            modifier = Modifier
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.surface.copy(alpha = 0.98f),
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.1f),
                            MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)
                        )
                    )
                )
        ) {
            // REMOVED: Disconnection banner completely

            // Main input row with proper spacing
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 12.dp)
                    .imePadding(),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Premium Attachment Button
                PremiumCircularButton(
                    icon = Icons.Default.Add,
                    contentDescription = "Attach File",
                    onClick = {
                        // Only allow attachments when connected (files need immediate transfer)
                        if (isConnected) {
                            focusManager.clearFocus()
                            keyboardController?.hide()
                            onAttachmentClick()
                        }

                    },
                    enabled = isConnected, // File attachments require connection
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isConnected) {
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                        },
                        contentColor = if (isConnected) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        }
                    )
                )

                // FIXED: Enhanced Text Field - ALWAYS ENABLED for text input
                TextField(
                    value = input,
                    onValueChange = onInputChange, // Always allow text input for queuing
                    modifier = Modifier
                        .weight(1f)
                        .onFocusChanged { if (it.isFocused) onTextFieldFocus() },
                    placeholder = {
                        Text(
                            text = when {
                                !isConnected && replyToMessage != null -> "Reply (will be queued)..."
                                !isConnected -> "Message will be queued..."
                                replyToMessage != null -> "Reply to ${if (replyToMessage.isFromMe) "your message" else replyToMessage.sender}..."
                                else -> "Type a message..."
                            },
                            style = MaterialTheme.typography.bodyLarge
                        )
                    },
                    enabled = true, // ALWAYS ENABLED - users can always type
                    shape = RoundedCornerShape(28.dp),
                    colors = TextFieldDefaults.colors(
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        disabledIndicatorColor = Color.Transparent,
                        focusedContainerColor = when {
                            replyToMessage != null -> MaterialTheme.colorScheme.primaryContainer.copy(
                                alpha = 0.4f
                            )

                            !isConnected -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f)
                            else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f)
                        },
                        unfocusedContainerColor = when {
                            replyToMessage != null -> MaterialTheme.colorScheme.primaryContainer.copy(
                                alpha = 0.3f
                            )

                            !isConnected -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
                            else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                        },
                        // FIXED: Text color - always readable
                        focusedTextColor = MaterialTheme.colorScheme.onSurface,
                        unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                    ),
                    textStyle = MaterialTheme.typography.bodyLarge,
                    maxLines = 4
                )

                // FIXED: Enhanced Send/Mic Button - Allow sending even when disconnected (will queue)
                AnimatedContent(
                    targetState = input.text.isBlank(),
                    transitionSpec = {
                        scaleIn(
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                stiffness = Spring.StiffnessHigh
                            )
                        ) + fadeIn() togetherWith
                                scaleOut(
                                    animationSpec = spring(
                                        dampingRatio = Spring.DampingRatioMediumBouncy,
                                        stiffness = Spring.StiffnessHigh
                                    )
                                ) + fadeOut()
                    },
                    label = "send_mic_animation"
                ) { isTextEmpty ->
                    if (isTextEmpty) {
                        PremiumMicrophoneButton(
                            isRecording = isRecording,
                            isEnabled = isConnected, // Voice recording requires connection for immediate processing
                            onClick = onStartRecording
                        )
                    } else {
                        PremiumSendButton(
                            onClick = onSendClick,
                            isEnabled = true, // ALWAYS ENABLED - allow queuing messages
                            isReply = replyToMessage != null,
                            isDisconnected = !isConnected // Pass connection status for visual feedback
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PremiumMicrophoneButton(
    isRecording: Boolean,
    isEnabled: Boolean,
    onClick: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "mic_pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (isRecording) 1.1f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    val backgroundColor = when {
        !isEnabled -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        isRecording -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.primary
    }

    val contentColor = when {
        !isEnabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
        else -> MaterialTheme.colorScheme.onPrimary
    }

    Box(
        modifier = Modifier
            .size(56.dp)
            .scale(if (isEnabled) pulseScale else 1f)
            .clip(CircleShape)
            .background(
                brush = Brush.radialGradient(
                    colors = listOf(
                        backgroundColor,
                        backgroundColor.copy(alpha = 0.9f)
                    )
                )
            )
            .clickable(enabled = isEnabled) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Default.Mic,
            contentDescription = "Record Voice Message",
            tint = contentColor,
            modifier = Modifier.size(24.dp)
        )
    }
}

@Composable
private fun PremiumSendButton(
    onClick: () -> Unit,
    isEnabled: Boolean,
    isReply: Boolean = false,
    isDisconnected: Boolean = false
) {
    var isPressed by remember { mutableStateOf(false) }

    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.9f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessHigh
        ),
        label = "send_scale"
    )

    // FIXED: Better color logic for different states
    val backgroundColor = when {
        !isEnabled -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        isDisconnected -> MaterialTheme.colorScheme.secondary // Orange/amber tone for queue
        isReply -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.primary
    }

    val contentColor = when {
        !isEnabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
        isDisconnected -> MaterialTheme.colorScheme.onSecondary
        isReply -> MaterialTheme.colorScheme.onTertiary
        else -> MaterialTheme.colorScheme.onPrimary
    }

    // FIXED: Better pulse animation logic
    val infiniteTransition = rememberInfiniteTransition(label = "send_pulse")
    val pulseEffect by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if ((isReply || isDisconnected) && isEnabled) 1.03f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    Box(
        modifier = Modifier
            .size(56.dp)
            .scale(scale * pulseEffect)
            .clip(CircleShape)
            .background(
                brush = Brush.linearGradient(
                    colors = listOf(
                        backgroundColor,
                        backgroundColor.copy(alpha = 0.85f)
                    )
                )
            )
            .clickable(
                enabled = isEnabled,
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                isPressed = true
                onClick()
            },
        contentAlignment = Alignment.Center
    ) {
        // FIXED: Better background indicator for special states
        if ((isReply || isDisconnected) && isEnabled) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .scale(pulseEffect)
                    .clip(CircleShape)
                    .background(
                        backgroundColor.copy(alpha = 0.12f)
                    )
            )
        }

        // FIXED: Better icon selection and styling
        Icon(
            imageVector = when {
                isDisconnected -> Icons.Default.Schedule // Clock icon when disconnected
                isReply -> Icons.AutoMirrored.Filled.Reply
                else -> Icons.AutoMirrored.Filled.Send
            },
            contentDescription = when {
                isDisconnected -> "Queue Message"
                isReply -> "Send Reply"
                else -> "Send"
            },
            tint = contentColor,
            modifier = Modifier.size(24.dp)
        )
    }

    LaunchedEffect(isPressed) {
        if (isPressed) {
            delay(100)
            isPressed = false
        }
    }
}

@Composable
private fun PremiumCircularButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    colors: ButtonColors = ButtonDefaults.buttonColors()
) {
    var isPressed by remember { mutableStateOf(false) }

    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessHigh
        ),
        label = "button_scale"
    )

    Box(
        modifier = Modifier
            .size(48.dp)
            .scale(scale)
            .clip(CircleShape)
            .background(colors.containerColor)
            .clickable(
                enabled = enabled,
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                isPressed = true
                onClick()
            },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = colors.contentColor,
            modifier = Modifier.size(22.dp)
        )
    }

    LaunchedEffect(isPressed) {
        if (isPressed) {
            delay(100)
            isPressed = false
        }
    }
}

@Composable
private fun PremiumRecordingInterface(
    duration: Int,
    amplitude: Int,
    onCancel: () -> Unit,
    onSend: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(88.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.98f),
        shadowElevation = 20.dp,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                            MaterialTheme.colorScheme.surface,
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.04f)
                        )
                    )
                )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Enhanced Cancel Button
                PremiumRecordingButton(
                    icon = Icons.Default.Delete,
                    contentDescription = "Cancel Recording",
                    onClick = onCancel,
                    backgroundColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    pulseColor = MaterialTheme.colorScheme.error.copy(alpha = 0.3f)
                )

                // Premium Recording Duration
                PremiumDurationDisplay(duration = duration)

                // Enhanced Waveform
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                    contentAlignment = Alignment.Center
                ) {
                    PremiumRecordingWaveform(
                        amplitude = amplitude,
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                // Premium Recording Indicator
                PremiumRecordingIndicator()

                // Enhanced Send Button
                PremiumRecordingButton(
                    icon = Icons.AutoMirrored.Filled.Send,
                    contentDescription = "Send Recording",
                    onClick = onSend,
                    backgroundColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    pulseColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                )
            }
        }
    }
}

@Composable
private fun PremiumRecordingButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    backgroundColor: Color,
    contentColor: Color,
    pulseColor: Color
) {
    var isPressed by remember { mutableStateOf(false) }

    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.9f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessHigh
        ),
        label = "recording_button_scale"
    )

    val infiniteTransition = rememberInfiniteTransition(label = "button_pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )

    Box(
        modifier = Modifier.size(56.dp),
        contentAlignment = Alignment.Center
    ) {
        // Pulse effect background
        Box(
            modifier = Modifier
                .size(64.dp)
                .scale(pulseScale)
                .clip(CircleShape)
                .background(pulseColor)
        )

        // Main button
        Box(
            modifier = Modifier
                .size(56.dp)
                .scale(scale)
                .clip(CircleShape)
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            backgroundColor,
                            backgroundColor.copy(alpha = 0.9f)
                        )
                    )
                )
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {
                    isPressed = true
                    onClick()
                },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = contentColor,
                modifier = Modifier.size(24.dp)
            )
        }
    }

    LaunchedEffect(isPressed) {
        if (isPressed) {
            delay(100)
            isPressed = false
        }
    }
}

@Composable
private fun PremiumDurationDisplay(duration: Int) {
    val animatedDuration by animateIntAsState(
        targetValue = duration,
        animationSpec = tween(100, easing = EaseOutCubic),
        label = "duration"
    )

    Box(
        modifier = Modifier
            .background(
                brush = Brush.horizontalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)
                    )
                ),
                shape = RoundedCornerShape(16.dp)
            )
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Text(
            text = formatDuration(animatedDuration),
            style = MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.Bold
            ),
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
private fun PremiumRecordingIndicator() {
    val infiniteTransition = rememberInfiniteTransition(label = "recording_pulse")

    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "indicator_pulse_scale"
    )

    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "indicator_pulse_alpha"
    )

    Box(
        modifier = Modifier
            .background(
                MaterialTheme.colorScheme.surface,
                CircleShape
            )
            .padding(12.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .scale(pulseScale)
                    .clip(CircleShape)
                    .background(
                        MaterialTheme.colorScheme.error.copy(alpha = pulseAlpha)
                    )
            )

            Icon(
                Icons.Default.Mic,
                contentDescription = "Recording",
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun PremiumRecordingWaveform(
    amplitude: Int,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    barCount: Int = 30
) {
    val amplitudeHistory = remember { mutableStateListOf<Float>() }
    val maxAmplitude = 32767f

    val normalizedAmplitude = (amplitude.toFloat() / maxAmplitude).coerceIn(0f, 1f)

    LaunchedEffect(normalizedAmplitude) {
        amplitudeHistory.add(normalizedAmplitude)
        if (amplitudeHistory.size > barCount) {
            amplitudeHistory.removeAt(0)
        }
    }

    val animatedAmplitudes = List(barCount) { index ->
        val targetValue = amplitudeHistory.getOrNull(index) ?: 0f
        val animatable = remember { Animatable(0f) }
        LaunchedEffect(targetValue) {
            animatable.animateTo(
                targetValue,
                animationSpec = spring(
                    dampingRatio = 0.7f,
                    stiffness = 300f
                )
            )
        }
        animatable.value
    }

    Canvas(modifier = modifier) {
        drawPremiumWaveform(
            amplitudes = animatedAmplitudes,
            color = color,
            canvasSize = size
        )
    }
}

private fun DrawScope.drawPremiumWaveform(
    amplitudes: List<Float>,
    color: Color,
    canvasSize: androidx.compose.ui.geometry.Size
) {
    val barWidth = 4.dp.toPx()
    val barSpacing = 3.dp.toPx()
    val totalBarSpace = barWidth + barSpacing
    val centerY = canvasSize.height / 2f
    val maxBarHeight = canvasSize.height * 0.85f

    amplitudes.forEachIndexed { index, amplitude ->
        val barHeight = max(8.dp.toPx(), amplitude * maxBarHeight)
        val startX = index * totalBarSpace

        // Calculate dynamic color based on amplitude
        val barColor = if (amplitude > 0.7f) {
            color
        } else if (amplitude > 0.4f) {
            color.copy(alpha = 0.8f)
        } else {
            color.copy(alpha = 0.5f + amplitude * 0.3f)
        }

        // Main bar
        drawLine(
            color = barColor,
            start = Offset(startX, centerY - barHeight / 2f),
            end = Offset(startX, centerY + barHeight / 2f),
            strokeWidth = barWidth,
            cap = StrokeCap.Round
        )

        // Glow effect for higher amplitudes
        if (amplitude > 0.6f) {
            drawLine(
                color = barColor.copy(alpha = 0.3f),
                start = Offset(startX, centerY - barHeight / 2f - 3.dp.toPx()),
                end = Offset(startX, centerY + barHeight / 2f + 3.dp.toPx()),
                strokeWidth = barWidth + 4.dp.toPx(),
                cap = StrokeCap.Round
            )
        }
    }
}

@Composable
private fun PremiumBackgroundParticles() {
    val particles = remember {
        List(20) {
            ParticleState(
                x = Random.nextFloat(),
                y = Random.nextFloat(),
                size = Random.nextFloat() * 3f + 1f,
                speed = Random.nextFloat() * 0.0005f + 0.0001f,
                alpha = Random.nextFloat() * 0.3f + 0.1f
            )
        }
    }

    LaunchedEffect(Unit) {
        while (true) {
            delay(50)
            particles.forEach { particle ->
                particle.y = (particle.y + particle.speed) % 1f
                if (particle.y < 0.01f) {
                    particle.x = Random.nextFloat()
                    particle.alpha = Random.nextFloat() * 0.3f + 0.1f
                }
            }
        }
    }

    Canvas(
        modifier = Modifier.fillMaxSize()
    ) {
        particles.forEach { particle ->
            drawCircle(
                color = Color.White.copy(alpha = particle.alpha),
                radius = particle.size,
                center = Offset(
                    x = particle.x * size.width,
                    y = particle.y * size.height
                )
            )
        }
    }
}

private data class ParticleState(
    var x: Float,
    var y: Float,
    val size: Float,
    val speed: Float,
    var alpha: Float
)

// Helper functions
private fun formatDuration(millis: Int): String {
    val seconds = millis / 1000
    val minutes = seconds / 60
    val remainingSeconds = seconds % 60
    return "%d:%02d".format(minutes, remainingSeconds)
}

private fun formatDuration(millis: Long): String {
    val minutes = TimeUnit.MILLISECONDS.toMinutes(millis)
    val seconds = TimeUnit.MILLISECONDS.toSeconds(millis) - TimeUnit.MINUTES.toSeconds(minutes)
    return String.format("%02d:%02d", minutes, seconds)
}

@SuppressLint("QueryPermissionsNeeded")
private fun openFile(context: Context, uriString: String, mimeType: String, isFromMe: Boolean) {
    try {
        val uri = uriString.toUri()
        Log.d(
            "ChatScreen",
            "Opening file - URI: $uriString, isFromMe: $isFromMe, scheme: ${uri.scheme}, mimeType: $mimeType"
        )

        val intent = when (uri.scheme) {
            "content" -> {
                Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, mimeType)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
            }

            "file" -> {
                val file = File(uri.path!!)
                if (file.exists()) {
                    val contentUri = FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.provider",
                        file
                    )
                    Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(contentUri, mimeType)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                } else {
                    Log.e("ChatScreen", "File does not exist: ${file.absolutePath}")
                    return
                }
            }

            null -> {
                val file = File(uriString)
                if (file.exists()) {
                    val contentUri = FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.provider",
                        file
                    )
                    Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(contentUri, mimeType)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                } else {
                    Log.e("ChatScreen", "File does not exist: $uriString")
                    return
                }
            }

            else -> {
                Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, mimeType)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
            }
        }

        if (intent.resolveActivity(context.packageManager) != null) {
            context.startActivity(intent)
            Log.d("ChatScreen", "Successfully opened file with external app")
        } else {
            Log.w("ChatScreen", "No app found to open file of type: $mimeType")
            val fallbackIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(intent.data, "*/*")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            if (fallbackIntent.resolveActivity(context.packageManager) != null) {
                context.startActivity(fallbackIntent)
            }
        }
    } catch (e: Exception) {
        Log.e("ChatScreen", "Error opening file: ${e.message}", e)
    }
}

private fun getDocumentIcon(mimeType: String?): ImageVector {
    return when {
        mimeType == null -> Icons.AutoMirrored.Filled.InsertDriveFile
        mimeType.startsWith("image/") -> Icons.Default.Image
        mimeType.startsWith("video/") -> Icons.Default.VideoFile
        mimeType.startsWith("audio/") -> Icons.Default.AudioFile
        mimeType.contains("pdf") -> Icons.Default.PictureAsPdf
        mimeType.contains("text") -> Icons.AutoMirrored.Filled.TextSnippet
        mimeType.contains("zip") || mimeType.contains("rar") || mimeType.contains("archive") -> Icons.Default.Archive
        mimeType.contains("word") || mimeType.contains("doc") -> Icons.Default.Description
        mimeType.contains("sheet") || mimeType.contains("excel") -> Icons.Default.TableChart
        mimeType.contains("presentation") || mimeType.contains("powerpoint") -> Icons.Default.Slideshow
        else -> Icons.AutoMirrored.Filled.InsertDriveFile
    }
}

private fun formatFileSize(bytes: Long): String {
    if (bytes <= 0) return "0 B"

    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    val digitGroups = (log10(bytes.toDouble()) / log10(1024.0)).toInt()

    return DecimalFormat("#,##0.#").format(bytes / 1024.0.pow(digitGroups.toDouble())) + " " + units[digitGroups]
}

@Composable
private fun PremiumSearchMessageContainer(
    message: Message,
    isHighlighted: Boolean = false,
    content: @Composable () -> Unit
) {
    var isVisible by remember { mutableStateOf(false) }

    LaunchedEffect(message) {
        delay(50)
        isVisible = true
    }

    val animatedScale by animateFloatAsState(
        targetValue = if (isVisible) 1f else 0.92f,
        animationSpec = spring(
            dampingRatio = 0.75f,
            stiffness = 400f
        ),
        label = "message_scale"
    )

    // FIXED: Professional selection-style highlighting
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = animatedScale
                scaleY = animatedScale
            }
            .then(
                if (isHighlighted) {
                    // FIXED: Use the same professional style as selection highlighting
                    Modifier.background(
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.15f), // Slightly more visible than selection
                        RoundedCornerShape(12.dp) // Slightly larger radius for search
                    )
                } else Modifier
            )
    ) {
        content()
    }
}

@Composable
private fun EnhancedReplyIndicator(
    replyToMessage: Message,
    isFromMe: Boolean
) {
    val borderColor = if (isFromMe) {
        MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f)
    } else {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
    }

    val backgroundColor = if (isFromMe) {
        MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.12f)
    } else {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
    }

    var isVisible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        delay(50)
        isVisible = true
    }

    val slideOffset by animateFloatAsState(
        targetValue = if (isVisible) 0f else -20f,
        animationSpec = spring(
            dampingRatio = 0.7f,
            stiffness = 300f
        ),
        label = "reply_slide"
    )

    // FIXED: Compact reply indicator
    Surface(
        modifier = Modifier
            .wrapContentWidth()
            .padding(horizontal = 6.dp, vertical = 3.dp)
            .offset(y = slideOffset.dp),
        shape = RoundedCornerShape(12.dp),
        color = backgroundColor,
        border = BorderStroke(
            width = 1.5.dp,
            color = borderColor
        )
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 8.dp, vertical = 6.dp)
                .wrapContentWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Compact reply line
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(24.dp)
                    .background(
                        borderColor,
                        shape = RoundedCornerShape(1.5.dp)
                    )
            )

            Column(
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                // Compact sender name
                Text(
                    text = if (replyToMessage.isFromMe) "You" else replyToMessage.sender,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp
                    ),
                    color = borderColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                // Compact message preview
                Text(
                    text = when (replyToMessage.type) {
                        MessageType.TEXT -> replyToMessage.content
                        MessageType.IMAGE -> "📷 Photo"
                        MessageType.DOCUMENT -> "📄 ${replyToMessage.fileName ?: "Document"}"
                        MessageType.AUDIO -> "🎵 Voice message"
                    },
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = 10.sp
                    ),
                    color = if (isFromMe) {
                        MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f)
                    } else {
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun PremiumChatBubble(
    message: Message,
    searchQuery: String = "",
    isCurrentSearchResult: Boolean = false
) {
    val context = LocalContext.current
    val isFromMe = message.isFromMe

    // Fixed: Use wrapContentWidth and proper alignment
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
        horizontalArrangement = if (isFromMe) Arrangement.End else Arrangement.Start
    ) {
        // Fixed: Compact container that wraps content tightly with corrected corner shapes and Pixel-style colors
        Surface(
            shape = RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 16.dp,
                // FIXED: Both sender and receiver should have upward-pointing nose
                bottomStart = if (isFromMe) 16.dp else 4.dp,  // Small radius for upward nose
                bottomEnd = if (isFromMe) 4.dp else 16.dp     // Small radius for upward nose
            ),
            // FIXED: Pixel-style blue colors - sender gets blue, receiver gets light gray
            color = if (isFromMe) {
                Color(0xFF1976D2) // Pixel-style blue for sent messages
            } else {
                Color(0xFFF5F5F5) // Light gray for received messages
            },
            tonalElevation = 2.dp,
            shadowElevation = 1.dp,
            modifier = Modifier
                .wrapContentWidth() // Fixed: Only take width needed
                .widthIn(max = 300.dp) // Maximum width constraint
                .animateContentSize(
                    animationSpec = spring(
                        dampingRatio = 0.8f,
                        stiffness = 380f
                    )
                )
        ) {
            Column(
                modifier = Modifier.wrapContentWidth() // Fixed: Wrap content width
            ) {
                // Show reply indicator at the top if this is a reply
                message.replyToMessage?.let { replyTo ->
                    EnhancedReplyIndicator(
                        replyToMessage = replyTo,
                        isFromMe = message.isFromMe
                    )
                }

                // Fixed: Message content with proper width handling and updated text colors
                when (message.type) {
                    MessageType.TEXT -> {
                        PremiumTextContentWithSearch(
                            text = message.content,
                            // FIXED: White text for sent (blue) messages, dark text for received (light) messages
                            textColor = if (isFromMe) Color.White else Color(0xFF212121),
                            searchQuery = searchQuery,
                            isCurrentSearchResult = isCurrentSearchResult,
                            isFromMe = message.isFromMe,
                            timestamp = message.timestamp,
                            status = message.status
                        )
                    }

                    MessageType.IMAGE -> {
                        PremiumImageContent(
                            message = message,
                            onImageClick = {
                                message.imageUri?.let { uriString ->
                                    openFile(context, uriString, "image/*", message.isFromMe)
                                }
                            }
                        )
                        // Timestamp for image messages with updated colors
                        CompactMessageTimestamp(
                            timestamp = message.timestamp,
                            status = message.status,
                            isFromMe = message.isFromMe,
                            textColor = if (isFromMe) Color.White else Color(0xFF212121)
                        )
                    }

                    MessageType.DOCUMENT -> {
                        EnhancedDocumentContent(
                            message = message,
                            textColor = if (isFromMe) Color.White else Color(0xFF212121),
                            searchQuery = searchQuery,
                            isCurrentSearchResult = isCurrentSearchResult,
                            onDocumentClick = {
                                if (message.status == MessageStatus.SENT) {
                                    message.documentUri?.let { uriString ->
                                        val mimeType = message.mimeType ?: "*/*"
                                        openFile(context, uriString, mimeType, message.isFromMe)
                                    }
                                }
                            }
                        )
                        // Timestamp for document messages with updated colors
                        CompactMessageTimestamp(
                            timestamp = message.timestamp,
                            status = message.status,
                            isFromMe = message.isFromMe,
                            textColor = if (isFromMe) Color.White else Color(0xFF212121)
                        )
                    }

                    MessageType.AUDIO -> {
                        PremiumAudioContent(
                            message = message,
                            textColor = if (isFromMe) Color.White else Color(0xFF212121),
                            onAudioClick = {
                                if (message.status == MessageStatus.SENT) {
                                    message.documentUri?.let { uriString ->
                                        openFile(context, uriString, "audio/*", message.isFromMe)
                                    }
                                }
                            }
                        )
                        // Timestamp for audio messages with updated colors
                        CompactMessageTimestamp(
                            timestamp = message.timestamp,
                            status = message.status,
                            isFromMe = message.isFromMe,
                            textColor = if (isFromMe) Color.White else Color(0xFF212121)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PremiumTextContentWithSearch(
    text: String,
    textColor: Color,
    searchQuery: String = "",
    isCurrentSearchResult: Boolean = false,
    isFromMe: Boolean,
    timestamp: Long,
    status: MessageStatus
) {
    var isVisible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        delay(100)
        isVisible = true
    }

    val animatedAlpha by animateFloatAsState(
        targetValue = if (isVisible) 1f else 0f,
        animationSpec = tween(300, easing = EaseOutCubic),
        label = "text_alpha"
    )

    // Fixed: Compact text container with proper width management
    Column(
        modifier = Modifier
            .wrapContentWidth() // Fixed: Wrap content width
            .graphicsLayer { alpha = animatedAlpha }
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        // Fixed: Main text with proper width handling
        PremiumHighlightedText(
            text = text,
            searchQuery = searchQuery,
            style = MaterialTheme.typography.bodyLarge,
            color = textColor,
            isCurrentResult = isCurrentSearchResult,
            modifier = Modifier.wrapContentWidth() // Fixed: Wrap content width
        )

        // Fixed: Compact timestamp row
        Row(
            modifier = Modifier
                .wrapContentWidth() // Fixed: Don't use fillMaxWidth
                .padding(top = 4.dp),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Timestamp
            Text(
                text = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(timestamp)),
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 11.sp
                ),
                color = textColor.copy(alpha = 0.7f)
            )

            // Status indicator for sent messages
            if (isFromMe) {
                Spacer(modifier = Modifier.width(3.dp))

                when (status) {
                    MessageStatus.SENDING -> {
                        Icon(
                            Icons.Default.Schedule,
                            contentDescription = "Sending",
                            modifier = Modifier.size(12.dp),
                            tint = textColor.copy(alpha = 0.6f)
                        )
                    }

                    MessageStatus.SENT -> {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = "Sent",
                            modifier = Modifier.size(12.dp),
                            tint = textColor.copy(alpha = 0.8f)
                        )
                    }

                    MessageStatus.FAILED -> {
                        Icon(
                            Icons.Default.Error,
                            contentDescription = "Failed",
                            modifier = Modifier.size(12.dp),
                            tint = MaterialTheme.colorScheme.error
                        )
                    }

                    else -> {}
                }
            }
        }
    }
}

@Composable
private fun CompactMessageTimestamp(
    timestamp: Long,
    status: MessageStatus,
    isFromMe: Boolean,
    textColor: Color
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Timestamp
        Text(
            text = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(timestamp)),
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 11.sp
            ),
            color = textColor.copy(alpha = 0.7f)
        )

        // Status indicator for sent messages
        if (isFromMe) {
            Spacer(modifier = Modifier.width(3.dp))

            when (status) {
                MessageStatus.SENDING -> {
                    Icon(
                        Icons.Default.Schedule,
                        contentDescription = "Sending",
                        modifier = Modifier.size(12.dp),
                        tint = textColor.copy(alpha = 0.6f)
                    )
                }

                MessageStatus.SENT -> {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = "Sent",
                        modifier = Modifier.size(12.dp),
                        tint = textColor.copy(alpha = 0.8f)
                    )
                }

                MessageStatus.FAILED -> {
                    Icon(
                        Icons.Default.Error,
                        contentDescription = "Failed",
                        modifier = Modifier.size(12.dp),
                        tint = MaterialTheme.colorScheme.error
                    )
                }

                else -> {}
            }
        }
    }
}

@Composable
private fun PremiumImageContent(
    message: Message,
    onImageClick: () -> Unit
) {
    var imageLoaded by remember { mutableStateOf(false) }
    var isVisible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        delay(80)
        isVisible = true
    }

    val imageScale by animateFloatAsState(
        targetValue = if (imageLoaded && isVisible) 1f else 0.95f,
        animationSpec = spring(
            dampingRatio = 0.75f,
            stiffness = 300f
        ),
        label = "image_scale"
    )

    val imageAlpha by animateFloatAsState(
        targetValue = if (isVisible) 1f else 0f,
        animationSpec = tween(400, easing = CubicBezierEasing(0.4f, 0.0f, 0.2f, 1f)),
        label = "image_alpha"
    )

    val shimmerTransition = rememberInfiniteTransition(label = "shimmer")
    val shimmerOffset by shimmerTransition.animateFloat(
        initialValue = -300f,
        targetValue = 300f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmer"
    )

    // Fixed: Use wrapContentSize for image container
    Box(
        modifier = Modifier
            .wrapContentSize() // Fixed: Wrap content size
            .aspectRatio(1f)
            .size(200.dp) // Fixed size for consistency
            .graphicsLayer {
                scaleX = imageScale
                scaleY = imageScale
                alpha = imageAlpha
            }
            .clickable(enabled = message.status == MessageStatus.SENT) { onImageClick() },
        contentAlignment = Alignment.Center
    ) {
        if (!imageLoaded) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        brush = Brush.linearGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                            ),
                            start = Offset(shimmerOffset - 150f, shimmerOffset - 150f),
                            end = Offset(shimmerOffset + 150f, shimmerOffset + 150f)
                        )
                    )
            )
        }

        AsyncImage(
            model = message.imageUri,
            contentDescription = "Image",
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
            onSuccess = { imageLoaded = true },
            onError = { imageLoaded = true }
        )

        if (imageLoaded) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color.White.copy(alpha = 0.03f),
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.02f)
                            )
                        )
                    )
            )
        }

        if (message.isFromMe && message.status != MessageStatus.SENT) {
            PremiumProgressOverlay(
                status = message.status,
                progress = message.progress
            )
        }
    }
}

@Composable
private fun EnhancedDocumentContent(
    message: Message,
    textColor: Color,
    searchQuery: String = "",
    isCurrentSearchResult: Boolean = false,
    onDocumentClick: () -> Unit
) {
    var isVisible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        delay(120)
        isVisible = true
    }

    val contentAlpha by animateFloatAsState(
        targetValue = if (isVisible) 1f else 0f,
        animationSpec = tween(350, easing = EaseOutCubic),
        label = "document_alpha"
    )

    Row(
        modifier = Modifier
            .wrapContentWidth() // Fixed: Wrap content width
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .graphicsLayer { alpha = contentAlpha }
            .clickable { onDocumentClick() },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        val iconScale by animateFloatAsState(
            targetValue = if (isVisible) 1f else 0.8f,
            animationSpec = spring(
                dampingRatio = 0.6f,
                stiffness = 400f
            ),
            label = "icon_scale"
        )

        Icon(
            imageVector = getDocumentIcon(message.mimeType),
            contentDescription = "Document",
            tint = textColor,
            modifier = Modifier
                .size(32.dp)
                .graphicsLayer {
                    scaleX = iconScale
                    scaleY = iconScale
                }
        )

        Column(
            modifier = Modifier.wrapContentWidth() // Fixed: Wrap content width
        ) {
            PremiumHighlightedText(
                text = message.fileName ?: "Document",
                searchQuery = searchQuery,
                style = MaterialTheme.typography.bodyMedium,
                color = textColor,
                isCurrentResult = isCurrentSearchResult
            )

            message.fileSize?.let { size ->
                Text(
                    text = formatFileSize(size),
                    color = textColor.copy(alpha = 0.7f),
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }

        if (message.isFromMe && message.status != MessageStatus.SENT) {
            PremiumStatusIndicator(
                status = message.status,
                progress = message.progress,
                color = textColor
            )
        }
    }
}

@Composable
private fun PremiumAudioContent(
    message: Message,
    textColor: Color,
    onAudioClick: () -> Unit
) {
    var isVisible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        delay(100)
        isVisible = true
    }

    val contentAlpha by animateFloatAsState(
        targetValue = if (isVisible) 1f else 0f,
        animationSpec = tween(400, easing = EaseOutCubic),
        label = "audio_alpha"
    )

    // Fixed: Use wrapContentWidth for audio content
    Column(
        modifier = Modifier
            .wrapContentWidth() // Fixed: Wrap content width
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .graphicsLayer { alpha = contentAlpha }
            .clickable { onAudioClick() }
    ) {
        Row(
            modifier = Modifier.wrapContentWidth(), // Fixed: Wrap content width
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val iconRotation by animateFloatAsState(
                targetValue = if (isVisible) 0f else -180f,
                animationSpec = spring(
                    dampingRatio = 0.7f,
                    stiffness = 200f
                ),
                label = "icon_rotation"
            )

            Icon(
                Icons.Default.PlayArrow,
                contentDescription = "Play Audio",
                tint = textColor,
                modifier = Modifier
                    .size(24.dp)
                    .graphicsLayer { rotationZ = iconRotation }
            )

            PremiumWaveformView(
                modifier = Modifier
                    .width(120.dp) // Fixed width
                    .height(24.dp),
                color = textColor,
                isVisible = isVisible
            )

            Text(
                text = formatDuration(message.duration ?: 0L),
                color = textColor.copy(alpha = 0.8f),
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}

@Composable
private fun PremiumWaveformView(
    modifier: Modifier = Modifier,
    color: Color,
    isVisible: Boolean,
    barCount: Int = 20
) {
    val animatedBars = remember {
        (0 until barCount).map { Animatable(0f) }
    }

    LaunchedEffect(isVisible) {
        if (isVisible) {
            animatedBars.forEachIndexed { index, animatable ->
                launch {
                    delay(index * 30L)
                    animatable.animateTo(
                        targetValue = Random.nextFloat() * 0.4f + 0.2f,
                        animationSpec = spring(
                            dampingRatio = 0.8f,
                            stiffness = 200f
                        )
                    )
                }
            }
        }
    }

    Canvas(modifier = modifier.height(24.dp)) {
        val barWidth = 2.dp.toPx()
        val barSpacing = 1.5.dp.toPx()
        val totalBarSpace = barWidth + barSpacing
        val centerY = size.height / 2f
        val maxBarHeight = size.height * 0.8f

        animatedBars.forEachIndexed { index, animatable ->
            val barHeight = max(4.dp.toPx(), animatable.value * maxBarHeight)
            val startX = index * totalBarSpace

            drawLine(
                color = color.copy(alpha = 0.7f),
                start = Offset(startX, centerY - barHeight / 2f),
                end = Offset(startX, centerY + barHeight / 2f),
                strokeWidth = barWidth,
                cap = StrokeCap.Round
            )
        }
    }
}

@Composable
private fun PremiumProgressOverlay(
    status: MessageStatus,
    progress: Float
) {
    val overlayAlpha by animateFloatAsState(
        targetValue = 1f,
        animationSpec = tween(200, easing = EaseOutCubic),
        label = "overlay_alpha"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color.Black.copy(alpha = 0.4f),
                        Color.Black.copy(alpha = 0.6f)
                    )
                )
            )
            .graphicsLayer { alpha = overlayAlpha },
        contentAlignment = Alignment.Center
    ) {
        when (status) {
            MessageStatus.FAILED -> {
                Icon(
                    Icons.Default.Error,
                    contentDescription = "Failed",
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(32.dp)
                )
            }

            else -> {
                PremiumProgressIndicator(progress = progress)
            }
        }
    }
}

@Composable
private fun PremiumProgressIndicator(progress: Float) {
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = spring(
            dampingRatio = 0.8f,
            stiffness = 200f
        ),
        label = "progress"
    )

    CircularProgressIndicator(
        progress = { animatedProgress },
        modifier = Modifier.size(32.dp),
        color = Color.White,
        strokeWidth = 3.dp,
        trackColor = Color.White.copy(alpha = 0.3f),
        strokeCap = StrokeCap.Round
    )
}

@Composable
private fun PremiumStatusIndicator(
    status: MessageStatus,
    progress: Float,
    color: Color
) {
    when (status) {
        MessageStatus.SENDING -> {
            val animatedProgress by animateFloatAsState(
                targetValue = progress,
                animationSpec = spring(dampingRatio = 0.7f, stiffness = 300f),
                label = "status_progress"
            )

            CircularProgressIndicator(
                progress = { animatedProgress },
                modifier = Modifier.size(16.dp),
                strokeWidth = 2.dp,
                color = color,
                strokeCap = StrokeCap.Round
            )
        }

        MessageStatus.FAILED -> {
            Icon(
                Icons.Default.Error,
                contentDescription = "Failed",
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(16.dp)
            )
        }

        else -> {}
    }
}

// Premium Attachment Picker
@Composable
private fun PremiumAttachmentPicker(
    onGalleryClick: () -> Unit,
    onDocumentClick: () -> Unit,
    onCameraClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.Start
    ) {
        Card(
            modifier = Modifier
                .wrapContentWidth()
                .wrapContentHeight(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.98f)
            ),
            elevation = CardDefaults.cardElevation(
                defaultElevation = 16.dp
            )
        ) {
            Box(
                modifier = Modifier
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.surface.copy(alpha = 0.98f),
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                                MaterialTheme.colorScheme.surface.copy(alpha = 0.98f)
                            )
                        )
                    )
            ) {
                Column(
                    modifier = Modifier
                        .padding(20.dp)
                        .wrapContentSize(),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    PremiumAttachmentOption(
                        icon = Icons.Default.PhotoLibrary,
                        label = "Photos & Videos",
                        description = "Share from gallery",
                        onClick = onGalleryClick,
                        gradient = Brush.horizontalGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.1f)
                            )
                        ),
                        iconTint = MaterialTheme.colorScheme.primary
                    )

                    PremiumAttachmentOption(
                        icon = Icons.AutoMirrored.Filled.InsertDriveFile,
                        label = "Document",
                        description = "Share files & docs",
                        onClick = onDocumentClick,
                        gradient = Brush.horizontalGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.secondary.copy(alpha = 0.15f),
                                MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.1f)
                            )
                        ),
                        iconTint = MaterialTheme.colorScheme.secondary
                    )

                    PremiumAttachmentOption(
                        icon = Icons.Default.CameraAlt,
                        label = "Camera",
                        description = "Take photo or video",
                        onClick = onCameraClick,
                        gradient = Brush.horizontalGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.tertiary.copy(alpha = 0.15f),
                                MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.1f)
                            )
                        ),
                        iconTint = MaterialTheme.colorScheme.tertiary
                    )
                }
            }
        }
    }
}

@Composable
private fun PremiumAttachmentOption(
    icon: ImageVector,
    label: String,
    description: String,
    onClick: () -> Unit,
    gradient: Brush,
    iconTint: Color
) {
    var isPressed by remember { mutableStateOf(false) }

    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessHigh
        ),
        label = "option_scale"
    )

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                isPressed = true
                onClick()
            },
        shape = RoundedCornerShape(16.dp),
        color = Color.Transparent
    ) {
        Box(
            modifier = Modifier
                .background(brush = gradient)
                .padding(horizontal = 16.dp, vertical = 16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .background(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    iconTint.copy(alpha = 0.2f),
                                    iconTint.copy(alpha = 0.1f)
                                )
                            ),
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = label,
                        tint = iconTint,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.SemiBold
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                }

                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }

    LaunchedEffect(isPressed) {
        if (isPressed) {
            delay(100)
            isPressed = false
        }
    }
}

@Composable
private fun PremiumReplyPreview(
    replyToMessage: Message?,
    deviceName: String,
    onDismiss: () -> Unit
) {
    AnimatedVisibility(
        visible = replyToMessage != null,
        enter = slideInVertically(
            initialOffsetY = { -it },
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessMedium
            )
        ) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut()
    ) {
        replyToMessage?.let { message ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Clean reply line
                    Box(
                        modifier = Modifier
                            .width(4.dp)
                            .height(40.dp)
                            .background(
                                MaterialTheme.colorScheme.primary,
                                RoundedCornerShape(2.dp)
                            )
                    )

                    // Message content
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = if (message.isFromMe) "You" else deviceName,
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.SemiBold
                            ),
                            color = MaterialTheme.colorScheme.primary
                        )

                        Text(
                            text = when (message.type) {
                                MessageType.TEXT -> message.content
                                MessageType.IMAGE -> "📷 Photo"
                                MessageType.DOCUMENT -> "📄 ${message.fileName ?: "Document"}"
                                MessageType.AUDIO -> "🎵 Voice message"
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    // Clean close button
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Cancel reply",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}

// NEW: Helper function for reply timestamp formatting
private fun formatReplyTimestamp(timestamp: Long): String {
    val now = System.currentTimeMillis()
    val diff = now - timestamp

    return when {
        diff < 60_000 -> "Just now"
        diff < 3600_000 -> "${diff / 60_000}m ago"
        diff < 86400_000 -> "${diff / 3600_000}h ago"
        else -> "${diff / 86400_000}d ago"
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PremiumSwipeableMessageBubble(
    message: Message,
    isSelected: Boolean,
    isSelectionMode: Boolean,
    onLongPress: () -> Unit,
    onTap: () -> Unit,
    onSwipeReply: () -> Unit,
    content: @Composable () -> Unit
) {
    var offsetX by remember { mutableFloatStateOf(0f) }
    var isReplying by remember { mutableStateOf(false) }
    var isDragging by remember { mutableStateOf(false) }
    var hasStartedHorizontalSwipe by remember { mutableStateOf(false) }
    var initialTouchX by remember { mutableFloatStateOf(0f) }
    var initialTouchY by remember { mutableFloatStateOf(0f) }
    var totalDragX by remember { mutableFloatStateOf(0f) }
    var totalDragY by remember { mutableFloatStateOf(0f) }

    val maxSwipe = 80.dp.value
    val replyThreshold = maxSwipe * 0.4f
    val swipeDetectionThreshold = 40f
    val verticalScrollThreshold = 20f
    val directionRatio = 2.0f

    // Reply animation
    val replyScale by animateFloatAsState(
        targetValue = if (abs(offsetX) > replyThreshold) 1.2f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessHigh
        ),
        label = "reply_scale"
    )

    val replyAlpha by animateFloatAsState(
        targetValue = if (abs(offsetX) > replyThreshold) 1f else 0.6f,
        animationSpec = tween(200),
        label = "reply_alpha"
    )

    val animatedOffsetX by animateFloatAsState(
        targetValue = offsetX,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessHigh
        ),
        label = "offset_animation"
    )

    // Fixed: Use wrapContentWidth for the container
    Box(
        modifier = Modifier
            .fillMaxWidth() // Keep fillMaxWidth for touch area
            .padding(horizontal = 4.dp) // Reduced padding
            .offset { IntOffset(animatedOffsetX.roundToInt(), 0) }
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val down = awaitFirstDown(requireUnconsumed = false)

                        isDragging = false
                        hasStartedHorizontalSwipe = false
                        isReplying = false
                        initialTouchX = down.position.x
                        initialTouchY = down.position.y
                        totalDragX = 0f
                        totalDragY = 0f

                        drag(down.id) { change ->
                            val dragAmountX = change.position.x - initialTouchX
                            val dragAmountY = change.position.y - initialTouchY

                            totalDragX = dragAmountX
                            totalDragY = dragAmountY

                            val absDragX = abs(totalDragX)
                            val absDragY = abs(totalDragY)

                            if (!hasStartedHorizontalSwipe && !isDragging) {
                                if (absDragX > swipeDetectionThreshold || absDragY > verticalScrollThreshold) {
                                    if (absDragX > verticalScrollThreshold && absDragX > absDragY * directionRatio) {
                                        hasStartedHorizontalSwipe = true
                                        isDragging = true
                                    } else if (absDragY > swipeDetectionThreshold) {
                                        return@drag
                                    }
                                }
                            }

                            if (hasStartedHorizontalSwipe && isDragging) {
                                val newOffset = totalDragX
                                offsetX = newOffset.coerceIn(-maxSwipe, maxSwipe)
                                change.consume()
                            }
                        }

                        if (hasStartedHorizontalSwipe && isDragging) {
                            if (abs(offsetX) > replyThreshold && !isReplying) {
                                isReplying = true
                                onSwipeReply()
                            }
                        }

                        isDragging = false
                        hasStartedHorizontalSwipe = false
                        offsetX = 0f
                    }
                }
            }
            .combinedClickable(
                onClick = {
                    if (isSelectionMode) {
                        onTap()
                    }
                },
                onLongClick = {
                    if (abs(offsetX) < 10f && !isDragging) {
                        onLongPress()
                    }
                }
            )
            .then(
                if (isSelected) {
                    Modifier.background(
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                        RoundedCornerShape(8.dp)
                    )
                } else Modifier
            )
    ) {
        // Fixed: Content with proper width constraints
        content()

        // Reply indicator
        AnimatedVisibility(
            visible = abs(offsetX) > 10f && hasStartedHorizontalSwipe,
            enter = scaleIn() + fadeIn(),
            exit = scaleOut() + fadeOut(),
            modifier = Modifier.align(
                if (offsetX > 0) Alignment.CenterStart else Alignment.CenterEnd
            )
        ) {
            Box(
                modifier = Modifier
                    .padding(horizontal = 24.dp)
                    .size(40.dp)
                    .scale(replyScale)
                    .background(
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.2f * replyAlpha),
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.Reply,
                    contentDescription = "Reply",
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = replyAlpha),
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

@Composable
private fun EnhancedMessageFooter(
    message: Message,
    textColor: Color
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Timestamp
        Text(
            text = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(message.timestamp)),
            style = MaterialTheme.typography.labelSmall,
            color = textColor.copy(alpha = 0.7f)
        )

        // Status indicator for sent messages
        if (message.isFromMe) {
            Spacer(modifier = Modifier.width(4.dp))

            when (message.status) {
                MessageStatus.SENDING -> {
                    Icon(
                        Icons.Default.Schedule,
                        contentDescription = "Sending",
                        modifier = Modifier.size(12.dp),
                        tint = textColor.copy(alpha = 0.6f)
                    )
                }

                MessageStatus.SENT -> {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = "Sent",
                        modifier = Modifier.size(12.dp),
                        tint = textColor.copy(alpha = 0.8f)
                    )
                }

                MessageStatus.FAILED -> {
                    Icon(
                        Icons.Default.Error,
                        contentDescription = "Failed",
                        modifier = Modifier.size(12.dp),
                        tint = MaterialTheme.colorScheme.error
                    )
                }

                else -> {}
            }
        }
    }
}

@Composable
private fun PremiumSearchNavigationButtons(
    searchResults: List<Message>,
    currentSearchIndex: Int,
    searchQuery: String,
    onSearchPrevious: () -> Unit,
    onSearchNext: () -> Unit
) {
    val hasResults = searchResults.isNotEmpty()
    // Navigation logic - for newest-to-oldest order
    // currentSearchIndex 0 = newest message
    // currentSearchIndex (size-1) = oldest message
    val canNavigatePrevious =
        hasResults && currentSearchIndex < searchResults.size - 1 // Can go to older
    val canNavigateNext = hasResults && currentSearchIndex > 0 // Can go to newer

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // Results counter with premium styling
        AnimatedVisibility(
            visible = searchQuery.isNotEmpty(),
            enter = slideInHorizontally() + fadeIn(),
            exit = slideOutHorizontally() + fadeOut()
        ) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = if (hasResults && searchQuery.length >= 1) {
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                } else {
                    MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.6f)
                }
            ) {
                Text(
                    text = when {
                        searchQuery.isEmpty() -> "..."
                        hasResults -> "${currentSearchIndex + 1}/${searchResults.size}"
                        else -> "0"
                    },
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold
                    ),
                    color = if (hasResults && searchQuery.length >= 1) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onErrorContainer
                    },
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }

        // Up Button - Navigate to older messages (chronologically previous)
        PremiumSearchButton(
            icon = Icons.Default.KeyboardArrowUp,
            contentDescription = "Previous result (older)",
            enabled = canNavigatePrevious, // Active when can go to older messages
            onClick = onSearchNext // This goes to chronologically older messages
        )

        // Down Button - Navigate to newer messages (chronologically next)
        PremiumSearchButton(
            icon = Icons.Default.KeyboardArrowDown,
            contentDescription = "Next result (newer)",
            enabled = canNavigateNext, // Active when can go to newer messages
            onClick = onSearchPrevious // This goes to chronologically newer messages
        )
    }
}

@Composable
private fun PremiumSearchButton(
    icon: ImageVector,
    contentDescription: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    // Stable button sizing without animations that affect layout
    Box(
        modifier = Modifier
            .size(48.dp) // Fixed size
            .clip(CircleShape)
            .background(
                if (enabled) {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                } else {
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                }
            )
            .clickable(enabled = enabled) { if (enabled) onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            modifier = Modifier.size(24.dp), // Fixed icon size
            tint = if (enabled) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
            }
        )
    }
}

@Composable
private fun PremiumDisconnectionBanner() {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.15f),
        shadowElevation = 4.dp
    ) {
        Box(
            modifier = Modifier
                .background(
                    brush = Brush.horizontalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.1f),
                            MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.05f),
                            MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.1f)
                        )
                    )
                )
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.error.copy(alpha = 0.2f),
                    shape = RoundedCornerShape(20.dp)
                )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Animated warning indicator
                val infiniteTransition = rememberInfiniteTransition(label = "warning_pulse")
                val pulseAlpha by infiniteTransition.animateFloat(
                    initialValue = 0.5f,
                    targetValue = 1f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(1000, easing = EaseInOutSine),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "pulse"
                )

                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .background(
                            MaterialTheme.colorScheme.error.copy(alpha = 0.15f),
                            CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.SignalWifiOff,
                        contentDescription = "Disconnected",
                        modifier = Modifier
                            .size(18.dp)
                            .graphicsLayer { alpha = pulseAlpha },
                        tint = MaterialTheme.colorScheme.error
                    )
                }

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = "Device Disconnected",
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontWeight = FontWeight.Bold
                        ),
                        color = MaterialTheme.colorScheme.error
                    )
                    Text(
                        text = "Messages will be sent when reconnected",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f)
                    )
                }

                // Subtle "Queue" indicator
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.error.copy(alpha = 0.1f)
                ) {
                    Text(
                        text = "QUEUE",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold
                        ),
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun isSystemInDarkTheme(): Boolean {
    return androidx.compose.foundation.isSystemInDarkTheme()
}

private fun shouldReserveSpaceForTimestamp(text: String): Boolean {
    return text.length < 25 && !text.contains('\n')
}


private fun showDeleteDialog(
    context: Context,
    selectedCount: Int,
    onDeleteForMe: () -> Unit,
    onCancel: () -> Unit
) {
    val builder = android.app.AlertDialog.Builder(context)
    builder.setTitle("Delete Messages")
    builder.setMessage("Delete $selectedCount message${if (selectedCount > 1) "s" else ""}?")

    // Add options for delete for me vs delete for everyone
    val options = arrayOf("Delete for me", "Delete for everyone", "Cancel")

    builder.setItems(options) { dialog, which ->
        when (which) {
            0 -> {
                onDeleteForMe()
                dialog.dismiss()
            }

            1-> {
                onCancel()
                dialog.dismiss()
            }
        }
    }

    builder.setOnCancelListener {
        onCancel()
    }

    builder.create().show()
}
@Composable
private fun PremiumDeleteDialog(
    selectedCount: Int,
    onDeleteForMe: () -> Unit,
    onCancel: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = {
            Text(
                text = "Delete messages?",
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontWeight = FontWeight.Normal
                ),
                color = MaterialTheme.colorScheme.onSurface
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "$selectedCount message${if (selectedCount > 1) "s" else ""} will be deleted.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TextButton(
                    onClick = onCancel,
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Text("Cancel")
                }

                TextButton(
                    onClick = onDeleteForMe,
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Text("Delete for me")
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(16.dp)
    )
}


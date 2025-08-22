package com.bitchat.viewmodel

import android.annotation.SuppressLint
import android.app.Application
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.ContentValues.TAG
import android.content.Context
import android.net.Uri
import android.util.Log
import android.webkit.MimeTypeMap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.bitchat.bluetooth.BluetoothChatClient
import com.bitchat.model.Message
import com.bitchat.model.MessageType
import com.bitchat.model.MessageStatus
import com.bitchat.service.BluetoothService
import com.bitchat.service.ConnectionState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import kotlin.time.Duration.Companion.seconds

class ChatViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "ChatViewModel"
        private const val TYPING_TIMEOUT = 2000L // 2 seconds
    }

    // Current device context for chat
    private val _currentDeviceId = MutableStateFlow<String?>(null)
    val currentDeviceId = _currentDeviceId.asStateFlow()

    private val _currentDeviceName = MutableStateFlow<String?>(null)
    val currentDeviceName = _currentDeviceName.asStateFlow()

    // Messages for currently selected device
    val messages: StateFlow<List<Message>> = BluetoothService.messages

    // All messages from all devices
    val allMessages: StateFlow<Map<String, List<Message>>> = BluetoothService.allMessages

    // Enhanced connected devices tracking with proper client-service sync
    val connectedDevices: StateFlow<List<String>> = combine(
        BluetoothService.connectionState,
        BluetoothService.connectedDevices,
        flow {
            // Emit client connections periodically to keep sync
            while (true) {
                emit(client.getActiveConnections().keys.toList())
                delay(1.seconds)
            }
        }
    ) { connectionState, serviceConnectedDevices, clientConnectedDevices ->
        val allDevices = mutableSetOf<String>()

        // Add devices from service state (incoming connections)
        allDevices.addAll(serviceConnectedDevices)

        // Add devices from client connections (outgoing connections)
        allDevices.addAll(clientConnectedDevices)

        // Ensure current device is included if we have a connection
        _currentDeviceId.value?.let { deviceId ->
            if (client.isConnectedToDevice(deviceId)) {
                allDevices.add(deviceId)
            }
        }

        Log.d(TAG, "Connected devices updated: Service=$serviceConnectedDevices, Client=$clientConnectedDevices, Total=$allDevices")
        allDevices.toList()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Connection status with device context
    val connectionStatus: StateFlow<String> = combine(
        BluetoothService.connectionState,
        currentDeviceName,
        connectedDevices
    ) { state, deviceName, connected ->
        if (deviceName != null && _currentDeviceId.value?.let { connected.contains(it) } == true) {
            "Connected to $deviceName"
        } else when (state) {
            is ConnectionState.Connected -> "Connected to ${state.deviceName}"
            is ConnectionState.MultipleConnected -> "Connected to ${state.deviceNames.size} devices"
            is ConnectionState.Listening -> "Listening for connections"
            is ConnectionState.Error -> "Error: ${state.message}"
            else -> "Not connected"
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "Initializing...")

    // Typing indicator for current device
    val isFriendTyping: StateFlow<Boolean> = BluetoothService.currentDeviceTyping

    private val client = BluetoothChatClient(application)

    // Typing management
    private var isUserTyping = false
    private var typingJob: kotlinx.coroutines.Job? = null

    // Connection persistence map
    private val deviceConnectionAttempts = mutableMapOf<String, Int>()
    private val maxConnectionAttempts = 3

    // Enhanced Search Properties
    private val _isSearchMode = MutableStateFlow(false)
    val isSearchMode: StateFlow<Boolean> = _isSearchMode.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _searchResults = MutableStateFlow<List<Message>>(emptyList())
    val searchResults: StateFlow<List<Message>> = _searchResults.asStateFlow()

    private val _currentSearchIndex = MutableStateFlow(-1)
    val currentSearchIndex: StateFlow<Int> = _currentSearchIndex.asStateFlow()

    private val _highlightedMessageId = MutableStateFlow<String?>(null)
    val highlightedMessageId: StateFlow<String?> = _highlightedMessageId.asStateFlow()

    // Filtered messages based on search (keeping the existing implementation)
    val filteredMessages: StateFlow<List<Message>> = combine(
        messages,
        searchQuery
    ) { messageList, query ->
        if (query.isBlank()) {
            messageList
        } else {
            messageList.filter { message ->
                when (message.type) {
                    MessageType.TEXT -> message.content.contains(query, ignoreCase = true)
                    MessageType.DOCUMENT -> message.fileName?.contains(query, ignoreCase = true) ?: false
                    else -> false
                }
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        client.setOnMessageReceivedListener { messageText ->
            Log.d(TAG, "Client received message: $messageText")

            // Check if it's a delete command
            if (messageText.startsWith("DELETE_MESSAGE:")) {
                handleIncomingDeleteCommand(messageText)
                return@setOnMessageReceivedListener
            }

            // Messages from client connections are handled by the service
        }

        // Monitor connection states and attempt reconnection if needed
        monitorConnectionHealth()
    }

    private fun monitorConnectionHealth() {
        viewModelScope.launch {
            connectedDevices.collect { connected ->
                // Check if current device is still connected
                _currentDeviceId.value?.let { deviceId ->
                    if (!connected.contains(deviceId)) {
                        Log.w(TAG, "Current device $deviceId is no longer connected")
                        // Don't automatically clear current device - let user decide
                    }
                }
            }
        }
    }

    // Set current device for chat with connection verification
    fun setCurrentDevice(deviceId: String, deviceName: String) {
        _currentDeviceId.value = deviceId
        _currentDeviceName.value = deviceName
        BluetoothService.setCurrentDevice(deviceId)

        // Verify connection and attempt to connect if not connected
        viewModelScope.launch {
            if (!isConnectedToDevice(deviceId)) {
                Log.d(TAG, "Device $deviceName not connected, attempting to connect...")
                connectToDeviceByAddress(deviceId)
            }
        }

        Log.d(TAG, "Set current device: $deviceName ($deviceId)")
    }

    // Clear current device selection
    fun clearCurrentDevice() {
        _currentDeviceId.value = null
        _currentDeviceName.value = null
        BluetoothService.setCurrentDevice(null)
        Log.d(TAG, "Cleared current device selection")
    }

    // Enhanced device name resolution with proper caching
    @SuppressLint("MissingPermission")
    fun getDeviceName(deviceId: String): String {
        return try {
            // First check client connections
            client.getDeviceName(deviceId)?.let { return it }

            // Then check current device name
            if (_currentDeviceId.value == deviceId) {
                _currentDeviceName.value?.let { return it }
            }

            // Try to get the device name from BluetoothAdapter
            val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
            val device = bluetoothAdapter?.getRemoteDevice(deviceId)
            val deviceName = device?.name

            when {
                deviceName.isNullOrBlank() -> "Device ${deviceId.takeLast(8)}"
                else -> deviceName
            }
        } catch (e: SecurityException) {
            "Device ${deviceId.takeLast(8)}"
        } catch (e: Exception) {
            "Device ${deviceId.takeLast(8)}"
        }
    }

    // Enhanced connect to device with retry logic
    @SuppressLint("MissingPermission")
    fun connectToDevice(device: BluetoothDevice) {
        viewModelScope.launch {
            try {
                val actualDeviceName = try {
                    device.name?.takeIf { it.isNotBlank() } ?: "Device ${device.address.takeLast(8)}"
                } catch (e: SecurityException) {
                    "Device ${device.address.takeLast(8)}"
                }

                Log.d(TAG, "Attempting to connect to device: $actualDeviceName (${device.address})")

                // Check if already connected
                if (client.isConnectedToDevice(device.address)) {
                    Log.d(TAG, "Already connected to device: $actualDeviceName")
                    setCurrentDevice(device.address, actualDeviceName)
                    return@launch
                }

                // Reset connection attempts for new connection
                deviceConnectionAttempts[device.address] = 0

                val socket = client.connectToServer(device)
                if (socket != null) {
                    Log.d(TAG, "Successfully connected to device: $actualDeviceName")
                    setCurrentDevice(device.address, actualDeviceName)
                    deviceConnectionAttempts.remove(device.address) // Clear attempts on success
                } else {
                    Log.e(TAG, "Failed to connect to device: $actualDeviceName")
                    handleConnectionFailure(device.address)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error connecting to device", e)
                handleConnectionFailure(device.address)
            }
        }
    }

    // Enhanced connect by address with better error handling
    @SuppressLint("MissingPermission")
    fun connectToDeviceByAddress(deviceAddress: String) {
        viewModelScope.launch {
            try {
                Log.d(TAG, "Attempting to connect to device by address: $deviceAddress")

                if (client.isConnectedToDevice(deviceAddress)) {
                    Log.d(TAG, "Already connected to device: $deviceAddress")
                    val deviceName = getDeviceName(deviceAddress)
                    setCurrentDevice(deviceAddress, deviceName)
                    return@launch
                }

                val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
                if (bluetoothAdapter?.isEnabled == true) {
                    try {
                        val device = bluetoothAdapter.getRemoteDevice(deviceAddress)
                        if (device != null) {
                            connectToDevice(device)
                        }
                    } catch (e: IllegalArgumentException) {
                        Log.e(TAG, "Invalid device address: $deviceAddress", e)
                    } catch (e: SecurityException) {
                        Log.e(TAG, "Security exception accessing device: $deviceAddress", e)
                        // Still set the device info for UI purposes
                        setCurrentDevice(deviceAddress, "Device ${deviceAddress.takeLast(8)}")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error connecting to device by address: $deviceAddress", e)
                handleConnectionFailure(deviceAddress)
            }
        }
    }

    private fun handleConnectionFailure(deviceAddress: String) {
        val attempts = deviceConnectionAttempts.getOrDefault(deviceAddress, 0) + 1
        deviceConnectionAttempts[deviceAddress] = attempts

        if (attempts < maxConnectionAttempts) {
            Log.d(TAG, "Connection failed for $deviceAddress, attempt $attempts/$maxConnectionAttempts")
            // Retry after delay
            viewModelScope.launch {
                delay((2000 * attempts).toLong()) // Exponential backoff
                connectToDeviceByAddress(deviceAddress)
            }
        } else {
            Log.e(TAG, "Max connection attempts reached for $deviceAddress")
            deviceConnectionAttempts.remove(deviceAddress)
        }
    }

    // Send text message to current device
    fun sendMessage(msg: String) {
        val currentDevice = _currentDeviceId.value
        if (currentDevice == null) {
            Log.w(TAG, "Cannot send message: No device selected")
            return
        }

        viewModelScope.launch {
            try {
                if (isUserTyping) {
                    stopTyping()
                }

                // FIXED: Always create and add message to UI first
                val message = Message(
                    content = msg,
                    sender = "Me",
                    isFromMe = true,
                    type = MessageType.TEXT,
                    status = MessageStatus.PENDING // Start with PENDING status
                )

                // Add message to UI immediately
                BluetoothService.addMessageForDevice(currentDevice, message)
                Log.d(TAG, "Message added to UI for device $currentDevice: $msg")

                // Then attempt to send the message
                var connectionEstablished = isConnectedToDevice(currentDevice)

                if (!connectionEstablished) {
                    Log.w(TAG, "Device not connected, attempting to reconnect...")
                    // Update message status to show connection attempt
                    BluetoothService.updateMessageStateForDevice(
                        currentDevice,
                        message.timestamp,
                        MessageStatus.PENDING,
                        0f
                    )

                    connectToDeviceByAddress(currentDevice)
                    delay(2000L) // Wait for connection
                    connectionEstablished = isConnectedToDevice(currentDevice)
                }

                if (connectionEstablished) {
                    // Connection successful, attempt to send
                    val success = client.sendMessage(msg, currentDevice)
                    val finalStatus = if (success) MessageStatus.SENT else MessageStatus.FAILED

                    BluetoothService.updateMessageStateForDevice(
                        currentDevice,
                        message.timestamp,
                        finalStatus,
                        if (success) 1f else 0f
                    )

                    if (success) {
                        Log.d(TAG, "Text message sent successfully to device $currentDevice: $msg")
                    } else {
                        Log.e(TAG, "Failed to send message to device $currentDevice")
                    }
                } else {
                    // Connection failed, mark message as failed but keep it in UI
                    Log.e(TAG, "Failed to establish connection for sending message to device $currentDevice")
                    BluetoothService.updateMessageStateForDevice(
                        currentDevice,
                        message.timestamp,
                        MessageStatus.FAILED,
                        0f
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Exception while sending message to device $currentDevice", e)
                // Even if there's an exception, the message should remain visible as failed
            }
        }
    }

    // NEWLY ADDED: Send reply message to current device
    fun sendReplyMessage(messageText: String, replyToMessage: Message) {
        val currentDevice = _currentDeviceId.value
        if (currentDevice == null) {
            Log.w(TAG, "Cannot send reply: No device selected")
            return
        }

        viewModelScope.launch {
            try {
                if (isUserTyping) {
                    stopTyping()
                }

                // FIXED: Always create and add reply message to UI first
                val message = Message(
                    content = messageText,
                    sender = "Me",
                    isFromMe = true,
                    type = MessageType.TEXT,
                    status = MessageStatus.PENDING, // Start with PENDING status
                    replyToMessage = replyToMessage,
                    replyToMessageId = replyToMessage.id,
                    deviceAddress = currentDevice
                )

                // Add message to UI immediately
                BluetoothService.addMessageForDevice(currentDevice, message)
                Log.d(TAG, "Reply message added to UI for device $currentDevice: $messageText")

                // Then attempt to send the reply
                var connectionEstablished = isConnectedToDevice(currentDevice)

                if (!connectionEstablished) {
                    Log.w(TAG, "Device not connected for reply, attempting to reconnect...")
                    BluetoothService.updateMessageStateForDevice(
                        currentDevice,
                        message.timestamp,
                        MessageStatus.PENDING,
                        0f
                    )

                    connectToDeviceByAddress(currentDevice)
                    delay(2000L)
                    connectionEstablished = isConnectedToDevice(currentDevice)
                }

                if (connectionEstablished) {
                    // Create structured reply data
                    val replyData = JSONObject().apply {
                        put("type", "reply")
                        put("message", messageText)
                        put("replyTo", JSONObject().apply {
                            put("id", replyToMessage.id)
                            put("content", replyToMessage.content.take(100))
                            put("sender", replyToMessage.sender)
                            put("timestamp", replyToMessage.timestamp)
                            put("messageType", replyToMessage.type.name)
                        })
                    }

                    val success = client.sendMessage(replyData.toString(), currentDevice)
                    val finalStatus = if (success) MessageStatus.SENT else MessageStatus.FAILED

                    BluetoothService.updateMessageStateForDevice(
                        currentDevice,
                        message.timestamp,
                        finalStatus,
                        if (success) 1f else 0f
                    )

                    if (success) {
                        Log.d(TAG, "Reply sent successfully to device $currentDevice: $messageText")
                    } else {
                        Log.e(TAG, "Failed to send reply to device $currentDevice")
                    }
                } else {
                    // Connection failed, mark reply as failed but keep it in UI
                    Log.e(TAG, "Failed to establish connection for sending reply to device $currentDevice")
                    BluetoothService.updateMessageStateForDevice(
                        currentDevice,
                        message.timestamp,
                        MessageStatus.FAILED,
                        0f
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Exception while sending reply to device $currentDevice", e)
            }
        }
    }

    // Typing indicator management
    fun onTyping() {
        val currentDevice = _currentDeviceId.value
        if (currentDevice == null) return

        viewModelScope.launch {
            if (!isUserTyping) {
                isUserTyping = true
                sendTypingStatus(true)
                Log.d(TAG, "User started typing to device $currentDevice")
            }

            typingJob?.cancel()
            typingJob = launch {
                delay(TYPING_TIMEOUT)
                if (isUserTyping) {
                    stopTyping()
                }
            }
        }
    }

    fun onStoppedTyping() {
        viewModelScope.launch {
            if (isUserTyping) {
                typingJob?.cancel()
                stopTyping()
            }
        }
    }

    private fun stopTyping() {
        viewModelScope.launch {
            if (isUserTyping) {
                isUserTyping = false
                sendTypingStatus(false)
                Log.d(TAG, "User stopped typing")
            }
        }
    }

    private fun sendTypingStatus(isTyping: Boolean) {
        val currentDevice = _currentDeviceId.value ?: return

        viewModelScope.launch {
            try {
                val status = if (isTyping) "__TYPING__" else "__STOP_TYPING__"
                client.sendMessage(status, currentDevice)
                Log.d(TAG, "Typing status sent to $currentDevice: $status")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to send typing status to $currentDevice", e)
            }
        }
    }

    // Send file to current device with connection verification
    fun sendFile(uri: Uri, fileType: String) {
        val currentDevice = _currentDeviceId.value
        val deviceName = _currentDeviceName.value
        if (currentDevice == null) {
            Log.w(TAG, "Cannot send file: No device selected")
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            // Verify connection before sending
            if (!isConnectedToDevice(currentDevice)) {
                Log.w(TAG, "Device not connected for file transfer, attempting to reconnect...")
                connectToDeviceByAddress(currentDevice)
                delay(2000L) // Wait for connection

                if (!isConnectedToDevice(currentDevice)) {
                    Log.e(TAG, "Failed to establish connection for file transfer")
                    return@launch
                }
            }

            val context = getApplication<Application>().applicationContext
            Log.d(TAG, "Starting file send to device $deviceName ($currentDevice) - URI: $uri, type: $fileType")

            val localFile = copyUriToAppStorage(context, uri, fileType)
            if (localFile == null) {
                Log.e(TAG, "Failed to copy file to app storage")
                return@launch
            }

            val localUri = Uri.fromFile(localFile)
            val fileName = localFile.name
            val fileSize = localFile.length()
            val mimeType = getMimeType(context, uri, fileName)

            val message = when (fileType) {
                "image" -> Message(
                    content = fileName,
                    sender = "Me",
                    isFromMe = true,
                    type = MessageType.IMAGE,
                    imageUri = localUri.toString(),
                    status = MessageStatus.PENDING,
                    progress = 0f
                )
                "document" -> Message(
                    content = fileName,
                    sender = "Me",
                    isFromMe = true,
                    type = MessageType.DOCUMENT,
                    documentUri = localUri.toString(),
                    fileName = fileName,
                    fileSize = fileSize,
                    mimeType = mimeType,
                    status = MessageStatus.PENDING,
                    progress = 0f
                )
                else -> Message(
                    content = fileName,
                    sender = "Me",
                    isFromMe = true,
                    type = MessageType.DOCUMENT,
                    documentUri = localUri.toString(),
                    fileName = fileName,
                    fileSize = fileSize,
                    mimeType = mimeType,
                    status = MessageStatus.PENDING,
                    progress = 0f
                )
            }

            launch(Dispatchers.Main) {
                BluetoothService.addMessageForDevice(currentDevice, message)
                Log.d(TAG, "File message added to UI for device $currentDevice: $fileType")
            }

            delay(100L)

            try {
                Log.d(TAG, "Starting file transfer to device $deviceName...")
                val success = client.sendFile(localUri, fileType, currentDevice) { bytesSent, totalBytes ->
                    val newProgress = if (totalBytes > 0) (bytesSent.toFloat() / totalBytes.toFloat()) else 0f
                    updateMessageStateForDevice(
                        deviceId = currentDevice,
                        timestamp = message.timestamp,
                        newStatus = MessageStatus.SENDING,
                        newProgress = newProgress
                    )
                }

                val finalStatus = if (success) MessageStatus.SENT else MessageStatus.FAILED
                updateMessageStateForDevice(currentDevice, message.timestamp, finalStatus, 1f)

                Log.d(TAG, "File transfer to $deviceName completed with status: $finalStatus")
            } catch (e: Exception) {
                Log.e(TAG, "File transfer to $deviceName failed with exception", e)
                updateMessageStateForDevice(currentDevice, message.timestamp, MessageStatus.FAILED, 0f)
            }
        }
    }

    // Send audio file to current device
    fun sendAudioFile(audioFile: File) {
        val currentDevice = _currentDeviceId.value
        val deviceName = _currentDeviceName.value
        if (currentDevice == null) {
            Log.w(TAG, "Cannot send audio file: No device selected")
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            // Verify connection before sending
            if (!isConnectedToDevice(currentDevice)) {
                Log.w(TAG, "Device not connected for audio transfer, attempting to reconnect...")
                connectToDeviceByAddress(currentDevice)
                delay(2000L) // Wait for connection

                if (!isConnectedToDevice(currentDevice)) {
                    Log.e(TAG, "Failed to establish connection for audio transfer")
                    return@launch
                }
            }

            Log.d(TAG, "Starting audio file send to device $deviceName ($currentDevice): ${audioFile.absolutePath}")

            if (!audioFile.exists() || audioFile.length() == 0.toLong()) {
                Log.e(TAG, "Audio file does not exist or is empty")
                return@launch
            }

            val localUri = Uri.fromFile(audioFile)
            val fileName = audioFile.name
            val fileSize = audioFile.length()
            val mimeType = "audio/m4a"

            val message = Message(
                content = fileName,
                sender = "Me",
                isFromMe = true,
                type = MessageType.AUDIO,
                documentUri = localUri.toString(),
                fileName = fileName,
                fileSize = fileSize,
                mimeType = mimeType,
                status = MessageStatus.PENDING,
                progress = 0f
            )

            launch(Dispatchers.Main) {
                BluetoothService.addMessageForDevice(currentDevice, message)
                Log.d(TAG, "Audio message added to UI for device $currentDevice")
            }

            delay(100L)

            try {
                Log.d(TAG, "Starting audio file transfer to device $deviceName...")
                val success = client.sendFile(localUri, "audio", currentDevice) { bytesSent, totalBytes ->
                    val newProgress = if (totalBytes > 0) (bytesSent.toFloat() / totalBytes.toFloat()) else 0f
                    updateMessageStateForDevice(
                        deviceId = currentDevice,
                        timestamp = message.timestamp,
                        newStatus = MessageStatus.SENDING,
                        newProgress = newProgress
                    )
                }

                val finalStatus = if (success) MessageStatus.SENT else MessageStatus.FAILED
                updateMessageStateForDevice(currentDevice, message.timestamp, finalStatus, 1f)

                Log.d(TAG, "Audio file transfer to $deviceName completed with status: $finalStatus")
            } catch (e: Exception) {
                Log.e(TAG, "Audio file transfer to $deviceName failed with exception", e)
                updateMessageStateForDevice(currentDevice, message.timestamp, MessageStatus.FAILED, 0f)
            }
        }
    }

    // ================================
    // FIXED SEARCH NAVIGATION METHODS
    // ================================

    // Enhanced Search Methods
    fun toggleSearchMode() {
        _isSearchMode.value = !_isSearchMode.value
        if (!_isSearchMode.value) {
            clearSearch()
        }
        Log.d(TAG, "Search mode toggled: ${_isSearchMode.value}")
    }

    fun exitSearchMode() {
        _isSearchMode.value = false
        clearSearch()
        Log.d(TAG, "Search mode exited")
    }

    private fun clearSearch() {
        _searchQuery.value = ""
        _searchResults.value = emptyList()
        _currentSearchIndex.value = -1
        _highlightedMessageId.value = null
        Log.d(TAG, "Search cleared")
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
        if (query.length >= 2) {
            performSearch(query)
        } else {
            _searchResults.value = emptyList()
            _currentSearchIndex.value = -1
            _highlightedMessageId.value = null
        }
        Log.d(TAG, "Search query updated: $query")
    }

    private fun performSearch(query: String) {
        val currentDeviceId = _currentDeviceId.value ?: return
        val messages = allMessages.value[currentDeviceId] ?: emptyList()

        val results = messages.filter { message ->
            when (message.type) {
                MessageType.TEXT -> message.content.contains(query, ignoreCase = true)
                MessageType.DOCUMENT -> message.fileName?.contains(query, ignoreCase = true) == true
                else -> false
            }
        }.sortedByDescending { it.timestamp } // FIXED: Sort by newest first (descending order)

        _searchResults.value = results
        _currentSearchIndex.value = if (results.isNotEmpty()) 0 else -1
        _highlightedMessageId.value = results.firstOrNull()?.id

        Log.d(TAG, "Search performed for '$query': Found ${results.size} results")
    }

    // FIXED: Navigate to next search result (chronologically backward = older message)
    fun searchNext() {
        val results = _searchResults.value
        val currentIndex = _currentSearchIndex.value

        if (results.isEmpty()) {
            Log.d(TAG, "Search next: No results available")
            return
        }

        val newIndex = if (currentIndex < results.size - 1) {
            currentIndex + 1 // Move to next result (chronologically older)
        } else {
            0 // Wrap around to first result (newest)
        }

        _currentSearchIndex.value = newIndex
        _highlightedMessageId.value = results[newIndex].id

        Log.d(TAG, "Search next: Moving to result ${newIndex + 1} of ${results.size} (chronologically older)")
    }

    // FIXED: Navigate to previous search result (chronologically forward = newer message)
    fun searchPrevious() {
        val results = _searchResults.value
        val currentIndex = _currentSearchIndex.value

        if (results.isEmpty()) {
            Log.d(TAG, "Search previous: No results available")
            return
        }

        val newIndex = if (currentIndex > 0) {
            currentIndex - 1 // Move to previous result (chronologically newer)
        } else {
            results.size - 1 // Wrap around to last result (oldest)
        }

        _currentSearchIndex.value = newIndex
        _highlightedMessageId.value = results[newIndex].id

        Log.d(TAG, "Search previous: Moving to result ${newIndex + 1} of ${results.size} (chronologically newer)")
    }

    // Additional helper function to get current search status
    fun getCurrentSearchStatus(): String {
        val results = _searchResults.value
        val currentIndex = _currentSearchIndex.value

        return if (results.isEmpty()) {
            "No results"
        } else {
            "${currentIndex + 1} of ${results.size}"
        }
    }

    fun isCurrentSearchResult(messageId: String): Boolean {
        return _highlightedMessageId.value == messageId
    }

    // Jump to a specific search result by index
    fun jumpToSearchResult(index: Int) {
        val results = _searchResults.value
        if (index >= 0 && index < results.size) {
            _currentSearchIndex.value = index
            _highlightedMessageId.value = results[index].id
            Log.d(TAG, "Jumped to search result ${index + 1} of ${results.size}")
        }
    }

    // ================================
    // MESSAGE DELETION METHODS
    // ================================

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
        // Create a special command message that the other device can recognize
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

                    Log.d(TAG, "Deleted message $messageId from incoming delete command")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error handling delete command", e)
            }
        }
    }

    // ================================
    // EXISTING HELPER METHODS
    // ================================

    // Get messages for specific device
    fun getMessagesForDevice(deviceId: String): List<Message> {
        return BluetoothService.getMessagesForDevice(deviceId)
    }

    // Clear messages for specific device
    fun clearMessagesForDevice(deviceId: String) {
        BluetoothService.clearMessagesForDevice(deviceId)
        Log.d(TAG, "Cleared messages for device: $deviceId")
    }

    // Enhanced connection checking
    fun isConnectedToDevice(deviceId: String): Boolean {
        val clientConnected = client.isConnectedToDevice(deviceId)
        val serviceConnected = BluetoothService.connectedDevices.value.contains(deviceId)
        return clientConnected || serviceConnected
    }

    // Disconnect from specific device
    fun disconnectFromDevice(deviceId: String) {
        client.closeDeviceConnection(deviceId)
        deviceConnectionAttempts.remove(deviceId) // Clear retry attempts

        // If it's the current device, don't clear it immediately - let user decide
        if (_currentDeviceId.value == deviceId) {
            Log.d(TAG, "Current device $deviceId disconnected")
        }

        Log.d(TAG, "Disconnected from device: $deviceId")
    }

    // Force reconnect to current device
    fun reconnectToCurrentDevice() {
        _currentDeviceId.value?.let { deviceId ->
            Log.d(TAG, "Force reconnecting to current device: $deviceId")
            connectToDeviceByAddress(deviceId)
        }
    }

    // Get all device IDs that have messages
    fun getAllDeviceIds(): List<String> {
        return BluetoothService.getAllDeviceIds()
    }

    // Check if service is connected to device
    fun isDeviceConnectedToService(deviceId: String): Boolean {
        return BluetoothService.isDeviceConnectedToService(deviceId)
    }

    // Helper methods
    private fun getMimeType(context: Context, uri: Uri, fileName: String): String? {
        var mimeType = context.contentResolver.getType(uri)

        if (mimeType == null) {
            val extension = MimeTypeMap.getFileExtensionFromUrl(fileName)
            if (extension.isNotEmpty()) {
                mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension.lowercase())
            }
        }

        return mimeType
    }

    private fun updateMessageStateForDevice(
        deviceId: String,
        timestamp: Long,
        newStatus: MessageStatus,
        newProgress: Float
    ) {
        viewModelScope.launch(Dispatchers.Main) {
            BluetoothService.updateMessageStateForDevice(deviceId, timestamp, newStatus, newProgress)
            Log.d(TAG, "Message state updated for device $deviceId: $newStatus, progress: $newProgress")
        }
    }

    private fun copyUriToAppStorage(context: Context, uri: Uri, type: String): File? {
        return try {
            Log.d(TAG, "Copying URI to app storage: $uri")

            var fileName: String? = null
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (nameIndex >= 0) {
                        fileName = cursor.getString(nameIndex)
                    }
                }
            }

            if (fileName == null || fileName!!.isEmpty()) {
                val extension = when {
                    type == "image" -> "jpg"
                    uri.toString().contains(".pdf", ignoreCase = true) -> "pdf"
                    uri.toString().contains(".doc", ignoreCase = true) -> "doc"
                    uri.toString().contains(".txt", ignoreCase = true) -> "txt"
                    else -> "dat"
                }
                fileName = "sent_${System.currentTimeMillis()}.$extension"
            }

            val inputStream = context.contentResolver.openInputStream(uri)
            if (inputStream == null) {
                Log.e(TAG, "Could not open input stream for URI: $uri")
                return null
            }

            val file = File(context.filesDir, fileName!!)
            val outputStream = FileOutputStream(file)

            Log.d(TAG, "Copying file data...")
            val bytesCopied = inputStream.copyTo(outputStream)

            inputStream.close()
            outputStream.close()

            Log.d(TAG, "File copied successfully: ${file.absolutePath}, bytes: $bytesCopied")

            if (file.exists() && file.length() > 0) {
                file
            } else {
                Log.e(TAG, "Copied file is empty or doesn't exist")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to copy URI to app storage", e)
            null
        }
    }

    override fun onCleared() {
        super.onCleared()
        Log.d(TAG, "ViewModel cleared, closing client connections")
        if (isUserTyping) {
            viewModelScope.launch {
                sendTypingStatus(false)
            }
        }
        client.closeAllConnections()
        deviceConnectionAttempts.clear()
    }
}
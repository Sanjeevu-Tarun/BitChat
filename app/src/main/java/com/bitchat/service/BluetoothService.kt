package com.bitchat.service

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.IBinder
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import com.bitchat.R
import com.bitchat.model.Message
import com.bitchat.model.MessageType
import com.bitchat.model.MessageStatus
import com.bitchat.util.NotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class BluetoothService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var serverSocket: BluetoothServerSocket? = null

    // Multi-device connection management with enhanced state tracking
    private val activeConnections = ConcurrentHashMap<String, DeviceConnection>()
    private val connectionHealthJob = serviceScope.launch {
        while (isActive) {
            monitorConnectionHealth()
            delay(5000) // Check every 5 seconds
        }
    }

    private val bluetoothAdapter: BluetoothAdapter? by lazy {
        (getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
    }
    private val notificationManager by lazy {
        getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    }

    // Enhanced typing indicator management per device
    private val typingTimeoutJobs = ConcurrentHashMap<String, kotlinx.coroutines.Job>()
    private val TYPING_TIMEOUT = 3000L // 3 seconds timeout
    private val lastTypingReceived = ConcurrentHashMap<String, Long>()

    private enum class ReceiveState {
        READING_MESSAGES,
        READING_FILE_DATA
    }

    private data class FileTransferInfo(
        val fileName: String,
        val fileSize: Long,
        val fileType: String,
        val mimeType: String?
    )

    // Enhanced device connection wrapper with health monitoring
    private data class DeviceConnection(
        val deviceId: String,
        val deviceName: String,
        val socket: BluetoothSocket,
        var receiveState: ReceiveState = ReceiveState.READING_MESSAGES,
        var messageBuffer: ByteArrayOutputStream = ByteArrayOutputStream(),
        var fileOutputStream: FileOutputStream? = null,
        var currentFile: File? = null,
        var currentFileInfo: FileTransferInfo? = null,
        var expectedFileSize: Long = 0L,
        var receivedFileSize: Long = 0L,
        var isTyping: Boolean = false,
        var lastActivity: Long = System.currentTimeMillis(),
        var isHealthy: Boolean = true,
        var connectionJob: kotlinx.coroutines.Job? = null
    )

    // Connection health monitoring
    private suspend fun monitorConnectionHealth() {
        val currentTime = System.currentTimeMillis()
        val staleConnections = mutableListOf<String>()

        activeConnections.forEach { (deviceId, connection) ->
            try {
                // Check if connection is still alive
                if (!connection.socket.isConnected ||
                    currentTime - connection.lastActivity > 60000L || // 1 minute timeout
                    !connection.isHealthy) {
                    Log.w(TAG, "Stale connection detected for ${connection.deviceName}")
                    staleConnections.add(deviceId)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error checking connection health for ${connection.deviceName}", e)
                staleConnections.add(deviceId)
            }
        }

        // Clean up stale connections
        staleConnections.forEach { deviceId ->
            cleanupDeviceConnection(deviceId)
        }
    }

    // Enhanced message validation
    private fun isValidTextMessage(message: String): Boolean {
        val trimmed = message.trim()

        if (trimmed.isEmpty() || trimmed.length < 1) return false

        // Reject file transfer markers
        if (trimmed.contains("__EOF__") ||
            trimmed.startsWith("{\"type\":\"file_transfer") ||
            trimmed.contains("startxref") ||
            trimmed.contains("endobj")) {
            return false
        }

        // Check for binary data patterns
        val nonPrintableCount = trimmed.count { it.code < 32 && it != '\n' && it != '\r' && it != '\t' }
        if (nonPrintableCount > trimmed.length * 0.1) {
            return false
        }

        return true
    }

    private fun handleClient(socket: BluetoothSocket, deviceName: String) {
        val deviceId = socket.remoteDevice.address
        val connection = DeviceConnection(
            deviceId = deviceId,
            deviceName = deviceName,
            socket = socket
        )

        // Create connection handling job
        connection.connectionJob = serviceScope.launch {
            try {
                activeConnections[deviceId] = connection
                updateConnectionState()

                val inputStream = socket.inputStream
                val eofSignal = "__EOF__\n".toByteArray(Charsets.UTF_8)

                Log.d(TAG, "Started handling client: $deviceName ($deviceId)")

                val readBuffer = ByteArray(8192)
                while (socket.isConnected && isActive && connection.isHealthy) {
                    val bytesRead = try {
                        inputStream.read(readBuffer)
                    } catch (e: IOException) {
                        Log.e(TAG, "Error reading from input stream for $deviceName", e)
                        connection.isHealthy = false
                        break
                    }

                    if (bytesRead == -1) {
                        Log.d(TAG, "End of stream reached for $deviceName")
                        break
                    }

                    // Update activity timestamp
                    connection.lastActivity = System.currentTimeMillis()

                    val dataToProcess = readBuffer.copyOfRange(0, bytesRead)
                    Log.d(TAG, "Received $bytesRead bytes from $deviceName in state: ${connection.receiveState}")

                    when (connection.receiveState) {
                        ReceiveState.READING_MESSAGES -> {
                            val result = handleMessageData(dataToProcess, connection)
                            result?.let { fileInfo ->
                                connection.currentFileInfo = fileInfo
                                connection.currentFile = File(cacheDir, "received_${System.currentTimeMillis()}_${fileInfo.fileName}")
                                connection.fileOutputStream = FileOutputStream(connection.currentFile)
                                connection.expectedFileSize = fileInfo.fileSize
                                connection.receivedFileSize = 0L
                                connection.receiveState = ReceiveState.READING_FILE_DATA
                                Log.d(TAG, "Started receiving file from $deviceName: ${fileInfo.fileName}, size: ${fileInfo.fileSize}")
                            }
                        }

                        ReceiveState.READING_FILE_DATA -> {
                            val result = handleFileData(dataToProcess, eofSignal, connection)
                            if (result.fileComplete) {
                                connection.receiveState = ReceiveState.READING_MESSAGES
                                connection.currentFile = null
                                connection.currentFileInfo = null
                                connection.fileOutputStream?.close()
                                connection.fileOutputStream = null
                                connection.expectedFileSize = 0L
                                connection.receivedFileSize = 0L

                                if (result.leftoverData.isNotEmpty()) {
                                    connection.messageBuffer.write(result.leftoverData)
                                    val fileInfo = processCompleteMessages(connection)
                                    if (fileInfo != null) {
                                        connection.currentFileInfo = fileInfo
                                        connection.currentFile = File(cacheDir, "received_${System.currentTimeMillis()}_${fileInfo.fileName}")
                                        connection.fileOutputStream = FileOutputStream(connection.currentFile)
                                        connection.expectedFileSize = fileInfo.fileSize
                                        connection.receivedFileSize = 0L
                                        connection.receiveState = ReceiveState.READING_FILE_DATA
                                    }
                                }
                            } else {
                                connection.receivedFileSize = result.newReceivedSize
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Client disconnected or error in handleClient for $deviceName.", e)
                connection.isHealthy = false
            } finally {
                cleanupDeviceConnection(deviceId)
                try {
                    socket.close()
                } catch (e: Exception) {
                    Log.e(TAG, "Error closing socket for $deviceName", e)
                }
                Log.d(TAG, "Client handler finished for: $deviceName ($deviceId)")
            }
        }
    }

    private fun cleanupDeviceConnection(deviceId: String) {
        val connection = activeConnections[deviceId]
        connection?.let {
            // Cancel connection job
            it.connectionJob?.cancel()

            // Clean up typing indicators
            setDeviceTyping(deviceId, false)
            typingTimeoutJobs[deviceId]?.cancel()
            typingTimeoutJobs.remove(deviceId)
            lastTypingReceived.remove(deviceId)

            // Clean up file operations
            try {
                it.fileOutputStream?.close()
                it.messageBuffer.close()
            } catch (e: Exception) {
                Log.e(TAG, "Error cleaning up streams for ${it.deviceName}", e)
            }

            // Remove from active connections
            activeConnections.remove(deviceId)
            updateConnectionState()

            Log.d(TAG, "Cleaned up connection for device: ${it.deviceName} ($deviceId)")
        }
    }

    private fun handleMessageData(dataToProcess: ByteArray, connection: DeviceConnection): FileTransferInfo? {
        connection.messageBuffer.write(dataToProcess)
        return processCompleteMessages(connection)
    }

    private fun processCompleteMessages(connection: DeviceConnection): FileTransferInfo? {
        val data = connection.messageBuffer.toByteArray()
        val text = String(data, Charsets.UTF_8)
        val lines = text.split('\n')
        var processedBytes = 0

        for (i in 0 until lines.size - 1) {
            val line = lines[i]
            val lineBytes = (line + '\n').toByteArray(Charsets.UTF_8)
            processedBytes += lineBytes.size

            if (line.startsWith("{\"type\":\"file_transfer_v2\"")) {
                try {
                    val header = JSONObject(line)
                    val fileName = header.getString("fileName")
                    val fileSize = header.optLong("fileSize", -1)
                    val fileType = header.optString("fileType", "document")
                    val mimeType = header.optString("mimeType", null)

                    val remaining = data.copyOfRange(processedBytes, data.size)
                    connection.messageBuffer.reset()
                    connection.messageBuffer.write(remaining)

                    Log.d(TAG, "File transfer header parsed from ${connection.deviceName}: $fileName, size: $fileSize")
                    return FileTransferInfo(fileName, fileSize, fileType, mimeType)
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing file transfer header from ${connection.deviceName}", e)
                }
            } else if (isValidTextMessage(line)) {
                processTextMessage(line, connection)
            }
        }

        if (processedBytes > 0) {
            val remaining = data.copyOfRange(processedBytes, data.size)
            connection.messageBuffer.reset()
            connection.messageBuffer.write(remaining)
        }

        return null
    }

    private data class FileHandleResult(
        val fileComplete: Boolean,
        val newReceivedSize: Long,
        val leftoverData: ByteArray
    )

    private fun handleFileData(dataToProcess: ByteArray, eofSignal: ByteArray, connection: DeviceConnection): FileHandleResult {
        val eofIndex = findIndexOf(dataToProcess, dataToProcess.size, eofSignal)

        if (eofIndex != -1) {
            if (eofIndex > 0) {
                connection.fileOutputStream?.write(dataToProcess, 0, eofIndex)
                connection.fileOutputStream?.flush()
            }

            connection.fileOutputStream?.close()

            connection.currentFile?.let { file ->
                if (file.exists() && file.length() > 0) {
                    Log.d(TAG, "File received successfully from ${connection.deviceName}: ${file.name}, size: ${file.length()}")
                    addReceivedFileMessage(file, connection.currentFileInfo, connection.deviceId, connection.deviceName)
                } else {
                    Log.e(TAG, "File transfer failed or file is empty from ${connection.deviceName}: ${file.name}")
                }
            }

            val leftoverOffset = eofIndex + eofSignal.size
            val leftoverData = if (leftoverOffset < dataToProcess.size) {
                dataToProcess.copyOfRange(leftoverOffset, dataToProcess.size)
            } else {
                ByteArray(0)
            }

            return FileHandleResult(true, 0L, leftoverData)
        } else {
            connection.fileOutputStream?.write(dataToProcess, 0, dataToProcess.size)
            connection.fileOutputStream?.flush()

            val newReceivedSize = connection.receivedFileSize + dataToProcess.size
            Log.d(TAG, "File progress from ${connection.deviceName}: $newReceivedSize / ${connection.expectedFileSize} bytes")

            return FileHandleResult(false, newReceivedSize, ByteArray(0))
        }
    }

    private fun addReceivedFileMessage(file: File, fileInfo: FileTransferInfo?, deviceId: String, deviceName: String) {
        try {
            val messageType = when (fileInfo?.fileType) {
                "image" -> MessageType.IMAGE
                "audio" -> MessageType.AUDIO
                "document" -> MessageType.DOCUMENT
                else -> MessageType.DOCUMENT
            }

            val fileUri = Uri.fromFile(file).toString()

            val newMessage = when (messageType) {
                MessageType.IMAGE -> Message(
                    content = file.name,
                    sender = deviceName,
                    isFromMe = false,
                    type = MessageType.IMAGE,
                    imageUri = fileUri,
                    status = MessageStatus.SENT
                )
                MessageType.AUDIO -> Message(
                    content = fileInfo?.fileName ?: file.name,
                    sender = deviceName,
                    isFromMe = false,
                    type = MessageType.AUDIO,
                    documentUri = fileUri,
                    fileName = fileInfo?.fileName ?: file.name,
                    fileSize = file.length(),
                    mimeType = fileInfo?.mimeType,
                    status = MessageStatus.SENT
                )
                else -> Message(
                    content = fileInfo?.fileName ?: file.name,
                    sender = deviceName,
                    isFromMe = false,
                    type = MessageType.DOCUMENT,
                    documentUri = fileUri,
                    fileName = fileInfo?.fileName ?: file.name,
                    fileSize = file.length(),
                    mimeType = fileInfo?.mimeType,
                    status = MessageStatus.SENT
                )
            }

            addMessageForDevice(deviceId, newMessage)

            val notificationText = when (messageType) {
                MessageType.IMAGE -> "Received an image from $deviceName"
                MessageType.AUDIO -> "Received an audio message from $deviceName"
                MessageType.DOCUMENT -> "Received a document from $deviceName: ${newMessage.fileName}"
                else -> "Received a file from $deviceName"
            }
            NotificationHelper.showMessageNotification(applicationContext, notificationText, deviceId, deviceName)
            Log.d(TAG, "File message added successfully from $deviceName: ${file.name}")
        } catch (e: Exception) {
            Log.e(TAG, "Error creating file message from $deviceName", e)
        }
    }

    private fun processRegularTextMessage(message: String, connection: DeviceConnection) {
        serviceScope.launch {
            setDeviceTyping(connection.deviceId, false)
            typingTimeoutJobs[connection.deviceId]?.cancel()

            val newMessage = Message(
                content = message,
                sender = connection.deviceName,
                isFromMe = false,
                type = MessageType.TEXT,
                status = MessageStatus.SENT,
                deviceAddress = connection.deviceId
            )

            addMessageForDevice(connection.deviceId, newMessage)
            NotificationHelper.showMessageNotification(
                applicationContext,
                "Message from ${connection.deviceName}: ${newMessage.content}",
                connection.deviceId,
                connection.deviceName
            )
            Log.d(TAG, "Text message received from ${connection.deviceName}: ${message.take(100)}")
        }
    }
    private fun processTextMessage(message: String, connection: DeviceConnection) {
        val trimmedMessage = message.trim()
        if (trimmedMessage.isBlank()) return

        val deviceId = connection.deviceId
        val deviceName = connection.deviceName

        // Update activity on any message
        connection.lastActivity = System.currentTimeMillis()

        when {
            trimmedMessage == "__TYPING__" -> {
                // Handle typing indicator
                serviceScope.launch {
                    lastTypingReceived[deviceId] = System.currentTimeMillis()
                    setDeviceTyping(deviceId, true)
                    // ... existing typing logic
                }
            }
            trimmedMessage == "__STOP_TYPING__" -> {
                // Handle stop typing
                serviceScope.launch {
                    setDeviceTyping(deviceId, false)
                    typingTimeoutJobs[deviceId]?.cancel()
                }
            }
            trimmedMessage == "__HEARTBEAT__" -> {
                // Handle heartbeat
                Log.v(TAG, "Heartbeat received from $deviceName")
            }
            trimmedMessage.startsWith("{\"type\":\"reply\"") -> {
                // FIXED: Handle structured reply messages
                try {
                    val replyJson = JSONObject(trimmedMessage)
                    val messageContent = replyJson.getString("message")
                    val replyToJson = replyJson.getJSONObject("replyTo")

                    // Reconstruct the original message being replied to
                    val originalMessage = Message(
                        id = replyToJson.getString("id"),
                        content = replyToJson.getString("content"),
                        sender = replyToJson.getString("sender"),
                        timestamp = replyToJson.getLong("timestamp"),
                        type = MessageType.valueOf(replyToJson.getString("messageType")),
                        isFromMe = replyToJson.getString("sender") == "Me"
                    )

                    val newMessage = Message(
                        content = messageContent,
                        sender = deviceName,
                        isFromMe = false,
                        type = MessageType.TEXT,
                        status = MessageStatus.SENT,
                        replyToMessage = originalMessage,
                        replyToMessageId = originalMessage.id,
                        deviceAddress = deviceId
                    )

                    addMessageForDevice(deviceId, newMessage)
                    NotificationHelper.showMessageNotification(
                        applicationContext,
                        "Reply from $deviceName: $messageContent",
                        deviceId,
                        deviceName
                    )
                    Log.d(TAG, "Reply message received from $deviceName: ${messageContent.take(100)}")
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing reply message from $deviceName", e)
                    // Fallback to regular message
                    processRegularTextMessage(trimmedMessage, connection)
                }
            }
            trimmedMessage.startsWith("DELETE_MESSAGE:") -> {
                // Handle delete command from remote device
                try {
                    val messageId = trimmedMessage.substringAfter("DELETE_MESSAGE:")
                    removeMessageForDevice(deviceId, messageId)
                    Log.d(TAG, "Deleted message $messageId from delete command from $deviceName")
                } catch (e: Exception) {
                    Log.e(TAG, "Error handling delete command from $deviceName", e)
                }
            }
            else -> {
                // Handle regular text messages
                processRegularTextMessage(trimmedMessage, connection)
            }
        }
    }
    private fun setDeviceTyping(deviceId: String, isTyping: Boolean) {
        activeConnections[deviceId]?.let { connection ->
            connection.isTyping = isTyping

            val anyDeviceTyping = activeConnections.values.any { it.isTyping }
            _isFriendTyping.value = anyDeviceTyping

            if (deviceId == _currentDeviceId.value) {
                _currentDeviceTyping.value = isTyping
            }
        }
    }

    private fun findIndexOf(data: ByteArray, dataLength: Int, pattern: ByteArray, startIndex: Int = 0): Int {
        if (pattern.isEmpty() || startIndex >= dataLength) return -1

        for (i in startIndex..dataLength - pattern.size) {
            var found = true
            for (j in pattern.indices) {
                if (i + j >= dataLength || data[i + j] != pattern[j]) {
                    found = false
                    break
                }
            }
            if (found) return i
        }
        return -1
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "BluetoothService created")
        observeConnectionState()
        startForeground(NOTIFICATION_ID, createNotification("Initializing..."))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "BluetoothService onStartCommand")
        if (serverSocket == null) {
            startServer()
        }
        return START_STICKY
    }

    private fun startServer() {
        if (serverSocket != null) {
            Log.d(TAG, "Server already running")
            return
        }

        serviceScope.launch {
            // Check permissions first
            if (ActivityCompat.checkSelfPermission(this@BluetoothService, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                _connectionState.value = ConnectionState.Error("BLUETOOTH_CONNECT permission not granted")
                Log.e(TAG, "BLUETOOTH_CONNECT permission not granted")
                return@launch
            }

            if (bluetoothAdapter?.isEnabled != true) {
                _connectionState.value = ConnectionState.Error("Bluetooth not enabled")
                Log.e(TAG, "Bluetooth not enabled")
                return@launch
            }

            try {
                serverSocket = bluetoothAdapter?.listenUsingRfcommWithServiceRecord("BitChat", SERVICE_UUID)
                _connectionState.value = ConnectionState.Listening
                Log.d(TAG, "Bluetooth server started, listening for connections...")

                while (isActive && serverSocket != null) {
                    val socket = try {
                        serverSocket?.accept()
                    } catch (e: IOException) {
                        if (isActive) {
                            Log.e(TAG, "Server socket was closed or accept failed.", e)
                        }
                        break
                    }

                    if (socket != null) {
                        val deviceName = try {
                            socket.remoteDevice.name ?: "Unknown Device"
                        } catch (e: SecurityException) {
                            "Unknown Device"
                        }
                        val deviceId = socket.remoteDevice.address
                        Log.d(TAG, "Connection accepted from: $deviceName ($deviceId)")

                        handleClient(socket, deviceName)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error starting server", e)
                _connectionState.value = ConnectionState.Error("Failed to start server: ${e.message}")
            }
        }
    }

    private fun updateConnectionState() {
        val connectedDevices = activeConnections.values.map { it.deviceName }
        _connectedDevices.value = activeConnections.keys.toList()

        _connectionState.value = when {
            connectedDevices.isEmpty() -> ConnectionState.Listening
            connectedDevices.size == 1 -> ConnectionState.Connected(connectedDevices.first())
            else -> ConnectionState.MultipleConnected(connectedDevices)
        }

        Log.d(TAG, "Connection state updated: ${connectedDevices.size} devices connected")
    }

    private fun observeConnectionState() {
        connectionState
            .onEach { state ->
                val notificationText = when (state) {
                    is ConnectionState.Connected -> "Connected to ${state.deviceName}"
                    is ConnectionState.MultipleConnected -> "Connected to ${state.deviceNames.size} devices"
                    is ConnectionState.Listening -> "Listening for connections"
                    is ConnectionState.Error -> "Connection error: ${state.message}"
                    is ConnectionState.Idle -> "Idle"
                }
                updateNotification(notificationText)
            }
            .launchIn(serviceScope)
    }

    private fun updateNotification(contentText: String) {
        val notification = createNotification(contentText)
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    private fun createNotification(contentText: String): Notification {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "BitChat Background Service",
            NotificationManager.IMPORTANCE_LOW
        )
        notificationManager.createNotificationChannel(channel)
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("BitChat is Active")
            .setContentText(contentText)
            .setSmallIcon(R.drawable.ic_bluetooth)
            .setOngoing(true)
            .build()
    }

    // Public method to check if device is connected via service
    fun isDeviceConnected(deviceId: String): Boolean {
        return activeConnections.containsKey(deviceId) &&
                activeConnections[deviceId]?.socket?.isConnected == true
    }

    // Public method to get connected device names for external use
    fun getConnectedDeviceNames(): Map<String, String> {
        return activeConnections.mapValues { it.value.deviceName }
    }

    // Public method to force disconnect a device
    fun forceDisconnectDevice(deviceId: String) {
        Log.d(TAG, "Force disconnecting device: $deviceId")
        activeConnections[deviceId]?.let { connection ->
            connection.isHealthy = false
            connection.connectionJob?.cancel()
            cleanupDeviceConnection(deviceId)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "BluetoothService destroyed.")

        // Cancel connection health monitoring
        connectionHealthJob.cancel()

        try {
            serverSocket?.close()
            serverSocket = null

            // Clean up all connections
            activeConnections.values.forEach { connection ->
                connection.isHealthy = false
                connection.connectionJob?.cancel()
                try {
                    connection.socket.close()
                } catch (e: IOException) {
                    Log.e(TAG, "Error closing socket for ${connection.deviceName}", e)
                }
            }
            activeConnections.clear()
            updateConnectionState()
        } catch (e: IOException) {
            Log.e(TAG, "Error closing server socket", e)
        }

        // Clean up typing jobs
        typingTimeoutJobs.values.forEach { it.cancel() }
        typingTimeoutJobs.clear()
        lastTypingReceived.clear()

        serviceScope.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val TAG = "BluetoothService"
        private const val CHANNEL_ID = "BitChatChannel"
        private const val NOTIFICATION_ID = 1001
        val SERVICE_UUID: UUID = UUID.fromString("fa87c0d0-afac-11de-8a39-0800200c9a66")

        // Device-specific message storage
        private val _allMessages = MutableStateFlow<Map<String, List<Message>>>(emptyMap())
        val allMessages = _allMessages.asStateFlow()

        // Current device selection for UI
        private val _currentDeviceId = MutableStateFlow<String?>(null)
        val currentDeviceId = _currentDeviceId.asStateFlow()

        // Messages for currently selected device
        private val _messages = MutableStateFlow<List<Message>>(emptyList())
        val messages = _messages.asStateFlow()

        // Connection state
        private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Idle)
        val connectionState = _connectionState.asStateFlow()

        // Typing indicators
        private val _isFriendTyping = MutableStateFlow(false)
        val isFriendTyping = _isFriendTyping.asStateFlow()

        private val _currentDeviceTyping = MutableStateFlow(false)
        val currentDeviceTyping = _currentDeviceTyping.asStateFlow()

        // Connected devices list
        private val _connectedDevices = MutableStateFlow<List<String>>(emptyList())
        val connectedDevices = _connectedDevices.asStateFlow()

        fun setCurrentDevice(deviceId: String?) {
            _currentDeviceId.value = deviceId
            deviceId?.let { id ->
                _messages.value = _allMessages.value[id] ?: emptyList()
                Log.d(TAG, "Set current device: $id, messages: ${_messages.value.size}")
            } ?: run {
                _messages.value = emptyList()
                Log.d(TAG, "Cleared current device")
            }
        }

        fun addMessageForDevice(deviceId: String, message: Message) {
            val currentMessages = _allMessages.value.toMutableMap()
            val deviceMessages = currentMessages[deviceId]?.toMutableList() ?: mutableListOf()
            deviceMessages.add(message)
            currentMessages[deviceId] = deviceMessages
            _allMessages.value = currentMessages

            if (_currentDeviceId.value == deviceId) {
                _messages.value = deviceMessages
            }

            Log.d(TAG, "Message added for device $deviceId: ${message.type}, content: ${message.content.take(50)}")
        }

        fun updateMessageStateForDevice(deviceId: String, timestamp: Long, newStatus: MessageStatus, newProgress: Float) {
            val currentMessages = _allMessages.value.toMutableMap()
            val deviceMessages = currentMessages[deviceId]?.toMutableList()

            deviceMessages?.let { messages ->
                val messageIndex = messages.indexOfFirst { it.timestamp == timestamp }
                if (messageIndex != -1) {
                    val updatedMessage = messages[messageIndex].copy(
                        status = newStatus,
                        progress = newProgress
                    )
                    messages[messageIndex] = updatedMessage
                    currentMessages[deviceId] = messages
                    _allMessages.value = currentMessages

                    if (_currentDeviceId.value == deviceId) {
                        _messages.value = messages
                    }

                    Log.d(TAG, "Message status updated for device $deviceId: $newStatus, progress: $newProgress")
                }
            }
        }

        // NEW: Set messages for a device (replaces entire message list)
        fun setMessagesForDevice(deviceId: String, messages: List<Message>) {
            val currentMessages = _allMessages.value.toMutableMap()
            currentMessages[deviceId] = messages
            _allMessages.value = currentMessages

            if (_currentDeviceId.value == deviceId) {
                _messages.value = messages
            }

            Log.d(TAG, "Messages set for device $deviceId: ${messages.size} messages")
        }

        // NEW: Remove specific messages by IDs for a device
        fun removeMessagesForDevice(deviceId: String, messageIds: Set<String>) {
            val currentMessages = _allMessages.value.toMutableMap()
            val deviceMessages = currentMessages[deviceId]?.toMutableList()

            deviceMessages?.let { messages ->
                val updatedMessages = messages.filterNot { messageIds.contains(it.id) }
                currentMessages[deviceId] = updatedMessages
                _allMessages.value = currentMessages

                if (_currentDeviceId.value == deviceId) {
                    _messages.value = updatedMessages
                }

                Log.d(TAG, "Removed ${messageIds.size} messages for device $deviceId")
            }
        }

        // NEW: Remove a single message by ID for a device
        fun removeMessageForDevice(deviceId: String, messageId: String) {
            val currentMessages = _allMessages.value.toMutableMap()
            val deviceMessages = currentMessages[deviceId]?.toMutableList()

            deviceMessages?.let { messages ->
                val updatedMessages = messages.filterNot { it.id == messageId }
                currentMessages[deviceId] = updatedMessages
                _allMessages.value = currentMessages

                if (_currentDeviceId.value == deviceId) {
                    _messages.value = updatedMessages
                }

                Log.d(TAG, "Removed message $messageId for device $deviceId")
            }
        }

        fun getMessagesForDevice(deviceId: String): List<Message> {
            return _allMessages.value[deviceId] ?: emptyList()
        }

        fun clearMessagesForDevice(deviceId: String) {
            val currentMessages = _allMessages.value.toMutableMap()
            currentMessages.remove(deviceId)
            _allMessages.value = currentMessages

            if (_currentDeviceId.value == deviceId) {
                _messages.value = emptyList()
            }

            Log.d(TAG, "Cleared messages for device: $deviceId")
        }

        fun getAllDeviceIds(): List<String> {
            return _allMessages.value.keys.toList()
        }

        // Method to get connection status for external components
        fun isDeviceConnectedToService(deviceId: String): Boolean {
            return _connectedDevices.value.contains(deviceId)
        }
    }
}

sealed interface ConnectionState {
    data object Idle : ConnectionState
    data object Listening : ConnectionState
    data class Connected(val deviceName: String) : ConnectionState
    data class MultipleConnected(val deviceNames: List<String>) : ConnectionState
    data class Error(val message: String) : ConnectionState
}
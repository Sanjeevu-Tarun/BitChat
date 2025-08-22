package com.bitchat.bluetooth

import android.Manifest
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import android.util.Log
import android.webkit.MimeTypeMap
import androidx.core.content.ContextCompat
import com.bitchat.service.BluetoothService
import kotlinx.coroutines.*
import org.json.JSONObject
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.*
import java.util.concurrent.ConcurrentHashMap

class BluetoothChatClient(private val context: Context) {

    // Multi-device connection management with persistence
    private val activeConnections = ConcurrentHashMap<String, ClientConnection>()
    private val connectionAttempts = ConcurrentHashMap<String, Int>()
    private val reconnectionJobs = ConcurrentHashMap<String, Job>()
    private val coroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // Connection persistence and health monitoring
    private val connectionHealthJob = coroutineScope.launch {
        while (isActive) {
            monitorConnectionHealth()
            delay(5000) // Check every 5 seconds
        }
    }

    private data class ClientConnection(
        val deviceId: String,
        val deviceName: String,
        val socket: BluetoothSocket,
        val inputStream: InputStream,
        val outputStream: OutputStream,
        var isListening: Boolean = false,
        var listenJob: Job? = null,
        var lastActivity: Long = System.currentTimeMillis(),
        var connectionStable: Boolean = true
    )

    private var onMessageReceived: ((String) -> Unit)? = null

    companion object {
        private const val TAG = "BluetoothChatClient"
        private const val MAX_RECONNECTION_ATTEMPTS = 5
        private const val RECONNECTION_DELAY_BASE = 2000L // Base delay in ms
        private const val CONNECTION_TIMEOUT = 10000L // 10 seconds
        private const val HEARTBEAT_INTERVAL = 30000L // 30 seconds
    }

    fun setOnMessageReceivedListener(listener: (String) -> Unit) {
        onMessageReceived = listener
    }

    private suspend fun monitorConnectionHealth() {
        val currentTime = System.currentTimeMillis()
        val staleConnections = mutableListOf<String>()

        activeConnections.forEach { (deviceId, connection) ->
            try {
                // Check if connection is truly alive
                if (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
                        !connection.socket.isConnected ||
                            currentTime - connection.lastActivity > HEARTBEAT_INTERVAL * 2
                    } else {
                        TODO("VERSION.SDK_INT < ICE_CREAM_SANDWICH")
                    }
                ) {
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
            cleanupConnection(deviceId)
        }
    }

    private fun cleanupConnection(deviceId: String) {
        activeConnections[deviceId]?.let { connection ->
            Log.d(TAG, "Cleaning up connection for ${connection.deviceName}")
            connection.isListening = false
            connection.listenJob?.cancel()

            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ECLAIR) {
                    connection.socket.close()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error closing socket for ${connection.deviceName}", e)
            }

            activeConnections.remove(deviceId)
        }

        reconnectionJobs[deviceId]?.cancel()
        reconnectionJobs.remove(deviceId)
    }

    suspend fun connectToServer(device: BluetoothDevice): BluetoothSocket? =
        withContext(Dispatchers.IO) {
            val deviceId = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ECLAIR) {
                device.address
            } else {
                TODO("VERSION.SDK_INT < ECLAIR")
            }
            val deviceName = try {
                device.name ?: "Device ${deviceId.takeLast(8)}"
            } catch (e: SecurityException) {
                "Device ${deviceId.takeLast(8)}"
            }

            // Check if already connected and healthy
            activeConnections[deviceId]?.let { existingConnection ->
                if (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
                        existingConnection.socket.isConnected && existingConnection.connectionStable
                    } else {
                        TODO("VERSION.SDK_INT < ICE_CREAM_SANDWICH")
                    }
                ) {
                    Log.d(TAG, "Already connected to $deviceName, reusing connection")
                    return@withContext existingConnection.socket
                } else {
                    Log.d(TAG, "Existing connection to $deviceName is unhealthy, reconnecting")
                    cleanupConnection(deviceId)
                }
            }

            try {
                // Check permissions
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    val permission = ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.BLUETOOTH_CONNECT
                    )
                    if (permission != PackageManager.PERMISSION_GRANTED) {
                        throw SecurityException("BLUETOOTH_CONNECT permission is not granted")
                    }
                }

                Log.d(TAG, "Attempting to connect to $deviceName ($deviceId)")

                // Create socket with timeout
                val bluetoothSocket = device.createRfcommSocketToServiceRecord(BluetoothService.SERVICE_UUID)

                // Connect with timeout
                withTimeout(CONNECTION_TIMEOUT) {
                    bluetoothSocket.connect()
                }

                val inputStream = bluetoothSocket.inputStream
                val outputStream = bluetoothSocket.outputStream

                val connection = ClientConnection(
                    deviceId = deviceId,
                    deviceName = deviceName,
                    socket = bluetoothSocket,
                    inputStream = inputStream,
                    outputStream = outputStream,
                    lastActivity = System.currentTimeMillis()
                )

                activeConnections[deviceId] = connection
                connectionAttempts.remove(deviceId) // Reset attempts on success

                Log.d(TAG, "Successfully connected to $deviceName ($deviceId)")

                // Start listening for messages from this device
                startListeningForDevice(connection)

                // Send initial heartbeat
                sendHeartbeat(connection)

                bluetoothSocket
            } catch (e: Exception) {
                Log.e(TAG, "Failed to connect to device $deviceName", e)
                scheduleReconnection(deviceId, deviceName)
                null
            }
        }

    private fun scheduleReconnection(deviceId: String, deviceName: String) {
        val attempts = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            connectionAttempts.getOrDefault(deviceId, 0) + 1
        } else {
            TODO("VERSION.SDK_INT < N")
        }
        connectionAttempts[deviceId] = attempts

        if (attempts <= MAX_RECONNECTION_ATTEMPTS) {
            val delay = RECONNECTION_DELAY_BASE * attempts // Exponential backoff
            Log.d(TAG, "Scheduling reconnection for $deviceName in ${delay}ms (attempt $attempts)")

            reconnectionJobs[deviceId]?.cancel()
            reconnectionJobs[deviceId] = coroutineScope.launch {
                delay(delay)

                try {
                    val bluetoothAdapter = android.bluetooth.BluetoothAdapter.getDefaultAdapter()
                    val device = bluetoothAdapter?.getRemoteDevice(deviceId)
                    device?.let { connectToServer(it) }
                } catch (e: Exception) {
                    Log.e(TAG, "Error during scheduled reconnection for $deviceName", e)
                }
            }
        } else {
            Log.w(TAG, "Max reconnection attempts reached for $deviceName")
            connectionAttempts.remove(deviceId)
        }
    }

    private fun startListeningForDevice(connection: ClientConnection) {
        if (connection.isListening) {
            Log.d(TAG, "Already listening for device ${connection.deviceName}")
            return
        }

        connection.isListening = true
        connection.listenJob = coroutineScope.launch {
            try {
                Log.d(TAG, "Started listening for messages from ${connection.deviceName}")
                listenForMessages(connection)
            } catch (e: Exception) {
                Log.e(TAG, "Error in message listening for ${connection.deviceName}", e)
                connection.connectionStable = false
            } finally {
                connection.isListening = false
                if (connection.connectionStable) {
                    // If connection was stable and listening stopped unexpectedly, try to reconnect
                    Log.w(TAG, "Stable connection lost for ${connection.deviceName}, attempting reconnection")
                    scheduleReconnection(connection.deviceId, connection.deviceName)
                }
                cleanupConnection(connection.deviceId)
            }
        }
    }

    private suspend fun sendHeartbeat(connection: ClientConnection) {
        coroutineScope.launch {
            while (connection.isListening && activeConnections.containsKey(connection.deviceId)) {
                try {
                    delay(HEARTBEAT_INTERVAL)

                    // Send heartbeat message
                    val heartbeat = "__HEARTBEAT__"
                    connection.outputStream.write((heartbeat + "\n").toByteArray(Charsets.UTF_8))
                    connection.outputStream.flush()

                    connection.lastActivity = System.currentTimeMillis()
                    Log.v(TAG, "Heartbeat sent to ${connection.deviceName}")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to send heartbeat to ${connection.deviceName}", e)
                    connection.connectionStable = false
                    break
                }
            }
        }
    }

    suspend fun sendMessage(message: String, targetDeviceId: String? = null): Boolean =
        withContext(Dispatchers.IO) {
            val connections = if (targetDeviceId != null) {
                activeConnections[targetDeviceId]?.let { listOf(it) } ?: emptyList()
            } else {
                activeConnections.values.toList() // Send to all connected devices
            }

            if (connections.isEmpty()) {
                Log.e(TAG, "No active connections to send message to")

                // If target device specified but not connected, try to reconnect
                targetDeviceId?.let { deviceId ->
                    try {
                        val bluetoothAdapter = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ECLAIR) {
                            android.bluetooth.BluetoothAdapter.getDefaultAdapter()
                        } else {
                            TODO("VERSION.SDK_INT < ECLAIR")
                        }
                        val device = bluetoothAdapter?.getRemoteDevice(deviceId)
                        device?.let {
                            Log.d(TAG, "Attempting to reconnect to send message")
                            connectToServer(it)
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to reconnect for message sending", e)
                    }
                }

                return@withContext false
            }

            var success = false
            for (connection in connections) {
                try {
                    val messageBytes = (message + "\n").toByteArray(Charsets.UTF_8)
                    connection.outputStream.write(messageBytes)
                    connection.outputStream.flush()

                    connection.lastActivity = System.currentTimeMillis()
                    Log.d(TAG, "Text message sent to ${connection.deviceName}: $message")
                    success = true
                } catch (e: IOException) {
                    Log.e(TAG, "Failed to send message to ${connection.deviceName}", e)
                    connection.connectionStable = false
                    scheduleReconnection(connection.deviceId, connection.deviceName)
                }
            }

            return@withContext success
        }

    suspend fun sendFile(
        uri: Uri,
        fileType: String,
        targetDeviceId: String? = null,
        onProgress: (sent: Long, total: Long) -> Unit
    ): Boolean = withContext(Dispatchers.IO) {
        val connections = if (targetDeviceId != null) {
            activeConnections[targetDeviceId]?.let { listOf(it) } ?: emptyList()
        } else {
            activeConnections.values.toList() // Send to all connected devices
        }

        if (connections.isEmpty()) {
            Log.e(TAG, "No active connections to send file to")

            // If target device specified but not connected, try to reconnect
            targetDeviceId?.let { deviceId ->
                try {
                    val bluetoothAdapter = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ECLAIR) {
                        android.bluetooth.BluetoothAdapter.getDefaultAdapter()
                    } else {
                        TODO("VERSION.SDK_INT < ECLAIR")
                    }
                    val device = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ECLAIR) {
                        bluetoothAdapter?.getRemoteDevice(deviceId)
                    } else {
                        TODO("VERSION.SDK_INT < ECLAIR")
                    }
                    device?.let {
                        Log.d(TAG, "Attempting to reconnect for file transfer")
                        connectToServer(it)
                        delay(1000) // Wait for connection to establish

                        // Retry with new connection
                        return@withContext sendFile(uri, fileType, targetDeviceId, onProgress)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to reconnect for file transfer", e)
                }
            }

            return@withContext false
        }

        var fileStream: InputStream? = null
        try {
            Log.d(TAG, "Starting file transfer for URI: $uri, type: $fileType")

            // Get file info
            fileStream = context.contentResolver.openInputStream(uri)
                ?: throw IOException("Cannot open input stream for URI: $uri")

            var fileSize = -1L
            var fileName = "file_${System.currentTimeMillis()}"
            var mimeType: String? = null

            // Get file metadata
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                        val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)

                        if (sizeIndex >= 0) {
                            fileSize = cursor.getLong(sizeIndex)
                        }
                        if (nameIndex >= 0) {
                            cursor.getString(nameIndex)?.let { name ->
                                fileName = name
                            }
                        }
                    }
                }
            }

            // Get MIME type
            mimeType = context.contentResolver.getType(uri)
            if (mimeType == null) {
                val extension = MimeTypeMap.getFileExtensionFromUrl(fileName)
                if (extension.isNotEmpty()) {
                    mimeType =
                        MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension.lowercase())
                }
            }

            // Fallback for file size
            if (fileSize <= 0L) {
                val tempBytes = fileStream.readBytes()
                fileSize = tempBytes.size.toLong()
                fileStream.close()
                fileStream = tempBytes.inputStream()
                Log.d(TAG, "File size determined by reading: $fileSize bytes")
            }

            // Set default file name based on type if not available
            if (fileName == "file_${System.currentTimeMillis()}") {
                val extension = when {
                    fileType == "image" -> "jpg"
                    mimeType?.startsWith("image/") == true -> mimeType.substringAfter("/")
                    mimeType?.contains("pdf") == true -> "pdf"
                    mimeType?.contains("doc") == true -> "doc"
                    mimeType?.contains("text") == true -> "txt"
                    else -> when (fileType) {
                        "image" -> "jpg"
                        "document" -> "pdf"
                        else -> "dat"
                    }
                }
                fileName = "${fileType}_${System.currentTimeMillis()}.$extension"
            }

            Log.d(
                TAG,
                "File info - Name: $fileName, Size: $fileSize bytes, MIME: $mimeType, Type: $fileType"
            )

            if (fileSize <= 0L) {
                throw IOException("Invalid file size: $fileSize")
            }

            // Send file to all specified connections
            var overallSuccess = false
            for (connection in connections) {
                try {
                    Log.d(TAG, "Sending file to ${connection.deviceName}")

                    // Reset file stream for each connection
                    if (fileStream != null) {
                        fileStream.close()
                    }
                    fileStream = context.contentResolver.openInputStream(uri)
                        ?: throw IOException("Cannot reopen input stream for URI: $uri")

                    val success = sendFileToDevice(
                        fileStream,
                        fileName,
                        fileSize,
                        fileType,
                        mimeType,
                        connection,
                        onProgress
                    )
                    if (success) {
                        overallSuccess = true
                        Log.d(TAG, "File sent successfully to ${connection.deviceName}")
                    } else {
                        Log.e(TAG, "Failed to send file to ${connection.deviceName}")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error sending file to ${connection.deviceName}", e)
                    connection.connectionStable = false
                    scheduleReconnection(connection.deviceId, connection.deviceName)
                }
            }

            return@withContext overallSuccess

        } catch (e: Exception) {
            Log.e(TAG, "File transfer failed", e)
            return@withContext false
        } finally {
            try {
                fileStream?.close()
            } catch (e: Exception) {
                Log.e(TAG, "Error closing file stream", e)
            }
        }
    }

    private suspend fun sendFileToDevice(
        fileStream: InputStream,
        fileName: String,
        fileSize: Long,
        fileType: String,
        mimeType: String?,
        connection: ClientConnection,
        onProgress: (sent: Long, total: Long) -> Unit
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            // Send enhanced header with file type and MIME type
            val header = JSONObject().apply {
                put("type", "file_transfer_v2")
                put("fileName", fileName)
                put("fileSize", fileSize)
                put("fileType", fileType)
                put("mimeType", mimeType)
            }.toString() + "\n"

            Log.d(TAG, "Sending header to ${connection.deviceName}: $header")
            connection.outputStream.write(header.toByteArray(Charsets.UTF_8))
            connection.outputStream.flush()

            // Wait a bit to ensure header is processed
            delay(50)

            // Send file data
            val buffer = ByteArray(4096)
            var bytesRead: Int
            var totalSent = 0L
            var progressUpdateCount = 0

            Log.d(TAG, "Starting file data transfer to ${connection.deviceName}...")

            while (fileStream.read(buffer).also { bytesRead = it } != -1) {
                connection.outputStream.write(buffer, 0, bytesRead)
                totalSent += bytesRead

                // Update progress (limit frequency to avoid UI lag)
                progressUpdateCount++
                if (progressUpdateCount % 10 == 0 || totalSent == fileSize) {
                    onProgress(totalSent, fileSize)
                    Log.d(
                        TAG,
                        "Progress to ${connection.deviceName}: $totalSent / $fileSize bytes (${(totalSent * 100 / fileSize)}%)"
                    )
                }
            }

            // Ensure all data is sent
            connection.outputStream.flush()
            Log.d(TAG, "File data sent successfully to ${connection.deviceName}: $totalSent bytes")

            // Wait before sending EOF to ensure all data is processed
            delay(100)

            // Send EOF signal
            val eofSignal = "__EOF__\n".toByteArray(Charsets.UTF_8)
            connection.outputStream.write(eofSignal)
            connection.outputStream.flush()

            Log.d(TAG, "EOF signal sent to ${connection.deviceName}")

            // Final progress update
            onProgress(fileSize, fileSize)
            connection.lastActivity = System.currentTimeMillis()

            // Wait a bit more to ensure everything is processed
            delay(200)

            Log.d(TAG, "File transfer to ${connection.deviceName} completed successfully")
            return@withContext true

        } catch (e: Exception) {
            Log.e(TAG, "File transfer to ${connection.deviceName} failed", e)
            connection.connectionStable = false
            return@withContext false
        }
    }

    private suspend fun listenForMessages(connection: ClientConnection) =
        withContext(Dispatchers.IO) {
            val buffer = ByteArray(1024)
            var leftover = ""
            try {
                Log.d(TAG, "Started listening for messages from ${connection.deviceName}")
                while (connection.isListening && !currentCoroutineContext().job.isCancelled) {
                    val bytes = connection.inputStream.read(buffer)
                    if (bytes > 0) {
                        connection.lastActivity = System.currentTimeMillis()

                        val fullString = leftover + String(buffer, 0, bytes, Charsets.UTF_8)
                        val parts = fullString.split("\n")
                        leftover = if (fullString.endsWith("\n")) "" else parts.last()

                        for (msg in parts.dropLast(if (leftover.isEmpty()) 0 else 1)) {
                            val cleanMsg = msg.trim()
                            if (cleanMsg.isNotEmpty()) {
                                // Filter out heartbeat messages
                                if (cleanMsg != "__HEARTBEAT__") {
                                    Log.d(TAG, "Received message from ${connection.deviceName}: $cleanMsg")
                                    onMessageReceived?.invoke(cleanMsg)
                                } else {
                                    Log.v(TAG, "Heartbeat received from ${connection.deviceName}")
                                }
                            }
                        }
                    }
                }
            } catch (e: IOException) {
                if (connection.isListening) {
                    Log.e(TAG, "Error in message listening from ${connection.deviceName}", e)
                    connection.connectionStable = false
                }
            } finally {
                Log.d(TAG, "Stopped listening for messages from ${connection.deviceName}")
            }
        }

    fun closeDeviceConnection(deviceId: String) {
        Log.d(TAG, "Closing connection for device: $deviceId")

        // Cancel any pending reconnection attempts
        reconnectionJobs[deviceId]?.cancel()
        reconnectionJobs.remove(deviceId)
        connectionAttempts.remove(deviceId)

        cleanupConnection(deviceId)
    }

    fun closeAllConnections() {
        val deviceIds = activeConnections.keys.toList()
        deviceIds.forEach { deviceId ->
            closeDeviceConnection(deviceId)
        }

        // Cancel all reconnection jobs
        reconnectionJobs.values.forEach { it.cancel() }
        reconnectionJobs.clear()
        connectionAttempts.clear()

        Log.d(TAG, "All client connections closed")
    }

    fun getActiveConnections(): Map<String, String> {
        return activeConnections.mapValues { it.value.deviceName }
    }

    fun isConnectedToDevice(deviceId: String): Boolean {
        return activeConnections[deviceId]?.let { connection ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
                connection.socket.isConnected && connection.connectionStable
            } else {
                TODO("VERSION.SDK_INT < ICE_CREAM_SANDWICH")
            }
        } ?: false
    }

    fun getDeviceName(deviceId: String): String? {
        return activeConnections[deviceId]?.deviceName
    }

    // Force reconnection for a specific device
    fun forceReconnectToDevice(deviceId: String) {
        Log.d(TAG, "Force reconnecting to device: $deviceId")

        // Clean up existing connection
        cleanupConnection(deviceId)

        // Reset attempts and trigger immediate reconnection
        connectionAttempts.remove(deviceId)

        coroutineScope.launch {
            try {
                val bluetoothAdapter = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ECLAIR) {
                    android.bluetooth.BluetoothAdapter.getDefaultAdapter()
                } else {
                    TODO("VERSION.SDK_INT < ECLAIR")
                }
                val device = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ECLAIR) {
                    bluetoothAdapter?.getRemoteDevice(deviceId)
                } else {
                    TODO("VERSION.SDK_INT < ECLAIR")
                }
                device?.let { connectToServer(it) }
            } catch (e: Exception) {
                Log.e(TAG, "Error during force reconnection for $deviceId", e)
            }
        }
    }

    fun closeSocket() {
        // For backward compatibility - close all connections
        closeAllConnections()
        connectionHealthJob.cancel()
        coroutineScope.cancel()
    }
}
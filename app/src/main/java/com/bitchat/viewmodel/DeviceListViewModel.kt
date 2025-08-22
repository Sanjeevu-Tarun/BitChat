package com.bitchat.viewmodel

import android.Manifest
import android.annotation.SuppressLint
import android.app.Application
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.bitchat.bluetooth.BluetoothRepository
import com.bitchat.model.ChatDevice
import com.bitchat.service.BluetoothService
import com.bitchat.service.ConnectionState
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.seconds

@SuppressLint("MissingPermission")
class DeviceListViewModel(private val app: Application) : AndroidViewModel(app) {

    companion object {
        private const val TAG = "DeviceListViewModel"
        private const val SCAN_DURATION = 12000L // 12 seconds
    }

    private val bluetoothRepository = BluetoothRepository(app)

    private val bluetoothAdapter: BluetoothAdapter? by lazy {
        val bluetoothManager = app.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothManager.adapter
    }

    // Combine discovered devices from repository with bonded devices
    private val _allDevices = MutableStateFlow<List<ChatDevice>>(emptyList())
    val devices = _allDevices.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _connectionStatus = MutableStateFlow("Idle")
    val connectionStatus = _connectionStatus.asStateFlow()

    // Enhanced connection tracking with proper client-service sync
    private val connectedDeviceIds = mutableSetOf<String>()
    private val deviceNames = mutableMapOf<String, String>() // Cache device names

    init {
        observeConnectionStates()
        observeDiscoveredDevices()
        loadInitialDevices()
    }

    private fun observeConnectionStates() {
        viewModelScope.launch {
            // Observe BluetoothService connection state
            BluetoothService.connectionState.collect { state ->
                _connectionStatus.value = when (state) {
                    is ConnectionState.Connected -> {
                        val deviceAddress = findDeviceAddressByName(state.deviceName)
                        if (deviceAddress.isNotEmpty()) {
                            connectedDeviceIds.add(deviceAddress)
                            deviceNames[deviceAddress] = state.deviceName
                        }
                        "Connected to ${state.deviceName}"
                    }
                    is ConnectionState.MultipleConnected -> {
                        // Don't clear all - add new connections
                        state.deviceNames.forEach { name ->
                            val deviceAddress = findDeviceAddressByName(name)
                            if (deviceAddress.isNotEmpty()) {
                                connectedDeviceIds.add(deviceAddress)
                                deviceNames[deviceAddress] = name
                            }
                        }
                        "Connected to ${state.deviceNames.size} devices"
                    }
                    is ConnectionState.Listening -> {
                        // Don't clear connections when listening - they might still be active
                        "Listening for connections"
                    }
                    is ConnectionState.Error -> {
                        "Error: ${state.message}"
                    }
                    is ConnectionState.Idle -> {
                        "Idle"
                    }
                }
                updateDeviceConnectionStatus()
                Log.d(TAG, "Connection state changed: $state, connected devices: $connectedDeviceIds")
            }
        }

        // Observe the connected devices list from BluetoothService
        viewModelScope.launch {
            BluetoothService.connectedDevices.collect { connectedDevices ->
                // Merge with existing connections instead of replacing
                connectedDeviceIds.addAll(connectedDevices)

                // Remove devices that are no longer in either list
                val allConnected = connectedDevices.toSet()
                val iterator = connectedDeviceIds.iterator()
                while (iterator.hasNext()) {
                    val deviceId = iterator.next()
                    if (!allConnected.contains(deviceId)) {
                        // Double-check by verifying actual connection status
                        if (!isDeviceReallyConnected(deviceId)) {
                            iterator.remove()
                            deviceNames.remove(deviceId)
                        }
                    }
                }

                updateDeviceConnectionStatus()
                Log.d(TAG, "Connected devices updated from service: $connectedDevices, total: $connectedDeviceIds")
            }
        }

        // Periodically verify connection status to handle edge cases
        viewModelScope.launch {
            while (true) {
                delay(5.seconds) // Check every 5 seconds
                verifyConnectionStatus()
            }
        }
    }

    private fun verifyConnectionStatus() {
        val iterator = connectedDeviceIds.iterator()
        while (iterator.hasNext()) {
            val deviceId = iterator.next()
            if (!isDeviceReallyConnected(deviceId)) {
                Log.d(TAG, "Removing stale connection for device: $deviceId")
                iterator.remove()
                deviceNames.remove(deviceId)
            }
        }
        updateDeviceConnectionStatus()
    }

    private fun isDeviceReallyConnected(deviceId: String): Boolean {
        // Check both service and static method connections
        return BluetoothService.connectedDevices.value.contains(deviceId) ||
                BluetoothService.isDeviceConnectedToService(deviceId)
    }

    private fun findDeviceAddressByName(deviceName: String): String {
        // First check cached names
        deviceNames.entries.find { it.value == deviceName }?.key?.let { return it }

        // Then check device list
        return _allDevices.value.find {
            try {
                it.device.name == deviceName
            } catch (e: SecurityException) {
                false
            }
        }?.device?.address ?: ""
    }

    private fun observeDiscoveredDevices() {
        viewModelScope.launch {
            bluetoothRepository.bitChatDevices.collect { discoveredDevices ->
                Log.d(TAG, "Repository discovered ${discoveredDevices.size} devices")
                updateAllDevices(discoveredDevices)
            }
        }
    }

    fun loadPairedDevices() {
        loadInitialDevices()
    }

    private fun loadInitialDevices() {
        if (!hasRequiredPermissions()) {
            Log.w(TAG, "Required Bluetooth permissions not granted")
            return
        }

        if (bluetoothAdapter?.isEnabled != true) {
            Log.w(TAG, "Bluetooth not enabled")
            return
        }

        loadBondedDevices()
    }

    private fun loadBondedDevices() {
        viewModelScope.launch {
            try {
                val bondedDevices = bluetoothRepository.getBondedDevices()
                val discoveredDevices = bluetoothRepository.bitChatDevices.value
                updateAllDevices(discoveredDevices, bondedDevices)
                Log.d(TAG, "Loaded ${bondedDevices.size} bonded devices")
            } catch (e: SecurityException) {
                Log.e(TAG, "Permission error loading bonded devices", e)
            }
        }
    }

    private fun updateAllDevices(discoveredDevices: List<ChatDevice>, bondedDevices: List<ChatDevice> = emptyList()) {
        val allDevicesMap = mutableMapOf<String, ChatDevice>()

        // Add bonded devices first (they have priority)
        bondedDevices.forEach { device ->
            allDevicesMap[device.device.address] = device
            // Cache device name
            try {
                device.device.name?.let { name ->
                    deviceNames[device.device.address] = name
                }
            } catch (e: SecurityException) {
                // Ignore
            }
        }

        // Add discovered devices (only if not already bonded)
        discoveredDevices.forEach { device ->
            if (!allDevicesMap.containsKey(device.device.address)) {
                allDevicesMap[device.device.address] = device
                // Cache device name
                try {
                    device.device.name?.let { name ->
                        deviceNames[device.device.address] = name
                    }
                } catch (e: SecurityException) {
                    // Ignore
                }
            }
        }

        // Update connection status for all devices
        val updatedDevices = allDevicesMap.values.map { device ->
            val isConnected = connectedDeviceIds.contains(device.device.address)
            device.copy(
                isOnline = isConnected,
                lastSeen = if (isConnected) System.currentTimeMillis() else device.lastSeen
            )
        }

        // Sort devices: connected first, then bonded, then by last seen
        val sortedDevices = updatedDevices.sortedWith(
            compareByDescending<ChatDevice> { it.isOnline }
                .thenBy {
                    try {
                        bluetoothAdapter?.bondedDevices?.contains(it.device) != true
                    } catch (e: SecurityException) {
                        true
                    }
                }
                .thenByDescending { it.lastSeen }
        )

        _allDevices.value = sortedDevices
        Log.d(TAG, "Updated device list: ${sortedDevices.size} total devices, ${connectedDeviceIds.size} connected")
    }

    private fun updateDeviceConnectionStatus() {
        val currentDevices = _allDevices.value

        val updatedDevices = currentDevices.map { device ->
            val isConnected = connectedDeviceIds.contains(device.device.address)
            if (device.isOnline != isConnected) {
                device.copy(
                    isOnline = isConnected,
                    lastSeen = if (isConnected) System.currentTimeMillis() else device.lastSeen
                )
            } else {
                device
            }
        }

        if (updatedDevices != currentDevices) {
            _allDevices.value = updatedDevices.sortedWith(
                compareByDescending<ChatDevice> { it.isOnline }
                    .thenByDescending { it.lastSeen }
            )
        }
    }

    fun startScan() {
        if (_isScanning.value) {
            Log.d(TAG, "Scan already in progress")
            return
        }

        if (bluetoothAdapter?.isEnabled != true) {
            Log.w(TAG, "Bluetooth not available or disabled")
            return
        }

        if (!hasRequiredPermissions()) {
            Log.w(TAG, "Required Bluetooth permissions not granted")
            return
        }

        viewModelScope.launch {
            try {
                _isScanning.value = true
                Log.d(TAG, "Starting Bluetooth device scan using repository")

                // Refresh bonded devices first
                loadBondedDevices()

                // Start repository scan for nearby devices
                bluetoothRepository.startScanForBitChatDevices(SCAN_DURATION)

                // Auto-stop scanning after duration
                delay(SCAN_DURATION + 1000L) // Add buffer
                _isScanning.value = false

            } catch (e: Exception) {
                Log.e(TAG, "Error during device scan", e)
                _isScanning.value = false
            }
        }
    }

    fun stopScan() {
        if (!_isScanning.value) return

        viewModelScope.launch {
            bluetoothRepository.stopScan()
            _isScanning.value = false
            Log.d(TAG, "Scan stopped by user")
        }
    }

    // Enhanced device name resolution with comprehensive fallbacks
    fun getDeviceName(deviceAddress: String): String {
        return try {
            Log.d(TAG, "Getting device name for address: $deviceAddress")

            // First check cached names
            deviceNames[deviceAddress]?.let {
                Log.d(TAG, "Found cached device name: $it")
                return it
            }

            // Then check if device is in our list
            _allDevices.value.find { it.device.address == deviceAddress }?.device?.let { device ->
                try {
                    val name = device.name?.takeIf { it.isNotBlank() }
                    if (name != null) {
                        Log.d(TAG, "Found device name in list: $name")
                        deviceNames[deviceAddress] = name // Cache it
                        return name
                    } else {

                    }
                } catch (e: SecurityException) {
                    Log.w(TAG, "Permission denied getting device name from list")
                }
            }

            // Try to get from bluetooth adapter
            try {
                val device = bluetoothAdapter?.getRemoteDevice(deviceAddress)
                val deviceName = device?.name
                Log.d(TAG, "Device name from adapter: $deviceName")

                when {
                    deviceName.isNullOrBlank() -> {
                        val fallbackName = "Device ${deviceAddress.takeLast(8)}"
                        Log.d(TAG, "Using fallback name: $fallbackName")
                        fallbackName
                    }
                    else -> {
                        Log.d(TAG, "Using adapter name: $deviceName")
                        deviceNames[deviceAddress] = deviceName // Cache it
                        deviceName
                    }
                }
            } catch (e: SecurityException) {
                val fallbackName = "Device ${deviceAddress.takeLast(8)}"
                Log.w(TAG, "Permission denied, using fallback: $fallbackName")
                fallbackName
            }
        } catch (e: Exception) {
            val fallbackName = "Device ${deviceAddress.takeLast(8)}"
            Log.e(TAG, "Error getting device name, using fallback: $fallbackName", e)
            fallbackName
        }
    }

    // Get BluetoothDevice by address
    @SuppressLint("MissingPermission")
    fun getBluetoothDevice(deviceAddress: String): BluetoothDevice? {
        return try {
            _allDevices.value.find { it.device.address == deviceAddress }?.device
                ?: bluetoothAdapter?.getRemoteDevice(deviceAddress)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting BluetoothDevice for address: $deviceAddress", e)
            null
        }
    }

    // Manual connection status update (for external use)
    fun updateDeviceConnectionState(deviceAddress: String, isConnected: Boolean, deviceName: String? = null) {
        if (isConnected) {
            connectedDeviceIds.add(deviceAddress)
            deviceName?.let { deviceNames[deviceAddress] = it }
        } else {
            connectedDeviceIds.remove(deviceAddress)
        }
        updateDeviceConnectionStatus()
        Log.d(TAG, "Manually updated connection state for $deviceAddress: $isConnected")
    }

    // Check if we have all required permissions
    private fun hasRequiredPermissions(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            hasBluetoothConnectPermission() && hasBluetoothScanPermission()
        } else {
            true // For older Android versions
        }
    }

    // Permission checks
    @RequiresApi(Build.VERSION_CODES.S)
    fun hasBluetoothConnectPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            app,
            Manifest.permission.BLUETOOTH_CONNECT
        ) == PackageManager.PERMISSION_GRANTED
    }

    @RequiresApi(Build.VERSION_CODES.S)
    fun hasBluetoothScanPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            app,
            Manifest.permission.BLUETOOTH_SCAN
        ) == PackageManager.PERMISSION_GRANTED
    }

    // Get connection status for a specific device
    fun getDeviceConnectionStatus(deviceAddress: String): Boolean {
        return connectedDeviceIds.contains(deviceAddress)
    }

    // Refresh device list
    fun refreshDevices() {
        loadInitialDevices()
    }

    // Get all connected device IDs
    fun getConnectedDeviceIds(): List<String> {
        return connectedDeviceIds.toList()
    }

    // Clear connection state (for cleanup)
    fun clearConnectionState() {
        connectedDeviceIds.clear()
        deviceNames.clear()
        updateDeviceConnectionStatus()
        Log.d(TAG, "Connection state cleared")
    }

    // Get device messages count
    fun getDeviceMessageCount(deviceAddress: String): Int {
        return BluetoothService.getMessagesForDevice(deviceAddress).size
    }

    // Get last message for device
    fun getLastMessageForDevice(deviceAddress: String): String? {
        val messages = BluetoothService.getMessagesForDevice(deviceAddress)
        return messages.lastOrNull()?.content
    }

    // Check if device has unread messages (this would need additional implementation)
    fun hasUnreadMessages(deviceAddress: String): Boolean {
        // For now, return false - would need message read state tracking
        return false
    }

    // Get device from list by address
    fun getDeviceByAddress(deviceAddress: String): ChatDevice? {
        return _allDevices.value.find { it.device.address == deviceAddress }
    }

    // Check if device is bonded
    fun isDeviceBonded(deviceAddress: String): Boolean {
        return try {
            bluetoothAdapter?.bondedDevices?.any { it.address == deviceAddress } == true
        } catch (e: SecurityException) {
            false
        }
    }

    // Get all device IDs with messages
    fun getAllDeviceIdsWithMessages(): List<String> {
        return BluetoothService.getAllDeviceIds()
    }

    // Force refresh device connection status
    fun forceRefreshConnectionStatus() {
        verifyConnectionStatus()
    }

    // Get device signal strength (if available)
    fun getDeviceSignalStrength(deviceAddress: String): Int? {
        // This would need RSSI implementation
        return null
    }

    // Check if device supports our chat protocol
    fun isDeviceCompatible(device: BluetoothDevice): Boolean {
        // For now, assume all devices are compatible
        // Could implement UUID checking here
        return true
    }

    // Get devices by connection status
    fun getConnectedDevices(): List<ChatDevice> {
        return _allDevices.value.filter { it.isOnline }
    }

    fun getDisconnectedDevices(): List<ChatDevice> {
        return _allDevices.value.filter { !it.isOnline }
    }

    // Get bonded devices
    fun getBondedDevices(): List<ChatDevice> {
        return try {
            val bondedAddresses = bluetoothAdapter?.bondedDevices?.map { it.address }?.toSet() ?: emptySet()
            _allDevices.value.filter { bondedAddresses.contains(it.device.address) }
        } catch (e: SecurityException) {
            emptyList()
        }
    }

    override fun onCleared() {
        super.onCleared()
        bluetoothRepository.stopScan()
        Log.d(TAG, "DeviceListViewModel cleared")
    }
}
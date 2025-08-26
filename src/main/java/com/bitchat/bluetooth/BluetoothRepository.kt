package com.bitchat.bluetooth

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.*
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import com.bitchat.model.ChatDevice
import com.bitchat.service.BluetoothService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.util.*

class BluetoothRepository(private val context: Context) {

    private val adapter: BluetoothAdapter by lazy {
        (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
    }

    private val _bitChatDevices = MutableStateFlow<List<ChatDevice>>(emptyList())
    val bitChatDevices: StateFlow<List<ChatDevice>> = _bitChatDevices

    // Enhanced device tracking
    private val discovered = mutableMapOf<String, ChatDevice>()
    private val knownDevices = mutableMapOf<String, ChatDevice>() // Persistent device cache
    private val scanScope = CoroutineScope(Dispatchers.IO)
    private var isDiscovering = false
    private var receiverRegistered = false

    // Device connection state tracking
    private val deviceConnectionStates = mutableMapOf<String, Boolean>()

    private val discoveryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                BluetoothDevice.ACTION_FOUND -> {
                    handleDeviceFound(intent)
                }
                BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                    Log.d(TAG, "Discovery finished")
                    isDiscovering = false
                    emitDevices()
                }
                BluetoothAdapter.ACTION_DISCOVERY_STARTED -> {
                    Log.d(TAG, "Discovery started")
                    isDiscovering = true
                }
                BluetoothDevice.ACTION_BOND_STATE_CHANGED -> {
                    handleBondStateChanged(intent)
                }
                BluetoothAdapter.ACTION_STATE_CHANGED -> {
                    handleBluetoothStateChanged(intent)
                }
            }
        }
    }

    init {
        // Load previously known devices from persistent storage if available
        loadKnownDevices()

        // Register for important Bluetooth events
        registerPersistentReceiver()
    }

    private fun registerPersistentReceiver() {
        if (!receiverRegistered) {
            try {
                val filter = IntentFilter().apply {
                    addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
                    addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
                }
                context.registerReceiver(discoveryReceiver, filter)
                receiverRegistered = true
                Log.d(TAG, "Persistent receiver registered")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to register persistent receiver", e)
            }
        }
    }

    private fun handleBondStateChanged(intent: Intent) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
            val bondState = intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.BOND_NONE)
            device?.let {
                Log.d(TAG, "Bond state changed for ${it.address}: $bondState")
                // Refresh device list when bonding changes
                if (bondState == BluetoothDevice.BOND_BONDED) {
                    updateDeviceInCache(it)
                    emitDevices()
                }
            }
        } else {
            @Suppress("DEPRECATION")
            val device = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
            val bondState = intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.BOND_NONE)
            device?.let {
                Log.d(TAG, "Bond state changed for ${it.address}: $bondState")
                if (bondState == BluetoothDevice.BOND_BONDED) {
                    updateDeviceInCache(it)
                    emitDevices()
                }
            }
        }
    }

    private fun handleBluetoothStateChanged(intent: Intent) {
        val state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)
        Log.d(TAG, "Bluetooth state changed: $state")

        when (state) {
            BluetoothAdapter.STATE_ON -> {
                // Bluetooth turned on, refresh devices
                scanScope.launch {
                    delay(1000L) // Wait a bit for adapter to be ready
                    loadBondedDevices() // Fixed function name
                }
            }
            BluetoothAdapter.STATE_OFF -> {
                // Bluetooth turned off, clear current discovery
                if (isDiscovering) {
                    isDiscovering = false
                }
                // Keep known devices but mark as offline
                markAllDevicesOffline()
            }
        }
    }

    private fun markAllDevicesOffline() {
        discovered.values.forEach { it.isOnline = false }
        knownDevices.values.forEach { it.isOnline = false }
        deviceConnectionStates.clear()
        emitDevices()
    }

    private fun loadKnownDevices() {
        // In a real implementation, you might load this from SharedPreferences or a database
        // For now, we'll start with an empty cache that gets populated during runtime
        Log.d(TAG, "Known devices cache initialized")
    }

    // Fixed function - now properly loads bonded devices
    private fun loadBondedDevices() {
        if (!adapter.isEnabled) {
            Log.w(TAG, "Bluetooth adapter not enabled, cannot load bonded devices")
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT)
                != PackageManager.PERMISSION_GRANTED) {
                Log.w(TAG, "BLUETOOTH_CONNECT permission not granted")
                return
            }
        }

        try {
            val bondedDevices = adapter.bondedDevices ?: emptySet()
            Log.d(TAG, "Loading ${bondedDevices.size} bonded devices")

            bondedDevices.forEach { device ->
                updateDeviceInCache(device)
            }

            emitDevices()
        } catch (e: SecurityException) {
            Log.e(TAG, "Security exception loading bonded devices", e)
        } catch (e: Exception) {
            Log.e(TAG, "Error loading bonded devices", e)
        }
    }

    private fun updateDeviceInCache(device: BluetoothDevice) {
        try {
            val chatDevice = ChatDevice(
                device = device,
                isOnline = deviceConnectionStates[device.address] ?: false,
                lastSeen = System.currentTimeMillis()
            )

            knownDevices[device.address] = chatDevice
            discovered[device.address] = chatDevice

            try {
                device.name?.let { name ->
                    Log.d(TAG, "Updated device cache: $name (${device.address})")
                }
            } catch (e: SecurityException) {
                Log.d(TAG, "Updated device cache: ${device.address}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error updating device cache", e)
        }
    }

    private fun handleDeviceFound(intent: Intent) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
            device?.let { processFoundDevice(it) }
        } else {
            @Suppress("DEPRECATION")
            val device = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
            device?.let { processFoundDevice(it) }
        }
    }

    private fun processFoundDevice(device: BluetoothDevice) {
        val address = device.address ?: return

        // Skip if we already have this device in current discovery session
        if (discovered.containsKey(address)) {
            // Update last seen time
            discovered[address]?.lastSeen = System.currentTimeMillis()
            return
        }

        try {
            // Check permissions before accessing device name
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT)
                    != PackageManager.PERMISSION_GRANTED) {
                    Log.w(TAG, "BLUETOOTH_CONNECT permission not granted")
                    return
                }
            }

            // Create chat device with current connection state
            val chatDevice = ChatDevice(
                device = device,
                isOnline = deviceConnectionStates[address] ?: false,
                lastSeen = System.currentTimeMillis()
            )

            discovered[address] = chatDevice
            knownDevices[address] = chatDevice // Add to persistent cache

            val deviceName = try {
                device.name ?: "Unknown Device"
            } catch (e: SecurityException) {
                "Unknown Device"
            }

            Log.d(TAG, "Discovered device: $deviceName ($address)")
            emitDevices()

        } catch (e: SecurityException) {
            Log.e(TAG, "Security exception processing device: ${e.message}")
        } catch (e: Exception) {
            Log.e(TAG, "Error processing found device: ${e.message}")
        }
    }

    fun startScanForBitChatDevices(durationMillis: Long = 12000L) {
        if (!adapter.isEnabled) {
            Log.w(TAG, "Bluetooth adapter is not enabled")
            return
        }

        // Check permissions
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN)
                != PackageManager.PERMISSION_GRANTED) {
                Log.w(TAG, "BLUETOOTH_SCAN permission not granted")
                return
            }
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT)
                != PackageManager.PERMISSION_GRANTED) {
                Log.w(TAG, "BLUETOOTH_CONNECT permission not granted")
                return
            }
        }

        // Start with known devices (preserves previous discoveries)
        discovered.clear()
        discovered.putAll(knownDevices)

        // Update connection states for known devices
        discovered.values.forEach { device ->
            device.isOnline = deviceConnectionStates[device.device.address] ?: false
        }

        try {
            // Cancel any ongoing discovery
            if (adapter.isDiscovering) {
                adapter.cancelDiscovery()
                // Wait a bit for cancellation to complete
                Thread.sleep(100)
            }

            // Register discovery receiver
            registerDiscoveryReceiver()

            // Start discovery
            val started = adapter.startDiscovery()
            if (started) {
                Log.d(TAG, "Bluetooth discovery started for ${durationMillis}ms")
                isDiscovering = true

                // Emit current known devices immediately
                emitDevices()
            } else {
                Log.w(TAG, "Failed to start Bluetooth discovery")
                stopDiscovery()
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "Security exception during discovery: ${e.message}")
            stopDiscovery()
        } catch (e: Exception) {
            Log.e(TAG, "Exception during discovery: ${e.message}")
            stopDiscovery()
        }

        // Auto-stop discovery after duration
        scanScope.launch {
            delay(durationMillis)
            if (isDiscovering) {
                stopDiscovery()
            }
        }
    }

    private fun registerDiscoveryReceiver() {
        try {
            val filter = IntentFilter().apply {
                addAction(BluetoothDevice.ACTION_FOUND)
                addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
                addAction(BluetoothAdapter.ACTION_DISCOVERY_STARTED)
            }

            // Use a different receiver instance for discovery to avoid conflicts
            context.registerReceiver(discoveryReceiver, filter)
            Log.d(TAG, "Discovery receiver registered")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register discovery receiver", e)
        }
    }

    private fun unregisterDiscoveryReceiver() {
        try {
            context.unregisterReceiver(discoveryReceiver)
            Log.d(TAG, "Discovery receiver unregistered")
        } catch (e: IllegalArgumentException) {
            // Receiver not registered, ignore
            Log.v(TAG, "Discovery receiver was not registered")
        } catch (e: Exception) {
            Log.e(TAG, "Error unregistering discovery receiver", e)
        }
    }

    private fun stopDiscovery() {
        try {
            if (adapter.isDiscovering) {
                adapter.cancelDiscovery()
            }

            unregisterDiscoveryReceiver()

            isDiscovering = false
            emitDevices()
            Log.d(TAG, "Bluetooth discovery stopped")
        } catch (e: SecurityException) {
            Log.e(TAG, "Security exception stopping discovery: ${e.message}")
        } catch (e: Exception) {
            Log.e(TAG, "Exception stopping discovery: ${e.message}")
        }
    }

    fun getBondedDevices(): List<ChatDevice> {
        if (!adapter.isEnabled) {
            Log.w(TAG, "Bluetooth adapter not enabled, returning cached devices")
            return knownDevices.values.toList()
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT)
                != PackageManager.PERMISSION_GRANTED) {
                Log.w(TAG, "BLUETOOTH_CONNECT permission not granted")
                return emptyList()
            }
        }

        return try {
            val bondedDevices = adapter.bondedDevices ?: emptySet()
            val chatDevices = bondedDevices.map { device ->
                // Use existing cached device if available, otherwise create new
                val existingDevice = knownDevices[device.address]
                if (existingDevice != null) {
                    existingDevice.copy(
                        isOnline = deviceConnectionStates[device.address] ?: false,
                        lastSeen = if (deviceConnectionStates[device.address] == true)
                            System.currentTimeMillis() else existingDevice.lastSeen
                    )
                } else {
                    val newDevice = ChatDevice(
                        device = device,
                        isOnline = deviceConnectionStates[device.address] ?: false,
                        lastSeen = System.currentTimeMillis()
                    )
                    // Cache the new device
                    knownDevices[device.address] = newDevice
                    newDevice
                }
            }

            Log.d(TAG, "Retrieved ${chatDevices.size} bonded devices")
            chatDevices
        } catch (e: SecurityException) {
            Log.e(TAG, "Security exception getting bonded devices: ${e.message}")
            emptyList()
        } catch (e: Exception) {
            Log.e(TAG, "Exception getting bonded devices: ${e.message}")
            emptyList()
        }
    }

    private fun emitDevices() {
        // Merge discovered and known devices
        val allDevicesMap = mutableMapOf<String, ChatDevice>()

        // Add known devices first
        knownDevices.forEach { (address, device) ->
            allDevicesMap[address] = device.copy(
                isOnline = deviceConnectionStates[address] ?: false
            )
        }

        // Update with discovered devices (fresher data)
        discovered.forEach { (address, device) ->
            allDevicesMap[address] = device.copy(
                isOnline = deviceConnectionStates[address] ?: false
            )
        }

        // Sort devices: connected first, then bonded, then by last seen
        val sortedDevices = allDevicesMap.values.sortedWith(
            compareByDescending<ChatDevice> { it.isOnline }
                .thenBy {
                    try {
                        adapter.bondedDevices?.contains(it.device) != true
                    } catch (e: SecurityException) {
                        true
                    }
                }
                .thenByDescending { it.lastSeen }
        )

        _bitChatDevices.value = sortedDevices
        Log.d(TAG, "Emitted ${sortedDevices.size} devices (${deviceConnectionStates.values.count { it }} connected)")
    }

    fun stopScan() {
        stopDiscovery()
    }

    fun updateDeviceConnectionStatus(deviceAddress: String, isConnected: Boolean) {
        Log.d(TAG, "Updating connection status for $deviceAddress: $isConnected")

        deviceConnectionStates[deviceAddress] = isConnected

        // Update in discovered devices
        discovered[deviceAddress]?.let { device ->
            device.isOnline = isConnected
            device.lastSeen = if (isConnected) System.currentTimeMillis() else device.lastSeen
        }

        // Update in known devices cache
        knownDevices[deviceAddress]?.let { device ->
            device.isOnline = isConnected
            device.lastSeen = if (isConnected) System.currentTimeMillis() else device.lastSeen
        }

        emitDevices()
    }

    fun clearDiscoveredDevices() {
        discovered.clear()
        deviceConnectionStates.clear()
        _bitChatDevices.value = emptyList()
        Log.d(TAG, "Cleared discovered devices")
    }

    // Get cached device by address
    fun getCachedDevice(deviceAddress: String): ChatDevice? {
        return knownDevices[deviceAddress] ?: discovered[deviceAddress]
    }

    // Update device last seen time
    fun updateDeviceLastSeen(deviceAddress: String) {
        val currentTime = System.currentTimeMillis()

        knownDevices[deviceAddress]?.let { it.lastSeen = currentTime }
        discovered[deviceAddress]?.let { it.lastSeen = currentTime }

        emitDevices()
    }

    // Get connection state for device
    fun getDeviceConnectionState(deviceAddress: String): Boolean {
        return deviceConnectionStates[deviceAddress] ?: false
    }

    // Refresh all devices (bonded + discovered)
    fun refreshAllDevices() {
        scanScope.launch {
            try {
                // First load bonded devices
                val bondedDevices = getBondedDevices()

                // Update known devices with bonded devices
                bondedDevices.forEach { device ->
                    knownDevices[device.device.address] = device
                }

                emitDevices()
                Log.d(TAG, "Refreshed all devices")
            } catch (e: Exception) {
                Log.e(TAG, "Error refreshing devices", e)
            }
        }
    }

    fun cleanup() {
        try {
            stopDiscovery()

            if (receiverRegistered) {
                try {
                    context.unregisterReceiver(discoveryReceiver)
                    receiverRegistered = false
                } catch (e: IllegalArgumentException) {
                    // Already unregistered
                }
            }

            Log.d(TAG, "Repository cleaned up")
        } catch (e: Exception) {
            Log.e(TAG, "Error during cleanup", e)
        }
    }

    companion object {
        private const val TAG = "BluetoothRepository"
        // Use the same UUID as BluetoothService
        val SERVICE_UUID: UUID = BluetoothService.SERVICE_UUID
    }
}
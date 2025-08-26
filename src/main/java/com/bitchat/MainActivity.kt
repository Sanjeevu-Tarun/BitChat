package com.bitchat

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.bitchat.service.BluetoothService
import com.bitchat.ui.ChatScreen
import com.bitchat.ui.DeviceListScreen
import com.bitchat.ui.theme.BitChatTheme
import com.bitchat.viewmodel.ChatViewModel
import com.bitchat.viewmodel.DeviceListViewModel
import com.bitchat.util.NotificationHelper

class MainActivity : ComponentActivity() {

    companion object {
        private const val TAG = "MainActivity"
    }

    private var bluetoothAdapter: BluetoothAdapter? = null

    // Permission launcher for Bluetooth
    private val bluetoothPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        if (allGranted) {
            Log.d(TAG, "All Bluetooth permissions granted")
            enableBluetoothAndStartService()
        } else {
            Log.w(TAG, "Bluetooth permissions denied")
            Toast.makeText(this, "Bluetooth permissions are required for the app to work", Toast.LENGTH_LONG).show()
        }
    }

    // Bluetooth enable launcher
    private val enableBluetoothLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            Log.d(TAG, "Bluetooth enabled by user")
            startBluetoothService()
        } else {
            Log.w(TAG, "User declined to enable Bluetooth")
            Toast.makeText(this, "Bluetooth is required for this app", Toast.LENGTH_SHORT).show()
        }
    }

    // Navigation state to handle notification intents
    private var pendingNavigation by mutableStateOf<PendingNavigation?>(null)

    data class PendingNavigation(
        val deviceId: String,
        val deviceName: String
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Log.d(TAG, "MainActivity onCreate")

        // Initialize Bluetooth adapter
        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = bluetoothManager.adapter

        if (bluetoothAdapter == null) {
            Toast.makeText(this, "Bluetooth not supported on this device", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        // Handle notification intent
        handleNotificationIntent(intent)

        setContent {
            var isDarkTheme by remember { mutableStateOf(false) }

            BitChatTheme(darkTheme = isDarkTheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    BitChatApp(
                        isDarkTheme = isDarkTheme,
                        onThemeChange = { isDarkTheme = it },
                        pendingNavigation = pendingNavigation,
                        onNavigationHandled = { pendingNavigation = null }
                    )
                }
            }
        }

        // Check and request permissions
        checkAndRequestPermissions()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Handle new intents when app is already running
        Log.d(TAG, "onNewIntent called")
        handleNotificationIntent(intent)
        // Update the intent for getIntent() calls
        setIntent(intent)
    }

    private fun handleNotificationIntent(intent: Intent?) {
        intent?.let {
            // Check if this intent came from a notification
            val shouldNavigateToChat = it.getBooleanExtra("NAVIGATE_TO_CHAT", false)
            val deviceId = it.getStringExtra("DEVICE_ID")
            val deviceName = it.getStringExtra("DEVICE_NAME")

            if (shouldNavigateToChat && !deviceId.isNullOrEmpty() && !deviceName.isNullOrEmpty()) {
                Log.d(TAG, "Notification intent detected - navigating to chat: $deviceName ($deviceId)")

                // Store the navigation intent to be handled once the UI is ready
                pendingNavigation = PendingNavigation(deviceId, deviceName)

                // Set the active chat device in NotificationHelper to suppress further notifications
                NotificationHelper.setActiveChatDevice(deviceId)
            }
        }
    }

    private fun checkAndRequestPermissions() {
        Log.d(TAG, "Checking Bluetooth permissions")

        val permissionsToRequest = mutableListOf<String>()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Android 12+ permissions
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.BLUETOOTH_CONNECT)
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.BLUETOOTH_SCAN)
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_ADVERTISE) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.BLUETOOTH_ADVERTISE)
            }
        } else {
            // Pre-Android 12 permissions
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.BLUETOOTH)
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_ADMIN) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.BLUETOOTH_ADMIN)
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.ACCESS_FINE_LOCATION)
            }
        }

        if (permissionsToRequest.isNotEmpty()) {
            Log.d(TAG, "Requesting permissions: $permissionsToRequest")
            bluetoothPermissionLauncher.launch(permissionsToRequest.toTypedArray())
        } else {
            Log.d(TAG, "All permissions already granted")
            enableBluetoothAndStartService()
        }
    }

    @SuppressLint("MissingPermission")
    private fun enableBluetoothAndStartService() {
        if (bluetoothAdapter?.isEnabled == true) {
            Log.d(TAG, "Bluetooth already enabled")
            startBluetoothService()
        } else {
            Log.d(TAG, "Requesting to enable Bluetooth")
            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            enableBluetoothLauncher.launch(enableBtIntent)
        }
    }

    private fun startBluetoothService() {
        Log.d(TAG, "Starting BluetoothService")
        val serviceIntent = Intent(this, BluetoothService::class.java)
        startForegroundService(serviceIntent)
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "MainActivity onDestroy")
        // Clear active chat device when app is destroyed
        NotificationHelper.setActiveChatDevice(null)
        // Don't stop the service here as it should run in background
    }

    override fun onPause() {
        super.onPause()
        // Clear active chat device when app goes to background
        NotificationHelper.setActiveChatDevice(null)
    }

    override fun onResume() {
        super.onResume()
        // Handle case where app is resumed from notification
        handleNotificationIntent(intent)
    }
}

@Composable
fun BitChatApp(
    isDarkTheme: Boolean,
    onThemeChange: (Boolean) -> Unit,
    pendingNavigation: MainActivity.PendingNavigation? = null,
    onNavigationHandled: () -> Unit = {}
) {
    val navController = rememberNavController()
    val deviceListViewModel: DeviceListViewModel = viewModel()
    val chatViewModel: ChatViewModel = viewModel()

    // Handle pending navigation from notification
    LaunchedEffect(pendingNavigation) {
        pendingNavigation?.let { navigation ->
            Log.d("BitChatApp", "Handling pending navigation to ${navigation.deviceName}")

            // Set the current device in the chat view model
            chatViewModel.setCurrentDevice(navigation.deviceId, navigation.deviceName)

            // Navigate to the chat screen
            navController.navigate("chat/${navigation.deviceId}/${navigation.deviceName}") {
                // Clear the back stack to avoid issues
                popUpTo("device_list") { inclusive = false }
            }

            // Mark navigation as handled
            onNavigationHandled()
        }
    }

    NavHost(
        navController = navController,
        startDestination = "device_list"
    ) {
        composable("device_list") {
            // Clear active chat device when on device list
            LaunchedEffect(Unit) {
                NotificationHelper.setActiveChatDevice(null)
            }

            DeviceListScreen(
                deviceListViewModel = deviceListViewModel,
                chatViewModel = chatViewModel,
                isDarkTheme = isDarkTheme,
                onThemeChange = onThemeChange,
                onNavigateToChat = { deviceId, deviceName ->
                    navController.navigate("chat/$deviceId/$deviceName")
                }
            )
        }

        composable("chat/{deviceId}/{deviceName}") { backStackEntry ->
            val deviceId = backStackEntry.arguments?.getString("deviceId") ?: ""
            val deviceName = backStackEntry.arguments?.getString("deviceName") ?: "Unknown Device"

            // Set active chat device for notification suppression
            LaunchedEffect(deviceId) {
                if (deviceId.isNotEmpty()) {
                    NotificationHelper.setActiveChatDevice(deviceId)
                }
            }

            // Clear active chat device when leaving this screen
            DisposableEffect(Unit) {
                onDispose {
                    NotificationHelper.setActiveChatDevice(null)
                }
            }

            ChatScreen(
                viewModel = chatViewModel,
                deviceId = deviceId,
                deviceName = deviceName,
                onNavigateUp = {
                    navController.popBackStack()
                }
            )
        }
    }
}
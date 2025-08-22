package com.bitchat.ui

import android.Manifest
import android.annotation.SuppressLint
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BluetoothSearching
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.Bluetooth
import androidx.compose.material.icons.outlined.BluetoothDisabled
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshContainer
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.bitchat.model.ChatDevice
import com.bitchat.viewmodel.ChatViewModel
import com.bitchat.viewmodel.DeviceListViewModel
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun DeviceListScreen(
    deviceListViewModel: DeviceListViewModel,
    chatViewModel: ChatViewModel,
    isDarkTheme: Boolean,
    onThemeChange: (Boolean) -> Unit,
    onNavigateToChat: (deviceId: String, deviceName: String) -> Unit
) {
    val context = LocalContext.current
    val devices by deviceListViewModel.devices.collectAsState()
    val isScanning by deviceListViewModel.isScanning.collectAsState()
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior(rememberTopAppBarState())

    // Use ChatViewModel's connected devices and messages
    val connectedDevices by chatViewModel.connectedDevices.collectAsState()
    val allMessages by chatViewModel.allMessages.collectAsState()

    val pullRefreshState = rememberPullToRefreshState()
    if (pullRefreshState.isRefreshing) {
        LaunchedEffect(true) {
            deviceListViewModel.loadPairedDevices()
            delay(1500)
            pullRefreshState.endRefresh()
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            deviceListViewModel.loadPairedDevices()
        } else {
            Toast.makeText(context, "Bluetooth permission is required.", Toast.LENGTH_SHORT).show()
        }
    }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (!deviceListViewModel.hasBluetoothConnectPermission()) {
                permissionLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT)
            } else {
                deviceListViewModel.loadPairedDevices()
            }
        } else {
            deviceListViewModel.loadPairedDevices()
        }
    }

    Scaffold(
        modifier = Modifier
            .nestedScroll(scrollBehavior.nestedScrollConnection)
            .statusBarsPadding(),
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        "BitChat",
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                },
                actions = {
                    // Refresh button
                    IconButton(
                        onClick = {
                            deviceListViewModel.refreshDevices()
                        }
                    ) {
                        Icon(
                            Icons.Filled.Refresh,
                            contentDescription = "Refresh",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    ThemeIconButton(isDarkTheme = isDarkTheme, onThemeChange = onThemeChange)
                },
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    actionIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = {
                    if (isScanning) {
                        deviceListViewModel.stopScan()
                    } else {
                        deviceListViewModel.startScan()
                    }
                },
                text = { Text(if (isScanning) "Stop" else "Scan") },
                icon = {
                    if (isScanning) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    } else {
                        Icon(Icons.Filled.BluetoothSearching, contentDescription = "Scan for devices")
                    }
                },
                expanded = true,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.animateContentSize()
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when {
                isScanning && devices.isEmpty() -> {
                    LoadingState(modifier = Modifier.fillMaxSize())
                }
                devices.isEmpty() -> {
                    EmptyState(modifier = Modifier.fillMaxSize())
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(items = devices, key = { it.device.address }) { device ->
                            DeviceListItem(
                                modifier = Modifier.animateItemPlacement(),
                                device = device,
                                isConnected = connectedDevices.contains(device.device.address),
                                messageCount = allMessages[device.device.address]?.size ?: 0,
                                lastMessage = allMessages[device.device.address]?.lastOrNull()?.content,
                                chatViewModel = chatViewModel,
                                deviceListViewModel = deviceListViewModel,
                                onClick = {
                                    // Get the actual device name using proper resolution
                                    val actualDeviceName = deviceListViewModel.getDeviceName(device.device.address)

                                    // If not connected, connect first
                                    if (!connectedDevices.contains(device.device.address)) {
                                        chatViewModel.connectToDevice(device.device)
                                    }

                                    // Set current device and navigate
                                    chatViewModel.setCurrentDevice(device.device.address, actualDeviceName)
                                    onNavigateToChat(device.device.address, actualDeviceName)
                                },
                                onDisconnect = {
                                    chatViewModel.disconnectFromDevice(device.device.address)
                                    // Update device list connection state
                                    deviceListViewModel.updateDeviceConnectionState(device.device.address, false)
                                },
                                onClearMessages = {
                                    chatViewModel.clearMessagesForDevice(device.device.address)
                                }
                            )
                        }
                    }
                }
            }

            PullToRefreshContainer(
                state = pullRefreshState,
                modifier = Modifier.align(Alignment.TopCenter),
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
                contentColor = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@SuppressLint("MissingPermission")
@Composable
private fun DeviceListItem(
    device: ChatDevice,
    isConnected: Boolean,
    messageCount: Int,
    lastMessage: String?,
    chatViewModel: ChatViewModel,
    deviceListViewModel: DeviceListViewModel,
    onClick: () -> Unit,
    onDisconnect: () -> Unit,
    onClearMessages: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Use DeviceListViewModel's enhanced getDeviceName method
    val deviceName = deviceListViewModel.getDeviceName(device.device.address)

    ListItem(
        modifier = modifier
            .clip(MaterialTheme.shapes.large)
            .clickable(onClick = onClick),
        colors = ListItemDefaults.colors(
            containerColor = if (isConnected)
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
            else
                MaterialTheme.colorScheme.surfaceVariant,
            headlineColor = MaterialTheme.colorScheme.onSurface,
            supportingColor = MaterialTheme.colorScheme.onSurfaceVariant
        ),
        headlineContent = {
            Text(
                text = deviceName,
                fontWeight = FontWeight.SemiBold
            )
        },
        supportingContent = {
            Column {
                // Show MAC address as secondary info only if device name is different from the formatted address
                if (!deviceName.startsWith("Device ")) {
                    Text(
                        text = device.device.address,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                }

                // Connection status
                if (isConnected) {
                    Text(
                        text = "Connected",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium
                    )
                }

                if (lastMessage != null) {
                    Text(
                        text = lastMessage,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
                if (messageCount > 0) {
                    Text(
                        text = "$messageCount messages",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        },
        leadingContent = {
            Icon(
                Icons.Outlined.Bluetooth,
                contentDescription = "Bluetooth Device",
                tint = if (isConnected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        trailingContent = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (isConnected) {
                    IconButton(
                        onClick = onDisconnect,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            Icons.Filled.Clear,
                            contentDescription = "Disconnect",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
                if (messageCount > 0) {
                    IconButton(
                        onClick = onClearMessages,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            Icons.Filled.Delete,
                            contentDescription = "Clear Messages",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
                StatusIndicator(isOnline = isConnected)
            }
        }
    )
}

@Composable
private fun LoadingState(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(48.dp),
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = "Scanning for devices...",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Make sure other devices have BitChat running and are discoverable",
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
        )
    }
}

@Composable
private fun EmptyState(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Outlined.BluetoothDisabled,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
        )
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = "No Devices Found",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Tap the Scan button to search for nearby devices running BitChat.",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Make sure:\n• Bluetooth is enabled\n• Other devices are discoverable\n• Other devices are running BitChat",
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
        )
    }
}

@Composable
private fun StatusIndicator(isOnline: Boolean, modifier: Modifier = Modifier) {
    val onlineColor = MaterialTheme.colorScheme.primary
    val offlineColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)

    Box(
        modifier = modifier
            .size(12.dp)
            .clip(CircleShape)
            .background(if (isOnline) onlineColor else offlineColor)
    )
}
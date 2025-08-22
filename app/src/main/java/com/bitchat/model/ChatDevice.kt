package com.bitchat.model

import android.bluetooth.BluetoothDevice

data class ChatDevice(
    val device: BluetoothDevice,
    var isOnline: Boolean = false,
    var lastSeen: Long = 0L
)

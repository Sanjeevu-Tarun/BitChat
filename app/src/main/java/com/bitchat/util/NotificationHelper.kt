package com.bitchat.util

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.bitchat.MainActivity
import com.bitchat.R

object NotificationHelper {

    private const val CHANNEL_ID = "bitchat_messages"
    private const val CHANNEL_NAME = "BitChat Messages"
    private const val CHANNEL_DESC = "Notifies when a new message is received"
    private const val NOTIFICATION_ID = 1001

    // Track current active chat to suppress notifications
    private var currentActiveChatDeviceId: String? = null

    /**
     * Creates the notification channel for Android O+
     */
    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = CHANNEL_DESC
            }

            val manager: NotificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    /**
     * Set the currently active chat device ID to suppress notifications
     */
    fun setActiveChatDevice(deviceId: String?) {
        currentActiveChatDeviceId = deviceId
    }

    /**
     * Shows a message notification with proper navigation and suppression logic
     */
    fun showMessageNotification(
        context: Context,
        message: String,
        deviceId: String,
        deviceName: String
    ) {
        // ⛔ Skip if notification permission is missing (Android 13+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val hasPermission = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED

            if (!hasPermission) return
        }

        // ⛔ Skip notification if user is already chatting with this device
        if (currentActiveChatDeviceId == deviceId) {
            return
        }

        // 🎯 Intent to open specific chat when user taps notification
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            // Add device information for navigation
            putExtra("DEVICE_ID", deviceId)
            putExtra("DEVICE_NAME", deviceName)
            putExtra("NAVIGATE_TO_CHAT", true)
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            deviceId.hashCode(), // Use device-specific request code
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_message_notification)
            .setContentTitle("New Message from $deviceName")
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)

        NotificationManagerCompat.from(context).notify(
            deviceId.hashCode(), // Use device-specific notification ID
            builder.build()
        )
    }

    /**
     * Overloaded method for backward compatibility
     */
    fun showMessageNotification(context: Context, message: String) {
        // For backward compatibility, show generic notification
        showMessageNotification(context, message, "unknown", "Unknown Device")
    }
}
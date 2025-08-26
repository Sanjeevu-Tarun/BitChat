package com.bitchat.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.bitchat.util.AudioRecorder
import kotlinx.coroutines.delay
import java.io.File
import kotlin.math.max
import kotlin.random.Random

@Composable
fun VoiceRecorder(
    onAudioRecorded: (File) -> Unit,
    modifier: Modifier = Modifier,
    showPermissionDialog: Boolean = true
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val audioRecorder = remember { AudioRecorder(context) }

    var isRecording by remember { mutableStateOf(false) }
    var recordingDuration by remember { mutableStateOf(0) }
    var currentAmplitude by remember { mutableStateOf(0) }
    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        )
    }
    var showPermissionRequest by remember { mutableStateOf(false) }

    // Permission launcher
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasPermission = granted
        showPermissionRequest = false
        if (granted) {
            startRecording(audioRecorder, haptic) { isRecording = true }
        }
    }

    // Timer effect for recording duration
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

    Box(modifier = modifier) {
        // Recording interface with slide animation
        AnimatedVisibility(
            visible = isRecording,
            enter = slideInHorizontally(
                initialOffsetX = { fullWidth -> fullWidth },
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioLowBouncy,
                    stiffness = Spring.StiffnessMedium
                )
            ) + fadeIn(
                animationSpec = tween(
                    durationMillis = 400,
                    easing = CubicBezierEasing(0.25f, 0.46f, 0.45f, 0.94f)
                )
            ) + scaleIn(
                initialScale = 0.95f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioLowBouncy,
                    stiffness = Spring.StiffnessMedium
                )
            ),
            exit = slideOutHorizontally(
                targetOffsetX = { fullWidth -> fullWidth },
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessHigh
                )
            ) + fadeOut(
                animationSpec = tween(
                    durationMillis = 250,
                    easing = CubicBezierEasing(0.55f, 0.06f, 0.68f, 0.19f)
                )
            ) + scaleOut(
                targetScale = 0.95f,
                animationSpec = tween(
                    durationMillis = 250,
                    easing = EaseInBack
                )
            )
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
                    recordedFile?.let { onAudioRecorded(it) }
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                }
            )
        }

        // Microphone button
        AnimatedVisibility(
            visible = !isRecording,
            enter = scaleIn(
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessHigh
                )
            ) + fadeIn(),
            exit = scaleOut(
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessHigh
                )
            ) + fadeOut()
        ) {
            PremiumMicrophoneButton(
                isRecording = false,
                onClick = {
                    if (hasPermission) {
                        startRecording(audioRecorder, haptic) { isRecording = true }
                    } else if (showPermissionDialog) {
                        showPermissionRequest = true
                    } else {
                        permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    }
                }
            )
        }
    }

    // Permission request dialog
    if (showPermissionRequest && showPermissionDialog) {
        AlertDialog(
            onDismissRequest = { showPermissionRequest = false },
            title = {
                Text(
                    "Microphone Permission Required",
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontWeight = FontWeight.Bold
                    )
                )
            },
            text = {
                Text(
                    "This app needs microphone permission to record voice messages.",
                    style = MaterialTheme.typography.bodyLarge
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Text("Grant Permission")
                }
            },
            dismissButton = {
                TextButton(onClick = { showPermissionRequest = false }) {
                    Text("Cancel")
                }
            },
            shape = RoundedCornerShape(20.dp),
            containerColor = MaterialTheme.colorScheme.surface
        )
    }
}

@Composable
private fun PremiumMicrophoneButton(
    isRecording: Boolean,
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

    val rotationAnimation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = if (isRecording) 360f else 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )

    Box(
        modifier = Modifier
            .size(56.dp)
            .scale(pulseScale)
            .clip(CircleShape)
            .background(
                brush = Brush.radialGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.primary,
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.9f)
                    )
                )
            )
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        // Rotating background ring for recording state
        if (isRecording) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .rotate(rotationAnimation)
                    .clip(CircleShape)
                    .background(
                        brush = Brush.sweepGradient(
                            colors = listOf(
                                Color.Transparent,
                                MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.3f),
                                Color.Transparent
                            )
                        )
                    )
            )
        }

        Icon(
            imageVector = Icons.Default.Mic,
            contentDescription = "Record Voice Message",
            tint = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.size(24.dp)
        )

        // Premium glow effect
        if (isRecording) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                                Color.Transparent
                            )
                        )
                    )
            )
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
        shape = RoundedCornerShape(24.dp)
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

                // Enhanced Waveform with proper spacing
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
    barCount: Int = 25
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
    val barWidth = 3.dp.toPx()
    val barSpacing = 2.dp.toPx()
    val totalBarSpace = barWidth + barSpacing
    val centerY = canvasSize.height / 2f
    val maxBarHeight = canvasSize.height * 0.8f

    amplitudes.forEachIndexed { index, amplitude ->
        val barHeight = max(6.dp.toPx(), amplitude * maxBarHeight)
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

        // Subtle glow effect for higher amplitudes
        if (amplitude > 0.5f) {
            drawLine(
                color = barColor.copy(alpha = 0.2f),
                start = Offset(startX, centerY - barHeight / 2f - 2.dp.toPx()),
                end = Offset(startX, centerY + barHeight / 2f + 2.dp.toPx()),
                strokeWidth = barWidth + 2.dp.toPx(),
                cap = StrokeCap.Round
            )
        }
    }
}

private fun startRecording(
    audioRecorder: AudioRecorder,
    haptic: androidx.compose.ui.hapticfeedback.HapticFeedback,
    onRecordingStarted: () -> Unit
) {
    val file = audioRecorder.startRecording()
    if (file != null) {
        onRecordingStarted()
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
    }
}

private fun formatDuration(millis: Int): String {
    val seconds = millis / 1000
    val minutes = seconds / 60
    val remainingSeconds = seconds % 60
    return "%d:%02d".format(minutes, remainingSeconds)
}
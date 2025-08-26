package com.bitchat.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable

@Composable
fun ThemeIconButton(
    isDarkTheme: Boolean,
    onThemeChange: (Boolean) -> Unit
) {
    IconButton(onClick = { onThemeChange(!isDarkTheme) }) {
        AnimatedContent(
            targetState = isDarkTheme,
            transitionSpec = {
                // Ultra smooth crossfade with rotation effect
                (fadeIn(
                    animationSpec = tween(
                        durationMillis = 0,
                        easing = FastOutSlowInEasing
                    )
                ) + slideInVertically(
                    initialOffsetY = { -it / 4 },
                    animationSpec = tween(
                        durationMillis =0,
                        easing = FastOutSlowInEasing
                    )
                )) togetherWith (fadeOut(
                    animationSpec = tween(
                        durationMillis = 75,
                        easing = FastOutSlowInEasing
                    )
                ) + slideOutVertically(
                    targetOffsetY = { it / 4 },
                    animationSpec = tween(
                        durationMillis = 75,
                        easing = FastOutSlowInEasing
                    )
                ))
            },
            label = "ThemeIconAnimation"
        ) { isDark ->
            Icon(
                imageVector = if (isDark) Icons.Default.DarkMode else Icons.Default.LightMode,
                contentDescription = if (isDark) "Switch to Light Mode" else "Switch to Dark Mode",
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}
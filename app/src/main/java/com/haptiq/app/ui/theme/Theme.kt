package com.haptiq.app.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val HaptiqDarkColorScheme = darkColorScheme(
    primary                 = ColorPrimary,
    onPrimary               = ColorOnPrimary,
    primaryContainer        = ColorPrimaryContainer,
    onPrimaryContainer      = ColorOnPrimaryContainer,

    secondary               = ColorHapticAccent,
    onSecondary             = ColorBackground,
    secondaryContainer      = ColorSurfaceContainerHigh,
    onSecondaryContainer    = ColorOnSurface,

    background              = ColorBackground,
    onBackground            = ColorOnBackground,
    surface                 = ColorSurface,
    onSurface               = ColorOnSurface,
    surfaceVariant          = ColorSurfaceVariant,
    onSurfaceVariant        = ColorOnSurfaceVariant,
    surfaceContainer        = ColorSurfaceContainer,
    surfaceContainerHigh    = ColorSurfaceContainerHigh,
    surfaceContainerLow     = ColorSurfaceContainerLow,

    outline                 = ColorOutline,
    outlineVariant          = ColorOutlineVariant,

    error                   = ColorError,
    onError                 = ColorOnError,
)

@Composable
fun HaptiqTheme(content: @Composable () -> Unit) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            WindowCompat.setDecorFitsSystemWindows(window, false)
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                @Suppress("DEPRECATION")
                window.statusBarColor = android.graphics.Color.argb(180, 33, 29, 26)
                @Suppress("DEPRECATION")
                window.navigationBarColor = android.graphics.Color.argb(180, 33, 29, 26)
            }
        }
    }
    MaterialTheme(
        colorScheme = HaptiqDarkColorScheme,
        typography  = Typography,
        content     = content
    )
}

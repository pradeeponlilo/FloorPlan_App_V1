package com.floorplan.tool.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val LightColorScheme = lightColorScheme(
    primary = androidx.compose.ui.graphics.Color(0xFF1976D2),
    onPrimary = androidx.compose.ui.graphics.Color.White,
    secondary = androidx.compose.ui.graphics.Color(0xFF455A64),
    surface = androidx.compose.ui.graphics.Color(0xFFFAFAFA),
    background = androidx.compose.ui.graphics.Color(0xFFF2F2F2),
    error = androidx.compose.ui.graphics.Color(0xFFD32F2F)
)

private val DarkColorScheme = darkColorScheme(
    primary = androidx.compose.ui.graphics.Color(0xFF90CAF9),
    secondary = androidx.compose.ui.graphics.Color(0xFF78909C),
    surface = androidx.compose.ui.graphics.Color(0xFF1E1E1E),
    background = androidx.compose.ui.graphics.Color(0xFF121212),
    error = androidx.compose.ui.graphics.Color(0xFFEF5350)
)

@Composable
fun FloorPlanToolTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography(),
        content = content
    )
}

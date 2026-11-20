package com.cosmo.butler

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val ObsidianBg = Color(0xFF07090E)
val HudSurface = Color(0xFF101520)
val HudSurfaceElevated = Color(0xFF171F2E)
val HudOutline = Color(0xFF26334D)
val TextPrimary = Color(0xFFF1F5F9)
val TextSecondary = Color(0xFF94A3B8)
val TextMuted = Color(0xFF64748B)

val StudyCyan = Color(0xFF00E5FF)
val GameAmber = Color(0xFFFFAB00)
val SleepCrimson = Color(0xFFFF1744)
val NormalEmerald = Color(0xFF00E676)
val TerminalGreen = Color(0xFF00E676)
val TerminalRed = Color(0xFFFF5252)

@Composable
fun CosmoTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            background = ObsidianBg,
            surface = HudSurface,
            primary = StudyCyan,
            onBackground = TextPrimary,
            onSurface = TextPrimary
        ),
        content = content
    )
}

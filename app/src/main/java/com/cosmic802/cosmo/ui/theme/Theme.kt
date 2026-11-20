package com.cosmic802.cosmo.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

object CosmoColors {
    val VoidBlack = Color(0xFF05080F)
    val DeepSlate = Color(0xFF0B1220)
    val PanelSurface = Color(0xFF101A2E)
    val CyanPrimary = Color(0xFF00E5FF)
    val CyanDim = Color(0xFF008394)
    val CyanGlow = Color(0x3300E5FF)
    val StarkGold = Color(0xFFFFB300)
    val CrimsonAlert = Color(0xFFFF2A55)
    val EmeraldOk = Color(0xFF00E676)
    val TextPrimary = Color(0xFFE6F4F8)
    val TextSecondary = Color(0xFF7D9BB0)
    val BorderLine = Color(0xFF1B324B)
}

private val CosmoDarkColorScheme = darkColorScheme(
    primary = CosmoColors.CyanPrimary,
    onPrimary = CosmoColors.VoidBlack,
    secondary = CosmoColors.StarkGold,
    onSecondary = CosmoColors.VoidBlack,
    tertiary = CosmoColors.EmeraldOk,
    background = CosmoColors.VoidBlack,
    onBackground = CosmoColors.TextPrimary,
    surface = CosmoColors.DeepSlate,
    onSurface = CosmoColors.TextPrimary,
    surfaceVariant = CosmoColors.PanelSurface,
    onSurfaceVariant = CosmoColors.TextSecondary,
    error = CosmoColors.CrimsonAlert,
    outline = CosmoColors.BorderLine
)

val CosmoTypography = Typography(
    headlineMedium = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Bold,
        fontSize = 22.sp,
        letterSpacing = 2.sp,
        color = CosmoColors.CyanPrimary
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp,
        letterSpacing = 1.2.sp,
        color = CosmoColors.TextPrimary
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        color = CosmoColors.TextPrimary
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        letterSpacing = 1.sp,
        color = CosmoColors.TextSecondary
    )
)

@Composable
fun CosmoTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = CosmoDarkColorScheme,
        typography = CosmoTypography,
        content = content
    )
}
